// Piezas comunes de todas las criaturas (monstruos y mascotas): huesos,
// texturas de la cara, materiales, ojos, caché de mallas y montaje del
// esqueleto. Cada especie esculpe su cuerpo (monster.js, pet.js) y se
// apoya en esto para la cara y la animación.

import * as THREE from 'three';
import { clamp } from '/shared/rng.js';
import { canvasTexture } from './materials.js';

const { PI } = Math;
export const V = (x = 0, y = 0, z = 0) => new THREE.Vector3(x, y, z);
export const Y = V(0, 1, 0);
export const Z = V(0, 0, 1);

export const BODY = 0;
export const HEAD = 1;
export const EAR_L = 2;
export const EAR_R = 3;
export const ARM_L = 4;
export const ARM_R = 5;
export const TAIL = 6;
export const BONES = 7;
export const PARENT = [-1, BODY, HEAD, HEAD, BODY, BODY, BODY];

export const INK = '#3a2118';

export const hsl = (h, s, l) => new THREE.Color().setHSL((((h % 360) + 360) % 360) / 360, clamp(s, 0, 100) / 100, clamp(l, 0, 100) / 100);

// ── Texturas de la cara ───────────────────────────────────────
// Se dibujan una vez y las comparten todas las criaturas.

const faceTextures = new Map();
export function faceTexture(key, width, height, draw) {
  if (!faceTextures.has(key)) {
    const texture = canvasTexture(width, height, (ctx, w, h) => {
      ctx.clearRect(0, 0, w, h);
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      draw(ctx, w, h);
    });
    texture.anisotropy = 4;
    texture.userData.shared = true;
    faceTextures.set(key, texture);
  }
  return faceTextures.get(key);
}

// La boca ocupa el centro de un lienzo de 256 × 160.
export function mouthTexture(kind, fang) {
  return faceTexture(`mouth:${kind}:${fang}`, 256, 160, (ctx, w) => {
    const cx = w / 2;
    ctx.strokeStyle = INK;
    ctx.lineWidth = 11;
    if (kind === 'smile') {
      ctx.beginPath();
      ctx.moveTo(cx - 34, 66);
      ctx.quadraticCurveTo(cx, 108, cx + 34, 66);
      ctx.stroke();
    } else if (kind === 'cat') {
      ctx.beginPath();
      ctx.moveTo(cx - 40, 64);
      ctx.quadraticCurveTo(cx - 20, 96, cx, 68);
      ctx.quadraticCurveTo(cx + 20, 96, cx + 40, 64);
      ctx.stroke();
    } else if (kind === 'sad') {
      ctx.beginPath();
      ctx.moveTo(cx - 30, 96);
      ctx.quadraticCurveTo(cx, 62, cx + 30, 96);
      ctx.stroke();
    } else if (kind === 'flat') {
      ctx.beginPath();
      ctx.moveTo(cx - 24, 80);
      ctx.lineTo(cx + 24, 80);
      ctx.stroke();
    } else if (kind === 'open') {
      ctx.beginPath();
      ctx.moveTo(cx - 34, 58);
      ctx.quadraticCurveTo(cx, 50, cx + 34, 58);
      ctx.bezierCurveTo(cx + 36, 104, cx - 36, 104, cx - 34, 58);
      ctx.closePath();
      ctx.fillStyle = '#7a2e36';
      ctx.fill();
      ctx.save();
      ctx.clip();
      ctx.fillStyle = '#ff8fa3';
      ctx.beginPath();
      ctx.ellipse(cx, 104, 26, 20, 0, 0, PI * 2);
      ctx.fill();
      ctx.restore();
      ctx.lineWidth = 8;
      ctx.stroke();
    }
    if (fang && kind !== 'sad') {
      const y = kind === 'open' ? 58 : kind === 'flat' ? 80 : 74;
      ctx.fillStyle = '#ffffff';
      ctx.strokeStyle = INK;
      ctx.lineWidth = 5;
      ctx.beginPath();
      ctx.moveTo(cx + 12, y);
      ctx.lineTo(cx + 26, y);
      ctx.lineTo(cx + 19, y + 18);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
    }
  });
}

