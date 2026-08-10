package com.world.cloudxsolution;

import java.util.Arrays;

/**
 * Mutable gamepad state frame.
 *
 * Design note: optimized for fast copy() and contentEquals() operations
 * by using a bitmask for buttons and a float array for axes.
 */
public final class GamepadFrame {

    // Button masks (aligned with InputPacket wire format)
    public static final int NEXUS = 2;
    public static final int MENU = 4;
    public static final int VIEW = 8;
    public static final int A = 16;
    public static final int B = 32;
    public static final int X = 64;
    public static final int Y = 128;
    public static final int DPAD_UP = 256;
    public static final int DPAD_DOWN = 512;
    public static final int DPAD_LEFT = 1024;
    public static final int DPAD_RIGHT = 2048;
    public static final int LEFT_SHOULDER = 4096;
    public static final int RIGHT_SHOULDER = 8192;
    public static final int LEFT_THUMB = 16384;
    public static final int RIGHT_THUMB = 32768;

    // Axis indices
    public static final int AXIS_LEFT_X = 0;
    public static final int AXIS_LEFT_Y = 1;
    public static final int AXIS_RIGHT_X = 2;
    public static final int AXIS_RIGHT_Y = 3;
    public static final int AXIS_LEFT_TRIGGER = 4;
    public static final int AXIS_RIGHT_TRIGGER = 5;
    public static final int AXIS_COUNT = 6;

    public int gamepadIndex;

    /** Bitmask of pressed buttons. See constants above. */
    public int buttons;

    /** Analog axis values. See AXIS_* constants for indices. */
    public final float[] axes = new float[AXIS_COUNT];

    public GamepadFrame(int gamepadIndex) {
        this.gamepadIndex = gamepadIndex;
    }

    /** Independent snapshot copy — fast via System.arraycopy. */
    public GamepadFrame copy() {
        GamepadFrame f = new GamepadFrame(gamepadIndex);
        f.copyFrom(this);
        return f;
    }

    public void copyFrom(GamepadFrame other) {
        this.gamepadIndex = other.gamepadIndex;
        this.buttons = other.buttons;
        System.arraycopy(other.axes, 0, this.axes, 0, AXIS_COUNT);
    }

    /**
     * True if every field that ends up on the wire is identical.
     * Fast via single int comparison and Arrays.equals.
     */
    public boolean contentEquals(GamepadFrame other) {
        if (other == null) return false;
        if (other == this) return true;

        return gamepadIndex == other.gamepadIndex
                && buttons == other.buttons
                && Arrays.equals(axes, other.axes);
    }

    /** Sets a named button's pressed state. */
    public void setButton(String button, boolean pressed) {
        int mask = getMaskForButton(button);
        if (mask != 0) {
            if (pressed) {
                buttons |= mask;
            } else {
                buttons &= ~mask;
            }
        }
    }

    private int getMaskForButton(String button) {
        switch (button) {
            case "A": return A;
            case "B": return B;
            case "X": return X;
            case "Y": return Y;
            case "DPadUp": return DPAD_UP;
            case "DPadDown": return DPAD_DOWN;
            case "DPadLeft": return DPAD_LEFT;
            case "DPadRight": return DPAD_RIGHT;
            case "LeftShoulder": return LEFT_SHOULDER;
            case "RightShoulder": return RIGHT_SHOULDER;
            case "LeftThumb": return LEFT_THUMB;
            case "RightThumb": return RIGHT_THUMB;
            case "View": return VIEW;
            case "Menu": return MENU;
            case "Nexus": return NEXUS;
            default: return 0;
        }
    }

    /** x, y in Android joystick convention; y is inverted here to match the Xbox-protocol wire format. */
    public void setLeftStick(float x, float y) {
        axes[AXIS_LEFT_X] = x;
        axes[AXIS_LEFT_Y] = -y;
    }

    /** x, y in Android joystick convention; y is inverted here to match the Xbox-protocol wire format. */
    public void setRightStick(float x, float y) {
        axes[AXIS_RIGHT_X] = x;
        axes[AXIS_RIGHT_Y] = -y;
    }

    public void setTriggers(float left, float right) {
        axes[AXIS_LEFT_TRIGGER] = left;
        axes[AXIS_RIGHT_TRIGGER] = right;
    }

    public void setDPad(boolean up, boolean down, boolean left, boolean right) {
        setButtonMask(DPAD_UP, up);
        setButtonMask(DPAD_DOWN, down);
        setButtonMask(DPAD_LEFT, left);
        setButtonMask(DPAD_RIGHT, right);
    }

    public void setButtonMask(int mask, boolean pressed) {
        if (pressed) {
            buttons |= mask;
        } else {
            buttons &= ~mask;
        }
    }
}