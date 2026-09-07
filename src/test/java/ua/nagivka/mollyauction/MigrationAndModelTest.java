package ua.nagivka.mollyauction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.nagivka.mollyauction.models.Category;
import ua.nagivka.mollyauction.models.SortMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.jupiter.api.Assertions.*;

public class MigrationAndModelTest {

    @Test
    public void testCategoryCycle() {
        Category cat = Category.ALL;
        assertEquals("Все", cat.getName());
        Category next = cat.next();
        assertNotNull(next);
        assertNotEquals(cat, next);
        Category prev = next.previous();
        assertEquals(cat, prev);
    }

    @Test
    public void testSortModeCycle() {
        SortMode mode = SortMode.DEFAULT;
        assertEquals("По умолчанию", mode.getName());
        SortMode next = mode.next();
        assertNotNull(next);
        assertNotEquals(mode, next);
        SortMode prev = next.previous();
        assertEquals(mode, prev);
    }

    @Test
    public void testFolderMigrationSimulation(@TempDir Path tempServerPluginsDir) throws IOException {
        Path oldFolder = tempServerPluginsDir.resolve("NGVKauction");
        Path newFolder = tempServerPluginsDir.resolve("MollyAuction");

        Files.createDirectories(oldFolder);
        Files.createDirectories(oldFolder.resolve("logs"));

        Files.writeString(oldFolder.resolve("config.yml"), "settings: {prefix: 'test'}");
        Files.writeString(oldFolder.resolve("database.db"), "mock database binary data");
        Files.writeString(oldFolder.resolve("logs").resolve("auction.log"), "[LOG] item sold");

        // Симуляция алгоритма FolderMigrator
        Files.createDirectories(newFolder);
        Files.walkFileTree(oldFolder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path rel = oldFolder.relativize(dir);
                Path targetDir = newFolder.resolve(rel);
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path rel = oldFolder.relativize(file);
                Path targetFile = newFolder.resolve(rel);
                if (!Files.exists(targetFile) || Files.size(targetFile) == 0) {
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // Проверяем, что все файлы перенесены в MollyAuction
        assertTrue(Files.exists(newFolder.resolve("config.yml")));
        assertEquals("settings: {prefix: 'test'}", Files.readString(newFolder.resolve("config.yml")));

        assertTrue(Files.exists(newFolder.resolve("database.db")));
        assertEquals("mock database binary data", Files.readString(newFolder.resolve("database.db")));

        assertTrue(Files.exists(newFolder.resolve("logs").resolve("auction.log")));
        assertEquals("[LOG] item sold", Files.readString(newFolder.resolve("logs").resolve("auction.log")));

        // Проверяем переименование старой папки
        File migratedFolder = tempServerPluginsDir.resolve("NGVKauction_migrated").toFile();
        boolean renamed = oldFolder.toFile().renameTo(migratedFolder);
        assertTrue(renamed);
        assertTrue(migratedFolder.exists());
        assertFalse(Files.exists(oldFolder));
    }

    @Test
    public void testOfflineNotificationModel() {
        java.util.UUID uuid = java.util.UUID.randomUUID();
        ua.nagivka.mollyauction.models.OfflineNotification notif =
                new ua.nagivka.mollyauction.models.OfflineNotification(uuid, "Diamond Sword", 1, 1500.0, "Player123");

        assertEquals(uuid, notif.getPlayerUuid());
        assertEquals("Diamond Sword", notif.getItemName());
        assertEquals(1, notif.getAmount());
        assertEquals(1500.0, notif.getPrice());
        assertEquals("Player123", notif.getBuyerName());
        assertTrue(notif.getSoldAt() > 0);
    }
}
