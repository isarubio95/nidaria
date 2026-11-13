// Modelos 3D de los artículos de la tienda y del huevo, hechos con
// primitivas. Los mismos modelos sirven para la escena y para las
// miniaturas de la interfaz.

import * as THREE from 'three';
import { createRng } from '/shared/rng.js';
import { wallpaperTexture, buildRug, isWallpaper, isRug } from './decor.js';

const cache = new Map();
function mat(color, options = {}) {
  const key = color + JSON.stringify(options);
  if (!cache.has(key)) cache.set(key, new THREE.MeshStandardMaterial({ color, roughness: 0.62, ...options }));
  return cache.get(key);
}

const SPHERE = new THREE.SphereGeometry(1, 28, 20);
SPHERE.userData.shared = true;

function ball(color, rx, ry = rx, rz = rx, options) {
  const mesh = new THREE.Mesh(SPHERE, mat(color, options));
  mesh.scale.set(rx, ry, rz);
  mesh.castShadow = true;
  return mesh;
}
function cyl(color, top, bottom, height, options, segments = 28) {
  const mesh = new THREE.Mesh(new THREE.CylinderGeometry(top, bottom, height, segments), mat(color, options));
  mesh.castShadow = true;
  return mesh;
}
function cone(color, radius, height, options) {
  const mesh = new THREE.Mesh(new THREE.ConeGeometry(radius, height, 22), mat(color, options));
  mesh.castShadow = true;
  return mesh;
}
function box(color, x, y, z, options) {
  const mesh = new THREE.Mesh(new THREE.BoxGeometry(x, y, z), mat(color, options));
  mesh.castShadow = true;
  return mesh;
}
function ring(color, radius, tube, options) {
  const mesh = new THREE.Mesh(new THREE.TorusGeometry(radius, tube, 12, 36), mat(color, options));
  mesh.castShadow = true;
  return mesh;
}
function at(mesh, x = 0, y = 0, z = 0) {
  mesh.position.set(x, y, z);
  return mesh;
}
function group(...children) {
  const g = new THREE.Group();
  if (children.length) g.add(...children);
  return g;
}

// Cuenco: media esfera hueca apoyada en el suelo.
function bowl(color, radius = 0.5) {
  const points = [];
  for (let i = 0; i <= 12; i++) {
    const a = (i / 12) * (Math.PI / 2);
    points.push(new THREE.Vector2(Math.sin(a) * radius, radius * 0.62 * (1 - Math.cos(a))));
  }
  points.push(new THREE.Vector2(radius * 0.94, radius * 0.62));
  const mesh = new THREE.Mesh(new THREE.LatheGeometry(points, 30), mat(color, { side: THREE.DoubleSide }));
  mesh.castShadow = true;
  return mesh;
}

const C = {
  red: '#f2605c', orange: '#ff9f43', yellow: '#ffd166', green: '#6cc47f', leaf: '#4fae7c',
  blue: '#7fb2f0', navy: '#4d6fb8', pink: '#ff8fb1', cream: '#fff3dc', white: '#fffdf8',
  tan: '#e2b279', brown: '#a8744c', cocoa: '#6b4632', grey: '#b8c4cc', gold: '#f2c14e', mint: '#7ed6a5', violet: '#a98ee0',
};

