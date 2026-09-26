/*
 * Resona store music player.
 *
 * - Tap to start: browsers only allow sound after a tap, so a full-screen
 *   prompt starts the music (and comes back if the browser blocks playback).
 * - Smooth playback: two music players crossfade into each other, and the
 *   music is lowered while a jingle plays. Uses the Web Audio mixer, because
 *   iPads ignore volume changes on plain audio elements.
 * - Reconnecting: the live connection retries with back-off, reconnects as
 *   soon as the internet is back, and re-syncs regularly. Staff see the status.
 * - Offline: this store's tracks and today's jingles are saved on the device
 *   (served by /player-sw.js), and jingles are played on the device's own
 *   clock too, so they still play if the connection drops.
 *
 * Expects window.RESONA_PLAYER = {storeId, companyName} and the SockJS + STOMP
 * scripts to be loaded first.
 */
(function () {
    'use strict';

    const config = window.RESONA_PLAYER;
    const AUDIO_CACHE = 'resona-audio-v1';
    const CROSSFADE_SECONDS = 5;
    const DUCK_LEVEL = 0.2;
    const RECONNECT_DELAYS_MS = [2000, 4000, 8000, 15000, 30000];
    const RESYNC_INTERVAL_MS = 10 * 60 * 1000;
    const JINGLE_CHECK_INTERVAL_MS = 5000;
    const JINGLE_LATE_LIMIT_MS = 2 * 60 * 1000;
    const MAX_OFFLINE_BYTES = 1.5 * 1024 * 1024 * 1024;

    // ---------- state ----------
    let playlist = [];
    let trackIndex = Math.floor(Math.random() * 1000);
    let wantsPlayback = false;
    let crossfading = false;
    const cachedUrls = new Set();
    const slots = new Map();          // slotId -> {id, time, url}
    const playedSlotIds = new Set();
    const jingleQueue = [];
    let jinglePlaying = false;

    let stompClient = null;
    let connected = false;
    let reconnectAttempt = 0;
    let reconnectTimer = null;
    let heartbeatTimer = null;
    let cachingInProgress = false;
    let cacheProgress = null;          // {done, total}

    // ---------- DOM ----------
    const orbBtn = document.querySelector('.orb-play');
    const overlay = document.getElementById('tapToStart');
    const overlayText = document.getElementById('tapToStartText');
    const statusEl = document.getElementById('playerStatus');

    // ---------- audio graph ----------
    const musicEls = [createAudioElement(), createAudioElement()];
    const jingleEl = createAudioElement();
    let activeEl = 0;
    let audioCtx = null;
    let musicGains = null;
    let duckGain = null;

    function createAudioElement() {
        const el = new Audio();
        el.crossOrigin = 'anonymous';
        el.preload = 'auto';
        return el;
    }

    function setupAudioGraph() {
        if (audioCtx) return;
        const Ctx = window.AudioContext || window.webkitAudioContext;
        if (!Ctx) return;
        try {
            audioCtx = new Ctx();
            duckGain = audioCtx.createGain();
            duckGain.connect(audioCtx.destination);
            musicGains = musicEls.map((el) => {
                const gain = audioCtx.createGain();
                gain.gain.value = 0;
                audioCtx.createMediaElementSource(el).connect(gain);
                gain.connect(duckGain);
                return gain;
            });
            audioCtx.createMediaElementSource(jingleEl).connect(audioCtx.destination);
        } catch (e) {
            console.warn('Web Audio not available, using plain volume', e);
            audioCtx = null;
        }
    }

    function rampTo(index, value, seconds) {
        if (audioCtx) {
            const gain = musicGains[index].gain;
            const now = audioCtx.currentTime;
            gain.cancelScheduledValues(now);
            gain.setValueAtTime(gain.value, now);
            gain.linearRampToValueAtTime(value, now + seconds);
        } else {
            fadeVolume(musicEls[index], value, seconds);
        }
    }

    function duck(value, seconds) {
        if (audioCtx) {
            const now = audioCtx.currentTime;
            duckGain.gain.cancelScheduledValues(now);
            duckGain.gain.setValueAtTime(duckGain.gain.value, now);
            duckGain.gain.linearRampToValueAtTime(value, now + seconds);
        } else {
            musicEls.forEach((el) => fadeVolume(el, value, seconds));
        }
    }

    function fadeVolume(el, target, seconds) {
        const start = el.volume;
        const steps = Math.max(Math.round(seconds * 20), 1);
        let step = 0;
        const timer = setInterval(() => {
            step++;
            el.volume = Math.min(Math.max(start + (target - start) * (step / steps), 0), 1);
            if (step >= steps) clearInterval(timer);
        }, (seconds * 1000) / steps);
    }

    // Same file, same key: the cache, the audio elements and the server may spell a URL differently.
    function norm(url) {
        try {
            return new URL(url, location.href).href;
        } catch (e) {
            return url;
        }
    }

    // ---------- playlist ----------
    function isOffline() {
        return !navigator.onLine || (!connected && reconnectAttempt >= 2);
    }

    function nextTrackUrl() {
        if (playlist.length === 0) return null;
        // Offline: only tracks saved on this device can play.
        const candidates = isOffline() ? playlist.filter((url) => cachedUrls.has(url)) : playlist;
        if (candidates.length === 0) return null;
        trackIndex = (trackIndex + 1) % candidates.length;
        return candidates[trackIndex];
    }

    function startTrack(elIndex, url, fadeSeconds) {
        const el = musicEls[elIndex];
        el.src = url;
        el.currentTime = 0;
        rampTo(elIndex, 0, 0.01);
        return el.play().then(() => {
            rampTo(elIndex, 1, fadeSeconds);
        });
    }

    function playNext(fadeSeconds) {
        const url = nextTrackUrl();
        if (!url) {
            updateStatus();
            // Nothing playable right now (offline, nothing saved): try again soon.
            setTimeout(() => { if (wantsPlayback && musicEls.every((el) => el.paused)) playNext(1); }, 15000);
            return;
        }

        const previous = activeEl;
        const next = 1 - activeEl;
        activeEl = next;
        crossfading = true;

        startTrack(next, url, fadeSeconds)
            .then(() => {
                if (!musicEls[previous].paused) {
                    rampTo(previous, 0, fadeSeconds);
                    setTimeout(() => musicEls[previous].pause(), fadeSeconds * 1000 + 200);
                }
            })
            .catch((err) => handlePlayError(err))
            .finally(() => setTimeout(() => { crossfading = false; }, fadeSeconds * 1000));
    }

    musicEls.forEach((el, index) => {
        el.addEventListener('timeupdate', () => {
            if (index !== activeEl || crossfading || !wantsPlayback) return;
            if (el.duration && el.duration - el.currentTime <= CROSSFADE_SECONDS) {
                playNext(CROSSFADE_SECONDS);
            }
        });
        el.addEventListener('ended', () => {
            if (index === activeEl && wantsPlayback && !crossfading) playNext(1);
        });
        el.addEventListener('error', () => {
            if (index === activeEl && wantsPlayback) setTimeout(() => playNext(1), 2000);
        });
        // A track that isn't saved stops loading when the internet drops: switch to a saved one.
        el.addEventListener('waiting', () => {
            if (index !== activeEl || !wantsPlayback) return;
            setTimeout(() => {
                if (el.readyState < 3 && isOffline() && !cachedUrls.has(norm(el.currentSrc))) playNext(1);
            }, 8000);
        });
    });

    function handlePlayError(err) {
        if (err && err.name === 'NotAllowedError') {
            showOverlay('Tap to start the music');
        } else {
            console.warn('Playback failed', err);
            setTimeout(() => { if (wantsPlayback) playNext(1); }, 3000);
        }
    }

    // ---------- start / stop ----------
    function startPlayback() {
        wantsPlayback = true;
        hideOverlay();
        setupAudioGraph();
        if (audioCtx && audioCtx.state === 'suspended') audioCtx.resume();
        orbBtn.classList.add('is-playing');
        startHeartbeat();

        if (musicEls[activeEl].src && musicEls[activeEl].paused) {
            musicEls[activeEl].play().then(() => rampTo(activeEl, 1, 1)).catch(handlePlayError);
        } else if (musicEls.every((el) => el.paused)) {
            playNext(1.5);
        }
        updateStatus();
    }

    function stopPlayback() {
        wantsPlayback = false;
        musicEls.forEach((el, i) => {
            rampTo(i, 0, 0.5);
            setTimeout(() => el.pause(), 600);
        });
        orbBtn.classList.remove('is-playing');
        stopHeartbeat();
        updateStatus();
    }

    orbBtn.addEventListener('click', () => (wantsPlayback ? stopPlayback() : startPlayback()));
    overlay.addEventListener('click', startPlayback);

    function showOverlay(text) {
        overlayText.textContent = text;
        overlay.classList.add('visible');
    }

    function hideOverlay() {
        overlay.classList.remove('visible');
    }

    // ---------- jingles ----------
    function scheduleSlots(dailySlots) {
        slots.clear();
        (dailySlots || []).forEach((slot) => {
            const time = parseInstant(slot.playTime);
            if (time && slot.fileUrl) slots.set(slot.id, {id: slot.id, time, url: norm(slot.fileUrl)});
        });
    }

    function parseInstant(value) {
        if (value == null) return null;
        if (typeof value === 'number') return value < 1e12 ? value * 1000 : value;
        const ms = Date.parse(value);
        return Number.isNaN(ms) ? null : ms;
    }

    // The device's own clock plays jingles too, so they aren't missed while offline.
    setInterval(() => {
        const now = Date.now();
        slots.forEach((slot) => {
            if (playedSlotIds.has(slot.id)) return;
            if (now >= slot.time && now - slot.time < JINGLE_LATE_LIMIT_MS) queueJingle(slot.id, slot.url);
            else if (now - slot.time >= JINGLE_LATE_LIMIT_MS) playedSlotIds.add(slot.id);
        });
    }, JINGLE_CHECK_INTERVAL_MS);

    function queueJingle(slotId, url) {
        if (slotId != null) {
            if (playedSlotIds.has(slotId)) return;
            playedSlotIds.add(slotId);
        }
        if (!wantsPlayback) return;
        jingleQueue.push(url);
        playQueuedJingle();
    }

    function playQueuedJingle() {
        if (jinglePlaying || jingleQueue.length === 0) return;
        jinglePlaying = true;
        const url = jingleQueue.shift();

        duck(DUCK_LEVEL, 0.6);
        setTimeout(() => {
            jingleEl.src = url;
            jingleEl.play().catch(() => finishJingle());
        }, 500);
    }

    function finishJingle() {
        duck(1, 1.2);
        jinglePlaying = false;
        setTimeout(playQueuedJingle, 1500);
    }

    jingleEl.addEventListener('ended', finishJingle);
    jingleEl.addEventListener('error', finishJingle);

    function handleCommand(command) {
        if (command.type === 'PLAY_JINGLE') {
            queueJingle(command.slotId, norm(command.jingleUrl));
        } else if (command.type === 'FORCE_DISCONNECT') {
            stopPlayback();
            showOverlay('This player was opened on another device. Tap to play here instead.');
        }
    }

    // ---------- live connection ----------
    function connect() {
        clearTimeout(reconnectTimer);
        if (stompClient && connected) return;

        const socket = new SockJS('/ws-player');
        stompClient = Stomp.over(socket);
        stompClient.debug = null;

        stompClient.connect({}, () => {
            connected = true;
            reconnectAttempt = 0;
            updateStatus();

            stompClient.subscribe('/topic/store.' + config.storeId + '.init', (message) => {
                const data = JSON.parse(message.body);
                playlist = (data.playlistUrls || []).map(norm);
                scheduleSlots(data.dailySlots);
                saveForOffline();
                if (wantsPlayback && musicEls.every((el) => el.paused)) playNext(1.5);
                updateStatus();
            });
            stompClient.subscribe('/topic/store.' + config.storeId + '.commands', (message) => {
                handleCommand(JSON.parse(message.body));
            });
            sync();
        }, () => {
            connected = false;
            updateStatus();
            scheduleReconnect();
        });
    }

    function scheduleReconnect() {
        clearTimeout(reconnectTimer);
        const delay = RECONNECT_DELAYS_MS[Math.min(reconnectAttempt, RECONNECT_DELAYS_MS.length - 1)];
        reconnectAttempt++;
        reconnectTimer = setTimeout(connect, delay);
        updateStatus();
    }

    function sync() {
        if (stompClient && connected) stompClient.send('/app/sync.' + config.storeId, {}, {});
    }

    window.addEventListener('online', () => { reconnectAttempt = 0; connect(); updateStatus(); });
    window.addEventListener('offline', updateStatus);
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && !connected) connect();
    });
    // Picks up new or edited jingles and today's new schedule.
    setInterval(() => (connected ? sync() : connect()), RESYNC_INTERVAL_MS);

    function startHeartbeat() {
        stopHeartbeat();
        heartbeatTimer = setInterval(() => {
            if (wantsPlayback && connected) stompClient.send('/app/track.ping.' + config.storeId, {}, {});
        }, 60000);
    }

    function stopHeartbeat() {
        clearInterval(heartbeatTimer);
        heartbeatTimer = null;
    }

    // ---------- saving for offline ----------
    function registerServiceWorker() {
        if (!('serviceWorker' in navigator)) return;
        navigator.serviceWorker.register('/player-sw.js', {scope: '/stores/'})
            .catch((e) => console.warn('Offline support unavailable', e));
    }

    async function saveForOffline() {
        if (!('caches' in window) || cachingInProgress) return;
        cachingInProgress = true;
        try {
            if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
            const cache = await caches.open(AUDIO_CACHE);

            // Current track first, then jingles (small), then the rest of the playlist.
            const current = norm(musicEls[activeEl].src);
            const jingleUrls = [...new Set([...slots.values()].map((s) => s.url))];
            const tracks = [current, ...playlist.filter((u) => u !== current)].filter((u) => playlist.includes(u));
            const wanted = [...jingleUrls, ...tracks];

            // Forget files this store no longer uses.
            for (const request of await cache.keys()) {
                if (!wanted.includes(norm(request.url))) await cache.delete(request);
            }

            let budget = MAX_OFFLINE_BYTES;
            if (navigator.storage && navigator.storage.estimate) {
                const {quota, usage} = await navigator.storage.estimate();
                if (quota) budget = Math.min(budget, (quota - (usage || 0)) * 0.8);
            }

            let used = 0;
            cacheProgress = {done: 0, total: tracks.length};
            for (const url of wanted) {
                const cached = await cache.match(url);
                if (cached) {
                    used += Number(cached.headers.get('Content-Length') || 0);
                    cachedUrls.add(url);
                } else {
                    if (!navigator.onLine) break;
                    const head = await fetch(url, {method: 'HEAD', mode: 'cors'}).catch(() => null);
                    const size = head ? Number(head.headers.get('Content-Length') || 0) : 0;
                    if (used + size > budget) continue;
                    const response = await fetch(url, {mode: 'cors'}).catch(() => null);
                    if (!response || !response.ok) continue;
                    await cache.put(url, response);
                    used += size;
                    cachedUrls.add(url);
                }
                if (tracks.includes(url)) cacheProgress.done++;
                updateStatus();
            }
        } catch (e) {
            console.warn('Saving music for offline failed', e);
        } finally {
            cachingInProgress = false;
            cacheProgress = null;
            updateStatus();
        }
    }

    // ---------- status ----------
    function updateStatus() {
        if (!statusEl) return;
        const savedTracks = playlist.filter((u) => cachedUrls.has(u)).length;
        let text;
        let state;

        if (!navigator.onLine || (!connected && reconnectAttempt >= 2)) {
            state = 'offline';
            text = savedTracks > 0
                ? 'Offline · playing saved music'
                : 'Offline · music will resume when the internet is back';
        } else if (!connected) {
            state = 'reconnecting';
            text = 'Reconnecting…';
        } else if (cacheProgress && cacheProgress.done < cacheProgress.total) {
            state = 'live';
            text = `Live · saving music for offline (${cacheProgress.done} of ${cacheProgress.total})`;
        } else {
            state = 'live';
            text = savedTracks > 0 ? `Live · ${savedTracks} of ${playlist.length} tracks saved for offline` : 'Live';
        }

        statusEl.textContent = text;
        statusEl.dataset.state = state;
    }

    // ---------- go ----------
    registerServiceWorker();
    connect();
    showOverlay('Tap to start the music');
    updateStatus();
})();
