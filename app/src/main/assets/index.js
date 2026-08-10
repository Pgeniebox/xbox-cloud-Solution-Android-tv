"use strict";


// Bridge for Native Android WebRTC
window.handleAnswer = function(sdp) {
    console.log("[BxC] handleAnswer from Java, sdp length: " + (sdp ? sdp.length : 0));
    if (window.pendingAnswerResolver) {
        window.pendingAnswerResolver({ type: 'answer', sdp: sdp });
        window.pendingAnswerResolver = null;
    } else {
        console.warn("[BxC] No pendingAnswerResolver found");
    }
};

window.handleOffer = function(sdp) {
    console.log("[BxC] handleOffer from Java, sdp length: " + (sdp ? sdp.length : 0));
    if (window.pendingOfferResolver) {
        window.pendingOfferResolver({ type: 'offer', sdp: sdp });
        window.pendingOfferResolver = null;
    } else {
        console.warn("[BxC] No pendingOfferResolver found");
    }
};

window.handleIceCandidate = function(sdpMid, sdpMLineIndex, sdp) {
    console.log("[BxC] handleIceCandidate from Java: " + sdpMid + " sdp length: " + (sdp ? sdp.length : 0));
    if (window.peer && window.peer.onicecandidate) {
        window.peer.onicecandidate({
            candidate: {
                candidate: sdp,
                sdpMid: sdpMid,
                sdpMLineIndex: sdpMLineIndex
            }
        });
    } else {
        console.warn("[BxC] handleIceCandidate: peer or onicecandidate missing");
    }
};

window.handleDataChannelMessage = function(label, binaryData, textData, isBinary) {
    console.log("[BxC] handleDataChannelMessage: " + label + " (binary: " + isBinary + ")");
    const dc = window.proxyDataChannels && window.proxyDataChannels[label];
    if (dc) {
        let data;
        if (isBinary) {
            data = window.bxToArrayBuffer(binaryData);
        } else {
            data = textData;
        }

        console.warn("[BxC] recivin data from channel " + label + ", data: " + data);
        if(label=='message'&&data.includes('HandshakeAck')){
        async function CXkeepalive(){

                                                            let CXtoken =  window.BX_EXPOSED.streamSession.gsToken;
                                                            let CXsessionPath=window.BX_EXPOSED.streamSession.sessionPath;
                                                            let CXendPoint=window.BX_EXPOSED.streamSession.domainName;
                                                            if(CXtoken.length&&CXtoken.length>0&&CXsessionPath.length&&CXsessionPath.length>0&&CXendPoint.length&&CXendPoint.length>0)
                                                           {
                                                            let CXurl=CXendPoint+'/'+CXsessionPath+'/keepalive';
                                                            console.log("[BxC] Keepalive URL:", CXurl,' token: ',CXtoken);
                                                             if (window.AndroidBridge && window.AndroidBridge.keepalive) {
                                                                            window.AndroidBridge.keepalive(CXurl,CXtoken);
                                                                        }
                                                                        return;}
                                                                        return setTimeout(CXkeepalive,10);
                                                           }
                                                       setTimeout(CXkeepalive,10);
        }
        // Create a real MessageEvent so the app can access .data
        const event = new MessageEvent('message', {
            data: data,
            origin: window.location.origin
        });
        dc.dispatchEvent(event);
        if (dc.onmessage) dc.onmessage(event);
    }
};

window.handleDataChannelState = function(label, state) {
    console.log("[BxC] handleDataChannelState: " + label + " -> " + state);
    const dc = window.proxyDataChannels && window.proxyDataChannels[label];
    if (dc) {
        dc.readyState = state.toLowerCase();
        if (dc.onopen && dc.readyState === 'open') dc.onopen();
        if (dc.onclose && dc.readyState === 'closed') dc.onclose();
    }
};

