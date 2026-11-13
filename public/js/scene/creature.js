// Construye la criatura 3D a partir de su plano y la anima. El cuerpo se
// esculpe por código como si fuera plastilina (ver sdf.js): cabeza,
// cuerpo, orejas, patas y cola se funden en una sola malla lisa con sus
// colores pintados y sus sombras de pliegue horneadas. Unos huesos la
// mueven (cabeza, orejas, brazos y cola), y la cara va pintada encima:
// ojos de cuenta brillante, boca, coloretes y cejas.

import * as THREE from 'three';
import { gsap } from 'gsap';
import { createRng, clamp } from '/shared/rng.js';
import { planFor } from './plan.js';
import { Sculpt, shape, surfacePatch } from './sdf.js';
import { clay, canvasTexture } from './materials.js';

const { sin, cos, PI } = Math;
const V = (x = 0, y = 0, z = 0) => new THREE.Vector3(x, y, z);
const Y = V(0, 1, 0);
const Z = V(0, 0, 1);

const BODY = 0;
const HEAD = 1;
const EAR_L = 2;
const EAR_R = 3;
const ARM_L = 4;
const ARM_R = 5;
const TAIL = 6;
const BONES = 7;
const PARENT = [-1, BODY, HEAD, HEAD, BODY, BODY, BODY];

const INK = '#3a2118';

const hsl = (h, s, l) => new THREE.Color().setHSL((((h % 360) + 360) % 360) / 360, clamp(s, 0, 100) / 100, clamp(l, 0, 100) / 100);

// Colores de juguete: el ADN da el tono; aquí se llevan a pastel.
function palette(genes, plan) {
  const s = clamp((genes.sat + plan.tone.sat) * 0.95, 20, 82);
  const l = clamp(genes.light + plan.tone.light + 5, 48, 84);
  const body = hsl(genes.hue, s, l);
  const light = hsl(genes.hue, s - 14, Math.min(l + 9, 93));
  return {
    body,
    light,
    accent: hsl(genes.hue2, s + 6, l - 6),
    inner: light.clone().lerp(new THREE.Color('#ffb0c0'), 0.55),
    leaf: hsl(plan.family === 'hoja' ? genes.hue : 112, plan.family === 'hoja' ? s + 12 : 48, l - 16),
    ivory: new THREE.Color('#fff1dc'),
    nose: body.clone().lerp(new THREE.Color('#e27c8f'), 0.7),
  };
}

// ── Texturas de la cara ───────────────────────────────────────
// Se dibujan una vez y las comparten todas las criaturas.

