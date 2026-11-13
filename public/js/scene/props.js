// Modelos 3D de los artículos de la tienda y del huevo. Casi todos se
// esculpen como las criaturas (ver sdf.js): formas blandas fundidas, con
// el color pintado y las sombras de pliegue horneadas. Los mismos
// modelos sirven para la escena y para las miniaturas de la interfaz.

import * as THREE from 'three';
import { createRng } from '/shared/rng.js';
import { Sculpt, shape as Sh } from './sdf.js';
import { clay, plain, canvasTexture } from './materials.js';
import { wallpaperTexture, buildRug, isWallpaper, isRug } from './decor.js';

const { sin, cos, PI } = Math;
const V = (x = 0, y = 0, z = 0) => new THREE.Vector3(x, y, z);
const Y = V(0, 1, 0);
const quatTo = (dir) => new THREE.Quaternion().setFromUnitVectors(Y, dir.clone().normalize());

const C = {
  red: '#f2605c', apple: '#ee5252', orange: '#ff9f43', carrot: '#ff8c3a', yellow: '#ffd166', green: '#6cc47f', leaf: '#4fae7c',
  blue: '#7fb2f0', navy: '#4d6fb8', pink: '#ff8fb1', cream: '#fff3dc', white: '#fffdf8',
  tan: '#e2b279', crust: '#dc9a55', brown: '#a8744c', cocoa: '#6b4632', grey: '#b8c4cc', gold: '#f2c14e', mint: '#7ed6a5', violet: '#a98ee0',
  terracotta: '#e8875f',
};

// ── Utilidades ────────────────────────────────────────────────

// Mallas esculpidas guardadas por nombre: los artículos no cambian.
const geometries = new Map();
function sculpted(key, finish, make, { cell = 0.02, ao = 1, aoReach = 0.12, ground = 0, groundK = 0.02 } = {}) {
  if (geometries.has(key)) {
    // Recién usada: pasa al final de la cola.
    const geometry = geometries.get(key);
    geometries.delete(key);
    geometries.set(key, geometry);
  } else {
    const S = new Sculpt({ ground, groundK });
    make(S);
    const geometry = S.build({ cell, ao, aoReach });
    geometry.userData.shared = true;
    geometries.set(key, geometry);
    // La ropa depende de cada criatura: no se guardan sin límite.
    if (geometries.size > 160) {
      const [oldKey, old] = geometries.entries().next().value;
      geometries.delete(oldKey);
      old.dispose();
    }
  }
  const mesh = new THREE.Mesh(geometries.get(key), typeof finish === 'string' ? clay({ finish }) : finish);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  return mesh;
}

function group(...children) {
  const g = new THREE.Group();
  if (children.length) g.add(...children);
  return g;
}

const sharedGeo = (geometry) => {
  geometry.userData.shared = true;
  return geometry;
};
const BALL = sharedGeo(new THREE.SphereGeometry(1, 28, 20));

// Cuenco: un cilindro redondeado hueco con pie.
function bowl(S, color, r = 0.5, h = 0.4, band) {
  S.add(Sh.cylinder([0, h / 2, 0], r, h / 2, r * 0.32), { color, k: 0 });
  S.sub(Sh.cylinder([0, h / 2 + h * 0.62, 0], r - 0.05, h / 2 + 0.02, r * 0.26), { k: 0.025 });
  S.add(Sh.cylinder([0, 0.025, 0], r * 0.55, 0.025, 0.012), { color, k: 0.03 });
  if (band) S.paint(Sh.torus([0, h * 0.62, 0], r, 0.04), { color: band, soft: 0.006 });
}

// Anillo elíptico hecho de cápsulas (para el cuello).
function ellipseRing(S, cx, cy, cz, rx, rz, r, color, k = 0.03, segments = 20) {
  let prev = null;
  for (let i = 0; i <= segments; i++) {
    const a = (i / segments) * PI * 2;
    const p = [cx + sin(a) * rx, cy, cz + cos(a) * rz];
    if (prev) S.add(Sh.capsule(prev, p, r), { color, k });
    prev = p;
  }
}

// ── Comida ────────────────────────────────────────────────────

