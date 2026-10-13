// Generates the product illustrations: one side-view SVG per shoe style and colour.
//   node scripts/gen-shoe-images.mjs   ->  frontend/public/images/shoes/<style>-<colour>.svg
// The seed data references these files (DataSeeder: /images/shoes/<style>-<colour>.svg).
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const out = join(dirname(fileURLToPath(import.meta.url)), '..', 'frontend', 'public', 'images', 'shoes');
mkdirSync(out, { recursive: true });

const colors = {
  den: '#2b2b2e', trang: '#f4f4f2', xam: '#9aa1a8', navy: '#1f3a5f', do: '#c62f2f', nau: '#7a4a2b',
  be: '#d8c3a1', xanhla: '#2f7d4a', hong: '#e05a8a', vang: '#f2b631', cam: '#ec7a1c', xanhduong: '#2a74c9',
};

function shade(hex, amount) {
  const n = parseInt(hex.slice(1), 16);
  const f = (c) => Math.max(0, Math.min(255, Math.round(c + amount * 255)));
  const r = f(n >> 16), g = f((n >> 8) & 255), b = f(n & 255);
  return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`;
}

const light = (hex) => parseInt(hex.slice(1, 3), 16) * 0.3 + parseInt(hex.slice(3, 5), 16) * 0.59 + parseInt(hex.slice(5, 7), 16) * 0.11 > 200;

// Each style returns the SVG body for a given colour c.
const styles = {
  running: (c) => `
    <path d="M42 206 Q40 232 74 234 L326 234 Q366 232 366 206 L356 194 L52 194 Z" fill="#fafafa" stroke="#c9c9c9" stroke-width="2"/>
    <path d="M48 214 L362 214" stroke="${shade(c, -0.1)}" stroke-width="5" opacity=".55"/>
    <path d="M54 196 L62 150 Q72 118 112 118 L150 121 Q178 94 218 92 L244 96 Q262 130 304 150 Q352 166 360 196 Z" fill="${c}" stroke="${shade(c, -0.25)}" stroke-width="2"/>
    <path d="M70 182 Q170 150 330 168" fill="none" stroke="${light(c) ? '#2b2b2e' : '#ffffff'}" stroke-width="7" stroke-linecap="round" opacity=".85"/>
    <path d="M62 150 Q70 128 92 124 L96 160 Z" fill="${shade(c, -0.18)}"/>
    ${[0, 1, 2, 3].map((i) => `<line x1="${168 + i * 16}" y1="${108 + i * 9}" x2="${184 + i * 16}" y2="${100 + i * 9}" stroke="${light(c) ? '#555' : '#f0f0f0'}" stroke-width="4" stroke-linecap="round"/>`).join('')}`,
  sneaker: (c) => `
    <path d="M38 200 L38 226 Q40 234 52 234 L350 234 Q366 234 366 222 L366 200 Z" fill="#f7f7f5" stroke="#cfcfcf" stroke-width="2"/>
    <path d="M38 216 L366 216" stroke="#d9d9d9" stroke-width="2"/>
    <path d="M46 200 L52 140 Q58 112 96 110 L140 112 Q160 96 196 96 L232 102 Q256 140 300 156 Q350 172 362 200 Z" fill="${c}" stroke="${shade(c, -0.25)}" stroke-width="2"/>
    <path d="M300 156 Q350 172 362 200 L300 200 Q296 176 300 156 Z" fill="${shade(c, -0.12)}"/>
    <path d="M52 140 Q58 116 88 112 L92 170 L50 172 Z" fill="${shade(c, 0.12)}"/>
    <circle cx="120" cy="150" r="14" fill="none" stroke="${light(c) ? '#2b2b2e' : '#fff'}" stroke-width="5" opacity=".8"/>
    ${[0, 1, 2, 3].map((i) => `<line x1="${160 + i * 18}" y1="${110 + i * 10}" x2="${178 + i * 18}" y2="${102 + i * 10}" stroke="${light(c) ? '#666' : '#fafafa'}" stroke-width="4" stroke-linecap="round"/>`).join('')}`,
  oxford: (c) => `
    <path d="M44 214 L44 228 L94 228 L94 214 Z" fill="${shade(c, -0.35)}"/>
    <path d="M94 222 L360 222 Q372 220 370 210 L100 210 Z" fill="${shade(c, -0.35)}"/>
    <path d="M44 214 L50 162 Q56 140 90 138 L150 140 Q190 118 226 122 Q280 150 330 168 Q372 180 370 210 L94 212 L94 214 Z" fill="${c}" stroke="${shade(c, -0.3)}" stroke-width="2"/>
    <path d="M300 160 Q330 170 346 188" fill="none" stroke="${shade(c, -0.25)}" stroke-width="3"/>
    <path d="M150 140 Q196 150 226 122" fill="none" stroke="${shade(c, -0.25)}" stroke-width="3"/>
    ${[0, 1, 2].map((i) => `<line x1="${176 + i * 14}" y1="${134 + i * 4}" x2="${190 + i * 14}" y2="${126 + i * 4}" stroke="${shade(c, -0.45)}" stroke-width="3" stroke-linecap="round"/>`).join('')}
    <path d="M70 150 Q120 132 170 140" fill="none" stroke="${shade(c, 0.15)}" stroke-width="3" opacity=".7"/>`,
  boot: (c) => `
    <path d="M60 206 L60 234 L360 234 Q372 232 370 216 L362 206 Z" fill="${shade(c, -0.4)}"/>
    <path d="M60 222 L370 222" stroke="${shade(c, -0.5)}" stroke-width="3"/>
    <path d="M66 208 L70 70 Q72 54 92 54 L170 54 Q186 54 186 70 L190 132 Q240 140 300 160 Q356 178 362 208 Z" fill="${c}" stroke="${shade(c, -0.28)}" stroke-width="2"/>
    <path d="M120 60 L120 150 Q140 160 160 150 L160 60 Z" fill="${shade(c, -0.2)}"/>
    ${[0, 1, 2, 3, 4].map((i) => `<line x1="124" y1="${72 + i * 16}" x2="156" y2="${72 + i * 16}" stroke="${shade(c, -0.32)}" stroke-width="2"/>`).join('')}
    <path d="M70 54 Q60 40 74 36 L90 44 Z" fill="${shade(c, -0.25)}"/>`,
  sandal: (c) => `
    <path d="M40 212 Q40 228 64 228 L340 228 Q370 226 370 210 L364 200 L46 200 Z" fill="${shade(c, -0.35)}"/>
    <path d="M46 200 Q200 192 364 200 L360 208 L50 208 Z" fill="#d9c3a0"/>
    <path d="M80 200 Q90 140 130 140 Q160 140 160 200" fill="none" stroke="${c}" stroke-width="16" stroke-linecap="round"/>
    <path d="M200 200 Q230 130 290 150 Q330 166 340 200" fill="none" stroke="${c}" stroke-width="18" stroke-linecap="round"/>
    <path d="M150 168 L230 160" stroke="${shade(c, -0.15)}" stroke-width="12" stroke-linecap="round"/>
    <rect x="132" y="158" width="22" height="18" rx="3" fill="#b9b9b9"/>`,
  heel: (c) => `
    <path d="M82 150 L96 150 L108 236 L86 236 Z" fill="${shade(c, -0.3)}"/>
    <path d="M62 150 Q60 128 84 126 Q140 150 200 196 Q240 222 300 224 Q350 226 362 214 Q366 204 350 200 L320 198 Q280 196 250 186 Q170 160 120 128 Q100 116 82 124 Z" fill="${c}" stroke="${shade(c, -0.28)}" stroke-width="2"/>
    <path d="M200 196 Q240 222 300 224 Q350 226 362 214 L362 222 Q350 236 300 234 Q240 232 196 204 Z" fill="${shade(c, -0.35)}"/>
    <path d="M140 150 Q200 186 260 190" fill="none" stroke="${shade(c, 0.2)}" stroke-width="3" opacity=".7"/>`,
  football: (c) => `
    <path d="M42 200 L42 218 Q44 224 54 224 L352 224 Q366 224 366 212 L362 200 Z" fill="#232323"/>
    ${[70, 110, 150, 230, 280, 330].map((x) => `<path d="M${x - 8} 224 L${x + 8} 224 L${x + 4} 238 L${x - 4} 238 Z" fill="#3a3a3a"/>`).join('')}
    <path d="M48 200 L54 148 Q60 118 100 118 L146 120 Q170 100 206 98 L236 104 Q258 136 300 152 Q352 170 362 200 Z" fill="${c}" stroke="${shade(c, -0.25)}" stroke-width="2"/>
    <path d="M60 188 L340 176" stroke="${light(c) ? '#2b2b2e' : '#fff'}" stroke-width="6" opacity=".75"/>
    <path d="M90 160 L320 150" stroke="${light(c) ? '#2b2b2e' : '#fff'}" stroke-width="3" opacity=".5"/>
    ${[0, 1, 2, 3].map((i) => `<line x1="${164 + i * 16}" y1="${114 + i * 9}" x2="${180 + i * 16}" y2="${106 + i * 9}" stroke="${light(c) ? '#555' : '#eee'}" stroke-width="4" stroke-linecap="round"/>`).join('')}`,
  kids: (c) => `
    <path d="M70 200 L70 222 Q72 232 86 232 L320 232 Q340 232 340 216 L338 200 Z" fill="#fbfbfb" stroke="#d0d0d0" stroke-width="2"/>
    <path d="M76 200 L80 150 Q86 120 120 118 L180 116 Q220 118 250 140 Q320 160 336 200 Z" fill="${c}" stroke="${shade(c, -0.25)}" stroke-width="2"/>
    <rect x="150" y="128" width="90" height="18" rx="9" fill="${shade(c, -0.18)}" transform="rotate(14 195 137)"/>
    <rect x="140" y="156" width="96" height="18" rx="9" fill="${shade(c, -0.18)}" transform="rotate(10 188 165)"/>
    <circle cx="112" cy="168" r="10" fill="${light(c) ? '#f2b631' : '#fff'}" opacity=".9"/>
    <path d="M250 140 Q320 160 336 200 L290 200 Q280 160 250 140 Z" fill="${shade(c, 0.1)}"/>`,
};

function svg(style, hex) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 300" width="400" height="300">
  <rect width="400" height="300" fill="#f3f1ec"/>
  <ellipse cx="205" cy="246" rx="170" ry="12" fill="#000" opacity=".08"/>${styles[style](hex)}
</svg>
`;
}

let n = 0;
for (const style of Object.keys(styles)) {
  for (const [key, hex] of Object.entries(colors)) {
    writeFileSync(join(out, `${style}-${key}.svg`), svg(style, hex));
    n++;
  }
}
console.log(`wrote ${n} images to ${out}`);