const BUILDERS = {
  manzana: () => group(
    at(ball(C.red, 0.46, 0.42, 0.46), 0, 0.42, 0),
    at(cyl(C.cocoa, 0.025, 0.03, 0.2), 0.02, 0.9, 0),
    at(ball(C.leaf, 0.14, 0.03, 0.08), 0.15, 0.94, 0),
  ),
  zanahoria: () => {
    const body = cone(C.orange, 0.2, 0.86);
    body.rotation.z = Math.PI;
    const g = group(at(body, 0, 0.43, 0));
    for (const [x, tilt] of [[-0.09, 0.35], [0, 0], [0.09, -0.35]]) {
      const leaf = at(ball(C.leaf, 0.05, 0.18, 0.04), x, 0.98, 0);
      leaf.rotation.z = tilt;
      g.add(leaf);
    }
    g.rotation.z = -0.35;
    return group(g);
  },
  pan: () => {
    const g = group(at(ball(C.tan, 0.56, 0.3, 0.34), 0, 0.3, 0));
    for (const x of [-0.24, 0, 0.24]) {
      const cut = at(ball(C.cream, 0.035, 0.03, 0.2), x, 0.585, 0);
      cut.rotation.y = 0.45;
      g.add(cut);
    }
    return g;
  },
  arroz: () => group(
    bowl(C.blue, 0.5),
    at(ball(C.white, 0.42, 0.24, 0.42), 0, 0.32, 0),
    at(ball(C.cocoa, 0.07, 0.03, 0.07), 0, 0.555, 0),
  ),
  sopa: () => group(
    bowl(C.cream, 0.52),
    at(cyl(C.orange, 0.46, 0.46, 0.04), 0, 0.29, 0),
    at(ball(C.leaf, 0.06, 0.02, 0.04), -0.15, 0.32, 0.08),
    at(ball(C.leaf, 0.05, 0.02, 0.04), 0.14, 0.32, -0.1),
    at(ball(C.white, 0.07, 0.02, 0.07), 0.04, 0.32, 0.18),
  ),
  pescado: () => {
    const tail = cone(C.navy, 0.2, 0.3);
    tail.rotation.z = -Math.PI / 2;
    tail.scale.z = 0.3;
    return group(
      at(ball(C.blue, 0.5, 0.26, 0.16), 0, 0.3, 0),
      at(tail, -0.56, 0.3, 0),
      at(ball(C.white, 0.06, 0.06, 0.03), 0.28, 0.36, 0.14),
      at(ball(C.cocoa, 0.03, 0.03, 0.02), 0.29, 0.36, 0.17),
      at(ball(C.navy, 0.1, 0.05, 0.03), 0, 0.2, 0.15),
    );
  },
  galleta: () => {
    const g = group(at(cyl(C.tan, 0.46, 0.46, 0.12), 0, 0.06, 0));
    for (const [x, z] of [[0.15, 0.1], [-0.2, 0.05], [0.02, -0.22], [-0.05, 0.26], [0.26, -0.14]]) {
      g.add(at(ball(C.cocoa, 0.06, 0.035, 0.06), x, 0.13, z));
    }
    g.rotation.x = 0.7;
    return group(at(g, 0, 0.36, 0));
  },
  helado: () => {
    const wafer = cone(C.tan, 0.24, 0.6);
    wafer.rotation.z = Math.PI;
    return group(
      at(wafer, 0, 0.3, 0),
      at(ball(C.pink, 0.29), 0, 0.72, 0),
      at(ball(C.cream, 0.22), 0, 1.06, 0),
      at(ball(C.red, 0.07), 0, 1.3, 0),
    );
  },
  tarta: () => {
    const slice = (color, height, y) => {
      const mesh = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 0.8, height, 20, 1, false, -0.42, 0.84), mat(color));
      mesh.castShadow = true;
      return at(mesh, 0, y, -0.45);
    };
    return group(
      slice(C.cream, 0.22, 0.11), slice(C.pink, 0.08, 0.26), slice(C.cream, 0.2, 0.4), slice(C.white, 0.07, 0.535),
      at(ball(C.red, 0.1, 0.11, 0.1), 0, 0.66, 0.12),
    );
  },
  festin: () => group(
    at(cyl(C.white, 0.72, 0.6, 0.06), 0, 0.03, 0),
    at(ball(C.tan, 0.28, 0.16, 0.2), -0.24, 0.2, 0.08),
    at(ball(C.red, 0.2, 0.19, 0.2), 0.26, 0.24, 0.1),
    at(ball(C.green, 0.15), 0.06, 0.2, -0.28),
    at(ball(C.orange, 0.13), -0.14, 0.18, -0.3),
    at(ball(C.yellow, 0.1, 0.08, 0.1), 0.02, 0.14, 0.34),
  ),

  medicina: () => group(
    at(cyl(C.white, 0.3, 0.3, 0.62), 0, 0.31, 0),
    at(cyl(C.red, 0.2, 0.2, 0.2), 0, 0.72, 0),
    at(box(C.red, 0.26, 0.08, 0.02), 0, 0.32, 0.3),
    at(box(C.red, 0.08, 0.26, 0.02), 0, 0.32, 0.3),
  ),
  vitaminas: () => {
    const half = (color, y) => {
      const mesh = new THREE.Mesh(new THREE.CapsuleGeometry(0.2, 0.3, 8, 20), mat(color, { roughness: 0.3 }));
      mesh.castShadow = true;
      return at(mesh, 0, y, 0);
    };
    const g = group(half(C.yellow, 0.18), half(C.white, -0.18));
    g.rotation.z = -0.7;
    return group(at(g, 0, 0.42, 0));
  },
  jabon: () => group(
    at(ball(C.mint, 0.46, 0.2, 0.3), 0, 0.2, 0),
    at(ball(C.white, 0.16, 0.16, 0.16, { transparent: true, opacity: 0.6, roughness: 0.1 }), 0.24, 0.56, 0.05),
    at(ball(C.white, 0.1, 0.1, 0.1, { transparent: true, opacity: 0.6, roughness: 0.1 }), -0.14, 0.5, 0.1),
    at(ball(C.white, 0.07, 0.07, 0.07, { transparent: true, opacity: 0.6, roughness: 0.1 }), 0.04, 0.78, 0),
  ),

  pelota: () => {
    const band = cyl(C.white, 0.455, 0.455, 0.2);
    const star = ball(C.yellow, 0.14, 0.14, 0.05);
    return group(at(ball(C.pink, 0.45), 0, 0.45, 0), at(band, 0, 0.45, 0), at(star, 0, 0.45, 0.44));
  },
  peonza: () => {
    const lower = cone(C.violet, 0.42, 0.5);
    lower.rotation.z = Math.PI;
    const g = group(
      at(lower, 0, 0.25, 0),
      at(cyl(C.yellow, 0.42, 0.42, 0.1), 0, 0.55, 0),
      at(cone(C.pink, 0.4, 0.22), 0, 0.71, 0),
      at(cyl(C.cocoa, 0.045, 0.045, 0.3), 0, 0.92, 0),
    );
    g.rotation.z = 0.22;
    return group(g);
  },
  peluche: () => group(
    at(ball(C.brown, 0.3, 0.3, 0.26), 0, 0.3, 0),
    at(ball(C.brown, 0.27, 0.25, 0.25), 0, 0.72, 0),
    at(ball(C.brown, 0.1, 0.1, 0.06), -0.2, 0.94, 0),
    at(ball(C.brown, 0.1, 0.1, 0.06), 0.2, 0.94, 0),
    at(ball(C.cream, 0.12, 0.09, 0.08), 0, 0.66, 0.2),
    at(ball(C.cocoa, 0.035), -0.1, 0.78, 0.22),
    at(ball(C.cocoa, 0.035), 0.1, 0.78, 0.22),
    at(ball(C.cocoa, 0.035, 0.028, 0.03), 0, 0.7, 0.28),
    at(ball(C.brown, 0.1, 0.14, 0.1), -0.32, 0.36, 0.06),
    at(ball(C.brown, 0.1, 0.14, 0.1), 0.32, 0.36, 0.06),
    at(ball(C.brown, 0.12, 0.1, 0.14), -0.16, 0.07, 0.14),
    at(ball(C.brown, 0.12, 0.1, 0.14), 0.16, 0.07, 0.14),
  ),
  cometa: () => {
    const shape = new THREE.Shape();
    shape.moveTo(0, 0.6);
    shape.lineTo(0.36, 0.12);
    shape.lineTo(0, -0.5);
    shape.lineTo(-0.36, 0.12);
    shape.closePath();
    const sail = new THREE.Mesh(new THREE.ExtrudeGeometry(shape, { depth: 0.03, bevelEnabled: false }), mat(C.blue));
    sail.castShadow = true;
    const g = group(sail, at(box(C.white, 0.72, 0.03, 0.02), 0, 0.12, 0.04), at(box(C.white, 0.03, 1.1, 0.02), 0, 0.05, 0.04));
    for (let i = 0; i < 3; i++) {
      const bow = at(ball([C.pink, C.yellow, C.mint][i], 0.08, 0.04, 0.03), Math.sin(i * 1.3) * 0.1, -0.62 - i * 0.16, 0);
      bow.rotation.z = i - 1;
      g.add(bow);
    }
    g.rotation.z = 0.3;
    return group(at(g, 0, 0.95, 0));
  },
};

