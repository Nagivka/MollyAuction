package ua.nagivka.nGVKauction.managers;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import ua.nagivka.nGVKauction.NGVKauction;
import ua.nagivka.nGVKauction.database.DatabaseManager;
import ua.nagivka.nGVKauction.models.AuctionItem;
import ua.nagivka.nGVKauction.models.Category;
import ua.nagivka.nGVKauction.models.SortMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuctionManager {

    private final NGVKauction plugin;
    private final DatabaseManager databaseManager;
    private final LogManager logManager;

    private final Map<UUID, AuctionItem> activeItems = new ConcurrentHashMap<>();
    private final Map<UUID, List<ItemStack>> expiredItems = new ConcurrentHashMap<>();

    public AuctionManager(NGVKauction plugin, DatabaseManager databaseManager, LogManager logManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.logManager = logManager;
    }

    public void loadDataAsync() {
        databaseManager.loadAllActiveItemsAsync().thenAccept(items -> {
            activeItems.clear();
            for (AuctionItem item : items) {
                activeItems.put(item.getId(), item);
            }
        });

        databaseManager.loadAllExpiredItemsAsync().thenAccept(expired -> {
            expiredItems.clear();
            for (Map.Entry<UUID, List<ItemStack>> entry : expired.entrySet()) {
                expiredItems.put(entry.getKey(), new CopyOnWriteArrayList<>(entry.getValue()));
            }
        });
    }

    public int getPlayerSlotLimit(Player player) {
        if (player.hasPermission("ngvkauctions.admin")) return 999;
        int maxSlots = plugin.getConfig().getInt("settings.default-slots", 3);
        for (PermissionAttachmentInfo perm : player.getEffectivePermissions()) {
            if (perm.getPermission().startsWith("ngvkauctions.slots.")) {
                try {
                    int limit = Integer.parseInt(perm.getPermission().replace("ngvkauctions.slots.", ""));
                    if (limit > maxSlots) maxSlots = limit;
                } catch (NumberFormatException ignored) {}
            }
        }
        return maxSlots;
    }

    public int getActiveCount(Player player) {
        return (int) activeItems.values().stream()
                .filter(i -> i.getSellerUuid().equals(player.getUniqueId()))
                .count();
    }

    public void addItem(AuctionItem item) {
        activeItems.put(item.getId(), item);
        databaseManager.saveAuctionItemAsync(item);
        logManager.log("ITEM_SELL", "Player " + item.getSellerName() + " (" + item.getSellerUuid() + ") listed " + item.getItem().getType() + " x" + item.getItem().getAmount() + " for $" + item.getPrice());
    }

    public boolean removeItem(AuctionItem item) {
        AuctionItem removed = activeItems.remove(item.getId());
        if (removed != null) {
            databaseManager.deleteAuctionItemAsync(item.getId());
            return true;
        }
        return false;
    }

    public AuctionItem getItem(UUID id) {
        return activeItems.get(id);
    }

    public List<AuctionItem> getActiveItems() {
        return new ArrayList<>(activeItems.values());
    }

    public List<AuctionItem> getPlayerItems(UUID playerUuid) {
        return activeItems.values().stream()
                .filter(item -> item.getSellerUuid().equals(playerUuid))
                .sorted(Comparator.comparingLong(AuctionItem::getCreatedAt).reversed())
                .toList();
    }

    public List<AuctionItem> getFilteredAndSortedItems(Category category, SortMode sortMode, String searchQuery) {
        return activeItems.values().stream()
                .filter(item -> matchCategory(item, category))
                .filter(item -> matchSearch(item, searchQuery))
                .sorted(getComparator(sortMode))
                .toList();
    }

    private Comparator<AuctionItem> getComparator(SortMode sortMode) {
        return switch (sortMode) {
            case CHEAPEST -> Comparator.comparingDouble(AuctionItem::getPrice);
            case EXPENSIVE -> Comparator.comparingDouble(AuctionItem::getPrice).reversed();
            case CHEAPEST_UNIT -> Comparator.comparingDouble(a -> a.getPrice() / Math.max(1, a.getItem().getAmount()));
            case EXPENSIVE_UNIT -> (a, b) -> Double.compare(b.getPrice() / Math.max(1, b.getItem().getAmount()), a.getPrice() / Math.max(1, a.getItem().getAmount()));
            case OLDEST -> Comparator.comparingLong(AuctionItem::getCreatedAt);
            case NEWEST -> Comparator.comparingLong(AuctionItem::getCreatedAt).reversed();
            case DEFAULT -> (a, b) -> 0;
        };
    }

    private boolean matchSearch(AuctionItem item, String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase();
        String seller = item.getSellerName().toLowerCase();
        String type = item.getItem().getType().toString().toLowerCase();
        String name = item.getItem().hasItemMeta() && item.getItem().getItemMeta().hasDisplayName()
                ? item.getItem().getItemMeta().getDisplayName().toLowerCase() : "";
        return seller.contains(q) || type.contains(q) || name.contains(q);
    }

    private boolean matchCategory(AuctionItem item, Category category) {
        if (category == Category.ALL) return true;

        ItemStack is = item.getItem();
        Material mat = is.getType();
        String name = mat.toString();

        return switch (category) {
            case BLOCKS -> mat.isBlock();
            case TOOLS -> name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || mat == Material.FISHING_ROD || mat == Material.SHEARS || mat == Material.FLINT_AND_STEEL || mat == Material.COMPASS || mat == Material.CLOCK;
            case WEAPONS -> name.endsWith("_SWORD") || mat == Material.BOW || mat == Material.CROSSBOW || mat == Material.TRIDENT || mat == Material.MACE;
            case ARMOR -> name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || mat == Material.ELYTRA || mat == Material.SHIELD || mat == Material.WOLF_ARMOR;
            case FOOD -> mat.isEdible();
            case FUEL -> mat == Material.COAL || mat == Material.CHARCOAL || mat == Material.LAVA_BUCKET || mat == Material.BLAZE_ROD || mat == Material.DRIED_KELP_BLOCK || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_PLANKS");
            case POTIONS -> mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION || mat == Material.TIPPED_ARROW;
            case MECHANISMS -> mat == Material.REDSTONE || mat == Material.REPEATER || mat == Material.COMPARATOR || mat == Material.PISTON || mat == Material.STICKY_PISTON || mat == Material.OBSERVER || mat == Material.DROPPER || mat == Material.DISPENSER || mat == Material.HOPPER || mat == Material.TNT || mat == Material.LEVER || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE") || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR");
            case ALCHEMY -> mat == Material.NETHER_WART || mat == Material.BREWING_STAND || mat == Material.BLAZE_POWDER || mat == Material.GHAST_TEAR || mat == Material.FERMENTED_SPIDER_EYE || mat == Material.MAGMA_CREAM || mat == Material.GOLDEN_CARROT || mat == Material.GLISTERING_MELON_SLICE || mat == Material.PHANTOM_MEMBRANE || mat == Material.CAULDRON || mat == Material.GLASS_BOTTLE;
            case ENCHANTS -> mat == Material.ENCHANTED_BOOK || mat == Material.BOOK;
            case JEWELRY -> mat == Material.DIAMOND || mat == Material.EMERALD || mat == Material.GOLD_INGOT || mat == Material.GOLD_NUGGET || mat == Material.RAW_GOLD || mat == Material.AMETHYST_SHARD || mat == Material.LAPIS_LAZULI || mat == Material.QUARTZ || mat == Material.NETHERITE_INGOT || mat == Material.NETHERITE_SCRAP;
            case UNIQUE -> is.hasItemMeta() && (is.getItemMeta().hasCustomModelData() || is.getItemMeta().hasDisplayName());
            default -> true;
        };
    }

    public void addExpiredItem(UUID uuid, ItemStack item) {
        expiredItems.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>()).add(item);
        databaseManager.saveExpiredItemAsync(uuid, item);
    }

    public List<ItemStack> getExpiredItems(UUID uuid) {
        return expiredItems.getOrDefault(uuid, Collections.emptyList());
    }

    public void removeExpiredItem(UUID uuid, int index) {
        List<ItemStack> list = expiredItems.get(uuid);
        if (list != null && index >= 0 && index < list.size()) {
            list.remove(index);
            databaseManager.clearExpiredItemsForPlayerAsync(uuid).thenRun(() -> {
                for (ItemStack is : list) {
                    databaseManager.saveExpiredItemAsync(uuid, is);
                }
            });
        }
    }

    public void checkExpirations() {
        long now = System.currentTimeMillis();
        for (AuctionItem item : activeItems.values()) {
            if (now > item.getExpireTime()) {
                if (removeItem(item)) {
                    addExpiredItem(item.getSellerUuid(), item.getItem());
                    logManager.log("ITEM_EXPIRE", "Item " + item.getId() + " belonging to " + item.getSellerName() + " expired and moved to storage.");
                }
            }
        }
    }
}