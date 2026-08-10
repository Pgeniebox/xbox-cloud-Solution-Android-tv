package com.world.cloudxsolution;

import org.webrtc.DataChannel;

import java.nio.ByteBuffer;

/**
 * Shows how to connect an already-open WebRTC DataChannel (created
 * and negotiated by your existing peer connection code — this class
 * does not create or negotiate the channel itself) to InputChannel.
 */
public final class WebRtcInputWiring {

    /**
     * Call this once your DataChannel's state is OPEN.
     * `dataChannel` is whatever channel your existing WebRTC setup
     * created via peerConnection.createDataChannel("input", ...).
     */
    public static InputChannel createInputChannel(
            DataChannel dataChannel, int serverWidth, int serverHeight) {

        return new InputChannel(
                byteBuffer -> sendOverDataChannel(dataChannel, byteBuffer),
                serverWidth,
                serverHeight
        );
    }

    private static void sendOverDataChannel(DataChannel dataChannel, ByteBuffer data) {
        if (dataChannel == null
                || dataChannel.state() != DataChannel.State.OPEN) {
            return;
        }

        // NOTE: this now runs on InputChannel's dedicated sender
        // thread, not the Android input-dispatch/main thread.
        // DataChannel.send() is safe to call off the main thread —
        // that's the whole point of moving it here — but confirm
        // this against whatever WebRTC binding version you're on if
        // you're unsure.

        // data is InputPacket's internally-reused buffer; duplicate()
        // gives DataChannel.Buffer its own position/limit/mark so a
        // later reused write from InputPacket can't race with
        // whatever WebRTC does with this buffer after send() returns.
        ByteBuffer copy = data.duplicate();
        DataChannel.Buffer buffer = new DataChannel.Buffer(copy, true /* binary */);
        dataChannel.send(buffer);
    }
}

/*
 * Full wiring example, assuming you already have a PeerConnection
 * from your existing WebRTC negotiation code:
 *
 *   DataChannel.Init init = new DataChannel.Init();
 *   init.ordered = false;        // see reliability note below
 *   init.maxRetransmits = 0;     // UDP-like send-and-forget
 *   DataChannel dc = peerConnection.createDataChannel("input", init);
 *
 *   dc.registerObserver(new DataChannel.Observer() {
 *       @Override public void onStateChange() {
 *           if (dc.state() == DataChannel.State.OPEN) {
 *               InputChannel inputChannel =
 *                   WebRtcInputWiring.createInputChannel(dc, 1920, 1080);
 *               inputChannel.start(0); // starts sender thread + sends initial metadata packet
 *
 *               AndroidGamepadListener listener =
 *                   new AndroidGamepadListener(inputChannel, 0, 1.5f, 0.12f);
 *               // wire listener.onKeyDown/onKeyUp/onGenericMotion into
 *               // your Activity/dispatch class as shown previously
 *           }
 *       }
 *       @Override public void onMessage(DataChannel.Buffer buffer) {}
 *       @Override public void onBufferedAmountChange(long amount) {}
 *   });
 *
 *   // When the session ends / the channel closes:
 *   inputChannel.shutdown(); // stops the sender thread cleanly
 *
 * Reliability/ordering tradeoff worth deciding deliberately:
 *   init.ordered = true  -> frames arrive in order, but a dropped
 *                           packet blocks delivery of newer ones
 *                           until retransmitted (adds latency spikes)
 *   init.ordered = false -> frames can arrive out of order; combined
 *                           with init.maxRetransmits = 0 this becomes
 *                           UDP-like "send and forget," which is
 *                           usually what you want for real-time input
 *                           where a stale dropped frame is worse than
 *                           no frame — the next state update resends
 *                           current state anyway.
 */
