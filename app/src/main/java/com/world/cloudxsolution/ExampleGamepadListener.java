package com.world.cloudxsolution;

/**
 * Example of how a real gamepad listener (JInput, SDL2 binding, etc.)
 * would drive this. The key fix vs. the original JS: `currentFrame`
 * is REPLACED, never mutated, on each event. Whatever library you use
 * to poll the physical gamepad, this is the shape to aim for.
 */
public final class ExampleGamepadListener {
/*
    private final InputChannel channel;

    // Holds the latest known state so button-only events can carry
    // forward the current stick position, and vice versa. This field
    // itself is only ever replaced (volatile write), never mutated,
    // so there's no aliasing bug: old frames already sent are
    // untouched by future writes.
    private volatile GamepadFrame currentFrame;

    public ExampleGamepadListener(InputChannel channel, int gamepadIndex) {
        this.channel = channel;
        this.currentFrame = GamepadFrame.builder(gamepadIndex).build();
    }

    public void onButtonDown(String button) {
        currentFrame = currentFrame.withButton(button, true);
        channel.onFrame(currentFrame);
    }

    public void onButtonUp(String button) {
        currentFrame = currentFrame.withButton(button, false);
        channel.onFrame(currentFrame);
    }

    public void onLeftStickMove(float x, float y) {
        currentFrame = currentFrame.withLeftStick(x, y);
        channel.onFrame(currentFrame);
    }

    public void onRightStickMove(float x, float y) {
        currentFrame = currentFrame.withRightStick(x, y);
        channel.onFrame(currentFrame);
    }
    */
}
