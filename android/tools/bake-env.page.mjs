// Se ejecuta con tools/cdp.mjs contra el servidor web de Nidaria. Dibuja el
// entorno de Three.js (RoomEnvironment) desde el origen en seis vistas y lo
// convierte en el mapa cúbico prefiltrado que usa la app de Android.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const VIEW = 128;
const BASE = 64;
const LEVELS = 6; // 64, 32, 16, 8, 4, 2: rugosidad 0, 0.2 ... 1
const VIEWS = [
  { f: [1, 0, 0], u: [0, 1, 0] }, { f: [-1, 0, 0], u: [0, 1, 0] },
  { f: [0, 1, 0], u: [0, 0, -1] }, { f: [0, -1, 0], u: [0, 0, 1] },
  { f: [0, 0, 1], u: [0, 1, 0] }, { f: [0, 0, -1], u: [0, 1, 0] },
];

const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const norm = (a) => { const l = Math.hypot(a[0], a[1], a[2]); return [a[0] / l, a[1] / l, a[2] / l]; };

export default async function bake(page) {
  const encoded = await page.eval(`(async () => {
    const THREE = await import('three');
    const { RoomEnvironment } = await import('three/addons/environments/RoomEnvironment.js');
    const N = ${VIEW};
    const renderer = new THREE.WebGLRenderer({ canvas: document.createElement('canvas') });
    renderer.toneMapping = THREE.NoToneMapping;
    const scene = new RoomEnvironment();
    const target = new THREE.WebGLRenderTarget(N, N, { type: THREE.FloatType, colorSpace: THREE.LinearSRGBColorSpace });
    const camera = new THREE.PerspectiveCamera(90, 1, 0.1, 100);
    const out = [];
    for (const view of ${JSON.stringify(VIEWS)}) {
      camera.up.set(...view.u);
      camera.lookAt(...view.f);
      camera.updateMatrixWorld();
      renderer.setRenderTarget(target);
      renderer.render(scene, camera);
      const pixels = new Float32Array(N * N * 4);
      renderer.readRenderTargetPixels(target, 0, 0, N, N, pixels);
      const bytes = new Uint8Array(pixels.buffer);
      let text = '';
      for (let i = 0; i < bytes.length; i += 32768) text += String.fromCharCode(...bytes.subarray(i, i + 32768));
      out.push(btoa(text));
    }
    return out;
  })()`);

  const views = encoded.map((b64, i) => {
    const bytes = Buffer.from(b64, 'base64');
    const data = new Float32Array(bytes.buffer, bytes.byteOffset, bytes.byteLength / 4);
    const { f, u } = VIEWS[i];
    return { f, u, r: cross(f, u), data };
  });

  // Radiancia del entorno en una dirección, con filtro bilineal.
  function radiance(d) {
    let best = views[0];
    let bestDot = -Infinity;
    for (const view of views) {
      const a = dot(d, view.f);
      if (a > bestDot) { bestDot = a; best = view; }
    }
    const x = dot(d, best.r) / bestDot;
    const y = dot(d, best.u) / bestDot;
    const px = Math.min(VIEW - 1, Math.max(0, (x + 1) / 2 * VIEW - 0.5));
    const py = Math.min(VIEW - 1, Math.max(0, (y + 1) / 2 * VIEW - 0.5));
    const x0 = Math.floor(px), y0 = Math.floor(py);
    const x1 = Math.min(VIEW - 1, x0 + 1), y1 = Math.min(VIEW - 1, y0 + 1);
    const fx = px - x0, fy = py - y0;
    const out = [0, 0, 0];
    for (const [xi, yi, w] of [[x0, y0, (1 - fx) * (1 - fy)], [x1, y0, fx * (1 - fy)], [x0, y1, (1 - fx) * fy], [x1, y1, fx * fy]]) {
      const o = (yi * VIEW + xi) * 4;
      out[0] += best.data[o] * w; out[1] += best.data[o + 1] * w; out[2] += best.data[o + 2] * w;
    }
    return out;
  }

  // Dirección de un texel según el convenio de mapas cúbicos de OpenGL.
  function faceDir(face, s, t) {
    const sc = 2 * s - 1, tc = 2 * t - 1;
    switch (face) {
      case 0: return norm([1, -tc, -sc]);
      case 1: return norm([-1, -tc, sc]);
      case 2: return norm([sc, 1, tc]);
      case 3: return norm([sc, -1, -tc]);
      case 4: return norm([sc, -tc, 1]);
      default: return norm([-sc, -tc, -1]);
    }
  }

  function makeLevel(size, fn) {
    const data = new Float32Array(6 * size * size * 3);
    let o = 0;
    for (let face = 0; face < 6; face++) {
      for (let j = 0; j < size; j++) {
        for (let i = 0; i < size; i++) {
          const c = fn(face, i, j, size);
          data[o++] = c[0]; data[o++] = c[1]; data[o++] = c[2];
        }
      }
    }
    return { size, data };
  }

  // Nivel base: cuatro muestras por texel.
  const base = makeLevel(BASE, (face, i, j, size) => {
    const c = [0, 0, 0];
    for (const [dx, dy] of [[0.25, 0.25], [0.75, 0.25], [0.25, 0.75], [0.75, 0.75]]) {
      const s = radiance(faceDir(face, (i + dx) / size, (j + dy) / size));
      c[0] += s[0] / 4; c[1] += s[1] / 4; c[2] += s[2] / 4;
    }
    return c;
  });

  // Pirámide de cajas del nivel base, para muestrear sin ruido.
  const pyramid = [base];
  while (pyramid[pyramid.length - 1].size > 1) {
    const prev = pyramid[pyramid.length - 1];
    const size = prev.size / 2;
    pyramid.push(makeLevel(size, (face, i, j) => {
      const c = [0, 0, 0];
      for (const [dx, dy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) {
        const o = (face * prev.size * prev.size + (j * 2 + dy) * prev.size + i * 2 + dx) * 3;
        c[0] += prev.data[o] / 4; c[1] += prev.data[o + 1] / 4; c[2] += prev.data[o + 2] / 4;
      }
      return c;
    }));
  }

  function sampleLevel(level, d) {
    const ax = Math.abs(d[0]), ay = Math.abs(d[1]), az = Math.abs(d[2]);
    let face, sc, tc, ma;
    if (ax >= ay && ax >= az) { ma = ax; if (d[0] > 0) { face = 0; sc = -d[2]; tc = -d[1]; } else { face = 1; sc = d[2]; tc = -d[1]; } }
    else if (ay >= az) { ma = ay; if (d[1] > 0) { face = 2; sc = d[0]; tc = d[2]; } else { face = 3; sc = d[0]; tc = -d[2]; } }
    else { ma = az; if (d[2] > 0) { face = 4; sc = d[0]; tc = -d[1]; } else { face = 5; sc = -d[0]; tc = -d[1]; } }
    const n = level.size;
    const px = Math.min(n - 1, Math.max(0, (sc / ma + 1) / 2 * n - 0.5));
    const py = Math.min(n - 1, Math.max(0, (tc / ma + 1) / 2 * n - 0.5));
    const x0 = Math.floor(px), y0 = Math.floor(py);
    const x1 = Math.min(n - 1, x0 + 1), y1 = Math.min(n - 1, y0 + 1);
    const fx = px - x0, fy = py - y0;
    const out = [0, 0, 0];
    for (const [xi, yi, w] of [[x0, y0, (1 - fx) * (1 - fy)], [x1, y0, fx * (1 - fy)], [x0, y1, (1 - fx) * fy], [x1, y1, fx * fy]]) {
      const o = (face * n * n + yi * n + xi) * 3;
      out[0] += level.data[o] * w; out[1] += level.data[o + 1] * w; out[2] += level.data[o + 2] * w;
    }
    return out;
  }

  function samplePyramid(d, lod) {
    const l = Math.min(pyramid.length - 1, Math.max(0, lod));
    const l0 = Math.floor(l), l1 = Math.min(pyramid.length - 1, l0 + 1), f = l - l0;
    const a = sampleLevel(pyramid[l0], d);
    if (f < 1e-4) return a;
    const b = sampleLevel(pyramid[l1], d);
    return [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f];
  }

  // Prefiltrado GGX por muestreo de importancia (N = V = R).
  const SAMPLES = 768;
  const radicalInverse = (i) => {
    let bits = i >>> 0;
    bits = ((bits << 16) | (bits >>> 16)) >>> 0;
    bits = (((bits & 0x55555555) << 1) | ((bits & 0xaaaaaaaa) >>> 1)) >>> 0;
    bits = (((bits & 0x33333333) << 2) | ((bits & 0xcccccccc) >>> 2)) >>> 0;
    bits = (((bits & 0x0f0f0f0f) << 4) | ((bits & 0xf0f0f0f0) >>> 4)) >>> 0;
    bits = (((bits & 0x00ff00ff) << 8) | ((bits & 0xff00ff00) >>> 8)) >>> 0;
    return bits / 4294967296;
  };
  const texelSolid = (4 * Math.PI) / (6 * BASE * BASE);

  function prefilter(n, roughness) {
    const a = roughness * roughness;
    const up = Math.abs(n[1]) < 0.999 ? [0, 1, 0] : [1, 0, 0];
    const tx = norm(cross(up, n));
    const ty = cross(n, tx);
    const out = [0, 0, 0];
    let total = 0;
    for (let i = 0; i < SAMPLES; i++) {
      const u1 = (i + 0.5) / SAMPLES, u2 = radicalInverse(i);
      const phi = 2 * Math.PI * u2;
      const cosT = Math.sqrt((1 - u1) / (1 + (a * a - 1) * u1));
      const sinT = Math.sqrt(1 - cosT * cosT);
      const h = [
        tx[0] * sinT * Math.cos(phi) + ty[0] * sinT * Math.sin(phi) + n[0] * cosT,
        tx[1] * sinT * Math.cos(phi) + ty[1] * sinT * Math.sin(phi) + n[1] * cosT,
        tx[2] * sinT * Math.cos(phi) + ty[2] * sinT * Math.sin(phi) + n[2] * cosT,
      ];
      const vh = dot(n, h);
      const l = [2 * vh * h[0] - n[0], 2 * vh * h[1] - n[1], 2 * vh * h[2] - n[2]];
      const nl = dot(n, l);
      if (nl <= 0) continue;
      const d = (a * a) / (Math.PI * ((cosT * cosT) * (a * a - 1) + 1) ** 2);
      const pdf = d / 4;
      const lod = 0.5 * Math.log2(1 / (SAMPLES * pdf) / texelSolid) + 1;
      const c = samplePyramid(l, lod);
      out[0] += c[0] * nl; out[1] += c[1] * nl; out[2] += c[2] * nl;
      total += nl;
    }
    return [out[0] / total, out[1] / total, out[2] / total];
  }

  const levels = [base];
  for (let level = 1; level < LEVELS; level++) {
    const roughness = level / (LEVELS - 1);
    levels.push(makeLevel(BASE >> level, (face, i, j, size) => prefilter(faceDir(face, (i + 0.5) / size, (j + 0.5) / size), roughness)));
  }

  // Armónicos esféricos de orden 2 para la luz difusa: radiancia media
  // ponderada por el coseno (irradiancia / π).
  const sh = Array.from({ length: 9 }, () => [0, 0, 0]);
  for (let face = 0; face < 6; face++) {
    for (let j = 0; j < BASE; j++) {
      for (let i = 0; i < BASE; i++) {
        const s = (i + 0.5) / BASE, t = (j + 0.5) / BASE;
        const d = faceDir(face, s, t);
        const u = 2 * s - 1, v = 2 * t - 1;
        const weight = 4 / ((1 + u * u + v * v) ** 1.5) / (BASE * BASE);
        const o = (face * BASE * BASE + j * BASE + i) * 3;
        const [x, y, z] = d;
        const basis = [
          0.282095, 0.488603 * y, 0.488603 * z, 0.488603 * x,
          1.092548 * x * y, 1.092548 * y * z, 0.315392 * (3 * z * z - 1), 1.092548 * x * z, 0.546274 * (x * x - y * y),
        ];
        for (let k = 0; k < 9; k++) {
          for (let c = 0; c < 3; c++) sh[k][c] += base.data[o + c] * basis[k] * weight;
        }
      }
    }
  }
  const band = [1, 2 / 3, 2 / 3, 2 / 3, 1 / 4, 1 / 4, 1 / 4, 1 / 4, 1 / 4];
  const diffuse = sh.map((coef, k) => coef.map((value) => value * band[k]));

  // Fichero: 'NENV', tamaño base, niveles, 27 flotantes de SH y los niveles.
  const total = levels.reduce((sum, level) => sum + level.data.length, 0);
  const buffer = Buffer.alloc(12 + 27 * 4 + total * 4);
  buffer.write('NENV', 0, 'ascii');
  buffer.writeUInt32LE(BASE, 4);
  buffer.writeUInt32LE(LEVELS, 8);
  let offset = 12;
  for (const coef of diffuse) for (const value of coef) { buffer.writeFloatLE(value, offset); offset += 4; }
  for (const level of levels) for (const value of level.data) { buffer.writeFloatLE(value, offset); offset += 4; }
  const here = path.dirname(fileURLToPath(import.meta.url));
  const file = path.join(here, '..', 'app', 'src', 'main', 'assets', 'env.bin');
  fs.writeFileSync(file, buffer);

  const probe = (d) => {
    const [x, y, z] = d;
    const basis = [0.282095, 0.488603 * y, 0.488603 * z, 0.488603 * x, 1.092548 * x * y, 1.092548 * y * z, 0.315392 * (3 * z * z - 1), 1.092548 * x * z, 0.546274 * (x * x - y * y)];
    return [0, 1, 2].map((c) => diffuse.reduce((sum, coef, k) => sum + coef[c] * basis[k], 0).toFixed(3)).join(' ');
  };
  console.log('env.bin', buffer.length, 'bytes');
  console.log('difusa arriba', probe([0, 1, 0]), '| abajo', probe([0, -1, 0]), '| frente', probe([0, 0, 1]), '| atrás', probe([0, 0, -1]), '| izq', probe([-1, 0, 0]), '| der', probe([1, 0, 0]));
  let peak = 0;
  for (const value of base.data) peak = Math.max(peak, value);
  console.log('pico de radiancia', peak.toFixed(2));
}
