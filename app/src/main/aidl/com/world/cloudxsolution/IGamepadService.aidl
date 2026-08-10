package com.world.cloudxsolution;

import com.world.cloudxsolution.IGamepadCallback;

interface IGamepadService {
    // Called once per session start (mirrors "new AndroidGamepadListener(...)").
    oneway void init(int gamepadIndex, float sensitivity, float deadzone);

    // Must be called whenever a controller is (re)detected — mirrors
    // AndroidGamepadListener.prepare(InputDevice). deviceId is
    // MotionEvent/KeyEvent#getDeviceId(); the gamepad process resolves the
    // real InputDevice itself via InputDevice.getDevice(deviceId).
    oneway void prepareDevice(int deviceId);

    oneway void onKeyDown(int keyCode, int deviceId, int repeatCount);
    oneway void onKeyUp(int keyCode, int deviceId);

    // axisValues/axisIds are parallel arrays of exactly the axes
    // AndroidGamepadListener reads: AXIS_X, AXIS_Y, axisRSX, axisRSY,
    // axisLT, axisRT, AXIS_HAT_X, AXIS_HAT_Y (order defined by MainActivity;
    // GamepadService just needs deviceId + source to redo the same lookups).
    oneway void onGenericMotion(int deviceId, int source, in float[] axisValues, in int[] axisIds);

    oneway void handleAck(int token);
    oneway void updateSettings(float deadzone, float sensitivity);
    oneway void setTestModeActive(boolean active);
    oneway void setCallback(IGamepadCallback callback);
    oneway void shutdown();
}
