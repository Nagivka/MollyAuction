package ua.nagivka.nGVKauction.managers;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import ua.nagivka.nGVKauction.NGVKauction;

import java.lang.reflect.Method;
import java.util.UUID;

public class EconomyManager {

    private final NGVKauction plugin;
    private Economy vaultEconomy = null;
    private Object playerPointsAPI = null;

    public EconomyManager(NGVKauction plugin) {
        this.plugin = plugin;
        setupVault();
        setupPlayerPoints();
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                vaultEconomy = rsp.getProvider();
            }
        }
    }

    private void setupPlayerPoints() {
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            try {
                Class<?> ppClass = Class.forName("org.blackkaiser.playerpoints.PlayerPoints");
                Object ppInstance = ppClass.getMethod("getInstance").invoke(null);
                this.playerPointsAPI = ppClass.getMethod("getAPI").invoke(ppInstance);
            } catch (Exception ignored) {}
        }
    }

    public boolean has(Player player, double amount, String currency) {
        if ("PLAYER_POINTS".equalsIgnoreCase(currency)) {
            if (playerPointsAPI == null) return false;
            try {
                Method lookMethod = playerPointsAPI.getClass().getMethod("look", UUID.class);
                int balance = (int) lookMethod.invoke(playerPointsAPI, player.getUniqueId());
                return balance >= (int) amount;
            } catch (Exception e) {
                return false;
            }
        }
        return vaultEconomy != null && vaultEconomy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount, String currency) {
        if ("PLAYER_POINTS".equalsIgnoreCase(currency)) {
            if (playerPointsAPI == null) return false;
            try {
                Method takeMethod = playerPointsAPI.getClass().getMethod("take", UUID.class, int.class);
                return (boolean) takeMethod.invoke(playerPointsAPI, player.getUniqueId(), (int) amount);
            } catch (Exception e) {
                return false;
            }
        }
        if (vaultEconomy != null) {
            return vaultEconomy.withdrawPlayer(player, amount).transactionSuccess();
        }
        return false;
    }

    public void deposit(UUID playerUuid, double amount, String currency) {
        if ("PLAYER_POINTS".equalsIgnoreCase(currency)) {
            if (playerPointsAPI == null) return;
            try {
                Method giveMethod = playerPointsAPI.getClass().getMethod("give", UUID.class, int.class);
                giveMethod.invoke(playerPointsAPI, playerUuid, (int) amount);
            } catch (Exception e) {
                plugin.getLogger().severe(e.getMessage());
            }
        } else if (vaultEconomy != null) {
            vaultEconomy.depositPlayer(Bukkit.getOfflinePlayer(playerUuid), amount);
        }
    }
}