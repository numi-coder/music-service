// Checks the Resona service worker (run: node src/test/js/player-sw.test.mjs).
import { readFileSync } from 'node:fs';

const src = readFileSync(new URL('../../main/resources/static/player-sw.js', import.meta.url), 'utf8');
const self = { addEventListener() {}, skipWaiting() {}, clients: {}, location: new URL('https://weresona.com/') };
const fn = new Function('self', 'caches', src + '\nreturn { rangeResponse, isAudioRequest, isPlayerAsset, PLAYER_PAGE };');
const { rangeResponse, isAudioRequest, isPlayerAsset, PLAYER_PAGE } = fn(self, {});

let failed = 0;
const expect = (ok, label) => { console.log(ok ? 'PASS' : 'FAIL', label); if (!ok) failed++; };

// Saved tracks are served in slices, as audio players request them.
const body = new Uint8Array(1000).map((_, i) => i % 256);
const cached = new Response(body, { headers: { 'Content-Type': 'audio/mpeg', 'Content-Length': '1000' } });
const range = async (header, status, first, length) => {
  const r = await rangeResponse(cached.clone(), header);
  const buf = new Uint8Array(await r.arrayBuffer());
  expect(r.status === status && (length === undefined || (buf.length === length && buf[0] === first % 256)),
    `range ${header} -> ${r.status} ${r.headers.get('content-range')}`);
};
await range('bytes=0-', 206, 0, 1000);
await range('bytes=100-199', 206, 100, 100);
await range('bytes=900-5000', 206, 900, 100);
await range('bytes=-200', 206, 800, 200);
await range('bytes=1000-', 416);

// Which requests the worker handles.
const req = (url, destination = '') => [{ url, destination }, new URL(url)];
expect(isAudioRequest(...req('https://agnafyaipjhixqkijmwy.supabase.co/storage/v1/object/public/music/a.mp3')), 'supabase track is audio');
expect(isAudioRequest(...req('https://weresona.com/files/x.mp3')), 'jingle file is audio');
expect(!isAudioRequest(...req('https://weresona.com/stores/15/abc', 'document')), 'page is not audio');
expect(isPlayerAsset(new URL('https://weresona.com/js/store-player.js')), 'player script is kept for offline');
expect(isPlayerAsset(new URL('https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js')), 'cdnjs script is kept for offline');
expect(!isPlayerAsset(new URL('https://weresona.com/api/jingles')), 'API calls are not cached');
expect(PLAYER_PAGE.test('/stores/15/cd6c12f9-7a02-4362-9930-72888e895299'), 'store player page is recognised');
expect(!PLAYER_PAGE.test('/stores'), 'stores list is not a player page');

if (failed) process.exit(1);
