package com.world.cloudxsolution;

// NOTE: this file is reconstructed around the dispatch snippet you
// pasted in chat — I don't have your original file's package-private
// fields/imports (TAG, isStreaming, webRtcReceiver, showWebview(),
// etc.), so wire this back into your actual base class/Activity and
// double check names/visibility match. The logic changes are what
// matter here; the surrounding scaffolding is a best-effort rebuild.

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class StreamingInputDispatcher /* extends YourBaseActivityOrView */ {

    // --- placeholders standing in for your real fields ---
    protected boolean isStreaming;
    protected WebRtcReceiver webRtcReceiver; // your existing type
    // --- end placeholders ---

    @FunctionalInterface
    private interface KeyDispatchHandler {
        boolean handle(KeyEvent event);
    }

    @FunctionalInterface
    private interface MotionDispatchHandler {
        boolean handle(MotionEvent event);
    }

    private KeyDispatchHandler keyHandler = this::dispatchKeyDetecting;
    private MotionDispatchHandler motionHandler = this::dispatchMotionDetecting;

    // Cached listener reference, refreshed only when we (re)enter the fast path.
    private AndroidGamepadListener cachedListener;

    // Cached last-seen device id, to skip the source-mask check
    // entirely for repeat events from the same device.
    private int lastFastPathDeviceId = Integer.MIN_VALUE;
    private boolean lastFastPathDeviceIsGamepad = false;

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (keyHandler.handle(event)) {
            return isStreaming;
        }
        return superDispatchKeyEvent(event);
    }

    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (motionHandler.handle(event)) {
            return isStreaming;
        }
        return superDispatchGenericMotionEvent(event);
    }

    // Stand-ins for super.dispatchKeyEvent/dispatchGenericMotionEvent
    // — replace calls to these with `super.xxx(event)` once this is
    // merged back into your real base class.
    protected boolean superDispatchKeyEvent(KeyEvent event) { return false; }
    protected boolean superDispatchGenericMotionEvent(MotionEvent event) { return false; }
    protected void showWebview() { }

    // --- "Detecting" path: runs the guard checks, swaps to fast path once ready ---

    private boolean dispatchKeyDetecting(KeyEvent event) {
        if (!isStreaming || webRtcReceiver == null) {
            return false;
        }
        AndroidGamepadListener listener = webRtcReceiver.getGamepadListener();
        if (listener == null) {
            return false;
        }

        cachedListener = listener;
        keyHandler = this::dispatchKeyFast;
        return dispatchKeyFast(event);
    }

    private boolean dispatchMotionDetecting(MotionEvent event) {
        if (!isStreaming || webRtcReceiver == null) {
            return false;
        }
        AndroidGamepadListener listener = webRtcReceiver.getGamepadListener();
        if (listener == null) {
            return false;
        }

        cachedListener = listener;
        motionHandler = this::dispatchMotionFast;
        return dispatchMotionFast(event);
    }

    // --- "Fast" path: zero guard checks, just forwards to the listener ---

    private boolean dispatchKeyFast(KeyEvent event) {
        InputDevice device = event.getDevice();

        // Only run/refresh axis-layout detection for devices that are
        // actually gamepad/joystick sources. Previously this ran
        // unconditionally for every key event on every device
        // (including e.g. a TV remote or keyboard), and — combined
        // with the old single-slot detection cache — a second device
        // id showing up here would tear down the whole streaming
        // session. detectAxisLayout() no longer returns any kind of
        // "kill the session" signal; that was never its job.
        if (device != null) {
            int id = device.getId();
            if (id != lastFastPathDeviceId) {
                int source = device.getSources();
                boolean isGamepadSource =
                        (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                                || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
                lastFastPathDeviceId = id;
                lastFastPathDeviceIsGamepad = isGamepadSource;
                if (isGamepadSource) {
                    cachedListener.detectAxisLayout(device);
                }
            }
            if (!lastFastPathDeviceIsGamepad) {
                // Not a gamepad-ish device (e.g. remote/keyboard) —
                // let it fall through to normal handling instead of
                // touching gamepad state or the session at all.
                return false;
            }
        }

        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            return cachedListener.onKeyDown(event.getKeyCode(), event);
        } else if (action == KeyEvent.ACTION_UP) {
            return cachedListener.onKeyUp(event.getKeyCode(), event);
        }
        return false;
    }

    private boolean dispatchMotionFast(MotionEvent event) {
        InputDevice device = event.getDevice();
        if (device != null) {
            cachedListener.detectAxisLayout(device);
        }
        return cachedListener.onGenericMotion(event);
    }

    // --- Call this whenever streaming stops, or the receiver/listener changes,
    //     so stale state can't be used and detection re-runs on next event ---

    protected void resetGamepadDispatch() {
        keyHandler = this::dispatchKeyDetecting;
        motionHandler = this::dispatchMotionDetecting;
        cachedListener = null;
        lastFastPathDeviceId = Integer.MIN_VALUE;
        lastFastPathDeviceIsGamepad = false;
    }

    // Stand-in interface for your receiver type, referenced above.
    interface WebRtcReceiver {
        AndroidGamepadListener getGamepadListener();
        void closeSession();
    }
}
