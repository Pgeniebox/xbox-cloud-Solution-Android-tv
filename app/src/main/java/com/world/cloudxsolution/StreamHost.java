package com.world.cloudxsolution;

import android.content.Context;

/**
 * Replaces the direct `MainActivity activity` field that WebRtcReceiver used
 * to hold. WebRtcReceiver now runs inside StreamingService (:stream process)
 * and can no longer touch a MainActivity instance directly (it lives in the
 * main process). StreamingService implements this interface and relays each
 * call across IStreamingCallback to whichever MainActivity is currently bound.
 *
 * Every one of these methods maps 1:1 to something WebRtcReceiver.java
 * already calls on `activity.*` today -- this is a mechanical extraction,
 * not new behavior. See MIGRATION_NOTES.md for the exact find/replace list.
 */
public interface StreamHost {

    Context getAppContext();

    // was: activity.showCustomToast(msg)
    void showToast(String msg);

    // was: activity.webView.setVisibility(...)
    void setWebViewVisibility(int visibility);

    // was: activity.webView == null check -- service can't reach the WebView
    // instance at all anymore, so ask the host whether it's currently valid.
    boolean isWebViewAvailable();

    // was: activity.setStreamingState(bool)
    void setStreamingState(boolean isStreaming);

    // was: activity.setNativeGamepadEnabled(bool)
    void setNativeGamepadEnabled(boolean enabled);

    // was: activity.isNativeGamepadEnabled()
    boolean isNativeGamepadEnabled();

    // was: activity.onDataChannelStateChanged(label, state)
    void onDataChannelStateChanged(String label, String state);

    // was: activity.onDataChannelMessageReceived(label, base64Data, binary)
    void onDataChannelMessageReceived(String label,byte[] data, String base64Data, boolean binary);

    // was: activity.runOnUiThread(activity::showStreamingMenuDialog) via
    // gamepadListener.setOnMenuTrigger(...)
    void requestShowStreamingMenu();

    // Notify the host when the first video frame arrives
    void onFirstFrameRendered(int width, int height);
}