// ── Casa ──────────────────────────────────────────────────────
// Muebles para el rincón (al lado de la criatura, a la derecha) y para
// debajo de la ventana. Se apoyan en el suelo en y = 0.

const ROOM = { roughness: 0.7 };

function pot(color, band, scale = 1) {
  const g = group(
    at(cyl(color, 0.2, 0.15, 0.3, ROOM, 24), 0, 0.15, 0),
    at(cyl(band, 0.205, 0.19, 0.08, ROOM, 24), 0, 0.24, 0),
  );
  g.scale.setScalar(scale);
  return g;
}

const leafShape = new THREE.Shape();
leafShape.moveTo(0, 0);
leafShape.bezierCurveTo(0.14, 0.12, 0.12, 0.5, 0, 0.62);
leafShape.bezierCurveTo(-0.12, 0.5, -0.14, 0.12, 0, 0);
const LEAF = new THREE.ExtrudeGeometry(leafShape, { depth: 0.02, bevelEnabled: false, curveSegments: 10 });
LEAF.userData.shared = true;

function petals(color, count, length, width, radius) {
  const g = group();
  for (let i = 0; i < count; i++) {
    const a = (i / count) * Math.PI * 2;
    const petal = at(ball(color, width, length, 0.03), Math.cos(a) * radius, Math.sin(a) * radius, 0);
    petal.rotation.z = a - Math.PI / 2;
    g.add(petal);
  }
  return g;
}

