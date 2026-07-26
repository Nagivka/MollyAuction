package ua.nagivka.nGVKauction;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ua.nagivka.nGVKauction.commands.AuctionCommand;
import ua.nagivka.nGVKauction.config.ConfigMigrator;
import ua.nagivka.nGVKauction.database.DatabaseManager;
import ua.nagivka.nGVKauction.listeners.GUIListener;
import ua.nagivka.nGVKauction.managers.AuctionManager;
import ua.nagivka.nGVKauction.managers.EconomyManager;
import ua.nagivka.nGVKauction.managers.LogManager;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NGVKauction extends JavaPlugin {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private LogManager logManager;
    private AuctionManager auctionManager;

    private File langFile;
    private FileConfiguration langConfig;
    private File guisFile;
    private FileConfiguration guisConfig;
    private BukkitTask expirationTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ConfigMigrator.migrateConfigs(this);
        createCustomConfigs();

        this.economyManager = new EconomyManager(this);
        if (!this.economyManager.hasValidEconomy()) {
            getLogger().severe("Vault или подходящий плагин экономики не найден! Выключение...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.logManager = new LogManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.init();

        ConfigMigrator.migrateLegacyData(this, databaseManager);

        this.auctionManager = new AuctionManager(this, databaseManager, logManager);
        this.auctionManager.loadDataAsync();

        AuctionCommand command = new AuctionCommand(this);
        if (getCommand("ah") != null) {
            getCommand("ah").setExecutor(command);
            getCommand("ah").setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        long checkIntervalTicks = getConfig().getLong("settings.expiration-check-interval-seconds", 30L) * 20L;
        this.expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (auctionManager != null) {
                auctionManager.checkExpirations();
            }
        }, checkIntervalTicks, checkIntervalTicks);
    }

    @Override
    public void onDisable() {
        if (expirationTask != null && !expirationTask.isCancelled()) {
            expirationTask.cancel();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    private void createCustomConfigs() {
        langFile = new File(getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            langFile.getParentFile().mkdirs();
            saveResource("lang.yml", false);
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);

        guisFile = new File(getDataFolder(), "guis.yml");
        if (!guisFile.exists()) {
            guisFile.getParentFile().mkdirs();
            saveResource("guis.yml", false);
        }
        guisConfig = YamlConfiguration.loadConfiguration(guisFile);
    }

    public void reloadAllConfigs() {
        reloadConfig();
        ConfigMigrator.migrateConfigs(this);
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        guisConfig = YamlConfiguration.loadConfiguration(guisFile);
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    public LogManager getLogManager() {
        return logManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public FileConfiguration getLangConfig() {
        return langConfig;
    }

    public FileConfiguration getGuisConfig() {
        return guisConfig;
    }

    public String getMsg(String path) {
        String prefix = getConfig().getString("settings.prefix", "");
        String msg = langConfig.getString("messages." + path, "");
        return color(prefix + msg);
    }

    public String getCleanGuiString(String path) {
        return color(guisConfig.getString(path, ""));
    }

    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public void playCfgSound(Player player, String soundPath, float pitch) {
        if (!getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }
        try {
            String soundName = getConfig().getString("sounds." + soundPath);
            if (soundName != null && !soundName.isEmpty()) {
                player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase()), 1f, pitch);
            }
        } catch (IllegalArgumentException ignored) {}
    }
}