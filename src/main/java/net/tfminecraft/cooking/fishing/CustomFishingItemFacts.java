package net.tfminecraft.cooking.fishing;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

final class CustomFishingItemFacts {
    private CustomFishingItemFacts() {}

    static String lootId(ItemStack stack) {
        Object value = tag(stack, "getCustomFishingItemID");
        if (!(value instanceof String id) || id.isBlank()) {
            return null;
        }
        return id;
    }

    static Double size(ItemStack stack) {
        Object value = tag(stack, "getFishSize");
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static Object tag(ItemStack stack, String methodName) {
        if (stack == null) {
            return null;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CustomFishing");
        if (plugin == null || !plugin.isEnabled()) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.momirealms.customfishing.api.BukkitCustomFishingPlugin");
            Object instance = api.getMethod("getInstance").invoke(null);
            if (instance == null) {
                return null;
            }
            Object manager = api.getMethod("getItemManager").invoke(instance);
            Method method = manager.getClass().getMethod(methodName, ItemStack.class);
            return method.invoke(manager, stack);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }
}
