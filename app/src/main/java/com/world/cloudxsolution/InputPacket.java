package com.world.cloudxsolution;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converted from a static utility to an instance so it can hold
 * preallocated, reused ByteBuffers instead of allocating a fresh one
 * per packet (previously: one 39-byte allocation per gamepad frame,
 * which under sustained stick input adds steady GC churn on whatever
 * thread calls forFrame()).
 *
 * Each InputChannel owns one InputPacket instance. forFrame()/
 * metadataPacket() are only ever called from InputChannel's single
 * sender thread, so reusing the buffers across calls is safe — but
 * that also means the returned ByteBuffer is only valid until the
 * next call to forFrame()/metadataPacket() on this instance. Callers
 * (InputChannel.senderLoop -> sendRaw.accept()) must finish using/
 * copying it before requesting the next packet, which is exactly
 * what the sequential sender loop does.
 */
public final class InputPacket {

    private static final int REPORT_GAMEPAD = 2;
    private static final int REPORT_CLIENT_METADATA = 8;

    private static final int HEADER_SIZE = 14;
    private static final int FRAME_SIZE = 23;
    private static final int FRAME_COUNT_BYTE = 1;

    private static final long START_TIME_NS = System.nanoTime();

    private final ByteBuffer metadataBuf =
            ByteBuffer.allocate(HEADER_SIZE + 1).order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer frameBuf =
            ByteBuffer.allocate(HEADER_SIZE + FRAME_COUNT_BYTE + FRAME_SIZE).order(ByteOrder.LITTLE_ENDIAN);

    public ByteBuffer metadataPacket(long sequence, int maxTouchpoints) {
        metadataBuf.clear();

        metadataBuf.putShort((short) REPORT_CLIENT_METADATA);
        metadataBuf.putInt((int) sequence);
        metadataBuf.putDouble(getRelativeTimestampMs());
        metadataBuf.put((byte) maxTouchpoints);

        metadataBuf.flip();
        return metadataBuf;
    }

    public ByteBuffer forFrame(long sequence, GamepadFrame frame, int serverWidth, int serverHeight) {
        frameBuf.clear();

        frameBuf.putShort((short) REPORT_GAMEPAD);
        frameBuf.putInt((int) sequence);
        frameBuf.putDouble(getRelativeTimestampMs());

        frameBuf.put((byte) 1);
        writeFrame(frameBuf, frame);

        frameBuf.flip();
        return frameBuf;
    }

    private static double getRelativeTimestampMs() {
        return (System.nanoTime() - START_TIME_NS) / 1_000_000.0;
    }

    private static void writeFrame(ByteBuffer buf, GamepadFrame f) {
        buf.put((byte) f.gamepadIndex);

        buf.putShort((short) f.buttons);
        buf.putShort(normalizeAxis(f.axes[GamepadFrame.AXIS_LEFT_X]));
        buf.putShort(normalizeAxis(f.axes[GamepadFrame.AXIS_LEFT_Y]));
        buf.putShort(normalizeAxis(f.axes[GamepadFrame.AXIS_RIGHT_X]));
        buf.putShort(normalizeAxis(f.axes[GamepadFrame.AXIS_RIGHT_Y]));
        buf.putShort(normalizeTrigger(f.axes[GamepadFrame.AXIS_LEFT_TRIGGER]));
        buf.putShort(normalizeTrigger(f.axes[GamepadFrame.AXIS_RIGHT_TRIGGER]));

        buf.putInt(calculatePhysicality(f));
        buf.putInt(0);
    }

    private static short normalizeTrigger(float value) {
        if (value < 0) return (short) 0;
        float scaled = 65535f * value;
        if (scaled > 65535f) scaled = 65535f;
        return (short) (int) scaled;
    }

    private static short normalizeAxis(float value) {
        final short max = Short.MAX_VALUE;
        final short min = (short) -32767;
        float scaled = value * max;
        if (scaled > max) return max;
        if (scaled < min) return min;
        return (short) scaled;
    }

    private static int calculatePhysicality(GamepadFrame f) {
        int p = GamepadInputPhysicality.NONE;
        int buttons = f.buttons;
        if ((buttons & GamepadFrame.DPAD_UP) != 0) p |= GamepadInputPhysicality.DPAD_UP;
        if ((buttons & GamepadFrame.DPAD_DOWN) != 0) p |= GamepadInputPhysicality.DPAD_DOWN;
        if ((buttons & GamepadFrame.DPAD_LEFT) != 0) p |= GamepadInputPhysicality.DPAD_LEFT;
        if ((buttons & GamepadFrame.DPAD_RIGHT) != 0) p |= GamepadInputPhysicality.DPAD_RIGHT;
        if ((buttons & GamepadFrame.MENU) != 0) p |= GamepadInputPhysicality.MENU;
        if ((buttons & GamepadFrame.VIEW) != 0) p |= GamepadInputPhysicality.VIEW;
        if ((buttons & GamepadFrame.LEFT_THUMB) != 0) p |= GamepadInputPhysicality.LEFT_THUMB;
        if ((buttons & GamepadFrame.RIGHT_THUMB) != 0) p |= GamepadInputPhysicality.RIGHT_THUMB;
        if ((buttons & GamepadFrame.LEFT_SHOULDER) != 0) p |= GamepadInputPhysicality.LEFT_SHOULDER;
        if ((buttons & GamepadFrame.RIGHT_SHOULDER) != 0) p |= GamepadInputPhysicality.RIGHT_SHOULDER;
        if ((buttons & GamepadFrame.NEXUS) != 0) p |= GamepadInputPhysicality.NEXUS;
        if ((buttons & GamepadFrame.A) != 0) p |= GamepadInputPhysicality.A;
        if ((buttons & GamepadFrame.B) != 0) p |= GamepadInputPhysicality.B;
        if ((buttons & GamepadFrame.X) != 0) p |= GamepadInputPhysicality.X;
        if ((buttons & GamepadFrame.Y) != 0) p |= GamepadInputPhysicality.Y;

        if (f.axes[GamepadFrame.AXIS_LEFT_TRIGGER] > 0) p |= GamepadInputPhysicality.LEFT_TRIGGER;
        if (f.axes[GamepadFrame.AXIS_RIGHT_TRIGGER] > 0) p |= GamepadInputPhysicality.RIGHT_TRIGGER;

        double leftDist = Math.hypot(f.axes[GamepadFrame.AXIS_LEFT_X], f.axes[GamepadFrame.AXIS_LEFT_Y]);
        if (leftDist > 0) {
            p |= GamepadInputPhysicality.LEFT_THUMB_X_AXIS;
            p |= GamepadInputPhysicality.LEFT_THUMB_Y_AXIS;
        }
        double rightDist = Math.hypot(f.axes[GamepadFrame.AXIS_RIGHT_X], f.axes[GamepadFrame.AXIS_RIGHT_Y]);
        if (rightDist > 0) {
            p |= GamepadInputPhysicality.RIGHT_THUMB_X_AXIS;
            p |= GamepadInputPhysicality.RIGHT_THUMB_Y_AXIS;
        }
        return p;
    }
}
