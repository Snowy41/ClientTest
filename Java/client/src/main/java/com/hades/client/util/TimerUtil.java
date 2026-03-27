package com.hades.client.util;

public class TimerUtil {
    private long lastTime;

    public TimerUtil() {
        reset();
    }

    public void reset() {
        lastTime = System.currentTimeMillis();
    }

    public boolean hasElapsed(long milliseconds) {
        return System.currentTimeMillis() - lastTime >= milliseconds;
    }

    public long getElapsed() {
        return System.currentTimeMillis() - lastTime;
    }
}