package com.world.cloudxsolution;

/**
 * Bit flags describing which physical controls were engaged on a frame.
 * Direct port of the JS GamepadInputPhysicality bitmask usage.
 */
public final class GamepadInputPhysicality {
    private GamepadInputPhysicality() {}

    public static final int NONE = 0;
    public static final int DPAD_UP = 1;
    public static final int DPAD_DOWN = 1 << 1;
    public static final int DPAD_LEFT = 1 << 2;
    public static final int DPAD_RIGHT = 1 << 3;
    public static final int MENU = 1 << 4;
    public static final int VIEW = 1 << 5;
    public static final int LEFT_THUMB = 1 << 6;
    public static final int RIGHT_THUMB = 1 << 7;
    public static final int LEFT_SHOULDER = 1 << 8;
    public static final int RIGHT_SHOULDER = 1 << 9;
    public static final int NEXUS = 1 << 10;
    public static final int MISC = 1 << 11; // reserved slot in TS enum (0x800) - was missing, shifting every bit below by one
    public static final int A = 1 << 12;
    public static final int B = 1 << 13;
    public static final int X = 1 << 14;
    public static final int Y = 1 << 15;
    public static final int LEFT_TRIGGER = 1 << 16;
    public static final int RIGHT_TRIGGER = 1 << 17;
    public static final int LEFT_THUMB_X_AXIS = 1 << 18;
    public static final int LEFT_THUMB_Y_AXIS = 1 << 19;
    public static final int RIGHT_THUMB_X_AXIS = 1 << 20;
    public static final int RIGHT_THUMB_Y_AXIS = 1 << 21;
}