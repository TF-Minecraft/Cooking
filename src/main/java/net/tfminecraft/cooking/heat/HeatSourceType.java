package net.tfminecraft.cooking.heat;

import java.util.Locale;

public enum HeatSourceType {
    OVEN,
    CAMPFIRE;

    public static HeatSourceType fromConfig(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "oven" -> OVEN;
            case "campfire" -> CAMPFIRE;
            default -> null;
        };
    }
}
