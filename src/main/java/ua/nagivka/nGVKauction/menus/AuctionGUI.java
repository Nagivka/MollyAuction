package ua.nagivka.nGVKauction.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.nagivka.nGVKauction.NGVKauction;
import ua.nagivka.nGVKauction.models.AuctionItem;
import ua.nagivka.nGVKauction.models.Category;
import ua.nagivka.nGVKauction.models.SortMode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class AuctionGUI {

    private static boolean hooksInitialized = false;
    private static Method oraxenGetItemMethod = null;
    private static Method oraxenGetOptionalMethod = null;
    private static Method itemsAdderGetInstanceMethod = null;
    private static Method itemsAdderGetItemMethod = null;

    private AuctionGUI() {}

    public static void openMainMenu(Player player, NGVKauction plugin, int page) {
        openMainMenuFiltered(player, plugin, page, Category.ALL, SortMode.NEWEST, null);
    }

    public static void openMainMenuFiltered(Player player, NGVKauction plugin, int page, Category category, SortMode sortMode, String searchQuery) {
        List<AuctionItem> items = plugin.getAuctionManager().getFilteredAndSortedItems(category, sortMode, searchQuery);

        int size = plugin.getGuisConfig().getInt("main-menu.size", 54);
        String title = plugin.getCleanGuiString("main-menu.title").replace("%page%", String.valueOf(page + 1));
        AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.MAIN, page, category, sortMode, searchQuery, null);
        Inventory inv = Bukkit.createInventory(holder, size, title);

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
        for (Category c : Category.values()) {
            if (c == category) catLore.add(NGVKauction.color(" &#cfa151• &#eceff2" + c.getName()));
            else catLore.add(NGVKauction.color(" &#82919c• &#abb5be" + c.getName()));
        }
        setupButton(inv, plugin, "main-menu.buttons.category-filter", catLore);

        List<String> sortLore = new ArrayList<>();
        sortLore.add(NGVKauction.color("&#cfa151Сортировка"));
        for (SortMode s : SortMode.values()) {
            if (s == sortMode) sortLore.add(NGVKauction.color(" &#cfa151• &#eceff2" + s.getName()));
            else sortLore.add(NGVKauction.color(" &#82919c• &#abb5be" + s.getName()));
        }
        setupButton(inv, plugin, "main-menu.buttons.sort-mode", sortLore);

        if (page > 0) setupButton(inv, plugin, "main-menu.buttons.prev-page", null);
        setupButton(inv, plugin, "main-menu.buttons.refresh", null);
        if (end < items.size()) setupButton(inv, plugin, "main-menu.buttons.next-page", null);
        setupButton(inv, plugin, "main-menu.buttons.my-items", null);
        setupButton(inv, plugin, "main-menu.buttons.storage", null);

        player.openInventory(inv);
        plugin.playCfgSound(player, "open-menu", 1f);
    }

    public static void openMyItemsMenu(Player player, NGVKauction plugin, int page) {
        List<AuctionItem> items = plugin.getAuctionManager().getPlayerItems(player.getUniqueId());
        int size = plugin.getGuisConfig().getInt("my-items-menu.size", 54);
        String title = plugin.getCleanGuiString("my-items-menu.title").replace("%page%", String.valueOf(page + 1));
        AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.MY_ITEMS, page);
        Inventory inv = Bukkit.createInventory(holder, size, title);

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
        AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.CONFIRM, 0, Category.ALL, SortMode.NEWEST, null, item);
        Inventory inv = Bukkit.createInventory(holder, size, plugin.getCleanGuiString("confirm-menu.title"));

        setupFiller(inv, plugin, "confirm-menu.filler");

        List<Integer> itemSlots = getSlots(plugin.getGuisConfig(), "confirm-menu.item-slots");
        for (int slot : itemSlots) {
            inv.setItem(slot, item.getItem());
        }

        setupMultiSlotButton(inv, plugin, "confirm-menu.buttons.confirm");
        setupMultiSlotButton(inv, plugin, "confirm-menu.buttons.cancel");

        player.openInventory(inv);
        plugin.playCfgSound(player, "click", 1f);
    }

    public static void openStorageMenu(Player player, NGVKauction plugin) {
        List<ItemStack> expired = plugin.getAuctionManager().getExpiredItems(player.getUniqueId());
        int size = plugin.getGuisConfig().getInt("storage-menu.size", 54);
        AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.STORAGE, 0);
        Inventory inv = Bukkit.createInventory(holder, size, plugin.getCleanGuiString("storage-menu.title"));

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

    private static void setupFiller(Inventory inv, NGVKauction plugin, String path) {
        if (!plugin.getGuisConfig().contains(path)) return;
        String mat = plugin.getGuisConfig().getString(path + ".material", "GRAY_STAINED_GLASS_PANE");
        int customModelData = plugin.getGuisConfig().getInt(path + ".custom-model-data", -1);
        String name = NGVKauction.color(plugin.getGuisConfig().getString(path + ".name", " "));

        ItemStack filler = createCustomItem(mat, customModelData, name, null);
        List<Integer> slots = getSlots(plugin.getGuisConfig(), path + ".slots");
        for (int s : slots) {
            inv.setItem(s, filler);
        }
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
        for (int slot : slots) {
            inv.setItem(slot, item);
        }
    }

    public static List<Integer> getSlots(FileConfiguration config, String path) {
        if (config.isList(path)) {
            return config.getIntegerList(path);
        } else if (config.contains(path)) {
            return Collections.singletonList(config.getInt(path));
        }
        return new ArrayList<>();
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
                    if (res instanceof Optional<?> opt && opt.isPresent()) {
                        ItemStack stack = convertToItemStack(opt.get());
                        if (stack != null) return stack;
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
                    if (item instanceof ItemStack is) {
                        return is.clone();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static ItemStack convertToItemStack(Object obj) {
        if (obj == null) return null;
        if (obj instanceof ItemStack is) return is.clone();

        Class<?> clazz = obj.getClass();
        String[] buildMethods = {"build", "getItemStack", "toItemStack", "get", "generateItemStack"};
        for (String methodName : buildMethods) {
            try {
                Method m = clazz.getMethod(methodName);
                Object res = m.invoke(obj);
                if (res instanceof ItemStack is) {
                    return is.clone();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static List<String> colorList(List<String> list) {
        if (list == null) return null;
        List<String> colored = new ArrayList<>();
        for (String s : list) {
            colored.add(NGVKauction.color(s));
        }
        return colored;
    }
}