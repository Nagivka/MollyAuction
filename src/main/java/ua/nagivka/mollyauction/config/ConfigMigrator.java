package ua.nagivka.mollyauction.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import ua.nagivka.mollyauction.MollyAuction;
import ua.nagivka.mollyauction.database.DatabaseManager;
import ua.nagivka.mollyauction.models.AuctionItem;
import ua.nagivka.mollyauction.models.ExpiredItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ConfigMigrator {

    private static final String TARGET_VERSION = "1.2";

    private ConfigMigrator() {}

    public static void migrateConfigs(MollyAuction plugin) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        migrateMessagesToLang(plugin, dataFolder);
        migrateMainConfig(plugin, dataFolder);
    }

    public static void migrateLegacyData(MollyAuction plugin, DatabaseManager databaseManager) {
        File dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);

            if (cfg.isList("items")) {
                List<?> list = cfg.getList("items");
                if (list != null) {
                    for (Object obj : list) {
                        if (obj instanceof Map<?, ?> map) {
                            try {
                                AuctionItem ai = deserializeLegacyItem(map);
                                if (ai != null && ai.getItem() != null) {
                                    databaseManager.saveAuctionItemAsync(ai);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            if (cfg.getConfigurationSection("expired") != null) {
                for (String key : cfg.getConfigurationSection("expired").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        List<String> base64List = cfg.getStringList("expired." + key);
                        for (String b64 : base64List) {
                            ItemStack stack = AuctionItem.itemFromBase64(b64);
                            if (stack != null) {
                                databaseManager.saveExpiredItemAsync(new ExpiredItem(uuid, stack));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            File convertedFile = new File(plugin.getDataFolder(), "data.yml.converted");
            try {
                Files.move(dataFile.toPath(), convertedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Старый data.yml успешно мигрирован в базу данных и переименован в data.yml.converted");
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось переименовать data.yml: " + e.getMessage());
            }
        });
    }

    private static void migrateMessagesToLang(MollyAuction plugin, File dataFolder) {
        File oldMessagesFile = new File(dataFolder, "messages.yml");
        File newLangFile = new File(dataFolder, "lang.yml");

        if (oldMessagesFile.exists() && !newLangFile.exists()) {
            try {
                Files.move(oldMessagesFile.toPath(), newLangFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info("Обнаружен старый messages.yml -> успешно переименован в lang.yml");
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось переименовать messages.yml в lang.yml: " + e.getMessage());
            }
        }
    }

    private static void migrateMainConfig(MollyAuction plugin, File dataFolder) {
        File configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            return;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
        String currentVersion = cfg.getString("config-version", "1.0");

        if (TARGET_VERSION.equalsIgnoreCase(currentVersion)) {
            return;
        }

        plugin.getLogger().info("Миграция config.yml с версии " + currentVersion + " на " + TARGET_VERSION + "...");

        boolean modified = false;

        cfg.set("config-version", TARGET_VERSION);
        modified = true;

        if (!cfg.contains("settings.expiration-check-interval-seconds")) {
            cfg.set("settings.expiration-check-interval-seconds", 30);
            modified = true;
        }

        if (!cfg.contains("database.pool-size")) {
            cfg.set("database.pool-size", 10);
            modified = true;
        }

        if (modified) {
            try {
                cfg.save(configFile);
                plugin.getLogger().info("config.yml успешно обновлен до версии " + TARGET_VERSION);
            } catch (IOException e) {
                plugin.getLogger().severe("Не удалось сохранить обновленный config.yml: " + e.getMessage());
            }
        }
    }

    private static AuctionItem deserializeLegacyItem(Map<?, ?> map) {
        UUID id = UUID.fromString((String) map.get("id"));
        UUID sellerUuid = UUID.fromString((String) map.get("sellerUuid"));
        String sellerName = (String) map.get("sellerName");
        double price = ((Number) map.get("price")).doubleValue();
        long createdAt = map.containsKey("createdAt") ? ((Number) map.get("createdAt")).longValue() : System.currentTimeMillis();
        long expireTime = ((Number) map.get("expireTime")).longValue();
        String currencyType = map.containsKey("currencyType") ? (String) map.get("currencyType") : "VAULT";
        String category = map.containsKey("category") ? (String) map.get("category") : "DEFAULT";
        ItemStack item = AuctionItem.itemFromBase64((String) map.get("itemBase64"));
        return new AuctionItem(id, sellerUuid, sellerName, item, price, createdAt, expireTime, currencyType, category);
    }
}
