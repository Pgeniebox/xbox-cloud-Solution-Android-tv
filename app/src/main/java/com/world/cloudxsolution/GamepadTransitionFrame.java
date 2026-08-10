package com.world.cloudxsolution;

import java.util.Arrays;

public final class GamepadTransitionFrame {

    public int gamepadId;

    // Transition indices
    public static final int INDEX_DPAD_UP = 0;
    public static final int INDEX_DPAD_DOWN = 1;
    public static final int INDEX_DPAD_LEFT = 2;
    public static final int INDEX_DPAD_RIGHT = 3;
    public static final int INDEX_MENU = 4;
    public static final int INDEX_VIEW = 5;
    public static final int INDEX_LEFT_THUMB = 6;
    public static final int INDEX_RIGHT_THUMB = 7;
    public static final int INDEX_LEFT_SHOULDER = 8;
    public static final int INDEX_RIGHT_SHOULDER = 9;
    public static final int INDEX_NEXUS = 10;
    public static final int INDEX_SHARE = 11;
    public static final int INDEX_A = 12;
    public static final int INDEX_B = 13;
    public static final int INDEX_X = 14;
    public static final int INDEX_Y = 15;
    public static final int TRANSITION_COUNT = 16;

    // Axis indices
    public static final int AXIS_LEFT_X = 0;
    public static final int AXIS_LEFT_Y = 1;
    public static final int AXIS_RIGHT_X = 2;
    public static final int AXIS_RIGHT_Y = 3;
    public static final int AXIS_LEFT_TRIGGER = 4;
    public static final int AXIS_RIGHT_TRIGGER = 5;
    public static final int AXIS_COUNT = 6;

    public final int[] transitions = new int[TRANSITION_COUNT];
    public final boolean[] held = new boolean[TRANSITION_COUNT];
    public final float[] axes = new float[AXIS_COUNT];

    public int physicalPhysicality;
    public int virtualPhysicality;

    public GamepadTransitionFrame(int gamepadId) {
        this.gamepadId = gamepadId;
    }

    public void bumpButton(String button) {
        int index = getButtonIndex(button);
        if (index != -1) {
            bumpButton(index);
        }
    }

    public void bumpButton(int index) {
        if (index >= 0 && index < TRANSITION_COUNT) {
            transitions[index]++;
            held[index] = true;
        }
    }

    public void setReleased(String button) {
        int index = getButtonIndex(button);
        if (index != -1) {
            setReleased(index);
        }
    }

    public void setReleased(int index) {
        if (index >= 0 && index < TRANSITION_COUNT && held[index]) {
            held[index] = false;
            transitions[index]++;
        }
    }

    public void resetAllHeldButtons() {
        for (int i = 0; i < TRANSITION_COUNT; i++) {
            if (held[i]) {
                held[i] = false;
                transitions[i]++;
            }
        }
    }

    private int getButtonIndex(String button) {
        switch (button) {
            case "A": return INDEX_A;
            case "B": return INDEX_B;
            case "X": return INDEX_X;
            case "Y": return INDEX_Y;
            case "DPadUp": return INDEX_DPAD_UP;
            case "DPadDown": return INDEX_DPAD_DOWN;
            case "DPadLeft": return INDEX_DPAD_LEFT;
            case "DPadRight": return INDEX_DPAD_RIGHT;
            case "LeftShoulder": return INDEX_LEFT_SHOULDER;
            case "RightShoulder": return INDEX_RIGHT_SHOULDER;
            case "LeftThumb": return INDEX_LEFT_THUMB;
            case "RightThumb": return INDEX_RIGHT_THUMB;
            case "View": return INDEX_VIEW;
            case "Menu": return INDEX_MENU;
            case "Nexus": return INDEX_NEXUS;
            case "Share": return INDEX_SHARE;
            default: return -1;
        }
    }

    public void setLeftStick(float x, float y) {
        axes[AXIS_LEFT_X] = x;
        axes[AXIS_LEFT_Y] = -y; // Standard inversion for protocol
    }

    public void setRightStick(float x, float y) {
        axes[AXIS_RIGHT_X] = x;
        axes[AXIS_RIGHT_Y] = -y;
    }

    public void setTriggers(float left, float right) {
        axes[AXIS_LEFT_TRIGGER] = left;
        axes[AXIS_RIGHT_TRIGGER] = right;
    }

    public int computeButtonPhysicality() {
        int p = GamepadInputPhysicality.NONE;
        if (held[INDEX_A]) p |= GamepadInputPhysicality.A;
        if (held[INDEX_B]) p |= GamepadInputPhysicality.B;
        if (held[INDEX_X]) p |= GamepadInputPhysicality.X;
        if (held[INDEX_Y]) p |= GamepadInputPhysicality.Y;
        if (held[INDEX_MENU]) p |= GamepadInputPhysicality.MENU;
        if (held[INDEX_VIEW]) p |= GamepadInputPhysicality.VIEW;
        if (held[INDEX_LEFT_THUMB]) p |= GamepadInputPhysicality.LEFT_THUMB;
        if (held[INDEX_RIGHT_THUMB]) p |= GamepadInputPhysicality.RIGHT_THUMB;
        if (held[INDEX_LEFT_SHOULDER]) p |= GamepadInputPhysicality.LEFT_SHOULDER;
        if (held[INDEX_RIGHT_SHOULDER]) p |= GamepadInputPhysicality.RIGHT_SHOULDER;
        if (held[INDEX_NEXUS]) p |= GamepadInputPhysicality.NEXUS;
        if (held[INDEX_SHARE]) p |= GamepadInputPhysicality.MISC;

        if (held[INDEX_DPAD_UP]) p |= GamepadInputPhysicality.DPAD_UP;
        if (held[INDEX_DPAD_DOWN]) p |= GamepadInputPhysicality.DPAD_DOWN;
        if (held[INDEX_DPAD_LEFT]) p |= GamepadInputPhysicality.DPAD_LEFT;
        if (held[INDEX_DPAD_RIGHT]) p |= GamepadInputPhysicality.DPAD_RIGHT;

        if (axes[AXIS_LEFT_TRIGGER] > 0.05f) p |= GamepadInputPhysicality.LEFT_TRIGGER;
        if (axes[AXIS_RIGHT_TRIGGER] > 0.05f) p |= GamepadInputPhysicality.RIGHT_TRIGGER;

        if (Math.abs(axes[AXIS_LEFT_X]) > 0.1f) p |= GamepadInputPhysicality.LEFT_THUMB_X_AXIS;
        if (Math.abs(axes[AXIS_LEFT_Y]) > 0.1f) p |= GamepadInputPhysicality.LEFT_THUMB_Y_AXIS;
        if (Math.abs(axes[AXIS_RIGHT_X]) > 0.1f) p |= GamepadInputPhysicality.RIGHT_THUMB_X_AXIS;
        if (Math.abs(axes[AXIS_RIGHT_Y]) > 0.1f) p |= GamepadInputPhysicality.RIGHT_THUMB_Y_AXIS;

        return p;
    }

    public GamepadTransitionFrame copy() {
        GamepadTransitionFrame f = new GamepadTransitionFrame(gamepadId);
        System.arraycopy(this.transitions, 0, f.transitions, 0, TRANSITION_COUNT);
        System.arraycopy(this.held, 0, f.held, 0, TRANSITION_COUNT);
        System.arraycopy(this.axes, 0, f.axes, 0, AXIS_COUNT);
        f.physicalPhysicality = physicalPhysicality;
        f.virtualPhysicality = virtualPhysicality;
        return f;
    }
}
