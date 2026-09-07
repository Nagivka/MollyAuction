package ua.nagivka.mollyauction;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ua.nagivka.mollyauction.commands.AuctionCommand;
import ua.nagivka.mollyauction.config.ConfigMigrator;
import ua.nagivka.mollyauction.config.FolderMigrator;
import ua.nagivka.mollyauction.database.DatabaseManager;
import ua.nagivka.mollyauction.listeners.GUIListener;
import ua.nagivka.mollyauction.managers.AuctionManager;
import ua.nagivka.mollyauction.managers.EconomyManager;
import ua.nagivka.mollyauction.managers.LogManager;

import java.io.File;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MollyAuction extends JavaPlugin {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern BRACKET_HEX_PATTERN = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private LogManager logManager;
    private AuctionManager auctionManager;

    private File langFile;
    private FileConfiguration langConfig;
    private File guisFile;
    private FileConfiguration guisConfig;
    private BukkitTask expirationTask;
    private BukkitTask autoRefreshTask;

    private boolean papiEnabled = false;
    private Method papiSetPlaceholdersMethod = null;

    @Override
    public void onLoad() {
        // Миграция файлов из старой папки NGVKauction происходит до инициализации конфигов
        FolderMigrator.migrateOldPluginFolder(this);
    }

    @Override
    public void onEnable() {
        // Проверяем миграцию также в onEnable на случай загрузки без onLoad
        FolderMigrator.migrateOldPluginFolder(this);

        saveDefaultConfig();
        ConfigMigrator.migrateConfigs(this);
        createCustomConfigs();

        initHooks();

        this.economyManager = new EconomyManager(this);
        if (!this.economyManager.hasValidEconomy()) {
            getLogger().severe("Vault или совместимый плагин экономики не найден! Выключение плагина...");
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
        PluginCommand ahCmd = getCommand("ah");
        if (ahCmd != null) {
            ahCmd.setExecutor(command);
            ahCmd.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new ua.nagivka.mollyauction.listeners.PlayerJoinListener(this), this);

        startExpirationTask();
        startAutoRefreshTask();

        getLogger().info("MollyAuction успешно запущен! Автор: nagivka");
    }

    @Override
    public void onDisable() {
        stopExpirationTask();
        stopAutoRefreshTask();

        if (logManager != null) {
            logManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("MollyAuction выключен.");
    }

    private void initHooks() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                papiSetPlaceholdersMethod = papiClass.getMethod("setPlaceholders", Player.class, String.class);
                papiEnabled = true;
                getLogger().info("Интеграция с PlaceholderAPI успешно активирована.");
            } catch (Exception e) {
                papiEnabled = false;
            }
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

        // Перезапускаем фоновые задачи с новыми интервалами из конфига
        startExpirationTask();
        startAutoRefreshTask();

        getLogger().info("Все конфигурации и интервалы MollyAuction успешно перезагружены.");
    }

    private void startExpirationTask() {
        stopExpirationTask();
        long intervalSeconds = getConfig().getLong("settings.expiration-check-interval-seconds", 30L);
        if (intervalSeconds < 5L) intervalSeconds = 5L;
        long checkIntervalTicks = intervalSeconds * 20L;

        this.expirationTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (auctionManager != null) {
                auctionManager.checkExpirations();
            }
        }, checkIntervalTicks, checkIntervalTicks);
    }

    private void stopExpirationTask() {
        if (expirationTask != null && !expirationTask.isCancelled()) {
            expirationTask.cancel();
            expirationTask = null;
        }
    }

    private void startAutoRefreshTask() {
        stopAutoRefreshTask();
        if (!getConfig().getBoolean("settings.auto-refresh.enabled", true)) {
            return;
        }

        long intervalSeconds = getConfig().getLong("settings.auto-refresh.interval-seconds", 5L);
        if (intervalSeconds < 1L) intervalSeconds = 1L;
        long intervalTicks = intervalSeconds * 20L;

        this.autoRefreshTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof ua.nagivka.mollyauction.menus.AuctionGUIHolder holder) {
                    if (holder.getMenuType() == ua.nagivka.mollyauction.menus.AuctionGUIHolder.MenuType.MAIN) {
                        ua.nagivka.mollyauction.menus.AuctionGUI.refreshMainMenuInPlace(player, this, player.getOpenInventory().getTopInventory(), holder);
                    } else if (holder.getMenuType() == ua.nagivka.mollyauction.menus.AuctionGUIHolder.MenuType.MY_ITEMS) {
                        ua.nagivka.mollyauction.menus.AuctionGUI.refreshMyItemsMenuInPlace(player, this, player.getOpenInventory().getTopInventory(), holder);
                    }
                }
            }
        }, intervalTicks, intervalTicks);
    }

    private void stopAutoRefreshTask() {
        if (autoRefreshTask != null && !autoRefreshTask.isCancelled()) {
            autoRefreshTask.cancel();
            autoRefreshTask = null;
        }
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
        return getMsg(null, path);
    }

    public String getMsg(Player player, String path) {
        String prefix = getConfig().getString("settings.prefix", "");
        String msg = langConfig.getString("messages." + path, "");
        return color(player, prefix + msg);
    }

    public String getCleanGuiString(String path) {
        return getCleanGuiString(null, path);
    }

    public String getCleanGuiString(Player player, String path) {
        return color(player, guisConfig.getString(path, ""));
    }

    public static String color(String text) {
        return color(null, text);
    }

    public static String color(Player player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Поддержка PlaceholderAPI
        if (player != null) {
            MollyAuction instance = JavaPlugin.getPlugin(MollyAuction.class);
            if (instance.papiEnabled && instance.papiSetPlaceholdersMethod != null) {
                try {
                    text = (String) instance.papiSetPlaceholdersMethod.invoke(null, player, text);
                } catch (Exception ignored) {}
            }
        }

        // Поддержка MiniMessage тэгов, если текст содержит теги вроде <gold>, <gradient:..> и т.д.
        if (text.contains("<") && text.contains(">") && !text.contains("<#")) {
            try {
                Component comp = MINI_MESSAGE.deserialize(text);
                text = LEGACY_SERIALIZER.serialize(comp);
            } catch (Exception ignored) {}
        }

        // Поддержка <#RRGGBB>
        Matcher bracketMatcher = BRACKET_HEX_PATTERN.matcher(text);
        StringBuilder sbBracket = new StringBuilder();
        while (bracketMatcher.find()) {
            String hex = bracketMatcher.group(1);
            bracketMatcher.appendReplacement(sbBracket, ChatColor.of("#" + hex).toString());
        }
        bracketMatcher.appendTail(sbBracket);
        text = sbBracket.toString();

        // Поддержка &#RRGGBB
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(sb);

        // Классические цветовые коды &a, &l, &r
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public void playCfgSound(Player player, String soundPath, float pitch) {
        if (!getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }
        try {
            String soundName = getConfig().getString("sounds." + soundPath);
            if (soundName != null && !soundName.trim().isEmpty()) {
                Sound sound = Sound.valueOf(soundName.trim().toUpperCase());
                player.playSound(player.getLocation(), sound, 1f, pitch);
            }
        } catch (IllegalArgumentException ignored) {}
    }
}