window.handleIceConnectionState = function(state) {
    console.log("[BxC] handleIceConnectionState: " + state);
    if (window.peer) {
        window.peer.iceConnectionState = state.toLowerCase();
        if (window.peer.oniceconnectionstatechange) window.peer.oniceconnectionstatechange();
        if (window.peer._listeners['iceconnectionstatechange']) {
            window.peer._listeners['iceconnectionstatechange'].forEach(l => l());
        }
    }
};

window.setControllerSettings = function(deadzone, sensitivity) {
    setPref("controller.deadzone", deadzone);
    setPref("controller.sensitivity", sensitivity);
}

window.setUseUnreliableInput = function(enabled) {
    window.BX_USE_UNRELIABLE_INPUT = enabled;
    BxLogger.info("Settings", "Updated UseUnreliableInput to:", enabled);
}
window.setPref = function lsSet(key, value) {
    localStorage.setItem(key, value);
};

window.getPref=function lsget(key){
return localStorage.getItem(key);
}

const BypassServerIps = {
    us: "143.244.47.65",
    br: "169.150.198.66",
    kr: "121.125.60.151",
    jp: "138.199.21.239",
    pl: "45.134.212.66",
    es: "80.58.61.250",
    uk: "62.24.134.1",
    fr: "212.27.40.240"
};

const ServerRegionMap = {
    us: "westus",
    br: "brazilsouth",
    kr: "koreacentral",
    jp: "japaneast",
    pl: "swedencentral",
    es: "spaincentral",
    uk: "uksouth",
    fr: "francecentral"
};

const BxLogger = {
    info: (tag, ...args) => console.log("%c[BxC]", "color:#008746;font-weight:bold;", tag, "//", ...args),
    error: (tag, ...args) => console.error("%c[BxC]", "color:#c10404;font-weight:bold;", tag, "//", ...args)
};

const NATIVE_FETCH = window.fetch;

function deepClone(obj) {
    if (!obj) return {};
    if ("structuredClone" in window) return structuredClone(obj);
    return JSON.parse(JSON.stringify(obj));
}

function getTargetRegion() {
    // Priority: window global > localStorage > default (us)
    return window.BX_TARGET_REGION || localStorage.getItem('BX_TARGET_REGION') || 'es';
}

function getTargetResolution() {
    // Priority: window global > localStorage > default (1080p)
    return window.BX_TARGET_RES || localStorage.getItem('BX_TARGET_RES') || '1080p';
}

function getTargetBitrate() {
    // Priority: window global > localStorage > default (0 - Auto)
    return window.BX_TARGET_BITRATE || parseInt(localStorage.getItem('BX_TARGET_BITRATE')) || 0;
}

function getOsNameFromResolution(resolution) {
    let osName;
    switch (resolution) {
        case "1080HQ":
            osName = "tizen";
            break;
        case "1080":
            osName = "windows";
            break;
        case "720":
            osName = "android";
            break;
        default:
            osName = "tizen";
            break;
    }
    return osName;
}

function generateMsDeviceInfo(osName) {
    return {
        appInfo: {
            env: {
                clientAppId: window.location.host,
                clientAppType: "browser",
                clientAppVersion: "26.1.97",
                clientSdkVersion: "10.3.7",
                httpEnvironment: "prod",
                sdkInstallId: ""
            }
        },
        dev: {
            os: { name: osName, ver: "22631.2715", platform: "desktop" },
            hw: { make: "Microsoft", model: "unknown", sdktype: "web" },
            browser: { browserName: "chrome", browserVersion: "140.0.3485.54" },
            displayInfo: {
                dimensions: { widthInPixels: 4096, heightInPixels: 2160 },
                pixelDensity: { dpiX: 1, dpiY: 1 }
            }
        }
    };
}

