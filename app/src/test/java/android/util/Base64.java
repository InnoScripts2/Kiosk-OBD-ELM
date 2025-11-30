package android.util;

/** Minimal stub for android.util.Base64 used in JVM unit tests. */
public final class Base64 {
    public static final int NO_WRAP = 2;

    private Base64() {
    }

    public static String encodeToString(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encodeToString(input);
    }
}
