package ua.nagivka.mollyauction.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.inventory.ItemStack;
import ua.nagivka.mollyauction.MollyAuction;
import ua.nagivka.mollyauction.models.AuctionItem;
import ua.nagivka.mollyauction.models.ExpiredItem;
import ua.nagivka.mollyauction.models.OfflineNotification;
import ua.nagivka.mollyauction.models.TransactionRecord;

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

    private final MollyAuction plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(MollyAuction plugin) {
        this.plugin = plugin;
    }

    public void init() {
        HikariConfig config = new HikariConfig();
        String dbType = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();

        if (dbType.equals("MYSQL") || dbType.equals("MARIADB")) {
            String host = plugin.getConfig().getString("database.host", "localhost");
            int port = plugin.getConfig().getInt("database.port", 3306);
            String db = plugin.getConfig().getString("database.database", "minecraft");
            config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&characterEncoding=utf8&allowPublicKeyRetrieval=true");
            config.setUsername(plugin.getConfig().getString("database.username", "root"));
            config.setPassword(plugin.getConfig().getString("database.password", ""));
            config.setMaximumPoolSize(Math.max(2, plugin.getConfig().getInt("database.pool-size", 10)));
        } else {
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
            config.setConnectionTimeout(15000);
            config.addDataSourceProperty("busy_timeout", "5000");
        }

        config.setPoolName("MollyAuction-HikariPool");
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

        String historyTable = """
                CREATE TABLE IF NOT EXISTS auction_history (
                    id VARCHAR(36) PRIMARY KEY,
                    seller_uuid VARCHAR(36) NOT NULL,
                    seller_name VARCHAR(16) NOT NULL,
                    buyer_uuid VARCHAR(36) NOT NULL,
                    buyer_name VARCHAR(16) NOT NULL,
                    item_bytes TEXT NOT NULL,
                    price DOUBLE NOT NULL,
                    amount INT NOT NULL,
                    timestamp BIGINT NOT NULL
                );
                """;

        String notificationsTable = """
                CREATE TABLE IF NOT EXISTS auction_offline_notifications (
                    id VARCHAR(36) PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    item_name VARCHAR(128) NOT NULL,
                    amount INT NOT NULL,
                    price DOUBLE NOT NULL,
                    buyer_name VARCHAR(32) NOT NULL,
                    sold_at BIGINT NOT NULL
                );
                """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String dbType = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
            if (dbType.equals("SQLITE")) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
            }
            stmt.execute(itemsTable);
            stmt.execute(expiredTable);
            stmt.execute(historyTable);
            stmt.execute(notificationsTable);
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка инициализации таблиц базы данных: " + e.getMessage());
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

    public CompletableFuture<Void> saveExpiredItemAsync(ExpiredItem item) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO auction_expired (id, player_uuid, item_bytes, added_at) VALUES (?, ?, ?, ?);";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, item.getId().toString());
                pstmt.setString(2, item.getPlayerUuid().toString());
                pstmt.setString(3, AuctionItem.itemToBase64(item.getItem()));
                pstmt.setLong(4, item.getAddedAt());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка сохранения просроченного предмета: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> deleteExpiredItemAsync(UUID id) {
        return CompletableFuture.runAsync(() -> {
            String query = "DELETE FROM auction_expired WHERE id = ?;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, id.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка удаления просроченного предмета: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> deleteExpiredItemsBatchAsync(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            StringBuilder sb = new StringBuilder("DELETE FROM auction_expired WHERE id IN (");
            for (int i = 0; i < ids.size(); i++) {
                sb.append("?");
                if (i < ids.size() - 1) sb.append(",");
            }
            sb.append(");");

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sb.toString())) {
                for (int i = 0; i < ids.size(); i++) {
                    pstmt.setString(i + 1, ids.get(i).toString());
                }
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка пакетного удаления просроченных предметов: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Map<UUID, List<ExpiredItem>>> loadAllExpiredItemsAsync() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, List<ExpiredItem>> map = new HashMap<>();
            String query = "SELECT id, player_uuid, item_bytes, added_at FROM auction_expired;";
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    ItemStack item = AuctionItem.itemFromBase64(rs.getString("item_bytes"));
                    long addedAt = rs.getLong("added_at");
                    if (item != null) {
                        map.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(new ExpiredItem(id, playerUuid, item, addedAt));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка загрузки хранилища просроченных товаров: " + e.getMessage());
            }
            return map;
        });
    }

    // --- История транзакций ---
    public CompletableFuture<Void> saveTransactionRecordAsync(TransactionRecord record) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO auction_history (id, seller_uuid, seller_name, buyer_uuid, buyer_name, item_bytes, price, amount, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, record.getId().toString());
                pstmt.setString(2, record.getSellerUuid().toString());
                pstmt.setString(3, record.getSellerName());
                pstmt.setString(4, record.getBuyerUuid().toString());
                pstmt.setString(5, record.getBuyerName());
                pstmt.setString(6, AuctionItem.itemToBase64(record.getItem()));
                pstmt.setDouble(7, record.getPrice());
                pstmt.setInt(8, record.getAmount());
                pstmt.setLong(9, record.getTimestamp());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка сохранения истории транзакции: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<TransactionRecord>> loadPlayerHistoryAsync(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<TransactionRecord> list = new ArrayList<>();
            String query = "SELECT * FROM auction_history WHERE seller_uuid = ? OR buyer_uuid = ? ORDER BY timestamp DESC LIMIT 100;";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        UUID id = UUID.fromString(rs.getString("id"));
                        UUID sUuid = UUID.fromString(rs.getString("seller_uuid"));
                        String sName = rs.getString("seller_name");
                        UUID bUuid = UUID.fromString(rs.getString("buyer_uuid"));
                        String bName = rs.getString("buyer_name");
                        ItemStack item = AuctionItem.itemFromBase64(rs.getString("item_bytes"));
                        double price = rs.getDouble("price");
                        int amount = rs.getInt("amount");
                        long time = rs.getLong("timestamp");

                        if (item != null) {
                            list.add(new TransactionRecord(id, sUuid, sName, bUuid, bName, item, price, amount, time));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка загрузки истории игрока: " + e.getMessage());
            }
            return list;
        });
    }

    // --- Оффлайн-уведомления о продажах ---
    public CompletableFuture<Void> saveOfflineNotificationAsync(OfflineNotification notif) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO auction_offline_notifications (id, player_uuid, item_name, amount, price, buyer_name, sold_at) VALUES (?, ?, ?, ?, ?, ?, ?);";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, notif.getId().toString());
                pstmt.setString(2, notif.getPlayerUuid().toString());
                pstmt.setString(3, notif.getItemName());
                pstmt.setInt(4, notif.getAmount());
                pstmt.setDouble(5, notif.getPrice());
                pstmt.setString(6, notif.getBuyerName());
                pstmt.setLong(7, notif.getSoldAt());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка сохранения оффлайн-уведомления: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<OfflineNotification>> loadAndClearOfflineNotificationsAsync(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<OfflineNotification> list = new ArrayList<>();
            String selectQuery = "SELECT * FROM auction_offline_notifications WHERE player_uuid = ?;";
            String deleteQuery = "DELETE FROM auction_offline_notifications WHERE player_uuid = ?;";

            try (Connection conn = dataSource.getConnection()) {
                try (PreparedStatement pstmt = conn.prepareStatement(selectQuery)) {
                    pstmt.setString(1, playerUuid.toString());
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            UUID id = UUID.fromString(rs.getString("id"));
                            String itemName = rs.getString("item_name");
                            int amount = rs.getInt("amount");
                            double price = rs.getDouble("price");
                            String buyerName = rs.getString("buyer_name");
                            long soldAt = rs.getLong("sold_at");
                            list.add(new OfflineNotification(id, playerUuid, itemName, amount, price, buyerName, soldAt));
                        }
                    }
                }

                if (!list.isEmpty()) {
                    try (PreparedStatement pstmt = conn.prepareStatement(deleteQuery)) {
                        pstmt.setString(1, playerUuid.toString());
                        pstmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Ошибка обработки оффлайн-уведомлений: " + e.getMessage());
            }
            return list;
        });
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
