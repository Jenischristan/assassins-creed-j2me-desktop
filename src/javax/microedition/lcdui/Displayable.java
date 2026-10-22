package javax.microedition.lcdui;

import java.util.ArrayList;

public abstract class Displayable {
    private final ArrayList<Command> commands = new ArrayList<Command>();
    private CommandListener listener;
    private boolean shown;

    public int getWidth() {
        return Display.getScreenWidth();
    }

    public int getHeight() {
        return Display.getScreenHeight();
    }

    public boolean isShown() {
        return shown;
    }

    public void addCommand(Command cmd) {
        if (cmd != null && !commands.contains(cmd)) {
            commands.add(cmd);
        }
    }

    public void removeCommand(Command cmd) {
        commands.remove(cmd);
    }

    public void setCommandListener(CommandListener l) {
        listener = l;
    }

    CommandListener getCommandListener() {
        return listener;
    }

    void setShown(boolean value) {
        shown = value;
    }

    void showNotifyInternal() {
        if (this instanceof Canvas) {
            ((Canvas) this).showNotify();
        }
    }

    void hideNotifyInternal() {
        if (this instanceof Canvas) {
            ((Canvas) this).hideNotify();
        }
    }
}
