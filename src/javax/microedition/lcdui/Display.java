package javax.microedition.lcdui;

public final class Display {
    public interface Painter {
        void requestPaint(Canvas canvas);

        void serviceRepaints(Canvas canvas);

        void notifyDestroyed();
    }

    private static Display instance;
    private static Painter painter;
    private static int screenWidth = 480;
    private static int screenHeight = 800;

    private Displayable current;
    private MIDletHolder holder;

    private static final class MIDletHolder {
        final javax.microedition.midlet.MIDlet midlet;

        MIDletHolder(javax.microedition.midlet.MIDlet m) {
            midlet = m;
        }
    }

    private Display() {
    }

    public static synchronized Display getDisplay(javax.microedition.midlet.MIDlet midlet) {
        if (instance == null) {
            instance = new Display();
        }
        instance.holder = new MIDletHolder(midlet);
        return instance;
    }

    public static void setPainter(Painter p) {
        painter = p;
    }

    public static void setScreenSize(int width, int height) {
        screenWidth = width;
        screenHeight = height;
    }

    public static int getScreenWidth() {
        return screenWidth;
    }

    public static int getScreenHeight() {
        return screenHeight;
    }

    public void setCurrent(Displayable next) {
        Displayable previous = current;
        if (previous == next) {
            if (next instanceof Canvas) {
                requestPaint((Canvas) next);
            }
            return;
        }
        if (previous != null) {
            previous.setShown(false);
            previous.hideNotifyInternal();
        }
        current = next;
        if (next != null) {
            next.setShown(true);
            next.showNotifyInternal();
            if (next instanceof Canvas) {
                requestPaint((Canvas) next);
            }
        }
    }

    public Displayable getCurrent() {
        return current;
    }

    public void callSerially(Runnable r) {
        r.run();
    }

    public boolean vibrate(int duration) {
        return false;
    }

    public boolean flashBacklight(int duration) {
        return false;
    }

    public int numAlphaLevels() {
        return 256;
    }

    public int numColors() {
        return 16777216;
    }

    public static void requestPaint(Canvas canvas) {
        Painter p = painter;
        if (p != null) {
            p.requestPaint(canvas);
        }
    }

    public static void serviceRepaints(Canvas canvas) {
        Painter p = painter;
        if (p != null) {
            p.serviceRepaints(canvas);
        }
    }

    public static void notifyDestroyed() {
        Painter p = painter;
        if (p != null) {
            p.notifyDestroyed();
        }
    }
}