const BUILDERS = {
  manzana: () => sculpted('manzana', 'gloss', (S) => {
    S.add(Sh.sphere([0, 0.44, 0], 0.43), { color: C.apple, k: 0 });
    S.add(Sh.ellipsoid([0, 0.36, 0], [0.47, 0.34, 0.47]), { color: C.apple, k: 0.14 });
    S.sub(Sh.sphere([0, 0.92, 0], 0.12), { k: 0.16 });
    S.paint(Sh.sphere([-0.3, 0.62, 0.3], 0.32), { color: '#ff8f74', soft: 0.18, amount: 0.55 });
    S.paint(Sh.sphere([0, 0.85, 0], 0.16), { color: '#c7d65a', soft: 0.1, amount: 0.5 });
    S.add(Sh.capsule([0, 0.7, 0], [0.04, 1.0, 0], 0.026), { color: C.cocoa, k: 0.02 });
    S.add(Sh.leaf([0.04, 0.9, 0], [0.3, 0, -1.05], 0.3, 0.16, 0.026, 0.2), { color: C.green, k: 0.015 });
  }, { cell: 0.016 }),

  zanahoria: () => sculpted('zanahoria', 'matte', (S) => {
    const t = -0.35;
    const rot = ([x, y, z]) => [x * cos(t) - y * sin(t), x * sin(t) + y * cos(t) + 0.02, z];
    S.add(Sh.cone(rot([0, 0.88, 0]), rot([0, 0.08, 0]), 0.2, 0.04), { color: C.carrot, k: 0 });
    for (const h of [0.28, 0.42, 0.56, 0.7]) {
      const r = 0.04 + (0.2 - 0.04) * ((h - 0.08) / 0.8);
      S.sub(Sh.torus(rot([0, h, 0]), r + 0.004, 0.011, [0, 0, t]), { k: 0.012 });
    }
    for (const [a, len] of [[-0.45, 0.32], [0, 0.4], [0.45, 0.3]]) {
      const base = rot([0, 0.96, 0]);
      S.add(Sh.leaf(base, [0.25, 0, t + a], len, 0.12, 0.03, 0.15), { color: C.green, k: 0.03 });
    }
  }, { cell: 0.016, ground: null }),

  pan: () => sculpted('pan', 'matte', (S) => {
    S.add(Sh.ellipsoid([0, 0.28, 0], [0.56, 0.32, 0.36]), { color: C.crust, k: 0 });
    S.paint(Sh.ellipsoid([0, 0.55, 0], [0.62, 0.2, 0.42]), { color: '#c57d3c', soft: 0.12, amount: 0.75 });
    for (const x of [-0.26, 0, 0.26]) {
      const a = [x - 0.07, 0.6, -0.2];
      const b = [x + 0.07, 0.6, 0.2];
      S.sub(Sh.capsule(a, b, 0.032), { k: 0.035 });
      S.paint(Sh.capsule(a, b, 0.052), { color: '#ffe0aa', soft: 0.02 });
    }
  }),

  arroz: () => group(
    sculpted('arroz-cuenco', 'glaze', (S) => bowl(S, C.blue, 0.5, 0.4, C.white)),
    sculpted('arroz', 'matte', (S) => {
      S.add(Sh.ellipsoid([0, 0.36, 0], [0.43, 0.2, 0.43]), { color: C.white, k: 0 });
      const rng = createRng(77);
      for (let i = 0; i < 46; i++) {
        const a = rng.range(0, PI * 2);
        const r = Math.sqrt(rng.range(0, 1)) * 0.38;
        const y = 0.36 + 0.2 * Math.sqrt(Math.max(0, 1 - (r / 0.43) ** 2));
        S.add(Sh.ellipsoid([cos(a) * r, y, sin(a) * r], [0.034, 0.017, 0.017], [rng.range(0, 3), rng.range(0, 3), 0]), { color: C.white, k: 0.012 });
      }
      S.add(Sh.sphere([0.05, 0.58, 0.04], 0.065), { color: '#e2506a', k: 0.02 });
    }, { cell: 0.012 }),
    sculpted('palillos', 'gloss', (S) => {
      S.add(Sh.capsule([-0.55, 0.47, 0.2], [0.42, 0.6, -0.12], 0.018), { color: C.tan, k: 0 });
      S.add(Sh.capsule([-0.55, 0.44, 0.28], [0.44, 0.56, -0.02], 0.018), { color: C.tan, k: 0 });
    }, { cell: 0.008, ground: null }),
  ),

  sopa: () => group(
    sculpted('sopa-cuenco', 'glaze', (S) => bowl(S, C.cream, 0.52, 0.38, C.pink)),
    sculpted('sopa', 'gloss', (S) => {
      S.add(Sh.cylinder([0, 0.29, 0], 0.46, 0.025, 0.02), { color: '#ffae5c', k: 0 });
      const rng = createRng(31);
      for (let i = 0; i < 7; i++) {
        const a = rng.range(0, PI * 2);
        const r = rng.range(0.08, 0.34);
        const p = [cos(a) * r, 0.315, sin(a) * r];
        if (i % 3 === 0) S.add(Sh.box(p, [0.035, 0.02, 0.035], 0.012, [0, a, 0]), { color: C.carrot, k: 0.01 });
        else if (i % 3 === 1) S.add(Sh.leaf(p, [PI / 2, 0, a], 0.08, 0.05, 0.012, 0), { color: C.green, k: 0.01 });
        else S.add(Sh.capsule(p, [p[0] + 0.1, 0.315, p[2] + 0.04], 0.014), { color: C.cream, k: 0.01 });
      }
    }, { cell: 0.01, ground: null }),
    sculpted('cuchara', 'glaze', (S) => {
      S.add(Sh.capsule([0.18, 0.33, 0.12], [0.62, 0.62, -0.1], 0.025), { color: C.white, k: 0 });
      S.add(Sh.ellipsoid([0.14, 0.32, 0.14], [0.1, 0.03, 0.08], [0, 0.45, -0.3]), { color: C.white, k: 0.03 });
    }, { cell: 0.01, ground: null }),
  ),

  pescado: () => sculpted('pescado', 'gloss', (S) => {
    S.add(Sh.ellipsoid([0, 0.3, 0], [0.48, 0.25, 0.17]), { color: '#89b6ec', k: 0 });
    S.add(Sh.leaf([-0.4, 0.3, 0], [0, 0, PI / 2], 0.36, 0.42, 0.04, 0), { color: '#6a98d8', k: 0.06 });
    S.add(Sh.leaf([-0.05, 0.5, 0], [0, 0, 0.9], 0.22, 0.18, 0.03, 0), { color: '#6a98d8', k: 0.04 });
    S.paint(Sh.ellipsoid([0.02, 0.18, 0], [0.42, 0.14, 0.3]), { color: '#e8f2ff', soft: 0.05 });
    for (const x of [-0.18, 0, 0.18]) S.paint(Sh.capsule([x - 0.06, 0.2, 0.2], [x + 0.08, 0.42, 0.2], 0.02), { color: '#6b4a3a', soft: 0.01, amount: 0.8 });
    S.add(Sh.sphere([0.3, 0.36, 0.11], 0.05), { color: C.white, k: 0.01 });
    S.paint(Sh.sphere([0.32, 0.365, 0.16], 0.026), { color: '#2a1a14', soft: 0.004 });
    S.sub(Sh.sphere([0.5, 0.27, 0], 0.035), { k: 0.03 });
  }, { cell: 0.014 }),

  galleta: () => {
    const cookie = sculpted('galleta', 'matte', (S) => {
      S.add(Sh.cylinder([0, 0, 0], 0.46, 0.06, 0.055), { color: C.tan, k: 0 });
      S.sub(Sh.sphere([0.44, 0, 0.22], 0.14), { k: 0.03 });
      S.paint(Sh.cylinder([0, 0, 0], 0.5, 0.2, 0), { color: '#d79a58', soft: 0.06, amount: 0.25 });
      for (const [x, z] of [[0.15, 0.1], [-0.2, 0.05], [0.02, -0.22], [-0.05, 0.26], [0.22, -0.14], [-0.24, -0.18]]) {
        S.add(Sh.ellipsoid([x, 0.06, z], [0.055, 0.04, 0.05], [0, x * 4, 0]), { color: C.cocoa, k: 0.02 });
      }
    }, { cell: 0.012, ground: null });
    cookie.rotation.x = 0.7;
    cookie.position.y = 0.36;
    return group(cookie);
  },

  helado: () => group(
    sculpted('helado-cono', 'matte', (S) => {
      S.add(Sh.cone([0, 0.03, 0], [0, 0.62, 0], 0.03, 0.25), { color: '#e4ad6a', k: 0 });
      for (const h of [0.2, 0.32, 0.44, 0.56]) {
        const r = 0.03 + 0.22 * ((h - 0.03) / 0.59);
        S.paint(Sh.torus([0, h, 0], r, 0.011), { color: '#c98a45', soft: 0.004 });
      }
      for (let i = 0; i < 10; i++) {
        const a = (i / 10) * PI * 2;
        S.paint(Sh.capsule([sin(a) * 0.03, 0.03, cos(a) * 0.03], [sin(a) * 0.25, 0.62, cos(a) * 0.25], 0.01), { color: '#c98a45', soft: 0.004 });
      }
    }, { cell: 0.012 }),
    sculpted('helado-bolas', 'gloss', (S) => {
      S.add(Sh.sphere([0, 0.72, 0], 0.27), { color: C.pink, k: 0 });
      for (let i = 0; i < 7; i++) {
        const a = (i / 7) * PI * 2 + 0.3;
        S.add(Sh.ellipsoid([sin(a) * 0.24, 0.6 - (i % 2) * 0.04, cos(a) * 0.24], [0.06, 0.09, 0.06]), { color: C.pink, k: 0.06 });
      }
      S.add(Sh.sphere([0, 1.0, 0], 0.21), { color: C.cream, k: 0.06 });
      S.add(Sh.sphere([0.02, 1.25, 0.02], 0.07), { color: '#e5465e', k: 0.01 });
      S.add(Sh.capsule([0.03, 1.3, 0.02], [0.08, 1.42, -0.02], 0.01), { color: C.cocoa, k: 0.01 });
      const rng = createRng(5);
      for (let i = 0; i < 14; i++) {
        const a = rng.range(0, PI * 2);
        const p = rng.range(0.1, 1.2);
        S.paint(Sh.capsule([sin(a) * cos(p) * 0.21, 1.0 + sin(p) * 0.21, cos(a) * cos(p) * 0.21], [sin(a + 0.25) * cos(p) * 0.21, 1.0 + sin(p) * 0.21, cos(a + 0.25) * cos(p) * 0.21], 0.012), { color: ['#ff6f9c', '#7ed6a5', '#9db7ff', '#ffd166'][i % 4], soft: 0.004 });
      }
    }, { cell: 0.012, ground: null }),
  ),

  tarta: () => sculpted('tarta', 'matte', (S) => {
    const axis = [0, 0.28, -0.45];
    const a = 0.42;
    S.add(Sh.cylinder(axis, 0.8, 0.27, 0.03), { color: '#ffe6b8', k: 0 });
    S.sub(Sh.box([axis[0] + cos(a) * 1.0, 0.3, axis[2] - sin(a) * 1.0], [1, 1, 1.5], 0, [0, a, 0]), { k: 0.01 });
    S.sub(Sh.box([axis[0] - cos(a) * 1.0, 0.3, axis[2] - sin(a) * 1.0], [1, 1, 1.5], 0, [0, PI - a, 0]), { k: 0.01 });
    S.paint(Sh.cylinder([0, 0.24, -0.45], 0.9, 0.03, 0), { color: '#ff7f9f', soft: 0.008 });
    S.paint(Sh.cylinder([0, 0.4, -0.45], 0.9, 0.022, 0), { color: '#fff6ea', soft: 0.008 });
    S.paint(Sh.cylinder([0, 0.6, -0.45], 0.9, 0.06, 0), { color: '#fffaf2', soft: 0.01 });
    for (let i = 0; i < 4; i++) {
      S.add(Sh.sphere([-0.18 + i * 0.12, 0.56, 0.18 - Math.abs(i - 1.5) * 0.06], 0.06), { color: '#fffaf2', k: 0.04 });
    }
    S.add(Sh.ellipsoid([0, 0.68, 0.05], [0.09, 0.11, 0.09]), { color: '#e5465e', k: 0.02 });
    S.add(Sh.leaf([0, 0.77, 0.05], [0.3, 0, 0.6], 0.08, 0.05, 0.015, 0), { color: C.green, k: 0.01 });
  }, { cell: 0.014 }),

  festin: () => group(
    sculpted('festin-plato', 'glaze', (S) => {
      S.add(Sh.cylinder([0, 0.03, 0], 0.74, 0.03, 0.03), { color: C.white, k: 0 });
      S.add(Sh.torus([0, 0.06, 0], 0.68, 0.035), { color: C.white, k: 0.04 });
      S.paint(Sh.torus([0, 0.075, 0], 0.68, 0.012), { color: C.pink, soft: 0.004 });
    }, { cell: 0.016 }),
    sculpted('festin', 'matte', (S) => {
      S.add(Sh.ellipsoid([-0.26, 0.2, 0.08], [0.26, 0.15, 0.18]), { color: C.crust, k: 0 });
      S.paint(Sh.ellipsoid([-0.26, 0.32, 0.08], [0.3, 0.08, 0.22]), { color: '#c57d3c', soft: 0.06 });
      S.add(Sh.sphere([0.27, 0.24, 0.12], 0.19), { color: C.apple, k: 0 });
      S.add(Sh.capsule([0.27, 0.4, 0.12], [0.29, 0.5, 0.12], 0.016), { color: C.cocoa, k: 0.01 });
      for (let i = 0; i < 8; i++) {
        const a = (i / 6) * PI * 2;
        const layer = i < 6 ? 0 : 1;
        S.add(Sh.sphere([0.05 + sin(a) * 0.07 * (1 - layer), 0.13 + layer * 0.08, -0.3 + cos(a) * 0.07 * (1 - layer)], 0.06), { color: '#a77fd6', k: 0.01 });
      }
      S.add(Sh.box([-0.18, 0.12, -0.32], [0.12, 0.08, 0.09], 0.02, [0, 0.4, 0]), { color: C.yellow, k: 0 });
      S.sub(Sh.sphere([-0.24, 0.16, -0.24], 0.03), { k: 0.01 });
      S.sub(Sh.sphere([-0.1, 0.1, -0.24], 0.025), { k: 0.01 });
      S.add(Sh.cone([0.12, 0.1, 0.38], [0.36, 0.12, 0.3], 0.045, 0.02), { color: C.carrot, k: 0 });
    }, { cell: 0.014 }),
  ),

  // ── Cuidados ──
  medicina: () => sculpted('medicina', 'glaze', (S) => {
    S.add(Sh.cylinder([0, 0.3, 0], 0.3, 0.3, 0.12), { color: C.white, k: 0 });
    S.add(Sh.cylinder([0, 0.64, 0], 0.17, 0.06, 0.03), { color: C.white, k: 0.05 });
    S.add(Sh.cylinder([0, 0.76, 0], 0.21, 0.09, 0.04), { color: '#f2605c', k: 0.01 });
    for (let i = 0; i < 14; i++) {
      const a = (i / 14) * PI * 2;
      S.sub(Sh.capsule([sin(a) * 0.215, 0.7, cos(a) * 0.215], [sin(a) * 0.215, 0.82, cos(a) * 0.215], 0.012), { k: 0.008 });
    }
    S.paint(Sh.cylinder([0, 0.32, 0], 0.32, 0.17, 0), { color: '#ffe3ec', soft: 0.006 });
    S.paint(Sh.capsule([-0.09, 0.32, 0.3], [0.09, 0.32, 0.3], 0.03), { color: '#f2605c', soft: 0.004 });
    S.paint(Sh.capsule([0, 0.23, 0.3], [0, 0.41, 0.3], 0.03), { color: '#f2605c', soft: 0.004 });
  }, { cell: 0.012 }),

  vitaminas: () => sculpted('vitaminas', 'glaze', (S) => {
    const t = -0.7;
    const end = (d) => [cos(t) * d, 0.34 + sin(t) * d, 0];
    S.add(Sh.capsule(end(-0.24), end(0.24), 0.19), { color: C.white, k: 0 });
    S.paint(Sh.box([cos(t) * 0.3, 0.34 + sin(t) * 0.3, 0], [0.3, 0.6, 0.4], 0, [0, 0, t]), { color: C.yellow, soft: 0.006 });
    S.add(Sh.cylinder([0.38, 0.06, 0.18], 0.12, 0.05, 0.04), { color: C.mint, k: 0 });
    S.sub(Sh.capsule([0.3, 0.11, 0.18], [0.46, 0.11, 0.18], 0.01), { k: 0.006 });
  }, { cell: 0.012 }),

  jabon: () => {
    const bar = sculpted('jabon', 'gloss', (S) => {
      S.add(Sh.box([0, 0.15, 0], [0.42, 0.14, 0.27], 0.13), { color: C.mint, k: 0 });
      S.sub(Sh.sphere([-0.05, 0.3, 0.02], 0.06), { k: 0.02 });
      S.sub(Sh.sphere([0.05, 0.3, 0.02], 0.06), { k: 0.02 });
      S.sub(Sh.cone([0, 0.3, 0.13], [0, 0.3, 0.04], 0.012, 0.075), { k: 0.02 });
    }, { cell: 0.014 });
    const film = new THREE.MeshPhysicalMaterial({ color: '#e8f6ff', roughness: 0.02, transparent: true, opacity: 0.2, iridescence: 1, iridescenceIOR: 1.3, iridescenceThicknessRange: [200, 600], clearcoat: 1, clearcoatRoughness: 0, depthWrite: false });
    const g = group(bar);
    for (const [x, y, z, r] of [[0.24, 0.56, 0.05, 0.16], [-0.14, 0.5, 0.1, 0.1], [0.04, 0.8, 0, 0.07], [-0.3, 0.72, -0.05, 0.05]]) {
      const bubble = new THREE.Mesh(BALL, film);
      bubble.scale.setScalar(r);
      bubble.position.set(x, y, z);
      g.add(bubble);
    }
    return g;
  },

  // ── Juguetes ──
  pelota: () => sculpted('pelota', 'gloss', (S) => {
    S.add(Sh.sphere([0, 0.45, 0], 0.45), { color: C.pink, k: 0 });
    S.paint(Sh.torus([0, 0.45, 0], 0.45, 0.075, [0.45, 0, 0.35]), { color: C.white, soft: 0.006 });
    S.paint(Sh.sphere([0.12, 0.62, 0.38], 0.1), { color: C.yellow, soft: 0.006 });
    S.paint(Sh.sphere([-0.3, 0.3, 0.28], 0.07), { color: C.yellow, soft: 0.006 });
  }, { cell: 0.016 }),

  peonza: () => {
    const top = sculpted('peonza', 'gloss', (S) => {
      S.add(Sh.cone([0, 0.04, 0], [0, 0.46, 0], 0.03, 0.4), { color: C.violet, k: 0 });
      S.add(Sh.cylinder([0, 0.52, 0], 0.42, 0.05, 0.04), { color: C.yellow, k: 0.04 });
      S.add(Sh.ellipsoid([0, 0.58, 0], [0.38, 0.14, 0.38]), { color: C.pink, k: 0.04 });
      S.add(Sh.capsule([0, 0.62, 0], [0, 0.94, 0], 0.045), { color: C.cocoa, k: 0.04 });
      S.paint(Sh.torus([0, 0.3, 0], 0.27, 0.02), { color: C.white, soft: 0.005 });
      for (let i = 0; i < 6; i++) {
        const a = (i / 6) * PI * 2;
        S.paint(Sh.sphere([sin(a) * 0.3, 0.66, cos(a) * 0.3], 0.045), { color: C.white, soft: 0.005 });
      }
    }, { cell: 0.014, ground: null });
    top.rotation.z = 0.22;
    return group(top);
  },

  peluche: () => sculpted('peluche', 'plush', (S) => {
    const fur = '#b98258';
    const light = '#f1d3b0';
    S.add(Sh.ellipsoid([0, 0.32, 0], [0.3, 0.3, 0.26]), { color: fur, k: 0 });
    S.add(Sh.sphere([0, 0.74, 0.02], 0.27), { color: fur, k: 0.08 });
    for (const side of [-1, 1]) {
      S.add(Sh.ellipsoid([side * 0.2, 0.96, 0], [0.1, 0.1, 0.06]), { color: fur, k: 0.03 });
      S.paint(Sh.ellipsoid([side * 0.2, 0.96, 0.05], [0.06, 0.06, 0.04]), { color: '#f0b0a8', soft: 0.008 });
      S.add(Sh.cone([side * 0.24, 0.5, 0.08], [side * 0.34, 0.28, 0.18], 0.08, 0.085), { color: fur, k: 0.04 });
      S.add(Sh.ellipsoid([side * 0.16, 0.1, 0.18], [0.11, 0.1, 0.15]), { color: fur, k: 0.04 });
      S.paint(Sh.ellipsoid([side * 0.16, 0.1, 0.32], [0.07, 0.07, 0.03]), { color: light, soft: 0.008 });
      S.add(Sh.sphere([side * 0.1, 0.79, 0.26], 0.032), { color: '#2a1a14', k: 0.008 });
    }
    S.add(Sh.ellipsoid([0, 0.68, 0.22], [0.12, 0.09, 0.08]), { color: light, k: 0.04 });
    S.add(Sh.ellipsoid([0, 0.72, 0.3], [0.04, 0.028, 0.025]), { color: '#3a2118', k: 0.01 });
    S.paint(Sh.ellipsoid([0, 0.3, 0.24], [0.17, 0.18, 0.08]), { color: light, soft: 0.01 });
    S.add(Sh.torus([0, 0.53, 0.02], 0.16, 0.025), { color: C.pink, k: 0.01 });
    S.add(Sh.ellipsoid([0.07, 0.52, 0.2], [0.06, 0.04, 0.03], [0, 0, 0.4]), { color: C.pink, k: 0.01 });
    S.add(Sh.ellipsoid([-0.07, 0.52, 0.2], [0.06, 0.04, 0.03], [0, 0, -0.4]), { color: C.pink, k: 0.01 });
  }, { cell: 0.012 }),

  cometa: () => {
    const shape = new THREE.Shape();
    shape.moveTo(0, 0.6);
    shape.lineTo(0.36, 0.12);
    shape.lineTo(0, -0.5);
    shape.lineTo(-0.36, 0.12);
    shape.closePath();
    const geo = new THREE.ExtrudeGeometry(shape, { depth: 0.012, bevelEnabled: true, bevelSize: 0.025, bevelThickness: 0.015, bevelSegments: 4 });
    const uv = geo.attributes.uv;
    const pos = geo.attributes.position;
    for (let i = 0; i < pos.count; i++) uv.setXY(i, (pos.getX(i) + 0.4) / 0.8, (pos.getY(i) + 0.54) / 1.18);
    const map = kiteTexture();
    const sail = new THREE.Mesh(geo, new THREE.MeshPhysicalMaterial({ map, roughness: 0.75, sheen: 0.5, sheenColor: new THREE.Color('#ffffff').multiplyScalar(0.3) }));
    sail.castShadow = true;
    const frame = sculpted('cometa-varillas', 'wood', (S) => {
      S.add(Sh.capsule([-0.36, 0.12, 0.035], [0.36, 0.12, 0.035], 0.014), { color: C.tan, k: 0 });
      S.add(Sh.capsule([0, -0.5, 0.035], [0, 0.6, 0.035], 0.014), { color: C.tan, k: 0 });
    }, { cell: 0.008, ground: null });
    const tail = sculpted('cometa-cola', 'fabric', (S) => {
      const pts = [[0, -0.5, 0], [0.06, -0.66, 0], [0.1, -0.82, 0], [0.04, -0.98, 0], [-0.06, -1.12, 0]];
      for (let i = 1; i < pts.length; i++) S.add(Sh.capsule(pts[i - 1], pts[i], 0.008), { color: C.white, k: 0.01 });
      [[0.06, -0.66], [0.1, -0.82], [0.04, -0.98]].forEach(([x, y], i) => {
        const color = [C.pink, C.yellow, C.mint][i];
        S.add(Sh.ellipsoid([x - 0.05, y, 0], [0.055, 0.03, 0.02], [0, 0, 0.3]), { color, k: 0.02 });
        S.add(Sh.ellipsoid([x + 0.05, y, 0], [0.055, 0.03, 0.02], [0, 0, -0.3]), { color, k: 0.02 });
      });
    }, { cell: 0.008, ground: null });
    const g = group(sail, frame, tail);
    g.rotation.z = 0.3;
    g.position.y = 0.95;
    return group(g);
  },
};

