package net.tfminecraft.cooking.fishing;

public final class CatchSize {
    private CatchSize() {}

    public static Integer roundCm(Double size) {
        if (size == null || !Double.isFinite(size) || size <= 0.0) {
            return null;
        }
        long rounded = Math.round(size);
        if (rounded < 1L || rounded > Integer.MAX_VALUE) {
            return null;
        }
        return (int) rounded;
    }
}