// Ojo cerrado: 'sleep' es un arco hacia abajo (◡), 'happy' hacia arriba (^).
export function lidTexture(kind) {
  return faceTexture(`lid:${kind}`, 128, 96, (ctx, w, h) => {
    ctx.strokeStyle = INK;
    ctx.lineWidth = 12;
    ctx.beginPath();
    if (kind === 'sleep') {
      ctx.moveTo(22, 40);
      ctx.quadraticCurveTo(w / 2, 84, w - 22, 40);
    } else {
      ctx.moveTo(24, 66);
      ctx.quadraticCurveTo(w / 2, 14, w - 24, 66);
    }
    ctx.stroke();
  });
}

export function blushTexture() {
  return faceTexture('blush', 128, 128, (ctx, w, h) => {
    const g = ctx.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2);
    g.addColorStop(0, 'rgba(255,255,255,0.95)');
    g.addColorStop(0.45, 'rgba(255,255,255,0.7)');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
  });
}

// Ceja enfadada: baja hacia el centro de la cara.
export function browTexture(side) {
  return faceTexture(`brow:${side}`, 128, 64, (ctx) => {
    ctx.strokeStyle = INK;
    ctx.lineWidth = 16;
    ctx.beginPath();
    ctx.moveTo(22, side < 0 ? 22 : 44);
    ctx.lineTo(106, side < 0 ? 44 : 22);
    ctx.stroke();
  });
}

export function decalMaterial(map, extra = {}) {
  return new THREE.MeshStandardMaterial({
    map,
    transparent: true,
    depthWrite: false,
    roughness: 0.7,
    polygonOffset: true,
    polygonOffsetFactor: -2,
    polygonOffsetUnits: -2,
    ...extra,
  });
}

export const EYE = new THREE.MeshPhysicalMaterial({ color: '#1e120d', roughness: 0.18, clearcoat: 1, clearcoatRoughness: 0.06 });
export const GLINT = new THREE.MeshBasicMaterial({ color: '#ffffff' });
export const NOSE_SHINE = { roughness: 0.3, clearcoat: 0.8, clearcoatRoughness: 0.2 };
EYE.userData.shared = GLINT.userData.shared = true;
export const BALL = new THREE.SphereGeometry(1, 24, 16);
BALL.userData.shared = true;

// ── Caché de mallas ───────────────────────────────────────────
// Esculpir cuesta: la misma criatura (al cambiarle la ropa, al volver de
// otra pantalla) reaprovecha sus mallas.

const built = new Map();
const KEEP = 4;
export function sculptCache(key) {
  if (built.has(key)) {
    const entry = built.get(key);
    built.delete(key);
    built.set(key, entry);
    return entry;
  }
  const entry = {};
  built.set(key, entry);
  while (built.size > KEEP) {
    const [oldKey, old] = built.entries().next().value;
    built.delete(oldKey);
    for (const geometry of Object.values(old)) if (geometry?.isBufferGeometry) geometry.dispose();
  }
  return entry;
}

export function once(entry, name, make) {
  if (!entry[name]) {
    entry[name] = make();
    if (entry[name]?.isBufferGeometry) entry[name].userData.shared = true;
  }
  return entry[name];
}

// Base ortonormal con z = normal y la y lo más hacia arriba posible.
export function faceFrame(normal, up = Y) {
  const z = normal.clone().normalize();
  const x = new THREE.Vector3().crossVectors(up, z).normalize();
  const y = new THREE.Vector3().crossVectors(z, x);
  return new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, z));
}

export function quatFromTo(from, to) {
  return new THREE.Quaternion().setFromUnitVectors(from, to.clone().normalize());
}


export function earRig(bones) {
  return [{ pivot: bones[EAR_L], side: -1, rest: 0 }, { pivot: bones[EAR_R], side: 1, rest: 0 }];
}

export function armRig(bones) {
  return [{ pivot: bones[ARM_L], side: -1, rest: 0 }, { pivot: bones[ARM_R], side: 1, rest: 0 }];
}

