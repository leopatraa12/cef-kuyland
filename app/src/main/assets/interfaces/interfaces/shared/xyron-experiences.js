(function () {
    "use strict";

    function clamp(value, min, max) {
        return Math.max(min, Math.min(max, value));
    }

    function lerp(a, b, t) {
        return a + ((b - a) * t);
    }

    function sampleRoute(route, progress) {
        if (!route.length) {
            return { x: 0.5, y: 0.5 };
        }
        if (route.length === 1) {
            return route[0];
        }
        var scaled = clamp(progress, 0, 1) * (route.length - 1);
        var index = Math.floor(scaled);
        var nextIndex = Math.min(route.length - 1, index + 1);
        var localT = scaled - index;
        return {
            x: lerp(route[index].x, route[nextIndex].x, localT),
            y: lerp(route[index].y, route[nextIndex].y, localT)
        };
    }

    function createRouteMap(container, options) {
        var root = container;
        var canvas = document.createElement("canvas");
        var ctx = canvas.getContext("2d");
        var runtime = options.runtime || null;
        var dpr = Math.max(1, window.devicePixelRatio || 1);
        var state = {
            zoom: options.zoom || 1,
            progress: options.progress || 0.18,
            heading: options.heading || 42,
            auto: options.auto !== false,
            center: { x: 0.5, y: 0.5 },
            route: options.route || [
                { x: 0.12, y: 0.68 },
                { x: 0.28, y: 0.56 },
                { x: 0.39, y: 0.54 },
                { x: 0.52, y: 0.47 },
                { x: 0.65, y: 0.34 },
                { x: 0.79, y: 0.18 }
            ],
            markers: options.markers || [
                { x: 0.2, y: 0.64, label: "Loja", color: "#4f8cff" },
                { x: 0.56, y: 0.42, label: "Nu", color: "#b26fff" },
                { x: 0.79, y: 0.18, label: "Evento", color: "#4ade80" }
            ]
        };
        var rafId = 0;
        var isDestroyed = false;
        var isPaused = false;

        root.innerHTML = "";
        root.appendChild(canvas);

        function resize() {
            var rect = root.getBoundingClientRect();
            canvas.width = Math.max(1, Math.round(rect.width * dpr));
            canvas.height = Math.max(1, Math.round(rect.height * dpr));
            canvas.style.width = rect.width + "px";
            canvas.style.height = rect.height + "px";
            draw();
        }

        function drawGrid(width, height) {
            ctx.strokeStyle = "rgba(255,255,255,0.05)";
            ctx.lineWidth = 1;
            for (var x = 0; x <= width; x += width / 6) {
                ctx.beginPath();
                ctx.moveTo(x, 0);
                ctx.lineTo(x, height);
                ctx.stroke();
            }
            for (var y = 0; y <= height; y += height / 6) {
                ctx.beginPath();
                ctx.moveTo(0, y);
                ctx.lineTo(width, y);
                ctx.stroke();
            }
        }

        function project(point, width, height) {
            var px = (point.x - 0.5) * state.zoom + 0.5;
            var py = (point.y - 0.5) * state.zoom + 0.5;
            return {
                x: px * width,
                y: py * height
            };
        }

        function drawRoute(width, height) {
            ctx.lineCap = "round";
            ctx.lineJoin = "round";

            ctx.beginPath();
            state.route.forEach(function (point, index) {
                var p = project(point, width, height);
                if (index === 0) {
                    ctx.moveTo(p.x, p.y);
                } else {
                    ctx.lineTo(p.x, p.y);
                }
            });
            ctx.strokeStyle = "rgba(17, 24, 39, 0.88)";
            ctx.lineWidth = 20;
            ctx.stroke();

            ctx.beginPath();
            state.route.forEach(function (point, index) {
                var p = project(point, width, height);
                if (index === 0) {
                    ctx.moveTo(p.x, p.y);
                } else {
                    ctx.lineTo(p.x, p.y);
                }
            });
            ctx.strokeStyle = "rgba(90, 132, 255, 0.95)";
            ctx.lineWidth = 7;
            ctx.shadowColor = "rgba(90, 132, 255, 0.45)";
            ctx.shadowBlur = 18;
            ctx.stroke();
            ctx.shadowBlur = 0;
        }

        function drawMarkers(width, height) {
            state.markers.forEach(function (marker) {
                var p = project(marker, width, height);
                ctx.fillStyle = marker.color || "#ffffff";
                ctx.beginPath();
                ctx.arc(p.x, p.y, 7, 0, Math.PI * 2);
                ctx.fill();
                ctx.fillStyle = "rgba(255,255,255,0.96)";
                ctx.font = "700 12px Arial";
                ctx.fillText(marker.label || "", p.x + 10, p.y + 4);
            });
        }

        function drawVehicle(width, height) {
            var point = sampleRoute(state.route, state.progress);
            var p = project(point, width, height);
            ctx.save();
            ctx.translate(p.x, p.y);
            ctx.rotate((state.heading * Math.PI) / 180);
            ctx.fillStyle = "#ffffff";
            ctx.beginPath();
            ctx.moveTo(0, -12);
            ctx.lineTo(9, 10);
            ctx.lineTo(0, 5);
            ctx.lineTo(-9, 10);
            ctx.closePath();
            ctx.fill();
            ctx.restore();
        }

        function draw() {
            if (isDestroyed) {
                return;
            }
            var width = canvas.width;
            var height = canvas.height;
            ctx.clearRect(0, 0, width, height);

            var bg = ctx.createLinearGradient(0, 0, width, height);
            bg.addColorStop(0, "rgba(6,9,14,0.98)");
            bg.addColorStop(1, "rgba(2,3,5,0.98)");
            ctx.fillStyle = bg;
            ctx.fillRect(0, 0, width, height);

            drawGrid(width, height);
            drawRoute(width, height);
            drawMarkers(width, height);
            drawVehicle(width, height);
        }

        function tick() {
            if (isDestroyed) {
                return;
            }
            if (isPaused) {
                rafId = 0;
                return;
            }
            if (state.auto) {
                state.progress += 0.0018;
                if (state.progress > 1) {
                    state.progress = 0;
                }
                state.heading += 0.2;
            }
            draw();
            rafId = window.requestAnimationFrame(tick);
        }

        var liveHandler = function (event) {
            var payload = event.detail || {};
            var data = payload.payload || payload;
            if (!data || typeof data !== "object") {
                return;
            }
            if (Array.isArray(data.route)) {
                state.route = data.route;
            }
            if (Array.isArray(data.markers)) {
                state.markers = data.markers;
            }
            if (typeof data.progress === "number") {
                state.progress = data.progress;
            }
            if (typeof data.heading === "number") {
                state.heading = data.heading;
            }
            if (typeof data.zoom === "number") {
                state.zoom = data.zoom;
            }
            draw();
        };

        if (runtime) {
            window.addEventListener("xyron:live:map-state", liveHandler);
        }

        function resumeLoop() {
            if (isDestroyed) {
                return;
            }
            isPaused = false;
            if (!rafId) {
                tick();
            }
        }

        window.addEventListener("resize", resize);
        resize();
        resumeLoop();

        return {
            setZoom: function (value) {
                state.zoom = clamp(value, 0.75, 1.9);
                draw();
            },
            setProgress: function (value) {
                state.progress = clamp(value, 0, 1);
                draw();
            },
            setHeading: function (value) {
                state.heading = value;
                draw();
            },
            update: function (nextState) {
                nextState = nextState || {};
                if (Array.isArray(nextState.route)) {
                    state.route = nextState.route;
                }
                if (Array.isArray(nextState.markers)) {
                    state.markers = nextState.markers;
                }
                if (typeof nextState.zoom === "number") {
                    state.zoom = nextState.zoom;
                }
                if (typeof nextState.progress === "number") {
                    state.progress = nextState.progress;
                }
                if (typeof nextState.heading === "number") {
                    state.heading = nextState.heading;
                }
                draw();
            },
            pause: function () {
                isPaused = true;
                if (rafId) {
                    window.cancelAnimationFrame(rafId);
                    rafId = 0;
                }
            },
            resume: function () {
                resumeLoop();
            },
            destroy: function () {
                isDestroyed = true;
                window.cancelAnimationFrame(rafId);
                window.removeEventListener("resize", resize);
                window.removeEventListener("xyron:live:map-state", liveHandler);
            }
        };
    }

    function createImpulseResponse(audioContext, seconds, decay) {
        var length = Math.max(1, Math.floor(audioContext.sampleRate * seconds));
        var impulse = audioContext.createBuffer(2, length, audioContext.sampleRate);
        for (var channel = 0; channel < 2; channel += 1) {
            var data = impulse.getChannelData(channel);
            for (var i = 0; i < length; i += 1) {
                data[i] = (Math.random() * 2 - 1) * Math.pow(1 - (i / length), decay);
            }
        }
        return impulse;
    }

    function createSpatialStudio(options) {
        var context = null;
        var master = null;
        var panner = null;
        var analyser = null;
        var dryGain = null;
        var wetGain = null;
        var filter = null;
        var convolver = null;
        var oscillators = [];
        var playing = false;
        var currentRoom = "club";

        function ensureGraph() {
            if (context) {
                return;
            }
            context = new (window.AudioContext || window.webkitAudioContext)();
            master = context.createGain();
            dryGain = context.createGain();
            wetGain = context.createGain();
            filter = context.createBiquadFilter();
            panner = context.createPanner();
            analyser = context.createAnalyser();
            convolver = context.createConvolver();

            master.gain.value = 0.0;
            dryGain.gain.value = 0.82;
            wetGain.gain.value = 0.34;
            filter.type = "lowpass";
            filter.frequency.value = 1600;
            analyser.fftSize = 64;
            convolver.buffer = createImpulseResponse(context, 1.8, 2.4);

            panner.panningModel = "HRTF";
            panner.distanceModel = "inverse";
            panner.refDistance = 1;
            panner.maxDistance = 100;
            panner.rolloffFactor = 1.2;
            panner.positionX.value = 0;
            panner.positionY.value = 0;
            panner.positionZ.value = -1;

            filter.connect(panner);
            panner.connect(dryGain);
            panner.connect(convolver);
            convolver.connect(wetGain);
            dryGain.connect(master);
            wetGain.connect(master);
            master.connect(analyser);
            analyser.connect(context.destination);

            [
                { type: "triangle", frequency: 82.41, gain: 0.22 },
                { type: "sine", frequency: 164.82, gain: 0.11 },
                { type: "sawtooth", frequency: 329.64, gain: 0.05 }
            ].forEach(function (descriptor) {
                var oscillator = context.createOscillator();
                var gainNode = context.createGain();
                oscillator.type = descriptor.type;
                oscillator.frequency.value = descriptor.frequency;
                gainNode.gain.value = descriptor.gain;
                oscillator.connect(gainNode);
                gainNode.connect(filter);
                oscillator.start();
                oscillators.push({ oscillator: oscillator, gain: gainNode });
            });
        }

        ensureGraph();

        function setPlaying(nextPlaying) {
            if (context.state === "suspended") {
                context.resume();
            }
            playing = !!nextPlaying;
            master.gain.setTargetAtTime(playing ? 0.92 : 0.0, context.currentTime, 0.12);
            return playing;
        }

        return {
            resume: function () {
                if (context.state === "suspended") {
                    context.resume();
                }
            },
            setPlaying: function (nextPlaying) {
                return setPlaying(nextPlaying);
            },
            toggle: function (forceState) {
                if (typeof forceState === "boolean") {
                    return setPlaying(forceState);
                }
                return setPlaying(!playing);
            },
            setRoomPreset: function (preset) {
                var room = String(preset || "club").toLowerCase();
                currentRoom = room;
                if (room === "garage") {
                    filter.frequency.setTargetAtTime(1200, context.currentTime, 0.12);
                    wetGain.gain.setTargetAtTime(0.46, context.currentTime, 0.12);
                } else if (room === "open") {
                    filter.frequency.setTargetAtTime(2400, context.currentTime, 0.12);
                    wetGain.gain.setTargetAtTime(0.2, context.currentTime, 0.12);
                } else {
                    filter.frequency.setTargetAtTime(1800, context.currentTime, 0.12);
                    wetGain.gain.setTargetAtTime(0.34, context.currentTime, 0.12);
                }
            },
            getRoomName: function () {
                if (currentRoom === "garage") {
                    return "Garage";
                }
                if (currentRoom === "open") {
                    return "Open Air";
                }
                return "Club";
            },
            setSourcePosition: function (x, y, z) {
                panner.positionX.value = x;
                panner.positionY.value = y;
                panner.positionZ.value = z;
            },
            setListenerHeading: function (x, y, z) {
                if (!context.listener.forwardX) {
                    return;
                }
                context.listener.forwardX.value = x;
                context.listener.forwardY.value = y;
                context.listener.forwardZ.value = z;
            },
            getMeterLevel: function () {
                var bins = new Uint8Array(analyser.frequencyBinCount);
                analyser.getByteFrequencyData(bins);
                var sum = bins.reduce(function (acc, value) { return acc + value; }, 0);
                return sum / (bins.length * 255);
            },
            isPlaying: function () {
                return playing;
            },
            destroy: function () {
                oscillators.forEach(function (entry) {
                    try {
                        entry.oscillator.stop();
                    } catch (error) {
                        // noop
                    }
                    entry.oscillator.disconnect();
                    entry.gain.disconnect();
                });
                oscillators = [];
                if (context) {
                    context.close();
                }
            }
        };
    }

    window.XyronExperiences = {
        createRouteMap: createRouteMap,
        createSpatialStudio: createSpatialStudio
    };
})();
