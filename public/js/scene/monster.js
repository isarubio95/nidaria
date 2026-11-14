// Monstruos: las tres familias de criaturas. Pelusa es un zorro de fuego
// con cola de llama; Hoja, un dinosaurio con hojas por cresta y por cola;
// Gota, un anfibio con aletas. De crías son una bola con patitas; de
// jóvenes y adultos se ponen de pie, con garras, y ganan piezas según la
// crianza. Todo se esculpe como arcilla (sdf.js) y la cara va encima
// (creature-kit.js).

import * as THREE from 'three';
import { createRng, clamp } from '/shared/rng.js';
import { planFor } from './plan.js';
import { Sculpt, shape, surfacePatch } from './sdf.js';
import { clay } from './materials.js';
import {
  V, Y, Z, BODY, HEAD, EAR_L, EAR_R, ARM_L, ARM_R, TAIL, BONES, hsl,
  mouthTexture, lidTexture, blushTexture, browTexture, decalMaterial,
  sculptCache, once, faceFrame, quatFromTo, earRig, armRig, neckOf, makeEye, skinned,
} from './creature-kit.js';

const { sin, cos, PI, max, min, abs } = Math;
const col = (c) => new THREE.Color(c);

const IRIS = {
  pelusa: ['#c23a26', '#2f63c9', '#d99a1c'],
  hoja: ['#c23a26', '#e0761a', '#d99a1c'],
  gota: ['#d99a1c', '#cf4a26', '#1f2f7a'],
};

// Colores con fuerza: el ADN da el tono; aquí se llevan a juguete vivo,
// con barriga clara, marcas oscuras y un acento que contrasta.
function palette(genes, plan) {
  // El verde vivo enseguida parece plástico: la familia hoja va más apagada.
  // El zorro tiene que verse naranja fuego, no color arena.
  const s = clamp(genes.sat + (plan.family === 'hoja' ? 0 : 10) + plan.tone.sat, plan.family === 'pelusa' ? 64 : 44, 86);
  const l = clamp(genes.light - (plan.family === 'pelusa' ? 33 : 30) + plan.tone.light, 28, 46);
  const body = hsl(genes.hue, s, l);
  const warm = plan.family === 'pelusa';
  return {
    body,
    dark: hsl(genes.hue + (warm ? -8 : 6), clamp(s - 10, 30, 80), l - 22),
    // Barriga crema, tirando a amarillo: nunca blanco puro.
    light: hsl(warm ? 40 : genes.hue + 20, warm ? 70 : 45, 80),
    accent: hsl(genes.hue2, clamp(s + 6, 50, 92), clamp(l + 4, 42, 64)),
    // La llama de la cola y el mechón: un tono más cálido y brillante.
    flame: warm ? hsl(genes.hue + 28, 95, 60) : hsl(genes.hue2, 90, 62),
    leaf: hsl(plan.family === 'hoja' ? genes.hue + 18 : 118, 62, clamp(l - 4, 30, 50)),
    ivory: col('#fff2dc'),
    claw: col('#f3e6d0'),
    horn: hsl(genes.hue, 18, 22),
    nose: hsl(genes.hue, 30, 16),
    // Iris con carácter: tres por familia, según el ADN.
    iris: IRIS[plan.family][genes.eyeStyle % 3],
  };
}

