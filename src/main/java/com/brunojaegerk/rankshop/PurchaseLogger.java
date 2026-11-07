
package com.brunojaegerk.rankshop;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class PurchaseLogger {

    private final Main plugin;
    private final File logFile;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public PurchaseLogger(Main plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException(
                    "plugin no puede ser null"
            );
        }

        this.plugin = plugin;
        this.logFile = new File(
                plugin.getDataFolder(),
                "purchases.log"
        );
    }

    public synchronized void log(
            String playerName,
            String rankId,
            String rankName,
            double price,
            String status,
            String reason
    ) {
        String timestamp = dateFormat.format(new Date());

        String line = "[" + timestamp + "]"
                + " status=" + clean(status)
                + " player=\"" + clean(playerName) + "\""
                + " rank_id=\"" + clean(rankId) + "\""
                + " rank=\"" + clean(rankName) + "\""
                + " price=" + String.format(
                Locale.ROOT, "%.2f", price
        )
                + " reason=\"" + clean(reason) + "\"";

        try (BufferedWriter writer = new BufferedWriter(
                new FileWriter(logFile, true))) {

            writer.write(line);
            writer.newLine();

        } catch (IOException exception) {
            plugin.getLogger().warning(
                    "No se pudo escribir en purchases.log: "
                            + exception.getMessage()
            );
        }
    }

    private String clean(String value) {
        if (value == null) {
            return "unknown";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}

