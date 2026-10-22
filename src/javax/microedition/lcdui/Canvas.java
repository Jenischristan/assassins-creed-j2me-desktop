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

    // The MIDlet's pointer handlers dereference sprites that are only loaded
    // when the touch flag is set (e.g. e.e[13].s in pointerReleased), so on
    // screens without that sprite every pointer event would throw a
    // NullPointerException on the AWT event thread. That killed the rest of
    // the launcher's event handling (held-key restore, pointer cleanup). The
    // dispatch wrappers therefore contain the failure, log it once per
    // distinct cause, and let the launcher continue as if nothing happened.
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
            String key = what + ": " + t;
            synchronized (reportedDispatchFailures) {
                if (reportedDispatchFailures.put(key, Boolean.TRUE) == null) {
                    System.err.println("[Launcher] suppressed MIDlet " + key);
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
