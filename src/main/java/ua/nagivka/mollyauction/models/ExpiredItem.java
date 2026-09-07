package ua.nagivka.mollyauction.models;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

public class ExpiredItem {

    private final UUID id;
    private final UUID playerUuid;
    private final ItemStack item;
    private final long addedAt;

    public ExpiredItem(UUID playerUuid, ItemStack item) {
        this(UUID.randomUUID(), playerUuid, item, System.currentTimeMillis());
    }

    public ExpiredItem(UUID id, UUID playerUuid, ItemStack item, long addedAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.item = Objects.requireNonNull(item, "item cannot be null").clone();
        this.addedAt = addedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public long getAddedAt() {
        return addedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpiredItem that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