const BxExposed = {
    modifyPreloadedState: (state) => {
        try {
            state = deepClone(state);
            // Bypass region restriction check in preloaded state
            if (state.xcloud && state.xcloud.authentication) {
                const xCloud = state.xcloud.authentication.authStatusByStrategy.XCloud;
                if (xCloud && xCloud.type === 3 && xCloud.error.type === "UnsupportedMarketError") {
                    BxLogger.info("PreloadState", "Unsupported region detected, forcing bypass via redirect");
                    window.stop();
                    window.location.href = "https://www.xbox.com/en-US/play";
                }
            }
        } catch (e) {
            BxLogger.error("PreloadState", e);
        }
        return state;
    },

    modifyTitleInfo: (titleInfo) => {
        titleInfo = deepClone(titleInfo);
        if (titleInfo && titleInfo.details) {
            let supportedInputTypes = titleInfo.details.supportedInputTypes || [];

            // Ensure Controller is in supported input types to enable Play button
            if (!supportedInputTypes.includes("Controller")) {
                supportedInputTypes.push("Controller");
            }

            titleInfo.details.supportedInputTypes = supportedInputTypes;
            BxLogger.info("TitleInfo", "Modified title info for:", titleInfo.titleId);
        }
        return titleInfo;
    }
};

window.BX_EXPOSED = BxExposed;
window.BX_EXPOSED.streamSession = {
    gsToken: '',
    sessionPath: '',
    domainName: ''
};

class XcloudInterceptor {
    static async handleLogin(request, init) {
        const region = getTargetRegion();
        const ip = BypassServerIps[region] || BypassServerIps.us;

        BxLogger.info("XcloudInterceptor", "Using bypass IP for login:", region, "->", ip);

        request.headers.set("X-Forwarded-For", ip);

        let response;
        try {
            response = await NATIVE_FETCH(request, init);
        } catch (e) {
            BxLogger.error("XcloudInterceptor", "Login interception failed", e);
            throw e;
        }

        if (response.status === 200) {
            const obj = await response.clone().json();

            const targetServerRegion = ServerRegionMap[region];

            if (targetServerRegion && obj.offeringSettings && obj.offeringSettings.regions) {
                BxLogger.info("XcloudInterceptor", "Forcing streaming region to:", targetServerRegion);

                const filteredRegions = obj.offeringSettings.regions.filter(r => r.name.toLowerCase().includes(targetServerRegion));
                if (filteredRegions.length > 0) {
                    filteredRegions[0].isDefault = true;
                    obj.offeringSettings.regions = filteredRegions;
                }
            }

            response.json = () => Promise.resolve(obj);
        }

        return response;
    }

    static async handlePlay(request, init) {
        const res = getTargetResolution();
        const osName = getOsNameFromResolution(res);
        const deviceInfo = generateMsDeviceInfo(osName);

        BxLogger.info("XcloudInterceptor", "Patching play request for resolution:", res, "-> OS:", osName);

        // Inject hardware spoofing header
        request.headers.set("x-ms-device-info", JSON.stringify(deviceInfo));

        let finalRequest = request;
        // Modify body to include spoofed OS name
        try {
            const body = await request.clone().json();
            if (body.settings) {
                body.settings.osName = osName;
            }
            // Create a new request with the modified body
            finalRequest = new Request(request, {
                body: JSON.stringify(body)
            });
        } catch (e) {
            BxLogger.error("XcloudInterceptor", "Play request body modification failed", e);
        }

        const response = await NATIVE_FETCH(finalRequest, init);

        // Capture session info
        try {
            if (response.status >= 200 && response.status < 300) {
                const respBody = await response.clone().json();
                if (respBody.sessionPath) {
                    window.BX_EXPOSED.streamSession.sessionPath = respBody.sessionPath;
                }
                const url = typeof request === "string" ? request : request.url;
                // Use URL origin to avoid carrying over query parameters or path segments
                window.BX_EXPOSED.streamSession.domainName = new URL(url).origin;
                BxLogger.info("streamSession", "Captured sessionPath and domainName:", window.BX_EXPOSED.streamSession);
            }
        } catch (e) {
            BxLogger.error("XcloudInterceptor", "Session info capture failed", e);
        }

        return response;
    }

