package com.world.cloudxsolution;

import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Run this FIRST against your actual controller before trusting any
 * axis mapping. Android gamepad axis assignment is not standardized
 * across manufacturers — logging what a specific device actually
 * reports is the only reliable way to map it correctly.
 *
 * Usage: call logDeviceCapabilities(event.getDevice()) once when a
 * gamepad connects, and logRawAxisValues(event) from
 * onGenericMotionEvent temporarily while testing, to see live values
 * as you move each stick and pull each trigger.
 */
public final class GamepadDiagnostics {

    private static final String TAG = "GamepadDiagnostics";

    // All axes worth checking — not all devices will support all of these.
    private static final int[] AXES_TO_CHECK = {
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
            MotionEvent.AXIS_RX, MotionEvent.AXIS_RY,
            MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
            MotionEvent.AXIS_THROTTLE, MotionEvent.AXIS_RUDDER
    };

    private GamepadDiagnostics() {}

    /** Logs which axes this specific device actually declares support for. */
    public static void logDeviceCapabilities(InputDevice device) {
        if (device == null) {
            Log.w(TAG, "device is null");
            return;
        }
        Log.i(TAG, "=== Capabilities for: " + device.getName() + " ===");
        Log.i(TAG, "sources bitmask: " + Integer.toHexString(device.getSources()));

        List<String> supported = new ArrayList<>();
        for (int axis : AXES_TO_CHECK) {
            InputDevice.MotionRange range =
                    device.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
            if (range != null) {
                supported.add(axisName(axis)
                        + " [min=" + range.getMin() + " max=" + range.getMax()
                        + " flat=" + range.getFlat() + "]");
            }
        }
        if (supported.isEmpty()) {
            Log.w(TAG, "No joystick axes reported as supported — check source flags.");
        } else {
            for (String s : supported) Log.i(TAG, "  supports " + s);
        }
    }

    /** Call from onGenericMotionEvent while testing to see live values per axis. */
    public static void logRawAxisValues(MotionEvent event) {
        StringBuilder sb = new StringBuilder("axes: ");
        for (int axis : AXES_TO_CHECK) {
            float value = event.getAxisValue(axis);
            if (value != 0f) {
                sb.append(axisName(axis)).append('=').append(value).append(' ');
            }
        }
        Log.d(TAG, sb.toString());
    }

    private static String axisName(int axis) {
        switch (axis) {
            case MotionEvent.AXIS_X: return "X";
            case MotionEvent.AXIS_Y: return "Y";
            case MotionEvent.AXIS_Z: return "Z";
            case MotionEvent.AXIS_RZ: return "RZ";
            case MotionEvent.AXIS_LTRIGGER: return "LTRIGGER";
            case MotionEvent.AXIS_RTRIGGER: return "RTRIGGER";
            case MotionEvent.AXIS_BRAKE: return "BRAKE";
            case MotionEvent.AXIS_GAS: return "GAS";
            case MotionEvent.AXIS_RX: return "RX";
            case MotionEvent.AXIS_RY: return "RY";
            case MotionEvent.AXIS_HAT_X: return "HAT_X";
            case MotionEvent.AXIS_HAT_Y: return "HAT_Y";
            case MotionEvent.AXIS_THROTTLE: return "THROTTLE";
            case MotionEvent.AXIS_RUDDER: return "RUDDER";
            default: return "AXIS_" + axis;
        }
    }
}