function kiteTexture() {
  return canvasTexture(256, 256, (ctx, w, h) => {
    const colors = ['#7fb2f0', '#ff8fb1', '#ffd166', '#7ed6a5'];
    const cx = w / 2;
    const cy = h * (1 - 0.66 / 1.18);
    const quads = [[0, 0, cx, cy], [cx, 0, w, cy], [cx, cy, w, h], [0, cy, cx, h]];
    quads.forEach(([x0, y0, x1, y1], i) => {
      ctx.fillStyle = colors[i];
      ctx.fillRect(x0, y0, x1 - x0, y1 - y0);
    });
    ctx.strokeStyle = 'rgba(255,255,255,0.7)';
    ctx.setLineDash([6, 6]);
    ctx.lineWidth = 3;
    ctx.strokeRect(14, 14, w - 28, h - 28);
  });
}

// ── Casa ──────────────────────────────────────────────────────
// Muebles para el rincón (al lado de la criatura, a la derecha) y para
// debajo de la ventana. Se apoyan en el suelo en y = 0 y miden de verdad.

function pot(S, color, band, { r = 0.19, h = 0.3, soil = '#7a5238' } = {}) {
  S.add(Sh.cone([0, 0.02, 0], [0, h - 0.02, 0], r * 0.76, r), { color, k: 0 });
  S.add(Sh.torus([0, h - 0.01, 0], r * 0.98, 0.028), { color: band ?? color, k: 0.015 });
  S.sub(Sh.cylinder([0, h + 0.08, 0], r * 0.86, 0.1, 0.02), { k: 0.012 });
  S.add(Sh.cylinder([0, h - 0.04, 0], r * 0.88, 0.012, 0.008), { color: soil, k: 0 });
  if (band) S.paint(Sh.cylinder([0, h - 0.05, 0], r * 1.2, 0.05, 0), { color: band, soft: 0.006 });
}

