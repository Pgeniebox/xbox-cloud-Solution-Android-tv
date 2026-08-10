package com.world.cloudxsolution;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Simplified synchronous InputChannel, matching the logic from the 
 * backup version that worked with legacy input.
 */
public final class InputChannel {

    private final AtomicLong sequence = new AtomicLong(0);
    private final Consumer<ByteBuffer> sendRaw;
    private final InputPacket packetWriter = new InputPacket();

    private int serverWidth;
    private int serverHeight;

    public InputChannel(Consumer<ByteBuffer> sendRaw, int serverWidth, int serverHeight) {
        this.sendRaw = sendRaw;
        this.serverWidth = serverWidth;
        this.serverHeight = serverHeight;
    }

    public void onResolutionChange(int width, int height) {
        this.serverWidth = width;
        this.serverHeight = height;
    }

    /**
     * Sends the initial metadata packet. Call once the underlying transport is open.
     */
    public synchronized void start(int maxTouchpoints) {
        sequence.set(0); // Reset sequence for new session
        long seq = sequence.get();
        ByteBuffer packet = packetWriter.metadataPacket(seq, maxTouchpoints);
        sendRaw.accept(packet);
    }

    /** Stop the sender, no-op in synchronous mode. */
    public void shutdown() {
    }

    /**
     * Called from the input dispatch thread. Synchronously generates 
     * and ships the packet.
     */
    public synchronized void onFrame(GamepadFrame frame) {
        long seq = sequence.incrementAndGet();
        ByteBuffer packet = packetWriter.forFrame(seq, frame, serverWidth, serverHeight);
        sendRaw.accept(packet);
    }
}
