package com.hades.client.util;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HadesLogger {
    private static HadesLogger instance;
    private PrintWriter writer;
    private final Path logFile;
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private boolean initialized;

    private HadesLogger() {
        Path logDir = Paths.get(System.getProperty("user.home"), ".hades", "logs");
        try {
            Files.createDirectories(logDir);
        } catch (IOException ignored) {}

        String timestamp = LocalDateTime.now().format(dateFormat);
        logFile = logDir.resolve("hades_" + timestamp + ".log");

        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile.toFile(), true)), true);
            initialized = true;
            info("=== Hades Client Log gestartet ===");
            info("Log-Datei: " + logFile.toAbsolutePath());
            info("Java: " + System.getProperty("java.version"));
            info("OS: " + System.getProperty("os.name"));
        } catch (IOException e) {
            System.err.println("[Hades] Konnte Log-Datei nicht erstellen: " + e.getMessage());
            initialized = false;
        }
    }

    public static synchronized HadesLogger get() {
        if (instance == null) {
            instance = new HadesLogger();
        }
        return instance;
    }

    public void info(String message) {
        log("INFO", message);
    }

    public void warn(String message) {
        log("WARN", message);
    }

    public void error(String message) {
        log("ERROR", message);
    }

    public void error(String message, Throwable throwable) {
        log("ERROR", message);
        if (initialized && throwable != null) {
            throwable.printStackTrace(writer);
            writer.flush();
        }
    }

    public void debug(String message) {
        log("DEBUG", message);
    }

    private void log(String level, String message) {
        String timestamp = LocalDateTime.now().format(timeFormat);
        String threadName = Thread.currentThread().getName();
        String formatted = String.format("[%s] [%s/%s] %s", timestamp, threadName, level, message);

        // In eigene Datei schreiben
        if (initialized) {
            writer.println(formatted);
            writer.flush();
        }

        // Auch in stdout für Minecraft-Konsole (optional)
        System.out.println("[Hades] " + formatted);
    }

    public void close() {
        if (initialized && writer != null) {
            info("=== Hades Client Log beendet ===");
            writer.close();
        }
    }

    public Path getLogFile() {
        return logFile;
    }
}