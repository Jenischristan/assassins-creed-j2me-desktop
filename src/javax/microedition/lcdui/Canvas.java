package javax.microedition.lcdui;

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

    public final void dispatchKeyPressed(int keyCode) {
        keyPressed(keyCode);
    }

    public final void dispatchKeyReleased(int keyCode) {
        keyReleased(keyCode);
    }

    public final void dispatchKeyRepeated(int keyCode) {
        keyRepeated(keyCode);
    }

    public final void dispatchPointerPressed(int x, int y) {
        pointerPressed(x, y);
    }

    public final void dispatchPointerReleased(int x, int y) {
        pointerReleased(x, y);
    }

    public final void dispatchPointerDragged(int x, int y) {
        pointerDragged(x, y);
    }

    public final void render(Graphics g) {
        paint(g);
    }
}
