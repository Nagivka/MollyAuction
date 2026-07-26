package ua.nagivka.nGVKauction.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.nagivka.nGVKauction.NGVKauction;
import ua.nagivka.nGVKauction.managers.AuctionManager;
import ua.nagivka.nGVKauction.models.AuctionItem;

import java.lang.reflect.Method;
import java.util.*;

public class AuctionGUI implements Listener {
    private final NGVKauction plugin;
    private static final Map<UUID, Integer> playerPages = new HashMap<>();
    private static final Map<UUID, AuctionItem> pendingPurchases = new HashMap<>();
    private static final Map<UUID, String> activeSearchQueries = new HashMap<>();
    private static final Map<UUID, AuctionManager.Category> playerCategories = new HashMap<>();
    private static final Map<UUID, AuctionManager.SortMode> playerSortModes = new HashMap<>();

    private static boolean hooksInitialized = false;
    private static Method oraxenGetItemMethod = null;
    private static Method oraxenGetOptionalMethod = null;
    private static Method itemsAdderGetInstanceMethod = null;
    private static Method itemsAdderGetItemMethod = null;

    public AuctionGUI(NGVKauction plugin) {
        this.plugin = plugin;
    }

    public static class AuctionHolder implements InventoryHolder {
        public enum MenuType { MAIN, MY_ITEMS, CONFIRM, STORAGE }
        private final MenuType menuType;
        private final int page;

        public AuctionHolder(MenuType menuType, int page) {
            this.menuType = menuType;
            this.page = page;
        }

        public MenuType getMenuType() { return menuType; }
        public int getPage() { return page; }

        @Override
        public Inventory getInventory() { return null; }
    }

    public static void openMainMenu(Player player, NGVKauction plugin, int page) {
        openMainMenuFiltered(player, plugin, page, null);
    }

    public static void openMainMenuFiltered(Player player, NGVKauction plugin, int page, String searchQuery) {
        if (searchQuery != null) activeSearchQueries.put(player.getUniqueId(), searchQuery);

        AuctionManager.Category category = playerCategories.getOrDefault(player.getUniqueId(), AuctionManager.Category.ALL);
        AuctionManager.SortMode sortMode = playerSortModes.getOrDefault(player.getUniqueId(), AuctionManager.SortMode.NEWEST);

        List<AuctionItem> items = plugin.getAuctionManager().getFilteredAndSortedItems(category, sortMode, searchQuery);

        int size = plugin.getGuisConfig().getInt("main-menu.size", 54);
        String title = plugin.getCleanGuiString("main-menu.title").replace("%page%", String.valueOf(page + 1));
        Inventory inv = Bukkit.createInventory(new AuctionHolder(AuctionHolder.MenuType.MAIN, page), size, title);

        setupFiller(inv, plugin, "main-menu.filler");

        List<Integer> slots = getSlots(plugin.getGuisConfig(), "main-menu.item-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < size - 9; i++) slots.add(i);
        }

        int maxItems = slots.size();
        int start = page * maxItems;
        int end = Math.min(start + maxItems, items.size());
        long now = System.currentTimeMillis();

        for (int i = start; i < end; i++) {
            AuctionItem ai = items.get(i);
            ItemStack display = ai.getItem().clone();
            ItemMeta meta = display.getItemMeta();

            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(NGVKauction.color("&#82919c• &#abb5beПродавец: &#eceff2" + ai.getSellerName()));
            lore.add(NGVKauction.color("&#82919c• &#abb5beЦена: &#dfb15b$" + ai.getPrice()));

            long left = ai.getExpireTime() - now;
            long hours = Math.max(0, left / 3600000L);
            long mins = Math.max(0, (left % 3600000L) / 60000L);
            lore.add(NGVKauction.color("&#82919c• &#abb5beОсталось: &#c45a5a" + hours + "ч. " + mins + "м."));
            lore.add("");

            if (ai.getSellerUuid().equals(player.getUniqueId())) {
                lore.add(NGVKauction.color("&#c45a5aЛКМ — Снять с продажи"));
            } else {
                lore.add(NGVKauction.color("&#a67fa3ЛКМ — Купить (Подтверждение)"));
                lore.add(NGVKauction.color("&#c45a5aShift + ЛКМ — Мгновенная покупка"));
            }

            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(slots.get(i - start), display);
        }

        List<String> catLore = new ArrayList<>();
        catLore.add(NGVKauction.color("&#cfa151Категории"));
        for (AuctionManager.Category c : AuctionManager.Category.values()) {
            if (c == category) catLore.add(NGVKauction.color(" &#cfa151• &#eceff2" + c.getName()));
            else catLore.add(NGVKauction.color(" &#82919c• &#abb5be" + c.getName()));
        }
        setupButton(inv, plugin, "main-menu.buttons.category-filter", catLore);

        List<String> sortLore = new ArrayList<>();
        sortLore.add(NGVKauction.color("&#cfa151Сортировка"));
        for (AuctionManager.SortMode s : AuctionManager.SortMode.values()) {
            if (s == sortMode) sortLore.add(NGVKauction.color(" &#cfa151• &#eceff2" + s.getName()));
            else sortLore.add(NGVKauction.color(" &#82919c• &#abb5be" + s.getName()));
        }
        setupButton(inv, plugin, "main-menu.buttons.sort-mode", sortLore);

        if (page > 0) setupButton(inv, plugin, "main-menu.buttons.prev-page", null);
        setupButton(inv, plugin, "main-menu.buttons.refresh", null);
        if (end < items.size()) setupButton(inv, plugin, "main-menu.buttons.next-page", null);
        setupButton(inv, plugin, "main-menu.buttons.my-items", null);
        setupButton(inv, plugin, "main-menu.buttons.storage", null);

        playerPages.put(player.getUniqueId(), page);
        player.openInventory(inv);
        plugin.playCfgSound(player, "open-menu", 1f);
    }

