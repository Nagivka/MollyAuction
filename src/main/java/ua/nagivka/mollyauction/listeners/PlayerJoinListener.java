package ua.nagivka.mollyauction.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ua.nagivka.mollyauction.MollyAuction;
import ua.nagivka.mollyauction.models.OfflineNotification;

import java.util.List;

public class PlayerJoinListener implements Listener {

    private final MollyAuction plugin;

    public PlayerJoinListener(MollyAuction plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Задержка 30 тиков (1.5 сек), чтобы игрок успел прогрузиться в мир
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            plugin.getDatabaseManager().loadAndClearOfflineNotificationsAsync(player.getUniqueId()).thenAccept(notifications -> {
                if (notifications == null || notifications.isEmpty()) return;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    int totalItems = 0;
                    double totalEarned = 0.0;

                    for (OfflineNotification notif : notifications) {
                        totalItems += notif.getAmount();
                        totalEarned += notif.getPrice();
                    }

                    double roundedMoney = Math.round(totalEarned * 100.0) / 100.0;

                    String message = plugin.getMsg(player, "offline-sales-summary")
                            .replace("%count%", String.valueOf(totalItems))
                            .replace("%money%", String.valueOf(roundedMoney));

                    player.sendMessage(message);
                    plugin.playCfgSound(player, "receive-money", 1f);
                });
            });
        }, 30L);
    }
}
