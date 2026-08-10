package com.world.cloudxsolution;

import android.os.Build;
import android.os.CombinedVibration;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.InputDevice;

import androidx.annotation.RequiresApi;

/**
 * Converts a parsed FourMotorRumble request into vibration on the
 * physical gamepad itself, via InputDevice's own vibrator -- NOT the
 * phone/tablet's vibration motor.
 *
 * Android exposes this two ways depending on API level:
 *   API 31+ : InputDevice.getVibratorManager() -> VibratorManager, which
 *             can address multiple vibrators on the same device
 *             individually (some controllers report 2+ vibrator IDs,
 *             e.g. one per rumble motor).
 *   Pre-31  : InputDevice.getVibrator() -> a single Vibrator for the
 *             whole device (one combined motor).
 *
 * Whether a given controller actually HAS a vibrator Android can drive
 * depends entirely on the controller + its driver -- many Bluetooth
 * gamepads report vibrator=null or hasVibrator()=false even if the
 * physical pad supports rumble over its native protocol, because
 * Android's HID gamepad vibration support is inconsistent across
 * OEM/kernel versions. This class checks for that and no-ops safely
 * when unsupported rather than throwing.
 */
public final class GamepadRumbleHandler implements InputMessageParser.RumbleListener {

    private static final String TAG = "GamepadRumbleHandler";

    /**
     * Supplies the InputDevice currently associated with a given gamepad
     * index, so rumble can be routed to the right physical controller in
     * coop/multi-gamepad scenarios. In the common single-gamepad case this
     * can just always return the one known device regardless of index.
     */
    public interface DeviceResolver {
        InputDevice resolve(int gamepadIndex);
    }

    private final DeviceResolver deviceResolver;

    public GamepadRumbleHandler(DeviceResolver deviceResolver) {
        this.deviceResolver = deviceResolver;
    }

    @Override
    public void onRumble(int gamepadIndex,
                          float leftMotorPercent, float rightMotorPercent,
                          float leftTriggerPercent, float rightTriggerPercent,
                          int durationMs, int delayMs, int repeat) {

        InputDevice device = deviceResolver != null ? deviceResolver.resolve(gamepadIndex) : null;
        if (device == null) {
            Log.d(TAG, "No InputDevice known for gamepadIndex=" + gamepadIndex + ", cannot rumble physical controller");
            return;
        }

        if (durationMs <= 0) return;

        Runnable fire = () -> fireOnDevice(device, leftMotorPercent, rightMotorPercent,
                leftTriggerPercent, rightTriggerPercent, durationMs, repeat);

        if (delayMs > 0) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(fire, delayMs);
        } else {
            fire.run();
        }
    }

    private void fireOnDevice(InputDevice device,
                              float leftMotorPercent, float rightMotorPercent,
                              float leftTriggerPercent, float rightTriggerPercent,
                              int durationMs, int repeat) {
        try {
            fireLegacy(device, leftMotorPercent, rightMotorPercent, durationMs, repeat);

         /*   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                fireModern(device, leftMotorPercent, rightMotorPercent, durationMs, repeat);
            } else {
                fireLegacy(device, leftMotorPercent, rightMotorPercent, durationMs, repeat);
            }*/
        } catch (Exception e) {
            Log.e(TAG, "Failed to rumble InputDevice " + device.getName(), e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void fireModern(InputDevice device, float left, float right, int durationMs, int repeat) {
        VibratorManager vibratorManager = device.getVibratorManager();
        if (vibratorManager == null) {
            Log.d(TAG, "Device has no VibratorManager: " + device.getName());
            return;
        }
        int[] vibratorIds = vibratorManager.getVibratorIds();
        if (vibratorIds.length == 0) {
            Log.d(TAG, "Device reports no vibrators: " + device.getName());
            return;
        }

        // If the device exposes 2 distinct vibrators, address them
        // separately (left = strong/low-freq, right = weak/high-freq),
        // matching the two-motor rumble model. Otherwise blend into one.
        if (vibratorIds.length >= 2) {
            CombinedVibration combined = CombinedVibration.startParallel()
                    .addVibrator(vibratorIds[0], effectFor(left, durationMs, repeat))
                    .addVibrator(vibratorIds[1], effectFor(right, durationMs, repeat))
                    .combine();
            vibratorManager.vibrate(combined);
        } else {
            float blended = Math.max(left, right * 0.6f);
            vibratorManager.vibrate(CombinedVibration.createParallel(effectFor(blended, durationMs, repeat)));
        }
    }

    private void fireLegacy(InputDevice device, float left, float right, int durationMs, int repeat) {
        Vibrator vibrator = device.getVibrator();
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.d(TAG, "Device has no usable Vibrator: " + device.getName());
            return;
        }
        float blended = Math.max(left, right * 0.6f);
        vibrator.vibrate(effectFor(blended, durationMs, repeat));
    }

    private VibrationEffect effectFor(float percent, int durationMs, int repeat) {
        float clamped = Math.max(0f, Math.min(1f, percent));
        int amplitude = Math.max(1, Math.min(255, Math.round(clamped * 255f)));

        if (repeat > 0) {
            long[] timings = new long[]{durationMs, 100};
            int[] amplitudes = new int[]{amplitude, 0};
            return VibrationEffect.createWaveform(timings, amplitudes, 0);
        }
        return VibrationEffect.createOneShot(durationMs, amplitude);
    }

    /** Stops any ongoing repeating rumble on the given gamepad's device. */
    public void cancel(int gamepadIndex) {
        InputDevice device = deviceResolver != null ? deviceResolver.resolve(gamepadIndex) : null;
        if (device == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = device.getVibratorManager();
                if (vibratorManager != null) vibratorManager.cancel();
            } else {
                Vibrator vibrator = device.getVibrator();
                if (vibrator != null) vibrator.cancel();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel rumble", e);
        }
    }
}
