// IStreamingCallback.aidl
package com.world.cloudxsolution;

// Runs in the main process (implemented by MainActivity / a thin wrapper).
// StreamingService calls back across the Binder to relay events that used
// to be direct calls like activity.sendAnswerToJs(...), activity.showCustomToast(...).
oneway interface IStreamingCallback {

    // --- signaling replies, to forward into the WebView JS bridge ---
    void onAnswerReady(String sdp);
    void onOfferReady(String sdp);

    // --- ICE / connection state, mirrors WebRtcReceiver.SignalingListener ---
    void onLocalIceCandidate(String sdpMid, int sdpMLineIndex, String candidate);
    void onIceConnectionChange(String state);
    void onIceGatheringChange(String state);
    void onTrackReceived(String kind, String id);
    void onPerformanceStatsReceived(String stats);

    // --- misc activity-facing side effects (replace old activity.X calls
    // that used to run directly inside WebRtcReceiver) ---
    void onFirstFrameRendered(int width, int height);
    void showToast(String message);
    void onStreamingStateChanged(boolean isStreaming);
    void setWebViewVisibility(int visibility); // View.VISIBLE / INVISIBLE / GONE
    void onDataChannelStateChanged(String label, String state);
    void onDataChannelMessageReceived(String label, in byte[] data, String base64Data, boolean binary);
    void requestShowStreamingMenu();
    void setNativeGamepadEnabledOnUi(boolean enabled); // mirrors old activity.setNativeGamepadEnabled
}
