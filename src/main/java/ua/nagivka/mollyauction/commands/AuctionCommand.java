package ua.nagivka.mollyauction.commands;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ua.nagivka.mollyauction.MollyAuction;
import ua.nagivka.mollyauction.menus.AuctionGUI;
import ua.nagivka.mollyauction.models.AuctionItem;
import ua.nagivka.mollyauction.models.Category;
import ua.nagivka.mollyauction.models.SortMode;

import java.util.ArrayList;
import java.util.List;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final MollyAuction plugin;

    public AuctionCommand(MollyAuction plugin) {
        this.plugin = plugin;
    }

    private boolean hasUsePermission(CommandSender sender) {
        return sender.hasPermission("mollyauction.use") || sender.hasPermission("ngvkauctions.use");
    }

    private boolean hasSellPermission(CommandSender sender) {
        return sender.hasPermission("mollyauction.sell") || sender.hasPermission("ngvkauctions.sell");
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission("mollyauction.admin") || sender.hasPermission("ngvkauctions.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadAllConfigs();
                sender.sendMessage("Конфигурация MollyAuction успешно перезагружена!");
            } else {
                sender.sendMessage("Используйте: /" + label + " reload");
            }
            return true;
        }

        if (args.length == 0) {
            if (!hasUsePermission(player)) {
                player.sendMessage(plugin.getMsg(player, "no-permission"));
                return true;
            }
            AuctionGUI.openMainMenu(player, plugin, 0);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("sell")) {
            if (!hasSellPermission(player)) {
                player.sendMessage(plugin.getMsg(player, "no-permission"));
                return true;
            }

            ItemStack handItem = player.getInventory().getItemInMainHand();
            if (handItem.getType() == Material.AIR) {
                player.sendMessage(plugin.getMsg(player, "item-in-hand"));
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(plugin.getMsg(player, "usage-sell"));
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
                player.sendMessage(plugin.getMsg(player, "invalid-price")
                        .replace("%min%", String.valueOf(minPrice))
                        .replace("%max%", String.valueOf(maxPrice)));
                return true;
            }

            if (Double.isNaN(price) || Double.isInfinite(price) || price < minPrice || price > maxPrice) {
                player.sendMessage(plugin.getMsg(player, "invalid-price")
                        .replace("%min%", String.valueOf(minPrice))
                        .replace("%max%", String.valueOf(maxPrice)));
                return true;
            }

            int activeCount = plugin.getAuctionManager().getActiveCount(player);
            int slotLimit = plugin.getAuctionManager().getPlayerSlotLimit(player);

            if (activeCount >= slotLimit) {
                player.sendMessage(plugin.getMsg(player, "limit-reached")
                        .replace("%limit%", String.valueOf(slotLimit)));
                return true;
            }

            if (amount <= 0 || amount > handItem.getAmount()) {
                player.sendMessage(plugin.getMsg(player, "invalid-amount"));
                return true;
            }

            if (isBlacklisted(handItem)) {
                player.sendMessage(plugin.getMsg(player, "blacklisted-item"));
                return true;
            }

            // Проверка и списание комиссии за выставление (Listing Fee)
            boolean feeEnabled = plugin.getConfig().getBoolean("settings.listing-fee.enabled", false);
            double listingFee = 0.0;
            if (feeEnabled) {
                double percent = plugin.getConfig().getDouble("settings.listing-fee.percent", 1.0);
                double fixed = plugin.getConfig().getDouble("settings.listing-fee.fixed", 0.0);
                listingFee = Math.round(((price * (percent / 100.0)) + fixed) * 100.0) / 100.0;

                if (listingFee > 0.0) {
                    if (!plugin.getEconomyManager().has(player, listingFee, "VAULT")) {
                        player.sendMessage(plugin.getMsg(player, "not-enough-money-fee").replace("%fee%", String.valueOf(listingFee)));
                        return true;
                    }
                    plugin.getEconomyManager().withdraw(player, listingFee, "VAULT");
                    player.sendMessage(plugin.getMsg(player, "listing-fee").replace("%fee%", String.valueOf(listingFee)));
                    plugin.getLogManager().log("LISTING_FEE", "Player " + player.getName() + " paid listing fee $" + listingFee + " for listing price $" + price);
                }
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

            player.sendMessage(plugin.getMsg(player, "sell-success").replace("%price%", String.valueOf(price)));
            plugin.playCfgSound(player, "receive-item", 2f);
            return true;
        }

        if (sub.equals("search")) {
            if (!hasUsePermission(player)) {
                player.sendMessage(plugin.getMsg(player, "no-permission"));
                return true;
            }

            if (args.length < 2) {
                AuctionGUI.openMainMenu(player, plugin, 0);
                return true;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                sb.append(args[i]).append(" ");
            }
            String query = sb.toString().trim();
            AuctionGUI.openMainMenuFiltered(player, plugin, 0, Category.ALL, SortMode.NEWEST, query);
            return true;
        }

        if (sub.equals("my") || sub.equals("myitems")) {
            if (!hasUsePermission(player)) {
                player.sendMessage(plugin.getMsg(player, "no-permission"));
                return true;
            }
            AuctionGUI.openMyItemsMenu(player, plugin, 0);
            return true;
        }

        if (sub.equals("storage") || sub.equals("expired")) {
            if (!hasUsePermission(player)) {
                player.sendMessage(plugin.getMsg(player, "no-permission"));
                return true;
            }
            AuctionGUI.openStorageMenu(player, plugin, 0);
            return true;
        }

        if (sub.equals("history")) {
            if (!hasUsePermission(player)) {
                player.sendMessage(plugin.getMsg(player, "no-permission"));
                return true;
            }
            AuctionGUI.openHistoryMenu(player, plugin, 0);
            return true;
        }

        if (sub.equals("reload")) {
            if (!hasAdminPermission(player)) {
                player.sendMessage(plugin.getMsg(player, "no-permission"));
                return true;
            }
            plugin.reloadAllConfigs();
            player.sendMessage(plugin.getMsg(player, "reloaded"));
            return true;
        }

        if (hasUsePermission(player)) {
            AuctionGUI.openMainMenu(player, plugin, 0);
        } else {
            player.sendMessage(plugin.getMsg(player, "no-permission"));
        }
        return true;
    }

    private boolean isBlacklisted(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;

        FileConfiguration cfg = plugin.getConfig();
        String matName = item.getType().name();

        // 1. Проверка материалов (поддержка как старого формата списка, так и новой структуры)
        List<String> materials = cfg.isList("blacklist.materials")
                ? cfg.getStringList("blacklist.materials")
                : cfg.getStringList("blacklist");

        if (materials.stream().anyMatch(m -> m.equalsIgnoreCase(matName))) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // 2. Проверка названия предмета
        List<String> namePatterns = cfg.getStringList("blacklist.name-contains");
        if (!namePatterns.isEmpty() && meta.hasDisplayName()) {
            String cleanName = ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
            for (String pattern : namePatterns) {
                if (cleanName.contains(pattern.toLowerCase())) {
                    return true;
                }
            }
        }

        // 3. Проверка Lore
        List<String> lorePatterns = cfg.getStringList("blacklist.lore-contains");
        if (!lorePatterns.isEmpty() && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (String line : lore) {
                    String cleanLine = ChatColor.stripColor(line).toLowerCase();
                    for (String pattern : lorePatterns) {
                        if (cleanLine.contains(pattern.toLowerCase())) {
                            return true;
                        }
                    }
                }
            }
        }

        // 4. Проверка CustomModelData
        List<Integer> cmdList = cfg.getIntegerList("blacklist.custom-model-data");
        if (!cmdList.isEmpty() && meta.hasCustomModelData()) {
            if (cmdList.contains(meta.getCustomModelData())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> comp = new ArrayList<>();
        if (args.length == 1) {
            if (hasSellPermission(sender)) comp.add("sell");
            if (hasUsePermission(sender)) {
                comp.add("search");
                comp.add("my");
                comp.add("storage");
                comp.add("history");
            }
            if (hasAdminPermission(sender)) comp.add("reload");
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
        return comp.stream()
                .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .toList();
    }
}