const DECOR = {
  // La planta de siempre.
  planta: () => group(
    sculpted('planta-maceta', 'glaze', (S) => pot(S, '#fffaf2', '#ffd166'), { cell: 0.012 }),
    sculpted('planta-hojas', 'matte', (S) => {
      const rng = createRng(8);
      for (let i = 0; i < 9; i++) {
        const a = (i / 9) * PI * 2 + rng.range(-0.2, 0.2);
        const tilt = i === 0 ? 0.05 : rng.range(0.22, 0.6);
        const dir = V(sin(a) * sin(tilt), cos(tilt), cos(a) * sin(tilt));
        const q = quatTo(dir).multiply(new THREE.Quaternion().setFromAxisAngle(Y, -a + PI / 2));
        const len = rng.range(0.42, 0.62) * (1 - tilt * 0.35);
        const green = ['#4fae7c', '#5fbf88', '#3f9c6c'][i % 3];
        const base = [sin(a) * 0.04, 0.2, cos(a) * 0.04];
        S.add(Sh.leaf(base, q, len, rng.range(0.13, 0.17), 0.024, rng.range(0.1, 0.25)), { color: green, k: 0.02 });
        const tip = V(...base).addScaledVector(dir, len * 0.95);
        S.paint(Sh.capsule([base[0], base[1] + 0.05, base[2]], tip.toArray(), 0.012), { color: '#a6e3b9', soft: 0.008, amount: 0.7 });
      }
    }, { cell: 0.009, aoReach: 0.08 }),
  ),

  cactus: () => group(
    sculpted('cactus-maceta', 'matte', (S) => pot(S, C.terracotta, '#d9724c', { r: 0.2, h: 0.3 }), { cell: 0.012 }),
    sculpted('cactus', 'matte', (S) => {
      S.add(Sh.capsule([0, 0.28, 0], [0, 0.82, 0], 0.15), { color: '#6cc47f', k: 0 });
      for (const [side, y, up] of [[-1, 0.5, 0.2], [1, 0.62, 0.14]]) {
        const elbow = [side * 0.25, y, 0];
        S.add(Sh.capsule([side * 0.08, y - 0.04, 0], elbow, 0.07), { color: '#6cc47f', k: 0.05 });
        S.add(Sh.capsule(elbow, [side * 0.26, y + up, 0], 0.07), { color: '#6cc47f', k: 0.04 });
      }
      for (let i = 0; i < 10; i++) {
        const a = (i / 10) * PI * 2;
        S.paint(Sh.capsule([sin(a) * 0.15, 0.32, cos(a) * 0.15], [sin(a) * 0.15, 0.85, cos(a) * 0.15], 0.012), { color: '#55ad69', soft: 0.01 });
      }
      const rng = createRng(19);
      for (let i = 0; i < 26; i++) {
        const a = rng.range(0, PI * 2);
        const y = rng.range(0.34, 0.86);
        S.paint(Sh.sphere([sin(a) * 0.152, y, cos(a) * 0.152], 0.011), { color: C.cream, soft: 0.003 });
      }
      for (let i = 0; i < 5; i++) {
        const a = (i / 5) * PI * 2;
        S.add(Sh.ellipsoid([sin(a) * 0.045, 0.98, cos(a) * 0.045], [0.04, 0.025, 0.04], [0, 0, 0]), { color: '#ff8fb1', k: 0.015 });
      }
      S.add(Sh.sphere([0, 0.99, 0], 0.025), { color: C.yellow, k: 0.01 });
    }, { cell: 0.01 }),
  ),

  girasol: () => group(
    sculpted('girasol-maceta', 'glaze', (S) => pot(S, '#fffaf2', C.mint), { cell: 0.012 }),
    sculpted('girasol', 'matte', (S) => {
      S.add(Sh.capsule([0, 0.26, 0], [0.02, 1.02, 0.02], 0.024), { color: '#4fae7c', k: 0 });
      S.add(Sh.leaf([0, 0.6, 0], [0.3, 0, 1.0], 0.3, 0.16, 0.02, 0.2), { color: '#5fbf88', k: 0.02 });
      S.add(Sh.leaf([0.01, 0.74, 0], [0.3, 0, -1.0], 0.28, 0.15, 0.02, 0.2), { color: '#4fae7c', k: 0.02 });
      const c = V(0.02, 1.1, 0.06);
      const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(-0.3, 0.25, 0));
      S.add(Sh.ellipsoid(c.toArray(), [0.13, 0.13, 0.055], q), { color: '#8a5a32', k: 0.03 });
      const rng = createRng(3);
      for (let i = 0; i < 30; i++) {
        const a = i * 2.39996;
        const r = Math.sqrt(i / 30) * 0.11;
        const p = V(cos(a) * r, sin(a) * r, 0.052).applyQuaternion(q).add(c);
        S.paint(Sh.sphere(p.toArray(), 0.012 + rng.range(0, 0.004)), { color: '#5e3a22', soft: 0.004 });
      }
      for (let i = 0; i < 14; i++) {
        const a = (i / 14) * PI * 2;
        const p = V(cos(a) * 0.19, sin(a) * 0.19, -0.01).applyQuaternion(q).add(c);
        const pq = q.clone().multiply(new THREE.Quaternion().setFromAxisAngle(V(0, 0, 1), a));
        S.add(Sh.ellipsoid(p.toArray(), [0.085, 0.035, 0.014], pq), { color: i % 2 ? '#ffcf3f' : '#ffd95e', k: 0.015 });
      }
    }, { cell: 0.009 }),
  ),

  lampara: () => {
    const base = sculpted('lampara-pie', 'glaze', (S) => {
      S.add(Sh.cylinder([0, 0.03, 0], 0.19, 0.03, 0.025), { color: C.white, k: 0 });
      S.add(Sh.capsule([0, 0.04, 0], [0, 1.16, 0], 0.022), { color: C.tan, k: 0.03 });
      S.add(Sh.sphere([0, 0.5, 0], 0.035), { color: C.gold, k: 0.02 });
    }, { cell: 0.01 });
    const shade = sculpted('lampara-pantalla', new THREE.MeshPhysicalMaterial({
      vertexColors: true, roughness: 0.9, sheen: 0.6, sheenColor: new THREE.Color('#ffffff').multiplyScalar(0.3),
      emissive: '#ffcf88', emissiveIntensity: 0.32, side: THREE.DoubleSide,
    }), (S) => {
      S.add(Sh.cone([0, 1.1, 0], [0, 1.42, 0], 0.27, 0.15), { color: '#ffe9b8', k: 0 });
      S.sub(Sh.cone([0, 1.06, 0], [0, 1.46, 0], 0.25, 0.13), { k: 0.005 });
      S.paint(Sh.torus([0, 1.12, 0], 0.265, 0.016), { color: '#ff9fb8', soft: 0.004 });
      S.paint(Sh.torus([0, 1.4, 0], 0.155, 0.014), { color: '#ff9fb8', soft: 0.004 });
    }, { cell: 0.009, ao: 0.4, ground: null });
    const bulb = new THREE.Mesh(BALL, new THREE.MeshBasicMaterial({ color: '#fff3d0' }));
    bulb.scale.set(0.07, 0.08, 0.07);
    bulb.position.y = 1.18;
    return group(base, shade, bulb);
  },

  pecera: () => {
    const stand = sculpted('pecera-mesa', 'wood', (S) => {
      S.add(Sh.cylinder([0, 0.02, 0], 0.2, 0.02, 0.015), { color: C.tan, k: 0 });
      S.add(Sh.cone([0, 0.03, 0], [0, 0.53, 0], 0.05, 0.04), { color: C.tan, k: 0.03 });
      S.add(Sh.cylinder([0, 0.555, 0], 0.3, 0.025, 0.02), { color: '#e8bd87', k: 0.03 });
    }, { cell: 0.012 });
    const inside = sculpted('pecera-dentro', 'gloss', (S) => {
      for (const [x, z, color] of [[-0.07, 0.03, C.grey], [0.06, -0.02, C.pink], [0.0, 0.08, '#d9c8b4'], [-0.1, -0.08, '#d9c8b4'], [0.1, 0.07, C.grey]]) {
        S.add(Sh.ellipsoid([x, 0.6, z], [0.045, 0.028, 0.04]), { color, k: 0.01 });
      }
      S.add(Sh.leaf([-0.06, 0.6, -0.06], [0, 0, 0.15], 0.18, 0.06, 0.015, 0.2), { color: C.leaf, k: 0.01 });
      S.add(Sh.leaf([-0.03, 0.6, -0.08], [0, 0, -0.3], 0.14, 0.05, 0.015, 0.2), { color: C.green, k: 0.01 });
      const f = [0.03, 0.8, 0.1];
      S.add(Sh.ellipsoid(f, [0.07, 0.048, 0.035], [0, -0.4, 0]), { color: C.orange, k: 0 });
      S.add(Sh.leaf([f[0] - 0.05, f[1], f[2] + 0.03], [0, -0.4, PI / 2], 0.07, 0.07, 0.012, 0), { color: '#ff7a3a', k: 0.02 });
      S.paint(Sh.sphere([f[0] + 0.04, f[1] + 0.012, f[2] + 0.02], 0.012), { color: '#2a1a14', soft: 0.003 });
    }, { cell: 0.007, ground: null });
    const water = new THREE.Mesh(BALL, new THREE.MeshPhysicalMaterial({ color: '#8cc8f5', roughness: 0.1, transparent: true, opacity: 0.4, depthWrite: false }));
    water.scale.set(0.215, 0.16, 0.215);
    water.position.y = 0.76;
    const glass = new THREE.Mesh(BALL, new THREE.MeshPhysicalMaterial({ color: '#eaf6ff', roughness: 0.04, clearcoat: 1, clearcoatRoughness: 0.02, transparent: true, opacity: 0.2, depthWrite: false, side: THREE.DoubleSide }));
    glass.scale.set(0.25, 0.24, 0.25);
    glass.position.y = 0.82;
    const rim = new THREE.Mesh(new THREE.TorusGeometry(0.15, 0.014, 12, 40), plain('#eaf6ff', 'glaze', { transparent: true, opacity: 0.6 }));
    rim.rotation.x = PI / 2;
    rim.position.y = 1.04;
    glass.renderOrder = water.renderOrder = rim.renderOrder = 2;
    return group(stand, inside, water, glass, rim);
  },

  cojin: () => group(
    sculpted('cojin', 'fabric', (S) => {
      S.add(Sh.box([0, 0.15, 0], [0.4, 0.14, 0.34], 0.13), { color: C.pink, k: 0 });
      S.sub(Sh.sphere([0, 0.32, 0], 0.05), { k: 0.08 });
      S.add(Sh.sphere([0, 0.28, 0], 0.025), { color: '#e2648b', k: 0.01 });
      for (const [x, z] of [[-0.36, -0.3], [0.36, -0.3], [-0.36, 0.3], [0.36, 0.3]]) {
        S.add(Sh.cone([x * 0.98, 0.16, z * 0.98], [x * 1.08, 0.1, z * 1.08], 0.016, 0.03), { color: C.yellow, k: 0.02 });
      }
      S.add(Sh.box([0.08, 0.36, 0.0], [0.19, 0.06, 0.16], 0.06, [0, 0.4, -0.18]), { color: C.mint, k: 0.02 });
      S.paint(Sh.capsule([-0.1, 0.42, -0.08], [0.26, 0.36, 0.1], 0.012), { color: '#ffffff', soft: 0.004, amount: 0.6 });
    }, { cell: 0.012 }),
  ),

  mesita: () => group(
    sculpted('mesita', 'glaze', (S) => {
      S.add(Sh.cylinder([0, 0.46, 0], 0.27, 0.025, 0.022), { color: C.white, k: 0 });
      for (let i = 0; i < 3; i++) {
        const a = (i / 3) * PI * 2 + 0.5;
        S.add(Sh.cone([cos(a) * 0.17, 0.44, sin(a) * 0.17], [cos(a) * 0.2, 0.0, sin(a) * 0.2], 0.026, 0.017), { color: C.tan, k: 0.02 });
      }
      S.add(Sh.cone([0, 0.49, 0], [0, 0.66, 0], 0.075, 0.05), { color: C.blue, k: 0.01 });
      S.add(Sh.torus([0, 0.68, 0], 0.045, 0.015), { color: C.blue, k: 0.02 });
      S.sub(Sh.cylinder([0, 0.72, 0], 0.035, 0.06, 0.01), { k: 0.01 });
      S.paint(Sh.torus([0, 0.56, 0], 0.07, 0.012), { color: C.white, soft: 0.004 });
    }, { cell: 0.009 }),
    sculpted('tulipanes', 'matte', (S) => {
      for (const [x, tilt, height, color] of [[-0.03, 0.3, 0.22, C.pink], [0, 0, 0.28, C.yellow], [0.03, -0.32, 0.22, '#ff7a7a']]) {
        const base = V(x, 0.66, 0);
        const dir = V(-sin(tilt), cos(tilt), 0.05);
        const top = base.clone().addScaledVector(dir, height);
        S.add(Sh.capsule(base.toArray(), top.toArray(), 0.008), { color: C.leaf, k: 0 });
        const head = top.clone().addScaledVector(dir, 0.035);
        S.add(Sh.ellipsoid(head.toArray(), [0.042, 0.058, 0.042], [0, 0, tilt]), { color, k: 0.01 });
        for (let p = 0; p < 3; p++) {
          const a = (p / 3) * PI * 2;
          S.sub(Sh.sphere(head.clone().add(V(sin(a) * 0.012, 0.07, cos(a) * 0.012)).toArray(), 0.02), { k: 0.01 });
        }
      }
      S.add(Sh.leaf([0, 0.68, 0.02], [0.2, 0, 0.5], 0.14, 0.05, 0.012, 0.2), { color: C.green, k: 0.01 });
    }, { cell: 0.006, ground: null }),
  ),

  baul: () => {
    const box = sculpted('baul', 'wood', (S) => {
      S.add(Sh.box([0, 0.18, 0], [0.34, 0.17, 0.2], 0.04), { color: C.tan, k: 0 });
      S.sub(Sh.box([0, 0.3, 0], [0.3, 0.15, 0.16], 0.02), { k: 0.01 });
      for (const y of [0.06, 0.3]) S.paint(Sh.box([0, y, 0], [0.4, 0.025, 0.3], 0), { color: '#b97f4e', soft: 0.004 });
      S.paint(Sh.box([0, 0.18, 0.2], [0.07, 0.17, 0.02], 0), { color: '#c98c58', soft: 0.004 });
    }, { cell: 0.012 });
    const lid = sculpted('baul-tapa', 'wood', (S) => {
      S.add(Sh.box([0, 0.03, 0.2], [0.35, 0.035, 0.2], 0.03), { color: C.tan, k: 0 });
      S.add(Sh.cylinder([0, 0.05, 0.2], 0.2, 0.34, 0.03, [0, 0, PI / 2]), { color: C.tan, k: 0.02 });
      S.sub(Sh.box([0, -0.1, 0.2], [0.4, 0.13, 0.25], 0), { k: 0.005 });
      S.paint(Sh.box([0.32, 0.1, 0.2], [0.025, 0.2, 0.3], 0), { color: '#b97f4e', soft: 0.004 });
      S.paint(Sh.box([-0.32, 0.1, 0.2], [0.025, 0.2, 0.3], 0), { color: '#b97f4e', soft: 0.004 });
    }, { cell: 0.012, ground: null });
    lid.position.set(0, 0.35, -0.2);
    lid.rotation.x = -0.6;
    const toys = sculpted('baul-juguetes', 'gloss', (S) => {
      S.add(Sh.sphere([-0.14, 0.36, 0.02], 0.11), { color: C.pink, k: 0 });
      S.paint(Sh.torus([-0.14, 0.36, 0.02], 0.11, 0.02, [0.5, 0, 0.3]), { color: C.white, soft: 0.004 });
      S.add(Sh.box([0.13, 0.38, 0.03], [0.07, 0.07, 0.07], 0.025, [0.3, 0.5, 0.2]), { color: C.blue, k: 0 });
      S.add(Sh.ellipsoid([0.02, 0.4, -0.06], [0.07, 0.07, 0.05]), { color: C.yellow, k: 0 });
      S.add(Sh.box([0, 0.13, 0.205], [0.035, 0.045, 0.012], 0.01), { color: C.gold, k: 0 });
    }, { cell: 0.01, ground: null });
    return group(box, lid, toys);
  },

  camita: () => sculpted('camita', 'fabric', (S) => {
    S.add(Sh.torus([0, 0.12, 0], 0.34, 0.11, null, 0.9), { color: C.blue, k: 0 });
    S.add(Sh.cylinder([0, 0.05, 0], 0.4, 0.05, 0.04), { color: C.blue, k: 0.05 });
    S.add(Sh.ellipsoid([0, 0.1, 0], [0.3, 0.06, 0.28]), { color: C.cream, k: 0.03 });
    S.add(Sh.box([0.1, 0.16, 0.06], [0.16, 0.012, 0.13], 0.01, [0.05, 0.5, 0.06]), { color: C.yellow, k: 0.03 });
    S.paint(Sh.box([0.1, 0.17, 0.06], [0.16, 0.05, 0.03], 0, [0, 0.5, 0]), { color: '#ffffff', soft: 0.005, amount: 0.7 });
    S.add(Sh.ellipsoid([-0.14, 0.17, -0.1], [0.11, 0.05, 0.08], [0, 0.4, 0]), { color: C.pink, k: 0.03 });
    S.paint(Sh.torus([0, 0.2, 0], 0.34, 0.03), { color: '#9cc4f5', soft: 0.02, amount: 0.6 });
  }, { cell: 0.012 }),
};