    static async handleConfiguration(request, init) {
        // Extract token from Authorization header
        const authHeader = request.headers.get("Authorization");
        if (authHeader && authHeader.startsWith("Bearer ")) {
            window.BX_EXPOSED.streamSession.gsToken = authHeader.substring(7);
            BxLogger.info("streamSession", "Captured gsToken from configuration request headers");
        }

        let response = await NATIVE_FETCH(request, init);
        let text = await response.clone().text();
        if (!text.length) return response;

        try {
            let obj = JSON.parse(text);
            let overrides = JSON.parse(obj.clientStreamingConfigOverrides || "{}") || {};

            overrides.inputConfiguration = overrides.inputConfiguration || {};
            // Force unreliable input channel (unreliableinput) if enabled
            const useUnreliable = window.BX_USE_UNRELIABLE_INPUT !== false;
            overrides.inputConfiguration.useUnreliableInput = useUnreliable;
            overrides.inputConfiguration.enableVibration = true;

            obj.clientStreamingConfigOverrides = JSON.stringify(overrides);

            BxLogger.info("XcloudInterceptor", "Forcing input configuration - unreliableinput:", useUnreliable);

            // Reconstruct response
            const modifiedResponse = new Response(JSON.stringify(obj), {
                status: response.status,
                statusText: response.statusText,
                headers: response.headers
            });

            return modifiedResponse;
        } catch (e) {
            BxLogger.error("XcloudInterceptor", "Configuration modification failed", e);
        }

        return response;
    }

    static async handle(request, init) {
        const url = typeof request === "string" ? request : request.url;
        if (url.endsWith("/v2/login/user")) {
            return XcloudInterceptor.handleLogin(request, init);
        } else if (url.endsWith("/sessions/cloud/play")) {
            return XcloudInterceptor.handlePlay(request, init);
        } else if (url.endsWith("/configuration")) {
            return XcloudInterceptor.handleConfiguration(request, init);
        }
        return NATIVE_FETCH(request, init);
    }
}

function interceptHttpRequests() {
    window.fetch = async (request, init) => {
        let url = typeof request === "string" ? request : request.url;

        if (url.includes("xboxlive.com") || url.includes("gssv-play-prod.xboxlive.com")) {
            if (typeof request === "string") {
                request = new Request(request, init);
            }
            return XcloudInterceptor.handle(request, init);
        }

        return NATIVE_FETCH(request, init);
    };
}

// Configuration Cache for Renegotiation Fix
window._lastFullConfig = null;

function patchWebSocket() {
    const OriginalWebSocket = window.WebSocket;
    window.WebSocket = function(url, protocols) {
        const ws = new OriginalWebSocket(url, protocols);
        const originalSend = ws.send;

        ws.send = function(data) {
            if (typeof data === 'string') {
                try {
                    const msg = JSON.parse(data);

                    // 1. Capture full configuration from 'offer'
                    if (msg.messageType === 'offer' && msg.configuration) {
                        // Check if this is a sparse renegotiation config (e.g. from ChatStreamManager)
                        if (msg.configuration.isMediaStreamsChatRenegotiation && window._lastFullConfig) {
                            console.log("[BxC] Sparse renegotiation detected, merging with cached config");
                            msg.configuration = Object.assign({}, window._lastFullConfig, msg.configuration);
                            data = JSON.stringify(msg);
                        } else {
                            // Cache the full config for future use
                            window._lastFullConfig = msg.configuration;
                        }
                    }
                } catch (e) {
                    // Not JSON or parse error, ignore
                }
            }
            return originalSend.apply(this, arguments);
        };

        return ws;
    };
    // Preserve prototype chain
    window.WebSocket.prototype = OriginalWebSocket.prototype;
    Object.assign(window.WebSocket, OriginalWebSocket);
}

class Patcher {
    static init() {
        const nativeBind = Function.prototype.bind;
        Function.prototype.bind = function() {
            if (this.name.length <= 2 && arguments.length === 2 && arguments[0] === null && typeof arguments[1] === "function") {
                const orgFunc = this;
                const newFunc = (a, item) => {
                    Patcher.checkChunks(item);
                    orgFunc(a, item);
                };
                Function.prototype.bind = nativeBind;
                BxLogger.info("Patcher", "Webpack hook established");
                return nativeBind.apply(newFunc, arguments);
            }
            return nativeBind.apply(this, arguments);
        };
    }