// Radio de la figura a la altura `y` en la dirección (dx, dz).
export function radiusAt(S, y, dx, dz) {
  let inside = 0;
  let outside = 1.5;
  if (S.sdf(0, y, 0) > 0) return 0;
  for (let t = 0.02; t < 1.5; t += 0.02) {
    if (S.sdf(dx * t, y, dz * t) > 0) {
      outside = t;
      inside = t - 0.02;
      break;
    }
  }
  for (let i = 0; i < 12; i++) {
    const mid = (inside + outside) / 2;
    if (S.sdf(dx * mid, y, dz * mid) > 0) outside = mid;
    else inside = mid;
  }
  return (inside + outside) / 2;
}

// Altura y radios del cuello, donde se atan pañuelos y cascabeles. En
// coordenadas del cuerpo.
export function neckOf(S, plan, host) {
  const { h } = plan.body;
  if (plan.blob) {
    const y = h * 0.3;
    return { y, rx: radiusAt(S, y, 1, 0) + 0.012, rz: (radiusAt(S, y, 0, 1) + radiusAt(S, y, 0, -1)) / 2 + 0.012 };
  }
  let best = { y: h * 0.8, r: Infinity };
  for (let y = h * 0.6; y < host.center.y; y += 0.01) {
    const r = radiusAt(S, y, 1, 0);
    if (r < best.r) best = { y, r };
  }
  const y = best.y;
  return { y, rx: best.r + 0.014, rz: (radiusAt(S, y, 0, 1) + radiusAt(S, y, 0, -1)) / 2 + 0.014 };
}


// ── Bocas de mascota ──────────────────────────────────────────
// El lienzo es de 256 × 160 y su borde de arriba queda justo debajo de
// la nariz: de ahí baja el surco y salen los labios.

export function petMouthTexture(species, kind) {
  return faceTexture(`petmouth:${species}:${kind}`, 256, 160, (ctx, w) => {
    const cx = w / 2;
    const cat = species === 'gato';
    const drop = cat ? 30 : 46;
    const spread = cat ? 30 : 44;
    ctx.strokeStyle = INK;
    ctx.lineWidth = cat ? 8 : 10;
    // Surco bajo la nariz.
    ctx.beginPath();
    ctx.moveTo(cx, 4);
    ctx.lineTo(cx, drop);
    ctx.stroke();
    if (kind === 'open') {
      const wide = cat ? 34 : 48;
      const deep = cat ? 64 : 104;
      ctx.beginPath();
      ctx.moveTo(cx - wide, drop - 6);
      ctx.quadraticCurveTo(cx - wide * 0.4, drop + 10, cx, drop);
      ctx.quadraticCurveTo(cx + wide * 0.4, drop + 10, cx + wide, drop - 6);
      ctx.bezierCurveTo(cx + wide * 0.9, deep, cx - wide * 0.9, deep, cx - wide, drop - 6);
      ctx.closePath();
      ctx.fillStyle = '#6e2630';
      ctx.fill();
      ctx.save();
      ctx.clip();
      ctx.fillStyle = '#ef7f8e';
      ctx.beginPath();
      ctx.ellipse(cx, deep - (cat ? 4 : 10), wide * 0.62, cat ? 16 : 30, 0, 0, PI * 2);
      ctx.fill();
      ctx.strokeStyle = '#d0606f';
      ctx.lineWidth = 4;
      ctx.beginPath();
      ctx.moveTo(cx, deep - (cat ? 16 : 34));
      ctx.lineTo(cx, deep - 2);
      ctx.stroke();
      ctx.restore();
      ctx.strokeStyle = INK;
      ctx.lineWidth = cat ? 7 : 8;
      ctx.stroke();
      if (cat) {
        ctx.fillStyle = '#ffffff';
        for (const side of [-1, 1]) {
          ctx.beginPath();
          ctx.moveTo(cx + side * 22, drop + 2);
          ctx.lineTo(cx + side * 13, drop + 2);
          ctx.lineTo(cx + side * 18, drop + 16);
          ctx.closePath();
          ctx.fill();
        }
      }
      return;
    }
    const lift = kind === 'smile' ? -14 : kind === 'sad' ? 16 : -2;
    const curve = kind === 'smile' ? 26 : kind === 'sad' ? -8 : 10;
    for (const side of [-1, 1]) {
      ctx.beginPath();
      ctx.moveTo(cx, drop);
      ctx.quadraticCurveTo(cx + side * spread * 0.5, drop + curve, cx + side * spread, drop + lift);
      ctx.stroke();
    }
  });
}