const faceTextures = new Map();
function faceTexture(key, width, height, draw) {
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
function mouthTexture(kind, fang) {
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
function lidTexture(kind) {
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

function blushTexture() {
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
function browTexture(side) {
  return faceTexture(`brow:${side}`, 128, 64, (ctx) => {
    ctx.strokeStyle = INK;
    ctx.lineWidth = 16;
    ctx.beginPath();
    ctx.moveTo(22, side < 0 ? 22 : 44);
    ctx.lineTo(106, side < 0 ? 44 : 22);
    ctx.stroke();
  });
}

function decalMaterial(map, extra = {}) {
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

const EYE = new THREE.MeshPhysicalMaterial({ color: '#1e120d', roughness: 0.18, clearcoat: 1, clearcoatRoughness: 0.06 });
const GLINT = new THREE.MeshBasicMaterial({ color: '#ffffff' });
const NOSE_SHINE = { roughness: 0.3, clearcoat: 0.8, clearcoatRoughness: 0.2 };
EYE.userData.shared = GLINT.userData.shared = true;
const BALL = new THREE.SphereGeometry(1, 24, 16);
BALL.userData.shared = true;

// ── Caché de mallas ───────────────────────────────────────────
// Esculpir cuesta: la misma criatura (al cambiarle la ropa, al volver de
// otra pantalla) reaprovecha sus mallas.

const built = new Map();
const KEEP = 4;
function sculptCache(key) {
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

function once(entry, name, make) {
  if (!entry[name]) {
    entry[name] = make();
    if (entry[name]?.isBufferGeometry) entry[name].userData.shared = true;
  }
  return entry[name];
}

// Base ortonormal con z = normal y la y lo más hacia arriba posible.
function faceFrame(normal, up = Y) {
  const z = normal.clone().normalize();
  const x = new THREE.Vector3().crossVectors(up, z).normalize();
  const y = new THREE.Vector3().crossVectors(z, x);
  return new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, z));
}

function quatFromTo(from, to) {
  return new THREE.Quaternion().setFromUnitVectors(from, to.clone().normalize());
}

// ── Construcción ──────────────────────────────────────────────

export function buildCreature(formId, genes, { detail = 'high' } = {}) {
  const plan = planFor(formId, genes);
  const pal = palette(genes, plan);
  const rng = createRng(genes.seed ^ 0x9e3779b9);
  const cacheKey = `${formId}|${genes.seed}|${genes.hue}|${genes.hue2}|${genes.sat}|${genes.light}|${genes.pattern}|${genes.patternAmt}|${JSON.stringify(genes.build)}|${genes.eyeStyle}`;
  const geo = detail === 'high' ? sculptCache(cacheKey) : {};
  // 'high' para la habitación, 'low' para miniaturas y 'tiny' para las
  // siluetas del Criadex, que solo necesitan el contorno.
  const cell = { high: 0.02, low: 0.032, tiny: 0.046 }[detail] ?? 0.032;
  const sock = plan.pattern === 'calcetines' ? pal.accent : pal.body;
  const { w, h, d } = plan.body;

  const S = new Sculpt({ ground: 0, groundK: 0.04 });
  const bonePos = Array.from({ length: BONES }, () => V());
  const headY = plan.blob ? h * 0.42 : h * 0.86;
  bonePos[HEAD].set(0, headY, 0);

  // `host` es el elipsoide que lleva la cara: la cabeza, o el cuerpo
  // entero en las crías. En coordenadas del cuerpo (pies en y = 0).
  let host;
  if (plan.blob) {
    host = { center: V(0, h * 0.5, 0), rx: w / 2, ry: h / 2, rz: d / 2 };
    S.add(shape.ellipsoid([0, h * 0.5, 0], [w / 2, h / 2, d / 2]), { color: pal.body, k: 0, bone: HEAD });
    S.add(shape.ellipsoid([0, h * 0.3, 0], [w * 0.52, h * 0.3, d * 0.5]), { color: pal.body, k: 0.16, bone: HEAD });
  } else {
    const { rx, ry, rz } = plan.head;
    host = { center: V(0, headY + ry * 0.72, 0.02), rx, ry, rz };
    S.add(shape.ellipsoid([0, h * 0.52, 0], [w * 0.46, h * 0.52, d * 0.46]), { color: pal.body, k: 0, bone: BODY });
    S.add(shape.ellipsoid([0, h * 0.32, 0.01], [w * 0.52, h * 0.32, d * 0.5]), { color: pal.body, k: 0.12, bone: BODY });
    S.add(shape.ellipsoid(host.center.toArray(), [rx, ry, rz]), { color: pal.body, k: 0.18, bone: HEAD });
  }
  const unit = host.rx / 0.5;
  const facePitch = plan.blob ? 0.2 : 0.02;

  const onHost = (yaw, pitch) => {
    const cp = cos(pitch);
    return V(host.rx * sin(yaw) * cp, host.ry * sin(pitch), host.rz * cos(yaw) * cp).add(host.center);
  };
  const hostNormal = (p) => {
    const q = p.clone().sub(host.center);
    return V(q.x / host.rx ** 2, q.y / host.ry ** 2, q.z / host.rz ** 2).normalize();
  };

  // Orejas
  // Las ramas agrandan las orejas; con un tope para que no tapen la cabeza.
  const earSize = 0.2 * unit * Math.min(plan.ears.size, 1.32);
  const droop = plan.ears.droop;
  for (const side of [-1, 1]) {
    const bone = side < 0 ? EAR_L : EAR_R;
    const type = plan.ears.type;
    if (type === 'round') {
      const s = earSize * 0.72;
      const base = onHost(side * 1.0, 0.8 - droop * 0.32);
      const dir = hostNormal(base).add(V(0, 0.5, 0)).normalize().applyAxisAngle(Z, -side * droop * 0.85);
      const center = base.clone().addScaledVector(dir, s * 0.62);
      const q = quatFromTo(Y, dir).multiply(new THREE.Quaternion().setFromAxisAngle(Y, -side * 0.35));
      S.add(shape.ellipsoid(center.toArray(), [s, s * 0.94, s * 0.48], q), { color: sock, k: 0.04, bone });
      const inner = center.clone().add(V(0, 0, s * 0.32).applyQuaternion(q)).addScaledVector(dir, s * 0.04);
      S.paint(shape.ellipsoid(inner.toArray(), [s * 0.56, s * 0.54, s * 0.3], q), { color: pal.inner, soft: 0.01 });
      bonePos[bone].copy(base);
    } else if (type === 'pointy') {
      const s = earSize * 0.8;
      const base = onHost(side * 0.78, 0.84 - droop * 0.2);
      const dir = hostNormal(base).add(V(0, 0.9, 0)).normalize().applyAxisAngle(Z, -side * droop * 0.8);
      const tip = base.clone().addScaledVector(dir, s * 1.85);
      S.add(shape.cone(base.clone().addScaledVector(dir, -s * 0.2).toArray(), tip.toArray(), s * 0.6, s * 0.1), { color: sock, k: 0.04, bone });
      const fwd = V(0, 0, s * 0.24).applyQuaternion(quatFromTo(Y, hostNormal(base)));
      S.paint(shape.cone(base.clone().add(fwd).addScaledVector(dir, s * 0.25).toArray(), tip.clone().add(fwd).addScaledVector(dir, -s * 0.4).toArray(), s * 0.34, s * 0.05), { color: pal.inner, soft: 0.01 });
      bonePos[bone].copy(base);
    } else if (type === 'leaf') {
      const s = earSize * 0.82;
      const base = onHost(side * 1.0, 0.7 - droop * 0.25);
      const dir = hostNormal(base).multiplyScalar(1.1).add(V(0, 1, 0)).normalize().applyAxisAngle(Z, -side * droop * 1.1);
      const q = quatFromTo(Y, dir).multiply(new THREE.Quaternion().setFromAxisAngle(Y, -side * 0.5));
      S.add(shape.leaf(base.clone().addScaledVector(dir, -s * 0.15).toArray(), q, s * 2.4, s * 1.2, 0.055 * unit, 0.18), { color: pal.accent, k: 0.035, bone, colorK: 0.03 });
      bonePos[bone].copy(base);
    } else {
      // Branquias de ajolote: tres ramitas a cada lado.
      const s = earSize;
      const base = onHost(side * 1.16, 0.4 - droop * 0.15);
      const out = hostNormal(base);
      for (let f = 0; f < 3; f++) {
        const dir = out.clone().add(V(0, 0.8 - f * 0.68, 0.2)).normalize().applyAxisAngle(Z, -side * droop * 0.6);
        const len = s * (1.05 - Math.abs(f - 1) * 0.14);
        const root = base.clone().add(V(0, (1 - f) * s * 0.4, 0)).addScaledVector(out, -s * 0.12);
        S.add(shape.cone(root.toArray(), root.clone().addScaledVector(dir, len).toArray(), s * 0.17, s * 0.27), { color: pal.accent, k: 0.05, bone, colorK: 0.035 });
      }
      bonePos[bone].copy(base);
    }
  }

  // Remate de la cabeza (lo que va fundido con ella)
  const crown = onHost(0, PI / 2 - 0.14);
  const ts = plan.top.size * unit;
  if (plan.top.type === 'tuft') {
    // Un rizo de pelo: una gota gorda que se dobla hacia delante y otra
    // más pequeña al lado.
    const base = crown.clone().add(V(0, -0.03 * ts, 0.02 * ts));
    const mid = base.clone().add(V(0.01 * ts, 0.12 * ts, 0.02 * ts));
    S.add(shape.cone(base.toArray(), mid.toArray(), 0.075 * ts, 0.05 * ts), { color: pal.body, k: 0.07, bone: HEAD });
    S.add(shape.cone(mid.toArray(), mid.clone().add(V(0.035 * ts, 0.05 * ts, 0.07 * ts)).toArray(), 0.05 * ts, 0.03 * ts), { color: pal.body, k: 0.03, bone: HEAD });
    const side = crown.clone().add(V(-0.08 * ts, -0.04 * ts, 0.0));
    S.add(shape.cone(side.toArray(), side.clone().add(V(-0.06 * ts, 0.09 * ts, 0.03 * ts)).toArray(), 0.05 * ts, 0.026 * ts), { color: pal.body, k: 0.05, bone: HEAD });
  } else if (plan.top.type === 'drop') {
    S.add(shape.cone(crown.clone().add(V(0, -0.02 * ts, 0)).toArray(), crown.clone().add(V(0, 0.27 * ts, -0.02 * ts)).toArray(), 0.11 * ts, 0.018 * ts), { color: pal.accent, k: 0.06, bone: HEAD, colorK: 0.025 });
  } else if (plan.top.type === 'spikes') {
    for (const [x, tilt, len] of [[-0.15, 0.5, 0.22], [0, 0, 0.32], [0.15, -0.5, 0.22]]) {
      const base = crown.clone().add(V(x * ts, -0.04 * ts, 0));
      const dir = V(-sin(tilt), cos(tilt), -0.1).normalize();
      S.add(shape.cone(base.toArray(), base.clone().addScaledVector(dir, len * ts).toArray(), 0.075 * ts, 0.012 * ts), { color: pal.accent, k: 0.035, bone: HEAD, colorK: 0.02 });
    }
  }

  // Patas
  for (const side of [-1, 1]) {
    const f = plan.feet;
    S.add(shape.ellipsoid([side * w * 0.24, 0.045 * f, d * (plan.blob ? 0.3 : 0.28)], [0.12 * f, 0.08 * f, 0.15 * f]), { color: sock, k: 0.035, bone: BODY });
  }
  if (plan.arms) {
    for (const side of [-1, 1]) {
      const bone = side < 0 ? ARM_L : ARM_R;
      const shoulder = V(side * w * 0.42, h * 0.6, d * 0.1);
      const paw = V(side * w * 0.39, h * 0.46, d * 0.3);
      S.add(shape.cone(shoulder.toArray(), paw.toArray(), 0.062, 0.074), { color: sock, k: 0.055, bone });
      bonePos[bone].copy(shoulder);
    }
  }

  // Cola: asoma por un lado para verse desde delante.
  const tsz = plan.tail.size;
  const tailBase = V(w * 0.24, h * 0.26, -d * 0.4);
  bonePos[TAIL].copy(tailBase);
  const tailDir = V(0.55, 0.5, -0.65).normalize();
  if (plan.tail.type === 'pom') {
    S.add(shape.sphere(tailBase.clone().addScaledVector(tailDir, 0.08 * tsz).toArray(), 0.12 * tsz), { color: pal.light, k: 0.04, bone: TAIL });
  } else if (plan.tail.type === 'leaf') {
    S.add(shape.leaf(tailBase.toArray(), quatFromTo(Y, tailDir), 0.44 * tsz, 0.22 * tsz, 0.05, 0.2), { color: pal.accent, k: 0.03, bone: TAIL, colorK: 0.03 });
  } else if (plan.tail.type === 'drop') {
    S.add(shape.cone(tailBase.toArray(), tailBase.clone().addScaledVector(tailDir, 0.36 * tsz).toArray(), 0.1 * tsz, 0.02 * tsz), { color: pal.accent, k: 0.04, bone: TAIL, colorK: 0.03 });
  } else {
    S.add(shape.cone(tailBase.toArray(), tailBase.clone().addScaledVector(tailDir, 0.4 * tsz).toArray(), 0.09 * tsz, 0.01 * tsz), { color: pal.accent, k: 0.03, bone: TAIL, colorK: 0.02 });
  }

  // Piezas de adulta que van fundidas con el cuerpo
  if (plan.extra === 'mane' && plan.family === 'pelusa') {
    for (let i = 0; i < 12; i++) {
      const a = (i / 12) * PI * 2;
      const r = w * 0.42;
      S.add(shape.sphere([sin(a) * r, h * 0.9 + (i % 2) * 0.02, cos(a) * r * 0.92], 0.13), { color: pal.light, k: 0.06, bone: BODY });
    }
  } else if (plan.extra === 'horns') {
    for (let i = 0; i < 3; i++) {
      const base = V(0, h * (0.78 - i * 0.2), -d * (0.44 + i * 0.03));
      const dir = V(0, 0.45 - i * 0.12, -1).normalize();
      S.add(shape.cone(base.toArray(), base.clone().addScaledVector(dir, 0.2 - i * 0.03).toArray(), 0.07, 0.012), { color: pal.accent, k: 0.03, bone: BODY, colorK: 0.02 });
    }
  }

  // Dibujo del ADN
  if (plan.pattern === 'motas') {
    const count = 4 + Math.round(plan.patternAmt * 6);
    for (let i = 0; i < count; i++) {
      const size = rng.range(0.05, 0.09) * unit;
      const yaw = rng.range(0.85, 2.3) * (rng.chance(0.5) ? 1 : -1);
      S.paint(shape.sphere(onHost(yaw, rng.range(-0.1, 1.05)).toArray(), size), { color: pal.accent, soft: 0.008 });
    }
  } else if (plan.pattern === 'rayas') {
    // Tres rayas que pasan por encima de la cabeza, de la frente a la nuca.
    const width = 0.032 * unit * (0.7 + plan.patternAmt * 0.5);
    for (const yaw of [-0.36, 0, 0.36]) {
      let prev = null;
      for (let i = 0; i <= 6; i++) {
        const pitch = (plan.blob ? 0.9 : 0.8) + i * 0.22;
        const p = onHost(pitch > PI / 2 ? yaw + PI : yaw, pitch > PI / 2 ? PI - pitch : pitch);
        if (prev) S.paint(shape.capsule(prev.toArray(), p.toArray(), width), { color: pal.accent, soft: 0.008 });
        prev = p;
      }
    }
  }
  if (plan.belly) {
    const center = plan.blob ? [0, h * 0.27, d * 0.46] : [0, h * 0.42, d * 0.47];
    const radii = plan.blob ? [w * 0.3, h * 0.2, d * 0.16] : [w * 0.3, h * 0.3, d * 0.16];
    S.paint(shape.ellipsoid(center, radii), { color: pal.light, soft: 0.012 });
  }

  // ── Malla y huesos ──
  const bodyGeometry = once(geo, 'body', () => S.build({ cell, bones: BONES, aoReach: 0.2, ao: detail === 'tiny' ? 0 : 1 }));
  const finish = { plush: 'plush', matte: 'matte', gloss: 'gloss' }[plan.finish] ?? 'matte';
  const mesh = new THREE.SkinnedMesh(bodyGeometry, clay({ finish }));
  // Sin sombra propia: la oclusión horneada ya marca los pliegues y así
  // la piel queda limpia, sin el grano del mapa de sombras.
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

  const root = new THREE.Group();
  const bounce = new THREE.Group();
  const squash = new THREE.Group();
  root.add(bounce);
  bounce.add(squash);
  squash.add(mesh);
  root.scale.setScalar(plan.scale);
  const headPivot = bones[HEAD];
  const headBase = bonePos[HEAD];
  // Lo que cuelga de la cabeza se calcula en coordenadas del cuerpo y se
  // pasa a las de la cabeza restando dónde está su hueso.
  const toHead = (object) => {
    object.position.sub(headBase);
    headPivot.add(object);
    return object;
  };
  const owned = { geometries: [], materials: [] };
  const own = (thing) => {
    if (thing.isMaterial) owned.materials.push(thing);
    else owned.geometries.push(thing);
    return thing;
  };
  const decal = (name, center, normal, width, height, material, up = Y) => {
    const geometry = once(geo, name, () => {
      const g = surfacePatch(S, center, normal, up, width, height, 8, 0.0018 * unit);
      g.translate(-headBase.x, -headBase.y, -headBase.z);
      return g;
    });
    const m = new THREE.Mesh(geometry, material);
    m.renderOrder = 2;
    headPivot.add(m);
    return m;
  };

  // Ojos
  const gap = plan.eyes.gap * 1.08;
  const eyeR = 0.044 * unit * plan.eyes.size;
  const tall = plan.eyes.style === 1 ? 1.32 : 1.14;
  const content = plan.eyes.lids >= 0.35;
  const eyes = [];
  for (const side of [-1, 1]) {
    const { point, normal } = S.project(onHost(side * gap, facePitch));
    const group = new THREE.Group();
    group.position.copy(point);
    group.quaternion.copy(faceFrame(normal));
    toHead(group);
    const open = new THREE.Group();
    const bead = new THREE.Mesh(BALL, EYE);
    bead.scale.set(eyeR, eyeR * tall, eyeR * 0.62);
    bead.position.z = -eyeR * 0.12;
    const glint = new THREE.Mesh(BALL, GLINT);
    glint.scale.set(eyeR * 0.3, eyeR * 0.34, eyeR * 0.12);
    glint.position.set(-eyeR * 0.32, eyeR * 0.42 * tall, eyeR * 0.42);
    open.add(bead, glint);
    if (plan.eyes.style === 2) {
      const spark = new THREE.Mesh(BALL, GLINT);
      spark.scale.set(eyeR * 0.15, eyeR * 0.15, eyeR * 0.08);
      spark.position.set(eyeR * 0.3, -eyeR * 0.3 * tall, eyeR * 0.46);
      open.add(spark);
    }
    group.add(open);
    const lidMat = own(decalMaterial(lidTexture('sleep')));
    const closed = decal(`lid${side}`, point, normal, eyeR * 3.4, eyeR * 2.55, lidMat);
    closed.visible = content;
    open.visible = !content;
    eyes.push({ group, open, closed, side, r: eyeR, tall, content, restLid: content ? 0 : plan.eyes.lids, lidValue: 0 });
  }

  // Boca
  const mouthKind = plan.family === 'pelusa' ? 'cat' : 'smile';
  const mouthAt = S.project(onHost(0, facePitch - (plan.blob ? 0.22 : 0.25)));
  const mouthMat = own(decalMaterial(mouthTexture(mouthKind, plan.fang)));
  const mouth = decal('mouth', mouthAt.point, mouthAt.normal, 0.2 * unit, 0.125 * unit, mouthMat);
  const mouths = Object.fromEntries(['smile', 'sad', 'open', 'flat'].map((name) => [name, mouthTexture(name === 'smile' ? mouthKind : name, plan.fang)]));

  // Nariz de las peludas
  if (plan.family === 'pelusa') {
    const { point, normal } = S.project(onHost(0, facePitch - 0.1));
    const nose = new THREE.Mesh(BALL, own(new THREE.MeshPhysicalMaterial({ color: pal.nose, ...NOSE_SHINE })));
    nose.scale.set(0.026 * unit, 0.018 * unit, 0.016 * unit);
    nose.position.copy(point).addScaledVector(normal, 0.004 * unit);
    nose.quaternion.copy(faceFrame(normal));
    toHead(nose);
  }

  // Coloretes
  const cheeks = [];
  for (const side of [-1, 1]) {
    const { point, normal } = S.project(onHost(side * (gap + 0.3), facePitch - 0.15));
    const material = own(decalMaterial(blushTexture(), { color: '#ff8fa9', opacity: 0.62 }));
    const cheek = decal(`cheek${side}`, point, normal, 0.2 * unit * plan.cheeks, 0.13 * unit * plan.cheeks, material);
    cheeks.push(cheek);
  }

  if (plan.brows) {
    const lift = plan.eyes.lids > 0.2 ? 0.34 : 0.24;
    for (const side of [-1, 1]) {
      const { point, normal } = S.project(onHost(side * gap * 0.95, facePitch + lift));
      decal(`brow${side}`, point, normal, eyeR * 3.3, eyeR * 1.65, own(decalMaterial(browTexture(side))));
    }
  }

  // ── Piezas aparte ──
  const partMat = (finishName) => clay({ finish: finishName });
  const partMesh = (name, make, material = partMat(finish)) => {
    const geometry = once(geo, name, make);
    const m = new THREE.Mesh(geometry, material);
    m.castShadow = true;
    m.receiveShadow = true;
    return m;
  };

  let top = new THREE.Group();
  if (plan.top.type === 'sprout') {
    top = partMesh('sprout', () => {
      const P = new Sculpt();
      const base = crown.clone().add(V(0, -0.04 * ts, 0));
      const tip = crown.clone().add(V(0, 0.17 * ts, 0.01 * ts));
      P.add(shape.capsule(base.toArray(), tip.toArray(), 0.022 * ts), { color: pal.leaf, k: 0 });
      for (const side of [-1, 1]) {
        const dir = V(side * 0.95, 0.55, 0.12).normalize();
        P.add(shape.leaf(tip.toArray(), quatFromTo(Y, dir).multiply(new THREE.Quaternion().setFromAxisAngle(Y, side * 0.25)), 0.27 * ts, 0.16 * ts, 0.026 * ts, 0.18), { color: pal.leaf, k: 0.02 });
      }
      return P.build({ cell: 0.007 * ts, aoReach: 0.05 * ts });
    }, partMat('matte'));
    toHead(top);
  } else if (plan.top.type === 'cherry') {
    top = new THREE.Group();
    top.add(partMesh('cream', () => {
      const P = new Sculpt();
      const c = crown.clone().add(V(0, -0.01 * ts, 0));
      P.add(shape.ellipsoid(c.toArray(), [0.13 * ts, 0.07 * ts, 0.13 * ts]), { color: pal.ivory, k: 0 });
      P.add(shape.ellipsoid(c.clone().add(V(0, 0.08 * ts, 0)).toArray(), [0.09 * ts, 0.06 * ts, 0.09 * ts]), { color: pal.ivory, k: 0.03 });
      P.add(shape.cone(c.clone().add(V(0, 0.12 * ts, 0)).toArray(), c.clone().add(V(0.02 * ts, 0.2 * ts, 0)).toArray(), 0.05 * ts, 0.01 * ts), { color: pal.ivory, k: 0.03 });
      return P.build({ cell: 0.008 * ts, aoReach: 0.06 * ts });
    }, partMat('gloss')));
    top.add(partMesh('cherry', () => {
      const P = new Sculpt();
      const c = crown.clone().add(V(0.05 * ts, 0.25 * ts, 0.03 * ts));
      P.add(shape.sphere(c.toArray(), 0.072 * ts), { color: '#e5465e', k: 0 });
      P.sub(shape.sphere(c.clone().add(V(0, 0.075 * ts, 0)).toArray(), 0.02 * ts), { k: 0.02 });
      P.add(shape.capsule(c.clone().add(V(0, 0.04 * ts, 0)).toArray(), c.clone().add(V(0.05 * ts, 0.17 * ts, -0.01 * ts)).toArray(), 0.008 * ts), { color: '#6b4632', k: 0.01 });
      return P.build({ cell: 0.006 * ts, aoReach: 0.04 * ts });
    }, partMat('glaze')));
    toHead(top);
  } else if (plan.top.type === 'moon') {
    top = partMesh('moon', () => {
      const P = new Sculpt();
      const c = crown.clone().add(V(0, 0.16 * ts, 0));
      P.add(shape.ellipsoid(c.toArray(), [0.13 * ts, 0.13 * ts, 0.05 * ts]), { color: '#fff0b8', k: 0 });
      P.sub(shape.ellipsoid(c.clone().add(V(0.06 * ts, 0.05 * ts, 0)).toArray(), [0.115 * ts, 0.115 * ts, 0.08 * ts]), { k: 0.01 });
      return P.build({ cell: 0.006 * ts, ao: 0 });
    }, own(new THREE.MeshStandardMaterial({ color: '#fff0b8', emissive: '#ffd76e', emissiveIntensity: 0.75, roughness: 0.4 })));
    toHead(top);
  }

  // Alas
  const wings = [];
  if (plan.extra === 'wings') {
    for (const side of [-1, 1]) {
      const pivot = new THREE.Group();
      pivot.position.set(side * w * 0.32, h * 0.72, -d * 0.3);
      const wing = partMesh(`wing-${plan.family}`, () => {
        const P = new Sculpt();
        if (plan.family === 'pelusa') {
          for (let i = 0; i < 3; i++) {
            const a = -i * 0.42;
            const len = 0.22 - i * 0.035;
            P.add(shape.ellipsoid([sin(-a) * len * 0.6, cos(a) * len * 0.75, 0], [0.075, len * 0.75, 0.04], [0, 0, a]), { color: pal.light, k: 0.03 });
          }
        } else if (plan.family === 'hoja') {
          P.add(shape.leaf([0, 0, 0], [0, 0, -0.15], 0.5, 0.27, 0.04, 0.12), { color: pal.accent, k: 0 });
          P.paint(shape.capsule([0, 0.02, 0], [0.02, 0.42, 0.03], 0.008), { color: pal.leaf, soft: 0.006 });
        } else {
          for (let i = 0; i < 3; i++) {
            const a = 0.2 - i * 0.42;
            P.add(shape.leaf([0, 0, 0], [0, 0, a], 0.42 - i * 0.06, 0.2, 0.035, 0.1), { color: pal.accent, k: 0.04 });
          }
        }
        return P.build({ cell: 0.009, aoReach: 0.06 });
      }, partMat(plan.family === 'pelusa' ? 'plush' : finish));
      wing.scale.x = side;
      pivot.add(wing);
      pivot.rotation.z = side * -0.95;
      bones[BODY].add(pivot);
      wings.push({ pivot, side, rest: side * -0.95 });
    }
  }

  // Corona de adorno
  if (plan.extra === 'crown') {
    const ring = partMesh(`crown-${plan.family}`, () => {
      const P = new Sculpt();
      const count = 7;
      for (let i = 0; i < count; i++) {
        const angle = (i / count) * PI * 2;
        const { point, normal } = S.project(onHost(angle, 1.0));
        const c = point.clone().addScaledVector(normal, 0.03 * unit);
        if (plan.family === 'hoja') {
          const q = faceFrame(normal, Z);
          for (let p = 0; p < 5; p++) {
            const a = (p / 5) * PI * 2;
            const offset = V(cos(a) * 0.045 * unit, sin(a) * 0.045 * unit, 0).applyQuaternion(q);
            P.add(shape.ellipsoid(c.clone().add(offset).toArray(), [0.04 * unit, 0.028 * unit, 0.018 * unit], q.clone().multiply(new THREE.Quaternion().setFromAxisAngle(Z, a))), { color: pal.accent, k: 0.01 });
          }
          P.add(shape.sphere(c.clone().addScaledVector(normal, 0.01 * unit).toArray(), 0.024 * unit), { color: '#ffd166', k: 0.006 });
        } else {
          P.add(shape.sphere(c.toArray(), 0.055 * unit), { color: plan.family === 'gota' ? pal.ivory : pal.light, k: 0 });
        }
      }
      return P.build({ cell: 0.008 * unit, aoReach: 0.05 });
    }, partMat(plan.family === 'gota' ? 'glaze' : plan.family === 'pelusa' ? 'plush' : 'matte'));
    toHead(ring);
  }

  if (plan.extra === 'horns') {
    toHead(partMesh('horns', () => {
      const P = new Sculpt();
      for (const side of [-1, 1]) {
        const base = onHost(side * 0.6, 1.08);
        const mid = base.clone().add(V(side * 0.05 * unit, 0.15 * unit, -0.02 * unit));
        const tip = mid.clone().add(V(side * 0.1 * unit, 0.09 * unit, -0.04 * unit));
        P.add(shape.cone(base.clone().add(V(0, -0.04 * unit, 0)).toArray(), mid.toArray(), 0.07 * unit, 0.05 * unit), { color: pal.ivory, k: 0 });
        P.add(shape.cone(mid.toArray(), tip.toArray(), 0.05 * unit, 0.012 * unit), { color: pal.ivory, k: 0.03 });
      }
      return P.build({ cell: 0.008 * unit, aoReach: 0.05 });
    }, partMat('gloss')));
  }

  if (plan.extra === 'mane' && plan.family !== 'pelusa') {
    const collar = plan.family === 'hoja'
      ? partMesh('mane-hoja', () => {
        const P = new Sculpt();
        for (let i = 0; i < 11; i++) {
          const a = (i / 11) * PI * 2;
          const r = w * 0.36;
          const base = V(sin(a) * r, h * 0.92, cos(a) * r * 0.92);
          const dir = V(sin(a), -0.35, cos(a)).normalize();
          P.add(shape.leaf(base.clone().addScaledVector(dir, -0.04).toArray(), quatFromTo(Y, dir), 0.3, 0.19, 0.035, -0.12), { color: i % 2 ? pal.accent : pal.leaf, k: 0.02 });
        }
        return P.build({ cell: 0.009, aoReach: 0.06 });
      }, partMat('matte'))
      : partMesh('mane-gota', () => {
        const P = new Sculpt();
        for (let i = 0; i < 11; i++) {
          const a = (i / 11) * PI * 2;
          const r = w * 0.42;
          P.add(shape.sphere([sin(a) * r, h * 0.9, cos(a) * r * 0.92], 0.1 + (i % 3) * 0.012), { color: pal.light, k: 0 });
        }
        return P.build({ cell: 0.012, ao: 0.5, aoReach: 0.05 });
      }, own(new THREE.MeshPhysicalMaterial({ color: pal.light.clone().lerp(new THREE.Color('#ffffff'), 0.35), roughness: 0.12, clearcoat: 1, clearcoatRoughness: 0.05, transparent: true, opacity: 0.86, sheen: 0.6, sheenColor: new THREE.Color('#e8f6ff') })));
    bones[BODY].add(collar);
  }

  let halo = null;
  if (plan.extra === 'halo') {
    halo = new THREE.Mesh(own(new THREE.TorusGeometry(0.22 * unit, 0.026 * unit, 16, 48)), own(new THREE.MeshStandardMaterial({ color: '#ffe08a', emissive: '#ffcf5c', emissiveIntensity: 0.9, roughness: 0.3, metalness: 0.2 })));
    halo.rotation.set(PI / 2 - 0.22, 0, 0.1);
    const lift = plan.top.type === 'sprout' ? 0.46 : 0.32;
    halo.position.set(0, host.center.y + host.ry + lift * unit, -0.03 * unit);
    toHead(halo);
    halo.userData.y = halo.position.y;
  }

  // ── Anclajes para la ropa ──
  const hostLocal = { center: host.center.clone().sub(headBase), rx: host.rx, ry: host.ry, rz: host.rz };
  const anchors = {
    // En coordenadas de la cabeza, salvo `neck`, que va en las del cuerpo.
    hat: { y: hostLocal.center.y + host.ry, r: host.rx },
    face: { y: hostLocal.center.y + host.ry * sin(facePitch), z: host.rz, gap, r: host.rx, host: hostLocal, pitch: facePitch },
    neck: neckOf(S, plan, host),
  };

  const height = (host.center.y + host.ry) * plan.scale;

  return {
    object: root,
    plan,
    height,
    anchors,
    rig: { root, bounce, squash, headPivot, eyes, mouths, mouth, cheeks, ears: earRig(bones), arms: plan.arms ? armRig(bones) : [], feet: [], tail: bones[TAIL], wings, top, halo, mesh },
    dispose() {
      for (const geometry of owned.geometries) geometry.dispose?.();
      for (const material of owned.materials) material.dispose?.();
      if (detail !== 'high') {
        for (const geometry of Object.values(geo)) geometry?.dispose?.();
      }
      mesh.skeleton.dispose();
    },
  };
}

function earRig(bones) {
  return [{ pivot: bones[EAR_L], side: -1, rest: 0 }, { pivot: bones[EAR_R], side: 1, rest: 0 }];
}

function armRig(bones) {
  return [{ pivot: bones[ARM_L], side: -1, rest: 0 }, { pivot: bones[ARM_R], side: 1, rest: 0 }];
}

// Radio de la figura a la altura `y` en la dirección (dx, dz).
function radiusAt(S, y, dx, dz) {
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
function neckOf(S, plan, host) {
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

// ── Animación ─────────────────────────────────────────────────

// Envuelve una criatura construida y le da vida: respira, parpadea,
// mira y reacciona. `update` se llama en cada fotograma.
export class Actor {
  constructor(built) {
    Object.assign(this, built);
    this.mood = 'bien';
    this.blinkAt = 1.5;
    this.blink = 0;
    this.gaze = new THREE.Vector2();
    this.gazeTarget = new THREE.Vector2();
    this.gazeAt = 0;
    this.lean = 0;
    this.leanTarget = 0;
    this.busy = false;
    this.talk = 0;
    this.shiver = 0;
    this.squint = false;
    this.face = 'smile';
    this.wear = new Map();
    this.twitchAt = 3 + Math.random() * 4;
    this.twitch = 0;
    this.idleAt = 6 + Math.random() * 6;
    this.still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  setMouth(name) {
    if (this.face === name) return;
    this.face = name;
    this.rig.mouth.material.map = this.rig.mouths[name] ?? this.rig.mouths.smile;
  }

  setMood(mood) {
    this.mood = mood;
    this.applyEyes();
    if (!this.busy) this.setMouth(this.restMouth());
    const pale = mood === 'enferma';
    for (const cheek of this.rig.cheeks) {
      cheek.material.color.set(pale ? '#9ec3ea' : '#ff8fa9');
      cheek.material.opacity = mood === 'feliz' ? 0.8 : 0.62;
    }
  }

  // Ojos abiertos (cuentas) o cerrados (arcos pintados).
  applyEyes() {
    const asleep = this.mood === 'dormida';
    for (const eye of this.rig.eyes) {
      const shut = asleep || eye.content || this.squint;
      eye.open.visible = !shut;
      eye.closed.visible = shut;
      if (shut) eye.closed.material.map = lidTexture(this.squint && !asleep ? 'happy' : 'sleep');
    }
  }

  setSquint(on) {
    if (this.squint === on) return;
    this.squint = on;
    this.applyEyes();
  }

  restMouth() {
    if (this.mood === 'dormida') return 'flat';
    if (this.mood === 'mal' || this.mood === 'fatal' || this.mood === 'enferma') return 'sad';
    if (this.mood === 'regular') return 'flat';
    return 'smile';
  }

  lookAt(x, y) {
    this.gazeTarget.set(clamp(x, -1, 1), clamp(y, -1, 1));
    this.gazeAt = performance.now() / 1000 + 2.5;
  }

  update(t, dt) {
    const { rig, mood } = this;
    const asleep = mood === 'dormida';
    const low = mood === 'mal' || mood === 'fatal' || mood === 'enferma';
    const happy = mood === 'feliz';

    // Respiración: aplastar y estirar muy poco, más lento al dormir.
    const speed = asleep ? 1.1 : low ? 1.7 : 2.4;
    const depth = asleep ? 0.034 : 0.022;
    const breath = Math.sin(t * speed) * depth;
    if (!this.busy) {
      rig.squash.scale.set(1 - breath * 0.6, 1 + breath, 1 - breath * 0.6);
      rig.root.rotation.z = Math.sin(t * 0.9) * (happy ? 0.03 : 0.014);
    }

    // Mirada: sigue al dedo un rato y luego vaga sola.
    if (t > this.gazeAt) {
      this.gazeTarget.set((Math.random() - 0.5) * 0.9, (Math.random() - 0.5) * 0.4);
      this.gazeAt = t + 2 + Math.random() * 3;
    }
    this.gaze.lerp(this.gazeTarget, 1 - Math.exp(-dt * 5));
    this.lean += (this.leanTarget - this.lean) * (1 - Math.exp(-dt * 7));
    const droop = asleep ? 0.2 : low ? 0.16 : 0;
    if (!this.busy) {
      rig.headPivot.rotation.set(
        droop - this.gaze.y * 0.14,
        this.gaze.x * 0.3,
        this.lean * 0.22 + (asleep ? 0.1 : 0) + Math.sin(t * 0.7) * 0.025,
      );
    }
    for (const eye of rig.eyes) {
      eye.open.position.set(this.gaze.x * eye.r * 0.3, this.gaze.y * eye.r * 0.2, 0);
    }

    // Parpadeo: la cuenta se aplasta un instante.
    if (!asleep && t > this.blinkAt) {
      this.blink = 0.12;
      this.blinkAt = t + 2.2 + Math.random() * 3.6;
    }
    this.blink = Math.max(0, this.blink - dt);
    const shut = this.blink > 0 ? 1 : low ? 0.32 : 0;
    for (const eye of rig.eyes) {
      const lid = Math.max(eye.restLid, shut);
      eye.lidValue += (lid - eye.lidValue) * (1 - Math.exp(-dt * 40));
      eye.open.scale.y = Math.max(0.08, 1 - eye.lidValue * 0.95);
    }

    // Orejas, cola, alas y brazos. De vez en cuando una oreja da un respingo.
    if (t > this.twitchAt) {
      this.twitch = 1;
      this.twitchSide = Math.random() < 0.5 ? 0 : 1;
      this.twitchAt = t + 3 + Math.random() * 5;
    }
    this.twitch = Math.max(0, this.twitch - dt * 4);
    const wiggle = Math.sin(t * 1.3) * 0.05 + (happy ? Math.sin(t * 7) * 0.04 : 0);
    rig.ears.forEach((ear, i) => {
      const flick = i === this.twitchSide && !asleep ? Math.sin(this.twitch * Math.PI * 3) * 0.18 * this.twitch : 0;
      ear.pivot.rotation.z = ear.rest + ear.side * (wiggle - (low ? 0.22 : 0) + flick);
    });
    rig.tail.rotation.y = Math.sin(t * (happy ? 7 : 2.2)) * (asleep ? 0.03 : happy ? 0.5 : 0.2);
    for (const wing of rig.wings) wing.pivot.rotation.z = wing.rest + wing.side * Math.sin(t * (happy ? 9 : 2.6)) * (asleep ? 0.03 : 0.22);
    for (const arm of rig.arms) arm.pivot.rotation.z = arm.rest + arm.side * Math.sin(t * 1.9 + arm.side) * 0.08;
    if (rig.halo) rig.halo.position.y = rig.halo.userData.y + Math.sin(t * 1.6) * 0.012;

    if (this.shiver > 0 || mood === 'enferma') {
      this.shiver = Math.max(0, this.shiver - dt);
      rig.bounce.position.x = Math.sin(t * 46) * 0.006;
    } else {
      rig.bounce.position.x = 0;
    }

    // De vez en cuando hace algo por su cuenta, si está bien.
    if (t > this.idleAt) {
      this.idleAt = t + 7 + Math.random() * 8;
      if (!this.busy && !this.still && (mood === 'bien' || happy || mood === 'regular')) this.fidget(happy);
    }

    // Hablar: abre y cierra la boca mientras dura el bocadillo.
    if (this.talk > 0 && !this.busy) {
      this.talk -= dt;
      this.setMouth(Math.sin(t * 16) > 0 ? 'open' : this.restMouth());
      if (this.talk <= 0) this.setMouth(this.restMouth());
    }
  }

  // Reacciones de una vez. Devuelven la línea de tiempo de GSAP.
  act(fn) {
    this.timeline?.kill();
    this.busy = true;
    this.fidgeting = false;
    const tl = gsap.timeline({
      onComplete: () => {
        this.busy = false;
        this.setSquint(false);
        this.setMouth(this.restMouth());
      },
    });
    this.timeline = tl;
    fn(tl, this.rig);
    return tl;
  }

  hop(times = 1, height = 0.28) {
    return this.act((tl, rig) => {
      for (let i = 0; i < times; i++) {
        tl.to(rig.squash.scale, { x: 1.12, y: 0.86, z: 1.12, duration: 0.09, ease: 'power2.out' })
          .to(rig.squash.scale, { x: 0.92, y: 1.12, z: 0.92, duration: 0.12, ease: 'power2.out' })
          .to(rig.bounce.position, { y: height, duration: 0.2, ease: 'power2.out' }, '<')
          .to(rig.bounce.position, { y: 0, duration: 0.18, ease: 'power2.in' })
          .to(rig.squash.scale, { x: 1.14, y: 0.84, z: 1.14, duration: 0.07, ease: 'power1.out' })
          .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.3, ease: 'elastic.out(1, 0.5)' });
      }
      if (times > 1 && this.mood !== 'dormida') this.setSquint(true);
    });
  }

  eat(onBite) {
    this.setMouth('open');
    return this.act((tl, rig) => {
      for (let i = 0; i < 3; i++) {
        tl.to(rig.headPivot.rotation, { x: 0.34, duration: 0.14, ease: 'power2.out' })
          .call(() => { this.setMouth('flat'); this.setSquint(true); onBite?.(i); })
          .to(rig.squash.scale, { x: 1.06, y: 0.94, z: 1.06, duration: 0.08 }, '<')
          .to(rig.headPivot.rotation, { x: 0.02, duration: 0.16, ease: 'power2.inOut' })
          .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.14 }, '<')
          .call(() => { this.setSquint(false); this.setMouth(i < 2 ? 'open' : 'smile'); });
      }
      tl.to({}, { duration: 0.15 });
    });
  }

  shake() {
    this.setMouth('flat');
    return this.act((tl, rig) => {
      tl.to(rig.headPivot.rotation, { y: 0.4, duration: 0.11, ease: 'power2.out' })
        .to(rig.headPivot.rotation, { y: -0.4, duration: 0.18, ease: 'power2.inOut' })
        .to(rig.headPivot.rotation, { y: 0.3, duration: 0.16, ease: 'power2.inOut' })
        .to(rig.headPivot.rotation, { y: 0, duration: 0.2, ease: 'power2.out' });
    });
  }

  wash() {
    this.setMouth('open');
    return this.act((tl, rig) => {
      this.setSquint(true);
      tl.to(rig.root.rotation, { z: 0.12, duration: 0.12, ease: 'power1.inOut', yoyo: true, repeat: 7 })
        .to(rig.root.rotation, { z: 0, duration: 0.2, ease: 'power2.out' })
        .to(rig.squash.scale, { x: 1.1, y: 0.9, z: 1.1, duration: 0.1 })
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.4, ease: 'elastic.out(1, 0.45)' });
    });
  }

  nuzzle() {
    this.setMouth('smile');
    return this.act((tl, rig) => {
      this.setSquint(true);
      tl.to(rig.squash.scale, { x: 1.08, y: 0.92, z: 1.08, duration: 0.16, ease: 'power2.out' })
        .to(rig.headPivot.rotation, { z: this.leanTarget * 0.34, x: 0.1, duration: 0.2, ease: 'power2.out' }, '<')
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.5, ease: 'elastic.out(1, 0.4)' })
        .to(rig.headPivot.rotation, { z: 0, x: 0, duration: 0.35, ease: 'power2.inOut' }, '<0.1');
    });
  }

  spin() {
    this.setMouth('open');
    return this.act((tl, rig) => {
      this.setSquint(true);
      tl.to(rig.bounce.position, { y: 0.34, duration: 0.26, ease: 'power2.out' })
        .to(rig.root.rotation, { y: Math.PI * 2, duration: 0.6, ease: 'power2.inOut' }, '<')
        .to(rig.bounce.position, { y: 0, duration: 0.22, ease: 'power2.in' }, '>-0.24')
        .set(rig.root.rotation, { y: 0 })
        .to(rig.squash.scale, { x: 1.14, y: 0.84, z: 1.14, duration: 0.08 })
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.4, ease: 'elastic.out(1, 0.45)' });
    });
  }

  // Gestos de reposo: ladear la cabeza con curiosidad, balancearse o,
  // si está contenta, un saltito.
  // Se pueden cortar: un toque o un cuidado los sustituye (`fidgeting`).
  fidget(happy) {
    this.fidgetMove(happy);
    this.fidgeting = true;
  }

  fidgetMove(happy) {
    const pick = Math.random();
    if (happy && pick < 0.35) return this.hop(1, 0.1);
    const side = Math.random() < 0.5 ? -1 : 1;
    if (pick < 0.7) {
      return this.act((tl, rig) => {
        tl.to(rig.headPivot.rotation, { z: side * 0.26, y: side * 0.18, duration: 0.45, ease: 'back.out(2)' })
          .to(rig.headPivot.rotation, { z: side * 0.2, duration: 0.9, ease: 'sine.inOut' })
          .to(rig.headPivot.rotation, { z: 0, y: 0, duration: 0.5, ease: 'power2.inOut' });
      });
    }
    return this.act((tl, rig) => {
      tl.to(rig.root.rotation, { z: side * 0.08, duration: 0.3, ease: 'sine.inOut' })
        .to(rig.root.rotation, { z: -side * 0.08, duration: 0.5, ease: 'sine.inOut' })
        .to(rig.root.rotation, { z: 0, duration: 0.4, ease: 'sine.out' })
        .to(rig.squash.scale, { x: 1.05, y: 0.95, z: 1.05, duration: 0.12 }, '<')
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.4, ease: 'elastic.out(1, 0.5)' });
    });
  }

  say(seconds) {
    this.talk = seconds;
  }

  // Para la foto: si está a mitad de parpadeo, abre los ojos ya.
  openEyes() {
    if (this.blink <= 0) return;
    this.blink = 0;
    for (const eye of this.rig.eyes) {
      eye.lidValue = eye.restLid;
      eye.open.scale.y = Math.max(0.08, 1 - eye.lidValue * 0.95);
    }
  }
}