const DECOR = {
  // La planta de siempre.
  planta: () => {
    const g = pot('#fffdf8', '#ffd166');
    for (let i = 0; i < 6; i++) {
      const leaf = new THREE.Mesh(LEAF, mat(i % 2 ? '#6cc47f' : '#4fae7c', ROOM));
      leaf.position.y = 0.28;
      leaf.rotation.order = 'YXZ';
      leaf.rotation.y = (i / 6) * Math.PI * 2;
      leaf.rotation.x = 0.35 + (i % 3) * 0.16;
      leaf.scale.setScalar(0.9 + (i % 2) * 0.25);
      leaf.castShadow = true;
      g.add(leaf);
    }
    return g;
  },
  cactus: () => {
    const g = group(
      at(cyl('#f2a07b', 0.19, 0.15, 0.28, ROOM, 24), 0, 0.14, 0),
      at(cyl('#e8865f', 0.21, 0.205, 0.06, ROOM, 24), 0, 0.28, 0),
      at(cyl(C.cocoa, 0.18, 0.18, 0.02, ROOM, 24), 0, 0.3, 0),
      at(ball(C.green, 0.15, 0.34, 0.15), 0, 0.6, 0),
      at(ball(C.pink, 0.06, 0.04, 0.06), 0, 0.95, 0),
    );
    for (const [side, y, up] of [[-1, 0.58, 0.16], [1, 0.68, 0.12]]) {
      g.add(at(ball(C.green, 0.1, 0.06, 0.07), side * 0.17, y, 0));
      g.add(at(ball(C.green, 0.065, up, 0.065), side * 0.24, y + up * 0.7, 0));
    }
    for (let i = 0; i < 7; i++) {
      const a = (i / 7) * Math.PI * 2;
      g.add(at(ball(C.cream, 0.012), Math.sin(a) * 0.145, 0.45 + (i % 3) * 0.12, Math.cos(a) * 0.145));
    }
    return g;
  },
  girasol: () => {
    const head = group(
      petals(C.yellow, 12, 0.13, 0.065, 0.15),
      at(ball(C.brown, 0.12, 0.12, 0.05), 0, 0, 0.02),
    );
    head.position.set(0.02, 1.08, 0.04);
    head.rotation.set(-0.25, 0.25, 0);
    const leafL = at(ball(C.leaf, 0.12, 0.035, 0.06), -0.11, 0.66, 0);
    leafL.rotation.z = 0.5;
    const leafR = at(ball(C.green, 0.11, 0.035, 0.06), 0.1, 0.78, 0);
    leafR.rotation.z = -0.5;
    return group(
      pot('#fffdf8', C.mint),
      at(cyl(C.leaf, 0.022, 0.026, 0.78, ROOM, 12), 0, 0.68, 0),
      leafL, leafR, head,
    );
  },
  lampara: () => group(
    at(cyl(C.white, 0.17, 0.2, 0.05, ROOM), 0, 0.025, 0),
    at(cyl(C.tan, 0.022, 0.022, 1.12, ROOM, 12), 0, 0.6, 0),
    at(ball('#fffbe8', 0.07, 0.08, 0.07, { roughness: 0.3 }), 0, 1.16, 0),
    at(cyl('#ffe9b8', 0.13, 0.25, 0.3, { roughness: 0.9, side: THREE.DoubleSide }, 32), 0, 1.27, 0),
    at(ring(C.yellow, 0.25, 0.012, ROOM), 0, 1.12, 0).rotateX(Math.PI / 2),
  ),
  pecera: () => {
    const fish = group(
      at(ball(C.orange, 0.065, 0.045, 0.035), 0, 0, 0),
      at(cone(C.orange, 0.04, 0.06).rotateZ(Math.PI / 2), -0.08, 0, 0),
      at(ball(C.cocoa, 0.01), 0.04, 0.012, 0.03),
    );
    fish.position.set(0.02, 0.8, 0.13);
    fish.rotation.y = -0.4;
    return group(
      at(cyl(C.tan, 0.18, 0.2, 0.04, ROOM), 0, 0.02, 0),
      at(cyl(C.tan, 0.04, 0.05, 0.5, ROOM, 16), 0, 0.29, 0),
      at(cyl('#e8bd87', 0.3, 0.3, 0.05, ROOM, 32), 0, 0.555, 0),
      at(ball('#86c1f2', 0.2, 0.15, 0.2, { transparent: true, opacity: 0.82, roughness: 0.2 }), 0, 0.74, 0),
      at(ball(C.grey, 0.04, 0.025, 0.04), -0.07, 0.6, 0.03),
      at(ball(C.pink, 0.035, 0.022, 0.035), 0.06, 0.6, -0.02),
      at(cyl(C.leaf, 0.012, 0.016, 0.14, ROOM, 8), -0.05, 0.66, -0.06),
      fish,
      at(ball('#eaf5ff', 0.25, 0.24, 0.25, { transparent: true, opacity: 0.18, roughness: 0.05 }), 0, 0.82, 0),
      at(ring('#eaf5ff', 0.14, 0.016, { transparent: true, opacity: 0.6, roughness: 0.1 }), 0, 1.04, 0).rotateX(Math.PI / 2),
    );
  },
  cojin: () => {
    const top = at(ball(C.mint, 0.2, 0.08, 0.17, { roughness: 0.9 }), 0.08, 0.27, 0.02);
    top.rotation.z = -0.18;
    const g = group(at(ball(C.pink, 0.38, 0.15, 0.33, { roughness: 0.9 }), 0, 0.15, 0), top);
    for (const [x, z] of [[-0.3, -0.25], [0.3, -0.25], [-0.3, 0.25], [0.3, 0.25]]) g.add(at(ball(C.yellow, 0.04), x, 0.12, z));
    g.add(at(ball('#e2648b', 0.035, 0.02, 0.035), 0, 0.3, 0));
    return g;
  },
  mesita: () => {
    const g = group(at(cyl(C.white, 0.26, 0.24, 0.05, ROOM, 32), 0, 0.46, 0));
    for (let i = 0; i < 3; i++) {
      const a = (i / 3) * Math.PI * 2 + 0.5;
      const leg = at(cyl(C.tan, 0.024, 0.018, 0.46, ROOM, 10), Math.cos(a) * 0.15, 0.22, Math.sin(a) * 0.15);
      leg.rotation.set(Math.sin(a) * 0.18, 0, -Math.cos(a) * 0.18);
      g.add(leg);
    }
    g.add(
      at(cyl(C.blue, 0.06, 0.085, 0.18, ROOM, 20), 0, 0.575, 0),
      at(cyl(C.blue, 0.045, 0.06, 0.05, ROOM, 20), 0, 0.69, 0),
    );
    for (const [x, tilt, height, color] of [[-0.05, 0.3, 0.2, C.pink], [0, 0, 0.26, C.yellow], [0.05, -0.32, 0.2, '#ff7a7a']]) {
      const stem = group(
        at(cyl(C.leaf, 0.01, 0.01, height, ROOM, 6), 0, height / 2, 0),
        at(ball(color, 0.045, 0.06, 0.045), 0, height + 0.03, 0),
      );
      stem.position.set(x * 0.5, 0.7, 0);
      stem.rotation.z = tilt;
      g.add(stem);
    }
    return g;
  },
  baul: () => {
    const lid = group(at(box(C.tan, 0.7, 0.07, 0.4), 0, 0.035, 0.2));
    lid.position.set(0, 0.36, -0.2);
    lid.rotation.x = -0.55;
    const ballToy = at(ball(C.pink, 0.11), -0.16, 0.38, 0.02);
    const star = at(ball(C.yellow, 0.07, 0.07, 0.025), 0.14, 0.42, 0.04);
    star.rotation.z = 0.4;
    return group(
      at(box(C.tan, 0.68, 0.34, 0.38), 0, 0.17, 0),
      at(box(C.brown, 0.7, 0.05, 0.4), 0, 0.06, 0),
      at(box(C.brown, 0.7, 0.05, 0.4), 0, 0.3, 0),
      at(box(C.gold, 0.08, 0.1, 0.02), 0, 0.3, 0.2),
      ballToy, star, lid,
    );
  },
  camita: () => {
    const rim = new THREE.Mesh(new THREE.TorusGeometry(0.33, 0.11, 14, 40), mat(C.blue, { roughness: 0.9 }));
    rim.castShadow = true;
    rim.rotation.x = Math.PI / 2;
    rim.scale.set(1, 0.8, 0.9);
    rim.position.y = 0.1;
    const blanket = at(box(C.yellow, 0.3, 0.02, 0.24), 0.08, 0.12, 0.06);
    blanket.rotation.set(0, 0.5, 0.08);
    return group(
      rim,
      at(ball(C.cream, 0.32, 0.07, 0.28, { roughness: 0.9 }), 0, 0.06, 0),
      blanket,
      at(ball(C.pink, 0.11, 0.05, 0.08, { roughness: 0.9 }), -0.13, 0.13, -0.08),
    );
  },
};

