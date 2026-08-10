package com.world.cloudxsolution;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.WindowCallbackWrapper;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "XboxFramesMain";

    public WebView webView;
    private SurfaceViewRenderer surfaceView;
    private View loadingLayout;
    private TextView loadingText;
    private PerformanceDialog performanceDialog;
    private boolean isPerformanceOverlayVisible = false;
    private FrameLayout rootLayout;
    private AudioManager audioManager;
    private Dialog activeToastDialog;
    private boolean nativeGamepadEnabled = false;
    private volatile boolean isStreaming = false;

    private AndroidGamepadListener gamepadListener;
    private AndroidControllerLe controllerLe;
    private InputChannel legacyInputChannel;
    private String activeInputChannel = "unreliableinput"; // Default to modern
    private GamepadRumbleHandler rumbleHandler;

    private static final String PREFS_NAME = "CloudXPrefs";
    private static final String KEY_REGION = "target_region";
    private static final String KEY_RESOLUTION = "target_resolution";
    private static final String KEY_BITRATE = "target_bitrate";
    private static final String KEY_USER_AGENT = "target_user_agent";
    private static final String KEY_INITIAL_SCALE = "target_initial_scale";
    private static final String KEY_SHOW_HELP = "show_intro_help";
    private static final String KEY_USE_UNRELIABLE_INPUT = "use_unreliable_input";

    private static final java.util.Map<String, String> USER_AGENTS = new java.util.HashMap<String, String>() {{
        put("default", ""); // Empty uses system default
        put("edge", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 Edg/121.0.0.0");
        put("chrome", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");
        put("tizen", "Mozilla/5.0 (SMART-TV; LINUX; Tizen 7.0) AppleWebKit/537.36 (KHTML, like Gecko) 94.0.4606.54/7.0 TV Safari/537.36");
        put("android_tv", "Mozilla/5.0 (Linux; Android 11; Sony Bravia 4K Build/RP1A.200720.011) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Mobile Safari/537.36");
        put("fire_tv", "Mozilla/5.0 (Linux; Android 9; AFTS Build/NS6281; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/70.0.3538.110 Mobile Safari/537.36");
        put("xbox", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; Xbox; Xbox Series X) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 Edge/44.18363.8131");
        put("ps5", "Mozilla/5.0 (PlayStation; PlayStation 5/6.50) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15");
        put("android_mobile", "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36");
        put("ios", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1");
        put("mac", "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_2_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2.1 Safari/605.1.15");
    }};

    private boolean isSelectPressed = false;

    // Inside MainActivity.java:
    public boolean isNativeGamepadEnabled() {
        return nativeGamepadEnabled && isStreaming;
    }

    public boolean isStreaming() {
        return isStreaming;
    }
    private IStreamingService streamingService;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            streamingService = IStreamingService.Stub.asInterface(binder);
            try {
                streamingService.registerCallback(streamingCallback);
                streamingService.initPeerConnectionFactory();
                streamingService.createPeerConnection(null); // Initial empty config (STUN only)
            } catch (RemoteException ignored) {}
            pushSurfaceIfReady();
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            streamingService = null;
        }
    };

    public IStreamingService getStreamingService() { return streamingService; }
    private final IStreamingCallback.Stub streamingCallback = new IStreamingCallback.Stub() {
        @Override public void onAnswerReady(String sdp) { runOnUiThread(() -> sendAnswerToJs(sdp)); }
        @Override public void onOfferReady(String sdp) { runOnUiThread(() -> sendOfferToJs(sdp)); }

        @Override public void onLocalIceCandidate(String mid, int idx, String cand) {
            runOnUiThread(() -> {
                IceCandidate candidate = (mid == null && cand == null) ? null : new IceCandidate(mid, idx, cand);
                sendIceCandidateToJs(candidate);
            });
        }

        @Override public void onIceConnectionChange(String state) {
            runOnUiThread(() -> onIceConnectionStateChanged(state));
        }

        @Override public void onIceGatheringChange(String state) {
            runOnUiThread(() -> {
                if (webView == null) return;
                String stateStr = state.toLowerCase();
                String script = "if (window.handleIceGatheringState) { window.handleIceGatheringState('" + stateStr + "'); }";
                webView.evaluateJavascript(script, null);
            });
        }

        @Override public void onTrackReceived(String kind, String id) {
            runOnUiThread(() -> {
                if (webView == null) return;
                String script = "if (window.simulateOnTrack) { window.simulateOnTrack('" + kind + "', '" + id + "'); }";
                webView.evaluateJavascript(script, null);
            });

        }

        @Override public void onPerformanceStatsReceived(String stats) {
            runOnUiThread(() -> {
                if (performanceDialog != null && performanceDialog.isShowing()) {
                    performanceDialog.updateStats(stats);
                }
            });
        }

        @Override public void onFirstFrameRendered(int w, int h) {
            runOnUiThread(() -> {
                Log.i(TAG, "onFirstFrameRendered callback received: " + w + "x" + h);
                setStreamingState(true);
                if (legacyInputChannel != null) {
                    legacyInputChannel.onResolutionChange(w, h);
                }
                showCustomToast("Session Active: " + w + "x" + h);
                if (webView != null) {
                    webView.setVisibility(View.INVISIBLE);
                }

                    if(surfaceView!=null) {
                        ViewGroup parent = (ViewGroup) surfaceView.getParent();
                        //originalIndex = parent.indexOfChild(surfaceView);
                        surfaceView.setZOrderOnTop(true);
                        surfaceView.bringToFront();
                        parent.requestLayout();
                        parent.invalidate();
                    }
                    if (webView!=null&&Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        webView.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_JOYSTICK);
                    }

            });
        }
        @Override public void showToast(String message) { runOnUiThread(() -> showCustomToast(message)); }
        @Override public void onStreamingStateChanged(boolean streaming) {
            runOnUiThread(() -> setStreamingState(streaming));
        }
        @Override public void setWebViewVisibility(int visibility) {
            runOnUiThread(() -> { if (webView != null) webView.setVisibility(visibility); });
        }
        @Override public void onDataChannelStateChanged(String label, String state) {
            runOnUiThread(() -> MainActivity.this.onDataChannelStateChanged(label, state));
        }
        @Override public void onDataChannelMessageReceived(String label, byte[] data, String str, boolean binary) {
            runOnUiThread(() -> MainActivity.this.onDataChannelMessageReceived(label, data,str, binary));
        }
        @Override public void requestShowStreamingMenu() { runOnUiThread(MainActivity.this::showStreamingMenuDialog); }
        @Override public void setNativeGamepadEnabledOnUi(boolean enabled) {
            runOnUiThread(() -> nativeGamepadEnabled = enabled);
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "RestrictedApi"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        android.content.SharedPreferences gpPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        float dz = gpPrefs.getFloat("camera_deadzone", 0.12f);
        float sens = gpPrefs.getFloat("gamepad_sensitivity", 1.5f);
        boolean useUnreliable = gpPrefs.getBoolean(KEY_USE_UNRELIABLE_INPUT, true);
        activeInputChannel = useUnreliable ? "unreliableinput" : "input";
        
        rumbleHandler = new GamepadRumbleHandler(idx -> 
                gamepadListener != null ? gamepadListener.getLastDevice() : null);
        
        gamepadListener = new AndroidGamepadListener(0, sens, dz, buffer -> {
            if (isStreaming && streamingService != null && "unreliableinput".equals(activeInputChannel)) {
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                try {
                    streamingService.onDataChannelSend("unreliableinput", data, null, true);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to send gamepad frame", e);
                }
            }
        });
        gamepadListener.setOnMenuTrigger(this::showStreamingMenuDialog);

        legacyInputChannel = new InputChannel(buffer -> {
            if (isStreaming && streamingService != null && "input".equals(activeInputChannel)) {
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                try {
                    streamingService.onDataChannelSend("input", data, null, true);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to send legacy gamepad frame", e);
                }
            }
        }, 1920, 1080);
        controllerLe = new AndroidControllerLe(legacyInputChannel, 0, sens, dz);

        bindService(new Intent(this, StreamingService.class), serviceConnection, BIND_AUTO_CREATE);
        // Keep the splash screen on-screen for at least 1500ms
        // or until WebView starts loading.
        final long startTime = System.currentTimeMillis();
        splashScreen.setKeepOnScreenCondition(() -> {
            boolean isReady = (webView != null);
            boolean minTimeElapsed = (System.currentTimeMillis() - startTime) > 0;
            return !isReady || !minTimeElapsed;
        });



        getWindow().setCallback(new WindowCallbackWrapper(getWindow().getCallback()) {

            @Override
            public boolean dispatchGenericMotionEvent(MotionEvent event) {
                if (isStreaming) {
                    if ("unreliableinput".equals(activeInputChannel) && gamepadListener != null) {
                        showCustomToast(gamepadListener.prepare(event.getDevice()));
                        if (gamepadListener.onGenericMotion(event)) return true;
                    } else if ("input".equals(activeInputChannel) && controllerLe != null) {
                        controllerLe.detectAxisLayout(event.getDevice());
                        if (controllerLe.onGenericMotion(event)) return true;
                    }
                }
                return super.dispatchGenericMotionEvent(event);
            }

            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_SELECT) {
                    isSelectPressed = event.getAction() == KeyEvent.ACTION_DOWN;
                }
                if (isSelectPressed && event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_START
                        && event.getAction() == KeyEvent.ACTION_DOWN) {
                    showStreamingMenuDialog();
                    return true;
                }
                if (isStreaming) {
                    if ("unreliableinput".equals(activeInputChannel) && gamepadListener != null) {
                        showCustomToast(gamepadListener.prepare(event.getDevice()));
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            if (gamepadListener.onKeyDown(event.getKeyCode(), event)) return true;
                        } else if (event.getAction() == KeyEvent.ACTION_UP) {
                            if (gamepadListener.onKeyUp(event.getKeyCode(), event)) return true;
                        }
                    } else if ("input".equals(activeInputChannel) && controllerLe != null) {
                        controllerLe.detectAxisLayout(event.getDevice());
                        if (event.getAction() == KeyEvent.ACTION_DOWN) {
                            if (controllerLe.onKeyDown(event.getKeyCode(), event)) return true;
                        } else if (event.getAction() == KeyEvent.ACTION_UP) {
                            if (controllerLe.onKeyUp(event.getKeyCode(), event)) return true;
                        }
                    }
                }
                return super.dispatchKeyEvent(event);
            }

        });

        rootLayout = findViewById(R.id.rootLayout);
        surfaceView = findViewById(R.id.surfaceView);
        loadingLayout = findViewById(R.id.loadingLayout);
        loadingText = findViewById(R.id.loadingText);


        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder holder) { pushSurfaceIfReady(); }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) { pushSurfaceIfReady(); }
            @Override public void surfaceDestroyed(SurfaceHolder holder) {
                if (streamingService != null) {
                    try { streamingService.clearRenderSurface(); } catch (RemoteException ignored) {}
                }
            }
        });


        showWebview();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!isStreaming && webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else if (isStreaming) {
                    showStreamingMenuDialog();
                } else {
                    assert webView != null;
                    if (webView.getVisibility() == WebView.VISIBLE && !webView.canGoBack()) {
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                    }
                }
            }
        });
