package net.tfminecraft.cooking.nutrition;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.bukkit.entity.Player;

import net.tfminecraft.RPCharacters.Objects.RPCharacter;
import net.tfminecraft.cooking.Cooking;

public final class NutritionLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
    private static final Object LOCK = new Object();

    private static volatile boolean enabled;
    private static volatile Path logFile;

    private NutritionLog() {}

    public static void configure(boolean loggingEnabled, boolean wipeLog, File dataFolder) {
        enabled = loggingEnabled;
        logFile = dataFolder == null ? null : dataFolder.toPath().resolve("logs").resolve("nutrition.log");
        if (wipeLog && logFile != null) {
            synchronized (LOCK) {
                try {
                    Files.deleteIfExists(logFile);
                } catch (IOException ex) {
                    warn("Failed to wipe nutrition.log: " + ex.getMessage());
                }
            }
        }
        append("CONFIG", null, null,
                "enabled=" + loggingEnabled
                + " wipe=" + wipeLog
                + " maxFood=" + NutritionConfig.maxFood()
                + " drainAmount=" + NutritionConfig.drainAmount()
                + " drainIntervalSeconds=" + NutritionConfig.drainIntervalSeconds());
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void append(String event, Player player, RPCharacter character, String details) {
        if (!enabled || logFile == null) {
            return;
        }
        StringBuilder line = new StringBuilder();
        line.append(TIME.format(Instant.now())).append(" event=").append(clean(event));
        if (player != null) {
            line.append(" player=").append(clean(player.getName()))
                    .append(" playerUuid=").append(player.getUniqueId());
        }
        if (character != null) {
            line.append(" character=").append(clean(character.getName()))
                    .append(" characterId=").append(clean(character.getId()))
                    .append(" pool=").append(character.getFoodValue())
                    .append(" diet=").append(character.getDietScore())
                    .append(" rawDiet=").append(character.getRawDietScore());
        }
        if (details != null && !details.isBlank()) {
            line.append(' ').append(details.replace('\r', ' ').replace('\n', ' '));
        }
        write(line.toString());
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace(' ', '_').replace('\r', '_').replace('\n', '_');
    }

    private static void write(String line) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(logFile.getParent());
                try (BufferedWriter writer = Files.newBufferedWriter(
                        logFile,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND)) {
                    writer.write(line);
                    writer.newLine();
                }
            } catch (IOException ex) {
                warn("Failed to write nutrition.log: " + ex.getMessage());
            }
        }
    }

    private static void warn(String message) {
        if (Cooking.plugin != null) {
            Cooking.plugin.getLogger().warning("[NutritionLog] " + message);
        }
    }
}
