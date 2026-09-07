package ua.nagivka.mollyauction.menus;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.nagivka.mollyauction.MollyAuction;
import ua.nagivka.mollyauction.models.AuctionItem;
import ua.nagivka.mollyauction.models.Category;
import ua.nagivka.mollyauction.models.ExpiredItem;
import ua.nagivka.mollyauction.models.SortMode;
import ua.nagivka.mollyauction.models.TransactionRecord;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public final class AuctionGUI {

    private static boolean hooksInitialized = false;
    private static Method oraxenGetItemMethod = null;
    private static Method oraxenGetOptionalMethod = null;
    private static Method itemsAdderGetInstanceMethod = null;
    private static Method itemsAdderGetItemMethod = null;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm");

    private AuctionGUI() {}

    public static void openMainMenu(Player player, MollyAuction plugin, int page) {
        openMainMenuFiltered(player, plugin, page, Category.ALL, SortMode.NEWEST, null);
    }

    public static void openMainMenuFiltered(Player player, MollyAuction plugin, int page, Category category, SortMode sortMode, String searchQuery) {
        List<AuctionItem> items = plugin.getAuctionManager().getFilteredAndSortedItems(category, sortMode, searchQuery);

        int size = plugin.getGuisConfig().getInt("main-menu.size", 54);
        List<Integer> slots = getSlots(plugin.getGuisConfig(), "main-menu.item-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < size - 9; i++) slots.add(i);
        }

        int maxItems = Math.max(1, slots.size());
        int maxPage = Math.max(0, (int) Math.ceil((double) items.size() / maxItems) - 1);
        if (page > maxPage) page = maxPage;
        if (page < 0) page = 0;

        String title = plugin.getCleanGuiString(player, "main-menu.title").replace("%page%", String.valueOf(page + 1));
        AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.MAIN, page, category, sortMode, searchQuery, null, maxPage);
        Inventory inv = Bukkit.createInventory(holder, size, title);

        setupFiller(player, inv, plugin, "main-menu.filler");

        int start = page * maxItems;
        int end = Math.min(start + maxItems, items.size());
        long now = System.currentTimeMillis();

        boolean isAdmin = player.hasPermission("mollyauction.admin") || player.hasPermission("ngvkauctions.admin");

        for (int i = start; i < end; i++) {
            AuctionItem ai = items.get(i);
            ItemStack display = ai.getItem().clone();
            ItemMeta meta = display.getItemMeta();

            List<String> lore = (meta != null && meta.hasLore()) ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(MollyAuction.color(player, "&#82919c• &#abb5beПродавец: &#eceff2" + ai.getSellerName()));
            lore.add(MollyAuction.color(player, "&#82919c• &#abb5beЦена: &#dfb15b$" + ai.getPrice()));

            if (ai.getItem().getAmount() > 1) {
                double unitPrice = Math.round((ai.getPrice() / ai.getItem().getAmount()) * 100.0) / 100.0;
                lore.add(MollyAuction.color(player, "&#82919c• &#abb5beЦена за шт: &#dfb15b$" + unitPrice));
            }

            long left = ai.getExpireTime() - now;
            long hours = Math.max(0, left / 3600000L);
            long mins = Math.max(0, (left % 3600000L) / 60000L);
            lore.add(MollyAuction.color(player, "&#82919c• &#abb5beОсталось: &#c45a5a" + hours + "ч. " + mins + "м."));
            lore.add("");

            if (ai.getSellerUuid().equals(player.getUniqueId())) {
                long expireHours = plugin.getConfig().getLong("settings.expire-hours", 48);
                lore.add(MollyAuction.color(player, "&#668f64ПКМ — Продлить срок на " + expireHours + "ч."));
                lore.add(MollyAuction.color(player, "&#c45a5aЛКМ — Снять с продажи"));
            } else {
                lore.add(MollyAuction.color(player, "&#a67fa3ЛКМ — Купить (Подтверждение)"));
                lore.add(MollyAuction.color(player, "&#c45a5aShift + ЛКМ — Мгновенная покупка"));
                if (ai.getItem().getAmount() > 1) {
                    lore.add(MollyAuction.color(player, "&#668f64ПКМ — Купить часть товара"));
                }
                if (isAdmin) {
                    lore.add(MollyAuction.color(player, "&#c45a5aShift + ПКМ — [Админ] Снять лот"));
                }
            }

            if (meta != null) {
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(slots.get(i - start), display);
        }

        List<String> catLore = new ArrayList<>();
        catLore.add(MollyAuction.color(player, "&#cfa151Категории"));
        for (Category c : Category.values()) {
            if (c == category) catLore.add(MollyAuction.color(player, " &#cfa151• &#eceff2" + c.getName()));
            else catLore.add(MollyAuction.color(player, " &#82919c• &#abb5be" + c.getName()));
        }
        setupButton(player, inv, plugin, "main-menu.buttons.category-filter", catLore);

        List<String> sortLore = new ArrayList<>();
        sortLore.add(MollyAuction.color(player, "&#cfa151Сортировка"));
        for (SortMode s : SortMode.values()) {
            if (s == sortMode) sortLore.add(MollyAuction.color(player, " &#cfa151• &#eceff2" + s.getName()));
            else sortLore.add(MollyAuction.color(player, " &#82919c• &#abb5be" + s.getName()));
        }
        setupButton(player, inv, plugin, "main-menu.buttons.sort-mode", sortLore);

        setupButton(player, inv, plugin, "main-menu.buttons.history", null);

        if (page > 0) {
            setupButton(player, inv, plugin, "main-menu.buttons.prev-page", null);
        }
        setupButton(player, inv, plugin, "main-menu.buttons.refresh", null);
        if (page < maxPage) {
            setupButton(player, inv, plugin, "main-menu.buttons.next-page", null);
        }
        setupButton(player, inv, plugin, "main-menu.buttons.my-items", null);
        setupButton(player, inv, plugin, "main-menu.buttons.storage", null);

        player.openInventory(inv);
        plugin.playCfgSound(player, "open-menu", 1f);
    }

    public static void refreshMainMenuInPlace(Player player, MollyAuction plugin, Inventory inv, AuctionGUIHolder holder) {
        List<AuctionItem> items = plugin.getAuctionManager().getFilteredAndSortedItems(holder.getCategory(), holder.getSortMode(), holder.getSearchQuery());
        List<Integer> slots = getSlots(plugin.getGuisConfig(), "main-menu.item-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < inv.getSize() - 9; i++) slots.add(i);
        }

        int maxItems = Math.max(1, slots.size());
        int maxPage = Math.max(0, (int) Math.ceil((double) items.size() / maxItems) - 1);
        int page = holder.getPage();
        if (page > maxPage) page = maxPage;
        if (page < 0) page = 0;

        int start = page * maxItems;
        int end = Math.min(start + maxItems, items.size());
        long now = System.currentTimeMillis();
        boolean isAdmin = player.hasPermission("mollyauction.admin") || player.hasPermission("ngvkauctions.admin");
        long expireHours = plugin.getConfig().getLong("settings.expire-hours", 48);

        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            int itemIndex = start + i;
            if (itemIndex < end) {
                AuctionItem ai = items.get(itemIndex);
                ItemStack display = ai.getItem().clone();
                ItemMeta meta = display.getItemMeta();

                List<String> lore = (meta != null && meta.hasLore()) ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(MollyAuction.color(player, "&#82919c• &#abb5beПродавец: &#eceff2" + ai.getSellerName()));
                lore.add(MollyAuction.color(player, "&#82919c• &#abb5beЦена: &#dfb15b$" + ai.getPrice()));

                if (ai.getItem().getAmount() > 1) {
                    double unitPrice = Math.round((ai.getPrice() / ai.getItem().getAmount()) * 100.0) / 100.0;
                    lore.add(MollyAuction.color(player, "&#82919c• &#abb5beЦена за шт: &#dfb15b$" + unitPrice));
                }

                long left = ai.getExpireTime() - now;
                long hours = Math.max(0, left / 3600000L);
                long mins = Math.max(0, (left % 3600000L) / 60000L);
                lore.add(MollyAuction.color(player, "&#82919c• &#abb5beОсталось: &#c45a5a" + hours + "ч. " + mins + "м."));
                lore.add("");

                if (ai.getSellerUuid().equals(player.getUniqueId())) {
                    lore.add(MollyAuction.color(player, "&#668f64ПКМ — Продлить срок на " + expireHours + "ч."));
                    lore.add(MollyAuction.color(player, "&#c45a5aЛКМ — Снять с продажи"));
                } else {
                    lore.add(MollyAuction.color(player, "&#a67fa3ЛКМ — Купить (Подтверждение)"));
                    lore.add(MollyAuction.color(player, "&#c45a5aShift + ЛКМ — Мгновенная покупка"));
                    if (ai.getItem().getAmount() > 1) {
                        lore.add(MollyAuction.color(player, "&#668f64ПКМ — Купить часть товара"));
                    }
                    if (isAdmin) {
                        lore.add(MollyAuction.color(player, "&#c45a5aShift + ПКМ — [Админ] Снять лот"));
                    }
                }

                if (meta != null) {
                    meta.setLore(lore);
                    display.setItemMeta(meta);
                }
                inv.setItem(slot, display);
            } else {
                inv.setItem(slot, null);
            }
        }

        if (page > 0) {
            setupButton(player, inv, plugin, "main-menu.buttons.prev-page", null);
        } else {
            int prevSlot = plugin.getGuisConfig().getInt("main-menu.buttons.prev-page.slot", 48);
            inv.setItem(prevSlot, null);
        }

        if (page < maxPage) {
            setupButton(player, inv, plugin, "main-menu.buttons.next-page", null);
        } else {
            int nextSlot = plugin.getGuisConfig().getInt("main-menu.buttons.next-page.slot", 50);
            inv.setItem(nextSlot, null);
        }
    }

    public static void openMyItemsMenu(Player player, MollyAuction plugin, int page) {
        List<AuctionItem> items = plugin.getAuctionManager().getPlayerItems(player.getUniqueId());
        int size = plugin.getGuisConfig().getInt("my-items-menu.size", 54);
        List<Integer> slots = getSlots(plugin.getGuisConfig(), "my-items-menu.item-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < size - 9; i++) slots.add(i);
        }

        int maxItems = Math.max(1, slots.size());
        int maxPage = Math.max(0, (int) Math.ceil((double) items.size() / maxItems) - 1);
        if (page > maxPage) page = maxPage;
        if (page < 0) page = 0;

        String title = plugin.getCleanGuiString(player, "my-items-menu.title").replace("%page%", String.valueOf(page + 1));
        AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.MY_ITEMS, page, maxPage);
        Inventory inv = Bukkit.createInventory(holder, size, title);

        setupFiller(player, inv, plugin, "my-items-menu.filler");

        int start = page * maxItems;
        int end = Math.min(start + maxItems, items.size());
        long now = System.currentTimeMillis();
        long expireHours = plugin.getConfig().getLong("settings.expire-hours", 48);

        for (int i = start; i < end; i++) {
            AuctionItem ai = items.get(i);
            ItemStack display = ai.getItem().clone();
            ItemMeta meta = display.getItemMeta();

            List<String> lore = (meta != null && meta.hasLore()) ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(MollyAuction.color(player, "&#82919c• &#abb5beЦена: &#dfb15b$" + ai.getPrice()));

            long left = ai.getExpireTime() - now;
            long hours = Math.max(0, left / 3600000L);
            long mins = Math.max(0, (left % 3600000L) / 60000L);
            lore.add(MollyAuction.color(player, "&#82919c• &#abb5beОсталось: &#c45a5a" + hours + "ч. " + mins + "м."));
            lore.add("");
            lore.add(MollyAuction.color(player, "&#668f64ЛКМ — Продлить срок на " + expireHours + "ч."));
            lore.add(MollyAuction.color(player, "&#c45a5aПКМ — Снять лот с продажи"));

            if (meta != null) {
                meta.setLore(lore);
                display.setItemMeta(meta);
            }
            inv.setItem(slots.get(i - start), display);
        }

        if (page > 0) {
            setupButton(player, inv, plugin, "my-items-menu.buttons.prev-page", null);
        }
        setupButton(player, inv, plugin, "my-items-menu.buttons.back", null);
        if (page < maxPage) {
            setupButton(player, inv, plugin, "my-items-menu.buttons.next-page", null);
        }

        player.openInventory(inv);
        plugin.playCfgSound(player, "open-menu", 1f);
    }

    public static void refreshMyItemsMenuInPlace(Player player, MollyAuction plugin, Inventory inv, AuctionGUIHolder holder) {
        List<AuctionItem> items = plugin.getAuctionManager().getPlayerItems(player.getUniqueId());
        List<Integer> slots = getSlots(plugin.getGuisConfig(), "my-items-menu.item-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < inv.getSize() - 9; i++) slots.add(i);
        }

        int maxItems = Math.max(1, slots.size());
        int maxPage = Math.max(0, (int) Math.ceil((double) items.size() / maxItems) - 1);
        int page = holder.getPage();
        if (page > maxPage) page = maxPage;
        if (page < 0) page = 0;

        int start = page * maxItems;
        int end = Math.min(start + maxItems, items.size());
        long now = System.currentTimeMillis();
        long expireHours = plugin.getConfig().getLong("settings.expire-hours", 48);

        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            int itemIndex = start + i;
            if (itemIndex < end) {
                AuctionItem ai = items.get(itemIndex);
                ItemStack display = ai.getItem().clone();
                ItemMeta meta = display.getItemMeta();

                List<String> lore = (meta != null && meta.hasLore()) ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(MollyAuction.color(player, "&#82919c• &#abb5beЦена: &#dfb15b$" + ai.getPrice()));

                long left = ai.getExpireTime() - now;
                long hours = Math.max(0, left / 3600000L);
                long mins = Math.max(0, (left % 3600000L) / 60000L);
                lore.add(MollyAuction.color(player, "&#82919c• &#abb5beОсталось: &#c45a5a" + hours + "ч. " + mins + "м."));
                lore.add("");
                lore.add(MollyAuction.color(player, "&#668f64ЛКМ — Продлить срок на " + expireHours + "ч."));
                lore.add(MollyAuction.color(player, "&#c45a5aПКМ — Снять лот с продажи"));

                if (meta != null) {
                    meta.setLore(lore);
                    display.setItemMeta(meta);
                }
                inv.setItem(slot, display);
            } else {
                inv.setItem(slot, null);
            }
        }

        if (page > 0) {
            setupButton(player, inv, plugin, "my-items-menu.buttons.prev-page", null);
        } else {
            int prevSlot = plugin.getGuisConfig().getInt("my-items-menu.buttons.prev-page.slot", 48);
            inv.setItem(prevSlot, null);
        }

        if (page < maxPage) {
            setupButton(player, inv, plugin, "my-items-menu.buttons.next-page", null);
        } else {
            int nextSlot = plugin.getGuisConfig().getInt("my-items-menu.buttons.next-page.slot", 50);
            inv.setItem(nextSlot, null);
        }
    }

    public static void openConfirmMenu(Player player, MollyAuction plugin, AuctionItem item) {
        int size = plugin.getGuisConfig().getInt("confirm-menu.size", 27);
        AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.CONFIRM, 0, Category.ALL, SortMode.NEWEST, null, item, 0);
        Inventory inv = Bukkit.createInventory(holder, size, plugin.getCleanGuiString(player, "confirm-menu.title"));

        setupFiller(player, inv, plugin, "confirm-menu.filler");

        List<Integer> itemSlots = getSlots(plugin.getGuisConfig(), "confirm-menu.item-slots");
        for (int slot : itemSlots) {
            inv.setItem(slot, item.getItem());
        }

        setupMultiSlotButton(player, inv, plugin, "confirm-menu.buttons.confirm");
        setupMultiSlotButton(player, inv, plugin, "confirm-menu.buttons.cancel");

        player.openInventory(inv);
        plugin.playCfgSound(player, "click", 1f);
    }

    public static void openBuyPartialMenu(Player player, MollyAuction plugin, AuctionItem item, int selectedAmount) {
        int maxAmount = item.getItem().getAmount();
        if (selectedAmount > maxAmount) selectedAmount = maxAmount;
        if (selectedAmount < 1) selectedAmount = 1;

        int size = plugin.getGuisConfig().getInt("buy-partial-menu.size", 27);
        AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.PARTIAL_BUY, item, selectedAmount);
        Inventory inv = Bukkit.createInventory(holder, size, plugin.getCleanGuiString(player, "buy-partial-menu.title"));

        setupFiller(player, inv, plugin, "buy-partial-menu.filler");

        double unitPrice = Math.round((item.getPrice() / item.getItem().getAmount()) * 100.0) / 100.0;
        double totalPrice = Math.round((unitPrice * selectedAmount) * 100.0) / 100.0;

        // Центральный предмет
        ItemStack display = item.getItem().clone();
        display.setAmount(selectedAmount);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(MollyAuction.color(player, "&#82919c• &#abb5beВыбрано: &#eceff2" + selectedAmount + " / " + maxAmount + " шт."));
            lore.add(MollyAuction.color(player, "&#82919c• &#abb5beЦена за шт: &#dfb15b$" + unitPrice));
            lore.add(MollyAuction.color(player, "&#82919c• &#abb5beИтоговая цена: &#dfb15b$" + totalPrice));
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        inv.setItem(13, display);

        setupButton(player, inv, plugin, "buy-partial-menu.buttons.minus-ten", null);
        setupButton(player, inv, plugin, "buy-partial-menu.buttons.minus-one", null);
        setupButton(player, inv, plugin, "buy-partial-menu.buttons.plus-one", null);
        setupButton(player, inv, plugin, "buy-partial-menu.buttons.plus-ten", null);

        // Кнопка подтверждения с плейсхолдерами
        List<String> confirmLore = colorList(player, plugin.getGuisConfig().getStringList("buy-partial-menu.buttons.confirm.lore"));
        if (confirmLore != null) {
            confirmLore = confirmLore.stream()
                    .map(s -> s.replace("%unit_price%", String.valueOf(unitPrice)).replace("%total_price%", String.valueOf(totalPrice)))
                    .toList();
        }
        setupButton(player, inv, plugin, "buy-partial-menu.buttons.confirm", confirmLore);
        setupButton(player, inv, plugin, "buy-partial-menu.buttons.cancel", null);

        player.openInventory(inv);
        plugin.playCfgSound(player, "click", 1f);
    }

    public static void openStorageMenu(Player player, MollyAuction plugin) {
        openStorageMenu(player, plugin, 0);
    }

    public static void openStorageMenu(Player player, MollyAuction plugin, int page) {
        List<ExpiredItem> expired = plugin.getAuctionManager().getExpiredItems(player.getUniqueId());
        int size = plugin.getGuisConfig().getInt("storage-menu.size", 54);
        List<Integer> slots = getSlots(plugin.getGuisConfig(), "storage-menu.item-slots");
        if (slots.isEmpty()) {
            for (int i = 0; i < size - 9; i++) slots.add(i);
        }

        int maxItems = Math.max(1, slots.size());
        int maxPage = Math.max(0, (int) Math.ceil((double) expired.size() / maxItems) - 1);
        if (page > maxPage) page = maxPage;
        if (page < 0) page = 0;

        String title = plugin.getCleanGuiString(player, "storage-menu.title").replace("%page%", String.valueOf(page + 1));
        AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.STORAGE, page, maxPage);
        Inventory inv = Bukkit.createInventory(holder, size, title);

        setupFiller(player, inv, plugin, "storage-menu.filler");

        int start = page * maxItems;
        int end = Math.min(start + maxItems, expired.size());

        for (int i = start; i < end; i++) {
            inv.setItem(slots.get(i - start), expired.get(i).getItem());
        }

        setupButton(player, inv, plugin, "storage-menu.buttons.collect-all", null);

        if (page > 0) {
            setupButton(player, inv, plugin, "storage-menu.buttons.prev-page", null);
        }
        setupButton(player, inv, plugin, "storage-menu.buttons.back", null);
        if (page < maxPage) {
            setupButton(player, inv, plugin, "storage-menu.buttons.next-page", null);
        }

        player.openInventory(inv);
        plugin.playCfgSound(player, "click", 1f);
    }

    public static void openHistoryMenu(Player player, MollyAuction plugin, int page) {
        plugin.getDatabaseManager().loadPlayerHistoryAsync(player.getUniqueId()).thenAccept(history -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                int size = plugin.getGuisConfig().getInt("history-menu.size", 54);
                List<Integer> slots = getSlots(plugin.getGuisConfig(), "history-menu.item-slots");
                if (slots.isEmpty()) {
                    for (int i = 0; i < size - 9; i++) slots.add(i);
                }

                int maxItems = Math.max(1, slots.size());
                int maxPage = Math.max(0, (int) Math.ceil((double) history.size() / maxItems) - 1);
                int targetPage = Math.max(0, Math.min(page, maxPage));

                String title = plugin.getCleanGuiString(player, "history-menu.title").replace("%page%", String.valueOf(targetPage + 1));
                AuctionGUIHolder holder = new AuctionGUIHolder(AuctionGUIHolder.MenuType.HISTORY, targetPage, maxPage);
                Inventory inv = Bukkit.createInventory(holder, size, title);

                setupFiller(player, inv, plugin, "history-menu.filler");

                int start = targetPage * maxItems;
                int end = Math.min(start + maxItems, history.size());

                for (int i = start; i < end; i++) {
                    TransactionRecord record = history.get(i);
                    ItemStack display = record.getItem().clone();
                    display.setAmount(record.getAmount());
                    ItemMeta meta = display.getItemMeta();

                    List<String> lore = (meta != null && meta.hasLore()) ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                    lore.add("");

                    boolean isBuyer = record.getBuyerUuid().equals(player.getUniqueId());
                    if (isBuyer) {
                        lore.add(MollyAuction.color(player, "&#82919c• &#abb5beТип сделки: &#c45a5aПокупка"));
                        lore.add(MollyAuction.color(player, "&#82919c• &#abb5beПродавец: &#eceff2" + record.getSellerName()));
                    } else {
                        lore.add(MollyAuction.color(player, "&#82919c• &#abb5beТип сделки: &#668f64Продажа"));
                        lore.add(MollyAuction.color(player, "&#82919c• &#abb5beПокупатель: &#eceff2" + record.getBuyerName()));
                    }

                    lore.add(MollyAuction.color(player, "&#82919c• &#abb5beКоличество: &#eceff2" + record.getAmount() + " шт."));
                    lore.add(MollyAuction.color(player, "&#82919c• &#abb5beСумма сделки: &#dfb15b$" + record.getPrice()));
                    lore.add(MollyAuction.color(player, "&#82919c• &#abb5beДата: &#eceff2" + DATE_FORMAT.format(new Date(record.getTimestamp()))));

                    if (meta != null) {
                        meta.setLore(lore);
                        display.setItemMeta(meta);
                    }
                    inv.setItem(slots.get(i - start), display);
                }

                if (targetPage > 0) {
                    setupButton(player, inv, plugin, "history-menu.buttons.prev-page", null);
                }
                setupButton(player, inv, plugin, "history-menu.buttons.back", null);
                if (targetPage < maxPage) {
                    setupButton(player, inv, plugin, "history-menu.buttons.next-page", null);
                }

                player.openInventory(inv);
                plugin.playCfgSound(player, "click", 1f);
            });
        });
    }

    private static void setupFiller(Player player, Inventory inv, MollyAuction plugin, String path) {
        if (!plugin.getGuisConfig().contains(path)) return;
        String mat = plugin.getGuisConfig().getString(path + ".material", "GRAY_STAINED_GLASS_PANE");
        int customModelData = plugin.getGuisConfig().getInt(path + ".custom-model-data", -1);
        String name = MollyAuction.color(player, plugin.getGuisConfig().getString(path + ".name", " "));

        ItemStack filler = createCustomItem(mat, customModelData, name, null);
        List<Integer> slots = getSlots(plugin.getGuisConfig(), path + ".slots");
        for (int s : slots) {
            if (s >= 0 && s < inv.getSize()) {
                inv.setItem(s, filler);
            }
        }
    }

    private static void setupButton(Player player, Inventory inv, MollyAuction plugin, String path, List<String> overrideLore) {
        if (!plugin.getGuisConfig().contains(path)) return;
        int slot = plugin.getGuisConfig().getInt(path + ".slot", 0);
        if (slot < 0 || slot >= inv.getSize()) return;

        String mat = plugin.getGuisConfig().getString(path + ".material", "STONE");
        int customModelData = plugin.getGuisConfig().getInt(path + ".custom-model-data", -1);
        String name = plugin.getCleanGuiString(player, path + ".name");

        List<String> lore = overrideLore != null ? overrideLore : colorList(player, plugin.getGuisConfig().getStringList(path + ".lore"));
        inv.setItem(slot, createCustomItem(mat, customModelData, name, lore));
    }

    private static void setupMultiSlotButton(Player player, Inventory inv, MollyAuction plugin, String path) {
        if (!plugin.getGuisConfig().contains(path)) return;
        List<Integer> slots = getSlots(plugin.getGuisConfig(), path + ".slots");
        String mat = plugin.getGuisConfig().getString(path + ".material", "STONE");
        int customModelData = plugin.getGuisConfig().getInt(path + ".custom-model-data", -1);
        String name = plugin.getCleanGuiString(player, path + ".name");
        List<String> rawLore = plugin.getGuisConfig().getStringList(path + ".lore");
        List<String> lore = colorList(player, rawLore);

        ItemStack item = createCustomItem(mat, customModelData, name, lore);
        for (int slot : slots) {
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, item);
            }
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

    private static List<String> colorList(Player player, List<String> list) {
        if (list == null) return null;
        List<String> colored = new ArrayList<>();
        for (String s : list) {
            colored.add(MollyAuction.color(player, s));
        }
        return colored;
    }
}