// ── Ojos ──────────────────────────────────────────────────────
// Un globo con el iris pintado (pupila redonda o rasgada), dos brillos
// que no se mueven (son reflejos) y un párpado de la piel con su línea
// de pestañas. Al mirar gira el globo; al parpadear baja el párpado.

// Esfera con las coordenadas de textura proyectadas desde delante.
function eyeballGeometry() {
  const g = new THREE.SphereGeometry(1, 40, 28);
  const pos = g.attributes.position;
  const uv = g.attributes.uv;
  for (let i = 0; i < pos.count; i++) uv.setXY(i, 0.5 + pos.getX(i) * 0.5, 0.5 + pos.getY(i) * 0.5);
  g.userData.shared = true;
  return g;
}
const EYEBALL = eyeballGeometry();
const LID = new THREE.SphereGeometry(1.1, 36, 14, 0, PI * 2, 0, PI / 2);
const LASH = new THREE.TorusGeometry(1.1, 0.085, 8, 36, PI);
LASH.rotateX(PI / 2);
LID.userData.shared = LASH.userData.shared = true;
const LASH_MAT = new THREE.MeshStandardMaterial({ color: '#1f1512', roughness: 0.6 });
LASH_MAT.userData.shared = true;

// Ángulo del párpado: abierto deja ver un filo arriba; cerrado tapa el globo.
export const LID_OPEN = -1.22;
export const LID_SHUT = 1.42;

const irisMaps = new Map();
// `kind`: 'round' (pupila redonda), 'slit' (rasgada, de gato) o
// 'sclera' (con blanco alrededor, como los monstruos).
export function irisTexture(color, kind = 'round') {
  const key = `${color}:${kind}`;
  if (irisMaps.has(key)) return irisMaps.get(key);
  const texture = canvasTexture(256, 256, (ctx, w) => {
    const c = w / 2;
    const iris = new THREE.Color(color);
    const css = (col) => `#${col.getHexString()}`;
    const dark = iris.clone().lerp(new THREE.Color('#0d0806'), 0.82);
    const deep = iris.clone().lerp(new THREE.Color('#0d0806'), 0.45);
    const bright = iris.clone().lerp(new THREE.Color('#ffffff'), 0.28);
    const R = kind === 'sclera' ? w * 0.3 : w * 0.4;
    if (kind === 'sclera') {
      const white = ctx.createLinearGradient(0, 0, 0, w);
      white.addColorStop(0, '#d9d3cc');
      white.addColorStop(0.35, '#fbf8f2');
      white.addColorStop(1, '#ffffff');
      ctx.fillStyle = white;
    } else {
      ctx.fillStyle = css(dark);
    }
    ctx.fillRect(0, 0, w, w);
    // Iris: más oscuro arriba (la sombra del párpado) y claro abajo,
    // donde la luz sale después de atravesar la córnea.
    const g = ctx.createLinearGradient(0, c - R, 0, c + R);
    g.addColorStop(0, css(deep));
    g.addColorStop(0.55, css(iris));
    g.addColorStop(1, css(bright));
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(c, c, R, 0, PI * 2);
    ctx.fill();
    // Fibras del iris.
    ctx.strokeStyle = 'rgba(255,255,255,0.12)';
    ctx.lineWidth = 2;
    for (let i = 0; i < 40; i++) {
      const a = (i / 40) * PI * 2;
      ctx.beginPath();
      ctx.moveTo(c + Math.cos(a) * R * 0.42, c + Math.sin(a) * R * 0.42);
      ctx.lineTo(c + Math.cos(a) * R * 0.9, c + Math.sin(a) * R * 0.9);
      ctx.stroke();
    }
    // Anillo exterior.
    ctx.strokeStyle = css(dark);
    ctx.lineWidth = R * 0.14;
    ctx.beginPath();
    ctx.arc(c, c, R - R * 0.07, 0, PI * 2);
    ctx.stroke();
    // Pupila.
    ctx.fillStyle = '#0a0605';
    ctx.beginPath();
    if (kind === 'slit') ctx.ellipse(c, c, R * 0.24, R * 0.78, 0, 0, PI * 2);
    else ctx.arc(c, c, R * (kind === 'sclera' ? 0.5 : 0.44), 0, PI * 2);
    ctx.fill();
  });
  texture.anisotropy = 4;
  texture.userData.shared = true;
  irisMaps.set(key, texture);
  return texture;
}

