package net.tfminecraft.cooking.fishing;

public final class VanillaFishAdapter {
    private VanillaFishAdapter() {}

    public enum Action {
        UNTOUCHED,
        REPLACE
    }

    public record Decision(Action action, VanillaFish fish) {
        public boolean replaces() {
            return action == Action.REPLACE;
        }

        static Decision untouched() {
            return new Decision(Action.UNTOUCHED, null);
        }

        static Decision replace(VanillaFish fish) {
            return new Decision(Action.REPLACE, fish);
        }
    }

    public static Decision decide(String material, boolean customModelData) {
        if (customModelData) {
            return Decision.untouched();
        }
        VanillaFish fish = CustomFishingCatalog.findVanilla(material);
        if (fish == null) {
            return Decision.untouched();
        }
        return Decision.replace(fish);
    }
}
