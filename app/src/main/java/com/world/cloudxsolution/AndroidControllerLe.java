package com.world.cloudxsolution;



import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

public final class AndroidControllerLe {

    private final InputChannel channel;

    // Single long-lived mutable frame. Only ever touched from the
    // Android input dispatch thread (main thread), so no
    // synchronization is needed here.
    private final GamepadFrame frame;

    // Snapshot of the last frame actually sent over the wire, used to
    // suppress redundant sends (held-button key-repeat events, and
    // analog stick events reporting an unchanged value).
    private GamepadFrame lastSent = null;

    // Device IDs we've already run axis-layout detection for, so that
    // work only happens once per physical device instead of on every
    // single key/motion event.
    private final Set<Integer> detectedDeviceIds = new HashSet<>();

    private float stickDeadzone = 0.12f;

    // Camera sensitivity multiplier applied to the right stick only.
    // 1.0 = raw passthrough. >1.0 = faster camera turn for the same
    // physical stick deflection, <1.0 = slower/more precise.
    // Tune this value directly by feel.
    private float cameraSensitivity = 1.5f;
    private volatile InputDevice lastDevice;


    public void setStickDeadzone(float deadzone) {
        this.stickDeadzone = deadzone;
    }

    public void setCameraSensitivity(float sensitivity) {
        this.cameraSensitivity = sensitivity;
    }

    private int rightStickXAxis = MotionEvent.AXIS_Z;
    private int rightStickYAxis = MotionEvent.AXIS_RZ;
    private int leftTriggerAxis = MotionEvent.AXIS_LTRIGGER;
    private int rightTriggerAxis = MotionEvent.AXIS_RTRIGGER;
    private boolean leftTriggerIsAnalog = true;
    private boolean rightTriggerIsAnalog = true;

    public void setgamepadIndex(int idx){
        this.frame.gamepadIndex = idx;

    }

    public AndroidControllerLe(InputChannel channel, int gamepadIndex, float sensitivity, float deadzone) {

        setCameraSensitivity(sensitivity);
        setStickDeadzone(deadzone);
        lastSent=null;
        if(!detectedDeviceIds.isEmpty()){
            detectedDeviceIds.clear();
        }
        this.channel = channel;
        this.frame = new GamepadFrame(gamepadIndex);
    }

    /**
     * Detects and caches axis layout for a device. Safe to call on
     * every event — after the first call for a given device ID, this
     * is a single HashSet lookup and returns immediately, avoiding the
     * repeated getMotionRange() calls (which cross into native code)
     * that used to run on every single key/motion event.
     */
    public int detectAxisLayout(InputDevice device) {
        if (device == null) return 0;

        int id = device.getId();
        //Log.d("INPUT_SOURCE", "device.getId:"+id);

        if (detectedDeviceIds.contains(id)) {
            this.lastDevice = device;
            return 2;
        }else if(!detectedDeviceIds.isEmpty()){
            return 1;

        }
        this.lastDevice = device;
        detectedDeviceIds.add(id);

        if (device.getMotionRange(MotionEvent.AXIS_Z, InputDevice.SOURCE_JOYSTICK) == null
                && device.getMotionRange(MotionEvent.AXIS_RX, InputDevice.SOURCE_JOYSTICK) != null) {
            rightStickXAxis = MotionEvent.AXIS_RX;
            rightStickYAxis = MotionEvent.AXIS_RY;
        }
        if (device.getMotionRange(MotionEvent.AXIS_LTRIGGER, InputDevice.SOURCE_JOYSTICK) != null) {
            leftTriggerAxis = MotionEvent.AXIS_LTRIGGER;
            leftTriggerIsAnalog = true;
        } else if (device.getMotionRange(MotionEvent.AXIS_BRAKE, InputDevice.SOURCE_JOYSTICK) != null) {
            leftTriggerAxis = MotionEvent.AXIS_BRAKE;
            leftTriggerIsAnalog = true;
        } else {
            leftTriggerIsAnalog = false;
            //Log.d("INPUT_SOURCE", "leftTriggerIsAnalog = false");
        }

        if (device.getMotionRange(MotionEvent.AXIS_RTRIGGER, InputDevice.SOURCE_JOYSTICK) != null) {
            rightTriggerAxis = MotionEvent.AXIS_RTRIGGER;
            rightTriggerIsAnalog = true;
        } else if (device.getMotionRange(MotionEvent.AXIS_GAS, InputDevice.SOURCE_JOYSTICK) != null) {
            rightTriggerAxis = MotionEvent.AXIS_GAS;
            rightTriggerIsAnalog = true;
        } else {
            rightTriggerIsAnalog = false;
            // Log.d("INPUT_SOURCE", "rightTriggerIsAnalog = false");

        }
        return 2;
    }