// Request unbuffered dispatch specifically for joystick input sources
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().getDecorView().requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_JOYSTICK);
        }
        // Debug: Launch Bluetooth Test Activity on start

        showIntroHelpDialog();
    }
    private void pushSurfaceIfReady() {
        if (streamingService == null) return;
        surfaceView.disableFpsReduction();
        surfaceView.setEnableHardwareScaler(true);
        surfaceView.setKeepScreenOn(true);
        surfaceView.setEnableHardwareScaler(true);
        surfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        Surface s = surfaceView.getHolder().getSurface();
        if (s == null || !s.isValid()) return;
        try { streamingService.setRenderSurface(s, surfaceView.getWidth(), surfaceView.getHeight()); }
        catch (RemoteException e) { Log.e(TAG, "setRenderSurface", e); }
    }
    private void showIntroHelpDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KEY_SHOW_HELP, true)) return;

        Dialog dialog = new Dialog(this, R.style.XboxDialogTheme);
        dialog.setContentView(R.layout.dialog_intro_help);
        dialog.setCancelable(true);

        Button btnOk = dialog.findViewById(R.id.btn_ok);
        Button btnDontShow = dialog.findViewById(R.id.btn_dont_show_again);

        btnOk.setOnClickListener(v -> dialog.dismiss());
        btnDontShow.setOnClickListener(v -> {
            prefs.edit().putBoolean(KEY_SHOW_HELP, false).apply();
            dialog.dismiss();
        });

        dialog.show();
        btnOk.requestFocus();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        webView = new WebView(this);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        // Apply saved Initial Scale
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int initialScale = prefs.getInt(KEY_INITIAL_SCALE, 0);
        if (initialScale > 0) {
            webView.setInitialScale(initialScale);
        }

        // Fix scaling issues for Desktop/TV User Agents
        webSettings.setUseWideViewPort(false);
        webSettings.setLoadWithOverviewMode(false);
        webSettings.setSupportZoom(false);
        webSettings.setBuiltInZoomControls(false);
        webSettings.setDisplayZoomControls(false);

        // Apply saved User-Agent
        String uaKey = prefs.getString(KEY_USER_AGENT, "default");
        String customUa = USER_AGENTS.get(uaKey);
        if (customUa != null && !customUa.isEmpty()) {
            webSettings.setUserAgentString(customUa);
        }

        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webSettings.setDatabaseEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                setWebviewVisible();

                // Inject viewport meta tag to force fit
                String viewportJs = "var meta = document.createElement('meta'); " +
                        "meta.name = 'viewport'; " +
                        "meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'; " +
                        "document.getElementsByTagName('head')[0].appendChild(meta);";
                view.evaluateJavascript(viewportJs, null);

                android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String region = prefs.getString(KEY_REGION, "us");
                String resolution = prefs.getString(KEY_RESOLUTION, "Auto");
                int bitrate = prefs.getInt(KEY_BITRATE, 0); // 0 means Auto
                boolean useUnreliable = prefs.getBoolean(KEY_USE_UNRELIABLE_INPUT, true);

                @SuppressLint("DefaultLocale") String initJs = String.format("window.BX_TARGET_REGION = '%s'; window.BX_TARGET_RES = '%s'; window.BX_TARGET_BITRATE = %d; window.BX_USE_UNRELIABLE_INPUT = %b;",
                        region, resolution, bitrate, useUnreliable);
                view.evaluateJavascript(initJs, null);

            if (gamepadListener != null) {
                // Multiply by 100 to send as percentages (e.g., 0.12 -> 12, 1.5 -> 150)
                float dz = gamepadListener.getStickDeadzone() * 100;
                float sens = gamepadListener.getCameraSensitivity() * 100;

                // Use String.format for cleaner JS string generation
                String js = String.format(Locale.US,
                        "if (window.setControllerSettings) { window.setControllerSettings(%.0f, %.0f); }",
                        dz, sens);
                view.evaluateJavascript(js, null);
            }
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (loadingLayout != null) {
                    loadingLayout.setVisibility(View.VISIBLE);
                }
                view.setVisibility(WebView.GONE);
                super.onPageStarted(view, url, favicon);
                Log.i(TAG, "Page started: " + url);
                if (webView != null && webView.getWebChromeClient() instanceof CustomWebChromeClient) {
                    ((CustomWebChromeClient) webView.getWebChromeClient()).resetInjection();
                }
            }

        });
        WebRtcBridge bridge = new WebRtcBridge(this);
        webView.addJavascriptInterface(bridge, "AndroidBridge");
        webView.setWebChromeClient(new CustomWebChromeClient());
    }

    public void setWebviewVisible(){
       if(webView!=null){
           webView.setVisibility(WebView.VISIBLE);}
       if (loadingLayout != null) {
           loadingLayout.setVisibility(View.GONE);
       }
        if (performanceDialog != null) {
            performanceDialog.dismiss();
        }
    }
