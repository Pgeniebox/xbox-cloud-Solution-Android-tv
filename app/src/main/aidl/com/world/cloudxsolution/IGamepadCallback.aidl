package com.world.cloudxsolution;

oneway interface IGamepadCallback {
    // A finished, encoded input frame, ready to hand straight to
    // DataChannel.send(). Replaces the old unreliableSendRaw Consumer<ByteBuffer>.
    void onFrameReady(in byte[] frame);

    // Replaces AndroidGamepadListener.setOnMenuTrigger(Runnable).
    void onMenuTriggered();

    // Optional but useful: surfaces prepare()'s human-readable result
    // (e.g. "Xbox Wireless Controller | Connected") back to the UI toast,
    // same as the return value of the old in-process prepare() call.
    void onDeviceStatus(String status);

    // Fired once per prepareById() call (i.e. once per newly-detected device,
    // same "only re-map on descriptor change" gate as the original prepare()).
    // Tells MainActivity exactly which MotionEvent axes to read for this
    // device going forward — mirrors axisRSX/axisRSY/axisLT/axisRT/hasHatAxes.
    void onAxisMappingReady(int deviceId, int rightStickAxisX, int rightStickAxisY,
                             int leftTriggerAxis, int rightTriggerAxis, boolean hasHatAxes);
}
