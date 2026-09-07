package ua.nagivka.mollyauction.managers;

import ua.nagivka.mollyauction.MollyAuction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogManager {

    private final MollyAuction plugin;
    private final File logFile;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "MollyAuction-Logger");
        t.setDaemon(true);
        return t;
    });

    public LogManager(MollyAuction plugin) {
        this.plugin = plugin;
        File logsDir = new File(plugin.getDataFolder(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        this.logFile = new File(logsDir, "auction.log");
    }

    public void log(String action, String details) {
        logExecutor.submit(() -> {
            try (FileOutputStream fos = new FileOutputStream(logFile, true);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                 BufferedWriter bw = new BufferedWriter(osw);
                 PrintWriter pw = new PrintWriter(bw)) {
                String timestamp = dtf.format(LocalDateTime.now());
                pw.println("[" + timestamp + "] [" + action.toUpperCase() + "] " + details);
                pw.flush();
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка записи лога: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        logExecutor.shutdown();
    }
}
