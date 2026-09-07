package ua.nagivka.mollyauction.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ua.nagivka.mollyauction.MollyAuction;
import ua.nagivka.mollyauction.menus.AuctionGUI;
import ua.nagivka.mollyauction.menus.AuctionGUIHolder;
import ua.nagivka.mollyauction.models.AuctionItem;
import ua.nagivka.mollyauction.models.Category;
import ua.nagivka.mollyauction.models.ExpiredItem;
import ua.nagivka.mollyauction.models.OfflineNotification;
import ua.nagivka.mollyauction.models.SortMode;
import ua.nagivka.mollyauction.models.TransactionRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class GUIListener implements Listener {

    private final MollyAuction plugin;
    private final Object purchaseLock = new Object();

    public GUIListener(MollyAuction plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AuctionGUIHolder) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof AuctionGUIHolder holder)) return;

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        if (clickedInv.equals(topInv)) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType() == Material.AIR) return;

            int slot = event.getRawSlot();
            switch (holder.getMenuType()) {
                case MAIN -> handleMainClick(player, slot, event.getClick(), holder);
                case MY_ITEMS -> handleMyItemsClick(player, slot, event.getClick(), holder);
                case CONFIRM -> handleConfirmClick(player, slot, holder);
                case STORAGE -> handleStorageClick(player, slot, holder);
                case PARTIAL_BUY -> handlePartialBuyClick(player, slot, holder);
                case HISTORY -> handleHistoryClick(player, slot, holder);
            }
        } else {
            if (event.isShiftClick() || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
            }
        }
    }

    private void handleMainClick(Player player, int slot, ClickType clickType, AuctionGUIHolder holder) {
        int categorySlot = plugin.getGuisConfig().getInt("main-menu.buttons.category-filter.slot", 45);
        int sortSlot = plugin.getGuisConfig().getInt("main-menu.buttons.sort-mode.slot", 46);
        int historySlot = plugin.getGuisConfig().getInt("main-menu.buttons.history.slot", 47);
        int prevSlot = plugin.getGuisConfig().getInt("main-menu.buttons.prev-page.slot", 48);
        int refreshSlot = plugin.getGuisConfig().getInt("main-menu.buttons.refresh.slot", 49);
        int nextSlot = plugin.getGuisConfig().getInt("main-menu.buttons.next-page.slot", 50);
        int myItemsSlot = plugin.getGuisConfig().getInt("main-menu.buttons.my-items.slot", 52);
        int storageSlot = plugin.getGuisConfig().getInt("main-menu.buttons.storage.slot", 53);

        int page = holder.getPage();
        Category category = holder.getCategory();
        SortMode sortMode = holder.getSortMode();
        String query = holder.getSearchQuery();

        if (slot == categorySlot) {
            Category nextCat = clickType.isRightClick() ? category.previous() : category.next();
            AuctionGUI.openMainMenuFiltered(player, plugin, 0, nextCat, sortMode, query);
        } else if (slot == sortSlot) {
            SortMode nextSort = clickType.isRightClick() ? sortMode.previous() : sortMode.next();
            AuctionGUI.openMainMenuFiltered(player, plugin, 0, category, nextSort, query);
        } else if (slot == historySlot) {
            AuctionGUI.openHistoryMenu(player, plugin, 0);
        } else if (slot == prevSlot) {
            if (page > 0) {
                AuctionGUI.openMainMenuFiltered(player, plugin, page - 1, category, sortMode, query);
            }
        } else if (slot == nextSlot) {
            if (page < holder.getMaxPage()) {
                AuctionGUI.openMainMenuFiltered(player, plugin, page + 1, category, sortMode, query);
            }
        } else if (slot == refreshSlot) {
            AuctionGUI.openMainMenuFiltered(player, plugin, 0, category, sortMode, null);
            player.sendMessage(plugin.getMsg("refreshed"));
        } else if (slot == myItemsSlot) {
            AuctionGUI.openMyItemsMenu(player, plugin, 0);
        } else if (slot == storageSlot) {
            AuctionGUI.openStorageMenu(player, plugin, 0);
        } else {
            List<Integer> slots = AuctionGUI.getSlots(plugin.getGuisConfig(), "main-menu.item-slots");
            if (slots.contains(slot)) {
                int index = (page * slots.size()) + slots.indexOf(slot);
                List<AuctionItem> items = plugin.getAuctionManager().getFilteredAndSortedItems(category, sortMode, query);

                if (index >= items.size()) return;
                AuctionItem ai = items.get(index);

                // Если игрок нажал на свой собственный товар
                if (ai.getSellerUuid().equals(player.getUniqueId())) {
                    if (clickType.isRightClick()) {
                        prolongItem(player, ai);
                        AuctionGUI.refreshMainMenuInPlace(player, plugin, player.getOpenInventory().getTopInventory(), holder);
                    } else {
                        if (plugin.getAuctionManager().removeItem(ai)) {
                            giveItem(player, ai.getItem());
                            player.sendMessage(plugin.getMsg("item-removed"));
                            plugin.playCfgSound(player, "receive-item", 1f);
                            AuctionGUI.refreshMainMenuInPlace(player, plugin, player.getOpenInventory().getTopInventory(), holder);
                        }
                    }
                    return;
                }

                // Админ-снятие через Shift + ПКМ
                boolean isAdmin = player.hasPermission("mollyauction.admin") || player.hasPermission("ngvkauctions.admin");
                if (isAdmin && clickType == ClickType.SHIFT_RIGHT) {
                    if (plugin.getAuctionManager().removeItem(ai)) {
                        plugin.getAuctionManager().addExpiredItem(ai.getSellerUuid(), ai.getItem());
                        plugin.getLogManager().log("ADMIN_REMOVE", "Admin " + player.getName() + " removed item " + ai.getId() + " of seller " + ai.getSellerName());
                        player.sendMessage(plugin.getMsg(player, "admin-removed-success").replace("%seller%", ai.getSellerName()));
                        plugin.playCfgSound(player, "receive-item", 1f);

                        Player seller = Bukkit.getPlayer(ai.getSellerUuid());
                        if (seller != null && seller.isOnline()) {
                            seller.sendMessage(plugin.getMsg(seller, "admin-removed-seller"));
                        }

                        AuctionGUI.openMainMenuFiltered(player, plugin, page, category, sortMode, query);
                    }
                    return;
                }

                // Покупка частями через ПКМ (если предметов в лоте больше 1)
                if (clickType.isRightClick() && ai.getItem().getAmount() > 1) {
                    AuctionGUI.openBuyPartialMenu(player, plugin, ai, 1);
                    return;
                }

                if (clickType == ClickType.SHIFT_LEFT) {
                    processPurchase(player, ai);
                    AuctionGUI.openMainMenuFiltered(player, plugin, page, category, sortMode, query);
                } else {
                    AuctionGUI.openConfirmMenu(player, plugin, ai);
                }
            }
        }
    }

    private void handleMyItemsClick(Player player, int slot, ClickType clickType, AuctionGUIHolder holder) {
        int backSlot = plugin.getGuisConfig().getInt("my-items-menu.buttons.back.slot", 49);
        int prevSlot = plugin.getGuisConfig().getInt("my-items-menu.buttons.prev-page.slot", 48);
        int nextSlot = plugin.getGuisConfig().getInt("my-items-menu.buttons.next-page.slot", 50);

        int page = holder.getPage();

        if (slot == backSlot) {
            AuctionGUI.openMainMenu(player, plugin, 0);
        } else if (slot == prevSlot) {
            if (page > 0) {
                AuctionGUI.openMyItemsMenu(player, plugin, page - 1);
            }
        } else if (slot == nextSlot) {
            if (page < holder.getMaxPage()) {
                AuctionGUI.openMyItemsMenu(player, plugin, page + 1);
            }
        } else {
            List<Integer> slots = AuctionGUI.getSlots(plugin.getGuisConfig(), "my-items-menu.item-slots");
            if (slots.contains(slot)) {
                int index = (holder.getPage() * slots.size()) + slots.indexOf(slot);
                List<AuctionItem> myItems = plugin.getAuctionManager().getPlayerItems(player.getUniqueId());
                if (index < myItems.size()) {
                    AuctionItem ai = myItems.get(index);
                    if (clickType.isLeftClick()) {
                        // ЛКМ — продлить срок лота
                        prolongItem(player, ai);
                        AuctionGUI.refreshMyItemsMenuInPlace(player, plugin, player.getOpenInventory().getTopInventory(), holder);
                    } else if (clickType.isRightClick()) {
                        // ПКМ — снять лот с продажи
                        if (plugin.getAuctionManager().removeItem(ai)) {
                            giveItem(player, ai.getItem());
                            player.sendMessage(plugin.getMsg("item-removed"));
                            plugin.playCfgSound(player, "receive-item", 1f);
                            AuctionGUI.refreshMyItemsMenuInPlace(player, plugin, player.getOpenInventory().getTopInventory(), holder);
                        }
                    }
                }
            }
        }
    }

    private void prolongItem(Player player, AuctionItem item) {
        double fee = plugin.getConfig().getDouble("settings.prolong-fee", 0.0);
        if (fee > 0.0) {
            if (!plugin.getEconomyManager().has(player, fee, item.getCurrencyType())) {
                player.sendMessage(plugin.getMsg(player, "not-enough-money-prolong").replace("%fee%", String.valueOf(fee)));
                plugin.playCfgSound(player, "error", 1f);
                return;
            }
            plugin.getEconomyManager().withdraw(player, fee, item.getCurrencyType());
        }

        long expireHours = plugin.getConfig().getLong("settings.expire-hours", 48);
        long newExpire = System.currentTimeMillis() + (expireHours * 3600000L);
        AuctionItem renewed = item.withNewExpireTime(newExpire);
        plugin.getAuctionManager().updateItem(renewed);

        player.sendMessage(plugin.getMsg(player, "item-prolonged").replace("%hours%", String.valueOf(expireHours)));
        plugin.playCfgSound(player, "receive-item", 1f);
        plugin.getLogManager().log("ITEM_PROLONG", "Player " + player.getName() + " prolonged item " + item.getId() + " for " + expireHours + " hours.");
    }

    private void handleConfirmClick(Player player, int slot, AuctionGUIHolder holder) {
        AuctionItem pending = holder.getPendingItem();
        if (pending == null || plugin.getAuctionManager().getItem(pending.getId()) == null) {
            player.sendMessage(plugin.getMsg("already-sold"));
            plugin.playCfgSound(player, "error", 1f);
            player.closeInventory();
            return;
        }

        if (pending.getSellerUuid().equals(player.getUniqueId())) {
            player.sendMessage(plugin.getMsg("cannot-buy-own"));
            plugin.playCfgSound(player, "error", 1f);
            player.closeInventory();
            return;
        }

        List<Integer> confirmSlots = AuctionGUI.getSlots(plugin.getGuisConfig(), "confirm-menu.buttons.confirm.slots");
        List<Integer> cancelSlots = AuctionGUI.getSlots(plugin.getGuisConfig(), "confirm-menu.buttons.cancel.slots");

        if (confirmSlots.contains(slot)) {
            processPurchase(player, pending);
            player.closeInventory();
        } else if (cancelSlots.contains(slot)) {
            AuctionGUI.openMainMenu(player, plugin, 0);
        }
    }

    private void handleStorageClick(Player player, int slot, AuctionGUIHolder holder) {
        int backSlot = plugin.getGuisConfig().getInt("storage-menu.buttons.back.slot", 49);
        int collectAllSlot = plugin.getGuisConfig().getInt("storage-menu.buttons.collect-all.slot", 47);
        int prevSlot = plugin.getGuisConfig().getInt("storage-menu.buttons.prev-page.slot", 48);
        int nextSlot = plugin.getGuisConfig().getInt("storage-menu.buttons.next-page.slot", 50);

        int page = holder.getPage();

        if (slot == backSlot) {
            AuctionGUI.openMainMenu(player, plugin, 0);
        } else if (slot == collectAllSlot) {
            // Забрать всё из хранилища
            List<ExpiredItem> expired = plugin.getAuctionManager().getExpiredItems(player.getUniqueId());
            if (expired.isEmpty()) {
                player.sendMessage(plugin.getMsg("storage-empty"));
                plugin.playCfgSound(player, "error", 1f);
                return;
            }

            List<ExpiredItem> collected = new ArrayList<>();
            for (ExpiredItem ei : expired) {
                HashMap<Integer, ItemStack> notFit = player.getInventory().addItem(ei.getItem());
                if (notFit.isEmpty()) {
                    collected.add(ei);
                } else {
                    // Возвращаем то, что не влезло, обратно и прекращаем забор
                    int leftover = notFit.values().stream().mapToInt(ItemStack::getAmount).sum();
                    int took = ei.getItem().getAmount() - leftover;
                    if (took > 0) {
                        player.getInventory().removeItem(new ItemStack(ei.getItem().getType(), took));
                    }
                    player.sendMessage(plugin.getMsg("inventory-full"));
                    break;
                }
            }

            if (!collected.isEmpty()) {
                plugin.getAuctionManager().removeExpiredItemsBatch(player.getUniqueId(), collected);
                player.sendMessage(plugin.getMsg("collected-all").replace("%count%", String.valueOf(collected.size())));
                plugin.playCfgSound(player, "receive-item", 1f);
            }

            AuctionGUI.openStorageMenu(player, plugin, page);

        } else if (slot == prevSlot) {
            if (page > 0) {
                AuctionGUI.openStorageMenu(player, plugin, page - 1);
            }
        } else if (slot == nextSlot) {
            if (page < holder.getMaxPage()) {
                AuctionGUI.openStorageMenu(player, plugin, page + 1);
            }
        } else {
            List<Integer> slots = AuctionGUI.getSlots(plugin.getGuisConfig(), "storage-menu.item-slots");
            if (slots.contains(slot)) {
                int maxItems = Math.max(1, slots.size());
                int index = (page * maxItems) + slots.indexOf(slot);
                List<ExpiredItem> expired = plugin.getAuctionManager().getExpiredItems(player.getUniqueId());
                if (index < expired.size()) {
                    ExpiredItem item = expired.get(index);
                    if (plugin.getAuctionManager().removeExpiredItem(player.getUniqueId(), item)) {
                        giveItem(player, item.getItem());
                        player.sendMessage(plugin.getMsg("items-collected"));
                        plugin.playCfgSound(player, "receive-item", 1f);
                        AuctionGUI.openStorageMenu(player, plugin, page);
                    }
                }
            }
        }
    }

    private void handlePartialBuyClick(Player player, int slot, AuctionGUIHolder holder) {
        AuctionItem item = holder.getPendingItem();
        if (item == null || plugin.getAuctionManager().getItem(item.getId()) == null) {
            player.sendMessage(plugin.getMsg("already-sold"));
            plugin.playCfgSound(player, "error", 1f);
            player.closeInventory();
            return;
        }

        int maxAmount = item.getItem().getAmount();
        int current = holder.getSelectedAmount();

        if (slot == 10) { // -10
            AuctionGUI.openBuyPartialMenu(player, plugin, item, Math.max(1, current - 10));
        } else if (slot == 11) { // -1
            AuctionGUI.openBuyPartialMenu(player, plugin, item, Math.max(1, current - 1));
        } else if (slot == 15) { // +1
            AuctionGUI.openBuyPartialMenu(player, plugin, item, Math.min(maxAmount, current + 1));
        } else if (slot == 16) { // +10
            AuctionGUI.openBuyPartialMenu(player, plugin, item, Math.min(maxAmount, current + 10));
        } else if (slot == 22) { // Подтвердить покупку
            processPartialPurchase(player, item, current);
            player.closeInventory();
        } else if (slot == 18) { // Отмена
            AuctionGUI.openMainMenu(player, plugin, 0);
        }
    }

    private void handleHistoryClick(Player player, int slot, AuctionGUIHolder holder) {
        int backSlot = plugin.getGuisConfig().getInt("history-menu.buttons.back.slot", 49);
        int prevSlot = plugin.getGuisConfig().getInt("history-menu.buttons.prev-page.slot", 48);
        int nextSlot = plugin.getGuisConfig().getInt("history-menu.buttons.next-page.slot", 50);

        int page = holder.getPage();

        if (slot == backSlot) {
            AuctionGUI.openMainMenu(player, plugin, 0);
        } else if (slot == prevSlot) {
            if (page > 0) {
                AuctionGUI.openHistoryMenu(player, plugin, page - 1);
            }
        } else if (slot == nextSlot) {
            if (page < holder.getMaxPage()) {
                AuctionGUI.openHistoryMenu(player, plugin, page + 1);
            }
        }
    }

    private void processPurchase(Player buyer, AuctionItem item) {
        synchronized (purchaseLock) {
            AuctionItem liveItem = plugin.getAuctionManager().getItem(item.getId());
            if (liveItem == null) {
                buyer.sendMessage(plugin.getMsg("already-sold"));
                plugin.playCfgSound(buyer, "error", 1f);
                return;
            }

            if (liveItem.getSellerUuid().equals(buyer.getUniqueId())) {
                buyer.sendMessage(plugin.getMsg("cannot-buy-own"));
                plugin.playCfgSound(buyer, "error", 1f);
                return;
            }

            double price = liveItem.getPrice();
            String currency = liveItem.getCurrencyType();

            if (!plugin.getEconomyManager().has(buyer, price, currency)) {
                buyer.sendMessage(plugin.getMsg("not-enough-money"));
                plugin.playCfgSound(buyer, "error", 1f);
                return;
            }

            if (!plugin.getEconomyManager().withdraw(buyer, price, currency)) {
                buyer.sendMessage(plugin.getMsg("not-enough-money"));
                plugin.playCfgSound(buyer, "error", 1f);
                return;
            }

            if (!plugin.getAuctionManager().removeItem(liveItem)) {
                plugin.getEconomyManager().deposit(buyer.getUniqueId(), price, currency);
                buyer.sendMessage(plugin.getMsg("already-sold"));
                plugin.playCfgSound(buyer, "error", 1f);
                return;
            }

            double taxPercent = plugin.getConfig().getDouble("settings.tax-rate", 5.0);
            double taxCut = price * (taxPercent / 100.0);
            double sellerGets = Math.max(0.0, price - taxCut);

            plugin.getEconomyManager().deposit(liveItem.getSellerUuid(), sellerGets, currency);
            giveItem(buyer, liveItem.getItem());

            plugin.getLogManager().log("ITEM_BUY", "Player " + buyer.getName() + " (" + buyer.getUniqueId() + ") purchased " + liveItem.getItem().getType() + " x" + liveItem.getItem().getAmount() + " from " + liveItem.getSellerName() + " for $" + price + " (tax: $" + taxCut + ")");

            // Сохраняем в историю транзакций
            plugin.getDatabaseManager().saveTransactionRecordAsync(new TransactionRecord(
                    liveItem.getSellerUuid(), liveItem.getSellerName(),
                    buyer.getUniqueId(), buyer.getName(),
                    liveItem.getItem(), price, liveItem.getItem().getAmount()
            ));

            buyer.sendMessage(plugin.getMsg("buy-success").replace("%price%", String.valueOf(price)));
            plugin.playCfgSound(buyer, "buy-success", 1f);

            Player sellerPlayer = Bukkit.getPlayer(liveItem.getSellerUuid());
            if (sellerPlayer != null && sellerPlayer.isOnline()) {
                String sMsg = plugin.getMsg("seller-notification")
                        .replace("%buyer%", buyer.getName())
                        .replace("%money%", String.valueOf(sellerGets));
                sellerPlayer.sendMessage(sMsg);
                plugin.playCfgSound(sellerPlayer, "receive-money", 1f);
            } else {
                // Если продавец оффлайн, создаем оффлайн-уведомление
                String itemName = liveItem.getItem().hasItemMeta() && liveItem.getItem().getItemMeta().hasDisplayName()
                        ? liveItem.getItem().getItemMeta().getDisplayName() : liveItem.getItem().getType().name();
                plugin.getDatabaseManager().saveOfflineNotificationAsync(new OfflineNotification(
                        liveItem.getSellerUuid(), itemName, liveItem.getItem().getAmount(), sellerGets, buyer.getName()
                ));
            }
        }
    }

    private void processPartialPurchase(Player buyer, AuctionItem item, int amountToBuy) {
        synchronized (purchaseLock) {
            AuctionItem liveItem = plugin.getAuctionManager().getItem(item.getId());
            if (liveItem == null) {
                buyer.sendMessage(plugin.getMsg("already-sold"));
                plugin.playCfgSound(buyer, "error", 1f);
                return;
            }

            if (liveItem.getSellerUuid().equals(buyer.getUniqueId())) {
                buyer.sendMessage(plugin.getMsg("cannot-buy-own"));
                plugin.playCfgSound(buyer, "error", 1f);
                return;
            }

            int available = liveItem.getItem().getAmount();
            if (amountToBuy > available) amountToBuy = available;
            if (amountToBuy <= 0) return;

            double unitPrice = liveItem.getPrice() / (double) available;
            double priceToPay = Math.round((unitPrice * amountToBuy) * 100.0) / 100.0;
            String currency = liveItem.getCurrencyType();

            if (!plugin.getEconomyManager().has(buyer, priceToPay, currency)) {
                buyer.sendMessage(plugin.getMsg("not-enough-money"));
                plugin.playCfgSound(buyer, "error", 1f);
                return;
            }

            if (!plugin.getEconomyManager().withdraw(buyer, priceToPay, currency)) {
                buyer.sendMessage(plugin.getMsg("not-enough-money"));
                plugin.playCfgSound(buyer, "error", 1f);
                return;
            }

            ItemStack boughtStack = liveItem.getItem().clone();
            boughtStack.setAmount(amountToBuy);

            if (amountToBuy == available) {
                // Выкуплен весь лот целиком
                if (!plugin.getAuctionManager().removeItem(liveItem)) {
                    plugin.getEconomyManager().deposit(buyer.getUniqueId(), priceToPay, currency);
                    buyer.sendMessage(plugin.getMsg("already-sold"));
                    plugin.playCfgSound(buyer, "error", 1f);
                    return;
                }
            } else {
                // Обновляем остаток лота в памяти и базе
                AuctionItem updated = liveItem.withReducedAmount(amountToBuy, priceToPay);
                plugin.getAuctionManager().updateItem(updated);
            }

            double taxPercent = plugin.getConfig().getDouble("settings.tax-rate", 5.0);
            double taxCut = priceToPay * (taxPercent / 100.0);
            double sellerGets = Math.max(0.0, priceToPay - taxCut);

            plugin.getEconomyManager().deposit(liveItem.getSellerUuid(), sellerGets, currency);
            giveItem(buyer, boughtStack);

            plugin.getLogManager().log("ITEM_PARTIAL_BUY", "Player " + buyer.getName() + " purchased " + amountToBuy + "x " + boughtStack.getType() + " for $" + priceToPay + " from " + liveItem.getSellerName());

            // Сохраняем в историю
            plugin.getDatabaseManager().saveTransactionRecordAsync(new TransactionRecord(
                    liveItem.getSellerUuid(), liveItem.getSellerName(),
                    buyer.getUniqueId(), buyer.getName(),
                    boughtStack, priceToPay, amountToBuy
            ));

            buyer.sendMessage(plugin.getMsg("partial-buy-success")
                    .replace("%amount%", String.valueOf(amountToBuy))
                    .replace("%price%", String.valueOf(priceToPay)));
            plugin.playCfgSound(buyer, "buy-success", 1f);

            Player sellerPlayer = Bukkit.getPlayer(liveItem.getSellerUuid());
            if (sellerPlayer != null && sellerPlayer.isOnline()) {
                String sMsg = plugin.getMsg("partial-seller-notification")
                        .replace("%buyer%", buyer.getName())
                        .replace("%amount%", String.valueOf(amountToBuy))
                        .replace("%money%", String.valueOf(sellerGets));
                sellerPlayer.sendMessage(sMsg);
                plugin.playCfgSound(sellerPlayer, "receive-money", 1f);
            } else {
                String itemName = boughtStack.hasItemMeta() && boughtStack.getItemMeta().hasDisplayName()
                        ? boughtStack.getItemMeta().getDisplayName() : boughtStack.getType().name();
                plugin.getDatabaseManager().saveOfflineNotificationAsync(new OfflineNotification(
                        liveItem.getSellerUuid(), itemName, amountToBuy, sellerGets, buyer.getName()
                ));
            }
        }
    }

    private void giveItem(Player player, ItemStack item) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> giveItem(player, item));
            return;
        }

        HashMap<Integer, ItemStack> drop = player.getInventory().addItem(item);
        if (!drop.isEmpty()) {
            for (ItemStack it : drop.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), it);
            }
            player.sendMessage(plugin.getMsg("inventory-full"));
        }
    }
}
