package com.world.cloudxsolution;

import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class AndroidGamepadListener {

    private final Object stateLock = new Object();

    private final GamepadTransitionFrame transitionFrame;
    private final Consumer<ByteBuffer> unreliableSendRaw;
    private final AtomicLong unreliableToken = new AtomicLong(0);
    private long tickCounter = 0;
    private float stickDeadzone = 0.12f;
    private float cameraSensitivity = 1.5f;

    private static final int TYPE_MOTION = 0;
    private static final int TYPE_KEYDOWN = 1;
    private static final int TYPE_KEYUP = 2;

    // Response curve exponent for the right (camera) stick: 1.0 = linear,
    // >1.0 = finer control near center with faster ramp near the edge (common for aim sticks).
    private volatile float rightStickCurveExponent = 1.0f;
    // Reused scratch buffer to avoid per-event allocation on the input thread (called at 60-120Hz+).
    private final float[] stickScratch = new float[2];
    // Raw (pre-deadzone/curve/sensitivity) stick values, kept only so UI (e.g. the settings dialog's
    // live stick-test visualizer) can read the true unprocessed input. Guarded by stateLock.
    private float rawLeftX = 0f, rawLeftY = 0f, rawRightX = 0f, rawRightY = 0f;
    private volatile InputDevice lastDevice;
    private Runnable onMenuTrigger;

    // When true: onKeyDown/onKeyUp/onGenericMotion still run (so callers like the settings
    // dialog can still route real hardware events through and get the StickMotionListener
    // callback for visualization), but NOTHING is ever sent to the server/game -- this is the
    // single choke point (see sendState) that guarantees that, regardless of how
    // or from where these methods get called. Also suppresses side-effecting callbacks
    // (onMenuTrigger) so a Start+Back combo pressed mid-test can't trigger game-side actions.
    private volatile boolean testModeActive = false;

    public void setTestModeActive(boolean active) {
        this.testModeActive = active;
    }

    public boolean isTestModeActive() {
        return testModeActive;
    }

    // --- HIGH PERFORMANCE MAPPING (Prepared Once) ---
    private boolean isPrepared = false;
    private String preparedDeviceDescriptor = null; // stable across disconnect/reconnect, unlike getId()
    private final int[] keyCodeMap = new int[KeyEvent.getMaxKeyCode() + 1];
    private int axisRSX, axisRSY, axisLT, axisRT;
    private boolean hasHatAxes = false;
    private boolean leftTriggerIsAnalog = true;
    private boolean rightTriggerIsAnalog = true;

    // Previous state for D-pad hat switch edge detection
    private boolean prevHatUp, prevHatDown, prevHatLeft, prevHatRight;

    // --------------------------------------------------

    public AndroidGamepadListener(int gamepadIndex, float sensitivity, float deadzone,
                                  Consumer<ByteBuffer> unreliableSendRaw) {
        setCameraSensitivity(sensitivity);
        setStickDeadzone(deadzone);
        this.transitionFrame = new GamepadTransitionFrame(gamepadIndex);
        this.unreliableSendRaw = unreliableSendRaw;
        Arrays.fill(keyCodeMap, -1);
    }

    private void sendTwice(GamepadPendingState state) {
        sendState(state);
        sendState(state);
    }

    private void sendState(GamepadPendingState state) {
        if (testModeActive) return;
        try {
            ByteBuffer packet = UnreliableInputPacket.forChangedGamepads(state.token, state.tick, state.snapshot);
            unreliableSendRaw.accept(packet);
        } catch (Exception e) {
            Log.e("GamepadListener", "Failed to send packet", e);
        }
    }

    public String prepare(InputDevice device) {
        if (device == null) return "";
        if (device.isVirtual()) return ""; // remote/injected input source, not a real gamepad -- never let it reconfigure mapping

        String descriptor = device.getDescriptor();

        synchronized (stateLock) {
            lastDevice = device;
            if (isPrepared && descriptor != null && descriptor.equals(preparedDeviceDescriptor)) {
                return "";
            }
            preparedDeviceDescriptor = descriptor;
            String devName = device.getName();
            Arrays.fill(keyCodeMap, -1);

            // Populate KeyCode Mapping Table
            map(KeyEvent.KEYCODE_BUTTON_A, GamepadTransitionFrame.INDEX_A);
            map(KeyEvent.KEYCODE_BUTTON_B, GamepadTransitionFrame.INDEX_B);
            map(KeyEvent.KEYCODE_BUTTON_X, GamepadTransitionFrame.INDEX_X);
            map(KeyEvent.KEYCODE_BUTTON_Y, GamepadTransitionFrame.INDEX_Y);
            map(KeyEvent.KEYCODE_BUTTON_L1, GamepadTransitionFrame.INDEX_LEFT_SHOULDER);
            map(KeyEvent.KEYCODE_BUTTON_R1, GamepadTransitionFrame.INDEX_RIGHT_SHOULDER);
            map(KeyEvent.KEYCODE_BUTTON_THUMBL, GamepadTransitionFrame.INDEX_LEFT_THUMB);
            map(KeyEvent.KEYCODE_BUTTON_THUMBR, GamepadTransitionFrame.INDEX_RIGHT_THUMB);
            map(KeyEvent.KEYCODE_BUTTON_START, GamepadTransitionFrame.INDEX_MENU);
            map(KeyEvent.KEYCODE_BUTTON_SELECT, GamepadTransitionFrame.INDEX_VIEW);
            map(KeyEvent.KEYCODE_BACK, GamepadTransitionFrame.INDEX_VIEW);
            map(KeyEvent.KEYCODE_BUTTON_MODE, GamepadTransitionFrame.INDEX_NEXUS);

            // Detect Axis Layout
            if (device.getMotionRange(MotionEvent.AXIS_Z, InputDevice.SOURCE_JOYSTICK) == null
                    && device.getMotionRange(MotionEvent.AXIS_RX, InputDevice.SOURCE_JOYSTICK) != null) {
                axisRSX = MotionEvent.AXIS_RX;
                axisRSY = MotionEvent.AXIS_RY;
            } else {
                axisRSX = MotionEvent.AXIS_Z;
                axisRSY = MotionEvent.AXIS_RZ;
            }

            if (device.getMotionRange(MotionEvent.AXIS_LTRIGGER, InputDevice.SOURCE_JOYSTICK) != null) {
                axisLT = MotionEvent.AXIS_LTRIGGER;
                leftTriggerIsAnalog = true;
            } else if (device.getMotionRange(MotionEvent.AXIS_BRAKE, InputDevice.SOURCE_JOYSTICK) != null) {
                axisLT = MotionEvent.AXIS_BRAKE;
                leftTriggerIsAnalog = true;
            } else {
                leftTriggerIsAnalog = false;
            }

            if (device.getMotionRange(MotionEvent.AXIS_RTRIGGER, InputDevice.SOURCE_JOYSTICK) != null) {
                axisRT = MotionEvent.AXIS_RTRIGGER;
                rightTriggerIsAnalog = true;
            } else if (device.getMotionRange(MotionEvent.AXIS_GAS, InputDevice.SOURCE_JOYSTICK) != null) {
                axisRT = MotionEvent.AXIS_GAS;
                rightTriggerIsAnalog = true;
            } else {
                rightTriggerIsAnalog = false;
            }

            hasHatAxes = (device.getMotionRange(MotionEvent.AXIS_HAT_X, InputDevice.SOURCE_JOYSTICK) != null &&
                    device.getMotionRange(MotionEvent.AXIS_HAT_Y, InputDevice.SOURCE_JOYSTICK) != null);

            if (!hasHatAxes) {
                map(KeyEvent.KEYCODE_DPAD_UP, GamepadTransitionFrame.INDEX_DPAD_UP);
                map(KeyEvent.KEYCODE_DPAD_DOWN, GamepadTransitionFrame.INDEX_DPAD_DOWN);
                map(KeyEvent.KEYCODE_DPAD_LEFT, GamepadTransitionFrame.INDEX_DPAD_LEFT);
                map(KeyEvent.KEYCODE_DPAD_RIGHT, GamepadTransitionFrame.INDEX_DPAD_RIGHT);
            }

            isPrepared = true;
            return devName+" | Connected";
        }
    }

    private boolean isRemoteEvent(InputDevice device) {
        return device == null || device.isVirtual();
    }

    private void map(int keyCode, int transitionIndex) {
        if (keyCode >= 0 && keyCode < keyCodeMap.length) {
            keyCodeMap[keyCode] = transitionIndex;
        }
    }
    private static class GamepadPendingState {
        final long token;
        final long tick;
        final GamepadTransitionFrame snapshot;

        GamepadPendingState(long token, long tick, GamepadTransitionFrame snapshot) {
            this.token = token;
            this.tick = tick;
            this.snapshot = snapshot;
        }
    }
    private long ms=0;
    //private long pression=0;
    private final boolean[] keyDown = new boolean[KeyEvent.getMaxKeyCode() + 1];
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (!isPrepared || isRemoteEvent(event.getDevice())) {
            return false;
        }
        if (keyDown[keyCode]) {
            return true;
        }

        if (event.getRepeatCount() > 0) return true;

        int index = (keyCode >= 0 && keyCode < keyCodeMap.length) ? keyCodeMap[keyCode] : -1;
        if (index == -1) {

            return false;
        }

        synchronized (stateLock) {

            if (index == GamepadTransitionFrame.INDEX_MENU && transitionFrame.held[GamepadTransitionFrame.INDEX_VIEW]) {
                transitionFrame.resetAllHeldButtons();
                sendUnreliableIfChanged(TYPE_KEYDOWN);
                if (!testModeActive && onMenuTrigger != null) onMenuTrigger.run();
                return true;
            }
            transitionFrame.bumpButton(index);
            sendUnreliableIfChanged(TYPE_KEYDOWN);

            keyDown[keyCode] = true;

        }
        return true;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!isPrepared || isRemoteEvent(event.getDevice())) {
            return false;
        }

        int index = (keyCode >= 0 && keyCode < keyCodeMap.length) ? keyCodeMap[keyCode] : -1;
        if (index == -1) {

            return false;
        }
        if (!keyDown[keyCode]) {
            return true;
        }
        synchronized (stateLock) {
            transitionFrame.setReleased(index);
            sendUnreliableIfChanged(TYPE_KEYUP);

            keyDown[keyCode] = false;
        }
        return true;
    }