private boolean debug=false;
    public void showCustomToast(final String message) {
        runOnUiThread(() -> {
            if(message.isEmpty()){return;}
            if(!debug&&message.startsWith("[debug]")){return;}
            if (activeToastDialog != null && activeToastDialog.isShowing()) {
                activeToastDialog.dismiss();
            }

            final Dialog dialog = new Dialog(this, R.style.XboxToastTheme);
            activeToastDialog = dialog;
            
            View toastView = getLayoutInflater().inflate(R.layout.layout_custom_toast, null);
            TextView textView = toastView.findViewById(R.id.toast_text);
            textView.setText(message);

            dialog.setContentView(toastView);
            
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL ;
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            dialog.getWindow().setAttributes(lp);

            dialog.show();

            Animation enterAnim = AnimationUtils.loadAnimation(this, R.anim.toast_enter);
            toastView.startAnimation(enterAnim);

            toastView.postDelayed(() -> {
                if (!dialog.isShowing()) return;

                Animation exitAnim = AnimationUtils.loadAnimation(this, R.anim.toast_exit);
                exitAnim.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation animation) {}
                    @Override public void onAnimationRepeat(Animation animation) {}
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        if (dialog.isShowing()) dialog.dismiss();
                    }
                });
                toastView.startAnimation(exitAnim);
            }, 8000);
        });
    }

    private Bundle webViewState;

    public void showWebview() {
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        runOnUiThread(() -> {
            if (webView == null) {
                initWebView();
                rootLayout.addView(webView, 1);
                if (webViewState != null) {
                    webView.restoreState(webViewState);
                    if(webView.canGoBack()){webView.goBack();}
                } else {
                    webView.loadUrl("https://www.xbox.com/fr-FR/play");
                }
            }
            webView.setVisibility(WebView.VISIBLE);
            webView.resumeTimers();
            webView.onResume();
            if (performanceDialog != null) {
                performanceDialog.dismiss();
            }
        });
    }

    public void closeWebview() {
        runOnUiThread(() -> {
            if (webView != null) {
                webViewState = new Bundle();
                webView.saveState(webViewState);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    webView.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_JOYSTICK);
                }
                //rootLayout.requestUnbufferedDispatch(InputDevice.SOURCE_JOYSTICK);
                rootLayout.removeView(webView);
                webView.stopLoading();
                webView.clearCache(true);
                webView.loadUrl("about:blank");
                webView.onPause();
                webView.pauseTimers();
                webView.destroy();
                webView = null;
                // Request unbuffered dispatch specifically for joystick input sources
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getWindow().getDecorView().requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_JOYSTICK);
                }

                // Toast.makeText(MainActivity.this,"Webview closed",Toast.LENGTH_LONG).show();
            }
        });


    }

    // before (the whole try block built List<PeerConnection.IceServer> and
