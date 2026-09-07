package ua.nagivka.mollyauction.menus;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ua.nagivka.mollyauction.models.AuctionItem;
import ua.nagivka.mollyauction.models.Category;
import ua.nagivka.mollyauction.models.SortMode;

public class AuctionGUIHolder implements InventoryHolder {

    public enum MenuType {
        MAIN, MY_ITEMS, CONFIRM, STORAGE, PARTIAL_BUY, HISTORY
    }

    private final MenuType menuType;
    private final int page;
    private final Category category;
    private final SortMode sortMode;
    private final String searchQuery;
    private final AuctionItem pendingItem;
    private final int maxPage;
    private final int selectedAmount;

    public AuctionGUIHolder(MenuType menuType, int page) {
        this(menuType, page, Category.ALL, SortMode.NEWEST, null, null, 0, 1);
    }

    public AuctionGUIHolder(MenuType menuType, int page, int maxPage) {
        this(menuType, page, Category.ALL, SortMode.NEWEST, null, null, maxPage, 1);
    }

    public AuctionGUIHolder(MenuType menuType, AuctionItem pendingItem, int selectedAmount) {
        this(menuType, 0, Category.ALL, SortMode.NEWEST, null, pendingItem, 0, selectedAmount);
    }

    public AuctionGUIHolder(MenuType menuType, int page, Category category, SortMode sortMode, String searchQuery, AuctionItem pendingItem, int maxPage) {
        this(menuType, page, category, sortMode, searchQuery, pendingItem, maxPage, 1);
    }

    public AuctionGUIHolder(MenuType menuType, int page, Category category, SortMode sortMode, String searchQuery, AuctionItem pendingItem, int maxPage, int selectedAmount) {
        this.menuType = menuType;
        this.page = page;
        this.category = category;
        this.sortMode = sortMode;
        this.searchQuery = searchQuery;
        this.pendingItem = pendingItem;
        this.maxPage = maxPage;
        this.selectedAmount = selectedAmount;
    }

    public MenuType getMenuType() {
        return menuType;
    }

    public int getPage() {
        return page;
    }

    public Category getCategory() {
        return category;
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public AuctionItem getPendingItem() {
        return pendingItem;
    }

    public int getMaxPage() {
        return maxPage;
    }

    public int getSelectedAmount() {
        return selectedAmount;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
