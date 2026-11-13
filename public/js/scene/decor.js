// Decoración de la habitación: los papeles pintados y las alfombras. Los
// muebles son modelos como los demás artículos y viven en props.js.
//
// Cada papel es una baldosa de 256 px que se repite cada medio metro,
// con grano de papel. Las alfombras son una pieza mullida con el canto
// redondeado y los anillos de color tejidos en su textura.

import * as THREE from 'three';
import { createRng } from '/shared/rng.js';

// Lo que hay en cada sitio si el jugador no ha puesto nada.
export const BASE_DECOR = { pared: 'papel-lunares', alfombra: 'alfombra-rosa', rincon: 'planta', ventana: null };

export const TILE = 256;
export const TILE_METERS = 0.5;
// Los dibujos se pensaron en una baldosa de 96: se escalan con esto.
const U = TILE / 96;

function dots(ctx, points, radius) {
  for (const [x, y] of points) {
    ctx.beginPath();
    ctx.arc(x * U, y * U, radius * U, 0, Math.PI * 2);
    ctx.fill();
  }
}

function star(ctx, x, y, outer, inner) {
  ctx.beginPath();
  for (let i = 0; i < 10; i++) {
    const r = (i % 2 ? inner : outer) * U;
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    ctx.lineTo(x * U + Math.cos(a) * r, y * U + Math.sin(a) * r);
  }
  ctx.closePath();
  ctx.fill();
}

