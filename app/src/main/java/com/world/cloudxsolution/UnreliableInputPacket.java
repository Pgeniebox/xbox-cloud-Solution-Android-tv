package com.world.cloudxsolution;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class UnreliableInputPacket {

    private static final int REPORT_UNRELIABLE_INPUT = 512;
    private static final int HEADER_SIZE = 14; // 2 bytes ID + 4 bytes Token + 8 bytes Timestamp
    private static final int GAMEPAD_FRAME_SIZE = 37;

    private static final long START_TIME_NS = System.nanoTime();

    private UnreliableInputPacket() {}

    public static ByteBuffer forChangedGamepads(long token, long tickCounter,
                                                GamepadTransitionFrame changedGamepads) {
        int totalSize = HEADER_SIZE
                + 4                              // t
                + 1                              // changedGamepads.length
                + GAMEPAD_FRAME_SIZE
                + 1                              // changedPointers.length (0)
                + 1                              // hasKeyboard (0)
                + 1                              // hasMouse (0)
                + 1                              // hasLockKeys (0)
                + 1;                             // terminator

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.putShort((short) REPORT_UNRELIABLE_INPUT);
        buf.putInt((int) token);
        buf.putDouble(getRelativeTimestampMs());

        buf.putInt((int) tickCounter);

        buf.put((byte) 1);
        writeGamepadFrame(buf, changedGamepads);

        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.put((byte) 0);
        buf.put((byte) 0);

        buf.flip();
        return buf;
    }

    private static void writeGamepadFrame(ByteBuffer buf, GamepadTransitionFrame f) {
        int start = buf.position();

        buf.put((byte) f.gamepadId);

        // Transitions ordered according to the previous fields
        for (int i = 0; i < GamepadTransitionFrame.TRANSITION_COUNT; i++) {
            buf.put((byte) f.transitions[i]);
        }

        // Axes ordered according to the previous fields
        buf.putShort(normalizeTrigger(Math.abs(f.axes[GamepadTransitionFrame.AXIS_LEFT_TRIGGER]) < 0.001f ? 0f : f.axes[GamepadTransitionFrame.AXIS_LEFT_TRIGGER]));
        buf.putShort(normalizeTrigger(Math.abs(f.axes[GamepadTransitionFrame.AXIS_RIGHT_TRIGGER]) < 0.001f ? 0f : f.axes[GamepadTransitionFrame.AXIS_RIGHT_TRIGGER]));
        buf.putShort(normalizeAxis(Math.abs(f.axes[GamepadTransitionFrame.AXIS_LEFT_X]) < 0.001f ? 0f : f.axes[GamepadTransitionFrame.AXIS_LEFT_X]));
        buf.putShort(normalizeAxis(Math.abs(f.axes[GamepadTransitionFrame.AXIS_LEFT_Y]) < 0.001f ? 0f : f.axes[GamepadTransitionFrame.AXIS_LEFT_Y]));
        buf.putShort(normalizeAxis(Math.abs(f.axes[GamepadTransitionFrame.AXIS_RIGHT_X]) < 0.001f ? 0f : f.axes[GamepadTransitionFrame.AXIS_RIGHT_X]));
        buf.putShort(normalizeAxis(Math.abs(f.axes[GamepadTransitionFrame.AXIS_RIGHT_Y]) < 0.001f ? 0f : f.axes[GamepadTransitionFrame.AXIS_RIGHT_Y]));

        buf.putInt(f.physicalPhysicality);
        buf.putInt(f.virtualPhysicality);

        int written = buf.position() - start;
        if (written != GAMEPAD_FRAME_SIZE) {
            Log.e("UNRELIABLE_INPUT", "gamepad frame size mismatch: expected "
                    + GAMEPAD_FRAME_SIZE + ", actual " + written);
        }
    }


    private static short normalizeTrigger(float value) {
        if (value <= 0f) return (short) 0;
        float scaled = 65535f * value;
        if (scaled > 65535f) scaled = 65535f;
        return (short) (int) scaled;
    }

    private static short normalizeAxis(float value) {
        if (value == 0f) return (short) 0;
        final short max = Short.MAX_VALUE;   // 32767
        final short min = (short) -32767;
        float scaled = value * max;
        if (scaled > max) return max;
        if (scaled < min) return min;
        return (short) Math.round(scaled);
    }

    private static double getRelativeTimestampMs() {
        return (System.nanoTime() - START_TIME_NS) / 1_000_000.0;
    }
}
