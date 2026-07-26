package ua.nagivka.nGVKauction.managers;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import ua.nagivka.nGVKauction.NGVKauction;
import ua.nagivka.nGVKauction.models.AuctionItem;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AuctionManager {
    private final NGVKauction plugin;
    private List<AuctionItem> activeItems = new ArrayList<>();
    private final Map<UUID, List<ItemStack>> expiredItems = new HashMap<>();
    private final File dataFile;

    public enum Category {
        ALL("Все"),
        BLOCKS("Блоки"),
        TOOLS("Инструменты"),
        WEAPONS("Оружие"),
        ARMOR("Броня"),
        FOOD("Еда"),
        FUEL("Топливо"),
        POTIONS("Зелья"),
        MECHANISMS("Механизмы"),
        ALCHEMY("Алхимия"),
        ENCHANTS("Зачарования"),
        JEWELRY("Ювелирные"),
        UNIQUE("Уникальные");

        private final String name;
        Category(String name) { this.name = name; }
        public String getName() { return name; }

        public Category next() {
            Category[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }

        public Category previous() {
            Category[] vals = values();
            return vals[(ordinal() - 1 + vals.length) % vals.length];
        }
    }

    public enum SortMode {
        DEFAULT("По умолчанию"),
        NEWEST("Новые"),
        OLDEST("Старые"),
        EXPENSIVE("Дорогие"),
        CHEAPEST("Дешёвые"),
        EXPENSIVE_UNIT("Дорогие/шт"),
        CHEAPEST_UNIT("Дешёвые/шт");

        private final String name;
        SortMode(String name) { this.name = name; }
        public String getName() { return name; }

        public SortMode next() {
            SortMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }

        public SortMode previous() {
            SortMode[] vals = values();
            return vals[(ordinal() - 1 + vals.length) % vals.length];
        }
    }

    public AuctionManager(NGVKauction plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
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
        return (int) activeItems.stream().filter(i -> i.getSellerUuid().equals(player.getUniqueId())).count();
    }

    public void addItem(AuctionItem item) { activeItems.add(item); }
    public void removeItem(AuctionItem item) { activeItems.remove(item); }
    public List<AuctionItem> getActiveItems() { return activeItems; }

    public List<AuctionItem> getPlayerItems(UUID playerUuid) {
        return activeItems.stream().filter(item -> item.getSellerUuid().equals(playerUuid)).collect(Collectors.toList());
    }

    public List<AuctionItem> getFilteredAndSortedItems(Category category, SortMode sortMode, String searchQuery) {
        return activeItems.stream()
                .filter(item -> matchCategory(item, category))
                .filter(item -> matchSearch(item, searchQuery))
                .sorted((a, b) -> {
                    switch (sortMode) {
                        case CHEAPEST: return Double.compare(a.getPrice(), b.getPrice());
                        case EXPENSIVE: return Double.compare(b.getPrice(), a.getPrice());
                        case CHEAPEST_UNIT: return Double.compare(a.getPrice() / Math.max(1, a.getItem().getAmount()), b.getPrice() / Math.max(1, b.getItem().getAmount()));
                        case EXPENSIVE_UNIT: return Double.compare(b.getPrice() / Math.max(1, b.getItem().getAmount()), a.getPrice() / Math.max(1, a.getItem().getAmount()));
                        case OLDEST: return Long.compare(a.getCreatedAt(), b.getCreatedAt());
                        case NEWEST: return Long.compare(b.getCreatedAt(), a.getCreatedAt());
                        case DEFAULT: default: return 0;
                    }
                })
                .collect(Collectors.toList());
    }

    private boolean matchSearch(AuctionItem item, String query) {
        if (query == null || query.isEmpty()) return true;
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

        switch (category) {
            case BLOCKS: return mat.isBlock();
            case TOOLS: return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || mat == Material.FISHING_ROD || mat == Material.SHEARS || mat == Material.FLINT_AND_STEEL || mat == Material.COMPASS || mat == Material.CLOCK;
            case WEAPONS: return name.endsWith("_SWORD") || mat == Material.BOW || mat == Material.CROSSBOW || mat == Material.TRIDENT || mat == Material.MACE;
            case ARMOR: return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS") || mat == Material.ELYTRA || mat == Material.SHIELD || mat == Material.WOLF_ARMOR;
            case FOOD: return mat.isEdible();
            case FUEL: return mat == Material.COAL || mat == Material.CHARCOAL || mat == Material.LAVA_BUCKET || mat == Material.BLAZE_ROD || mat == Material.DRIED_KELP_BLOCK || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_PLANKS");
            case POTIONS: return mat == Material.POTION || mat == Material.SPLASH_POTION || mat == Material.LINGERING_POTION || mat == Material.TIPPED_ARROW;
            case MECHANISMS: return mat == Material.REDSTONE || mat == Material.REPEATER || mat == Material.COMPARATOR || mat == Material.PISTON || mat == Material.STICKY_PISTON || mat == Material.OBSERVER || mat == Material.DROPPER || mat == Material.DISPENSER || mat == Material.HOPPER || mat == Material.TNT || mat == Material.LEVER || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE") || name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR");
            case ALCHEMY: return mat == Material.NETHER_WART || mat == Material.BREWING_STAND || mat == Material.BLAZE_POWDER || mat == Material.GHAST_TEAR || mat == Material.FERMENTED_SPIDER_EYE || mat == Material.MAGMA_CREAM || mat == Material.GOLDEN_CARROT || mat == Material.GLISTERING_MELON_SLICE || mat == Material.PHANTOM_MEMBRANE || mat == Material.CAULDRON || mat == Material.GLASS_BOTTLE;
            case ENCHANTS: return mat == Material.ENCHANTED_BOOK || mat == Material.BOOK;
            case JEWELRY: return mat == Material.DIAMOND || mat == Material.EMERALD || mat == Material.GOLD_INGOT || mat == Material.GOLD_NUGGET || mat == Material.RAW_GOLD || mat == Material.AMETHYST_SHARD || mat == Material.LAPIS_LAZULI || mat == Material.QUARTZ || mat == Material.NETHERITE_INGOT || mat == Material.NETHERITE_SCRAP;
            case UNIQUE: return is.hasItemMeta() && (is.getItemMeta().hasCustomModelData() || is.getItemMeta().hasDisplayName());
            default: return true;
        }
    }

    public void addExpiredItem(UUID uuid, ItemStack item) {
        expiredItems.computeIfAbsent(uuid, k -> new ArrayList<>()).add(item);
    }
    public List<ItemStack> getExpiredItems(UUID uuid) { return expiredItems.getOrDefault(uuid, new ArrayList<>()); }

    public void checkExpirations() {
        long now = System.currentTimeMillis();
        Iterator<AuctionItem> iterator = activeItems.iterator();
        while (iterator.hasNext()) {
            AuctionItem item = iterator.next();
            if (item == null || item.getItem() == null) {
                iterator.remove();
                continue;
            }
            if (now > item.getExpireTime()) {
                addExpiredItem(item.getSellerUuid(), item.getItem());
                iterator.remove();
            }
        }
    }

    public void saveData() {
        FileConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> serializedItems = new ArrayList<>();
        for (AuctionItem ai : activeItems) {
            serializedItems.add(ai.serializeToMap());
        }
        cfg.set("items", serializedItems);

        for (Map.Entry<UUID, List<ItemStack>> entry : expiredItems.entrySet()) {
            List<String> base64List = new ArrayList<>();
            for (ItemStack is : entry.getValue()) {
                try {
                    base64List.add(Base64.getEncoder().encodeToString(is.serializeAsBytes()));
                } catch (Exception ignored) {}
            }
            cfg.set("expired." + entry.getKey().toString(), base64List);
        }

        try { cfg.save(dataFile); } catch (IOException ignored) {}
    }

    public void loadData() {
        if (!dataFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

        activeItems.clear();
        if (cfg.isList("items")) {
            List<?> list = cfg.getList("items");
            if (list != null) {
                for (Object obj : list) {
                    if (obj instanceof Map) {
                        try {
                            AuctionItem ai = AuctionItem.deserializeFromMap((Map<?, ?>) obj);
                            if (ai.getItem() != null) activeItems.add(ai);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        if (cfg.getConfigurationSection("expired") != null) {
            for (String key : cfg.getConfigurationSection("expired").getKeys(false)) {
                List<String> base64List = cfg.getStringList("expired." + key);
                List<ItemStack> items = new ArrayList<>();
                for (String b64 : base64List) {
                    try {
                        byte[] bytes = Base64.getDecoder().decode(b64);
                        items.add(ItemStack.deserializeBytes(bytes));
                    } catch (Exception ignored) {}
                }
                expiredItems.put(UUID.fromString(key), items);
            }
        }
    }
}