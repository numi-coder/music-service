// Rebuilds the list of internet addresses of the countries where Resona opens in
// Russian first (the 15 post-Soviet countries). Source: RIPE NCC's public list of
// which addresses are assigned to which country. Run from the repo root a few
// times a year:  node scripts/update-russian-first-ranges.mjs
import fs from 'node:fs';

const COUNTRIES = ['RU', 'UA', 'BY', 'KZ', 'UZ', 'KG', 'TJ', 'TM', 'AZ', 'AM', 'GE', 'MD', 'EE', 'LV', 'LT'];
const SOURCE = 'https://ftp.ripe.net/pub/stats/ripencc/delegated-ripencc-extended-latest';
const TARGET = 'src/main/resources/geo/russian-first-ipv4.txt';

const text = process.argv[2] ? fs.readFileSync(process.argv[2], 'utf8') : await (await fetch(SOURCE)).text();

const toNumber = (ip) => ip.split('.').reduce((n, part) => n * 256 + Number(part), 0);
const toIp = (n) => [n / 16777216, (n / 65536) % 256, (n / 256) % 256, n % 256].map(Math.floor).join('.');

const ranges = text.split('\n')
    .map((line) => line.split('|'))
    .filter(([, country, type, , , , status]) => type === 'ipv4' && COUNTRIES.includes(country) && ['allocated', 'assigned'].includes(status))
    .map(([, , , start, count]) => [toNumber(start), toNumber(start) + Number(count) - 1])
    .sort((a, b) => a[0] - b[0]);

const merged = [];
for (const [start, end] of ranges) {
    const last = merged[merged.length - 1];
    if (last && start <= last[1] + 1) last[1] = Math.max(last[1], end);
    else merged.push([start, end]);
}
if (merged.length < 1000) throw new Error('Suspiciously few ranges: ' + merged.length);

const header = `# IPv4 ranges of: ${COUNTRIES.join(' ')}\n# Source: ${SOURCE} (${new Date().toISOString().slice(0, 10)})\n`;
fs.mkdirSync('src/main/resources/geo', {recursive: true});
fs.writeFileSync(TARGET, header + merged.map(([start, end]) => `${toIp(start)}-${toIp(end)}`).join('\n') + '\n');
console.log(`${merged.length} ranges written to ${TARGET}`);