// Muestra de papel pintado enmarcada, para la tienda.
function swatch(id) {
  const face = new THREE.Mesh(new THREE.PlaneGeometry(0.9, 0.9), new THREE.MeshBasicMaterial({ map: wallpaperTexture(id, 1.25, 1.25) }));
  face.position.set(0, 0.5, 0.026);
  return group(at(box(C.white, 1, 1, 0.05, ROOM), 0, 0.5, 0), face);
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
  const cp = Math.cos(pitch);
  const point = new THREE.Vector3(host.rx * Math.sin(yaw) * cp, host.ry * Math.sin(pitch), host.rz * Math.cos(yaw) * cp);
  const normal = new THREE.Vector3(point.x / host.rx ** 2, point.y / host.ry ** 2, point.z / host.rz ** 2).normalize();
  return { point: point.add(host.center), normal };
}
const AXIS_Z = new THREE.Vector3(0, 0, 1);
const AXIS_Y = new THREE.Vector3(0, 1, 0);

function onHead(anchors, yaw, pitch, object, sink = 0) {
  const { point, normal } = surface(anchors.face.host, yaw, pitch);
  object.position.copy(point).addScaledVector(normal, -sink);
  object.quaternion.setFromUnitVectors(AXIS_Y, normal);
  return object;
}

