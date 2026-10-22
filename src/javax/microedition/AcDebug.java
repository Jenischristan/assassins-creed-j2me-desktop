package javax.microedition;

/**
 * Tiny env-gated debug trace facility used for diagnosing input and audio
 * behaviour of the port. Enable with AC_DEBUG=sound,input (comma separated,
 * or "all"). Cost is a single boolean check per event when disabled.
 */
public final class AcDebug {
    public static final boolean SOUND;
    public static final boolean INPUT;
    public static final boolean SOUND_DUMP;
    public static final boolean PROBE;

    static {
        String spec = System.getProperty("ac.debug", System.getenv("AC_DEBUG"));
        if (spec == null) {
            spec = "";
        }
        spec = spec.trim().toLowerCase();
        boolean all = spec.contains("all");
        SOUND = all || spec.contains("sound");
        INPUT = all || spec.contains("input");
        SOUND_DUMP = all || spec.contains("dump");
        PROBE = all || spec.contains("probe");
    }

    private AcDebug() {
    }

    public static void log(String topic, String message) {
        System.out.println("[ac-debug/" + topic + "] " + System.currentTimeMillis()
                + " " + message);
    }
}