    static checkChunks(item) {
        const chunkData = item[1];
        if (!chunkData) return;

        for (const chunkId in chunkData) {
            let funcStr = chunkData[chunkId].toString();
            let modified = false;

            if (funcStr.includes("=window.__PRELOADED_STATE__;")) {
                funcStr = funcStr.replace("=window.__PRELOADED_STATE__;", "=window.BX_EXPOSED.modifyPreloadedState(window.__PRELOADED_STATE__);");
                modified = true;
            }

            if (funcStr.includes("async cloudConnect")) {
                const searchIdx = funcStr.indexOf("async cloudConnect");
                const bracketIndex = funcStr.indexOf("{", searchIdx);
                const paramsMatch = funcStr.substring(searchIdx, bracketIndex).match(/\(([^)]+)\)/);
                if (paramsMatch) {
                    const titleInfoVar = paramsMatch[1].split(",")[0].trim();
                    const injection = `\n${titleInfoVar} = window.BX_EXPOSED.modifyTitleInfo(${titleInfoVar});\n`;
                    funcStr = funcStr.substring(0, bracketIndex + 1) + injection + funcStr.substring(bracketIndex + 1);
                    modified = true;
                }
            }

            // 3. Disable Pause on Window Blur
            if (funcStr.includes("},this.onFocusChanged=")) {
                const searchIdx = funcStr.indexOf("},this.onFocusChanged=");
                const bracketIndex = funcStr.indexOf("=>{", searchIdx) + 3;
                // Find variable name before index
                let start = searchIdx - 1;
                while (funcStr[start] && (/[a-zA-Z0-9_$]/.test(funcStr[start]))) start--;
                const varName = funcStr.substring(start + 1, searchIdx);
                if (varName) {
                    funcStr = funcStr.substring(0, bracketIndex) + `try { ${varName} = "focus"; } catch (e) {}` + funcStr.substring(bracketIndex);
                    modified = true;
                }
            }

            if (modified) {
                try {
                    chunkData[chunkId] = eval("(function " + funcStr.replace(/^\d+/, "") + ")");
                    BxLogger.info("Patcher", `Successfully patched chunk: ${chunkId}`);
                } catch (e) {
                    BxLogger.error("Patcher", "Failed to eval patched chunk", e);
                }
            }
        }
    }
}

function patchSdpBitrate(sdp, videoBitrate) {
    if (!videoBitrate) return sdp;

    let lines = sdp.split('\r\n');
    let newLines = [];
    let inVideoSection = false;

    for (let line of lines) {
        newLines.push(line);
        if (line.startsWith('m=video')) {
            inVideoSection = true;
        } else if (line.startsWith('m=')) {
            inVideoSection = false;
        }

        if (inVideoSection && line.startsWith('c=IN')) {
            // Inject b=AS line after connection info in video section
            newLines.push('b=AS:' + videoBitrate);
        }
    }
    return newLines.join('\r\n');
}

function patchSdpFps(sdp, fps) {
    let lines = sdp.split('\r\n');
    for (let i = 0; i < lines.length; i++) {
        if (lines[i].startsWith('a=fmtp:')) {
            if (!lines[i].includes('max-fr=')) {
                lines[i] += ';max-fr=' + fps;
            } else {
                lines[i] = lines[i].replace(/max-fr=\d+/, 'max-fr=' + fps);
            }
        }
    }
    return lines.join('\r\n');
}

