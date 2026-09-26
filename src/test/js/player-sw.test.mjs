// Checks the store player service worker serves saved tracks correctly (run: node src/test/js/player-sw.test.mjs).
import { readFileSync } from 'node:fs';
const src = readFileSync(new URL("../../main/resources/static/player-sw.js", import.meta.url), "utf8");
const self = { addEventListener() {}, skipWaiting() {}, clients: {} };
const fn = new Function('self', 'caches', src + '\nreturn { rangeResponse, isAudioRequest };');
const { rangeResponse, isAudioRequest } = fn(self, {});

const body = new Uint8Array(1000).map((_, i) => i % 256);
const cached = new Response(body, { headers: { 'Content-Type': 'audio/mpeg', 'Content-Length': '1000' } });
const check = async (range, expStatus, expStart, expLen) => {
  const r = await rangeResponse(cached.clone(), range);
  const buf = new Uint8Array(await r.arrayBuffer());
  const ok = r.status === expStatus && (expLen === undefined || (buf.length === expLen && buf[0] === expStart % 256));
  console.log(ok ? 'PASS' : 'FAIL', range, r.status, r.headers.get('content-range'), buf.length);
  if (!ok) process.exitCode = 1;
};
await check('bytes=0-', 206, 0, 1000);
await check('bytes=100-199', 206, 100, 100);
await check('bytes=900-5000', 206, 900, 100);
await check('bytes=-200', 206, 800, 200);
await check('bytes=1000-', 416);
const req = (url, dest) => ({ url, destination: dest });
console.log(isAudioRequest(req('https://agnafyaipjhixqkijmwy.supabase.co/storage/v1/object/public/music/a.mp3', '')) &&
            isAudioRequest(req('https://weresona.com/files/x.mp3', '')) &&
            !isAudioRequest(req('https://weresona.com/stores/15/abc', 'document')) ? 'PASS request matching' : (process.exitCode = 1, 'FAIL request matching'));