// Muestra de papel pintado enmarcada, para la tienda.
function swatch(id) {
  const face = new THREE.Mesh(new THREE.PlaneGeometry(0.9, 0.9), new THREE.MeshStandardMaterial({ map: wallpaperTexture(id, 1.25, 1.25), roughness: 0.9 }));
  face.position.set(0, 0.5, 0.032);
  const frame = sculpted('marco', 'wood', (S) => {
    S.add(Sh.box([0, 0.5, 0], [0.5, 0.5, 0.03], 0.025), { color: '#e8bf8c', k: 0 });
    S.sub(Sh.box([0, 0.5, 0.05], [0.45, 0.45, 0.05], 0.01), { k: 0.01 });
  }, { cell: 0.012, ground: null });
  return group(frame, face);
}

// Alfombra de muestra, inclinada hacia la cámara para que se vea entera.
function rugSample(id) {
  const rug = buildRug(id).object;
  rug.rotation.x = 0.95;
  return group(rug);
}

export function buildDecor(itemId) {
  if (DECOR[itemId]) return DECOR[itemId]();
  if (isWallpaper(itemId)) return swatch(itemId);
  if (isRug(itemId)) return rugSample(itemId);
  return null;
}

// ── Ropa ──────────────────────────────────────────────────────
// Cada prenda recibe los anclajes de la criatura y devuelve el objeto y
// a qué parte se cuelga: 'head' (gira con la cabeza) o 'body'.

