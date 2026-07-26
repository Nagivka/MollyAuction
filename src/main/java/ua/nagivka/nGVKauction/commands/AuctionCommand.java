package ua.nagivka.nGVKauction.commands;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ua.nagivka.nGVKauction.NGVKauction;
import ua.nagivka.nGVKauction.menus.AuctionGUI;
import ua.nagivka.nGVKauction.models.AuctionItem;
import ua.nagivka.nGVKauction.models.Category;
import ua.nagivka.nGVKauction.models.SortMode;

import java.util.ArrayList;
import java.util.List;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final NGVKauction plugin;

    public AuctionCommand(NGVKauction plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadAllConfigs();
                sender.sendMessage("Конфигурация успешно перезагружена!");
            }
            return true;
        }

        if (args.length == 0) {
            if (!player.hasPermission("ngvkauctions.use")) {
                player.sendMessage(plugin.getMsg("no-permission"));
                return true;
            }
            AuctionGUI.openMainMenu(player, plugin, 0);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("sell")) {
            if (!player.hasPermission("ngvkauctions.sell")) {
                player.sendMessage(plugin.getMsg("no-permission"));
                return true;
            }

            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getType() == Material.AIR) {
                player.sendMessage(plugin.getMsg("item-in-hand"));
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(plugin.getMsg("usage-sell"));
                return true;
            }

            double price;
            int amount = handItem.getAmount();

            double minPrice = plugin.getConfig().getDouble("settings.min-price", 10.0);
            double maxPrice = plugin.getConfig().getDouble("settings.max-price", 100000000.0);

            try {
                price = Double.parseDouble(args[1]);
                if (args.length >= 3) {
                    amount = Integer.parseInt(args[2]);
                }
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.getMsg("invalid-price")
                        .replace("%min%", String.valueOf(minPrice))
                        .replace("%max%", String.valueOf(maxPrice)));
                return true;
            }

            if (price < minPrice || price > maxPrice) {
                player.sendMessage(plugin.getMsg("invalid-price")
                        .replace("%min%", String.valueOf(minPrice))
                        .replace("%max%", String.valueOf(maxPrice)));
                return true;
            }

            int activeCount = plugin.getAuctionManager().getActiveCount(player);
            int slotLimit = plugin.getAuctionManager().getPlayerSlotLimit(player);

            if (activeCount >= slotLimit) {
                player.sendMessage(plugin.getMsg("limit-reached")
                        .replace("%limit%", String.valueOf(slotLimit)));
                return true;
            }

            if (amount <= 0 || amount > handItem.getAmount()) {
                player.sendMessage(plugin.getMsg("invalid-amount"));
                return true;
            }

            List<String> blacklist = plugin.getConfig().getStringList("blacklist");
            if (blacklist.contains(handItem.getType().toString())) {
                player.sendMessage(plugin.getMsg("blacklisted-item"));
                return true;
            }

            ItemStack itemToSell = handItem.clone();
            itemToSell.setAmount(amount);

            if (handItem.getAmount() == amount) {
                player.getInventory().setItemInMainHand(null);
            } else {
                handItem.setAmount(handItem.getAmount() - amount);
            }

            long expireHours = plugin.getConfig().getLong("settings.expire-hours", 48);
            long expireTime = System.currentTimeMillis() + (expireHours * 3600000L);

            AuctionItem ai = new AuctionItem(player.getUniqueId(), player.getName(), itemToSell, price, expireTime);
            plugin.getAuctionManager().addItem(ai);

            player.sendMessage(plugin.getMsg("sell-success").replace("%price%", String.valueOf(price)));
            plugin.playCfgSound(player, "receive-item", 2f);
            return true;
        }

        if (sub.equals("search")) {
            if (!player.hasPermission("ngvkauctions.use")) {
                player.sendMessage(plugin.getMsg("no-permission"));
                return true;
            }

            if (args.length < 2) {
                AuctionGUI.openMainMenu(player, plugin, 0);
                return true;
            }

            String query = args[1];
            AuctionGUI.openMainMenuFiltered(player, plugin, 0, Category.ALL, SortMode.NEWEST, query);
            return true;
        }

        if (sub.equals("my") || sub.equals("myitems")) {
            AuctionGUI.openMyItemsMenu(player, plugin, 0);
            return true;
        }

        if (sub.equals("storage") || sub.equals("expired")) {
            AuctionGUI.openStorageMenu(player, plugin);
            return true;
        }

        if (sub.equals("reload") && player.hasPermission("ngvkauctions.admin")) {
            plugin.reloadAllConfigs();
            player.sendMessage(plugin.getMsg("reloaded"));
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> comp = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("ngvkauctions.sell")) comp.add("sell");
            if (sender.hasPermission("ngvkauctions.use")) {
                comp.add("search");
                comp.add("my");
                comp.add("storage");
            }
            if (sender.hasPermission("ngvkauctions.admin")) comp.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            comp.add("100");
            comp.add("1000");
            comp.add("50000");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("sell")) {
            if (sender instanceof Player p) {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item.getType() != Material.AIR) {
                    comp.add(String.valueOf(item.getAmount()));
                }
            }
        }
        return comp;
    }
}