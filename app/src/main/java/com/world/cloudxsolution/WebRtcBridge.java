package com.world.cloudxsolution;

import android.util.Log;
import android.webkit.JavascriptInterface;

import java.util.HashMap;

/**
 * Still lives in the MAIN process -- addJavascriptInterface requires being
 * in the same process as the WebView it's attached to, so this class can't
 * move to :stream. What changes: instead of calling `webRtcReceiver.*` /
 * `activity.*` directly, every method now relays across AIDL to whichever
 * IStreamingService is currently bound. Keep-alive logic (HTTP polling) is
 * unrelated to WebRTC/gamepad load and stays exactly as-is.
 */
public class WebRtcBridge {
    private static final String TAG = "WebRtcBridge";
    private final MainActivity activity;
    private String keepAliveUrl;
    private String keepAliveToken;
    private boolean firstKeepAliveSuccess = false;
    private final android.os.Handler keepAliveHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable keepAliveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!activity.isStreaming()) {
                Log.i(TAG, "Keep-alive stopped: not streaming");
                return;
            }
            if (keepAliveUrl == null || keepAliveToken == null) return;

            RequestNetwork request = new RequestNetwork(activity);
            HashMap<String, Object> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + keepAliveToken);
            request.setHeaders(headers);

            request.startRequestNetwork(RequestNetworkController.POST, keepAliveUrl, "keepalive", new RequestNetwork.RequestListener() {
                @Override
                public void onResponse(String tag, String response, HashMap<String, Object> responseHeaders) {
                    Log.i(TAG, "Keep-alive success");
                    if (!firstKeepAliveSuccess) {
                        firstKeepAliveSuccess = true;
                        activity.showCustomToast("Experience boosting...");
                        activity.closeWebview();
                    }
                    keepAliveHandler.postDelayed(keepAliveRunnable, 60000);
                }
                @Override
                public void onErrorResponse(String tag, String message) {
                    Log.e(TAG, "Keep-alive error: " + message);
                    keepAliveHandler.postDelayed(keepAliveRunnable, 60000);
                }
            });
        }
    };

    public WebRtcBridge(MainActivity activity) {
        this.activity = activity;
        // NOTE: no more `WebRtcReceiver webRtcReceiver` param -- WebRtcReceiver
        // now lives inside StreamingService, in the other process. Every method
        // below goes through activity.getStreamingService() (the bound AIDL
        // proxy) instead. See MainActivity ServiceConnection changes.
    }

    private IStreamingService service() {
        IStreamingService s = activity.getStreamingService();
        if (s == null) Log.w(TAG, "StreamingService not bound yet, dropping call");
        return s;
    }

    @JavascriptInterface
    public void onOfferReceived(final String sdp) {
        activity.runOnUiThread(() -> {
            IStreamingService s = service();
            if (s == null) return;
            try { s.handleRemoteOffer(sdp); } catch (Exception e) { Log.e(TAG, "onOfferReceived", e); }
        });
    }

    @JavascriptInterface
    public void onIceCandidateReceived(String sdpMid, int sdpMLineIndex, String candidate) {
        activity.runOnUiThread(() -> {
            IStreamingService s = service();
            if (s == null) return;
            try { s.addIceCandidate(sdpMid, sdpMLineIndex, candidate); } catch (Exception e) { Log.e(TAG, "iceCandidate", e); }
        });
    }

    @JavascriptInterface
    public void onOfferRequested() {
        activity.runOnUiThread(() -> {
            IStreamingService s = service();
            if (s == null) return;
            try { s.createOffer(); } catch (Exception e) { Log.e(TAG, "createOffer", e); }
        });
    }

    @JavascriptInterface
    public void onPeerConnectionConfig(String configJson) {
        activity.onPeerConnectionConfigReceived(configJson);
    }

    @JavascriptInterface
    public void onAnswerReceived(String sdp) {
        activity.runOnUiThread(() -> {
            IStreamingService s = service();
            if (s == null) return;
            try { s.handleRemoteAnswer(sdp); } catch (Exception e) { Log.e(TAG, "onAnswerReceived", e); }
        });
    }

    @JavascriptInterface
    public void onDataChannelCreate(String label) {
        activity.runOnUiThread(() -> {
            IStreamingService s = service();
            if (s == null) return;
            try { s.createDataChannel(label); } catch (Exception e) { Log.e(TAG, "createDataChannel", e); }
        });
    }

    @JavascriptInterface
    public void onDataChannelSend(String label, byte[] binary, String data, boolean isBinary) {
        activity.onDataChannelSend(label, binary, data, isBinary);
    }

    @JavascriptInterface
    public void gamepadIndex(int idx) {
        activity.runOnUiThread(() -> activity.setGamepadIndex(idx));
        activity.showCustomToast("gamepadIndex: " + idx);
    }

    @JavascriptInterface
    public void setNativeGamepadEnabled(boolean enabled) {
        activity.runOnUiThread(() -> activity.setNativeGamepadEnabled(enabled));
    }

    @JavascriptInterface
    public void onAddTransceiver(String trackOrKind, String direction) {
        activity.runOnUiThread(() -> {
            IStreamingService s = service();
            if (s == null) return;
            try { s.addTransceiver(trackOrKind, direction); } catch (Exception e) { Log.e(TAG, "addTransceiver", e); }
        });
    }

    @JavascriptInterface
    public void onPeerConnectionClose() {
        activity.runOnUiThread(() -> {
            IStreamingService s = service();
            if (s == null) return;
            try { s.closeSession(); } catch (Exception e) { Log.e(TAG, "closeSession", e); }
        });
    }

    @JavascriptInterface
    public void setGamepadSettings(float deadzone, float sensitivity) {
        activity.showCustomToast("Gamepad: deadzone=" + deadzone + ", sensitivity=" + sensitivity);
        activity.runOnUiThread(() -> activity.updateGamepadSettings(deadzone, sensitivity));
    }

    @JavascriptInterface
    public void setStreamingState(boolean isStreaming) {
        activity.runOnUiThread(() -> {
            activity.setStreamingState(isStreaming);
            IStreamingService s = service();
            if (s == null) return;
            try { s.setStreamingState(isStreaming); } catch (Exception e) { Log.e(TAG, "setStreamingState", e); }
        });
    }

    @JavascriptInterface
    public void reloadWebview() {
        activity.runOnUiThread(() -> {
            if (activity.webView != null) activity.webView.reload();
        });
    }

    @JavascriptInterface
    public void setWebviewVisible() {
        activity.runOnUiThread(activity::setWebviewVisible);
    }

    @JavascriptInterface
    public void keepalive(String url, String token) {
        this.keepAliveUrl = url;
        this.keepAliveToken = token;
        this.firstKeepAliveSuccess = false;
        keepAliveHandler.removeCallbacks(keepAliveRunnable);
        keepAliveHandler.post(keepAliveRunnable);
    }
}