function surface(host, yaw, pitch) {
  const cp = cos(pitch);
  const point = V(host.rx * sin(yaw) * cp, host.ry * sin(pitch), host.rz * cos(yaw) * cp);
  const normal = V(point.x / host.rx ** 2, point.y / host.ry ** 2, point.z / host.rz ** 2).normalize();
  return { point: point.add(host.center), normal };
}
const AXIS_Z = V(0, 0, 1);

function onHead(anchors, yaw, pitch, object, sink = 0) {
  const { point, normal } = surface(anchors.face.host, yaw, pitch);
  object.position.copy(point).addScaledVector(normal, -sink);
  object.quaternion.setFromUnitVectors(Y, normal);
  return object;
}

const r2 = (n) => Math.round(n * 100) / 100;
const anchorKey = (a) => [a.hat.y, a.hat.r, a.face.gap, a.face.pitch, a.face.host.rx, a.face.host.ry, a.face.host.rz, a.face.host.center.y, a.face.host.center.z, a.neck.y, a.neck.rx, a.neck.rz].map(r2).join(',');

const WEAR = {
  lazo: (a) => {
    const s = r2(a.hat.r / 0.5);
    const bow = sculpted(`lazo:${s}`, 'gloss', (S) => {
      for (const side of [-1, 1]) {
        S.add(Sh.ellipsoid([side * 0.12 * s, 0.09 * s, 0], [0.12 * s, 0.085 * s, 0.05 * s], [0, 0, side * 0.25]), { color: C.pink, k: 0 });
        S.sub(Sh.ellipsoid([side * 0.13 * s, 0.09 * s, 0.05 * s], [0.06 * s, 0.035 * s, 0.03 * s], [0, 0, side * 0.25]), { k: 0.02 });
        S.add(Sh.cone([side * 0.02 * s, 0.07 * s, 0], [side * 0.09 * s, -0.04 * s, 0.01 * s], 0.035 * s, 0.03 * s), { color: '#ff7aa3', k: 0.02 });
      }
      S.add(Sh.sphere([0, 0.09 * s, 0.01 * s], 0.055 * s), { color: '#ff6f9c', k: 0.02 });
    }, { cell: 0.006 * s, ground: null, aoReach: 0.04 });
    return { parent: 'head', object: onHead(a, 0.55, 1.0, group(bow)) };
  },
  gorro: (a) => {
    const s = r2(a.hat.r / 0.5);
    const hat = sculpted(`gorro:${s}`, 'fabric', (S) => {
      S.add(Sh.ellipsoid([0, 0, 0], [0.42 * s, 0.4 * s, 0.42 * s]), { color: '#f2605c', k: 0 });
      S.sub(Sh.box([0, -0.5 * s, 0], [1, 0.5 * s, 1], 0), { k: 0.02 });
      S.add(Sh.torus([0, 0.05 * s, 0], 0.415 * s, 0.07 * s), { color: '#fff3e8', k: 0.02 });
      for (let i = 0; i < 24; i++) {
        const ang = (i / 24) * PI * 2;
        S.paint(Sh.capsule([sin(ang) * 0.43 * s, 0.13 * s, cos(ang) * 0.43 * s], [sin(ang) * 0.05 * s, 0.42 * s, cos(ang) * 0.05 * s], 0.018 * s), { color: '#e04f4f', soft: 0.006 });
      }
      for (let i = 0; i < 9; i++) {
        const ang = i * 2.4;
        const r = i === 0 ? 0 : 0.06 * s;
        S.add(Sh.sphere([sin(ang) * r, 0.47 * s + (i % 3) * 0.02 * s, cos(ang) * r], 0.065 * s), { color: '#fff3e8', k: 0.03 });
      }
    }, { cell: 0.01 * s, ground: null, aoReach: 0.06 });
    return { parent: 'head', object: group(hat).translateY(a.hat.y - 0.24 * s) };
  },
  gorra: (a) => {
    const s = r2(a.hat.r / 0.5);
    const hat = sculpted(`gorra:${s}`, 'fabric', (S) => {
      S.add(Sh.ellipsoid([0, 0, 0], [0.41 * s, 0.34 * s, 0.41 * s]), { color: C.blue, k: 0 });
      S.sub(Sh.box([0, -0.5 * s, 0], [1, 0.5 * s, 1], 0), { k: 0.015 });
      S.add(Sh.ellipsoid([0, 0.02 * s, 0.38 * s], [0.3 * s, 0.025 * s, 0.24 * s], [-0.12, 0, 0]), { color: C.navy, k: 0.03 });
      S.add(Sh.sphere([0, 0.34 * s, 0], 0.04 * s), { color: C.navy, k: 0.015 });
      for (let i = 0; i < 6; i++) {
        const ang = (i / 6) * PI * 2;
        S.paint(Sh.capsule([sin(ang) * 0.42 * s, 0.02 * s, cos(ang) * 0.42 * s], [0, 0.36 * s, 0], 0.008 * s), { color: '#5d8fd6', soft: 0.004 });
      }
      S.paint(Sh.sphere([0, 0.18 * s, 0.38 * s], 0.08 * s), { color: '#fffaf2', soft: 0.006 });
    }, { cell: 0.009 * s, ground: null, aoReach: 0.06 });
    hat.rotation.x = -0.08;
    return { parent: 'head', object: group(hat).translateY(a.hat.y - 0.2 * s) };
  },
  paja: (a) => {
    const s = r2(a.hat.r / 0.5);
    const hat = sculpted(`paja:${s}`, 'matte', (S) => {
      S.add(Sh.cylinder([0, 0, 0], 0.68 * s, 0.02 * s, 0.018 * s), { color: '#f6d27a', k: 0 });
      S.add(Sh.cylinder([0, 0.13 * s, 0], 0.31 * s, 0.13 * s, 0.07 * s), { color: '#f6d27a', k: 0.05 });
      S.add(Sh.torus([0, 0.06 * s, 0], 0.325 * s, 0.035 * s, null, 1.1), { color: '#ef5a5a', k: 0.01 });
      for (let i = 1; i < 7; i++) S.paint(Sh.torus([0, 0.02 * s, 0], (0.32 + i * 0.055) * s, 0.008 * s), { color: '#e3b860', soft: 0.004 });
      for (let i = 0; i < 4; i++) S.paint(Sh.torus([0, (0.14 + i * 0.03) * s, 0], 0.31 * s, 0.006 * s), { color: '#e3b860', soft: 0.003 });
    }, { cell: 0.009 * s, ground: null, aoReach: 0.05 });
    hat.rotation.x = -0.1;
    return { parent: 'head', object: group(hat).translateY(a.hat.y - 0.08 * s) };
  },
  flor: (a) => {
    const s = r2(a.hat.r / 0.5);
    const flower = sculpted(`flor:${s}`, 'matte', (S) => {
      for (let i = 0; i < 7; i++) {
        const ang = (i / 7) * PI * 2;
        S.add(Sh.ellipsoid([cos(ang) * 0.085 * s, 0.02 * s, sin(ang) * 0.085 * s], [0.07 * s, 0.022 * s, 0.035 * s], [0, -ang, 0.1]), { color: C.white, k: 0.01 });
      }
      S.add(Sh.ellipsoid([0, 0.04 * s, 0], [0.05 * s, 0.035 * s, 0.05 * s]), { color: '#ffc93c', k: 0.02 });
    }, { cell: 0.005 * s, ground: null, aoReach: 0.03 });
    return { parent: 'head', object: onHead(a, -0.6, 0.98, group(flower)) };
  },
  corona: (a) => {
    const s = r2(a.hat.r / 0.5);
    const crown = sculpted(`corona:${s}`, 'metal', (S) => {
      S.add(Sh.cylinder([0, 0.07 * s, 0], 0.24 * s, 0.07 * s, 0.02 * s), { color: '#f6c453', k: 0 });
      S.sub(Sh.cylinder([0, 0.1 * s, 0], 0.21 * s, 0.12 * s, 0.01 * s), { k: 0.01 });
      for (let i = 0; i < 6; i++) {
        const ang = (i / 6) * PI * 2;
        const p = [cos(ang) * 0.225 * s, 0.13 * s, sin(ang) * 0.225 * s];
        S.add(Sh.cone(p, [p[0] * 1.04, 0.24 * s, p[2] * 1.04], 0.05 * s, 0.012 * s), { color: '#f6c453', k: 0.02 });
        S.add(Sh.sphere([p[0] * 1.05, 0.255 * s, p[2] * 1.05], 0.022 * s), { color: '#f6c453', k: 0.01 });
      }
    }, { cell: 0.006 * s, ground: null, aoReach: 0.04 });
    const gems = sculpted(`corona-gemas:${s}`, 'glaze', (S) => {
      for (let i = 0; i < 6; i++) {
        const ang = (i / 6) * PI * 2 + 0.52;
        S.add(Sh.sphere([cos(ang) * 0.245 * s, 0.07 * s, sin(ang) * 0.245 * s], 0.028 * s), { color: i % 2 ? '#f2605c' : '#6f9cf0', k: 0 });
      }
    }, { cell: 0.005 * s, ground: null, ao: 0 });
    return { parent: 'head', object: group(crown, gems).translateY(a.hat.y - 0.06 * s) };
  },
  gafas: (a) => glasses(a, false),
  gafasol: (a) => glasses(a, true),
  bigote: (a) => {
    const s = r2(a.hat.r / 0.5);
    const tache = sculpted(`bigote:${s}`, 'matte', (S) => {
      for (const side of [-1, 1]) {
        S.add(Sh.ellipsoid([side * 0.07 * s, 0, 0], [0.085 * s, 0.035 * s, 0.03 * s], [0, 0, side * -0.25]), { color: C.cocoa, k: 0.02 });
        S.add(Sh.sphere([side * 0.16 * s, 0.03 * s, -0.005 * s], 0.025 * s), { color: C.cocoa, k: 0.04 });
      }
    }, { cell: 0.004 * s, ground: null, aoReach: 0.02 });
    const { point, normal } = surface(a.face.host, 0, a.face.pitch - 0.15);
    tache.position.copy(point).addScaledVector(normal, 0.02 * s);
    tache.quaternion.setFromUnitVectors(AXIS_Z, normal);
    return { parent: 'head', object: group(tache) };
  },
  panuelo: (a) => ({
    parent: 'body',
    object: sculpted(`panuelo:${anchorKey(a)}`, 'fabric', (S) => {
      const { y, rx, rz } = a.neck;
      ellipseRing(S, 0, y, 0, rx, rz, 0.06, '#ef5a5a');
      S.add(Sh.leaf([0, y + 0.02, rz + 0.02], [0.25 + PI, 0, 0], 0.3, 0.32, 0.03, 0.05), { color: '#ef5a5a', k: 0.04 });
      const rng = createRng(4);
      for (let i = 0; i < 9; i++) S.paint(Sh.sphere([rng.range(-0.12, 0.12), y - rng.range(0, 0.22), rz + 0.05], 0.018), { color: C.white, soft: 0.004 });
    }, { cell: 0.01, ground: null, aoReach: 0.05 }),
  }),
  pajarita: (a) => ({
    parent: 'body',
    object: sculpted(`pajarita:${anchorKey(a)}`, 'gloss', (S) => {
      const { y, rx, rz } = a.neck;
      ellipseRing(S, 0, y, 0, rx, rz, 0.022, C.navy);
      const z = rz + 0.05;
      for (const side of [-1, 1]) S.add(Sh.ellipsoid([side * 0.09, y, z], [0.09, 0.065, 0.035], [0, 0, side * 0.1]), { color: C.blue, k: 0.02 });
      S.add(Sh.sphere([0, y, z + 0.01], 0.04), { color: C.navy, k: 0.02 });
      for (const side of [-1, 1]) S.paint(Sh.sphere([side * 0.1, y + 0.02, z + 0.035], 0.016), { color: C.white, soft: 0.004 });
    }, { cell: 0.008, ground: null, aoReach: 0.04 }),
  }),
  bufanda: (a) => ({
    parent: 'body',
    object: sculpted(`bufanda:${anchorKey(a)}`, 'fabric', (S) => {
      const { y, rx, rz } = a.neck;
      ellipseRing(S, 0, y, 0, rx, rz, 0.09, C.mint, 0.05);
      ellipseRing(S, 0, y + 0.09, 0, rx * 0.94, rz * 0.94, 0.05, C.mint, 0.05);
      const x = rx * 0.45;
      S.add(Sh.box([x, y - 0.18, rz + 0.07], [0.075, 0.16, 0.025], 0.022, [0.1, 0, 0.12]), { color: C.mint, k: 0.05 });
      for (const dy of [-0.12, -0.24]) S.paint(Sh.box([x, y + dy, rz + 0.07], [0.2, 0.025, 0.1], 0, [0, 0, 0.12]), { color: C.white, soft: 0.005 });
      for (let i = 0; i < 4; i++) S.add(Sh.capsule([x - 0.05 + i * 0.033 + 0.04, y - 0.33, rz + 0.07], [x - 0.05 + i * 0.033 + 0.045, y - 0.38, rz + 0.07], 0.01), { color: C.white, k: 0.01 });
      for (let i = 0; i < 16; i++) {
        const ang = (i / 16) * PI * 2;
        S.paint(Sh.capsule([sin(ang) * rx * 1.15, y - 0.12, cos(ang) * rz * 1.15], [sin(ang) * rx * 1.15, y + 0.14, cos(ang) * rz * 1.15], 0.012), { color: '#6cc794', soft: 0.008 });
      }
    }, { cell: 0.012, ground: null, aoReach: 0.06 }),
  }),
  cascabel: (a) => {
    const { y, rx, rz } = a.neck;
    const collar = sculpted(`collar:${anchorKey(a)}`, 'gloss', (S) => {
      ellipseRing(S, 0, y, 0, rx, rz, 0.035, '#ef5a5a');
    }, { cell: 0.01, ground: null, aoReach: 0.03 });
    const bell = sculpted('cascabel', 'metal', (S) => {
      S.add(Sh.sphere([0, 0, 0], 0.08), { color: '#f6c453', k: 0 });
      S.add(Sh.torus([0, 0.08, 0], 0.025, 0.01, [PI / 2, 0, 0]), { color: '#f6c453', k: 0.01 });
      S.sub(Sh.box([0, -0.04, 0.06], [0.06, 0.008, 0.05], 0.004), { k: 0.004 });
      S.sub(Sh.sphere([0, -0.04, 0.08], 0.018), { k: 0.004 });
    }, { cell: 0.004, ground: null, aoReach: 0.03 });
    bell.position.set(0, y - 0.09, rz + 0.05);
    return { parent: 'body', object: group(collar, bell) };
  },
};

