/*
 * Resona service worker (scope "/").
 *
 * 1. Saved music: when a store switches on "save music for offline", the player
 *    page puts its tracks and jingles into the "resona-audio" cache. Audio
 *    requests for those files are answered from the cache, including range
 *    requests (seeking, streaming), so music keeps playing through Wi-Fi drops.
 * 2. Installed app offline: the store player page and the files it needs
 *    (scripts, styles, icons) are kept in "resona-app", so the installed app
 *    still opens without internet. Online, they always come from the network.
 *
 * Everything else (dashboard pages, API calls) goes to the network untouched.
 */
const AUDIO_CACHE = 'resona-audio-v1';
const APP_CACHE = 'resona-app-v1';
const PLAYER_PAGE = /^\/stores\/\d+\/[0-9a-f-]{36}\/?$/i;
const LAST_PLAYER_KEY = '/__resona-last-player';

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        const keep = [AUDIO_CACHE, APP_CACHE];
        const names = await caches.keys();
        await Promise.all(names
            .filter((name) => name.startsWith('resona-') && !keep.includes(name))
            .map((name) => caches.delete(name)));
        await self.clients.claim();
    })());
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    if (request.method !== 'GET') return;
    const url = new URL(request.url);

    if (request.mode === 'navigate') {
        if (PLAYER_PAGE.test(url.pathname) || url.pathname === '/player') {
            event.respondWith(playerPage(request, url));
        }
        return;
    }

    if (isAudioRequest(request, url)) {
        event.respondWith(savedAudio(request));
        return;
    }

    if (isPlayerAsset(url)) {
        event.respondWith(networkFirst(request));
    }
});

// ---------- installed app ----------

async function playerPage(request, url) {
    const cache = await caches.open(APP_CACHE);
    try {
        const response = await fetch(request);
        // Keep the store player page (not login redirects) for opening offline.
        const finalPath = new URL(response.url || request.url).pathname;
        if (response.ok && PLAYER_PAGE.test(finalPath)) {
            await cache.put(LAST_PLAYER_KEY, response.clone());
        }
        return response;
    } catch (e) {
        const cached = await cache.match(LAST_PLAYER_KEY);
        if (cached) return cached;
        return new Response(
            '<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">'
            + '<body style="font-family:sans-serif;text-align:center;padding:48px">'
            + '<h2>No internet connection</h2><p>Connect to the internet and open Resona again.</p>',
            {status: 503, headers: {'Content-Type': 'text/html; charset=utf-8'}}
        );
    }
}

function isPlayerAsset(url) {
    if (url.origin === self.location.origin) {
        return url.pathname.startsWith('/js/')
            || url.pathname.startsWith('/css/')
            || url.pathname.startsWith('/assets/')
            || url.pathname === '/manifest.json';
    }
    return url.hostname === 'cdnjs.cloudflare.com'
        || url.hostname === 'fonts.googleapis.com'
        || url.hostname === 'fonts.gstatic.com';
}

async function networkFirst(request) {
    const cache = await caches.open(APP_CACHE);
    try {
        const response = await fetch(request);
        if (response.ok || response.type === 'opaque') await cache.put(request, response.clone());
        return response;
    } catch (e) {
        const cached = await cache.match(request);
        if (cached) return cached;
        throw e;
    }
}

// ---------- saved music ----------

function isAudioRequest(request, url) {
    if (request.destination === 'audio') return true;
    return url.pathname.endsWith('.mp3')
        || url.pathname.startsWith('/files/')
        || url.pathname.includes('/storage/v1/object/public/');
}

async function savedAudio(request) {
    const cache = await caches.open(AUDIO_CACHE);
    const cached = await cache.match(request.url);
    if (!cached) return fetch(request);

    const range = request.headers.get('range');
    if (!range) return cached;
    return rangeResponse(cached, range);
}

async function rangeResponse(cached, rangeHeader) {
    const blob = await cached.blob();
    const size = blob.size;
    const match = /bytes=(\d*)-(\d*)/.exec(rangeHeader);
    if (!match) return new Response(blob, {status: 200, headers: cached.headers});

    let start = match[1] === '' ? null : parseInt(match[1], 10);
    let end = match[2] === '' ? null : parseInt(match[2], 10);
    if (start === null) {            // "bytes=-500": the last 500 bytes
        start = Math.max(size - end, 0);
        end = size - 1;
    } else if (end === null || end >= size) {
        end = size - 1;
    }
    if (start >= size || start > end) {
        return new Response(null, {status: 416, headers: {'Content-Range': `bytes */${size}`}});
    }

    return new Response(blob.slice(start, end + 1), {
        status: 206,
        statusText: 'Partial Content',
        headers: {
            'Content-Type': cached.headers.get('Content-Type') || 'audio/mpeg',
            'Content-Length': String(end - start + 1),
            'Content-Range': `bytes ${start}-${end}/${size}`,
            'Accept-Ranges': 'bytes'
        }
    });
}
