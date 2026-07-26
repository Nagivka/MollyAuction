package ua.nagivka.nGVKauction.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.inventory.ItemStack;
import ua.nagivka.nGVKauction.NGVKauction;
import ua.nagivka.nGVKauction.models.AuctionItem;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        config.setMaximumPoolSize(10);
        this.dataSource = new HikariDataSource(config);

        createTables();
    }

    private void createTables() {
        String query = "CREATE TABLE IF NOT EXISTS auction_items (" +
                "id VARCHAR(36) PRIMARY KEY, " +
                "seller_uuid VARCHAR(36) NOT NULL, " +
                "seller_name VARCHAR(16) NOT NULL, " +
                "item_bytes TEXT NOT NULL, " +
                "price DOUBLE NOT NULL, " +
                "created_at BIGINT DEFAULT 0, " +
                "expire_at BIGINT NOT NULL, " +
                "currency_type VARCHAR(32) DEFAULT 'VAULT', " +
                "category VARCHAR(32) DEFAULT 'DEFAULT');";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(query);
        } catch (SQLException e) {
            plugin.getLogger().severe(e.getMessage());
        }
    }

    public void saveAuctionItem(AuctionItem item) {
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
            plugin.getLogger().severe(e.getMessage());
        }
    }

    public List<AuctionItem> loadAllItems() {
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
            plugin.getLogger().severe(e.getMessage());
        }
        return list;
    }

    public void deleteAuctionItem(UUID id) {
        String query = "DELETE FROM auction_items WHERE id = ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, id.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe(e.getMessage());
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}