function glasses(a, dark) {
  const s = r2(a.hat.r / 0.5);
  const centers = [];
  const normals = [];
  for (const side of [-1, 1]) {
    const { point, normal } = surface(a.face.host, side * a.face.gap, a.face.pitch);
    centers.push(point.clone().addScaledVector(normal, 0.05 * s));
    normals.push(normal);
  }
  const frame = sculpted(`gafas:${dark}:${anchorKey(a)}`, 'gloss', (S) => {
    centers.forEach((c, i) => {
      S.add(Sh.torus(c.toArray(), 0.105 * s, 0.017 * s, new THREE.Quaternion().setFromUnitVectors(Y, normals[i])), { color: dark ? '#3a2118' : C.navy, k: 0 });
    });
    const mid = centers[0].clone().lerp(centers[1], 0.5).add(V(0, 0.03 * s, 0.01 * s));
    S.add(Sh.capsule(centers[0].clone().add(V(0.1 * s, 0.03 * s, 0)).toArray(), mid.toArray(), 0.013 * s), { color: dark ? '#3a2118' : C.navy, k: 0.01 });
    S.add(Sh.capsule(mid.toArray(), centers[1].clone().add(V(-0.1 * s, 0.03 * s, 0)).toArray(), 0.013 * s), { color: dark ? '#3a2118' : C.navy, k: 0.01 });
  }, { cell: 0.005 * s, ground: null, aoReach: 0.03 });
  const lensMat = dark
    ? new THREE.MeshPhysicalMaterial({ color: '#2b2240', roughness: 0.08, clearcoat: 1, transparent: true, opacity: 0.88 })
    : new THREE.MeshPhysicalMaterial({ color: '#eef7ff', roughness: 0.04, clearcoat: 1, transparent: true, opacity: 0.18, depthWrite: false });
  const g = group(frame);
  centers.forEach((c, i) => {
    const lens = new THREE.Mesh(BALL, lensMat);
    lens.scale.set(0.1 * s, 0.1 * s, 0.01 * s);
    lens.position.copy(c);
    lens.quaternion.setFromUnitVectors(AXIS_Z, normals[i]);
    g.add(lens);
  });
  return { parent: 'head', object: g };
}

