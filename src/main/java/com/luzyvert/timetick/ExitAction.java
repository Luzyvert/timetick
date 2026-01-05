package com.luzyvert.timetick;

import java.lang.Runnable;

public class ExitAction implements AutoCloseable {
    private final Runnable action;

    public ExitAction(Runnable action) {
        this.action = action;
    }

    @Override
    public void close() {
        if (action != null) {
            action.run();
        }
    }
}