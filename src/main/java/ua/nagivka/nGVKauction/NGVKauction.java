package ua.nagivka.nGVKauction;

import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ua.nagivka.nGVKauction.commands.AuctionCommand;
import ua.nagivka.nGVKauction.listeners.GUIListener;
import ua.nagivka.nGVKauction.managers.AuctionManager;
import ua.nagivka.nGVKauction.menus.AuctionGUI;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NGVKauction extends JavaPlugin {

    private AuctionManager auctionManager;
    private Economy econ = null;

    private File messagesFile;
    private FileConfiguration messagesConfig;
    private File guisFile;
    private FileConfiguration guisConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createCustomConfigs();

        if (!setupEconomy()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.auctionManager = new AuctionManager(this);
        this.auctionManager.loadData();

        AuctionCommand command = new AuctionCommand(this);
        if (getCommand("ah") != null) {
            getCommand("ah").setExecutor(command);
            getCommand("ah").setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new AuctionGUI(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
    }

    @Override
    public void onDisable() {
        if (auctionManager != null) {
            auctionManager.saveData();
        }
    }

    private void createCustomConfigs() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            messagesFile.getParentFile().mkdirs();
            saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        guisFile = new File(getDataFolder(), "guis.yml");
        if (!guisFile.exists()) {
            guisFile.getParentFile().mkdirs();
            saveResource("guis.yml", false);
        }
        guisConfig = YamlConfiguration.loadConfiguration(guisFile);
    }

    public void reloadAllConfigs() {
        reloadConfig();
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        guisConfig = YamlConfiguration.loadConfiguration(guisFile);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public AuctionManager getAuctionManager() { return auctionManager; }
    public Economy getEconomy() { return econ; }
    public FileConfiguration getMessagesConfig() { return messagesConfig; }
    public FileConfiguration getGuisConfig() { return guisConfig; }

    public String getMsg(String path) {
        String prefix = getConfig().getString("settings.prefix", "");
        String msg = messagesConfig.getString("messages." + path, "");
        return color(prefix + msg);
    }

    public String getCleanGuiString(String path) {
        return color(guisConfig.getString(path, ""));
    }

    public static String color(String text) {
        if (text == null) return "";
        Matcher matcher = Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public void playCfgSound(Player player, String soundPath, float pitch) {
        if (!getConfig().getBoolean("sounds.enabled", true)) return;
        try {
            String soundName = getConfig().getString("sounds." + soundPath);
            if (soundName != null && !soundName.isEmpty()) {
                player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase()), 1f, pitch);
            }
        } catch (IllegalArgumentException ignored) {}
    }
}