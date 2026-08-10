// IStreamingService.aidl
package com.world.cloudxsolution;

import com.world.cloudxsolution.IStreamingCallback;
import android.view.Surface;
import android.view.KeyEvent;
import android.view.MotionEvent;

// Runs in the :stream process (hosted by StreamingService).
// MainActivity (main process) binds to this and calls these methods.
// KeyEvent/MotionEvent/Surface are all Parcelable so they cross the
// Binder boundary as-is -- no need to redesign the gamepad pipeline.
interface IStreamingService {

    void registerCallback(IStreamingCallback callback);
    void unregisterCallback(IStreamingCallback callback);

    // --- lifecycle ---
    void initPeerConnectionFactory();
    void releaseSession();

    // --- video output: called once when the SurfaceView in MainActivity
    // is created/resized. The service renders into this Surface directly
    // afterwards -- no per-frame IPC. ---
    void setRenderSurface(in Surface surface, int width, int height);
    void clearRenderSurface();

    // --- signaling (mirrors what WebRtcBridge currently calls directly
    // on WebRtcReceiver -- now relayed from the JS bridge in the main
    // process to here) ---
    void handleRemoteOffer(String sdp);
    void handleRemoteAnswer(String sdp);
    void createOffer();
    void addIceCandidate(String sdpMid, int sdpMLineIndex, String candidate);
    void createDataChannel(String label);
    void addTransceiver(String trackOrKind, String direction);
    void closeSession();
    oneway void onDataChannelSend(String label, in byte[] binary, String data, boolean isBinary);

    // Was: webRtcReceiver.createPeerConnection(List<PeerConnection.IceServer>).
    // IceServer isn't Parcelable, so pass the raw JSON MainActivity already
    // parses out of onPeerConnectionConfigReceived and let WebRtcReceiver do
    // the org.json parsing itself (see WebRtcReceiver.createPeerConnection(String)).
    void createPeerConnection(String iceServersJson);

    // --- gamepad input: forwarded from MainActivity's Window.Callback.
    // oneway = fire-and-forget, does not block the input-dispatch thread
    // waiting for a Binder round trip -- critical for 60-120Hz motion events. ---
    oneway void dispatchKeyEvent(in KeyEvent event);
    oneway void dispatchMotionEvent(in MotionEvent event);
    oneway void setGamepadIndex(int idx);
    oneway void setGamepadSettings(float deadzone, float sensitivity);
    oneway void setNativeGamepadEnabled(boolean enabled);
    oneway void setTestModeActive(boolean active);
    oneway void pressNexusOnce();
    oneway void setMicrophoneEnabled(boolean enabled);

    // --- read-only state the settings dialog / UI needs back ---
    // (blocking, non-oneway -- these are cheap field reads, and the settings
    // dialog needs the value synchronously to prefill its sliders. Fine to
    // block briefly, unlike the input-dispatch methods above.)
    boolean isStreaming();
    boolean isMicrophoneEnabled();
    float getCameraDeadzone();
    float getGamepadSensitivity();
    int getRightStickAxisX();
    int getRightStickAxisY();
    float getRightStickResponseCurve();

    // Relayed from WebRtcBridge.setStreamingState(JS) -- WebRtcReceiver's
    // data-channel-open callback used to flip activity.isStreaming directly;
    // now the service keeps its own mirror and MainActivity keeps its own,
    // kept in sync via this call + the onStreamingStateChanged callback.
    oneway void setStreamingState(boolean isStreaming);
}