    public static void openMyItemsMenu(Player player, NGVKauction plugin, int page) {
        List<AuctionItem> items = plugin.getAuctionManager().getPlayerItems(player.getUniqueId());
        int size = plugin.getGuisConfig().getInt("my-items-menu.size", 54);
        String title = plugin.getCleanGuiString("my-items-menu.title").replace("%page%", String.valueOf(page + 1));
        Inventory inv = Bukkit.createInventory(new AuctionHolder(AuctionHolder.MenuType.MY_ITEMS, page), size, title);

        setupFiller(inv, plugin, "my-items-menu.filler");

        List<Integer> slots = getSlots(plugin.getGuisConfig(), "my-items-menu.item-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < size - 9; i++) slots.add(i);
        }

        int maxItems = slots.size();
        int start = page * maxItems;
        int end = Math.min(start + maxItems, items.size());
        long now = System.currentTimeMillis();

        for (int i = start; i < end; i++) {
            AuctionItem ai = items.get(i);
            ItemStack display = ai.getItem().clone();
            ItemMeta meta = display.getItemMeta();

            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add(NGVKauction.color("&#82919c• &#abb5beЦена: &#dfb15b$" + ai.getPrice()));

            long left = ai.getExpireTime() - now;
            long hours = Math.max(0, left / 3600000L);
            long mins = Math.max(0, (left % 3600000L) / 60000L);
            lore.add(NGVKauction.color("&#82919c• &#abb5beОсталось: &#c45a5a" + hours + "ч. " + mins + "м."));
            lore.add("");
            lore.add(NGVKauction.color("&#c45a5aКлик — Снять лот с продажи"));

            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(slots.get(i - start), display);
        }

        setupButton(inv, plugin, "my-items-menu.buttons.back", null);
        player.openInventory(inv);
        plugin.playCfgSound(player, "open-menu", 1f);
    }

    public static void openConfirmMenu(Player player, NGVKauction plugin, AuctionItem item) {
        int size = plugin.getGuisConfig().getInt("confirm-menu.size", 27);
        Inventory inv = Bukkit.createInventory(new AuctionHolder(AuctionHolder.MenuType.CONFIRM, 0), size, plugin.getCleanGuiString("confirm-menu.title"));

        setupFiller(inv, plugin, "confirm-menu.filler");

        List<Integer> itemSlots = getSlots(plugin.getGuisConfig(), "confirm-menu.item-slots");
        for (int slot : itemSlots) inv.setItem(slot, item.getItem());

        setupMultiSlotButton(inv, plugin, "confirm-menu.buttons.confirm");
        setupMultiSlotButton(inv, plugin, "confirm-menu.buttons.cancel");

        pendingPurchases.put(player.getUniqueId(), item);
        player.openInventory(inv);
        plugin.playCfgSound(player, "click", 1f);
    }

