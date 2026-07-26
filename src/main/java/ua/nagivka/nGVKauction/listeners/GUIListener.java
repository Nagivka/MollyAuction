package ua.nagivka.nGVKauction.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ua.nagivka.nGVKauction.NGVKauction;
import ua.nagivka.nGVKauction.menus.AuctionGUI;
import ua.nagivka.nGVKauction.menus.AuctionGUIHolder;
import ua.nagivka.nGVKauction.models.AuctionItem;
import ua.nagivka.nGVKauction.models.Category;
import ua.nagivka.nGVKauction.models.SortMode;

import java.util.HashMap;
import java.util.List;

public class GUIListener implements Listener {

    private final NGVKauction plugin;

    public GUIListener(NGVKauction plugin) {
        this.plugin = plugin;
    }

    @EventHandler
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

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof AuctionGUIHolder holder)) return;

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        if (clickedInv.equals(topInv)) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType() == Material.AIR) return;

            int slot = event.getRawSlot();
            switch (holder.getMenuType()) {
                case MAIN -> handleMainClick(player, slot, event.getClick(), holder);
                case MY_ITEMS -> handleMyItemsClick(player, slot, holder);
                case CONFIRM -> handleConfirmClick(player, slot, holder);
                case STORAGE -> handleStorageClick(player, slot);
            }
        } else {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
        }
    }

    private void handleMainClick(Player player, int slot, ClickType clickType, AuctionGUIHolder holder) {
        int categorySlot = plugin.getGuisConfig().getInt("main-menu.buttons.category-filter.slot", 45);
        int sortSlot = plugin.getGuisConfig().getInt("main-menu.buttons.sort-mode.slot", 46);
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
        } else if (slot == prevSlot) {
            if (page > 0) AuctionGUI.openMainMenuFiltered(player, plugin, page - 1, category, sortMode, query);
        } else if (slot == nextSlot) {
            AuctionGUI.openMainMenuFiltered(player, plugin, page + 1, category, sortMode, query);
        } else if (slot == refreshSlot) {
            AuctionGUI.openMainMenuFiltered(player, plugin, 0, category, sortMode, null);
            player.sendMessage(plugin.getMsg("refreshed"));
        } else if (slot == myItemsSlot) {
            AuctionGUI.openMyItemsMenu(player, plugin, 0);
        } else if (slot == storageSlot) {
            AuctionGUI.openStorageMenu(player, plugin);
        } else {
            List<Integer> slots = AuctionGUI.getSlots(plugin.getGuisConfig(), "main-menu.item-slots");
            if (slots.contains(slot)) {
                int index = (page * slots.size()) + slots.indexOf(slot);
                List<AuctionItem> items = plugin.getAuctionManager().getFilteredAndSortedItems(category, sortMode, query);

                if (index >= items.size()) return;
                AuctionItem ai = items.get(index);

                if (ai.getSellerUuid().equals(player.getUniqueId())) {
                    if (plugin.getAuctionManager().removeItem(ai)) {
                        giveItem(player, ai.getItem());
                        player.sendMessage(plugin.getMsg("item-removed"));
                        plugin.playCfgSound(player, "receive-item", 1f);
                        AuctionGUI.openMainMenuFiltered(player, plugin, page, category, sortMode, query);
                    }
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

    private void handleMyItemsClick(Player player, int slot, AuctionGUIHolder holder) {
        int backSlot = plugin.getGuisConfig().getInt("my-items-menu.buttons.back.slot", 49);
        if (slot == backSlot) {
            AuctionGUI.openMainMenu(player, plugin, 0);
        } else {
            List<Integer> slots = AuctionGUI.getSlots(plugin.getGuisConfig(), "my-items-menu.item-slots");
            if (slots.contains(slot)) {
                int index = (holder.getPage() * slots.size()) + slots.indexOf(slot);
                List<AuctionItem> myItems = plugin.getAuctionManager().getPlayerItems(player.getUniqueId());
                if (index < myItems.size()) {
                    AuctionItem ai = myItems.get(index);
                    if (plugin.getAuctionManager().removeItem(ai)) {
                        giveItem(player, ai.getItem());
                        player.sendMessage(plugin.getMsg("item-removed"));
                        plugin.playCfgSound(player, "receive-item", 1f);
                        AuctionGUI.openMyItemsMenu(player, plugin, holder.getPage());
                    }
                }
            }
        }
    }

    private void handleConfirmClick(Player player, int slot, AuctionGUIHolder holder) {
        AuctionItem pending = holder.getPendingItem();
        if (pending == null || plugin.getAuctionManager().getItem(pending.getId()) == null) {
            player.sendMessage(plugin.getMsg("already-sold"));
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

    private void handleStorageClick(Player player, int slot) {
        int backSlot = plugin.getGuisConfig().getInt("storage-menu.buttons.back.slot", 49);
        if (slot == backSlot) {
            AuctionGUI.openMainMenu(player, plugin, 0);
        } else {
            List<Integer> slots = AuctionGUI.getSlots(plugin.getGuisConfig(), "storage-menu.item-slots");
            if (slots.contains(slot)) {
                int index = slots.indexOf(slot);
                List<ItemStack> expired = plugin.getAuctionManager().getExpiredItems(player.getUniqueId());
                if (index < expired.size()) {
                    ItemStack item = expired.get(index);
                    plugin.getAuctionManager().removeExpiredItem(player.getUniqueId(), index);
                    giveItem(player, item);
                    player.sendMessage(plugin.getMsg("items-collected"));
                    plugin.playCfgSound(player, "receive-item", 1f);
                    AuctionGUI.openStorageMenu(player, plugin);
                }
            }
        }
    }

    private synchronized void processPurchase(Player buyer, AuctionItem item) {
        AuctionItem liveItem = plugin.getAuctionManager().getItem(item.getId());
        if (liveItem == null) {
            buyer.sendMessage(plugin.getMsg("already-sold"));
            plugin.playCfgSound(buyer, "error", 1f);
            return;
        }

        double price = liveItem.getPrice();
        if (!plugin.getEconomyManager().has(buyer, price, liveItem.getCurrencyType())) {
            buyer.sendMessage(plugin.getMsg("not-enough-money"));
            plugin.playCfgSound(buyer, "error", 1f);
            return;
        }

        if (!plugin.getAuctionManager().removeItem(liveItem)) {
            buyer.sendMessage(plugin.getMsg("already-sold"));
            plugin.playCfgSound(buyer, "error", 1f);
            return;
        }

        plugin.getEconomyManager().withdraw(buyer, price, liveItem.getCurrencyType());

        double taxPercent = plugin.getConfig().getDouble("settings.tax-rate", 5.0);
        double taxCut = price * (taxPercent / 100.0);
        double sellerGets = price - taxCut;

        plugin.getEconomyManager().deposit(liveItem.getSellerUuid(), sellerGets, liveItem.getCurrencyType());
        giveItem(buyer, liveItem.getItem());

        plugin.getLogManager().log("ITEM_BUY", "Player " + buyer.getName() + " (" + buyer.getUniqueId() + ") purchased " + liveItem.getItem().getType() + " x" + liveItem.getItem().getAmount() + " from " + liveItem.getSellerName() + " for $" + price + " (tax: $" + taxCut + ")");

        buyer.sendMessage(plugin.getMsg("buy-success").replace("%price%", String.valueOf(price)));
        plugin.playCfgSound(buyer, "buy-success", 1f);

        Player sellerPlayer = Bukkit.getPlayer(liveItem.getSellerUuid());
        if (sellerPlayer != null && sellerPlayer.isOnline()) {
            String sMsg = plugin.getMsg("seller-notification")
                    .replace("%buyer%", buyer.getName())
                    .replace("%money%", String.valueOf(sellerGets));
            sellerPlayer.sendMessage(sMsg);
            plugin.playCfgSound(sellerPlayer, "receive-money", 1f);
        }
    }

    private void giveItem(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> drop = player.getInventory().addItem(item);
        if (!drop.isEmpty()) {
            for (ItemStack it : drop.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), it);
            }
            player.sendMessage(plugin.getMsg("inventory-full"));
        }
    }
}