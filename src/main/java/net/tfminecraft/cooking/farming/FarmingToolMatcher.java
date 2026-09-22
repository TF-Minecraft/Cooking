package net.tfminecraft.cooking.farming;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;

public final class FarmingToolMatcher {

    private FarmingToolMatcher() {}

    public static FarmingToolDefinition matchTool(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) {
            return null;
        }
        for (FarmingToolDefinition definition : FarmingConfig.tools()) {
            if (TLibs.getItemAPI().getChecker().checkItemWithPath(tool, definition.path())) {
                return definition;
            }
        }
        return null;
    }

    public static FarmingCropDefinition cropFor(Material blockType) {
        if (blockType == null) {
            return null;
        }
        return FarmingConfig.cropFor(blockType);
    }
}
