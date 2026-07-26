package ua.nagivka.nGVKauction.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import ua.nagivka.nGVKauction.NGVKauction;
import ua.nagivka.nGVKauction.menus.AuctionGUI;

public class GUIListener implements Listener {
    private final NGVKauction plugin;

    public GUIListener(NGVKauction plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AuctionGUI.AuctionHolder) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getInventory().getHolder() instanceof AuctionGUI.AuctionHolder) {
            if (event.getRawSlot() < event.getInventory().getSize()) {
                event.setCancelled(true);
            } else if (event.isShiftClick()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && clicked.getType() != Material.AIR) {
                    player.performCommand("ah sell " + plugin.getConfig().getDouble("settings.min-price"));
                }
            }
        }
    }
}