private long Gms=0;
    public boolean onGenericMotion(MotionEvent event) {
        if (!isPrepared || isRemoteEvent(event.getDevice())
                || (event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return false;
        }

        synchronized (stateLock) {
//            long Gnow=SystemClock.uptimeMillis();
//            if(Gnow-Gms<10){return true;}

            rawLeftX = event.getAxisValue(MotionEvent.AXIS_X);
            rawLeftY = event.getAxisValue(MotionEvent.AXIS_Y);
            rawRightX = event.getAxisValue(axisRSX);
            rawRightY = event.getAxisValue(axisRSY);

            // Radial deadzone: computed on stick magnitude, not per-axis. An axial (per-axis)
            // deadzone effectively creates a *square* dead area, which makes the dead radius up to
            // ~41% larger on diagonals than on the cardinal axes and skews the output direction near
            // the edge of the deadzone. That mismatch between input angle and output angle is exactly
            // what reads as "unordered"/desynced stick movement, especially on the camera stick.
            applyRadialDeadzone(rawLeftX, rawLeftY, stickDeadzone, 1.0f, stickScratch);
            transitionFrame.setLeftStick(stickScratch[0], stickScratch[1]);

            applyRadialDeadzone(rawRightX, rawRightY, stickDeadzone, rightStickCurveExponent, stickScratch);
            float rx = stickScratch[0] * cameraSensitivity;
            float ry = stickScratch[1] * cameraSensitivity;
            transitionFrame.setRightStick(clamp(rx), clamp(ry));

            if (leftTriggerIsAnalog) {
                transitionFrame.axes[GamepadTransitionFrame.AXIS_LEFT_TRIGGER] = Math.max(0f, event.getAxisValue(axisLT));
            }
            if (rightTriggerIsAnalog) {
                transitionFrame.axes[GamepadTransitionFrame.AXIS_RIGHT_TRIGGER] = Math.max(0f, event.getAxisValue(axisRT));
            }

            if (hasHatAxes) {
                float hx = event.getAxisValue(MotionEvent.AXIS_HAT_X);
                float hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y);

                boolean up = hy < -0.5f;
                boolean down = hy > 0.5f;
                boolean left = hx < -0.5f;
                boolean right = hx > 0.5f;

                if (up && !prevHatUp) transitionFrame.bumpButton(GamepadTransitionFrame.INDEX_DPAD_UP);
                else if (!up && prevHatUp) transitionFrame.setReleased(GamepadTransitionFrame.INDEX_DPAD_UP);

                if (down && !prevHatDown) transitionFrame.bumpButton(GamepadTransitionFrame.INDEX_DPAD_DOWN);
                else if (!down && prevHatDown) transitionFrame.setReleased(GamepadTransitionFrame.INDEX_DPAD_DOWN);

                if (left && !prevHatLeft) transitionFrame.bumpButton(GamepadTransitionFrame.INDEX_DPAD_LEFT);
                else if (!left && prevHatLeft) transitionFrame.setReleased(GamepadTransitionFrame.INDEX_DPAD_LEFT);

                if (right && !prevHatRight) transitionFrame.bumpButton(GamepadTransitionFrame.INDEX_DPAD_RIGHT);
                else if (!right && prevHatRight) transitionFrame.setReleased(GamepadTransitionFrame.INDEX_DPAD_RIGHT);

                prevHatUp = up; prevHatDown = down; prevHatLeft = left; prevHatRight = right;
            }

            sendUnreliableIfChanged(TYPE_MOTION);
        }

        return true;
    }

    private void sendUnreliableIfChanged(int methodType) {
        transitionFrame.physicalPhysicality = transitionFrame.computeButtonPhysicality();

        GamepadTransitionFrame snapshot = transitionFrame.copy();
        long token = unreliableToken.incrementAndGet();
        long tick = tickCounter++;
        GamepadPendingState state = new GamepadPendingState(token, tick, snapshot);
        
        if (methodType == TYPE_KEYDOWN || methodType == TYPE_KEYUP) {
            // Send multiple times to ensure the button event reaches the server
            // over the unreliable channel.
            sendTwice(state);
            sendState(state);
        } else {
            sendState(state);
        }
    }

    /**
     * Radial deadzone + response curve, applied to the (x,y) vector as a whole so direction is
     * preserved exactly and the dead area is a circle, not a square.
     *
     * Steps:
     *  1. Compute magnitude of the raw vector.
     *  2. Below deadzone -> output is exactly (0,0). No axis leakage/jitter at rest.
     *  3. Above deadzone -> rescale magnitude from [deadzone, 1] to [0, 1] ("scaled radial
     *     deadzone"), so output ramps smoothly from 0 right past the deadzone edge instead of
     *     jumping discontinuously.
     *  4. Optionally apply a power curve to the rescaled magnitude (exponent > 1 gives finer
     *     control near center and a faster ramp near the edge -- useful for aim/camera sticks).
     *  5. Rebuild x/y from the curved magnitude along the original direction.
     *
     * Result is written into `out` (out[0]=x, out[1]=y) to avoid allocation on the hot input path.
     */
    private static void applyRadialDeadzone(float rawX, float rawY, float deadzone, float curveExponent, float[] out) {
        float magnitude = (float) Math.sqrt(rawX * rawX + rawY * rawY);
        if (magnitude < deadzone || magnitude < 1e-6f) {
            out[0] = 0f;
            out[1] = 0f;
            return;
        }
        float normalizedMag = Math.min(1f, (magnitude - deadzone) / (1f - deadzone));
        float curvedMag = (curveExponent == 1.0f) ? normalizedMag : (float) Math.pow(normalizedMag, curveExponent);
        float scale = curvedMag / magnitude; // reapply as a fraction of the ORIGINAL vector -> preserves angle exactly
        out[0] = rawX * scale;
        out[1] = rawY * scale;
    }

    private static float clamp(float value) {
        if (value > 1f) return 1f;
        if (value < -1f) return -1f;
        return value;
    }

    public void setStickDeadzone(float deadzone) {
        this.stickDeadzone = deadzone;
    }

    public float getStickDeadzone() {
        return stickDeadzone;
    }

    public void setCameraSensitivity(float sensitivity) {
        this.cameraSensitivity = sensitivity;
    }

    public float getCameraSensitivity() {
        return cameraSensitivity;
    }

    public float getRightStickResponseCurve() {
        return rightStickCurveExponent;
    }

    /**
     * Raw axis IDs the right stick reports on the currently-prepared device (MotionEvent.AXIS_Z/
     * AXIS_RZ or AXIS_RX/AXIS_RY, depending on controller). Set once in prepare(). Exposed so
     * callers reading MotionEvents directly (e.g. the settings dialog's onGenericMotionEvent
     * override) can pull the right stick's values correctly without re-implementing device
     * detection. This is a one-time mapping read, not a live/callback subscription.
     */
    public int getRightStickAxisX() {
        return axisRSX;
    }

    public int getRightStickAxisY() {
        return axisRSY;
    }

    /**
     * Live raw (pre-deadzone) stick positions, for UI visualization only (e.g. the settings
     * dialog's stick-test widget). Not used anywhere on the input->network path.
     * out must be a float[4]: [rawLeftX, rawLeftY, rawRightX, rawRightY].
     */
    public void getRawStickState(float[] out) {
        synchronized (stateLock) {
            out[0] = rawLeftX;
            out[1] = rawLeftY;
            out[2] = rawRightX;
            out[3] = rawRightY;
        }
    }

    /**
     * Public preview of the radial-deadzone/curve/sensitivity pipeline, for UI use -- lets the
     * settings dialog show what a stick position WOULD look like with the currently-dragged
     * (not-yet-applied) slider values, using the exact same math as the live input path.
     * out must be a float[2]: [x, y].
     */
    public static void computeStickResponse(float rawX, float rawY, float deadzone, float curveExponent,
                                            float sensitivity, float[] out) {
        applyRadialDeadzone(rawX, rawY, deadzone, curveExponent, out);
        out[0] = clamp(out[0] * sensitivity);
        out[1] = clamp(out[1] * sensitivity);
    }

    /**
     * Exposes the right-stick response curve to game settings.
     * 1.0 = linear (default). Values > 1.0 (e.g. 1.5-2.0) give finer low-speed aim control near
     * center while still reaching full speed at the edge -- a common "aim curve" preference.
     * Values < 1.0 make the stick feel more twitchy near center. Clamped to a sane range.
     */
    public void setRightStickResponseCurve(float exponent) {
        this.rightStickCurveExponent = Math.max(0.25f, Math.min(4.0f, exponent));
    }

    public void setgamepadIndex(int idx) {
        this.transitionFrame.gamepadId = idx;
    }

    public void stop() {
    }

    public void detectAxisLayout(InputDevice device) {
        prepare(device);
    }

    public InputDevice getLastDevice() {
        return lastDevice;
    }

    public void pressNexusOnce() {
        new Thread(() -> {
            try {
                synchronized (stateLock) {
                    transitionFrame.bumpButton(GamepadTransitionFrame.INDEX_NEXUS);
                    sendUnreliableIfChanged(TYPE_KEYDOWN);
                }

                Thread.sleep(60); // Standard guide button press duration

                synchronized (stateLock) {
                    transitionFrame.setReleased(GamepadTransitionFrame.INDEX_NEXUS);
                    sendUnreliableIfChanged(TYPE_KEYUP);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void setOnMenuTrigger(Runnable onMenuTrigger) {
        this.onMenuTrigger = onMenuTrigger;
    }
}