function halfSphere(color, radius, squash = 1) {
  const mesh = new THREE.Mesh(new THREE.SphereGeometry(radius, 30, 16, 0, Math.PI * 2, 0, Math.PI / 2), mat(color, { side: THREE.DoubleSide }));
  mesh.scale.y = squash;
  mesh.castShadow = true;
  return mesh;
}

function neckRing(anchors, color, tube, lift = 0) {
  const mesh = ring(color, 1, tube);
  mesh.rotation.x = Math.PI / 2;
  mesh.scale.set(anchors.neck.rx, anchors.neck.rz, 1);
  mesh.position.y = anchors.neck.y + lift;
  return mesh;
}

const WEAR = {
  lazo: (a) => {
    const s = a.hat.r / 0.5;
    const bow = group();
    for (const side of [-1, 1]) {
      const loop = cone(C.pink, 0.13 * s, 0.24 * s);
      loop.rotation.z = side * Math.PI / 2;
      loop.scale.z = 0.5;
      bow.add(at(loop, side * 0.13 * s, 0, 0));
    }
    bow.add(ball(C.red, 0.06 * s));
    return { parent: 'head', object: onHead(a, 0.55, 1.0, group(at(bow, 0, 0.09 * s, 0))) };
  },
  gorro: (a) => {
    const s = a.hat.r / 0.5;
    const hat = group(
      at(halfSphere(C.red, 0.4 * s, 0.9), 0, 0, 0),
      at(ring(C.white, 0.4 * s, 0.065 * s), 0, 0.01 * s, 0),
      at(ball(C.white, 0.1 * s), 0, 0.42 * s, 0),
    );
    hat.children[1].rotation.x = Math.PI / 2;
    return { parent: 'head', object: at(hat, 0, a.hat.y - 0.24 * s, 0) };
  },
  gorra: (a) => {
    const s = a.hat.r / 0.5;
    const visor = cyl(C.navy, 0.3 * s, 0.3 * s, 0.035 * s);
    visor.scale.z = 1.1;
    const hat = group(
      halfSphere(C.blue, 0.4 * s, 0.82),
      at(visor, 0, 0.02 * s, 0.3 * s),
      at(ball(C.navy, 0.045 * s), 0, 0.33 * s, 0),
    );
    hat.rotation.x = -0.08;
    return { parent: 'head', object: at(hat, 0, a.hat.y - 0.2 * s, 0) };
  },
  paja: (a) => {
    const s = a.hat.r / 0.5;
    const hat = group(
      at(cyl(C.yellow, 0.66 * s, 0.7 * s, 0.035 * s), 0, 0, 0),
      at(cyl(C.yellow, 0.28 * s, 0.34 * s, 0.24 * s), 0, 0.13 * s, 0),
      at(cyl(C.red, 0.345 * s, 0.35 * s, 0.07 * s), 0, 0.055 * s, 0),
    );
    hat.rotation.x = -0.1;
    return { parent: 'head', object: at(hat, 0, a.hat.y - 0.08 * s, 0) };
  },
  flor: (a) => {
    const s = a.hat.r / 0.5;
    const flower = group(ball(C.yellow, 0.06 * s, 0.045 * s, 0.06 * s));
    for (let i = 0; i < 6; i++) {
      const angle = (i / 6) * Math.PI * 2;
      flower.add(at(ball(C.white, 0.07 * s, 0.025 * s, 0.05 * s), Math.cos(angle) * 0.1 * s, -0.01 * s, Math.sin(angle) * 0.1 * s));
      flower.children[flower.children.length - 1].rotation.y = -angle;
    }
    return { parent: 'head', object: onHead(a, -0.6, 0.98, group(at(flower, 0, 0.03 * s, 0))) };
  },
  corona: (a) => {
    const s = a.hat.r / 0.5;
    const gold = { roughness: 0.25, metalness: 0.7 };
    const crown = group(at(cyl(C.gold, 0.25 * s, 0.22 * s, 0.14 * s, gold), 0, 0.07 * s, 0));
    for (let i = 0; i < 6; i++) {
      const angle = (i / 6) * Math.PI * 2;
      crown.add(at(cone(C.gold, 0.06 * s, 0.14 * s, gold), Math.cos(angle) * 0.22 * s, 0.2 * s, Math.sin(angle) * 0.22 * s));
      crown.add(at(ball(i % 2 ? C.red : C.blue, 0.03 * s), Math.cos(angle + 0.52) * 0.245 * s, 0.07 * s, Math.sin(angle + 0.52) * 0.245 * s));
    }
    return { parent: 'head', object: at(crown, 0, a.hat.y - 0.06 * s, 0) };
  },
  gafas: (a) => glasses(a, false),
  gafasol: (a) => glasses(a, true),
  bigote: (a) => {
    const s = a.hat.r / 0.5;
    const whiskers = group();
    for (const side of [-1, 1]) {
      const half = ball(C.cocoa, 0.11 * s, 0.04 * s, 0.035 * s);
      half.rotation.z = side * -0.3;
      whiskers.add(at(half, side * 0.09 * s, 0, 0));
    }
    const { point, normal } = surface(a.face.host, 0, a.face.pitch - 0.15);
    whiskers.position.copy(point).addScaledVector(normal, 0.02 * s);
    whiskers.quaternion.setFromUnitVectors(AXIS_Z, normal);
    return { parent: 'head', object: whiskers };
  },
  panuelo: (a) => {
    const flap = cone(C.red, 0.2, 0.26);
    flap.rotation.set(Math.PI + 0.25, 0, 0);
    flap.scale.z = 0.35;
    return { parent: 'body', object: group(neckRing(a, C.red, 0.075), at(flap, 0, a.neck.y - 0.14, a.neck.rz + 0.03)) };
  },
  pajarita: (a) => {
    const bow = group(ball(C.navy, 0.045));
    for (const side of [-1, 1]) {
      const wing = cone(C.blue, 0.1, 0.17);
      wing.rotation.z = side * Math.PI / 2;
      wing.scale.z = 0.5;
      bow.add(at(wing, side * 0.095, 0, 0));
    }
    return { parent: 'body', object: group(neckRing(a, C.navy, 0.03), at(bow, 0, a.neck.y, a.neck.rz + 0.05)) };
  },
  bufanda: (a) => {
    const end = group(at(box(C.mint, 0.15, 0.3, 0.05), 0, -0.15, 0), at(box(C.white, 0.15, 0.05, 0.055), 0, -0.2, 0));
    end.rotation.z = 0.12;
    return {
      parent: 'body',
      object: group(neckRing(a, C.mint, 0.1), neckRing(a, C.white, 0.045, 0.085), at(end, a.neck.rx * 0.45, a.neck.y - 0.02, a.neck.rz + 0.05)),
    };
  },
  cascabel: (a) => group2('body',
    neckRing(a, C.red, 0.04),
    at(ball(C.gold, 0.08, 0.08, 0.08, { roughness: 0.25, metalness: 0.7 }), 0, a.neck.y - 0.08, a.neck.rz + 0.04),
  ),
};

