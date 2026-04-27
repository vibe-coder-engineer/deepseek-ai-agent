package ru.sibgatulinanton.logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConsoleLogger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void info(String message) {
        log("INFO", message);
    }

    public void warn(String message) {
        log("WARN", message);
    }

    public void error(String message) {
        log("ERROR", message);
    }

    public void block(String title, String value) {
        String ts = LocalDateTime.now().format(TS);
        System.out.println("[" + ts + "] [" + title + "] >>>");
        System.out.println(value == null ? "<null>" : value);
        System.out.println("[" + ts + "] [" + title + "] <<<");
    }

    private void log(String level, String message) {
        String ts = LocalDateTime.now().format(TS);
        System.out.println("[" + ts + "] [" + level + "] " + message);
    }
}
