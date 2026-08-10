package com.world.cloudxsolution;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.nio.ByteBuffer;

/**
 * Runs in the :stream process. Declare in AndroidManifest.xml:
 *
 *   <service android:name=".StreamingService"
 *            android:process=":stream"
 *            android:foregroundServiceType="mediaProjection|connectedDevice" />
 *
 * (adjust foregroundServiceType to whatever's accurate for your target SDK --
 * you're already streaming media + reading a connected gamepad, so both
 * likely apply; check the exact allowed combination for your compileSdk.)
 *
 * Owns everything that used to be instantiated directly inside MainActivity:
 * WebRtcReceiver (PeerConnectionFactory, EglBase, DataChannels) and, via
 * WebRtcReceiver, the AndroidGamepadListener. MainActivity now only owns the
 * WebView, the visible SurfaceView, and raw input capture -- everything else
 * is forwarded here over IStreamingService.
 */
public class StreamingService extends Service implements StreamHost {

    private static final String TAG = "StreamingService";
    private static final String CHANNEL_ID = "cloudx_streaming";

    private WebRtcReceiver webRtcReceiver;
    private EglBase serviceEglBase;
    private org.webrtc.EglRenderer eglRenderer; // raw-Surface renderer -- see note below

    private final RemoteCallbackList<IStreamingCallback> callbacks = new RemoteCallbackList<>();
    private Handler callbackHandler;

    // Local mirror -- deliberately NOT cross-process reads, because these
    // are checked on the hot input/data-channel path (see StreamHost javadoc).
    private volatile boolean isStreamingMirror = false;
    private volatile boolean nativeGamepadEnabledMirror = false;

    @Override
    public void onCreate() {
        super.onCreate();
        callbackHandler = new Handler(Looper.getMainLooper());
        startForegroundWithNotification();
        webRtcReceiver = new WebRtcReceiver(this /* StreamHost */);
        setupSignalingRelay();
        setupNetworkBinding();
    }

    private void setupNetworkBinding() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;

