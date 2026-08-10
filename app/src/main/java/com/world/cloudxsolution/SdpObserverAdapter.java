package com.world.cloudxsolution;

import android.util.Log;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public abstract class SdpObserverAdapter implements SdpObserver {
    private static final String TAG = "SdpObserverAdapter";
    @Override public void onCreateSuccess(SessionDescription sdp) { }
    @Override public void onSetSuccess() { }
    @Override public void onCreateFailure(String error) {
        Log.e(TAG, "SDP Create Failure: " + error);
    }
    @Override public void onSetFailure(String error) {
        Log.e(TAG, "SDP Set Failure: " + error);
    }
}