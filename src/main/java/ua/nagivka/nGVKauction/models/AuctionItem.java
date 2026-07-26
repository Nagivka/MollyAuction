package ua.nagivka.nGVKauction.models;

import org.bukkit.inventory.ItemStack;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
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

    public AuctionItem(UUID id, UUID sellerUuid, String sellerName, ItemStack item, double price, long expireTime) {
        this(id, sellerUuid, sellerName, item, price, System.currentTimeMillis(), expireTime, "VAULT", "DEFAULT");
    }

    public AuctionItem(UUID id, UUID sellerUuid, String sellerName, ItemStack item, double price, long expireTime, String currencyType, String category) {
        this(id, sellerUuid, sellerName, item, price, System.currentTimeMillis(), expireTime, currencyType, category);
    }

    public AuctionItem(UUID id, UUID sellerUuid, String sellerName, ItemStack item, double price, long createdAt, long expireTime, String currencyType, String category) {
        this.id = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.createdAt = createdAt;
        this.expireTime = expireTime;
        this.currencyType = currencyType != null ? currencyType : "VAULT";
        this.category = category != null ? category : "DEFAULT";
    }

    public Map<String, Object> serializeToMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id.toString());
        map.put("sellerUuid", sellerUuid.toString());
        map.put("sellerName", sellerName);
        map.put("price", price);
        map.put("createdAt", createdAt);
        map.put("expireTime", expireTime);
        map.put("currencyType", currencyType);
        map.put("category", category);
        map.put("itemBase64", itemToBase64(item));
        return map;
    }

    public static AuctionItem deserializeFromMap(Map<?, ?> map) {
        UUID id = UUID.fromString((String) map.get("id"));
        UUID sellerUuid = UUID.fromString((String) map.get("sellerUuid"));
        String sellerName = (String) map.get("sellerName");
        double price = ((Number) map.get("price")).doubleValue();
        long createdAt = map.containsKey("createdAt") ? ((Number) map.get("createdAt")).longValue() : System.currentTimeMillis();
        long expireTime = ((Number) map.get("expireTime")).longValue();
        String currencyType = map.containsKey("currencyType") ? (String) map.get("currencyType") : "VAULT";
        String category = map.containsKey("category") ? (String) map.get("category") : "DEFAULT";
        ItemStack item = itemFromBase64((String) map.get("itemBase64"));
        return new AuctionItem(id, sellerUuid, sellerName, item, price, createdAt, expireTime, currencyType, category);
    }

    public static String itemToBase64(ItemStack item) {
        try {
            byte[] bytes = item.serializeAsBytes();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    public static ItemStack itemFromBase64(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            return null;
        }
    }

    public UUID getId() { return id; }
    public UUID getSellerUuid() { return sellerUuid; }
    public String getSellerName() { return sellerName; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public long getCreatedAt() { return createdAt; }
    public long getExpireTime() { return expireTime; }
    public String getCurrencyType() { return currencyType; }
    public String getCategory() { return category; }
}