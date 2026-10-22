package javax.microedition.lcdui;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public abstract class Canvas extends Displayable {
    public static final int UP = 1;
    public static final int DOWN = 6;
    public static final int LEFT = 2;
    public static final int RIGHT = 5;
    public static final int FIRE = 8;

    public static final int KEY_NUM0 = 48;
    public static final int KEY_NUM1 = 49;
    public static final int KEY_NUM2 = 50;
    public static final int KEY_NUM3 = 51;
    public static final int KEY_NUM4 = 52;
    public static final int KEY_NUM5 = 53;
    public static final int KEY_NUM6 = 54;
    public static final int KEY_NUM7 = 55;
    public static final int KEY_NUM8 = 56;
    public static final int KEY_NUM9 = 57;
    public static final int KEY_STAR = 42;
    public static final int KEY_POUND = 35;

    private boolean fullScreen;

    protected Canvas() {
    }

    protected abstract void paint(Graphics g);

    protected void keyPressed(int keyCode) {
    }

    protected void keyReleased(int keyCode) {
    }

    protected void keyRepeated(int keyCode) {
    }

    protected void pointerPressed(int x, int y) {
    }

    protected void pointerReleased(int x, int y) {
    }

    protected void pointerDragged(int x, int y) {
    }

    protected void showNotify() {
    }

    protected void hideNotify() {
    }

    protected void sizeChanged(int w, int h) {
    }

    public final void repaint() {
        Display.requestPaint(this);
    }

    public final void repaint(int x, int y, int width, int height) {
        repaint();
    }

    public final void serviceRepaints() {
        Display.serviceRepaints(this);
    }

    public final void setFullScreenMode(boolean mode) {
        fullScreen = mode;
    }

    public final boolean isFullScreenMode() {
        return fullScreen;
    }

    public final boolean hasPointerEvents() {
        return true;
    }

    public final boolean hasPointerMotionEvents() {
        return true;
    }

    public final boolean hasRepeatEvents() {
        return true;
    }

    public final boolean isDoubleBuffered() {
        return true;
    }

    public final int getGameAction(int keyCode) {
        switch (keyCode) {
            case KEY_NUM2:
                return UP;
            case KEY_NUM8:
                return DOWN;
            case KEY_NUM4:
                return LEFT;
            case KEY_NUM6:
                return RIGHT;
            case KEY_NUM5:
                return FIRE;
            default:
                return 0;
        }
    }

    public final int getKeyCode(int gameAction) {
        switch (gameAction) {
            case UP:
                return KEY_NUM2;
            case DOWN:
                return KEY_NUM8;
            case LEFT:
                return KEY_NUM4;
            case RIGHT:
                return KEY_NUM6;
            case FIRE:
                return KEY_NUM5;
            default:
                return 0;
        }
    }

    public final String getKeyName(int keyCode) {
        if (keyCode >= KEY_NUM0 && keyCode <= KEY_NUM9) {
            return String.valueOf((char) keyCode);
        }
        return "key " + keyCode;
    }

    // The MIDlet's pointer/key handlers can crash on data-dependent game bugs
    // (e.g. the e.e[13] touch sprite, now null-guarded by tools/PatchE.java).
    // A throw on the AWT event thread used to kill the rest of the launcher's
    // event handling (held-key restore, pointer cleanup). The dispatch
    // wrappers contain the failure and log it once per distinct cause -
    // coordinates and key codes are stripped from the signature so repeated
    // events at different positions do not flood the console.
    private static final Map<String, Boolean> reportedDispatchFailures =
            new HashMap<String, Boolean>();

    private void safeDispatch(String what, Runnable run) {
        boolean debug = javax.microedition.AcDebug.INPUT;
        if (debug) {
            javax.microedition.AcDebug.log("input", what + " " + gameState());
        }
        try {
            run.run();
        } catch (Throwable t) {
            // Collapse "pointerReleased 170,421" / "pointerReleased 55,634" ...
            // into one signature per distinct failure site.
            String op = what;
            int sp = op.indexOf(' ');
            if (sp > 0) {
                op = op.substring(0, sp);
            }
            StringBuilder sig = new StringBuilder(op).append(" <- ").append(t);
            StackTraceElement[] st = t.getStackTrace();
            for (int i = 0; i < st.length && i < 8; i++) {
                sig.append(" / ").append(st[i]);
            }
            String key = sig.toString();
            synchronized (reportedDispatchFailures) {
                if (reportedDispatchFailures.put(key, Boolean.TRUE) == null) {
                    System.err.println("[Launcher] suppressed MIDlet " + what
                            + ": " + t);
                    t.printStackTrace();
                }
            }
        }
        if (debug) {
            javax.microedition.AcDebug.log("input", "  -> after " + what
                    + " " + gameState());
        }
    }

    public final void dispatchKeyPressed(final int keyCode) {
        safeDispatch("keyPressed " + keyCode, new Runnable() {
            public void run() {
                keyPressed(keyCode);
            }
        });
    }

    public final void dispatchKeyReleased(final int keyCode) {
        safeDispatch("keyReleased " + keyCode, new Runnable() {
            public void run() {
                keyReleased(keyCode);
            }
        });
    }

    public final void dispatchKeyRepeated(final int keyCode) {
        safeDispatch("keyRepeated " + keyCode, new Runnable() {
            public void run() {
                keyRepeated(keyCode);
            }
        });
    }

    public final void dispatchPointerPressed(final int x, final int y) {
        safeDispatch("pointerPressed " + x + "," + y, new Runnable() {
            public void run() {
                pointerPressed(x, y);
            }
        });
    }

    public final void dispatchPointerReleased(final int x, final int y) {
        safeDispatch("pointerReleased " + x + "," + y, new Runnable() {
            public void run() {
                pointerReleased(x, y);
            }
        });
    }

    public final void dispatchPointerDragged(final int x, final int y) {
        safeDispatch("pointerDragged " + x + "," + y, new Runnable() {
            public void run() {
                pointerDragged(x, y);
            }
        });
    }

    // --- reflective access to the MIDlet's own input state -----------------
    // The launcher needs to know when the game has wiped its held-key
    // bitmask (the touch handlers do that on every pointer release and when
    // a touch action starts) so it can re-assert keys that are still
    // physically held. Field names belong to the obfuscated MIDlet; if the
    // layout ever changes the helpers return -1 and all recovery logic
    // quietly disables itself.
    private Map<String, Field> gameFields;

    private Field gameField(String name) {
        Map<String, Field> fields = gameFields;
        if (fields == null) {
            fields = new HashMap<String, Field>();
            Class<?> c = getClass();
            while (c != null && c != Canvas.class && c != Displayable.class
                    && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == int.class || f.getType() == boolean.class) {
                        f.setAccessible(true);
                        fields.putIfAbsent(f.getName(), f);
                    }
                }
                c = c.getSuperclass();
            }
            gameFields = fields;
        }
        return fields.get(name);
    }

    /**
     * @return the MIDlet's current held-key bitmask, or -1 if unknown.
     */
    public final int getGameHeldKeyMask() {
        try {
            Field f = gameField("bF");
            return f == null ? -1 : f.getInt(this);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * @return the MIDlet's current screen/state id, or -1 if unknown.
     */
    public final int getGameScreenState() {
        try {
            Field f = gameField("v");
            return f == null ? -1 : f.getInt(this);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Sound-engine probe (AC_DEBUG=probe): dumps the MIDlet's sound state
     * machine - current screen, requested sound id (eg), saved sound id (eh),
     * delayed-replay counter (ei), sound-enabled flag (cE) and every static
     * Player field with its live state - so sound regressions can be traced
     * without patching the game. Returns null when the probe is disabled.
     */
    public final String soundProbeState() {
        if (!javax.microedition.AcDebug.PROBE) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            appendInt(sb, "v", "v");
            appendInt(sb, "eg", "eg");
            appendInt(sb, "eh", "eh");
            appendInt(sb, "ei", "ei");
            Field cef = gameField("cE");
            sb.append(" cE=").append(cef == null ? "?"
                    : String.valueOf(cef.getBoolean(this)));
            Class<?> c = getClass();
            while (c != null && c != Canvas.class && c != Displayable.class
                    && c != Object.class) {
                for (Field pf : c.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(pf.getModifiers())
                            || !javax.microedition.media.Player.class
                                    .isAssignableFrom(pf.getType())) {
                        continue;
                    }
                    pf.setAccessible(true);
                    Object p = pf.get(null);
                    sb.append(' ').append(pf.getName()).append('=')
                            .append(p == null ? "null"
                                    : "@" + Integer.toHexString(
                                            System.identityHashCode(p))
                                            + " state="
                                            + ((javax.microedition.media.Player) p)
                                                    .getState());
                }
                c = c.getSuperclass();
            }
            return sb.toString();
        } catch (Throwable t) {
            return " probe-failed: " + t;
        }
    }

    private void appendInt(StringBuilder sb, String label, String fieldName)
            throws IllegalAccessException {
        Field f = gameField(fieldName);
        sb.append(label).append('=').append(f == null ? -1 : f.getInt(this));
    }

    private String gameState() {
        try {
            StringBuilder sb = new StringBuilder("{");
            for (String name : new String[]{"v", "w", "db", "bE", "bF", "bG",
                    "bD", "bJ", "bP", "bR", "bQ", "bS", "a", "m", "cv"}) {
                Field f = gameField(name);
                if (f == null) {
                    continue;
                }
                sb.append(name).append('=').append(f.get(this)).append(' ');
            }
            return sb.append('}').toString();
        } catch (Throwable t) {
            return "{state-unavailable}";
        }
    }

    public final void render(Graphics g) {
        paint(g);
    }
}