const eyeMats = new Map();
function eyeMaterial(color, kind) {
  const key = `${color}:${kind}`;
  if (!eyeMats.has(key)) {
    const m = new THREE.MeshPhysicalMaterial({ map: irisTexture(color, kind), roughness: 0.2, clearcoat: 1, clearcoatRoughness: 0.03 });
    m.userData.shared = true;
    eyeMats.set(key, m);
  }
  return eyeMats.get(key);
}

// Un ojo puesto en la cara. `point` y `normal` son de la superficie, en
// coordenadas de la cabeza. `r` es el radio; `tall`, cuánto más alto que
// ancho; `tilt`, el giro del ojo (rasgado hacia arriba por fuera).
export function makeEye({ point, normal, r, tall = 1.1, depth = 0.62, iris = '#3a2516', kind = 'round', skin = '#ffffff', tilt = 0, sparkle = false }) {
  const group = new THREE.Group();
  group.position.copy(point).addScaledVector(normal, -r * 0.18);
  group.quaternion.copy(faceFrame(normal));
  group.rotateZ(tilt);
  const open = new THREE.Group();
  const ball = new THREE.Group();
  ball.scale.set(r, r * tall, r * depth);
  const bead = new THREE.Mesh(EYEBALL, eyeMaterial(iris, kind));
  ball.add(bead);
  const lid = new THREE.Group();
  const lidMat = clayLid(skin);
  lid.add(new THREE.Mesh(LID, lidMat), new THREE.Mesh(LASH, LASH_MAT));
  lid.rotation.x = LID_OPEN;
  ball.add(lid);
  open.add(ball);
  const glint = new THREE.Mesh(BALL, GLINT);
  glint.scale.set(r * 0.26, r * 0.3, r * 0.1);
  glint.position.set(-r * 0.3, r * 0.34 * tall, r * depth * 0.95);
  const glint2 = new THREE.Mesh(BALL, GLINT);
  glint2.scale.set(r * 0.11, r * 0.11, r * 0.06);
  glint2.position.set(r * 0.32, -r * 0.3 * tall, r * depth * 0.93);
  open.add(glint, glint2);
  if (sparkle) {
    const star = new THREE.Mesh(BALL, GLINT);
    star.scale.set(r * 0.08, r * 0.08, r * 0.05);
    star.position.set(-r * 0.05, r * 0.1, r * depth * 1.0);
    open.add(star);
  }
  group.add(open);
  return { group, open, ball, bead, lid, r, tall };
}

// Párpado: del color de la piel de alrededor, mate y sin oclusión.
const lidMats = new Map();
function clayLid(color) {
  const key = new THREE.Color(color).getHexString();
  if (!lidMats.has(key)) {
    const m = new THREE.MeshStandardMaterial({ color: new THREE.Color(color), roughness: 0.78 });
    m.userData.shared = true;
    lidMats.set(key, m);
  }
  return lidMats.get(key);
}

// ── Montaje ───────────────────────────────────────────────────

// Malla con esqueleto a partir de las posiciones de los huesos (en
// coordenadas del cuerpo).
export function skinned(geometry, material, bonePos) {
  const mesh = new THREE.SkinnedMesh(geometry, material);
  mesh.castShadow = true;
  mesh.receiveShadow = false;
  mesh.frustumCulled = false;
  const bones = bonePos.map(() => new THREE.Bone());
  bones.forEach((bone, i) => {
    const parent = PARENT[i];
    bone.position.copy(bonePos[i]);
    if (parent >= 0) {
      bone.position.sub(bonePos[parent]);
      bones[parent].add(bone);
    }
  });
  mesh.add(bones[BODY]);
  mesh.updateMatrixWorld(true);
  mesh.bind(new THREE.Skeleton(bones));
  return { mesh, bones };
}
