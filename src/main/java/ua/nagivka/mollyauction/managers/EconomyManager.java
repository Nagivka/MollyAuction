package ua.nagivka.mollyauction.managers;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import ua.nagivka.mollyauction.MollyAuction;

import java.lang.reflect.Method;
import java.util.UUID;

public class EconomyManager {

    private final MollyAuction plugin;
    private Economy vaultEconomy;
    private Object playerPointsAPI;

    public EconomyManager(MollyAuction plugin) {
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

    public boolean hasValidEconomy() {
        return vaultEconomy != null;
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
            EconomyResponse response = vaultEconomy.withdrawPlayer(player, amount);
            return response != null && response.transactionSuccess();
        }
        return false;
    }

    public boolean deposit(UUID playerUuid, double amount, String currency) {
        if ("PLAYER_POINTS".equalsIgnoreCase(currency)) {
            if (playerPointsAPI == null) return false;
            try {
                Method giveMethod = playerPointsAPI.getClass().getMethod("give", UUID.class, int.class);
                return (boolean) giveMethod.invoke(playerPointsAPI, playerUuid, (int) amount);
            } catch (Exception e) {
                plugin.getLogger().severe("Ошибка начисления PlayerPoints: " + e.getMessage());
                return false;
            }
        } else if (vaultEconomy != null) {
            EconomyResponse response = vaultEconomy.depositPlayer(Bukkit.getOfflinePlayer(playerUuid), amount);
            return response != null && response.transactionSuccess();
        }
        return false;
    }
}
