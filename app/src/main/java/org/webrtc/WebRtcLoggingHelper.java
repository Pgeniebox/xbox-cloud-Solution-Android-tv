package org.webrtc;

/**
 * Bridge class in the org.webrtc package to access package-private members
 * of the WebRTC library.
 */
public final class WebRtcLoggingHelper {
    
    public static void injectCustomLogger(Loggable loggable, Logging.Severity severity) {
        Logging.injectLoggable(loggable, severity);
    }

    public static void removeCustomLogger() {
        Logging.deleteInjectedLoggable();
    }
}
