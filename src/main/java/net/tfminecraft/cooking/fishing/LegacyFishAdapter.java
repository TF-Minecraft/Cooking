package net.tfminecraft.cooking.fishing;

public final class LegacyFishAdapter {
    private LegacyFishAdapter() {}

    public enum Action {
        UNTOUCHED,
        REPLACE
    }

    public record Decision(Action action, CatchMapping mapping, int sizeCm) {
        public boolean replaces() {
            return action == Action.REPLACE;
        }

        static Decision untouched() {
            return new Decision(Action.UNTOUCHED, null, 0);
        }

        static Decision replace(CatchMapping mapping, int sizeCm) {
            return new Decision(Action.REPLACE, mapping, sizeCm);
        }
    }

    public static Decision decide(boolean hasFoodId, String material, Integer modelData, String lootId, Double size) {
        if (hasFoodId || material == null || !"COD".equalsIgnoreCase(material)) {
            return Decision.untouched();
        }
        CatchMapping mapping;
        if (lootId != null && !lootId.isBlank()) {
            mapping = CustomFishingCatalog.find(lootId);
            if (mapping == null) {
                return Decision.untouched();
            }
        } else if (modelData != null) {
            mapping = CustomFishingCatalog.findModel(modelData);
        } else {
            mapping = null;
        }
        if (mapping == null) {
            return Decision.untouched();
        }
        Integer centimetres = CatchSize.roundCm(size);
        if (centimetres == null) {
            centimetres = CustomFishingCatalog.legacySizeCm();
        }
        return Decision.replace(mapping, centimetres);
    }
}
