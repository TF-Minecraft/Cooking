package net.tfminecraft.cooking.fishing;

public final class CustomFishingCatchAdapter {
    private CustomFishingCatchAdapter() {}

    public enum Action {
        UNTOUCHED,
        MALFORMED,
        REPLACE
    }

    public record Decision(Action action, CatchMapping mapping, Integer sizeCm, String lootId) {
        public boolean replacesItem() {
            return action == Action.REPLACE;
        }

        static Decision untouched() {
            return new Decision(Action.UNTOUCHED, null, null, null);
        }

        static Decision malformed(String lootId) {
            return new Decision(Action.MALFORMED, null, null, lootId);
        }

        static Decision replace(CatchMapping mapping, int sizeCm) {
            return new Decision(Action.REPLACE, mapping, sizeCm, mapping.id());
        }
    }

    public static Decision decide(String lootId, Double size, boolean itemEntity) {
        if (!itemEntity) {
            return Decision.untouched();
        }
        CatchMapping mapping = CustomFishingCatalog.find(lootId);
        if (mapping == null) {
            return Decision.untouched();
        }
        Integer centimetres = CatchSize.roundCm(size);
        if (centimetres == null) {
            return Decision.malformed(lootId);
        }
        return Decision.replace(mapping, centimetres);
    }
}
