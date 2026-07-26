package ua.nagivka.nGVKauction.menus;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class AuctionGUIHolder implements InventoryHolder {
    private final String guiType; // MAIN, CONFIRM, PROFILE
    private int page = 0;
    private String category = "ALL";
    private String sort = "NEWEST";
    private String search = "";

    public AuctionGUIHolder(String guiType) {
        this.guiType = guiType;
    }

    public String getGuiType() { return guiType; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }

    @Override
    public Inventory getInventory() { return null; }
}