        // 1. Immediate sync check for active connection at startup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))) {
                    cm.bindProcessToNetwork(activeNetwork);
                    Log.i(TAG, "Immediately locked background process to active network: " + activeNetwork);
                }
            }
        }

        // 2. Reactive listener for preferred network changes
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .build();

        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                cm.bindProcessToNetwork(network);
                Log.i(TAG, "Locked background process to Ethernet network: " + network);
            }

            @Override
            public void onLost(Network network) {
                if (network.equals(cm.getBoundNetworkForProcess())) {
                    cm.bindProcessToNetwork(null);
                    Log.w(TAG, "Bound background network lost, unbound process");
                }
            }
        });
    }

    private void setupSignalingRelay() {
        webRtcReceiver.setSignalingListener(new WebRtcReceiver.SignalingListener() {
            @Override
            public void onLocalIceCandidate(IceCandidate candidate) {
                broadcast(cb -> {
                    try {
                        if (candidate == null) {
                            cb.onLocalIceCandidate(null, 0, null);
                        } else {
                            cb.onLocalIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
                        }
                    } catch (Exception e) { Log.e(TAG, "callback failed", e); }
                });
            }
            @Override
            public void onIceConnectionChange(org.webrtc.PeerConnection.IceConnectionState state) {
                broadcast(cb -> safe(() -> cb.onIceConnectionChange(state.toString())));
            }
            @Override
            public void onIceGatheringChange(org.webrtc.PeerConnection.IceGatheringState state) {
                broadcast(cb -> safe(() -> cb.onIceGatheringChange(state.toString())));
            }
            @Override
            public void onTrackReceived(String kind, String id) {
                broadcast(cb -> safe(() -> cb.onTrackReceived(kind, id)));
            }
            @Override
            public void onPerformanceStatsReceived(String stats) {
                broadcast(cb -> safe(() -> cb.onPerformanceStatsReceived(stats)));
            }
        });
    }

    // ---------------- IStreamingService.Stub ----------------

    private final IStreamingService.Stub binder = new IStreamingService.Stub() {

        @Override
        public void registerCallback(IStreamingCallback callback) {
            callbacks.register(callback);
        }

        @Override
        public void unregisterCallback(IStreamingCallback callback) {
            callbacks.unregister(callback);
        }

        @Override
        public void initPeerConnectionFactory() {
            webRtcReceiver.initPeerConnectionFactory();
        }

        @Override
        public void releaseSession() {
            clearRenderSurface();
            webRtcReceiver.closeSession();
        }

        @Override
        public void setRenderSurface(Surface surface, int width, int height) {
            if (surface == null || !surface.isValid()) {
                if(width==41&&height==40){webRtcReceiver.enablePerfStats(true);}
                else if(width==51&&height==50){
                    webRtcReceiver.enablePerfStats(false);
                }
                Log.e(TAG, "setRenderSurface: Received invalid surface");
                return;
            }

            if (eglRenderer != null) {
                clearRenderSurface();
            }
            if (serviceEglBase == null) {
                serviceEglBase = EglBase.create(webRtcReceiver.getEglBaseContext());
            }
            eglRenderer = new org.webrtc.EglRenderer("stream-renderer");
            eglRenderer.init(serviceEglBase.getEglBaseContext(), EglBase.CONFIG_PLAIN,
                    new org.webrtc.GlRectDrawer());
            eglRenderer.createEglSurface(surface);

            org.webrtc.VideoSink wrapperSink = new org.webrtc.VideoSink() {
                private boolean firstFrameSeen = false;
                @Override
                public void onFrame(org.webrtc.VideoFrame frame) {
                    if (!firstFrameSeen) {
                        firstFrameSeen = true;
                        Log.i(TAG, "First frame detected in service! " + frame.getRotatedWidth() + "x" + frame.getRotatedHeight());
                        onFirstFrameRendered(frame.getRotatedWidth(), frame.getRotatedHeight());
                    }
                    if (eglRenderer != null) {
                        eglRenderer.onFrame(frame);
                    }
                }
            };

            VideoTrack remoteTrack = webRtcReceiver.getRemoteVideoTrack();
            if (remoteTrack != null) {
                remoteTrack.addSink(wrapperSink);
            }
            webRtcReceiver.setPendingRenderTarget(wrapperSink); // attach when track arrives later, if not yet present
        }

        @Override
        public void clearRenderSurface() {
            if (eglRenderer != null) {
                VideoTrack remoteTrack = webRtcReceiver.getRemoteVideoTrack();
                // Note: we can't easily remove the anonymous wrapperSink here
                // without storing it. But clearRenderSurface is usually called
                // on session end or surface destruction.
                if (remoteTrack != null && webRtcReceiver.getPendingRenderTarget() != null) {
                    remoteTrack.removeSink(webRtcReceiver.getPendingRenderTarget());
                }
                eglRenderer.release();
                eglRenderer = null;
            }
            if (serviceEglBase != null) {
                serviceEglBase.release();
                serviceEglBase = null;
            }
        }

        @Override
        public void handleRemoteOffer(String sdp) {
            SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, sdp);
            webRtcReceiver.handleRemoteOffer(offer, answer ->
                    broadcast(cb -> safe(() -> cb.onAnswerReady(answer.description))));
        }

        @Override
        public void handleRemoteAnswer(String sdp) {
            webRtcReceiver.handleRemoteAnswer(new SessionDescription(SessionDescription.Type.ANSWER, sdp));
        }

        @Override
        public void createOffer() {
            webRtcReceiver.createOffer(offer ->
                    broadcast(cb -> safe(() -> cb.onOfferReady(offer.description))));
        }

        @Override
        public void addIceCandidate(String sdpMid, int sdpMLineIndex, String candidate) {
            webRtcReceiver.addIceCandidate(new IceCandidate(sdpMid, sdpMLineIndex, candidate));
        }

        @Override
        public void createDataChannel(String label) {
            webRtcReceiver.createDataChannel(label);
        }

        @Override
        public void addTransceiver(String trackOrKind, String direction) {
            webRtcReceiver.addTransceiver(trackOrKind, direction);
        }

        @Override
        public void createPeerConnection(String iceServersJson) {
            webRtcReceiver.createPeerConnection(iceServersJson);
        }

        @Override
        public void closeSession() {
            clearRenderSurface();
            webRtcReceiver.closeSession();
        }

        @Override
        public void onDataChannelSend(String label, byte[] binary, String data, boolean isBinary) {
            if (binary != null) {
               // Log.i(TAG, "streamingService send data to: " + label + " (" + binary.length + " bytes)");
                webRtcReceiver.sendDataChannelMessage(label, binary, isBinary);
            } else if (data != null) {
                Log.i(TAG, "streamingService send data to: " + label + " (String length: " + data.length() + ")");
                webRtcReceiver.sendDataChannelMessage(label, data, isBinary);
            }
        }

        @Override
        public void dispatchKeyEvent(KeyEvent event) {
            // Deprecated: Input handled in Main process
        }

        @Override
        public void dispatchMotionEvent(MotionEvent event) {
            // Deprecated: Input handled in Main process
        }

        @Override
        public void setGamepadIndex(int idx) {
            // Deprecated: Input handled in Main process
        }

        @Override
        public void setGamepadSettings(float deadzone, float sensitivity) {
            // Deprecated: Input handled in Main process
        }

        @Override
        public void setNativeGamepadEnabled(boolean enabled) {
            nativeGamepadEnabledMirror = enabled;
        }

        @Override
        public void setTestModeActive(boolean active) {
            // Deprecated: Input handled in Main process
        }

        @Override
        public void pressNexusOnce() {
            // Deprecated: Input handled in Main process
        }

        @Override
        public void setMicrophoneEnabled(boolean enabled) {
            if (webRtcReceiver != null) {
                webRtcReceiver.setMicrophoneEnabled(enabled);
            }
        }

        @Override
        public boolean isStreaming() {
            return isStreamingMirror;
        }

        @Override
        public boolean isMicrophoneEnabled() {
            return webRtcReceiver != null && webRtcReceiver.isMicrophoneEnabled();
        }

        @Override
        public float getCameraDeadzone() {
            return 0.12f; // Stub
        }

        @Override
        public float getGamepadSensitivity() {
            return 1.5f; // Stub
        }

        @Override
        public int getRightStickAxisX() {
            return android.view.MotionEvent.AXIS_Z; // Stub
        }

        @Override
        public int getRightStickAxisY() {
            return android.view.MotionEvent.AXIS_RZ; // Stub
        }

        @Override
        public float getRightStickResponseCurve() {
            return 1.0f; // Stub
        }

        @Override
        public void setStreamingState(boolean isStreaming) {
            isStreamingMirror = isStreaming;
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // ---------------- StreamHost (replaces old activity.* calls in WebRtcReceiver) ----------------

    @Override
    public android.content.Context getAppContext() {
        return getApplicationContext();
    }

    @Override
    public void showToast(String msg) {
        broadcast(cb -> safe(() -> cb.showToast(msg)));
    }

    @Override
    public void setWebViewVisibility(int visibility) {
        broadcast(cb -> safe(() -> cb.setWebViewVisibility(visibility)));
    }

    @Override
    public boolean isWebViewAvailable() {
        // WebRtcReceiver previously null-checked activity.webView directly.
        // The service can't see the WebView anymore -- if a call site truly
        // needs a synchronous answer, that's a sign it should be restructured
        // as a callback instead. Default true; tighten if you hit a real case.
        return true;
    }

    @Override
    public void setStreamingState(boolean isStreaming) {
        isStreamingMirror = isStreaming;
        broadcast(cb -> safe(() -> cb.onStreamingStateChanged(isStreaming)));
    }

    @Override
    public void setNativeGamepadEnabled(boolean enabled) {
        nativeGamepadEnabledMirror = enabled;
        broadcast(cb -> safe(() -> cb.setNativeGamepadEnabledOnUi(enabled)));
    }

    @Override
    public boolean isNativeGamepadEnabled() {
        return nativeGamepadEnabledMirror && isStreamingMirror;
    }

    @Override
    public void onDataChannelStateChanged(String label, String state) {
        broadcast(cb -> safe(() -> cb.onDataChannelStateChanged(label, state)));
    }

    @Override
    public void onDataChannelMessageReceived(String label,byte[] data, String base64Data, boolean binary) {
        broadcast(cb -> safe(() -> cb.onDataChannelMessageReceived(label, data, base64Data, binary)));
    }

    @Override
    public void requestShowStreamingMenu() {
        broadcast(cb -> safe(() -> cb.requestShowStreamingMenu()));
    }

    @Override
    public void onFirstFrameRendered(int width, int height) {
        Log.i(TAG, "Broadcasting onFirstFrameRendered: " + width + "x" + height);
        broadcast(cb -> safe(() -> cb.onFirstFrameRendered(width, height)));
    }

    // ---------------- helpers ----------------

    private interface CallbackAction { void run(IStreamingCallback cb) throws Exception; }

    private void broadcast(CallbackAction action) {
        callbackHandler.post(() -> {
            int n = callbacks.beginBroadcast();
            //Log.v(TAG, "Broadcasting to " + n + " listeners");
            for (int i = 0; i < n; i++) {
                try {
                    action.run(callbacks.getBroadcastItem(i));
                } catch (Exception e) {
                    Log.e(TAG, "callback broadcast failed", e);
                }
            }
            callbacks.finishBroadcast();
        });
    }

    private interface ThrowingRunnable { void run() throws Exception; }
    private static void safe(ThrowingRunnable r) {
        try { r.run(); } catch (Exception ignored) {}
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Cloud Streaming", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(channel);
        }
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Streaming active")
                .setSmallIcon(android.R.drawable.presence_video_online)
                .build();
        startForeground(1, notification);
    }

    @Override
    public void onDestroy() {
        try { binder.clearRenderSurface(); } catch (android.os.RemoteException ignored) {}
        if (webRtcReceiver != null) webRtcReceiver.release();
        callbacks.kill();
        super.onDestroy();
    }
}