function patchRtcPeerConnection() {
    let OrgRTCPeerConnection = window.RTCPeerConnection;
window.handleIceCandidate = function(sdpMid, sdpMLineIndex, candidateStr) {
    if (!window.peer) return;

    // If candidateStr is null/empty, WebRTC treats it as the end of candidates
    let candidateObj = candidateStr ? { sdpMid: sdpMid, sdpMLineIndex: sdpMLineIndex, candidate: candidateStr } : null;

    let event = new Event('icecandidate');
    event.candidate = candidateObj;

    if (window.peer.onicecandidate) window.peer.onicecandidate(event);
    if (window.peer._listeners && window.peer._listeners['icecandidate']) {
        window.peer._listeners['icecandidate'].forEach(l => l(event));
    }
};
    // Base64 Helpers
    window.bxToBase64 = function(buffer) {
        let binary = '';
        let bytes = new Uint8Array(buffer);
        for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
        return window.btoa(binary);
    };
    window.bxToArrayBuffer = function(base64) {
        let binary = window.atob(base64);
        let bytes = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        return bytes.buffer;
    };


    window.simulateOnTrack = function(kind, id) {
        console.log("[BxC] Simulating ontrack for " + kind);

        let stream;
        let track;

        if (kind === 'video') {
            const canvas = document.createElement('canvas');
            canvas.width = 1920;
            canvas.height = 1080;
            const ctx = canvas.getContext('2d');
            ctx.fillStyle = 'black';
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            ctx.fillStyle = 'white';
            ctx.font = '30px Arial';
            ctx.fillText('Native Bridge Active', 50, 50);

            stream = canvas.captureStream(60);
            track = stream.getVideoTracks()[0];
        } else {

                const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                const destination = audioCtx.createMediaStreamDestination();
                stream = destination.stream;
                track = stream.getAudioTracks()[0];
            }

        // Construct a real-like Track event
        let event = new Event('track');
        event.track = track;
        event.streams = [stream];
        event.transceiver = { receiver: { track: track } };

        if (window.peer) {
            if (window.peer.ontrack) window.peer.ontrack(event);
            if (window.peer._listeners && window.peer._listeners['track']) {
                window.peer._listeners['track'].forEach(l => l(event));
            }
        }
    };

    window.filterSdp = function(sdp) {
        let lines = sdp.split('\r\n');
        let newLines = [];
        let skipExtmap = [
            'urn:ietf:params:rtp-hdrext:toffset',
                        'urn:ietf:params:rtp-hdrext:sdes:rtp-stream-id',
                        'urn:ietf:params:rtp-hdrext:sdes:repaired-rtp-stream-id',
                        'http://www.webrtc.org/experiments/rtp-hdrext/video-layers-allocation00',
            'http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01'
        ];

        for (let line of lines) {
            let skip = false;
            if (line.startsWith('a=extmap:')) {
                 for (let ext of skipExtmap) {
                                      if (line.includes(ext)) {
                                          skip = true;
                                          break;
                                      }
                                  }
            }

            if (!skip) newLines.push(line);
        }
        return newLines.join('\r\n');
    };

    window.RTCPeerConnection = function(config) {
        console.log("[BxC] RTCPeerConnection constructor called", JSON.stringify(config));

        if (window.AndroidBridge && config) {
            window.AndroidBridge.onPeerConnectionConfig(JSON.stringify(config));
        }

        const pc = {
            config: config,
            localDescription: null,
            remoteDescription: null,
            connectionState: 'new',
            iceConnectionState: 'new',
            signalingState: 'stable',
            _listeners: {},
            _transceivers: [],

            createDataChannel: function(label, options) {
                console.log("[BxC] Proxy createDataChannel: " + label + " options: " + JSON.stringify(options));
                if (window.AndroidBridge) {
                    window.AndroidBridge.onDataChannelCreate(label);
                }

                const dc = {
                    label: label,
                    id: options ? options.id : null,
                    ordered: options ? options.ordered : true,
                    protocol: options ? (options.protocol || "") : "",
                    readyState: 'connecting',
                    binaryType: 'arraybuffer',
                    onmessage: null,
                    onopen: null,
                    onclose: null,

                   send: function(data) {
                                           if(label=='input'||label=='unreliableinput'||label=='reliableinput')return;
                                           if (window.AndroidBridge) {
                                               if(label=='message'&&!data.includes('Handshake')){return;}
                                               let base64;
                                               let isBinary = typeof data !== 'string';
                                               if (isBinary) {

                                                       window.AndroidBridge.onDataChannelSend(label, new Uint8Array(data), null, true);
                                                       return;

                                               } else {
                                               if(label=='control'&&data.includes('resolutionAlias')){
                                               let overridesData=JSON.parse(data);
                                               overridesData.resolutionAlias=window.BX_TARGET_RES;
                                               data=JSON.stringify(overridesData);
                                               }
                                               console.log('sending data from channel'+label+': '+data);
                                               base64 = window.btoa(unescape(encodeURIComponent(data)));
                                               window.AndroidBridge.onDataChannelSend(label, null, base64, false);

                                           }}
                                       },
                    close: function() {
                        console.log("[BxC] Proxy DataChannel close: " + label);
                    },
                    addEventListener: function(type, listener) {
                        if (!this._listeners) this._listeners = {};
                        if (!this._listeners[type]) this._listeners[type] = [];
                        this._listeners[type].push(listener);
                    },
                    removeEventListener: function(type, listener) {
                        if (this._listeners && this._listeners[type]) {
                            this._listeners[type] = this._listeners[type].filter(l => l !== listener);
                        }
                    },
                    dispatchEvent: function(event) {
                        if (event.type === 'message') {
                            if (this.onmessage) this.onmessage(event);
                            if (this._listeners && this._listeners['message']) {
                                this._listeners['message'].forEach(l => l(event));
                            }
                        }
                    }
                };

                if (!window.proxyDataChannels) window.proxyDataChannels = {};
                window.proxyDataChannels[label] = dc;

                return dc;
            },

            addTransceiver: function(trackOrKind, init) {
                console.log("[BxC] Proxy addTransceiver: " + trackOrKind);
                if (window.AndroidBridge) {
                    window.AndroidBridge.onAddTransceiver(trackOrKind, init ? (init.direction || "recvonly") : "recvonly");
                }
                const transceiver = {
                    mid: null,
                    direction: init ? (init.direction || 'sendrecv') : 'sendrecv',
                    sender: { track: (typeof trackOrKind !== 'string' ? trackOrKind : null) },
                    receiver: { track: { kind: (typeof trackOrKind === 'string' ? trackOrKind : trackOrKind.kind), stop: () => {} } },
                    stop: () => {},
                    setCodecPreferences: function(codecs) { console.log("[BxC] Proxy setCodecPreferences"); }
                };
                this._transceivers.push(transceiver);
                return transceiver;
            },

            addTrack: function(track, ...streams) {
                console.log("[BxC] Proxy addTrack: " + track.kind);
                return { track: track };
            },

            getTransceivers: function() { return this._transceivers || []; },
            getReceivers: function() { return (this._transceivers || []).map(t => t.receiver); },
            getSenders: function() { return (this._transceivers || []).map(t => t.sender); },

            setRemoteDescription: function(description) {
                console.log("[BxC] Proxy setRemoteDescription: " + description.type);
                this.remoteDescription = description;
                if (description && window.AndroidBridge) {
                    if (description.type === 'offer') {
                        window.AndroidBridge.onOfferReceived(description.sdp);
                    } else if (description.type === 'answer') {
                        window.AndroidBridge.onAnswerReceived(description.sdp);
                    }
                }
                return Promise.resolve();
            },

            createAnswer: function() {
                console.log("[BxC] Proxy createAnswer");
                return new Promise((resolve) => {
                    window.pendingAnswerResolver = (answer) => {
                        this.localDescription = answer;
                        resolve(answer);
                    };
                });
            },

            createOffer: function() {
                            console.log("[BxC] Proxy createOffer requested");
                            return new Promise((resolve) => {
                                window.pendingOfferResolver = (offer) => {
                                    const bitrateMbps = getTargetBitrate();
                                    if (bitrateMbps > 0) {
                                        try {
                                            // Convert Mbps to Kbps for SDP (b=AS line)
                                            const bitrateKbps = bitrateMbps * 1000;
                                            offer.sdp = patchSdpBitrate(offer.sdp, bitrateKbps);
                                            BxLogger.info("Patcher", "Applied bitrate patch:", bitrateMbps + " Mbps");
                                        } catch (e) {
                                            BxLogger.error("Patcher", "Bitrate patch failed", e);
                                        }
                                    }

                                    offer.sdp = patchSdpFps(offer.sdp, 60);
                                    this.localDescription = offer;
                                    console.log("[BxC] Sending offer to server, length: " + offer.sdp.length);
                                    resolve(offer);
                                };
                                if (window.AndroidBridge) {
                                    window.AndroidBridge.onOfferRequested();
                                }
                            });
                        },

           setLocalDescription: function(description) {
               console.log("[BxC] Proxy setLocalDescription: " + (description ? description.type : "null"));
               this.localDescription = description;

               // 1. Update the internal state
               this.signalingState = description.type === 'offer' ? 'have-local-offer' : 'stable';

               // 2. Fire the state change event
               let event = new Event('signalingstatechange');
               if (this.onsignalingstatechange) this.onsignalingstatechange(event);
               if (this._listeners && this._listeners['signalingstatechange']) {
                   this._listeners['signalingstatechange'].forEach(l => l(event));
               }

               return Promise.resolve();
           },

            setConfiguration: function(config) {
                console.log("[BxC] Proxy setConfiguration", JSON.stringify(config));
                this.config = config;
                if (window.AndroidBridge && config) {
                    window.AndroidBridge.onPeerConnectionConfig(JSON.stringify(config));
                }
            },

            addIceCandidate: function(candidate) {
                console.log("[BxC] Proxy addIceCandidate");
                if (candidate && window.AndroidBridge) {
                    window.AndroidBridge.onIceCandidateReceived(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate || candidate.sdp || "");
                }
                return Promise.resolve();
            },

            addEventListener: function(type, listener) {
                if (!this._listeners[type]) this._listeners[type] = [];
                this._listeners[type].push(listener);
                this['on' + type] = listener;
            },

            getStats: function() {
                return Promise.resolve(new Map());
            },

            close: function() {
                console.log("[BxC] Proxy close");
                if (window.AndroidBridge) {
                    window.AndroidBridge.onPeerConnectionClose();
                }
            }
        };

        window.peer = pc;
        return pc;
    };

    window.RTCPeerConnection.prototype = OrgRTCPeerConnection.prototype;
    if (OrgRTCPeerConnection.generateCertificate) {
        window.RTCPeerConnection.generateCertificate = OrgRTCPeerConnection.generateCertificate.bind(OrgRTCPeerConnection);
    }
}


window.addEventListener("load", (e) => {
window.setTimeout(() => {
patchRtcPeerConnection();
if (document.body.classList.contains("legacyBackground"))
{ window.stop(), window.location.reload(!0);}
}, 3000);
InitBc();
});

function InitBc(){
window.worker=null;
window.d=null;
window.canvas=null;
window.firstBoost=false;
window.forKeyboard=false;

window.handleIceGatheringState = function(state) {
    console.log("[BxC] ICE Gathering State changed to: " + state);
    if (window.peer) {
        // Update the proxy state
        window.peer.iceGatheringState = state;

        // Dispatch the standard WebRTC event
        let event = new Event('icegatheringstatechange');
        if (window.peer.onicegatheringstatechange) window.peer.onicegatheringstatechange(event);
        if (window.peer._listeners && window.peer._listeners['icegatheringstatechange']) {
            window.peer._listeners['icegatheringstatechange'].forEach(l => l(event));
        }
    }
};

};

function main() {
    BxLogger.info("Init", "Loading lightweight xCloud bypass...");
    interceptHttpRequests();
    patchWebSocket();
    Patcher.init();
}

main();
