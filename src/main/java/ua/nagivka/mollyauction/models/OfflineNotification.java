package ua.nagivka.mollyauction.models;

import java.util.Objects;
import java.util.UUID;

public class OfflineNotification {

    private final UUID id;
    private final UUID playerUuid;
    private final String itemName;
    private final int amount;
    private final double price;
    private final String buyerName;
    private final long soldAt;

    public OfflineNotification(UUID playerUuid, String itemName, int amount, double price, String buyerName) {
        this(UUID.randomUUID(), playerUuid, itemName, amount, price, buyerName, System.currentTimeMillis());
    }

    public OfflineNotification(UUID id, UUID playerUuid, String itemName, int amount, double price, String buyerName, long soldAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.itemName = Objects.requireNonNull(itemName, "itemName cannot be null");
        this.amount = amount;
        this.price = price;
        this.buyerName = Objects.requireNonNull(buyerName, "buyerName cannot be null");
        this.soldAt = soldAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getItemName() {
        return itemName;
    }

    public int getAmount() {
        return amount;
    }

    public double getPrice() {
        return price;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public long getSoldAt() {
        return soldAt;
    }
}