// called webRtcReceiver.setSignalingListener(null)/createPeerConnection(finalServers)/setupSignalingListener()):

    public void onPeerConnectionConfigReceived(String configJson) {
        if (configJson == null || configJson.equals("null") || configJson.isEmpty()) {
            return;
        }
        runOnUiThread(() -> {
            if (streamingService == null) return;
            try {
                streamingService.createPeerConnection(configJson);
            } catch (RemoteException e) {
                Log.e(TAG, "createPeerConnection", e);
            }
        });
    }

    public void sendAnswerToJs(String sdp) {
        if (webView == null) return;
        String script = "if (typeof handleAnswer === 'function') { handleAnswer(" + JSONObject.quote(sdp) + "); }";
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    public void sendOfferToJs(String sdp) {
        if (webView == null) return;
        String script = "if (typeof handleOffer === 'function') { handleOffer(" + JSONObject.quote(sdp) + "); }";
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    public void sendIceCandidateToJs(IceCandidate candidate) {
        if (webView == null) return;
        String script;
        if (candidate == null) {
            script = "if (typeof handleIceCandidate === 'function') { handleIceCandidate(null, 0, null); }";
        } else {
            script = "if (typeof handleIceCandidate === 'function') { handleIceCandidate('" + 
                    candidate.sdpMid + "', " + candidate.sdpMLineIndex + ", " + JSONObject.quote(candidate.sdp) + "); }";
        }
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    public void onDataChannelMessageReceived(String label, byte[] bin, String message, boolean isBinary) {

        if (("reliableinput".equalsIgnoreCase(label) || "input".equalsIgnoreCase(label)) && bin != null) {
            InputMessageParser.parse(bin,
                    (w, h) -> {
                        Log.i(TAG, "Server resolution update: " + w + "x" + h);
                        if (legacyInputChannel != null) {
                            legacyInputChannel.onResolutionChange(w, h);
                        }
                    },
                    (idx, left, right, leftTrig, rightTrig, duration, delay, repeat) -> {
                        if (rumbleHandler != null) {
                            rumbleHandler.onRumble(idx, left, right, leftTrig, rightTrig, duration, delay, repeat);
                        }
                    },
                    token -> {
                        // Modern gamepadListener doesn't need ACKs usually, but if it did:
                        // if (gamepadListener != null) gamepadListener.handleAck(token);
                    });
            return;
        }
        if (webView == null) return;

        // 2. Filter: Only forward 'control', 'message', and 'reliableinput' to JS
        boolean shouldSendToJs = "control".equalsIgnoreCase(label) ||
                "message".equalsIgnoreCase(label) ||"chat".equalsIgnoreCase(label);

        if (shouldSendToJs) {
            // Ensure message is populated for binary data so JS gets something via Base64
            if (isBinary && message == null && bin != null) {
                message = Base64.encodeToString(bin, Base64.NO_WRAP);
            }

            if (message != null) {
                // JS signature: (label, binaryData, textData, isBinary)
                String binaryArg = isBinary ? JSONObject.quote(message) : "null";
                String textArg = isBinary ? "null" : JSONObject.quote(message);

                String script = "if (window.handleDataChannelMessage) { window.handleDataChannelMessage('" +
                        label + "', " + binaryArg + ", " + textArg + ", " + isBinary + "); }";

                runOnUiThread(() -> {
                    if (webView != null) webView.evaluateJavascript(script, null);
                });
            }
        }
    }

    public void onDataChannelStateChanged(String label, String state) {
        if ("unreliableinput".equalsIgnoreCase(label)) {
            if ("OPEN".equalsIgnoreCase(state)) {
                activeInputChannel = "unreliableinput";
            } else if ("CLOSING".equalsIgnoreCase(state) || "CLOSED".equalsIgnoreCase(state)) {
                if ("unreliableinput".equals(activeInputChannel)) {
                    activeInputChannel = "input";
                    //showCustomToast("[debug]: Modern input channel lost, switching to legacy");
                    Log.d(TAG,"[debug]: Modern input channel lost, switching to legacy");
                }
            }
        } else if ("input".equalsIgnoreCase(label)) {
            if ("OPEN".equalsIgnoreCase(state)) {
                // Only switch active channel to legacy 'input' if unreliable is NOT preferred
                // or if unreliable hasn't opened yet. This prevents legacy 'input' from 
                // overriding 'unreliableinput' if both open.
                if (!"unreliableinput".equals(activeInputChannel)) {
                    activeInputChannel = "input";
                    Log.d(TAG, "Legacy input channel active");
                }
                
                if (legacyInputChannel != null) {
                    legacyInputChannel.start(0);
                }
            } else if ("CLOSING".equalsIgnoreCase(state) || "CLOSED".equalsIgnoreCase(state)) {
                if ("input".equals(activeInputChannel)) {
                    activeInputChannel = "unreliableinput";
                    Log.d(TAG, "Legacy input channel lost, switching to unreliable");
                }
                if (legacyInputChannel != null) {
                    legacyInputChannel.shutdown();
                }
            }
        }

        if (webView == null) return;
        String script = "if (window.handleDataChannelState) { window.handleDataChannelState('" + label + "', '" + state + "'); }";
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    public void onIceConnectionStateChanged(String state) {
        if (webView == null) return;
        String script = "if (window.handleIceConnectionState) { window.handleIceConnectionState('" + state + "'); }";
        runOnUiThread(() -> {
            if (webView != null) webView.evaluateJavascript(script, null);
        });
    }

    public void onDataChannelSend(String label, byte[] binary, String data, boolean isBinary) {
        if (streamingService == null) return;
        try {
            Log.e(TAG, "onDataChannelSend:"+label);

            streamingService.onDataChannelSend(label, binary, data, isBinary);
        } catch (RemoteException e) {
            Log.e(TAG, "onDataChannelSend", e);
        }
    }

    public void setNativeGamepadEnabled(boolean enabled) {
        Log.i(TAG, "Native Gamepad " + (enabled ? "ENABLED" : "DISABLED"));
        this.nativeGamepadEnabled = enabled;
    }

    public void setGamepadIndex(int idx) {
        if (gamepadListener != null) {
            gamepadListener.setgamepadIndex(idx);
        }
        if (controllerLe != null) {
            controllerLe.setgamepadIndex(idx);
        }
    }

    public void updateGamepadSettings(float deadzone, float sensitivity) {
        if (gamepadListener != null) {
            gamepadListener.setStickDeadzone(deadzone);
            gamepadListener.setCameraSensitivity(sensitivity);
        }
        if (controllerLe != null) {
            controllerLe.setStickDeadzone(deadzone);
            controllerLe.setCameraSensitivity(sensitivity);
        }
        android.content.SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat("camera_deadzone", deadzone);
        editor.putFloat("gamepad_sensitivity", sensitivity);
        editor.apply();
    }

    private void resetGamepadDispatch() {
        Log.i(TAG, "Resetting gamepad dispatch state");
    }

    private int originalIndex=0;
    public void setStreamingState(boolean isStreaming) {
        Log.i(TAG, "Streaming State: " + (isStreaming ? "ACTIVE" : "INACTIVE"));
        this.isStreaming = isStreaming;
        runOnUiThread(() -> {
//            if (isStreaming ) {
//                if(surfaceView!=null) {
//                    ViewGroup parent = (ViewGroup) surfaceView.getParent();
//                    //originalIndex = parent.indexOfChild(surfaceView);
//                    surfaceView.setZOrderOnTop(true);
//                    surfaceView.bringToFront();
//                    parent.requestLayout();
//                    parent.invalidate();
//                }
//                if (webView!=null&&Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                    webView.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_JOYSTICK);
//                }
//            } else {
//                if(surfaceView!=null){
//                    ViewGroup parent = (ViewGroup) surfaceView.getParent();
//                    surfaceView.setZOrderOnTop(false);
//                    parent.removeView(surfaceView);
//                    parent.addView(surfaceView, 0);
//                }
//                resetGamepadDispatch();
//                if (webView != null) {
//                    webView.setVisibility(View.VISIBLE);
//                }
//            }
        });
    }
    public void showNativeKeyboardDialog(String inputValue,String title) {
        Dialog dialog = new Dialog(this, R.style.XboxDialogTheme);
        dialog.setContentView(R.layout.dialog_keyboard);
        dialog.setCancelable(false);

        EditText input = dialog.findViewById(R.id.keyboard_input);
        Button btnClose = dialog.findViewById(R.id.btn_close);
        Button btnSubmit = dialog.findViewById(R.id.btn_submit);
        TextView dialogtitle = dialog.findViewById(R.id.keyboard_title);
        input.setText(inputValue);
        dialogtitle.setText(title);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (webView == null) return;
                String text = s.toString();
                String script = "if (window.updateKeyboardInput) { window.updateKeyboardInput(" + JSONObject.quote(text) + "); }";
                webView.evaluateJavascript(script, null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                if (webView != null) webView.evaluateJavascript("if (window.submitKeyboardInput) { window.submitKeyboardInput(); }", null);
                dialog.dismiss();
                return true;
            }
            return false;
        });

        btnClose.setOnClickListener(v -> {
            if (webView != null) webView.evaluateJavascript("if (window.closeKeyboardInput) { window.closeKeyboardInput(); }", null);
            dialog.dismiss();
        });

        btnSubmit.setOnClickListener(v -> {
            if (webView != null) webView.evaluateJavascript("if (window.submitKeyboardInput) { window.submitKeyboardInput(); }", null);
            dialog.dismiss();
        });

        dialog.show();
        btnSubmit.requestFocus();
    }






    @Override
    protected void onDestroy() {
        if (performanceDialog != null) {
            performanceDialog.dismiss();
            performanceDialog = null;
        }

        if (activeToastDialog != null) {
            activeToastDialog.dismiss();
            activeToastDialog = null;
        }

        if (legacyInputChannel != null) {
            legacyInputChannel.shutdown();
        }

        if (streamingService != null) {
            try { streamingService.unregisterCallback(streamingCallback); } catch (RemoteException ignored) {}
        }

        try { unbindService(serviceConnection); } catch (Exception ignored) {}

        if (webView != null) {
            if (rootLayout != null) rootLayout.removeView(webView);
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    private class CustomWebChromeClient extends WebChromeClient {
        private boolean isScriptInjected = false;
        public void resetInjection() { isScriptInjected = false; }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (loadingText != null) {
                loadingText.setText(newProgress + "%");
            }
            if (newProgress > 30 && !isScriptInjected && view.getUrl() != null && view.getUrl().contains("play")) {
                isScriptInjected = true;

                view.evaluateJavascript(loadScriptFromAssets(), null);
            }
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            Log.d(TAG, "WebView Console: " + consoleMessage.message());
            return true;
        }
    }

    private String loadScriptFromAssets() {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = getAssets().open("index.js");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        } catch (IOException ignored) {}
        return sb.toString();
    }

    public void showStreamingMenuDialog() {
        Dialog dialog = new Dialog(this, R.style.XboxDialogTheme);
        dialog.setContentView(R.layout.dialog_streaming_menu);
        dialog.setCancelable(true);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getWindow().getDecorView().requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_JOYSTICK);
                }
            }
        });
        Button btnExit = dialog.findViewById(R.id.btn_exit_game);
        Button btnSettings = dialog.findViewById(R.id.btn_settings);
        Button btnAppSettings = dialog.findViewById(R.id.btn_app_settings);
        Button btnReloadXbox = dialog.findViewById(R.id.btn_reload_xbox);
        Button btnPerformanceToggle = dialog.findViewById(R.id.btn_performance_toggle);
        Button btnNexus = dialog.findViewById(R.id.btn_open_nexus);
        Button btnMic = dialog.findViewById(R.id.btn_mic_toggle);
        Button btnClose = dialog.findViewById(R.id.btn_close_dialog);
        if(!isStreaming()){
            btnExit.setVisibility(View.GONE);
            btnPerformanceToggle.setVisibility(View.GONE);
            btnNexus.setVisibility(View.GONE);
            btnMic.setVisibility(View.GONE);
        }

        // Initial Mic State
        boolean micCurrentlyEnabled = false;
        try {
            if (streamingService != null) micCurrentlyEnabled = streamingService.isMicrophoneEnabled();
        } catch (RemoteException ignored) {}
        btnMic.setText(micCurrentlyEnabled ? "Microphone: Enabled" : "Microphone: Disabled");

        btnMic.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                dialog.dismiss();
                return;
            }

            try {
                if (streamingService != null) {
                    boolean newState = !streamingService.isMicrophoneEnabled();
                    streamingService.setMicrophoneEnabled(newState);
                    btnMic.setText(newState ? "Microphone: Enabled" : "Microphone: Disabled");
                    showCustomToast(newState ? "Microphone enabled" : "Microphone muted");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to toggle mic", e);
            }
        });
        btnPerformanceToggle.setText(isPerformanceOverlayVisible ? "Disable in game performance" : "Show in game performance");
        btnPerformanceToggle.setOnClickListener(v -> {
            isPerformanceOverlayVisible = !isPerformanceOverlayVisible;
            if (isPerformanceOverlayVisible) {
                if (performanceDialog == null) {
                    performanceDialog = new PerformanceDialog(this);
                }
                performanceDialog.show();
                try { streamingService.setRenderSurface(null, 41, 40); }
                catch (RemoteException e) { Log.e(TAG, "Cannot show performance states:"+ e.getMessage()); }
            } else {
                if (performanceDialog != null) {
                    try { streamingService.setRenderSurface(null, 51, 50); }
                    catch (RemoteException e) { Log.e(TAG, "Cannot close performance states:"+ e.getMessage()); }
                    performanceDialog.dismiss();
                }
            }
            dialog.dismiss();
        });

        btnExit.setOnClickListener(v -> {
            isStreaming = false;
            resetGamepadDispatch();
            if (legacyInputChannel != null) {
                legacyInputChannel.shutdown();
            }
            if (streamingService != null) {
                try {
                    streamingService.closeSession();

                        if(surfaceView!=null){
                            ViewGroup parent = (ViewGroup) surfaceView.getParent();
                            surfaceView.setZOrderOnTop(false);
                            parent.removeView(surfaceView);
                            parent.addView(surfaceView, 0);
                        }
                        resetGamepadDispatch();
                        if (webView != null) {
                            webView.setVisibility(View.VISIBLE);
                        }

                } catch (RemoteException e) { Log.e(TAG, "closeSession", e); }
            }

            showWebview();
            dialog.dismiss();
        });

        btnSettings.setOnClickListener(v -> {
            showSettingsDialog();
            dialog.dismiss();
        });

        btnAppSettings.setOnClickListener(v -> {
            showAppSettingsDialog();
            dialog.dismiss();
        });

        btnReloadXbox.setOnClickListener(v -> {
            if (webView != null) {
                webView.reload();
            }
            dialog.dismiss();
        });

        btnNexus.setOnClickListener(v -> {
            if ("unreliableinput".equals(activeInputChannel) && gamepadListener != null) {
                gamepadListener.pressNexusOnce();
            } else if ("input".equals(activeInputChannel) && controllerLe != null) {
                controllerLe.pressNexusOnce();
            }
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        btnExit.requestFocus();
    }


    private class PerformanceDialog extends Dialog {
        private TextView tvStats;

        public PerformanceDialog(@NonNull Context context) {
            super(context, android.R.style.Theme_Translucent_NoTitleBar);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.layout_performance_overlay);
            tvStats = findViewById(R.id.tv_performance_stats);

            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = 20; // top margin
            
            // Critical flags for non-modal behavior
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            
            getWindow().setAttributes(lp);
        }

        public void updateStats(String stats) {
            if (tvStats != null) {
                tvStats.setText(stats);
            }
        }
    }


    private class CXdialoge extends Dialog {
        private SeekBar sbDeadzone;
        private SeekBar sbSensitivity;
        private androidx.appcompat.widget.SwitchCompat swUnreliableInput;
        private StickTestView stickTestLeft;
        private StickTestView stickTestRight;
        private Button btnStartTesting;

        private boolean testingActive = false;

        private final float[] leftProcessed = new float[2];
        private final float[] rightProcessed = new float[2];

        CXdialoge(@NonNull Context context) {
            super(context, R.style.XboxDialogTheme);
            setCancelable(true);
        }

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.dialog_settings);

            TextView tvDeadzone = findViewById(R.id.tv_deadzone);
            sbDeadzone = findViewById(R.id.sb_deadzone);
            TextView tvSensitivity = findViewById(R.id.tv_sensitivity);
            sbSensitivity = findViewById(R.id.sb_sensitivity);
            swUnreliableInput = findViewById(R.id.sw_unreliable_input);
            stickTestLeft = findViewById(R.id.stick_test_left);
            stickTestRight = findViewById(R.id.stick_test_right);
            Button btnCancel = findViewById(R.id.btn_cancel);
            Button btnApply = findViewById(R.id.btn_apply);
            btnStartTesting = findViewById(R.id.btn_start_testing);

            btnStartTesting.setOnClickListener(v -> {
                testingActive = !testingActive;
                btnStartTesting.setText(testingActive ? "Stop Testing" : "Start Testing");
                if (gamepadListener != null) {
                    gamepadListener.setTestModeActive(testingActive);
                }
                if (!testingActive) {
                    // Snap both dots back to center so the visualizer doesn't show a stale reading
                    // while testing is off.
                    stickTestLeft.setPositions(0f, 0f, 0f, 0f);
                    stickTestRight.setPositions(0f, 0f, 0f, 0f);
                }
            });

            if (gamepadListener != null) {
                float currentDeadzone = gamepadListener.getStickDeadzone();
                float currentSensitivity = gamepadListener.getCameraSensitivity();
                sbDeadzone.setProgress((int) (currentDeadzone * 100));
                tvDeadzone.setText(String.format("Stick Deadzone: %.2f", currentDeadzone));
                stickTestLeft.setDeadzone(currentDeadzone);
                stickTestRight.setDeadzone(currentDeadzone);
                sbSensitivity.setProgress((int) (currentSensitivity * 10));
                tvSensitivity.setText(String.format("Camera Sensitivity: %.1f", currentSensitivity));

                SharedPreferences gpPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                swUnreliableInput.setChecked(gpPrefs.getBoolean(KEY_USE_UNRELIABLE_INPUT, true));
            }

            sbDeadzone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    float deadzone = progress / 100.0f;
                    tvDeadzone.setText(String.format("Stick Deadzone: %.2f", deadzone));
                    stickTestLeft.setDeadzone(deadzone);
                    stickTestRight.setDeadzone(deadzone);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            sbSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    tvSensitivity.setText(String.format("Camera Sensitivity: %.1f", progress / 10.0f));
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            btnCancel.setOnClickListener(v -> dismiss());

            btnApply.setOnClickListener(v -> {
                float newDeadzone = sbDeadzone.getProgress() / 100.0f;
                float newSensitivity = sbSensitivity.getProgress() / 10.0f;
                boolean useUnreliable = swUnreliableInput.isChecked();

                if (gamepadListener != null) {
                    gamepadListener.setStickDeadzone(newDeadzone);
                    gamepadListener.setCameraSensitivity(newSensitivity);
                }
                if (controllerLe != null) {
                    controllerLe.setStickDeadzone(newDeadzone);
                    controllerLe.setCameraSensitivity(newSensitivity);
                }
                
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putFloat("camera_deadzone", newDeadzone);
                editor.putFloat("gamepad_sensitivity", newSensitivity);
                editor.putBoolean(KEY_USE_UNRELIABLE_INPUT, useUnreliable);
                editor.apply();

                activeInputChannel = useUnreliable ? "unreliableinput" : "input";
                if (webView != null) {
                    webView.evaluateJavascript("if (window.setUseUnreliableInput) { window.setUseUnreliableInput(" + useUnreliable + "); }", null);
                }

                dismiss();
            });
        }

        @Override
        public void show() {
            super.show();
            Button btnApply = findViewById(R.id.btn_apply);
            if (btnApply != null) btnApply.requestFocus();
        }

        @Override
        public void dismiss() {
            testingActive = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Objects.requireNonNull(getWindow()).getDecorView().requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_JOYSTICK);
            }
            super.dismiss();
        }

        // --- Live stick test: intercepted directly from the window's joystick events. No dependency
        // on AndroidGamepadListener's callback pipeline — the dot positions are computed straight from
        // whatever the sliders currently show (even pre-Apply), same as before. ---



        @Override
        public boolean dispatchGenericMotionEvent(@NonNull MotionEvent event) {
            if (!testingActive) {
                return super.dispatchGenericMotionEvent(event);
            }

            if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
                return super.dispatchGenericMotionEvent(event);
            }

            InputDevice device = event.getDevice();
            if (device == null || device.isVirtual()) {
                return super.dispatchGenericMotionEvent(event);
            }

            int axisRSX = MotionEvent.AXIS_Z;
            int axisRSY = MotionEvent.AXIS_RZ;
            float curveExponent = 1.0f;
            if (gamepadListener != null) {
                axisRSX = gamepadListener.getRightStickAxisX();
                axisRSY = gamepadListener.getRightStickAxisY();
                curveExponent = gamepadListener.getRightStickResponseCurve();
            }

            float rawLeftX = event.getAxisValue(MotionEvent.AXIS_X);
            float rawLeftY = event.getAxisValue(MotionEvent.AXIS_Y);
            float rawRightX = event.getAxisValue(axisRSX);
            float rawRightY = event.getAxisValue(axisRSY);

            float liveDeadzone = sbDeadzone.getProgress() / 100.0f;
            float liveSensitivity = sbSensitivity.getProgress() / 10.0f;

            // Left stick: deadzone only, no camera sensitivity (matches the real input path).
            AndroidGamepadListener.computeStickResponse(
                    rawLeftX, rawLeftY, liveDeadzone, 1.0f, 1.0f, leftProcessed);

            // Right stick: deadzone + curve + the sensitivity value currently on the slider.
            AndroidGamepadListener.computeStickResponse(
                    rawRightX, rawRightY, liveDeadzone, curveExponent, liveSensitivity, rightProcessed);

            stickTestLeft.setPositions(rawLeftX, rawLeftY, leftProcessed[0], leftProcessed[1]);
            stickTestRight.setPositions(rawRightX, rawRightY, rightProcessed[0], rightProcessed[1]);

            return true; // consumed — swallow so it doesn't also drive game input while the dialog is open
        }
    }

    // --- Activity method: now just builds and shows the dialog above ---
    private void showSettingsDialog() {
        CXdialoge dialog = new CXdialoge(this);
        dialog.show();
    }

    private void showAppSettingsDialog() {
        Dialog dialog = new Dialog(this, R.style.XboxDialogTheme);
        dialog.setContentView(R.layout.dialog_app_settings);
        dialog.setCancelable(true);

        androidx.appcompat.widget.AppCompatSpinner spinnerRegion = dialog.findViewById(R.id.spinner_region);
        androidx.appcompat.widget.AppCompatSpinner spinnerRes = dialog.findViewById(R.id.spinner_resolution);
        androidx.appcompat.widget.AppCompatSpinner spinnerBitrate = dialog.findViewById(R.id.spinner_bitrate);
        androidx.appcompat.widget.AppCompatSpinner spinnerUA = dialog.findViewById(R.id.spinner_user_agent);
        TextView tvScale = dialog.findViewById(R.id.tv_initial_scale);
        SeekBar sbScale = dialog.findViewById(R.id.sb_initial_scale);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnApply = dialog.findViewById(R.id.btn_apply);

        // Region options
        String[] regions = {"us", "br", "kr", "jp", "pl", "es", "uk", "fr"};
        String[] regionLabels = {"United States", "Brazil", "Korea", "Japan", "Poland", "Spain", "United Kingdom", "France"};
        android.widget.ArrayAdapter<String> regionAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, regionLabels);
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRegion.setAdapter(regionAdapter);

        // Resolution options
        String[] resolutions = {"Auto","720","720HQ", "1080","1080HQ", "1440"};
        android.widget.ArrayAdapter<String> resAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, resolutions);
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRes.setAdapter(resAdapter);

        // Bitrate options
        Integer[] bitrateValues = {0, 5, 10, 15, 20, 30, 40, 50};
        String[] bitrateLabels = {"Auto", "5 Mbps", "10 Mbps", "15 Mbps", "20 Mbps", "30 Mbps", "40 Mbps", "50 Mbps"};
        android.widget.ArrayAdapter<String> bitrateAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bitrateLabels);
        bitrateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBitrate.setAdapter(bitrateAdapter);

        // User Agent options
        String[] uaKeys = {"default", "edge", "chrome", "tizen", "android_tv", "fire_tv", "xbox", "ps5", "android_mobile", "ios", "mac"};
        String[] uaLabels = {"Default", "Edge (Desktop)", "Chrome (Desktop)", "Samsung TV (Tizen)", "Android TV", "Fire TV", "Xbox", "PlayStation 5", "Android Mobile", "iOS Phone", "macOS (Safari)"};
        android.widget.ArrayAdapter<String> uaAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, uaLabels);
        uaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUA.setAdapter(uaAdapter);

        // Load current selection
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String currentRegion = prefs.getString(KEY_REGION, "us");
        String currentRes = prefs.getString(KEY_RESOLUTION, "1080");
        int currentBitrate = prefs.getInt(KEY_BITRATE, 0);
        String currentUA = prefs.getString(KEY_USER_AGENT, "default");
        int currentScale = prefs.getInt(KEY_INITIAL_SCALE, 0);

        for (int i = 0; i < regions.length; i++) {
            if (regions[i].equals(currentRegion)) {
                spinnerRegion.setSelection(i);
                break;
            }
        }
        for (int i = 0; i < resolutions.length; i++) {
            if (resolutions[i].equals(currentRes)) {
                spinnerRes.setSelection(i);
                break;
            }
        }
        for (int i = 0; i < bitrateValues.length; i++) {
            if (bitrateValues[i] == currentBitrate) {
                spinnerBitrate.setSelection(i);
                break;
            }
        }
        for (int i = 0; i < uaKeys.length; i++) {
            if (uaKeys[i].equals(currentUA)) {
                spinnerUA.setSelection(i);
                break;
            }
        }

        sbScale.setProgress(currentScale);
        tvScale.setText(currentScale == 0 ? "WebView Initial Scale: Auto" : "WebView Initial Scale: " + currentScale + "%");

        sbScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvScale.setText(progress == 0 ? "WebView Initial Scale: Auto" : "WebView Initial Scale: " + progress + "%");
                webView.setInitialScale(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnApply.setOnClickListener(v -> {
            String selectedRegion = regions[spinnerRegion.getSelectedItemPosition()];
            String selectedRes = resolutions[spinnerRes.getSelectedItemPosition()];
            int selectedBitrate = bitrateValues[spinnerBitrate.getSelectedItemPosition()];
            String selectedUA = uaKeys[spinnerUA.getSelectedItemPosition()];
            int selectedScale = sbScale.getProgress();

            boolean uaChanged = !selectedUA.equals(currentUA);
            boolean scaleChanged = selectedScale != currentScale;

            prefs.edit()
                    .putString(KEY_REGION, selectedRegion)
                    .putString(KEY_RESOLUTION, selectedRes)
                    .putInt(KEY_BITRATE, selectedBitrate)
                    .putString(KEY_USER_AGENT, selectedUA)
                    .putInt(KEY_INITIAL_SCALE, selectedScale)
                    .apply();

            if (webView != null) {
                @SuppressLint("DefaultLocale") String js = String.format("window.BX_TARGET_REGION = '%s'; window.BX_TARGET_RES = '%s'; window.BX_TARGET_BITRATE = %d;",
                        selectedRegion, selectedRes, selectedBitrate);
                webView.evaluateJavascript(js, null);

                if (scaleChanged) {
                    webView.setInitialScale(selectedScale);
                }
                
                if (uaChanged || scaleChanged) {
                    if (uaChanged) {
                        String uaString = USER_AGENTS.get(selectedUA);
                        webView.getSettings().setUserAgentString(uaString);
                        showCustomToast("User-Agent changed. Reloading...");
                        closeWebview();
                        showWebview();
                    } else {
                        webView.setInitialScale(selectedScale);

                        showCustomToast("Scale changed.");
                    }

                } else {
                    showCustomToast("Settings applied.");
                }
            }
            dialog.dismiss();
        });

        dialog.show();
        btnApply.requestFocus();
    }
}