const MANNEQUIN = {
  hat: { y: 0.45, r: 0.5 },
  face: { gap: 0.38, pitch: 0.04, host: { center: new THREE.Vector3(), rx: 0.5, ry: 0.45, rz: 0.47 } },
  neck: { y: 0, rx: 0.34, rz: 0.3 },
};

export function buildWear(itemId, anchors = MANNEQUIN) {
  return WEAR[itemId]?.(anchors) ?? null;
}

export function buildItem(itemId) {
  if (BUILDERS[itemId]) return BUILDERS[itemId]();
  return buildDecor(itemId) ?? buildWear(itemId)?.object ?? null;
}

export const isWear = (itemId) => itemId in WEAR;

// Huevo: color de su ADN y motas del color secundario, pintadas sobre la
// cáscara. `userData.shellColor` es el color de los trozos al romperse.
export function buildEgg(egg) {
  const shell = new THREE.Color().setHSL(egg.hue / 360, Math.min(0.9, (egg.sat + 2) / 100), Math.min(0.88, (egg.light + 6) / 100));
  const spot = new THREE.Color().setHSL(egg.hue2 / 360, Math.min(0.9, (egg.sat + 8) / 100), (egg.light - 4) / 100);
  const key = `egg:${egg.hue}:${egg.sat}:${egg.light}:${egg.hue2}`;
  const mesh = sculpted(key, 'gloss', (S) => {
    S.add(Sh.ellipsoid([0, 0.5, 0], [0.34, 0.47, 0.34]), { color: shell, k: 0 });
    S.add(Sh.ellipsoid([0, 0.33, 0], [0.37, 0.32, 0.37]), { color: shell, k: 0.2 });
    const rng = createRng(egg.hue * 131 + egg.hue2);
    for (let i = 0; i < 10; i++) {
      const t = rng.range(0.55, 2.4);
      const yaw = rng.range(0, PI * 2);
      const y = 0.47 - cos(t) * 0.46;
      const r = 0.36 * sin(t);
      S.paint(Sh.sphere([sin(yaw) * r, y, cos(yaw) * r], rng.range(0.04, 0.075)), { color: spot, soft: 0.008 });
    }
    // Una banda en zigzag alrededor, pegada a la cáscara.
    const at = (ang, y) => S.project(V(sin(ang) * 0.45, y, cos(ang) * 0.45), 3).point.toArray();
    for (let i = 0; i < 14; i++) {
      const a0 = (i / 14) * PI * 2;
      const a1 = ((i + 0.5) / 14) * PI * 2;
      const a2 = ((i + 1) / 14) * PI * 2;
      S.paint(Sh.capsule(at(a0, 0.6), at(a1, 0.53), 0.02), { color: '#ffffff', soft: 0.005, amount: 0.8 });
      S.paint(Sh.capsule(at(a1, 0.53), at(a2, 0.6), 0.02), { color: '#ffffff', soft: 0.005, amount: 0.8 });
    }
  }, { cell: 0.016, groundK: 0.03 });
  const g = group(mesh);
  g.userData.shellColor = shell;
  return g;
}