    public synchronized boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isGamepadSource(event, keyCode)) return false;
        //Log.d("INPUT_SOURCE", "keyCode:"+keyCode);

        if (keyCode == KeyEvent.KEYCODE_BUTTON_L2 && !leftTriggerIsAnalog) {
            frame.axes[GamepadFrame.AXIS_LEFT_TRIGGER] = 1.0f;
            sendIfChanged();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R2 && !rightTriggerIsAnalog) {
            frame.axes[GamepadFrame.AXIS_RIGHT_TRIGGER] = 1.0f;
            sendIfChanged();
            return true;
        }

        String button = mapButton(keyCode);
        if (button == null) return false;

        frame.setButton(button, true);
        sendIfChanged();
        return true;
    }

    public synchronized boolean onKeyUp(int keyCode, KeyEvent event) {

        if (!isGamepadSource(event, keyCode)) return false;

        if (keyCode == KeyEvent.KEYCODE_BUTTON_L2 && !leftTriggerIsAnalog) {
            frame.axes[GamepadFrame.AXIS_LEFT_TRIGGER] = 0f;
            sendIfChanged();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BUTTON_R2 && !rightTriggerIsAnalog) {
            frame.axes[GamepadFrame.AXIS_RIGHT_TRIGGER] = 0f;
            sendIfChanged();
            return true;
        }

        String button = mapButton(keyCode);
        if (button == null) return false;

        frame.setButton(button, false);
        sendIfChanged();
        return true;
    }

    public synchronized boolean onGenericMotion(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {

            return false;
        }

        float leftX = deadzone(event.getAxisValue(MotionEvent.AXIS_X));
        float leftY = deadzone(event.getAxisValue(MotionEvent.AXIS_Y));
        float rightX = applySensitivity(deadzone(event.getAxisValue(rightStickXAxis)));
        float rightY = applySensitivity(deadzone(event.getAxisValue(rightStickYAxis)));
        // Log.d("INPUT_SOURCE", "setLeftStick:"+leftX+ "   "+leftY);
        // Log.d("INPUT_SOURCE", "setRightStick:"+rightX+ "   "+rightY);
        frame.setLeftStick(leftX, leftY);
        frame.setRightStick(rightX, rightY);

        float leftTrigger = leftTriggerIsAnalog
                ? Math.max(0f, event.getAxisValue(leftTriggerAxis)) : frame.axes[GamepadFrame.AXIS_LEFT_TRIGGER];
        float rightTrigger = rightTriggerIsAnalog
                ? Math.max(0f, event.getAxisValue(rightTriggerAxis)) : frame.axes[GamepadFrame.AXIS_RIGHT_TRIGGER];
        frame.setTriggers(leftTrigger, rightTrigger);

        // D-Pad HAT Axis mapping (Bluetooth gamepads)
        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);

        boolean hatLeft = hatX < -0.5f;
        boolean hatRight = hatX > 0.5f;
        boolean hatUp = hatY < -0.5f;
        boolean hatDown = hatY > 0.5f;

        frame.setDPad(hatUp, hatDown, hatLeft, hatRight);

        sendIfChanged();
        return true;
    }

    /**
     * Sends the current frame only if it differs from the last frame
     * actually sent. Suppresses:
     *  - Android key-repeat ACTION_DOWN events for a held button
     *    (server already treats the button as held until ACTION_UP,
     *    so resending the same down-state is redundant)
     *  - Analog stick/trigger events reporting an unchanged value
     *    (including the common case of continuous events at rest,
     *    which collapse to the same deadzoned 0f every time)
     *
     * Wire format is unaffected: when a frame IS sent, its fields are
     * populated exactly as before, so InputPacket produces identical
     * bytes for identical state.
     */
    private synchronized void sendIfChanged() {

        if (lastSent == null || !frame.contentEquals(lastSent)) {
            GamepadFrame snapshot = frame.copy();
            channel.onFrame(snapshot);
            lastSent = snapshot;
        }
    }

    private boolean isGamepadSource(KeyEvent event, int keyCode) {
        if (mapButton(keyCode) != null) return true; // Valid gamepad button
        int source = event.getSource();
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private float deadzone(float value) {
        float mag = Math.abs(value);
        if (mag < stickDeadzone) return 0f;
        float sign = Math.signum(value);
        float rescaled = (mag - stickDeadzone) / (1f - stickDeadzone);
        return sign * rescaled;
    }

    /**
     * Scales a normalized -1..1 stick value by CAMERA_SENSITIVITY and
     * clamps back into -1..1. Clamping matters because the wire format
     * (normalizeAxis in InputPacket) expects values in this range —
     * without clamping, sensitivity > 1.0 could push values outside it
     * and produce inconsistent/clipped behavior at the extremes.
     */
    private float applySensitivity(float value) {
        float scaled = value * cameraSensitivity;
        if (scaled > 1f) return 1f;
        if (scaled < -1f) return -1f;
        return scaled;
    }
    public InputDevice getLastDevice() {
        return lastDevice;
    }

    public void pressNexusOnce() {
        new Thread(() -> {
            try {
                synchronized (this) {
                    frame.setButton("Nexus", true);
                    sendIfChanged();
                }

                Thread.sleep(60); // Standard guide button press duration

                synchronized (this) {
                    frame.setButton("Nexus", false);
                    sendIfChanged();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private String mapButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "LeftShoulder";
            case KeyEvent.KEYCODE_BUTTON_R1: return "RightShoulder";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "LeftThumb";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "RightThumb";
            case KeyEvent.KEYCODE_BUTTON_START: return "Menu";
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BACK: return "View";
            case KeyEvent.KEYCODE_DPAD_UP: return "DPadUp";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "DPadDown";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "DPadLeft";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "DPadRight";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "Nexus";
            default: return null;
        }
    }
}