function daisy(ctx, x, y, petal, center) {
  ctx.fillStyle = petal;
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2 - Math.PI / 2;
    ctx.beginPath();
    ctx.ellipse((x + Math.cos(a) * 6) * U, (y + Math.sin(a) * 6) * U, 5.5 * U, 3.6 * U, a, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.fillStyle = center;
  dots(ctx, [[x, y]], 3.4);
}

// Grano de papel: motas claras y oscuras casi invisibles. Se repite sin
// costuras porque cada mota se pinta también al otro lado del borde.
function grain(ctx, w, h, seed, strength = 1) {
  const rng = createRng(seed);
  for (let i = 0; i < 2600; i++) {
    const x = rng.range(0, w);
    const y = rng.range(0, h);
    const light = rng.chance(0.5);
    ctx.fillStyle = light ? `rgba(255,255,255,${0.09 * strength})` : `rgba(90,60,40,${0.035 * strength})`;
    const s = rng.range(0.6, 1.8);
    ctx.fillRect(x, y, s, s);
  }
}

const rect = (ctx, x, y, w, h) => ctx.fillRect(x * U, y * U, w * U, h * U);

// Pinta una baldosa de 256 × 256. Todas se repiten sin costuras.
const WALLPAPERS = {
  'papel-lunares': (ctx, w, h) => {
    ctx.fillStyle = '#bfe3ff';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#d4edff';
    dots(ctx, [[48, 48], [0, 0], [96, 0], [0, 96], [96, 96]], 7);
    ctx.fillStyle = '#e3f3ff';
    dots(ctx, [[46.5, 46.5], [-1.5, -1.5], [94.5, -1.5], [-1.5, 94.5], [94.5, 94.5]], 3.2);
    grain(ctx, w, h, 11);
  },
  'papel-rayas': (ctx, w, h) => {
    ctx.fillStyle = '#e6f7ec';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#c8ecd6';
    rect(ctx, 0, 0, 24, 96);
    rect(ctx, 48, 0, 24, 96);
    ctx.fillStyle = '#fffdf8';
    rect(ctx, 34, 0, 4, 96);
    rect(ctx, 82, 0, 4, 96);
    grain(ctx, w, h, 12);
  },
  'papel-vichy': (ctx, w, h) => {
    ctx.fillStyle = '#fff4f7';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = 'rgba(255, 143, 177, 0.28)';
    rect(ctx, 0, 0, 96, 24);
    rect(ctx, 0, 48, 96, 24);
    rect(ctx, 0, 0, 24, 96);
    rect(ctx, 48, 0, 24, 96);
    grain(ctx, w, h, 13);
  },
  'papel-flores': (ctx, w, h) => {
    ctx.fillStyle = '#fff6e6';
    ctx.fillRect(0, 0, w, h);
    daisy(ctx, 24, 24, '#ffffff', '#ffd166');
    daisy(ctx, 72, 72, '#ffc7d8', '#ffd166');
    ctx.fillStyle = '#b8e6c8';
    dots(ctx, [[72, 22], [24, 70]], 3);
    grain(ctx, w, h, 14);
  },
  'papel-olas': (ctx, w, h) => {
    ctx.fillStyle = '#e1f1ff';
    ctx.fillRect(0, 0, w, h);
    // Escamas de olas: cada fila tapa la parte baja de la de arriba, así
    // que solo asoman los arcos. Cuatro filas por baldosa.
    for (let row = -2; row <= 6; row++) {
      const y = row * 24;
      const shift = (((row % 2) + 2) % 2) * 48;
      for (let k = -1; k <= 1; k++) {
        for (const [r, color] of [[48, '#b7dcfa'], [40, '#e1f1ff'], [30, '#cbe6fc'], [22, '#e1f1ff']]) {
          ctx.fillStyle = color;
          dots(ctx, [[shift + k * 96, y]], r);
        }
      }
    }
    grain(ctx, w, h, 15);
  },
  'papel-estrellas': (ctx, w, h) => {
    ctx.fillStyle = '#ddd3f6';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#ffd166';
    star(ctx, 24, 28, 9, 4);
    star(ctx, 72, 76, 9, 4);
    ctx.fillStyle = '#f3efff';
    dots(ctx, [[70, 24], [22, 72], [48, 50]], 2.6);
    grain(ctx, w, h, 16);
  },
};

const canvases = new Map();

function wallpaperCanvas(id) {
  if (!canvases.has(id)) {
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = TILE;
    (WALLPAPERS[id] ?? WALLPAPERS['papel-lunares'])(canvas.getContext('2d'), TILE, TILE);
    canvases.set(id, canvas);
  }
  return canvases.get(id);
}

export const isWallpaper = (id) => id in WALLPAPERS;

// Textura de un papel que se repite. Cada llamada da una textura nueva
// (con su propia repetición) sobre el mismo dibujo.
export function wallpaperTexture(id, repeatX = 1, repeatY = 1) {
  const texture = new THREE.CanvasTexture(wallpaperCanvas(id));
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = texture.wrapT = THREE.RepeatWrapping;
  texture.repeat.set(repeatX, repeatY);
  return texture;
}

// ── Alfombras ──

// `edge` da el radio del contorno (1 = círculo) según el ángulo.
const EDGES = {
  disc: () => 1,
  flor: (a) => 0.84 + 0.16 * Math.sqrt(Math.abs(Math.cos(5 * a))),
  nube: (a) => (0.83 + 0.17 * Math.sqrt(Math.abs(Math.cos(3.5 * a)))) * (1 + 0.05 * Math.sin(a)),
};

// `layers` van de fuera adentro; `scale` es el tamaño de cada capa.
const RUGS = {
  'alfombra-rosa': { edge: 'disc', layers: [[1, '#ff8fb1'], [0.88, '#ffb3c9'], [0.78, '#ff94b5']] },
  'alfombra-menta': { edge: 'disc', layers: [[1, '#7ed6a5'], [0.88, '#aee8c5'], [0.78, '#84d9aa']] },
  'alfombra-flor': { edge: 'flor', layers: [[1, '#ffd56b'], [0.82, '#fff1c4'], [0.66, '#ffc44d']] },
  'alfombra-arcoiris': {
    edge: 'disc',
    layers: [[1, '#ff9aa8'], [0.86, '#ffc78f'], [0.72, '#fff0a3'], [0.58, '#a8e6b8'], [0.44, '#a9cdf5']],
  },
  'alfombra-nube': { edge: 'nube', layers: [[1, '#b5d8fb'], [0.84, '#eef7ff'], [0.7, '#c9e4fd']] },
};

export const isRug = (id) => id in RUGS;

// Margen del canto mullido por fuera del contorno (en radios).
const BEVEL = 0.05;
const REACH = 1 + BEVEL;

function outlineShape(edge, scale = 1) {
  const shape = new THREE.Shape();
  const steps = 128;
  for (let i = 0; i < steps; i++) {
    const a = (i / steps) * Math.PI * 2;
    const r = EDGES[edge](a) * scale;
    if (i === 0) shape.moveTo(Math.cos(a) * r, Math.sin(a) * r);
    else shape.lineTo(Math.cos(a) * r, Math.sin(a) * r);
  }
  shape.closePath();
  return shape;
}

const rugTextures = new Map();

// Textura de la cara de arriba: los anillos con una costura entre ellos y
// motas de fibra.
function rugTexture(id, spec) {
  if (rugTextures.has(id)) return rugTextures.get(id);
  const size = 512;
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext('2d');
  const toPx = size / 2 / REACH;
  ctx.fillStyle = spec.layers[0][1];
  ctx.fillRect(0, 0, size, size);
  const path = (scale) => {
    ctx.beginPath();
    for (let i = 0; i <= 160; i++) {
      const a = (i / 160) * Math.PI * 2;
      const r = EDGES[spec.edge](a) * scale * toPx;
      // y se invierte: la cara de arriba se ve desde +y tras girarla.
      ctx.lineTo(size / 2 + Math.cos(a) * r, size / 2 - Math.sin(a) * r);
    }
    ctx.closePath();
  };
  spec.layers.forEach(([scale, color], index) => {
    path(scale);
    ctx.fillStyle = color;
    ctx.fill();
    if (index > 0) {
      ctx.lineWidth = 3;
      ctx.strokeStyle = 'rgba(120, 70, 60, 0.16)';
      ctx.stroke();
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.3)';
      path(scale - 0.012);
      ctx.stroke();
    }
  });
  const rng = createRng(id.length * 977 + 13);
  for (let i = 0; i < 9000; i++) {
    const light = rng.chance(0.5);
    ctx.fillStyle = light ? 'rgba(255,255,255,0.1)' : 'rgba(90,50,40,0.07)';
    const x = rng.range(0, size);
    const y = rng.range(0, size);
    ctx.fillRect(x, y, rng.range(1, 2.5), rng.range(1, 2.5));
  }
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.anisotropy = 8;
  rugTextures.set(id, texture);
  return texture;
}

