package org.webrtc;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import android.os.Process;

/* JADX INFO: loaded from: classes.jar:org/webrtc/EglThread.class */
public class EglThread implements RenderSynchronizer.Listener {
    private final ReleaseMonitor releaseMonitor;
    private final HandlerWithExceptionCallbacks handler;
    private final EglBase.EglConnection eglConnection;
    private final RenderSynchronizer renderSynchronizer;
    private final List<RenderUpdate> pendingRenderUpdates = new ArrayList();
    private boolean renderWindowOpen = true;

    /* JADX INFO: loaded from: classes.jar:org/webrtc/EglThread$ReleaseMonitor.class */
    public interface ReleaseMonitor {
        boolean onRelease(EglThread eglThread);
    }

    /* JADX INFO: loaded from: classes.jar:org/webrtc/EglThread$RenderUpdate.class */
    public interface RenderUpdate {
        void update(boolean z);
    }

    public static EglThread create(@Nullable ReleaseMonitor releaseMonitor, @Nullable EglBase.Context sharedContext, int[] configAttributes, @Nullable RenderSynchronizer renderSynchronizer) {
        HandlerThread renderThread = new HandlerThread("EglThread") {
            @Override
            protected void onLooperPrepared() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
            }
        };
        renderThread.start();
        HandlerWithExceptionCallbacks handler = new HandlerWithExceptionCallbacks(renderThread.getLooper());
        EglBase.EglConnection eglConnection = (EglBase.EglConnection) ThreadUtils.invokeAtFrontUninterruptibly(handler, () -> {
            if (sharedContext == null) {
                return EglBase.EglConnection.createEgl10(configAttributes);
            }
            return EglBase.EglConnection.create(sharedContext, configAttributes);
        });
        return new EglThread(releaseMonitor != null ? releaseMonitor : eglThread -> {
            return true;
        }, handler, eglConnection, renderSynchronizer);
    }

    public static EglThread create(@Nullable ReleaseMonitor releaseMonitor, @Nullable EglBase.Context sharedContext, int[] configAttributes) {
        return create(releaseMonitor, sharedContext, configAttributes, null);
    }

    /* JADX INFO: loaded from: classes.jar:org/webrtc/EglThread$HandlerWithExceptionCallbacks.class */
    private static class HandlerWithExceptionCallbacks extends Handler {
        private final Object callbackLock;

        @GuardedBy("callbackLock")
        private final List<Runnable> exceptionCallbacks;

        public HandlerWithExceptionCallbacks(Looper looper) {
            super(looper);
            this.callbackLock = new Object();
            this.exceptionCallbacks = new ArrayList();
        }

        @Override // android.os.Handler
        public void dispatchMessage(Message msg) {
            try {
                super.dispatchMessage(msg);
            } catch (Exception e) {
                Logging.e("EglThread", "Exception on EglThread", e);
                synchronized (this.callbackLock) {
                    for (Runnable callback : this.exceptionCallbacks) {
                        callback.run();
                    }
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException(e);
                }
            }
        }

        public void addExceptionCallback(Runnable callback) {
            synchronized (this.callbackLock) {
                this.exceptionCallbacks.add(callback);
            }
        }

        public void removeExceptionCallback(Runnable callback) {
            synchronized (this.callbackLock) {
                this.exceptionCallbacks.remove(callback);
            }
        }
    }

    private EglThread(ReleaseMonitor releaseMonitor, HandlerWithExceptionCallbacks handler, EglBase.EglConnection eglConnection, RenderSynchronizer renderSynchronizer) {
        this.releaseMonitor = releaseMonitor;
        this.handler = handler;
        this.eglConnection = eglConnection;
        this.renderSynchronizer = renderSynchronizer;
        if (renderSynchronizer != null) {
            renderSynchronizer.registerListener(this);
        }
    }

    public void release() {
        if (!this.releaseMonitor.onRelease(this)) {
            return;
        }
        if (this.renderSynchronizer != null) {
            this.renderSynchronizer.removeListener(this);
        }
        HandlerWithExceptionCallbacks handlerWithExceptionCallbacks = this.handler;
        EglBase.EglConnection eglConnection = this.eglConnection;
        Objects.requireNonNull(eglConnection);
        handlerWithExceptionCallbacks.post(eglConnection::release);
        this.handler.getLooper().quitSafely();
    }

    public EglBase createEglBaseWithSharedConnection() {
        return EglBase.create(this.eglConnection);
    }

    public Handler getHandler() {
        return this.handler;
    }

    public void addExceptionCallback(Runnable callback) {
        this.handler.addExceptionCallback(callback);
    }

    public void removeExceptionCallback(Runnable callback) {
        this.handler.removeExceptionCallback(callback);
    }

    public void scheduleRenderUpdate(RenderUpdate update) {
        if (this.renderWindowOpen) {
            update.update(true);
        } else {
            this.pendingRenderUpdates.add(update);
        }
    }

    @Override // org.webrtc.RenderSynchronizer.Listener
    public void onRenderWindowOpen() {
        this.handler.post(() -> {
            this.renderWindowOpen = true;
            for (RenderUpdate update : this.pendingRenderUpdates) {
                update.update(false);
            }
            this.pendingRenderUpdates.clear();
        });
    }

    @Override // org.webrtc.RenderSynchronizer.Listener
    public void onRenderWindowClose() {
        this.handler.post(() -> {
            this.renderWindowOpen = false;
        });
    }
}
