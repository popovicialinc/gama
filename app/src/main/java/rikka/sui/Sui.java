package rikka.sui;

/**
 * Minimal stub of Sui — vendored to avoid the sui library dependency.
 * Sui is an alternative to Shizuku that runs as a Zygote injector.
 * This stub always reports Sui as unavailable, which is correct for
 * standard Shizuku-only usage.
 */
public class Sui {

    /**
     * Returns true if Sui (not Shizuku) is the active provider.
     * Always returns false in this stub — app uses Shizuku only.
     */
    public static boolean isSui() {
        return false;
    }

    /**
     * Initialises Sui for the given package. No-op in this stub.
     *
     * @param packageName the app's package name
     * @return false — Sui is not present
     */
    public static boolean init(String packageName) {
        return false;
    }
}