// Relieve de fibra compartido por todas las alfombras.
let fiber = null;
function fiberTexture() {
  if (!fiber) {
    const size = 256;
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = size;
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#808080';
    ctx.fillRect(0, 0, size, size);
    const rng = createRng(4242);
    for (let i = 0; i < 7000; i++) {
      const v = Math.round(rng.range(70, 200));
      ctx.fillStyle = `rgb(${v},${v},${v})`;
      ctx.beginPath();
      ctx.arc(rng.range(0, size), rng.range(0, size), rng.range(0.6, 1.6), 0, Math.PI * 2);
      ctx.fill();
    }
    fiber = new THREE.CanvasTexture(canvas);
    fiber.wrapS = fiber.wrapT = THREE.RepeatWrapping;
    fiber.repeat.set(6, 6);
  }
  return fiber;
}

const rugGeometries = new Map();

// Pieza mullida: el contorno extruido con un canto redondo grueso. La
// cara de arriba lleva la textura de anillos.
function rugGeometry(edge) {
  if (!rugGeometries.has(edge)) {
    const geometry = new THREE.ExtrudeGeometry(outlineShape(edge), {
      depth: 0.004,
      bevelEnabled: true,
      bevelThickness: 0.011,
      bevelSize: BEVEL,
      bevelSegments: 5,
      curveSegments: 4,
    });
    const pos = geometry.attributes.position;
    const uv = geometry.attributes.uv;
    for (let i = 0; i < pos.count; i++) {
      uv.setXY(i, pos.getX(i) / (2 * REACH) + 0.5, pos.getY(i) / (2 * REACH) + 0.5);
    }
    // El grosor (z de la extrusión) pasa a ser la altura.
    geometry.rotateX(-Math.PI / 2);
    geometry.translate(0, 0.011, 0);
    geometry.computeVertexNormals();
    geometry.userData.shared = true;
    rugGeometries.set(edge, geometry);
  }
  return rugGeometries.get(edge);
}

// La alfombra entera, lista para poner en el suelo. Su material es
// propio (la textura y la malla se comparten).
export function buildRug(id) {
  const spec = RUGS[id] ?? RUGS['alfombra-rosa'];
  const key = RUGS[id] ? id : 'alfombra-rosa';
  const material = new THREE.MeshPhysicalMaterial({
    map: rugTexture(key, spec),
    roughness: 0.96,
    sheen: 1,
    sheenRoughness: 0.42,
    sheenColor: new THREE.Color('#ffffff').multiplyScalar(0.42),
    bumpMap: fiberTexture(),
    bumpScale: 0.9,
  });
  const mesh = new THREE.Mesh(rugGeometry(spec.edge), material);
  mesh.scale.set(0.84, 1, 0.52);
  mesh.receiveShadow = true;
  const group = new THREE.Group();
  group.add(mesh);
  return { object: group, material };
}
