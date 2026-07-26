package ua.nagivka.nGVKauction.managers;

import ua.nagivka.nGVKauction.NGVKauction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

public class LogManager {

    private final NGVKauction plugin;
    private final File logFile;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public LogManager(NGVKauction plugin) {
        this.plugin = plugin;
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        this.logFile = new File(logsDir, "auction.log");
    }

    public void log(String action, String details) {
        CompletableFuture.runAsync(() -> {
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                String timestamp = dtf.format(LocalDateTime.now());
                pw.println("[" + timestamp + "] [" + action.toUpperCase() + "] " + details);
            } catch (IOException e) {
                plugin.getLogger().severe("Ошибка записи лога: " + e.getMessage());
            }
        });
    }
}