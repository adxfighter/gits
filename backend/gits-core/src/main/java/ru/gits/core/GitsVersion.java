package ru.gits.core;

/**
 * Build-independent version information shared by all GITS modules.
 */
public final class GitsVersion {

    public static final String PRODUCT = "GITS";
    public static final String VERSION = "1.0.0-SNAPSHOT";

    private GitsVersion() {
    }

    public static String display() {
        return PRODUCT + " " + VERSION;
    }
}
