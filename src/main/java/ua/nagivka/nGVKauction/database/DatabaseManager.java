package ua.nagivka.nGVKauction.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.inventory.ItemStack;
import ua.nagivka.nGVKauction.NGVKauction;
import ua.nagivka.nGVKauction.models.AuctionItem;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final NGVKauction plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(NGVKauction plugin) {
        this.plugin = plugin;
    }

    public void init() {
        HikariConfig config = new HikariConfig();
        String dbType = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();

        if (dbType.equals("MYSQL") || dbType.equals("MARIADB")) {
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String db = plugin.getConfig().getString("database.database", "minecraft");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&characterEncoding=utf8");
            config.setUsername(plugin.getConfig().getString("database.username", "root"));
            config.setPassword(plugin.getConfig().getString("database.password", ""));
        } else {
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
        }

        config.setMaximumPoolSize(plugin.getConfig().getInt("database.pool-size", 10));
        config.setPoolName("NGVKauction-HikariPool");
        this.dataSource = new HikariDataSource(config);

        createTables();
    }

    private void createTables() {
        String itemsTable = """
                CREATE TABLE IF NOT EXISTS auction_items (
                    id VARCHAR(36) PRIMARY KEY,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(16) NOT NULL,
                    item_bytes TEXT NOT NULL,
                    price DOUBLE NOT NULL,
                    created_at BIGINT DEFAULT 0,
                    expire_at BIGINT NOT NULL,
                    currency_type VARCHAR(32) DEFAULT 'VAULT',
                    category VARCHAR(32) DEFAULT 'DEFAULT'
                );
                """;

        String expiredTable = """
                CREATE TABLE IF NOT EXISTS auction_expired (
                    id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    item_bytes TEXT NOT NULL,
                    added_at BIGINT NOT NULL
                );
                """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(itemsTable);
            stmt.execute(expiredTable);
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка инициализации таблиц: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> saveAuctionItemAsync(AuctionItem item) {
        return CompletableFuture.runAsync(() -> {
            String query = "REPLACE INTO auction_items (id, seller_uuid, seller_name, item_bytes, price, created_at, expire_at, currency_type, category) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, item.getId().toString());
                pstmt.setString(2, item.getSellerUuid().toString());
                pstmt.setString(3, item.getSellerName());
                pstmt.setString(4, AuctionItem.itemToBase64(item.getItem()));
                pstmt.setDouble(5, item.getPrice());
                pstmt.setLong(6, item.getCreatedAt());
                pstmt.setLong(7, item.getExpireTime());
                pstmt.setString(8, item.getCurrencyType());
                pstmt.setString(9, item.getCategory());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка сохранения лота: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> deleteAuctionItemAsync(UUID id) {
        return CompletableFuture.runAsync(() -> {
            String query = "DELETE FROM auction_items WHERE id = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, id.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка удаления лота: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<AuctionItem>> loadAllActiveItemsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<AuctionItem> list = new ArrayList<>();
            String query = "SELECT * FROM auction_items;";
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
                    String sellerName = rs.getString("seller_name");
                    ItemStack item = AuctionItem.itemFromBase64(rs.getString("item_bytes"));
                    double price = rs.getDouble("price");
                    long createdAt = rs.getLong("created_at");
                    if (createdAt == 0) createdAt = System.currentTimeMillis();
                    long expireAt = rs.getLong("expire_at");
                    String currency = rs.getString("currency_type");
                    String category = rs.getString("category");

                    if (item != null) {
                        list.add(new AuctionItem(id, sellerUuid, sellerName, item, price, createdAt, expireAt, currency, category));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка загрузки активных лотов: " + e.getMessage());
            }
            return list;
        });
    }

    public CompletableFuture<Void> saveExpiredItemAsync(UUID playerUuid, ItemStack item) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO auction_expired (id, player_uuid, item_bytes, added_at) VALUES (?, ?, ?, ?);";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, UUID.randomUUID().toString());
                pstmt.setString(2, playerUuid.toString());
                pstmt.setString(3, AuctionItem.itemToBase64(item));
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка сохранения просроченного предмета: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Map<UUID, List<ItemStack>>> loadAllExpiredItemsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, List<ItemStack>> map = new HashMap<>();
            String query = "SELECT player_uuid, item_bytes FROM auction_expired;";
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    ItemStack item = AuctionItem.itemFromBase64(rs.getString("item_bytes"));
                    if (item != null) {
                        map.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(item);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка загрузки хранилища просроченных товаров: " + e.getMessage());
            }
            return map;
        });
    }

    public CompletableFuture<Void> clearExpiredItemsForPlayerAsync(UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            String query = "DELETE FROM auction_expired WHERE player_uuid = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка очистки хранилища: " + e.getMessage());
            }
        });
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}