export function buildMonster(formId, genes, { detail = 'high' } = {}) {
  const plan = planFor(formId, genes);
  const pal = palette(genes, plan);
  const rng = createRng((genes.seed ^ 0x9e3779b9) >>> 0);
  const cacheKey = `mon|${formId}|${genes.seed}|${genes.hue}|${genes.hue2}|${genes.sat}|${genes.light}|${genes.pattern}|${genes.patternAmt}|${JSON.stringify(genes.build)}|${genes.eyeStyle}`;
  const geo = detail === 'high' ? sculptCache(cacheKey) : {};
  const cell = { high: 0.016, 'high-thumb': 0.018, low: 0.026, tiny: 0.04 }[detail] ?? 0.026;
  const sock = plan.pattern === 'calcetines' ? pal.accent : pal.body;
  const fam = plan.family;

  const S = new Sculpt({ ground: 0, groundK: 0.03 });
  const bonePos = Array.from({ length: BONES }, () => V());

  // ── Cuerpo ──
  // `host` es el elipsoide que lleva la cara: la cabeza, o el cuerpo
  // entero en las crías.
  let host;
  let bodyTop;
  const { w, h, d } = plan.body;
  const legH = plan.legs;
  const shoulders = [];
  if (plan.blob) {
    host = { center: V(0, h * 0.5, 0), rx: w / 2, ry: h / 2, rz: d / 2 };
    bonePos[HEAD].set(0, h * 0.42, 0);
    S.add(shape.ellipsoid([0, h * 0.5, 0], [w / 2, h / 2, d / 2]), { color: pal.body, k: 0, bone: HEAD });
    S.add(shape.ellipsoid([0, h * 0.3, 0], [w * 0.52, h * 0.3, d * 0.5]), { color: pal.body, k: 0.16, bone: HEAD });
    for (const side of [-1, 1]) {
      foot(V(side * w * 0.24, 0, d * 0.3), 1.15, HEAD);
      S.add(shape.ellipsoid([side * w * 0.47, h * 0.4, d * 0.14], [0.07, 0.1, 0.075], [0, 0, side * 0.5]), { color: sock, k: 0.05, bone: HEAD });
    }
    bodyTop = h;
  } else {
    const ty = legH + h * 0.46;
    bonePos[HEAD].set(0, legH + h * 0.9, 0.02);
    // Torso de huevo: estrecho arriba (el cuello) y con barriga abajo.
    S.add(shape.ellipsoid([0, ty, 0.01], [w * 0.92, h * 0.54, d * 0.95]), { color: pal.body, k: 0, bone: BODY });
    S.add(shape.ellipsoid([0, legH + h * 0.26, 0.0], [w * 1.0, h * 0.34, d * 1.0]), { color: pal.body, k: 0.08, bone: BODY });
    for (const side of [-1, 1]) {
      // Muslo, pie con garras y brazo con su mano.
      S.add(shape.ellipsoid([side * w * 0.58, legH * 0.95, 0.0], [0.11 + w * 0.12, legH * 0.66 + 0.05, 0.125]), { color: pal.body, k: 0.06, bone: BODY });
      foot(V(side * w * 0.62, 0, 0.05), 1.18, BODY);
      const bone = side < 0 ? ARM_L : ARM_R;
      const shoulder = V(side * w * 0.88, legH + h * 0.8, 0.04);
      const hand = V(side * w * 1.18, legH + h * 0.4, 0.14);
      shoulders.push(shoulder);
      bonePos[bone].copy(shoulder);
      S.add(shape.cone(shoulder.toArray(), hand.toArray(), 0.07, 0.058), { color: pal.body, k: 0.05, bone });
      S.add(shape.ellipsoid(hand.toArray(), [0.068, 0.072, 0.065]), { color: fam === 'pelusa' ? pal.dark : sock, k: 0.03, bone });
      for (const t of [-1, 0, 1]) {
        const c = hand.clone().add(V(side * 0.01 + t * 0.022, -0.045, 0.03));
        S.add(shape.cone(c.toArray(), c.clone().add(V(t * 0.006, -0.03, 0.022)).toArray(), 0.014, 0.004), { color: pal.claw, k: 0.008, bone, colorK: 0.004 });
      }
    }
    const { rx, ry, rz } = plan.head;
    host = { center: V(0, legH + h + ry * 0.68, 0.03), rx: rx * 1.06, ry, rz };
    S.add(shape.ellipsoid(host.center.toArray(), [host.rx, ry, rz]), { color: pal.body, k: 0.045, bone: HEAD });
    bodyTop = legH + h;
  }
  const unit = host.rx / 0.5;
  const facePitch = plan.blob ? 0.2 : 0.06;

  function foot(p, s, bone) {
    S.add(shape.ellipsoid([p.x, 0.05 * s, p.z], [0.085 * s, 0.055 * s, 0.12 * s]), { color: sock, k: 0.04, bone });
    for (const t of [-1, 0, 1]) {
      const c = V(p.x + t * 0.04 * s, 0.025 * s, p.z + 0.1 * s - abs(t) * 0.015);
      S.add(shape.cone(c.toArray(), c.clone().add(V(t * 0.008, -0.005, 0.035 * s)).toArray(), 0.018 * s, 0.005), { color: pal.claw, k: 0.008, bone, colorK: 0.004 });
    }
  }

  const onHost = (yaw, pitch) => {
    const cp = cos(pitch);
    return V(host.rx * sin(yaw) * cp, host.ry * sin(pitch), host.rz * cos(yaw) * cp).add(host.center);
  };
  const hostNormal = (p) => {
    const q = p.clone().sub(host.center);
    return V(q.x / host.rx ** 2, q.y / host.ry ** 2, q.z / host.rz ** 2).normalize();
  };
  const basis = (up, forward) => {
    const yv = up.clone().normalize();
    const zv = forward.clone().addScaledVector(yv, -forward.dot(yv)).normalize();
    const xv = new THREE.Vector3().crossVectors(yv, zv);
    return new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(xv, yv, zv));
  };
  // Un tubo curvo que se estrecha (colas, mechones, cuernos).
  const tube = (points, r0, r1, color, bone, k = 0.03, k0 = 0.05) => {
    for (let i = 0; i < points.length - 1; i++) {
      const a = r0 + (r1 - r0) * (i / (points.length - 1));
      const b = r0 + (r1 - r0) * ((i + 1) / (points.length - 1));
      S.add(shape.cone(points[i].toArray(), points[i + 1].toArray(), a, b), { color, k: i === 0 ? k0 : k, bone, colorK: 0.02 });
    }
  };
  const curve = (ctrl, n = 10) => new THREE.CatmullRomCurve3(ctrl).getPoints(n);

  // ── Hocico ──
  let snoutTip = null;
  if (plan.snout > 0.2) {
    const s = plan.snout * (plan.blob ? 0.6 : 1);
    const fox = fam === 'pelusa';
    const c = onHost(0, facePitch - (fox ? 0.36 : 0.42));
    const len = host.rz * (fox ? 0.55 : 0.34) * s;
    const sc = c.clone().add(V(0, 0, len * 0.55));
    const sw = host.rx * (fox ? 0.3 : 0.38);
    S.add(shape.ellipsoid(sc.toArray(), [sw, host.ry * (fox ? 0.2 : 0.24), host.rz * 0.18 + len * 0.6], [fox ? 0.12 : 0.05, 0, 0]), { color: pal.body, k: 0.07, bone: HEAD });
    snoutTip = sc.clone().add(V(0, host.ry * (fox ? 0.06 : 0.1), host.rz * 0.18 + len * 0.6));
  }

  // ── Orejas ──
  const earSize = 0.2 * unit * Math.min(plan.ears.size, 1.4);
  const droop = plan.ears.droop;
  for (const side of [-1, 1]) {
    const bone = side < 0 ? EAR_L : EAR_R;
    const type = plan.ears.type;
    if (type === 'fox') {
      // Orejas grandes de zorro, con el interior oscuro y la punta de acento.
      const base = onHost(side * 0.7, 0.7 - droop * 0.3);
      const dir = V(side * (0.62 + droop * 0.6), 1 - droop * 0.5, -0.15).add(hostNormal(base).multiplyScalar(0.2)).normalize();
      const q = basis(dir, V(side * 0.25, 0, 1));
      const len = earSize * 2.1;
      const start = base.clone().addScaledVector(dir, -len * 0.42);
      S.add(shape.leaf(start.toArray(), q, len * 1.55, len * 0.86, 0.07 * unit, -0.06), { color: pal.body, k: 0.04, bone });
      const inner = start.clone().addScaledVector(dir, len * 0.2).add(V(0, 0, 0.03 * unit).applyQuaternion(q));
      S.paint(shape.leaf(inner.toArray(), q, len * 1.1, len * 0.55, 0.04 * unit, -0.05), { color: pal.dark, soft: 0.01 });
      S.paint(shape.sphere(start.clone().addScaledVector(dir, len * 1.45).toArray(), len * 0.32), { color: pal.dark, soft: 0.02 });
      bonePos[bone].copy(base);
    } else if (type === 'leaf') {
      const s = earSize * 0.9;
      const base = onHost(side * 1.05, 0.55 - droop * 0.25);
      const dir = hostNormal(base).multiplyScalar(1.2).add(V(0, 0.8, -0.3)).normalize().applyAxisAngle(Z, -side * droop * 1.0);
      const q = quatFromTo(Y, dir).multiply(new THREE.Quaternion().setFromAxisAngle(Y, -side * 0.6));
      S.add(shape.leaf(base.clone().addScaledVector(dir, -s * 0.2).toArray(), q, s * 2.6, s * 1.15, 0.055 * unit, 0.22), { color: pal.leaf, k: 0.035, bone, colorK: 0.03 });
      S.paint(shape.capsule(base.toArray(), base.clone().addScaledVector(dir, s * 2.1).toArray(), 0.012 * unit), { color: pal.dark, soft: 0.006, amount: 0.6 });
      bonePos[bone].copy(base);
    } else if (plan.blob && type === 'fin') {
      // Branquias de ajolote en las crías de agua.
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
    } else {
      // Aletas de agua: hacia atrás y hacia fuera, con radios.
      const s = earSize * 0.8;
      const base = onHost(side * 1.3, 0.3 - droop * 0.2);
      const dir = V(side * 0.7, 0.45 - droop * 0.6, -0.75).normalize();
      const q = basis(dir, V(side * 0.4, 1, 0.2));
      S.add(shape.leaf(base.clone().addScaledVector(dir, -s * 0.3).toArray(), q, s * 2.4, s * 1.3, 0.04 * unit, 0.15), { color: pal.accent, k: 0.035, bone, colorK: 0.03 });
      for (const t of [-0.25, 0, 0.25]) {
        const a = base.clone().addScaledVector(dir, s * 0.3);
        S.paint(shape.capsule(a.toArray(), a.clone().addScaledVector(dir, s * 1.5).add(V(0, t * s, 0)).toArray(), 0.01 * unit), { color: pal.dark, soft: 0.006, amount: 0.5 });
      }
      bonePos[bone].copy(base);
    }
  }

  // ── Remate de la cabeza ──
  const crown = onHost(0, PI / 2 - 0.12);
  const ts = plan.top.size * unit;
  if (plan.top.type === 'flame') {
    // Mechón de llama: tres lenguas curvas hacia atrás.
    // Tupé de llama: tres lenguas gruesas que salen de la frente y se
    // curvan hacia delante y arriba.
    for (const [x, len, lean, back] of [[0, 1, 0, 0], [-0.1, 0.62, -0.7, 0.12], [0.1, 0.66, 0.7, 0.12]]) {
      const a = onHost(x * 2.2, 1.0 - back);
      const pts = curve([
        a.clone().add(V(0, -0.04 * ts, -0.06 * ts)),
        a.clone().add(V(lean * 0.05 * ts, 0.16 * len * ts, 0.0)),
        a.clone().add(V(lean * 0.1 * ts, 0.3 * len * ts, 0.06 * ts)),
        a.clone().add(V(lean * 0.12 * ts, 0.36 * len * ts, 0.16 * ts)),
      ], 8);
      tube(pts, 0.11 * ts, 0.01 * ts, pal.body, HEAD, 0.025, 0.08);
      S.paint(shape.sphere(pts[pts.length - 1].toArray(), 0.13 * ts * len), { color: pal.flame, soft: 0.05 });
    }
  } else if (plan.top.type === 'leaf' && !plan.blob) {
    // Cresta: una hoja grande que nace en la frente y se curva hacia atrás.
    for (const side of [-1, 1]) {
      const a = onHost(side * 0.18, 0.95);
      const dir = V(side * 0.45, 0.75, -0.5).normalize();
      const q = basis(dir, V(side * 0.6, 0.4, 0.6));
      S.add(shape.leaf(a.clone().addScaledVector(dir, -0.04 * ts).toArray(), q, 0.5 * ts, 0.26 * ts, 0.045 * ts, -0.25), { color: pal.leaf, k: 0.04, bone: HEAD, colorK: 0.03 });
      S.paint(shape.capsule(a.toArray(), a.clone().addScaledVector(dir, 0.42 * ts).toArray(), 0.011 * ts), { color: pal.dark, soft: 0.006, amount: 0.5 });
    }
  } else if (plan.top.type === 'fin' && !plan.blob) {
    // Aleta dorsal de la frente a la nuca.
    const a = onHost(0, 0.95);
    const q = basis(V(0, 0.7, -0.7), V(1, 0, 0));
    S.add(shape.leaf(a.clone().add(V(0, -0.06 * ts, 0.04 * ts)).toArray(), q, 0.55 * ts, 0.28 * ts, 0.04 * ts, 0.2), { color: pal.accent, k: 0.04, bone: HEAD, colorK: 0.03 });
  } else if (plan.top.type === 'fin') {
    S.add(shape.cone(crown.clone().add(V(0, -0.02 * ts, 0)).toArray(), crown.clone().add(V(0, 0.24 * ts, -0.04 * ts)).toArray(), 0.1 * ts, 0.02 * ts), { color: pal.accent, k: 0.06, bone: HEAD, colorK: 0.025 });
  }
  if (plan.spikes > 0) {
    // Pelo o púas de punta: en la coronilla y por la espalda.
    for (const [x, tilt, len] of [[-0.14, 0.45, 0.2], [0, 0, 0.28], [0.14, -0.45, 0.2]]) {
      const base = onHost(x * 2, PI / 2 - 0.45);
      const dir = V(-sin(tilt) * 0.6, 0.7, -0.7).normalize();
      S.add(shape.cone(base.toArray(), base.clone().addScaledVector(dir, len * unit * plan.spikes).toArray(), 0.06 * unit, 0.008), { color: fam === 'hoja' ? pal.ivory : pal.dark, k: 0.03, bone: HEAD, colorK: 0.02 });
    }
    if (!plan.blob) {
      for (let i = 0; i < 4; i++) {
        const y = legH + h * (0.85 - i * 0.2);
        const base = V(0, y, -d * 0.92);
        const dir = V(0, 0.35, -1).normalize();
        S.add(shape.cone(base.toArray(), base.clone().addScaledVector(dir, (0.14 - i * 0.02) * plan.spikes).toArray(), 0.05, 0.006), { color: fam === 'hoja' ? pal.ivory : pal.dark, k: 0.025, bone: BODY, colorK: 0.02 });
      }
    }
  }
  if (fam === 'hoja' && !plan.blob) {
    // Placas de hoja por el lomo.
    for (let i = 0; i < 3; i++) {
      const y = legH + h * (0.8 - i * 0.25);
      const base = V(0, y, -d * 0.85);
      const dir = V(0, 0.6, -1).normalize();
      const q = quatFromTo(Y, dir).multiply(new THREE.Quaternion().setFromAxisAngle(Y, PI / 2));
      S.add(shape.leaf(base.clone().addScaledVector(dir, -0.04).toArray(), q, 0.2 - i * 0.03, 0.12, 0.025, 0.2), { color: pal.leaf, k: 0.03, bone: BODY, colorK: 0.02 });
    }
  }
  if (fam === 'pelusa' && !plan.blob) {
    // Mechones de las mejillas.
    for (const side of [-1, 1]) {
      for (let i = 0; i < 2; i++) {
        const a = onHost(side * (1.3 + i * 0.2), -0.25 - i * 0.18);
        const dir = V(side * 0.9, -0.15 + i * 0.1, -0.4).normalize();
        S.add(shape.cone(a.toArray(), a.clone().addScaledVector(dir, (0.2 - i * 0.05) * unit).toArray(), 0.1 * unit, 0.02 * unit), { color: pal.body, k: 0.05, bone: HEAD, colorK: 0.02 });
      }
    }
  }

  // ── Cola ──
  const tsz = plan.tail.size;
  const tailBase = plan.blob ? V(0, h * 0.24, -d * 0.42) : V(0, legH + h * 0.1, -d * 0.82);
  bonePos[TAIL].copy(tailBase);
  const tailBone = plan.blob ? TAIL : TAIL;
  if (plan.tail.type === 'flame') {
    // Cola tupida que sube y acaba en llama.
    const k = plan.blob ? 0.7 : 1;
    const pts = curve([tailBase, tailBase.clone().add(V(0.16 * k, 0.02 * k, -0.12 * k)), tailBase.clone().add(V(0.32 * k * tsz, 0.18 * k * tsz, -0.08 * k)), tailBase.clone().add(V(0.36 * k * tsz, 0.42 * k * tsz, 0.02 * k))], 10);
    tube(pts, 0.06 * k, 0.12 * k * tsz, pal.body, tailBone, 0.03, 0.05);
    const tip = pts[pts.length - 1];
    S.add(shape.cone(tip.toArray(), tip.clone().add(V(-0.02 * k, 0.14 * k * tsz, 0.04 * k)).toArray(), 0.1 * k * tsz, 0.01), { color: pal.flame, k: 0.04, bone: tailBone, colorK: 0.03 });
    S.paint(shape.sphere(tip.toArray(), 0.12 * k * tsz), { color: pal.flame, soft: 0.03 });
  } else if (plan.tail.type === 'leaf') {
    const k = plan.blob ? 0.6 : 1;
    const pts = curve([tailBase, tailBase.clone().add(V(0.14 * k, -0.06 * k, -0.12 * k)), tailBase.clone().add(V(0.3 * k * tsz, -0.06 * k, -0.08 * k * tsz)), tailBase.clone().add(V(0.42 * k * tsz, 0.04 * k, 0.04 * k * tsz))], 8);
    tube(pts, 0.085 * k, 0.03 * k, pal.body, tailBone);
    const tip = pts[pts.length - 1];
    S.add(shape.leaf(tip.toArray(), quatFromTo(Y, V(0.5, 0.8, 0.1).normalize()), 0.3 * k * tsz, 0.18 * k * tsz, 0.035 * k, 0.2), { color: pal.leaf, k: 0.03, bone: tailBone, colorK: 0.02 });
  } else if (plan.tail.type === 'fish') {
    const k = plan.blob ? 0.7 : 1;
    const pts = curve([tailBase, tailBase.clone().add(V(0.12 * k, -0.04, -0.12 * k)), tailBase.clone().add(V(0.28 * k * tsz, 0.0, -0.1 * k * tsz))], 6);
    tube(pts, 0.08 * k, 0.035 * k, pal.body, tailBone);
    const tip = pts[pts.length - 1];
    for (const side of [-1, 1]) {
      const dir = V(0.4, side * 0.75, -0.5).normalize();
      S.add(shape.leaf(tip.toArray(), basis(dir, V(1, 0, 0.4)), 0.24 * k * tsz, 0.15 * k * tsz, 0.03 * k, 0.15), { color: pal.accent, k: 0.03, bone: tailBone, colorK: 0.02 });
    }
  }

  // ── Piezas de adulta fundidas con el cuerpo ──
  if (plan.extra === 'mane') {
    // Collar: de llamas, de pétalos o de aletas.
    // Gorguera por los hombros y la espalda; por delante queda libre la cara.
    const ny = bodyTop - 0.04;
    for (let i = 0; i < 11; i++) {
      const a = PI * 0.42 + (i / 10) * PI * 1.16;
      const r = w * 0.86;
      const base = V(sin(a) * r, ny, cos(a) * r * 0.95);
      const out = V(sin(a), fam === 'pelusa' ? 0.25 : -0.25, cos(a) - 0.2).normalize();
      if (fam === 'pelusa') {
        const tip = base.clone().addScaledVector(out, 0.2);
        S.add(shape.cone(base.toArray(), tip.toArray(), 0.085, 0.012), { color: pal.body, k: 0.04, bone: BODY, colorK: 0.02 });
        S.paint(shape.sphere(tip.toArray(), 0.07), { color: pal.flame, soft: 0.03 });
      } else {
        S.add(shape.leaf(base.clone().addScaledVector(out, -0.03).toArray(), quatFromTo(Y, out), 0.26, 0.16, 0.03, -0.12), { color: i % 2 ? pal.accent : fam === 'hoja' ? pal.leaf : pal.dark, k: 0.025, bone: BODY, colorK: 0.02 });
      }
    }
  }

  // ── Dibujo del ADN ──
  if (plan.belly) {
    const c = plan.blob ? [0, h * 0.3, d * 0.42] : [0, legH + h * 0.45, d * 0.62];
    const r = plan.blob ? [w * 0.3, h * 0.22, d * 0.2] : [w * 0.62, h * 0.4, d * 0.5];
    S.paint(shape.ellipsoid(c, r), { color: pal.light, soft: 0.01 });
    if (snoutTip) S.paint(shape.ellipsoid(onHost(0, facePitch - 0.42).add(V(0, -0.02 * unit, 0.08 * unit)).toArray(), [host.rx * 0.4, host.ry * 0.26, host.rz * 0.5]), { color: pal.light, soft: 0.012 });
  }
  if (plan.pattern === 'motas') {
    const count = 4 + Math.round(plan.patternAmt * 6);
    for (let i = 0; i < count; i++) {
      const size = rng.range(0.045, 0.075) * unit;
      const yaw = rng.range(0.9, 2.4) * (rng.chance(0.5) ? 1 : -1);
      S.paint(shape.sphere(onHost(yaw, rng.range(-0.1, 1.05)).toArray(), size), { color: pal.dark, soft: 0.006 });
    }
  } else if (plan.pattern === 'rayas') {
    // Tres rayas en la frente que bajan por la nuca.
    const width = 0.03 * unit * (0.7 + plan.patternAmt * 0.5);
    for (const yaw of [-0.3, 0, 0.3]) {
      let prev = null;
      for (let i = 0; i <= 6; i++) {
        const pitch = (plan.blob ? 0.82 : 0.66) + i * 0.22;
        const p = onHost(pitch > PI / 2 ? yaw + PI : yaw, pitch > PI / 2 ? PI - pitch : pitch);
        if (prev) S.paint(shape.capsule(prev.toArray(), p.toArray(), width * (1 - i * 0.06)), { color: pal.dark, soft: 0.006 });
        prev = p;
      }
    }
    if (!plan.blob) {
      for (let i = 0; i < 3; i++) {
        const y = legH + h * (0.75 - i * 0.2);
        S.paint(shape.torus([0, y, -d * 0.2], w * 0.75, 0.025, [0.2, 0, 0]), { color: pal.dark, soft: 0.006 });
      }
    }
  }

  // ── Malla y huesos ──
  const finish = { plush: 'plush', matte: 'matte', gloss: 'gloss' }[plan.finish] ?? 'matte';
  const bodyGeometry = once(geo, 'body', () => S.build({ cell, bones: BONES, aoReach: 0.16, ao: detail === 'tiny' ? 0 : 1 }));
  const { mesh, bones } = skinned(bodyGeometry, clay({ finish }), bonePos);

  const root = new THREE.Group();
  const bounce = new THREE.Group();
  const squash = new THREE.Group();
  root.add(bounce);
  bounce.add(squash);
  squash.add(mesh);
  root.scale.setScalar(plan.scale);
  const headPivot = bones[HEAD];
  const headBase = bonePos[HEAD];
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

  // Ojos grandes con iris; los de las crías, aún más.
  const gap = plan.eyes.gap * (plan.blob ? 1.05 : 1.1);
  const eyeR = (plan.blob ? 0.09 : 0.1) * unit * plan.eyes.size;
  // Las de pie miran con decisión: el párpado tapa un poco el ojo.
  const restLid = plan.blob ? plan.eyes.lids : Math.max(plan.eyes.lids, 0.14);
  const content = plan.eyes.lids >= 0.35 && plan.top.type === 'cherry';
  const eyes = [];
  for (const side of [-1, 1]) {
    const { point, normal } = S.project(onHost(side * gap, facePitch));
    const skin = S.colorNear(point.clone().add(V(0, eyeR * 1.3, 0)));
    const eye = makeEye({ point, normal, r: eyeR, tall: plan.blob ? 1.12 : 1.22, iris: pal.iris, kind: plan.blob ? 'round' : 'sclera', skin, tilt: -side * (plan.blob ? 0.04 : 0.12), sparkle: plan.eyes.style === 2 });
    toHead(eye.group);
    if (plan.brows) eye.lid.rotation.z = side * 0.36;
    const lidMat = own(decalMaterial(lidTexture('sleep')));
    const closed = decal(`lid${side}`, point, normal, eyeR * 3.2, eyeR * 2.4, lidMat);
    closed.visible = content;
    eye.open.visible = !content;
    eyes.push({ group: eye.group, open: eye.open, closed, side, r: eyeR, tall: eye.tall, content, restLid: plan.brows ? Math.max(restLid, 0.22) : restLid, lidValue: 0, lid: eye.lid, ball: eye.bead });
  }

  // Boca, en el hocico si lo tiene.
  const mouthKind = 'smile';
  const mouthAt = snoutTip
    ? S.project(snoutTip.clone().add(V(0, -host.ry * 0.2, 0)))
    : S.project(onHost(0, facePitch - (plan.blob ? 0.26 : 0.3)));
  const mouthMat = own(decalMaterial(mouthTexture(mouthKind, plan.fang)));
  const mouthW = (snoutTip ? 0.2 : 0.24) * unit;
  const mouth = decal('mouth', mouthAt.point, mouthAt.normal, mouthW, mouthW * 0.625, mouthMat);
  const mouths = Object.fromEntries(['smile', 'sad', 'open', 'flat'].map((name) => [name, mouthTexture(name, plan.fang)]));

  // Nariz del zorro; dos agujeritos en el resto.
  if (snoutTip && fam === 'pelusa') {
    const { point, normal } = S.project(snoutTip);
    const nose = new THREE.Mesh(new THREE.SphereGeometry(1, 20, 14), own(new THREE.MeshPhysicalMaterial({ color: pal.nose, roughness: 0.25, clearcoat: 1, clearcoatRoughness: 0.1 })));
    own(nose.geometry);
    nose.scale.set(0.034 * unit, 0.024 * unit, 0.022 * unit);
    nose.position.copy(point).addScaledVector(normal, 0.006 * unit);
    nose.quaternion.copy(faceFrame(normal));
    toHead(nose);
  }

  // Coloretes: discretos.
  const cheeks = [];
  for (const side of [-1, 1]) {
    const { point, normal } = S.project(onHost(side * (gap + 0.32), facePitch - 0.2));
    const material = own(decalMaterial(blushTexture(), { color: '#ff8fa9', opacity: 0.3 }));
    cheeks.push(decal(`cheek${side}`, point, normal, 0.18 * unit * plan.cheeks, 0.11 * unit * plan.cheeks, material));
  }

  if (plan.brows) {
    for (const side of [-1, 1]) {
      const { point, normal } = S.project(onHost(side * gap * 0.92, facePitch + 0.33));
      decal(`brow${side}`, point, normal, eyeR * 2.8, eyeR * 1.4, own(decalMaterial(browTexture(side))));
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
      P.add(shape.capsule(base.toArray(), tip.toArray(), 0.024 * ts), { color: pal.leaf, k: 0 });
      for (const side of [-1, 1]) {
        const dir = V(side * 0.95, 0.55, 0.12).normalize();
        P.add(shape.leaf(tip.toArray(), quatFromTo(Y, dir).multiply(new THREE.Quaternion().setFromAxisAngle(Y, side * 0.25)), 0.3 * ts, 0.17 * ts, 0.028 * ts, 0.18), { color: pal.leaf, k: 0.02 });
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
      P.add(shape.sphere(c.toArray(), 0.072 * ts), { color: '#d63a4f', k: 0 });
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

  // Alas: de plumas encendidas, de hoja o de aleta.
  const wings = [];
  if (plan.extra === 'wings') {
    for (const side of [-1, 1]) {
      const pivot = new THREE.Group();
      pivot.position.set(side * w * 0.62, plan.blob ? h * 0.72 : legH + h * 0.86, plan.blob ? -d * 0.3 : -d * 0.62);
      const wing = partMesh(`wing-${fam}`, () => {
        const P = new Sculpt();
        if (fam === 'pelusa') {
          for (let i = 0; i < 4; i++) {
            const a = 0.15 - i * 0.32;
            const len = 0.5 - i * 0.07;
            const dir = V(sin(-a + 0.6) * 0.3, cos(a), 0).normalize();
            P.add(shape.cone([0, 0, 0], dir.clone().multiplyScalar(len).toArray(), 0.06, 0.012), { color: i === 0 ? pal.flame : i % 2 ? pal.body : pal.light, k: 0.025 });
          }
        } else if (fam === 'hoja') {
          P.add(shape.leaf([0, 0, 0], [0, 0, -0.2], 0.68, 0.36, 0.035, 0.12), { color: pal.leaf, k: 0 });
          P.paint(shape.capsule([0, 0.02, 0], [0.03, 0.44, 0.03], 0.008), { color: pal.dark, soft: 0.006 });
        } else {
          for (let i = 0; i < 3; i++) {
            const a = 0.25 - i * 0.42;
            P.add(shape.leaf([0, 0, 0], [0, 0, a], 0.58 - i * 0.08, 0.26, 0.03, 0.1), { color: pal.accent, k: 0.04 });
          }
        }
        return P.build({ cell: 0.009, aoReach: 0.06 });
      }, partMat(fam === 'pelusa' ? 'plush' : finish));
      wing.scale.x = side;
      pivot.add(wing);
      pivot.rotation.set(0, side * -0.55, side * -1.05);
      bones[BODY].add(pivot);
      wings.push({ pivot, side, rest: side * -1.05 });
    }
  }

  // Corona: cresta de llama, flores o perlas.
  if (plan.extra === 'crown') {
    const ring = partMesh(`crown-${fam}`, () => {
      const P = new Sculpt();
      const count = 7;
      for (let i = 0; i < count; i++) {
        const angle = (i / count) * PI * 2;
        const { point, normal } = S.project(onHost(angle, 1.0));
        const c = point.clone().addScaledVector(normal, 0.03 * unit);
        if (fam === 'hoja') {
          const q = faceFrame(normal, Z);
          for (let p = 0; p < 5; p++) {
            const a = (p / 5) * PI * 2;
            const offset = V(cos(a) * 0.045 * unit, sin(a) * 0.045 * unit, 0).applyQuaternion(q);
            P.add(shape.ellipsoid(c.clone().add(offset).toArray(), [0.04 * unit, 0.028 * unit, 0.018 * unit], q.clone().multiply(new THREE.Quaternion().setFromAxisAngle(Z, a))), { color: pal.accent, k: 0.01 });
          }
          P.add(shape.sphere(c.clone().addScaledVector(normal, 0.01 * unit).toArray(), 0.024 * unit), { color: '#ffd166', k: 0.006 });
        } else if (fam === 'pelusa') {
          P.add(shape.cone(c.toArray(), c.clone().addScaledVector(normal, 0.05 * unit).add(V(0, 0.26 * unit, 0)).toArray(), 0.06 * unit, 0.006), { color: i % 2 ? pal.flame : pal.accent, k: 0.01 });
        } else {
          P.add(shape.sphere(c.toArray(), 0.07 * unit), { color: pal.ivory, k: 0 });
        }
      }
      return P.build({ cell: 0.008 * unit, aoReach: 0.05 });
    }, partMat(fam === 'gota' ? 'glaze' : fam === 'pelusa' ? 'plush' : 'matte'));
    toHead(ring);
  }

  // Cuernos curvos.
  if (plan.extra === 'horns') {
    toHead(partMesh('horns', () => {
      const P = new Sculpt();
      for (const side of [-1, 1]) {
        const base = onHost(side * 0.55, 1.0);
        const pts = curve([base.clone().add(V(0, -0.04 * unit, 0)), base.clone().add(V(side * 0.06 * unit, 0.14 * unit, -0.04 * unit)), base.clone().add(V(side * 0.16 * unit, 0.22 * unit, -0.12 * unit)), base.clone().add(V(side * 0.22 * unit, 0.2 * unit, -0.2 * unit))], 8);
        for (let i = 0; i < pts.length - 1; i++) {
          const a = 0.07 * unit * (1 - i / pts.length);
          const b = 0.07 * unit * (1 - (i + 1) / pts.length) + 0.004;
          P.add(shape.cone(pts[i].toArray(), pts[i + 1].toArray(), a, b), { color: i < 3 ? pal.horn : pal.ivory, k: 0.01 });
        }
      }
      return P.build({ cell: 0.007 * unit, aoReach: 0.05 });
    }, partMat('gloss')));
  }

  let halo = null;
  if (plan.extra === 'halo') {
    halo = new THREE.Mesh(own(new THREE.TorusGeometry(0.22 * unit, 0.026 * unit, 16, 48)), own(new THREE.MeshStandardMaterial({ color: '#ffe08a', emissive: '#ffcf5c', emissiveIntensity: 0.9, roughness: 0.3, metalness: 0.2 })));
    halo.rotation.set(PI / 2 - 0.22, 0, 0.1);
    halo.position.set(0, host.center.y + host.ry + 0.36 * unit, -0.03 * unit);
    toHead(halo);
    halo.userData.y = halo.position.y;
  }

  // ── Anclajes para la ropa ──
  const hostLocal = { center: host.center.clone().sub(headBase), rx: host.rx, ry: host.ry, rz: host.rz };
  const anchors = {
    hat: { y: hostLocal.center.y + host.ry, r: host.rx },
    face: { y: hostLocal.center.y + host.ry * sin(facePitch), z: host.rz, gap, r: host.rx, host: hostLocal, pitch: facePitch },
    neck: plan.blob ? neckOf(S, plan, host) : bipedNeck(S, bodyTop),
  };

  const height = (host.center.y + host.ry) * plan.scale;
  // La habitación encuadra con el ancho del cuerpo: en las de pie, el doble del radio.
  const view = plan.blob ? plan : { ...plan, body: { ...plan.body, w: w * 2 } };

  return {
    object: root,
    plan: view,
    height,
    anchors,
    rig: { root, bounce, squash, headPivot, eyes, mouths, mouth, cheeks, ears: earRig(bones), arms: plan.arms ? armRig(bones) : [], feet: [], tail: bones[TAIL], wings, top, halo, mesh, blush: { rest: 0.22, happy: 0.42 } },
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

// Cuello de las de pie: justo donde el cuerpo se junta con la cabeza.
function bipedNeck(S, y) {
  const at = y - 0.03;
  let r = 0.2;
  for (let t = 0.05; t < 0.6; t += 0.01) {
    if (S.sdf(t, at, 0) > 0) { r = t; break; }
  }
  let rz = 0.2;
  for (let t = 0.05; t < 0.6; t += 0.01) {
    if (S.sdf(0, at, t) > 0) { rz = t; break; }
  }
  return { y: at, rx: r + 0.012, rz: rz + 0.012 };
}