function group2(parent, ...children) {
  return { parent, object: group(...children) };
}

function glasses(a, dark) {
  const s = a.hat.r / 0.5;
  const g = group();
  const centers = [];
  for (const side of [-1, 1]) {
    const { point, normal } = surface(a.face.host, side * a.face.gap, a.face.pitch);
    const lens = group(ring(dark ? C.cocoa : C.navy, 0.105 * s, 0.014 * s));
    if (dark) lens.add(ball(C.cocoa, 0.1 * s, 0.1 * s, 0.012 * s, { roughness: 0.15 }));
    lens.position.copy(point).addScaledVector(normal, 0.05 * s);
    lens.quaternion.setFromUnitVectors(AXIS_Z, normal);
    centers.push(lens.position.clone());
    g.add(lens);
  }
  const bridge = new THREE.Mesh(new THREE.CapsuleGeometry(0.012 * s, centers[0].distanceTo(centers[1]) - 0.2 * s, 4, 8), mat(dark ? C.cocoa : C.navy));
  bridge.rotation.z = Math.PI / 2;
  bridge.position.copy(centers[0]).lerp(centers[1], 0.5);
  bridge.position.z += 0.012 * s;
  g.add(bridge);
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

// Huevo: color de su ADN y motas del color secundario.
export function buildEgg(egg) {
  const points = [];
  for (let i = 0; i <= 28; i++) {
    const t = (i / 28) * Math.PI;
    // Más ancho abajo que arriba, como un huevo de verdad.
    const widen = 1 + 0.2 * Math.cos(t);
    points.push(new THREE.Vector2(Math.sin(t) * 0.36 * widen, 0.47 - Math.cos(t) * 0.47));
  }
  const shell = new THREE.MeshPhysicalMaterial({
    color: new THREE.Color().setHSL(egg.hue / 360, (egg.sat - 4) / 100, (egg.light + 3) / 100),
    roughness: 0.42, clearcoat: 0.35, clearcoatRoughness: 0.4,
  });
  const body = new THREE.Mesh(new THREE.LatheGeometry(points, 36), shell);
  body.castShadow = true;
  const g = group(body);
  const rng = createRng(egg.hue * 131 + egg.hue2);
  const spot = new THREE.MeshStandardMaterial({
    color: new THREE.Color().setHSL(egg.hue2 / 360, (egg.sat + 6) / 100, (egg.light - 8) / 100), roughness: 0.5,
  });
  for (let i = 0; i < 9; i++) {
    const t = rng.range(0.5, 2.5);
    const yaw = rng.range(0, Math.PI * 2);
    const widen = 1 + 0.2 * Math.cos(t);
    const radius = Math.sin(t) * 0.36 * widen;
    const size = rng.range(0.035, 0.07);
    const mesh = new THREE.Mesh(SPHERE, spot);
    mesh.scale.set(size, size, size * 0.25);
    mesh.position.set(Math.sin(yaw) * radius, 0.47 - Math.cos(t) * 0.47, Math.cos(yaw) * radius);
    mesh.lookAt(mesh.position.x * 3, mesh.position.y + (Math.cos(t) > 0 ? -0.2 : 0.2), mesh.position.z * 3);
    g.add(mesh);
  }
  return g;
}
