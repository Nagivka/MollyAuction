package ua.nagivka.mollyauction.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Level;

public final class FolderMigrator {

    private FolderMigrator() {}

    /**
     * Выполняет автоматическую миграцию файлов из папки старого плагина (NGVKauction)
     * в папку нового плагина (MollyAuction).
     */
    public static void migrateOldPluginFolder(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        File serverPluginsDir = dataFolder.getParentFile();
        if (serverPluginsDir == null || !serverPluginsDir.exists()) {
            return;
        }

        File oldFolder = new File(serverPluginsDir, "NGVKauction");
        if (!oldFolder.exists() || !oldFolder.isDirectory()) {
            return;
        }

        File marker = new File(oldFolder, ".migrated");
        if (marker.exists()) {
            return;
        }

        plugin.getLogger().info("====================================================");
        plugin.getLogger().info("Обнаружена папка старого плагина NGVKauction!");
        plugin.getLogger().info("Начинается автоматический перенос файлов в MollyAuction...");
        plugin.getLogger().info("====================================================");

        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().severe("Не удалось создать целевую папку плагина: " + dataFolder.getAbsolutePath());
            return;
        }

        Path oldPath = oldFolder.toPath();
        Path newPath = dataFolder.toPath();

        try {
            Files.walkFileTree(oldPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path rel = oldPath.relativize(dir);
                    Path targetDir = newPath.resolve(rel);
                    if (!Files.exists(targetDir)) {
                        Files.createDirectories(targetDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path rel = oldPath.relativize(file);
                    Path targetFile = newPath.resolve(rel);

                    // Копируем файл (если в целевой папке файл еще не был изменен или отсутствует)
                    if (!Files.exists(targetFile) || Files.size(targetFile) == 0) {
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                        plugin.getLogger().info("Мигрирован файл: " + rel);
                    } else {
                        plugin.getLogger().info("Файл уже существует в MollyAuction, пропуск: " + rel);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // Помечаем старую папку маркером или переименовываем
            File migratedFolder = new File(serverPluginsDir, "NGVKauction_migrated");
            boolean renamed = false;
            if (!migratedFolder.exists()) {
                renamed = oldFolder.renameTo(migratedFolder);
            }

            if (renamed) {
                plugin.getLogger().info("Старая папка переименована в: NGVKauction_migrated");
            } else {
                try {
                    new File(oldFolder, ".migrated").createNewFile();
                    plugin.getLogger().info("В старой папке создан маркер миграции: .migrated");
                } catch (IOException e) {
                    plugin.getLogger().warning("Не удалось создать маркер миграции: " + e.getMessage());
                }
            }

            plugin.getLogger().info("====================================================");
            plugin.getLogger().info("Миграция из NGVKauction в MollyAuction успешно завершена!");
            plugin.getLogger().info("====================================================");

        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка при миграции файлов из NGVKauction: " + e.getMessage(), e);
        }
    }
}
