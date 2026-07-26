package ua.nagivka.nGVKauction.models;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public class AuctionItem {

    private final UUID id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long createdAt;
    private final long expireTime;
    private final String currencyType;
    private final String category;

    public AuctionItem(UUID sellerUuid, String sellerName, ItemStack item, double price, long expireTime) {
        this(UUID.randomUUID(), sellerUuid, sellerName, item, price, System.currentTimeMillis(), expireTime, "VAULT", "DEFAULT");
    }

    public AuctionItem(UUID id, UUID sellerUuid, String sellerName, ItemStack item, double price, long createdAt, long expireTime, String currencyType, String category) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.sellerUuid = Objects.requireNonNull(sellerUuid, "sellerUuid cannot be null");
        this.sellerName = Objects.requireNonNull(sellerName, "sellerName cannot be null");
        this.item = Objects.requireNonNull(item, "item cannot be null").clone();
        this.price = price;
        this.createdAt = createdAt;
        this.expireTime = expireTime;
        this.currencyType = currencyType != null ? currencyType : "VAULT";
        this.category = category != null ? category : "DEFAULT";
    }

    public static String itemToBase64(ItemStack item) {
        if (item == null) return "";
        try {
            byte[] bytes = item.serializeAsBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack itemFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public String getSellerName() {
        return sellerName;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public double getPrice() {
        return price;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public String getCurrencyType() {
        return currencyType;
    }

    public String getCategory() {
        return category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuctionItem that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}