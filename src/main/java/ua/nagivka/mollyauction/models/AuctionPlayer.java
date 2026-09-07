package ua.nagivka.mollyauction.models;

import java.util.UUID;

public class AuctionPlayer {

    private final UUID uuid;
    private final String name;
    private boolean banned;
    private double unclaimedMoney;

    public AuctionPlayer(UUID uuid, String name, boolean banned, double unclaimedMoney) {
        this.uuid = uuid;
        this.name = name;
        this.banned = banned;
        this.unclaimedMoney = unclaimedMoney;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public boolean isBanned() {
        return banned;
    }

    public void setBanned(boolean banned) {
        this.banned = banned;
    }

    public double getUnclaimedMoney() {
        return unclaimedMoney;
    }

    public void addUnclaimedMoney(double amount) {
        this.unclaimedMoney += amount;
    }

    public void clearUnclaimedMoney() {
        this.unclaimedMoney = 0.0;
    }
}
