package ua.nagivka.nGVKauction.menus;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ua.nagivka.nGVKauction.models.AuctionItem;
import ua.nagivka.nGVKauction.models.Category;
import ua.nagivka.nGVKauction.models.SortMode;

public class AuctionGUIHolder implements InventoryHolder {

    public enum MenuType {
        MAIN, MY_ITEMS, CONFIRM, STORAGE
    }

    private final MenuType menuType;
    private final int page;
    private final Category category;
    private final SortMode sortMode;
    private final String searchQuery;
    private final AuctionItem pendingItem;

    public AuctionGUIHolder(MenuType menuType, int page) {
        this(menuType, page, Category.ALL, SortMode.NEWEST, null, null);
    }

    public AuctionGUIHolder(MenuType menuType, int page, Category category, SortMode sortMode, String searchQuery, AuctionItem pendingItem) {
        this.menuType = menuType;
        this.page = page;
        this.category = category;
        this.sortMode = sortMode;
        this.searchQuery = searchQuery;
        this.pendingItem = pendingItem;
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

    @Override
    public Inventory getInventory() {
        return null;
    }
}