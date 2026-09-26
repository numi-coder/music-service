/*
 * Resona store player service worker.
 *
 * The player page saves this store's tracks and today's jingles into the
 * "resona-audio" cache while online. When the audio element asks for one of
 * those files, it is answered from the cache, so music keeps playing through
 * Wi-Fi drops. Range requests (seeking, streaming) are served by slicing the
 * cached file. Anything not in the cache goes to the network as usual.
 */
const AUDIO_CACHE = 'resona-audio-v1';

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', (event) => {
    event.waitUntil((async () => {
        const names = await caches.keys();
        await Promise.all(names
            .filter((name) => name.startsWith('resona-audio-') && name !== AUDIO_CACHE)
            .map((name) => caches.delete(name)));
        await self.clients.claim();
    })());
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    if (request.method !== 'GET' || !isAudioRequest(request)) return;

    event.respondWith((async () => {
        const cache = await caches.open(AUDIO_CACHE);
        const cached = await cache.match(request.url);
        if (!cached) return fetch(request);

        const range = request.headers.get('range');
        if (!range) return cached;
        return rangeResponse(cached, range);
    })());
});

function isAudioRequest(request) {
    if (request.destination === 'audio') return true;
    const url = new URL(request.url);
    return url.pathname.endsWith('.mp3')
        || url.pathname.startsWith('/files/')
        || url.pathname.includes('/storage/v1/object/public/');
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
