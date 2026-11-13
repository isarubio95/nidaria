// Decoración de la habitación: los papeles pintados y las alfombras. Los
// muebles son modelos como los demás artículos y viven en props.js.
//
// Cada papel es una baldosa de 96 px que se repite cada medio metro. Las
// alfombras son capas planas con el mismo contorno, de mayor a menor.

import * as THREE from 'three';

// Lo que hay en cada sitio si el jugador no ha puesto nada.
export const BASE_DECOR = { pared: 'papel-lunares', alfombra: 'alfombra-rosa', rincon: 'planta', ventana: null };

export const TILE = 96;
export const TILE_METERS = 0.5;

function dots(ctx, points, radius) {
  for (const [x, y] of points) {
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    ctx.fill();
  }
}

function star(ctx, x, y, outer, inner) {
  ctx.beginPath();
  for (let i = 0; i < 10; i++) {
    const r = i % 2 ? inner : outer;
    const a = -Math.PI / 2 + (i * Math.PI) / 5;
    ctx.lineTo(x + Math.cos(a) * r, y + Math.sin(a) * r);
  }
  ctx.closePath();
  ctx.fill();
}

function daisy(ctx, x, y, petal, center) {
  ctx.fillStyle = petal;
  for (let i = 0; i < 5; i++) {
    const a = (i / 5) * Math.PI * 2 - Math.PI / 2;
    ctx.beginPath();
    ctx.ellipse(x + Math.cos(a) * 6, y + Math.sin(a) * 6, 5.5, 3.6, a, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.fillStyle = center;
  dots(ctx, [[x, y]], 3.4);
}

// Pinta una baldosa de 96 × 96. Todas se repiten sin costuras.
const WALLPAPERS = {
  'papel-lunares': (ctx, w, h) => {
    ctx.fillStyle = '#bfe3ff';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#cdeaff';
    dots(ctx, [[w / 2, h / 2], [0, 0], [w, 0], [0, h], [w, h]], 7);
  },
  'papel-rayas': (ctx, w, h) => {
    ctx.fillStyle = '#e6f7ec';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#c3ead2';
    ctx.fillRect(0, 0, 24, h);
    ctx.fillRect(48, 0, 24, h);
    ctx.fillStyle = '#fffdf8';
    ctx.fillRect(34, 0, 4, h);
    ctx.fillRect(82, 0, 4, h);
  },
  'papel-vichy': (ctx, w, h) => {
    ctx.fillStyle = '#fff4f7';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = 'rgba(255, 143, 177, 0.3)';
    ctx.fillRect(0, 0, w, 24);
    ctx.fillRect(0, 48, w, 24);
    ctx.fillRect(0, 0, 24, h);
    ctx.fillRect(48, 0, 24, h);
  },
  'papel-flores': (ctx, w, h) => {
    ctx.fillStyle = '#fff6e6';
    ctx.fillRect(0, 0, w, h);
    daisy(ctx, 24, 24, '#ffffff', '#ffd166');
    daisy(ctx, 72, 72, '#ffc7d8', '#ffd166');
    ctx.fillStyle = '#b8e6c8';
    dots(ctx, [[72, 22], [24, 70]], 3);
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
  },
  'papel-estrellas': (ctx, w, h) => {
    ctx.fillStyle = '#ddd3f6';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#ffd166';
    star(ctx, 24, 28, 9, 4);
    star(ctx, 72, 76, 9, 4);
    ctx.fillStyle = '#f3efff';
    dots(ctx, [[70, 24], [22, 72], [48, 50]], 2.6);
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
  'alfombra-rosa': { edge: 'disc', layers: [[1, '#ff8fb1'], [0.88, '#ffabc5'], [0.78, '#ff8fb1']] },
  'alfombra-menta': { edge: 'disc', layers: [[1, '#7ed6a5'], [0.88, '#a8e6c1'], [0.78, '#7ed6a5']] },
  'alfombra-flor': { edge: 'flor', layers: [[1, '#ffd56b'], [0.82, '#fff1c4'], [0.66, '#ffc44d']] },
  'alfombra-arcoiris': {
    edge: 'disc',
    layers: [[1, '#ff9aa8'], [0.86, '#ffc78f'], [0.72, '#fff0a3'], [0.58, '#a8e6b8'], [0.44, '#a9cdf5']],
  },
  'alfombra-nube': { edge: 'nube', layers: [[1, '#b5d8fb'], [0.84, '#eef7ff'], [0.7, '#c9e4fd']] },
};

export const isRug = (id) => id in RUGS;

// De noche las alfombras se tiñen como la de siempre (rosa → #8d5884).
export const RUG_NIGHT = new THREE.Color('#8d9dbe');

const outlines = new Map();

// Contorno plano en el suelo, con radio 1 y la normal hacia arriba.
function outline(edge) {
  if (!outlines.has(edge)) {
    const shape = new THREE.Shape();
    const steps = 96;
    for (let i = 0; i < steps; i++) {
      const a = (i / steps) * Math.PI * 2;
      const r = EDGES[edge](a);
      if (i === 0) shape.moveTo(Math.cos(a) * r, Math.sin(a) * r);
      else shape.lineTo(Math.cos(a) * r, Math.sin(a) * r);
    }
    shape.closePath();
    const geometry = new THREE.ShapeGeometry(shape);
    geometry.rotateX(-Math.PI / 2);
    geometry.userData.shared = true;
    outlines.set(edge, geometry);
  }
  return outlines.get(edge);
}

// La alfombra entera. Devuelve el grupo y sus materiales con el color de
// día, para que la habitación los oscurezca de noche.
export function buildRug(id) {
  const spec = RUGS[id] ?? RUGS['alfombra-rosa'];
  const group = new THREE.Group();
  const materials = [];
  spec.layers.forEach(([scale, color], index) => {
    const material = new THREE.MeshBasicMaterial({ color });
    const mesh = new THREE.Mesh(outline(spec.edge), material);
    mesh.scale.set(0.84 * scale, 1, 0.52 * scale - (1 - scale) * 0.1);
    mesh.position.y = 0.025 + index * 0.002;
    group.add(mesh);
    materials.push({ material, day: material.color.clone() });
  });
  return { object: group, materials };
}