    public static void openStorageMenu(Player player, NGVKauction plugin) {
        List<ItemStack> expired = plugin.getAuctionManager().getExpiredItems(player.getUniqueId());
        int size = plugin.getGuisConfig().getInt("storage-menu.size", 54);
        Inventory inv = Bukkit.createInventory(new AuctionHolder(AuctionHolder.MenuType.STORAGE, 0), size, plugin.getCleanGuiString("storage-menu.title"));

        List<Integer> slots = getSlots(plugin.getGuisConfig(), "storage-menu.item-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < size - 9; i++) slots.add(i);
        }

        for (int i = 0; i < Math.min(slots.size(), expired.size()); i++) {
            inv.setItem(slots.get(i), expired.get(i));
        }

        setupButton(inv, plugin, "storage-menu.buttons.back", null);
        player.openInventory(inv);
        plugin.playCfgSound(player, "click", 1f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof AuctionHolder)) return;

        AuctionHolder holder = (AuctionHolder) topInv.getHolder();
        Inventory clickedInv = event.getClickedInventory();

        if (clickedInv != null && clickedInv.equals(topInv)) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType() == Material.AIR) return;

            int slot = event.getRawSlot();

            switch (holder.getMenuType()) {
                case MAIN -> handleMainClick(player, slot, event.getClick(), holder.getPage());
                case MY_ITEMS -> handleMyItemsClick(player, slot, holder.getPage());
                case CONFIRM -> handleConfirmClick(player, slot);
                case STORAGE -> handleStorageClick(player, slot);
            }
        } else {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }

    private void handleMainClick(Player player, int slot, ClickType clickType, int page) {
        String query = activeSearchQueries.get(player.getUniqueId());

        int categorySlot = plugin.getGuisConfig().getInt("main-menu.buttons.category-filter.slot", 45);
        int sortSlot = plugin.getGuisConfig().getInt("main-menu.buttons.sort-mode.slot", 46);
        int prevSlot = plugin.getGuisConfig().getInt("main-menu.buttons.prev-page.slot", 48);
        int refreshSlot = plugin.getGuisConfig().getInt("main-menu.buttons.refresh.slot", 49);
        int nextSlot = plugin.getGuisConfig().getInt("main-menu.buttons.next-page.slot", 50);
        int myItemsSlot = plugin.getGuisConfig().getInt("main-menu.buttons.my-items.slot", 52);
        int storageSlot = plugin.getGuisConfig().getInt("main-menu.buttons.storage.slot", 53);

        if (slot == categorySlot) {
            AuctionManager.Category current = playerCategories.getOrDefault(player.getUniqueId(), AuctionManager.Category.ALL);
            AuctionManager.Category nextCat = clickType.isRightClick() ? current.previous() : current.next();
            playerCategories.put(player.getUniqueId(), nextCat);
            openMainMenuFiltered(player, plugin, 0, query);
        } else if (slot == sortSlot) {
            AuctionManager.SortMode current = playerSortModes.getOrDefault(player.getUniqueId(), AuctionManager.SortMode.NEWEST);
            AuctionManager.SortMode nextSort = clickType.isRightClick() ? current.previous() : current.next();
            playerSortModes.put(player.getUniqueId(), nextSort);
            openMainMenuFiltered(player, plugin, 0, query);
        } else if (slot == prevSlot) {
            if (page > 0) openMainMenuFiltered(player, plugin, page - 1, query);
        } else if (slot == nextSlot) {
            openMainMenuFiltered(player, plugin, page + 1, query);
        } else if (slot == refreshSlot) {
            openMainMenuFiltered(player, plugin, 0, null);
            player.sendMessage(plugin.getMsg("refreshed"));
        } else if (slot == myItemsSlot) {
            openMyItemsMenu(player, plugin, 0);
        } else if (slot == storageSlot) {
            openStorageMenu(player, plugin);
        } else {
            List<Integer> slots = getSlots(plugin.getGuisConfig(), "main-menu.item-slots");
            if (slots.contains(slot)) {
                int index = (page * slots.size()) + slots.indexOf(slot);
                AuctionManager.Category category = playerCategories.getOrDefault(player.getUniqueId(), AuctionManager.Category.ALL);
                AuctionManager.SortMode sortMode = playerSortModes.getOrDefault(player.getUniqueId(), AuctionManager.SortMode.NEWEST);
                List<AuctionItem> items = plugin.getAuctionManager().getFilteredAndSortedItems(category, sortMode, query);

                if (index >= items.size()) return;
                AuctionItem ai = items.get(index);

                if (ai.getSellerUuid().equals(player.getUniqueId())) {
                    plugin.getAuctionManager().removeItem(ai);
                    giveItem(player, ai.getItem());
                    player.sendMessage(plugin.getMsg("item-removed"));
                    plugin.playCfgSound(player, "receive-item", 1f);
                    openMainMenuFiltered(player, plugin, page, query);
                    return;
                }

                if (clickType == ClickType.SHIFT_LEFT) {
                    processPurchase(player, ai);
                    openMainMenuFiltered(player, plugin, page, query);
                } else {
                    openConfirmMenu(player, plugin, ai);
                }
            }
        }
    }

    private void handleMyItemsClick(Player player, int slot, int page) {
        int backSlot = plugin.getGuisConfig().getInt("my-items-menu.buttons.back.slot", 49);
        if (slot == backSlot) {
            openMainMenu(player, plugin, 0);
        } else {
            List<Integer> slots = getSlots(plugin.getGuisConfig(), "my-items-menu.item-slots");
            if (slots.contains(slot)) {
                int index = (page * slots.size()) + slots.indexOf(slot);
                List<AuctionItem> myItems = plugin.getAuctionManager().getPlayerItems(player.getUniqueId());
                if (index < myItems.size()) {
                    AuctionItem ai = myItems.get(index);
                    plugin.getAuctionManager().removeItem(ai);
                    giveItem(player, ai.getItem());
                    player.sendMessage(plugin.getMsg("item-removed"));
                    plugin.playCfgSound(player, "receive-item", 1f);
                    openMyItemsMenu(player, plugin, page);
                }
            }
        }
    }

    private void handleConfirmClick(Player player, int slot) {
        AuctionItem pending = pendingPurchases.get(player.getUniqueId());
        if (pending == null || !plugin.getAuctionManager().getActiveItems().contains(pending)) {
            player.sendMessage(plugin.getMsg("already-sold"));
            plugin.playCfgSound(player, "error", 1f);
            player.closeInventory();
            return;
        }

        List<Integer> confirmSlots = getSlots(plugin.getGuisConfig(), "confirm-menu.buttons.confirm.slots");
        List<Integer> cancelSlots = getSlots(plugin.getGuisConfig(), "confirm-menu.buttons.cancel.slots");

        if (confirmSlots.contains(slot)) {
            processPurchase(player, pending);
            player.closeInventory();
        } else if (cancelSlots.contains(slot)) {
            openMainMenu(player, plugin, playerPages.getOrDefault(player.getUniqueId(), 0));
        }
    }

    private void handleStorageClick(Player player, int slot) {
        int backSlot = plugin.getGuisConfig().getInt("storage-menu.buttons.back.slot", 49);
        if (slot == backSlot) {
            openMainMenu(player, plugin, 0);
        } else {
            List<Integer> slots = getSlots(plugin.getGuisConfig(), "storage-menu.item-slots");
            if (slots.contains(slot)) {
                int index = slots.indexOf(slot);
                List<ItemStack> expired = plugin.getAuctionManager().getExpiredItems(player.getUniqueId());
                if (index < expired.size()) {
                    ItemStack item = expired.remove(index);
                    giveItem(player, item);
                    player.sendMessage(plugin.getMsg("items-collected"));
                    plugin.playCfgSound(player, "receive-item", 1f);
                    openStorageMenu(player, plugin);
                }
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AuctionHolder) {
            pendingPurchases.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerPages.remove(uuid);
        pendingPurchases.remove(uuid);
        activeSearchQueries.remove(uuid);
        playerCategories.remove(uuid);
        playerSortModes.remove(uuid);
    }

    private void processPurchase(Player buyer, AuctionItem item) {
        double price = item.getPrice();
        if (!plugin.getEconomy().has(buyer, price)) {
            buyer.sendMessage(plugin.getMsg("not-enough-money"));
            plugin.playCfgSound(buyer, "error", 1f);
            return;
        }

        double taxPercent = plugin.getConfig().getDouble("settings.tax-rate", 5.0);
        double taxCut = price * (taxPercent / 100.0);
        double sellerGets = price - taxCut;

        plugin.getEconomy().withdrawPlayer(buyer, price);
        OfflinePlayer seller = Bukkit.getOfflinePlayer(item.getSellerUuid());
        plugin.getEconomy().depositPlayer(seller, sellerGets);

        plugin.getAuctionManager().removeItem(item);
        giveItem(buyer, item.getItem());

        buyer.sendMessage(plugin.getMsg("buy-success").replace("%price%", String.valueOf(price)));
        plugin.playCfgSound(buyer, "buy-success", 1f);

        if (seller.isOnline() && seller.getPlayer() != null) {
            Player s = seller.getPlayer();
            String sMsg = plugin.getMsg("seller-notification")
                    .replace("%buyer%", buyer.getName())
                    .replace("%money%", String.valueOf(sellerGets));
            s.sendMessage(sMsg);
            plugin.playCfgSound(s, "receive-money", 1f);
        }
    }

    private void giveItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> drop = player.getInventory().addItem(item);
        if (!drop.isEmpty()) {
            for (ItemStack it : drop.values()) player.getWorld().dropItem(player.getLocation(), it);
            player.sendMessage(plugin.getMsg("inventory-full"));
        }
    }

    private static void setupFiller(Inventory inv, NGVKauction plugin, String path) {
        if (!plugin.getGuisConfig().contains(path)) return;
        String mat = plugin.getGuisConfig().getString(path + ".material", "GRAY_STAINED_GLASS_PANE");
        int customModelData = plugin.getGuisConfig().getInt(path + ".custom-model-data", -1);
        String name = NGVKauction.color(plugin.getGuisConfig().getString(path + ".name", " "));

        ItemStack filler = createCustomItem(mat, customModelData, name, null);
        List<Integer> slots = getSlots(plugin.getGuisConfig(), path + ".slots");
        for (int s : slots) inv.setItem(s, filler);
    }

    private static void setupButton(Inventory inv, NGVKauction plugin, String path, List<String> overrideLore) {
        int slot = plugin.getGuisConfig().getInt(path + ".slot", 0);
        String mat = plugin.getGuisConfig().getString(path + ".material", "STONE");
        int customModelData = plugin.getGuisConfig().getInt(path + ".custom-model-data", -1);
        String name = plugin.getCleanGuiString(path + ".name");

        List<String> lore = overrideLore != null ? overrideLore : colorList(plugin.getGuisConfig().getStringList(path + ".lore"));
        inv.setItem(slot, createCustomItem(mat, customModelData, name, lore));
    }

    private static void setupMultiSlotButton(Inventory inv, NGVKauction plugin, String path) {
        List<Integer> slots = getSlots(plugin.getGuisConfig(), path + ".slots");
        String mat = plugin.getGuisConfig().getString(path + ".material", "STONE");
        int customModelData = plugin.getGuisConfig().getInt(path + ".custom-model-data", -1);
        String name = plugin.getCleanGuiString(path + ".name");
        List<String> rawLore = plugin.getGuisConfig().getStringList(path + ".lore");
        List<String> lore = colorList(rawLore);

        ItemStack item = createCustomItem(mat, customModelData, name, lore);
        for (int slot : slots) inv.setItem(slot, item);
    }

    public static List<Integer> getSlots(FileConfiguration config, String path) {
        if (config.isList(path)) {
            return config.getIntegerList(path);
        } else if (config.contains(path)) {
            return Collections.singletonList(config.getInt(path));
        }
        return new ArrayList<>();
    }

    public static ItemStack createCustomItem(String materialKey, String displayName, List<String> lore) {
        return createCustomItem(materialKey, -1, displayName, lore);
    }

    public static ItemStack createCustomItem(String materialKey, int customModelData, String displayName, List<String> lore) {
        ItemStack item = null;

        if (materialKey != null && !materialKey.isEmpty()) {
            String raw = materialKey.trim();
            String lower = raw.toLowerCase();

            if (lower.startsWith("oraxen-") || lower.startsWith("oraxen:")) {
                String id = raw.substring(raw.contains("-") ? raw.indexOf('-') + 1 : raw.indexOf(':') + 1);
                item = getOraxenItem(id);
            } else if (lower.startsWith("itemsadder-") || lower.startsWith("itemsadder:")) {
                String id = raw.substring(raw.contains("-") ? raw.indexOf('-') + 1 : raw.indexOf(':') + 1);
                item = getItemsAdderItem(id);
            } else {
                try {
                    Material mat = Material.valueOf(raw.toUpperCase());
                    item = new ItemStack(mat);
                } catch (IllegalArgumentException e) {
                    item = getOraxenItem(raw);
                    if (item == null) item = getItemsAdderItem(raw);
                }
            }
        }

        if (item == null) {
            item = new ItemStack(Material.PAPER);
        } else {
            item = item.clone();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            if (displayName != null) {
                meta.setDisplayName(displayName);
            }
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void initHooks() {
        if (hooksInitialized) return;
        hooksInitialized = true;

        if (Bukkit.getPluginManager().isPluginEnabled("Oraxen") || Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            String[] classNames = {
                    "io.thierrys.oraxen.api.OraxenItems",
                    "io.thierrys.oraxen.items.OraxenItems",
                    "io.thierrys.oraxen.OraxenItems",
                    "com.nexomc.nexo.api.NexoItems"
            };

            for (String className : classNames) {
                try {
                    Class<?> clazz = Class.forName(className);

                    try {
                        oraxenGetItemMethod = clazz.getMethod("getItemById", String.class);
                        if (oraxenGetItemMethod != null) break;
                    } catch (NoSuchMethodException ignored) {}

                    try {
                        oraxenGetOptionalMethod = clazz.getMethod("getOptionalItemById", String.class);
                        if (oraxenGetOptionalMethod != null) break;
                    } catch (NoSuchMethodException ignored) {}

                } catch (ClassNotFoundException ignored) {}
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            try {
                Class<?> iaClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                itemsAdderGetInstanceMethod = iaClass.getMethod("getInstance", String.class);
                itemsAdderGetItemMethod = iaClass.getMethod("getItemStack");
            } catch (Exception ignored) {}
        }
    }

    public static ItemStack getOraxenItem(String originalId) {
        initHooks();

        List<String> candidateIds = new ArrayList<>();
        candidateIds.add(originalId);
        candidateIds.add(originalId.toLowerCase());
        candidateIds.add(originalId.replace("-", "_"));
        candidateIds.add(originalId.replace("_", "-"));

        for (String id : candidateIds) {
            if (oraxenGetItemMethod != null) {
                try {
                    Object res = oraxenGetItemMethod.invoke(null, id);
                    ItemStack stack = convertToItemStack(res);
                    if (stack != null) return stack;
                } catch (Exception ignored) {}
            }

            if (oraxenGetOptionalMethod != null) {
                try {
                    Object res = oraxenGetOptionalMethod.invoke(null, id);
                    if (res instanceof Optional<?>) {
                        Optional<?> opt = (Optional<?>) res;
                        if (opt.isPresent()) {
                            ItemStack stack = convertToItemStack(opt.get());
                            if (stack != null) return stack;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public static ItemStack getItemsAdderItem(String id) {
        initHooks();

        if (itemsAdderGetInstanceMethod != null && itemsAdderGetItemMethod != null) {
            try {
                Object stack = itemsAdderGetInstanceMethod.invoke(null, id);
                if (stack != null) {
                    Object item = itemsAdderGetItemMethod.invoke(stack);
                    if (item instanceof ItemStack) {
                        return ((ItemStack) item).clone();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static ItemStack convertToItemStack(Object obj) {
        if (obj == null) return null;
        if (obj instanceof ItemStack) return ((ItemStack) obj).clone();

        Class<?> clazz = obj.getClass();
        String[] buildMethods = {"build", "getItemStack", "toItemStack", "get", "generateItemStack"};
        for (String methodName : buildMethods) {
            try {
                Method m = clazz.getMethod(methodName);
                Object res = m.invoke(obj);
                if (res instanceof ItemStack) {
                    return ((ItemStack) res).clone();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static List<String> colorList(List<String> list) {
        if (list == null) return null;
        List<String> colored = new ArrayList<>();
        for (String s : list) colored.add(NGVKauction.color(s));
        return colored;
    }
}