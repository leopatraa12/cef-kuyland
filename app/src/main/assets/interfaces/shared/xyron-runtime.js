(function () {
    "use strict";

    var INSTANCE_KEY = "__xyronRuntimeInstance";

    function safeParse(raw, fallback) {
        if (!raw || typeof raw !== "string") {
            return fallback;
        }
        try {
            return JSON.parse(raw);
        } catch (error) {
            return fallback;
        }
    }

    function callBridge(bridge, method) {
        if (!bridge || typeof bridge[method] !== "function") {
            return null;
        }
        var args = Array.prototype.slice.call(arguments, 2);
        try {
            return bridge[method].apply(bridge, args);
        } catch (error) {
            return null;
        }
    }

    function emit(name, detail) {
        window.dispatchEvent(new CustomEvent(name, { detail: detail || {} }));
    }

    function stripTrailingSlash(value) {
        return String(value || "").replace(/\/+$/, "");
    }

    function stripLeadingSlash(value) {
        return String(value || "").replace(/^\/+/, "");
    }

    function normalizeText(value) {
        return String(value || "")
            .toLowerCase()
            .normalize("NFD")
            .replace(/[\u0300-\u036f]/g, "")
            .replace(/\s+/g, " ")
            .trim();
    }

    function extractHost(address) {
        var raw = String(address || "").trim();
        if (!raw) {
            return "";
        }
        if (raw.indexOf("://") !== -1) {
            try {
                return new URL(raw).hostname || "";
            } catch (error) {
                return "";
            }
        }
        if (raw.indexOf(":") !== -1 && raw.indexOf("]") === -1) {
            return raw.split(":")[0];
        }
        return raw;
    }

    function applyCssVars(vars) {
        if (!vars || typeof vars !== "object") {
            return;
        }
        Object.keys(vars).forEach(function (key) {
            document.documentElement.style.setProperty(key, vars[key]);
        });
    }

    function computePreviewWsUrl(config) {
        if (config.live && config.live.previewUrl) {
            return config.live.previewUrl;
        }
        if (window.location.protocol.indexOf("http") === 0 && window.location.hostname) {
            return "ws://" + window.location.hostname + ":" + config.live.port + config.live.path;
        }
        return "ws://127.0.0.1:" + config.live.port + config.live.path;
    }

    function buildConfig(options, bridge) {
        var nativeConfig = safeParse(callBridge(bridge, "getRuntimeConfig"), {}) || {};
        var liveConfig = nativeConfig.liveReload || {};
        var cdnConfig = nativeConfig.cdn || {};
        var localLiveOverride = "";
        var localCdnOverride = "";

        try {
            localLiveOverride = window.localStorage.getItem("xyron-live-ws") || "";
            localCdnOverride = window.localStorage.getItem("xyron-cdn-base") || "";
        } catch (error) {
            localLiveOverride = "";
            localCdnOverride = "";
        }

        var preview = options.preview;
        var host = extractHost(nativeConfig.serverAddress);
        var runtimeUrl = liveConfig.runtimeUrl || "";
        var performanceConfig = nativeConfig.performance || {};
        if (!runtimeUrl && host) {
            runtimeUrl = "ws://" + host + ":" + (liveConfig.port || 8766) + (liveConfig.path || "/live");
        }

        return {
            page: options.page || "overlay",
            preview: !!preview,
            playerName: nativeConfig.playerName || "",
            serverName: nativeConfig.serverName || "",
            serverAddress: nativeConfig.serverAddress || "",
            live: {
                enabled: liveConfig.enabled !== false,
                port: liveConfig.port || 8766,
                path: liveConfig.path || "/live",
                reconnectMs: liveConfig.reconnectMs || 1800,
                previewUrl: localLiveOverride || liveConfig.previewUrl || "",
                runtimeUrl: localLiveOverride || runtimeUrl || ""
            },
            cdn: {
                enabled: cdnConfig.enabled !== false,
                baseUrl: stripTrailingSlash(localCdnOverride || cdnConfig.baseUrl || "https://raw.githubusercontent.com/qbcore-framework/qb-inventory/main/html")
            },
            performance: {
                lowRam: !!performanceConfig.lowRam
            }
        };
    }

    function boot(options) {
        if (window[INSTANCE_KEY]) {
            return window[INSTANCE_KEY];
        }

        var bridge = options.bridge || window.Android || null;
        var config = buildConfig({
            page: options.page || "overlay",
            preview: options.preview !== undefined ? options.preview : !bridge
        }, bridge);

        var state = {
            ws: null,
            wsConnected: false,
            reconnectTimer: null,
            voiceMode: "idle",
            lastAnnouncement: "",
            lastTranscript: "",
            lastIntent: null,
            liveUrls: [],
            liveUrlIndex: 0,
            activeLiveUrl: ""
        };

        function setLiveStatus(connected) {
            state.wsConnected = !!connected;
            document.documentElement.dataset.xyronLive = connected ? "connected" : "offline";
            emit("xyron:live-status", {
                connected: state.wsConnected,
                page: config.page
            });
        }

        function buildLiveUrls() {
            var seen = {};
            var values = [];

            function push(url) {
                var normalized = String(url || "").trim();
                if (!normalized || seen[normalized]) {
                    return;
                }
                seen[normalized] = true;
                values.push(normalized);
            }

            if (!config.live.enabled) {
                return values;
            }

            if (config.preview) {
                push(config.live.previewUrl);
                push(computePreviewWsUrl(config));
                return values;
            }

            push(config.live.runtimeUrl);
            push(config.live.previewUrl);
            push(computePreviewWsUrl(config));
            return values;
        }

        function resolveLiveUrl() {
            if (!state.liveUrls.length) {
                state.liveUrls = buildLiveUrls();
            }
            if (!state.liveUrls.length) {
                return "";
            }
            var index = Math.min(state.liveUrlIndex, state.liveUrls.length - 1);
            return state.liveUrls[index] || "";
        }

        function receiveLiveMessage(message) {
            var payload = typeof message === "string" ? safeParse(message, null) : message;
            if (!payload || typeof payload !== "object") {
                return;
            }

            var target = payload.target;
            if (target && target !== "*" && target !== config.page && !(Array.isArray(target) && target.indexOf(config.page) !== -1)) {
                return;
            }

            if (payload.type === "reload") {
                console.log("[XyronRuntime] reload", config.page);
                emit("xyron:reload-request", payload);
                window.location.reload();
                return;
            }

            if (payload.type === "theme" || payload.type === "css-vars") {
                console.log("[XyronRuntime] theme", config.page);
                applyCssVars(payload.vars || payload.payload || {});
            }

            if (payload.type === "announcement") {
                state.lastAnnouncement = payload.message || (payload.payload && payload.payload.message) || "";
                console.log("[XyronRuntime] announcement", config.page, state.lastAnnouncement);
                emit("xyron:announcement", {
                    message: state.lastAnnouncement,
                    page: config.page
                });
            }

            if (payload.type === "blur") {
                runtime.requestBlur(!!((payload.payload && payload.payload.enabled) || payload.enabled));
            }

            emit("xyron:live", payload);
            emit("xyron:live:" + payload.type, payload);
        }

        function scheduleReconnect(advanceUrl) {
            if (state.reconnectTimer || !config.live.enabled) {
                return;
            }
            if (advanceUrl && state.liveUrls.length > 1) {
                state.liveUrlIndex = (state.liveUrlIndex + 1) % state.liveUrls.length;
            }
            state.reconnectTimer = window.setTimeout(function () {
                state.reconnectTimer = null;
                runtime.connectLiveSync();
            }, config.live.reconnectMs);
        }

        function connectLiveSync() {
            var liveUrl = resolveLiveUrl();
            if (!liveUrl || typeof WebSocket !== "function") {
                return false;
            }

            try {
                if (state.ws) {
                    state.ws.close();
                }
                state.activeLiveUrl = liveUrl;
                console.log("[XyronRuntime] live-connect", config.page, liveUrl);
                state.ws = new WebSocket(liveUrl);
            } catch (error) {
                console.warn("[XyronRuntime] live-connect-failed", config.page, liveUrl);
                scheduleReconnect(true);
                return false;
            }

            state.ws.addEventListener("open", function () {
                console.log("[XyronRuntime] live-open", config.page, state.activeLiveUrl);
                setLiveStatus(true);
            });

            state.ws.addEventListener("message", function (event) {
                receiveLiveMessage(event.data);
            });

            state.ws.addEventListener("close", function () {
                console.warn("[XyronRuntime] live-close", config.page, state.activeLiveUrl);
                setLiveStatus(false);
                scheduleReconnect(true);
            });

            state.ws.addEventListener("error", function () {
                console.warn("[XyronRuntime] live-error", config.page, state.activeLiveUrl);
                setLiveStatus(false);
            });

            return true;
        }

        function resolveAsset(remote, fallback) {
            if (!config.cdn.enabled) {
                return fallback || remote || "";
            }
            var source = remote || fallback || "";
            if (!source) {
                return "";
            }
            if (/^(data:|https?:|blob:)/i.test(source)) {
                return source;
            }
            return config.cdn.baseUrl + "/" + stripLeadingSlash(source);
        }

        function preloadImages(items) {
            var list = Array.isArray(items) ? items : [];
            return Promise.all(list.map(function (entry) {
                var remote = typeof entry === "string" ? entry : entry.remote;
                var fallback = typeof entry === "string" ? entry : entry.fallback;
                return new Promise(function (resolve) {
                    var image = new Image();
                    image.onload = function () { resolve(true); };
                    image.onerror = function () { resolve(false); };
                    image.src = resolveAsset(remote, fallback);
                });
            }));
        }

        function requestBlur(enabled) {
            document.documentElement.dataset.xyronBlur = enabled ? "on" : "off";
            callBridge(bridge, "setOverlayBlurEnabled", !!enabled);
        }

        function normalizeVoiceTranscript(transcript) {
            var raw = String(transcript || "").trim();
            var normalized = normalizeText(raw);

            if (!normalized) {
                return { kind: "empty", raw: raw };
            }
            if (/ligar motor|desligar motor|motor/.test(normalized)) {
                return { kind: "command", value: "/motor", raw: raw };
            }
            if (/porta mala|porta malas|porta-malas|porta malas/.test(normalized)) {
                return { kind: "command", value: "/portamalas", raw: raw };
            }
            if (/abrir inventario|inventario/.test(normalized)) {
                return { kind: "inventory", raw: raw };
            }
            if (/abrir celular|celular/.test(normalized)) {
                return { kind: "phone", raw: raw };
            }
            if (/whats|zap/.test(normalized)) {
                return { kind: "screen", value: "whatsapp-list", raw: raw };
            }
            if (/instagram|insta/.test(normalized)) {
                return { kind: "screen", value: "instagram-feed", raw: raw };
            }
            if (/nubank|banco|bank|pix/.test(normalized)) {
                return { kind: "screen", value: "bank-home", raw: raw };
            }
            if (/mapa|gps|rota/.test(normalized)) {
                return { kind: "screen", value: "maps-home", raw: raw };
            }
            if (/radio|som|musica|festa/.test(normalized)) {
                return { kind: "screen", value: "music-home", raw: raw };
            }
            return { kind: "text", raw: raw };
        }

        function handleVoiceTranscript(transcript) {
            var intent = normalizeVoiceTranscript(transcript);
            state.lastTranscript = transcript || "";
            state.lastIntent = intent;
            console.log("[XyronRuntime] voice", config.page, transcript || "", intent ? intent.kind : "none");

            if (intent.kind === "command") {
                callBridge(bridge, "runCommand", intent.value);
            } else if (intent.kind === "inventory") {
                callBridge(bridge, "openInventory");
            } else if (intent.kind === "phone") {
                callBridge(bridge, "openPhone");
            }

            emit("xyron:voice-result", {
                transcript: transcript || "",
                intent: intent,
                page: config.page
            });
            return intent;
        }

        function receiveNativeVoice(raw) {
            var payload = typeof raw === "string" ? safeParse(raw, null) : raw;
            state.voiceMode = "idle";
            console.log("[XyronRuntime] voice-native", config.page, payload && payload.status ? payload.status : "unknown");

            if (!payload || payload.status === "cancelled" || payload.status === "error") {
                emit("xyron:voice-error", payload || { status: "error" });
                return null;
            }

            return handleVoiceTranscript(payload.transcript || "");
        }

        function receiveNativeEvent(raw) {
            var payload = typeof raw === "string" ? safeParse(raw, null) : raw;
            if (!payload || typeof payload !== "object") {
                return;
            }
            if (payload.type === "voice-result") {
                receiveNativeVoice(payload.payload || payload);
                return;
            }
            if (payload.type === "live") {
                receiveLiveMessage(payload.payload || payload.message || {});
                return;
            }
            emit("xyron:native", payload);
            emit("xyron:native:" + payload.type, payload);
        }

        function startBrowserVoiceFallback() {
            var SpeechCtor = window.SpeechRecognition || window.webkitSpeechRecognition || null;
            if (!SpeechCtor) {
                emit("xyron:voice-error", { status: "unsupported" });
                return false;
            }

            var recognizer = new SpeechCtor();
            recognizer.lang = "pt-BR";
            recognizer.interimResults = false;
            recognizer.maxAlternatives = 1;
            state.voiceMode = "browser";

            recognizer.addEventListener("result", function (event) {
                var transcript = "";
                if (event.results && event.results[0] && event.results[0][0]) {
                    transcript = event.results[0][0].transcript || "";
                }
                handleVoiceTranscript(transcript);
            });

            recognizer.addEventListener("end", function () {
                state.voiceMode = "idle";
            });

            recognizer.addEventListener("error", function (event) {
                state.voiceMode = "idle";
                emit("xyron:voice-error", {
                    status: "error",
                    error: event.error || "browser"
                });
            });

            recognizer.start();
            return true;
        }

        function startVoiceCommand(overlayName) {
            state.voiceMode = "native";
            var started = callBridge(bridge, "startVoiceCommand", overlayName || config.page);
            if (started) {
                console.log("[XyronRuntime] voice-start", overlayName || config.page);
                emit("xyron:voice-start", {
                    mode: "native",
                    page: config.page
                });
                return true;
            }
            return startBrowserVoiceFallback();
        }

        var runtime = {
            bridge: bridge,
            config: config,
            state: state,
            connectLiveSync: connectLiveSync,
            receiveLiveMessage: receiveLiveMessage,
            receiveNativeEvent: receiveNativeEvent,
            receiveNativeVoice: receiveNativeVoice,
            resolveAsset: resolveAsset,
            preloadImages: preloadImages,
            requestBlur: requestBlur,
            startVoiceCommand: startVoiceCommand,
            normalizeVoiceTranscript: normalizeVoiceTranscript,
            handleVoiceTranscript: handleVoiceTranscript
        };

        window.XyronRuntimeReceiveNative = function (raw) {
            runtime.receiveNativeEvent(raw);
        };
        window.XyronRuntimeReceiveVoice = function (raw) {
            runtime.receiveNativeVoice(raw);
        };

        connectLiveSync();
        window[INSTANCE_KEY] = runtime;
        return runtime;
    }

    window.XyronRuntime = {
        boot: boot
    };
})();
