package ua.nagivka.mollyauction.models;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

public class TransactionRecord {

    private final UUID id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final UUID buyerUuid;
    private final String buyerName;
    private final ItemStack item;
    private final double price;
    private final int amount;
    private final long timestamp;

    public TransactionRecord(UUID sellerUuid, String sellerName, UUID buyerUuid, String buyerName, ItemStack item, double price, int amount) {
        this(UUID.randomUUID(), sellerUuid, sellerName, buyerUuid, buyerName, item, price, amount, System.currentTimeMillis());
    }

    public TransactionRecord(UUID id, UUID sellerUuid, String sellerName, UUID buyerUuid, String buyerName, ItemStack item, double price, int amount, long timestamp) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.sellerUuid = Objects.requireNonNull(sellerUuid, "sellerUuid cannot be null");
        this.sellerName = Objects.requireNonNull(sellerName, "sellerName cannot be null");
        this.buyerUuid = Objects.requireNonNull(buyerUuid, "buyerUuid cannot be null");
        this.buyerName = Objects.requireNonNull(buyerName, "buyerName cannot be null");
        this.item = Objects.requireNonNull(item, "item cannot be null").clone();
        this.price = price;
        this.amount = amount;
        this.timestamp = timestamp;
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

    public UUID getBuyerUuid() {
        return buyerUuid;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public double getPrice() {
        return price;
    }

    public int getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
