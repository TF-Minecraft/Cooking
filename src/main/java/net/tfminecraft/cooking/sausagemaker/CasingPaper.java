package net.tfminecraft.cooking.sausagemaker;

import org.bukkit.inventory.ItemStack;

import net.tfminecraft.tlibs.TLibs;

/**
 * Sausage casing is vanilla paper. {@code v.paper} rejects custom items that
 * only use paper as their base, such as the masher and a scooped bowl of soup.
 */
final class CasingPaper {

    static final String PATH = "v.paper";

    private CasingPaper() {}

    static boolean isCasing(ItemStack stack) {
        return TLibs.getItemAPI().getChecker().checkItemWithPath(stack, PATH);
    }
}
