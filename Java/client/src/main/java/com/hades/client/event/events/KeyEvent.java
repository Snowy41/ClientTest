package com.hades.client.event.events;

import com.hades.client.event.HadesEvent;

/**
 * Fired when a key is pressed or released.
 */
public class KeyEvent extends HadesEvent {
    private final int keyCode;
    private final boolean pressed;

    public KeyEvent(int keyCode, boolean pressed) {
        this.keyCode = keyCode;
        this.pressed = pressed;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public boolean isPressed() {
        return pressed;
    }
}
