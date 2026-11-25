package org.sigf.app;

/** Entry point for a classpath-based native image. */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Main.main(args);
    }
}
