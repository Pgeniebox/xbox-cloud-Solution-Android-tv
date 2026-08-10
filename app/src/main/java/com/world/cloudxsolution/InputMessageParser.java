package com.world.cloudxsolution;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parses incoming binary reports on the "input" DataChannel, mirroring
 * Channel/Input.ts's onMessage() exactly:
 *
 *   - ServerMetadata (bit 16): server tells the client the actual stream
 *     resolution (height, then width -- note the TS order, height first).
 *   - Vibration (bit 128): a FourMotorRumble request for a specific
 *     gamepad index, with per-motor magnitudes (0-100 -> 0.0-1.0),
 *     duration/delay in ms, and a repeat count.
 *
 * Same little-endian byte order as the outgoing InputPacket, since this
 * is the same wire format read in the other direction.
 */
public final class InputMessageParser {

    private static final int REPORT_SERVER_METADATA = 16;
    private static final int REPORT_VIBRATION = 128;
    private static final int REPORT_ACK = 1024;

    public interface ResolutionListener {
        void onServerResolution(int width, int height);
    }

    public interface RumbleListener {
        /**
         * @param gamepadIndex        which gamepad this rumble targets
         * @param leftMotorPercent    0.0-1.0, low-frequency/strong motor
         * @param rightMotorPercent   0.0-1.0, high-frequency/weak motor
         * @param leftTriggerPercent  0.0-1.0, left trigger motor (if supported)
         * @param rightTriggerPercent 0.0-1.0, right trigger motor (if supported)
         * @param durationMs          how long the rumble should last
         * @param delayMs             delay before the rumble starts
         * @param repeat              repeat count
         */
        void onRumble(int gamepadIndex,
                      float leftMotorPercent, float rightMotorPercent,
                      float leftTriggerPercent, float rightTriggerPercent,
                      int durationMs, int delayMs, int repeat);
    }

    public interface AckListener {
        void onAck(int token);
    }

    private InputMessageParser() {}

    /**
     * @param data raw bytes as received from DataChannel.Buffer (already
     *             copied out of the ByteBuffer, NOT the original
     *             DataChannel.Buffer.data itself -- that buffer's position/
     *             limit are only valid for the duration of the WebRTC
     *             callback).
     */
    public static void parse(byte[] data, ResolutionListener resolutionListener, RumbleListener rumbleListener, AckListener ackListener) {
        if (data == null || data.length < 2) return;

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        int reportType = buf.getShort(0) & 0xFFFF; // uint16
        int offset = 2;

        if ((reportType & REPORT_SERVER_METADATA) != 0) {
            if (data.length >= offset + 8) {
                // NOTE: matches Input.ts exactly -- height is read first,
                // then width, even though that looks reversed at a glance.
                int serverHeight = buf.getInt(offset);
                int serverWidth = buf.getInt(offset + 4);
                if (resolutionListener != null) {
                    resolutionListener.onServerResolution(serverWidth, serverHeight);
                }
            }
            offset += 8;
        }

        if ((reportType & REPORT_VIBRATION) != 0) {
            if (data.length < offset + 10) return;

            // byte 0 of this section: rumbleType (0 = FourMotorRumble) -- read but unused, matches TS (value discarded there too)
            int cursor = offset + 1;

            int gamepadIndex = data[cursor] & 0xFF;
            cursor += 1;

            float leftMotorPercent = (data[cursor] & 0xFF) / 100f;
            float rightMotorPercent = (data[cursor + 1] & 0xFF) / 100f;
            float leftTriggerPercent = (data[cursor + 2] & 0xFF) / 100f;
            float rightTriggerPercent = (data[cursor + 3] & 0xFF) / 100f;
            cursor += 4;

            int durationMs = buf.getShort(cursor) & 0xFFFF;
            int delayMs = buf.getShort(cursor + 2) & 0xFFFF;
            int repeat = data[cursor + 4] & 0xFF;

            if (rumbleListener != null) {
                rumbleListener.onRumble(gamepadIndex,
                        leftMotorPercent, rightMotorPercent,
                        leftTriggerPercent, rightTriggerPercent,
                        durationMs, delayMs, repeat);
            }
            offset += 10;
        }

        if ((reportType & REPORT_ACK) != 0) {
            if (data.length >= offset + 4) {
                int token = buf.getInt(offset);
                if (ackListener != null) {
                    ackListener.onAck(token);
                }
            }
            offset += 4;
        }
    }
}
