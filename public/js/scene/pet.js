// Perros y gatos esculpidos. La raza (shared/breeds.js) da las
// proporciones, el hocico, las orejas, el pelo, la cola y la capa; la
// etapa los lleva de cachorro a adulto y la crianza les da su aire
// (rellenito, atlético, noble o fiero). Todo se funde en una sola figura
// de arcilla sentada, con la cara y los ojos encima, como el resto de
// criaturas (ver creature-kit.js).

import * as THREE from 'three';
import { createRng, clamp } from '/shared/rng.js';
import { getForm } from '/shared/forms.js';
import { getBreed, breedsOf } from '/shared/breeds.js';
import { Sculpt, shape, surfacePatch } from './sdf.js';
import { clay } from './materials.js';
import {
  V, Y, Z, BODY, HEAD, EAR_L, EAR_R, ARM_L, ARM_R, TAIL, BONES,
  sculptCache, once, faceFrame, decalMaterial, blushTexture, browTexture, lidTexture,
  petMouthTexture, makeEye, skinned, radiusAt, LID_OPEN,
} from './creature-kit.js';

const { sin, cos, PI, max, min, abs } = Math;
const mix = (a, b, t) => a + (b - a) * t;
const col = (c) => new THREE.Color(c);

const STAGE_KID = { cria: 1, joven: 0.45, adulto: 0 };
const STAGE_SCALE = { cria: 0.7, joven: 0.85, adulto: 1 };

// Colores de las formas ocultas que cambian la capa.
const NIGHT = { base: '#2f3a5e', light: '#c9d3ee', mark: '#ffe9a6' };
const GOLD = '#f2c14e';

// ── Plano ─────────────────────────────────────────────────────
// Medidas de la figura a partir de la raza, la etapa, la crianza y el ADN.

function petPlan(form, genes, breed) {
  const cat = breed.species === 'gato';
  const kid = STAGE_KID[form.stage] ?? 0;
  const g = genes.build;
  const [b1, b2] = form.path;
  const m = {
    chunk: breed.body.chunk + (g.round - 0.5) * 0.16,
    leg: breed.body.leg,
    chest: breed.body.chest,
    long: breed.body.long,
    head: breed.head.size + (g.head - 0.5) * 0.12,
    dome: breed.head.dome,
    width: breed.head.width,
    snout: breed.snout.len,
    snoutW: breed.snout.width,
    drop: breed.snout.drop,
    stop: breed.snout.stop,
    ears: { ...breed.ears, size: breed.ears.size + (g.ears - 0.5) * 0.14 },
    tail: { ...breed.tail, size: breed.tail.size + (g.tail - 0.5) * 0.2 },
    coatType: breed.coat,
    extras: new Set(breed.extras),
    eyes: 1 + (g.eyes - 0.5) * 0.14,
    eyeGap: (g.eyeGap - 0.5) * 0.08,
    fluff: 0,
    lids: 0,
    brows: false,
    fang: false,
    scruff: 0,
    scar: false,
    pose: null,
    scale: 1,
    look: null,
  };

  // La crianza: la rama de joven pesa menos en la adulta que la suya.
  const branch = (id, w) => {
    if (id === 'gloton') {
      m.chunk += 0.24 * w;
      m.chest += 0.1 * w;
      m.scale *= 1 + 0.05 * w;
    } else if (id === 'jugueton') {
      m.leg += 0.16 * w;
      m.chunk -= 0.1 * w;
      m.ears.size += 0.1 * w;
      m.tail.size += 0.2 * w;
    } else if (id === 'tranquilo') {
      m.fluff += 0.3 * w;
      m.lids = max(m.lids, 0.36 * w);
    } else if (id === 'arisco') {
      m.brows = true;
      m.scruff += w;
      m.fang = m.fang || w >= 0.9;
    }
  };
  if (b1) branch(b1, form.stage === 'joven' ? 1 : 0.55);
  if (b2) branch(b2, 1);
  if (b2 === 'jugueton') m.pose = 'paw';
  if (b2 === 'tranquilo') m.fluff += 0.25;
  if (b2 === 'arisco') m.scar = true;

  if (form.secret === 'radiante') {
    m.look = 'radiante';
    m.eyes *= 1.06;
    m.fluff += 0.2;
  } else if (form.secret === 'sibarita') {
    m.look = 'sibarita';
    m.chunk = 1.15;
    m.chest += 0.2;
    m.scale *= 1.06;
  } else if (form.secret === 'lunar') {
    m.look = 'lunar';
    m.lids = max(m.lids, 0.42);
    m.fluff += 0.2;
  } else if (form.secret === 'mestiza') {
    // Mezcla: orejas y cola de la raza de la que viene.
    const other = getBreed(genes.origin);
    if (other && other.species === breed.species) {
      m.ears = { ...other.ears, size: (other.ears.size + m.ears.size) / 2 };
      m.tail = { ...other.tail };
      m.snout = (m.snout + other.snout.len) / 2;
      m.look = 'mestiza';
    }
  }

  m.chunk = clamp(m.chunk, 0, 1.2);
  m.leg = clamp(m.leg, 0, 1);

  // Medidas. Las crías tienen cabeza grande, patas cortas y hocico corto.
  const bw = (cat ? 0.135 : 0.15) + m.chunk * (cat ? 0.075 : 0.085);
  const legH = (0.19 + m.leg * 0.2) * (1 - kid * 0.32);
  const torsoLen = 0.15 + m.long * 0.06 + m.chunk * 0.02;
  const sh = legH + 0.07;
  const hr = (0.205 + m.head * 0.085) * (1 + kid * 0.26);
  const headW = 1 + (m.width - 0.5) * 0.26;
  const snoutLen = hr * (0.06 + m.snout * 1.0) * (1 - kid * 0.42);
  const mw = hr * (0.26 + m.snoutW * 0.22) * (cat ? 0.92 : 1);
  const mh = hr * (0.2 + m.snoutW * 0.08);
  return {
    species: breed.species,
    family: form.family,
    breed,
    cat,
    kid,
    stage: form.stage,
    m,
    bw, legH, torsoLen, sh, hr, headW, snoutLen, mw, mh,
    scale: STAGE_SCALE[form.stage] * (0.88 + breed.size * 0.22) * m.scale,
  };
}

// La capa: color de fondo y zonas pintadas, ya con lo que cambian las
// formas ocultas.
function coatOf(P, genes) {
  const coats = P.breed.coats;
  const coat = coats[clamp(genes.coat ?? 0, 0, coats.length - 1)];
  let base = coat.base;
  let paint = coat.paint.map(([region, color]) => [region, color]);
  if (P.m.look === 'lunar') {
    // Noche: el fondo pasa a azul tinta y lo claro, a plata.
    const light = (color) => typeof color === 'string' && col(color).getHSL({}).l > 0.6;
    paint = paint.map(([region, color]) => [region, light(color) ? NIGHT.light : Array.isArray(color) ? [NIGHT.light] : '#1d2440']);
    base = NIGHT.base;
  } else if (P.m.look === 'radiante') {
    base = `#${col(base).lerp(col(GOLD), 0.28).getHexString()}`;
  } else if (P.m.look === 'mestiza') {
    paint.push(['patches', [P.cat ? '#3a2e2a' : '#5a3a26']]);
  }
  return { base, paint, name: coat.name };
}

// ── Escultura ─────────────────────────────────────────────────

export function buildPet(formId, genes, { detail = 'high' } = {}) {
  const form = getForm(formId);
  const breed = getBreed(genes.breed) ?? breedsOf(form.family)[0];
  const P = petPlan(form, genes, breed);
  const { cat, m, bw, legH, torsoLen, sh, hr, headW, snoutLen, mw, mh } = P;
  const coat = coatOf(P, genes);
  const base = col(coat.base);
  const rng = createRng((genes.seed ^ 0x5bd1e995) >>> 0);
  const cacheKey = `pet|${formId}|${genes.breed}|${genes.coat}|${genes.eyes}|${genes.seed}|${JSON.stringify(genes.build)}|${genes.origin ?? ''}`;
  const geo = detail === 'high' ? sculptCache(cacheKey) : {};
  const cell = { high: 0.0125, low: 0.022, tiny: 0.034 }[detail] ?? 0.022;

  // Colores de zona que también dan forma (orejas, cola).
  const region = (name) => {
    const hit = coat.paint.find(([r]) => r === name);
    return hit && typeof hit[1] === 'string' ? col(hit[1]) : null;
  };
  const earColor = region('ears') ?? region('points') ?? region('cap') ?? base;
  const tailColor = region('tail') ?? region('points') ?? region('cap') ?? base;
  const innerEar = region('innerEar') ?? earColor.clone().lerp(col(cat ? '#e3a19c' : '#b98a7c'), cat ? 0.45 : 0.3);
  const fur = { plush: ['fluffy', 'long', 'curly', 'feathered'].includes(m.coatType) || m.fluff > 0.3 };

  const S = new Sculpt({ ground: 0, groundK: 0.025 });
  const bonePos = Array.from({ length: BONES }, () => V());

  // ── Cuerpo sentado ──
  const torsoY = sh * 0.6;
  S.add(shape.ellipsoid([0, torsoY, -0.03], [bw, sh * 0.56 + 0.05, torsoLen], [0.34, 0, 0]), { color: base, k: 0, bone: BODY });
  const chestZ = 0.06 + m.chest * 0.025;
  S.add(shape.ellipsoid([0, sh * 0.84, chestZ], [bw * 0.84, 0.11 + m.chest * 0.045, 0.1 + m.chest * 0.035]), { color: base, k: 0.08, bone: BODY });
  // Muslos de las patas de atrás y sus pies, asomando delante.
  const thigh = { x: bw * 0.8, y: 0.11 + legH * 0.14, z: -0.06 };
  for (const side of [-1, 1]) {
    S.add(shape.ellipsoid([side * thigh.x, thigh.y, thigh.z], [0.085 + m.chunk * 0.045, 0.11 + legH * 0.14, 0.14 + m.long * 0.03], [0.3, 0, -side * 0.12]), { color: base, k: 0.07, bone: BODY });
    pawAt(V(side * (thigh.x + 0.01), 0, 0.07 + m.long * 0.02 - (cat ? 0.02 : 0)), 1.05, BODY);
  }
  // Patas de delante: van con su hueso para poder dar la pata.
  const legR = (cat ? 0.036 : 0.04) + m.chunk * 0.024;
  const shoulders = [];
  for (const side of [-1, 1]) {
    const bone = side < 0 ? ARM_L : ARM_R;
    const top = V(side * bw * 0.5, sh * 0.92, 0.08);
    const foot = V(side * bw * 0.44, 0.05, 0.16);
    shoulders.push(top);
    bonePos[bone].copy(top);
    S.add(shape.cone(top.toArray(), foot.toArray(), legR, legR * 0.86), { color: base, k: 0.05, bone: BODY, split: { bone, from: sh * 0.86, to: sh * 0.5 } });
    pawAt(foot.clone().setY(0), 1, bone);
  }
  function pawAt(p, s, bone) {
    const r = (cat ? 0.048 : 0.054) * s * (0.9 + m.chunk * 0.2);
    S.add(shape.ellipsoid([p.x, r * 0.72, p.z + r * 0.35], [r, r * 0.74, r * 1.32]), { color: base, k: 0.035, bone });
    // Dedos: tres bultos delante.
    for (const t of [-1, 0, 1]) {
      S.add(shape.sphere([p.x + t * r * 0.52, r * 0.5, p.z + r * 1.32 - abs(t) * r * 0.16], r * 0.36), { color: base, k: 0.018, bone });
    }
  }

  // ── Cuello y cabeza ──
  const headC = V(0, sh + 0.05 + hr * 0.82, 0.05 + hr * 0.04);
  bonePos[HEAD].set(0, sh + 0.02, 0.05);
  S.add(shape.ellipsoid([0, sh + 0.05, 0.05], [bw * 0.72, 0.1, 0.105]), { color: base, k: 0.07, bone: BODY, split: { bone: HEAD, from: sh - 0.02, to: sh + 0.1 } });
  const head = { center: headC, rx: hr * headW, ry: hr * 0.92, rz: hr * 0.9 };
  S.add(shape.ellipsoid(headC.toArray(), [head.rx, head.ry, head.rz]), { color: base, k: 0.07, bone: HEAD });
  if (m.dome > 0.45) {
    const d = 0.84 + (m.dome - 0.45) * 0.4;
    S.add(shape.ellipsoid([0, headC.y + hr * 0.2, headC.z - hr * 0.06], [hr * 0.86 * d * headW, hr * 0.8 * d, hr * 0.84 * d]), { color: base, k: 0.1, bone: HEAD });
  }
  for (const side of [-1, 1]) {
    const c = 0.55 + m.width * 0.45 + (cat ? 0.15 : 0);
    S.add(shape.ellipsoid([side * hr * 0.44 * headW, headC.y - hr * 0.34, headC.z + hr * 0.34], [hr * 0.36 * c, hr * 0.3 * c, hr * 0.34]), { color: base, k: 0.07, bone: HEAD });
  }

  const onHead = (yaw, pitch) => {
    const cp = cos(pitch);
    return V(head.rx * sin(yaw) * cp, head.ry * sin(pitch), head.rz * cos(yaw) * cp).add(headC);
  };
  const headNormal = (p) => {
    const q = p.clone().sub(headC);
    return V(q.x / head.rx ** 2, q.y / head.ry ** 2, q.z / head.rz ** 2).normalize();
  };

  // Hocico: parte de arriba, almohadillas de los bigotes y barbilla.
  const mBase = V(0, headC.y - hr * 0.3, headC.z + hr * 0.6);
  const halfL = max(snoutLen * 0.6, mh * 0.85);
  const mC = mBase.clone().add(V(0, -snoutLen * 0.06, snoutLen * 0.5));
  const tipZ = mC.z + halfL;
  S.add(shape.ellipsoid(mC.toArray(), [mw, mh, halfL], [0.06, 0, 0]), { color: base, k: 0.06, bone: HEAD });
  if (snoutLen > hr * 0.35 && m.stop < 0.6) {
    // Caña nasal: une la frente con el hocico en las razas de cara larga.
    S.add(shape.cone([0, headC.y + hr * 0.08, headC.z + hr * 0.62], [0, mC.y + mh * 0.5, tipZ - mh * 0.7], mw * 0.5, mw * 0.46), { color: base, k: 0.08, bone: HEAD });
  }
  const padDrop = m.drop * mh * 0.45;
  for (const side of [-1, 1]) {
    S.add(shape.ellipsoid([side * mw * 0.42, mC.y - mh * 0.34 - padDrop, tipZ - mh * 0.62], [mw * 0.56, mh * (0.6 + m.drop * 0.4), mh * 0.78]), { color: base, k: 0.035, bone: HEAD });
  }
  S.add(shape.ellipsoid([0, mC.y - mh * 0.86 - padDrop * 0.6, tipZ - mh * 0.95 - snoutLen * 0.12], [mw * 0.5, mh * 0.42, max(halfL * 0.7, mh * 0.6)]), { color: base, k: 0.04, bone: HEAD });
  // Cejas del hueso de la frente.
  if (m.stop > 0.35) {
    for (const side of [-1, 1]) {
      S.add(shape.ellipsoid([side * hr * 0.34, headC.y + hr * 0.22, headC.z + hr * 0.7], [hr * 0.22, hr * 0.11 * m.stop, hr * 0.14]), { color: base, k: 0.06, bone: HEAD });
    }
  }

  // ── Orejas ──
  const earLen = hr * (0.55 + m.ears.size * 0.7) * (1 + P.kid * 0.08);
  for (const side of [-1, 1]) {
    const bone = side < 0 ? EAR_L : EAR_R;
    bonePos[bone].copy(ear(side, bone, m.ears.type));
  }

  function basis(up, forward) {
    const y = up.clone().normalize();
    const z = forward.clone().addScaledVector(y, -forward.dot(y)).normalize();
    const x = new THREE.Vector3().crossVectors(y, z);
    return new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, z));
  }

  // Devuelve dónde va el hueso de la oreja.
  function ear(side, bone, type) {
    const set = m.ears.set;
    if (type === 'erect' || type === 'cat' || type === 'tufted' || type === 'bat') {
      const yaw = side * (cat ? 0.66 : 0.7 - set * 0.14);
      const pitch = 0.58 + set * 0.26;
      const root = onHead(yaw, pitch);
      const out = headNormal(root);
      const flare = (cat ? 0.3 : 0.24) + m.ears.size * (cat ? 0.16 : 0.3);
      const dir = V(side * flare, 1, -0.1).add(out.clone().multiplyScalar(0.2)).normalize();
      const fwd = V(side * 0.18, -0.05, 1);
      const q = basis(dir, fwd);
      const len = earLen * (type === 'bat' ? 0.9 : 0.82);
      const wide = len * (cat ? 1.02 : type === 'bat' ? 1.0 : 0.96);
      const thick = hr * 0.13;
      const start = root.clone().addScaledVector(dir, -len * 0.42);
      if (type === 'bat') {
        const c = root.clone().addScaledVector(dir, len * 0.32);
        S.add(shape.ellipsoid(c.toArray(), [wide * 0.56, len * 0.6, thick * 0.6], q), { color: earColor, k: 0.04, bone });
        S.paint(shape.ellipsoid(c.clone().add(V(0, len * 0.04, thick * 0.5).applyQuaternion(q)).toArray(), [wide * 0.38, len * 0.46, thick * 0.4], q), { color: innerEar, soft: 0.01 });
      } else {
        S.add(shape.leaf(start.toArray(), q, len * 1.42, wide, thick, -0.04), { color: earColor, k: 0.035, bone });
        const inner = start.clone().addScaledVector(dir, len * 0.18).add(V(0, 0, thick * 0.55).applyQuaternion(q));
        S.paint(shape.leaf(inner.toArray(), q, len * 1.12, wide * 0.6, thick * 0.6, -0.04), { color: innerEar, soft: 0.008 });
        if (type === 'tufted') {
          const tip = start.clone().addScaledVector(dir, len * 1.42);
          S.add(shape.cone(tip.clone().addScaledVector(dir, -len * 0.1).toArray(), tip.clone().addScaledVector(dir, len * 0.28).add(V(side * 0.01, 0, 0)).toArray(), thick * 0.35, 0.003), { color: region('ears') ?? base.clone().lerp(col('#2a2523'), 0.4), k: 0.01, bone });
        }
        if (cat && fur.plush) {
          // Mechones blancos dentro de la oreja.
          for (const t of [-1, 1]) {
            const a = root.clone().add(V(t * wide * 0.12, 0, thick * 0.4).applyQuaternion(q));
            S.add(shape.cone(a.toArray(), a.clone().addScaledVector(dir, len * 0.42).add(V(-side * 0.01, 0, 0.012)).toArray(), thick * 0.3, 0.003), { color: col('#f6efe4'), k: 0.012, bone });
          }
        }
      }
      return root;
    }
    if (type === 'fold') {
      // Botón: base de pie y la punta doblada hacia delante.
      const root = onHead(side * 0.58, 0.82);
      const dir = V(side * 0.35, 1, 0).normalize();
      const q = basis(dir, V(0, 0, 1));
      S.add(shape.ellipsoid(root.clone().addScaledVector(dir, earLen * 0.12).toArray(), [earLen * 0.36, earLen * 0.3, hr * 0.06], q), { color: earColor, k: 0.035, bone });
      const flapDir = V(side * 0.25, -0.75, 0.6).normalize();
      const flap = root.clone().addScaledVector(dir, earLen * 0.34).addScaledVector(flapDir, earLen * 0.22);
      S.add(shape.ellipsoid(flap.toArray(), [earLen * 0.34, earLen * 0.3, hr * 0.055], basis(flapDir, V(0, 1, 0.3))), { color: earColor, k: 0.025, bone });
      return root;
    }
    if (type === 'rose') {
      const root = onHead(side * 0.82, 0.6);
      const dir = V(side * 0.85, 0.1, -0.5).normalize();
      const q = basis(dir, V(side * 0.3, 1, 0.4));
      S.add(shape.ellipsoid(root.clone().addScaledVector(dir, earLen * 0.18).toArray(), [earLen * 0.24, earLen * 0.3, hr * 0.08], q), { color: earColor, k: 0.035, bone });
      return root;
    }
    if (type === 'catFold') {
      const root = onHead(side * 0.5, 0.86);
      const dir = V(side * 0.3, -0.15, 1).normalize();
      const q = basis(dir, V(0, 1, 0));
      S.add(shape.ellipsoid(root.clone().addScaledVector(dir, earLen * 0.18).add(V(0, earLen * 0.04, 0)).toArray(), [earLen * 0.34, earLen * 0.26, earLen * 0.2], q), { color: earColor, k: 0.04, bone });
      return root;
    }
    // Caídas ('drop') y largas ('long'): cuelgan de lo alto de la cabeza.
    const long = type === 'long';
    const root = onHead(side * (long ? 1.12 : 1.02), long ? 0.42 + m.ears.set * 0.2 : 0.52 + m.ears.set * 0.12);
    const len = earLen * (long ? 1.35 : 0.82);
    const out = headNormal(root);
    const hang = V(side * (long ? 0.12 : 0.28), -1, long ? 0.08 : 0.16).normalize();
    const q = basis(hang, out.clone().setY(0).normalize());
    const top = root.clone().addScaledVector(out, hr * 0.02);
    const c = top.clone().addScaledVector(hang, len * 0.46).addScaledVector(out, hr * 0.07);
    const wide = len * (long ? 0.5 : 0.62);
    S.add(shape.ellipsoid(top.toArray(), [wide * 0.42, len * 0.16, hr * 0.13], q), { color: earColor, k: 0.035, bone });
    S.add(shape.ellipsoid(c.toArray(), [wide * 0.5, len * 0.5, hr * 0.095], q), { color: earColor, k: 0.03, bone });
    if (m.coatType === 'feathered' || m.coatType === 'curly' || m.coatType === 'long') {
      // Flecos al final de la oreja.
      for (const t of [-1, 0, 1]) {
        const a = c.clone().addScaledVector(hang, len * 0.3).add(V(t * wide * 0.25, 0, 0).applyQuaternion(q));
        S.add(shape.ellipsoid(a.toArray(), [wide * 0.2, len * 0.22, hr * 0.06], q), { color: earColor, k: 0.03, bone });
      }
    }
    return root;
  }

  // ── Cola ──
  const tailBase = V(0, 0.1 + legH * 0.15, -torsoLen - 0.03);
  bonePos[TAIL].copy(tailBase);
  tail(m.tail.type, m.tail.size);

  function tube(points, r0, r1, color = tailColor, k = 0.035, bone = TAIL) {
    for (let i = 0; i < points.length - 1; i++) {
      const a = mix(r0, r1, i / (points.length - 1));
      const b = mix(r0, r1, (i + 1) / (points.length - 1));
      S.add(shape.cone(points[i].toArray(), points[i + 1].toArray(), a, b), { color, k: i === 0 ? 0.04 : k, bone });
    }
  }
  function curve(ctrl, n = 8) {
    const c = new THREE.CatmullRomCurve3(ctrl);
    return c.getPoints(n);
  }

  function tail(type, size) {
    const s = 0.75 + size * 0.5;
    const tb = tailBase;
    const thick = (cat ? 0.03 : 0.036) * (0.85 + m.chunk * 0.3);
    const fluffy = fur.plush || m.coatType === 'fluffy';
    if (type === 'none') return;
    if (type === 'bob') {
      S.add(shape.sphere(tb.clone().add(V(0, 0.02, -0.03)).toArray(), 0.045 * s * (fluffy ? 1.3 : 1)), { color: tailColor, k: 0.04, bone: TAIL });
      return;
    }
    if (type === 'carrot') {
      const pts = curve([tb, tb.clone().add(V(0.01, 0.12 * s, -0.08)), tb.clone().add(V(0.03, 0.27 * s, -0.08)), tb.clone().add(V(0.04, 0.36 * s, -0.03))]);
      tube(pts, thick * 1.1, thick * 0.6);
      const tip = pts[pts.length - 1];
      if (region('tailTip')) S.paint(shape.sphere(tip.toArray(), 0.05 * s), { color: region('tailTip'), soft: 0.01 });
      return;
    }
    if (type === 'curl') {
      // Enroscada sobre el lomo.
      const up = sh * 0.75;
      const pts = curve([tb, tb.clone().add(V(0.0, up * 0.5, -0.07)), tb.clone().add(V(0.04, up * 0.95, -0.02)), tb.clone().add(V(0.09, up * 1.02, 0.08)), tb.clone().add(V(0.12, up * 0.82, 0.1))], 10);
      tube(pts, thick * (fluffy ? 1.7 : 1.15), thick * (fluffy ? 1.2 : 0.6));
      if (fluffy) for (let i = 2; i < pts.length; i += 2) S.add(shape.sphere(pts[i].clone().add(V(0.02, 0.025, -0.01)).toArray(), thick * 1.3), { color: tailColor, k: 0.03, bone: TAIL });
      if (region('tailUnder')) for (let i = 1; i < pts.length; i++) S.paint(shape.sphere(pts[i].clone().add(V(-0.03, -0.02, 0.02)).toArray(), thick * 1.15), { color: region('tailUnder'), soft: 0.012 });
      return;
    }
    if (type === 'pom') {
      const pts = curve([tb, tb.clone().add(V(0, 0.12 * s, -0.06)), tb.clone().add(V(0.01, 0.24 * s, -0.06))], 4);
      tube(pts, thick * 0.8, thick * 0.6);
      const end = pts[pts.length - 1];
      for (let i = 0; i < 7; i++) {
        const a = i * 2.4;
        S.add(shape.sphere(end.clone().add(V(sin(a) * 0.03, 0.03 + (i % 2) * 0.02, cos(a) * 0.03)).toArray(), 0.045 * s), { color: tailColor, k: 0.02, bone: TAIL });
      }
      return;
    }
    if (type === 'cat' || type === 'whip' || type === 'bushy') {
      // Por el suelo y rodeando las patas, como un gato sentado.
      const r = bw + 0.07;
      const pts = curve([
        tb,
        V(r * 0.55, 0.04, -torsoLen - 0.02),
        V(r * 1.05, 0.04, -0.02),
        V(r * 0.95, 0.04, 0.16 * s),
        V(r * 0.55, 0.05, 0.25 * s),
      ], 12);
      const t0 = type === 'bushy' ? thick * 1.9 : type === 'whip' ? thick * 0.8 : thick;
      const t1 = type === 'bushy' ? thick * 1.5 : type === 'whip' ? thick * 0.4 : thick * 0.75;
      tube(pts, t0, t1);
      if (type === 'bushy') for (let i = 2; i < pts.length; i += 2) S.add(shape.sphere(pts[i].clone().add(V(0, 0.02, 0)).toArray(), t0 * 0.95), { color: tailColor, k: 0.03, bone: TAIL });
      const tip = pts[pts.length - 1];
      if (region('tailTip')) S.paint(shape.sphere(tip.toArray(), 0.05), { color: region('tailTip'), soft: 0.01 });
      return;
    }
    // Perros: nutria ('otter') y pluma ('plume'), apoyadas en el suelo.
    const pts = curve([tb, tb.clone().add(V(0.08, -0.04, -0.07)), V(bw + 0.1, 0.045, -torsoLen * 0.4), V(bw + 0.14, 0.05, 0.06 * s), V(bw + 0.1, 0.07, 0.14 * s)], 10);
    const plume = type === 'plume';
    tube(pts, thick * (plume ? 1.15 : 1.35), thick * (plume ? 0.5 : 0.65));
    if (plume && fluffy) {
      for (let i = 2; i < pts.length - 1; i += 2) {
        S.add(shape.ellipsoid(pts[i].clone().add(V(0.035, 0.0, 0)).toArray(), [thick * 1.3, thick * 0.9, thick * 1.5]), { color: tailColor, k: 0.025, bone: TAIL });
      }
    }
    if (region('tailTip')) S.paint(shape.sphere(pts[pts.length - 1].toArray(), 0.045), { color: region('tailTip'), soft: 0.01 });
  }

  // ── Pelo ──
  if (m.coatType === 'fluffy' || (cat && m.coatType === 'long') || m.fluff > 0.2) {
    const f = (m.coatType === 'fluffy' ? 1 : cat && m.coatType === 'long' ? 1.35 : 0.5) + m.fluff;
    // Golilla: mechones alrededor del cuello y la cara.
    const fc = min(f, 1.5);
    for (const side of [-1, 1]) {
      for (let i = 0; i < 3; i++) {
        const a = onHead(side * (1.1 + i * 0.25), -0.38 - i * 0.1);
        const dir = V(side * 0.85, -0.5, -0.15 - i * 0.25).normalize();
        S.add(shape.cone(a.toArray(), a.clone().addScaledVector(dir, hr * 0.26 * fc).toArray(), hr * 0.15 * fc, hr * 0.065 * fc), { color: region('face') ?? base, k: 0.05, bone: HEAD });
      }
    }
    // Pechera: tres mechones redondos que asoman bajo la barbilla.
    const bibC = V(0, sh * 0.92, chestZ + 0.09);
    for (let i = 0; i < 3; i++) {
      const x = (i - 1) * bw * 0.36;
      const a = bibC.clone().add(V(x, -abs(i - 1) * 0.02, -abs(i - 1) * 0.025));
      S.add(shape.cone(a.toArray(), a.clone().add(V(x * 0.1, -0.075 * fc, 0.035)).toArray(), 0.055 * fc, 0.03 * fc), { color: region('chest') ?? base, k: 0.05, bone: BODY });
    }
  }
  if (m.coatType === 'feathered') {
    for (const side of [-1, 1]) {
      const top = shoulders[side < 0 ? 0 : 1];
      for (let i = 0; i < 3; i++) {
        const a = top.clone().add(V(0, -0.06 - i * 0.07, -0.02));
        S.add(shape.cone(a.toArray(), a.clone().add(V(side * 0.01, -0.05, -0.05)).toArray(), 0.028, 0.008), { color: base, k: 0.03, bone: side < 0 ? ARM_L : ARM_R });
      }
    }
    const bibC = V(0, sh * 0.88, chestZ + 0.09);
    for (let i = 0; i < 3; i++) {
      const a = bibC.clone().add(V((i - 1) * bw * 0.34, -abs(i - 1) * 0.02, 0));
      S.add(shape.cone(a.toArray(), a.clone().add(V(0, -0.09, 0.025)).toArray(), 0.045, 0.024), { color: region('chest') ?? base, k: 0.045, bone: BODY });
    }
  }
  if (m.coatType === 'long' && !cat) {
    // Melena larga que cae por los lados de la cara y el cuerpo.
    const hair = region('head') ?? base;
    for (const side of [-1, 1]) {
      for (let i = 0; i < 3; i++) {
        const a = onHead(side * (0.9 + i * 0.28), -0.1 - i * 0.08);
        const end = a.clone().add(V(side * hr * 0.12, -hr * (0.55 + i * 0.12), -hr * 0.05 * i));
        S.add(shape.cone(a.toArray(), end.toArray(), hr * 0.16, hr * 0.07), { color: hair, k: 0.05, bone: HEAD });
      }
    }
    for (let i = 0; i < 8; i++) {
      const ang = (i / 8) * PI * 2 + 0.2;
      const top = V(sin(ang) * bw * 0.8, sh * 0.9, cos(ang) * torsoLen * 0.8 - 0.02);
      const bottom = V(sin(ang) * (bw + 0.06), 0.03, cos(ang) * (torsoLen + 0.05) - 0.02);
      S.add(shape.cone(top.toArray(), bottom.toArray(), 0.05, 0.075), { color: base, k: 0.05, bone: BODY });
    }
  }
  if (m.coatType === 'curly') {
    // Rizos: bultitos por todo el cuerpo y un pompón en la cabeza.
    // Bultitos sobre la superficie ya esculpida (cuerpo, patas y cabeza).
    const bump = (p, r, bone) => S.add(shape.sphere(p.toArray(), r), { color: base, k: 0.018, bone });
    for (let i = 0; i < 120; i++) {
      const u = (i + 0.5) / 120;
      const ang = i * 2.399;
      const y = 0.03 + u * (sh + 0.05);
      const point = surfaceAt(y, ang);
      const { normal } = S.project(point, 1);
      bump(point.addScaledVector(normal, -0.008), 0.026 + rng.range(0, 0.008), BODY);
    }
    for (let i = 0; i < 46; i++) {
      const u = (i + 0.5) / 46;
      const pitch = -0.2 + u * 1.75;
      const yaw = i * 2.399;
      if (cos(yaw) > 0.55 && pitch < 0.55) continue;
      const { point, normal } = S.project(onHead(yaw, pitch));
      bump(point.addScaledVector(normal, -0.006), hr * 0.13 + rng.range(0, hr * 0.03), HEAD);
    }
    for (let i = 0; i < 7; i++) {
      const a = i * 2.399;
      const r = i === 0 ? 0 : hr * 0.26;
      S.add(shape.sphere([sin(a) * r, headC.y + hr * 0.95 + (i % 3) * hr * 0.05, headC.z - hr * 0.08 + cos(a) * r * 0.8], hr * 0.2), { color: base, k: 0.03, bone: HEAD });
    }
  }
  if (m.extras.has('topknot') && m.coatType !== 'curly') {
    const t = onHead(0, PI / 2 - 0.2);
    S.add(shape.cone(t.toArray(), t.clone().add(V(0, hr * 0.38, -hr * 0.05)).toArray(), hr * 0.16, hr * 0.06), { color: region('head') ?? base, k: 0.05, bone: HEAD });
  }
  if (m.extras.has('beard') || m.coatType === 'wire') {
    const beard = region('muzzle') ?? base;
    S.add(shape.ellipsoid([0, mC.y - mh * 1.05, tipZ - mh * 0.7], [mw * 0.7, mh * 0.7, mh * 0.7]), { color: beard, k: 0.04, bone: HEAD });
    for (const side of [-1, 1]) {
      S.add(shape.cone([side * mw * 0.45, mC.y - mh * 0.5, tipZ - mh * 0.4], [side * mw * 0.7, mC.y - mh * 1.6, tipZ - mh * 0.5], mh * 0.3, mh * 0.08), { color: beard, k: 0.03, bone: HEAD });
    }
  }
  if (m.extras.has('wrinkles')) {
    // Pliegues de la frente y el rollo sobre la nariz.
    for (let i = 0; i < 2; i++) {
      const pitch = 0.36 + i * 0.12;
      let prev = null;
      for (let j = -2; j <= 2; j++) {
        const p = S.project(onHead(j * 0.1, pitch - abs(j) * 0.03)).point;
        if (prev) S.add(shape.capsule(prev.toArray(), p.toArray(), hr * 0.022), { color: base, k: 0.02, bone: HEAD });
        prev = p;
      }
    }
    if (snoutLen < hr * 0.3) {
      S.add(shape.capsule([-mw * 0.55, mC.y + mh * 0.75, tipZ - mh * 0.25], [mw * 0.55, mC.y + mh * 0.75, tipZ - mh * 0.25], mh * 0.2), { color: base, k: 0.03, bone: HEAD });
    }
  }
  if (m.extras.has('mane')) {
    // Melena de león alrededor de la cara.
    for (let i = 0; i < 14; i++) {
      const a = (i / 14) * PI * 2;
      if (cos(a) > 0.55) continue;
      const p = V(sin(a) * head.rx * 1.0, headC.y - hr * 0.05 + cos(a) * hr * 0.92, headC.z - hr * 0.42);
      S.add(shape.sphere(p.toArray(), hr * 0.27), { color: base, k: 0.05, bone: HEAD });
    }
    S.add(shape.ellipsoid([0, sh + 0.02, 0.05], [bw * 1.25, 0.16, 0.16]), { color: base, k: 0.07, bone: BODY, split: { bone: HEAD, from: sh - 0.04, to: sh + 0.1 } });
  }
  if (m.extras.has('brows') || m.coatType === 'wire') {
    for (const side of [-1, 1]) {
      const a = S.project(onHead(side * 0.32, 0.36)).point;
      S.add(shape.cone(a.toArray(), a.clone().add(V(side * hr * 0.12, hr * 0.06, hr * 0.2)).toArray(), hr * 0.1, hr * 0.03), { color: region('brows') ?? base, k: 0.03, bone: HEAD });
    }
  }
  if (m.extras.has('folds')) {
    S.add(shape.torus([0, sh + 0.0, 0.04], bw * 0.85, 0.035, [0.3, 0, 0]), { color: base, k: 0.04, bone: BODY });
    S.add(shape.torus([0, sh * 0.62, -0.02], bw * 1.02, 0.03, [0.3, 0, 0]), { color: base, k: 0.04, bone: BODY });
  }
  if (m.coatType === 'rex') {
    for (let i = 0; i < 60; i++) {
      const ang = i * 2.399;
      const p = surfaceAt(0.05 + ((i + 0.5) / 60) * (sh + 0.04), ang);
      const { normal } = S.project(p, 1);
      S.add(shape.ellipsoid(p.addScaledVector(normal, -0.006).toArray(), [0.02, 0.012, 0.02]), { color: base, k: 0.016, bone: BODY });
    }
  }
  if (m.scruff > 0) {
    // Pelo de punta en la coronilla y el lomo.
    const n = 4;
    for (let i = 0; i < n; i++) {
      const a = onHead((i - (n - 1) / 2) * 0.2, PI / 2 - 0.35);
      const dir = headNormal(a).add(V(0, 0.4, -0.5)).normalize();
      S.add(shape.cone(a.toArray(), a.clone().addScaledVector(dir, hr * 0.28 * m.scruff).toArray(), hr * 0.09, 0.004), { color: base, k: 0.025, bone: HEAD });
    }
  }

  // ── Capa ──
  const headParts = { headC, head, onHead, mC, mw, mh, tipZ };
  for (const [name, color] of coat.paint) paintRegion(name, color);

  function soft(s = 0.012) { return s; }
  function paintPath(points, radius, color, softness = 0.008) {
    let prev = null;
    for (const p of points) {
      const q = S.project(p).point;
      if (prev) S.paint(shape.capsule(prev.toArray(), q.toArray(), radius), { color, soft: softness });
      prev = q;
    }
  }
  function paintRegion(name, color) {
    if (name === 'ears' || name === 'tail' || name === 'innerEar' || name === 'tailUnder' || name === 'tailTip') return;
    if (Array.isArray(color)) return paintPatches(color, name === 'patches' ? 6 : 4);
    const c = col(color);
    const { head: hd } = headParts;
    switch (name) {
      case 'muzzle':
        S.paint(shape.ellipsoid([0, mC.y - mh * 0.2, mC.z + halfL * 0.15], [mw * 1.18, mh * 1.45, halfL * 1.05 + mh * 0.3]), { color: c, soft: soft(0.014) });
        break;
      case 'face':
        S.paint(shape.ellipsoid([0, headC.y - hr * 0.42, headC.z + hr * 0.45], [hd.rx * 1.02, hr * 0.5, hr * 0.85]), { color: c, soft: soft(0.02) });
        S.paint(shape.ellipsoid([0, sh + 0.02, 0.1], [bw * 0.7, 0.12, 0.12]), { color: c, soft: soft(0.02) });
        break;
      case 'mask':
        S.paint(shape.ellipsoid([0, mC.y, mC.z + halfL * 0.2], [mw * 1.25, mh * 1.6, halfL * 1.1 + mh * 0.4]), { color: c, soft: soft(0.02) });
        for (const side of [-1, 1]) {
          S.paint(shape.sphere(S.project(onHead(side * (0.4 + m.eyeGap), 0.14)).point.toArray(), hr * 0.2), { color: c, soft: soft(0.025) });
        }
        break;
      case 'blaze':
        paintPath([V(0, mC.y + mh * 0.9, tipZ - mh * 0.4), V(0, headC.y, headC.z + hr * 0.9), V(0, headC.y + hr * 0.5, headC.z + hr * 0.75), V(0, headC.y + hr * 0.85, headC.z + hr * 0.3)], hr * 0.1, c, 0.01);
        break;
      case 'brows':
        for (const side of [-1, 1]) {
          const p = S.project(onHead(side * (0.36 + m.eyeGap), 0.42)).point;
          S.paint(shape.sphere(p.toArray(), hr * 0.09), { color: c, soft: soft(0.006) });
        }
        break;
      case 'cheeks':
        for (const side of [-1, 1]) {
          S.paint(shape.sphere([side * mw * 0.85, mC.y - mh * 0.2, tipZ - mh * 0.9], mh * 0.75), { color: c, soft: soft(0.012) });
        }
        break;
      case 'head':
        S.paint(shape.ellipsoid(headC.toArray(), [hd.rx * 1.3, hd.ry * 1.25, hd.rz * 1.3]), { color: c, soft: soft(0.03) });
        break;
      case 'chest':
        S.paint(shape.ellipsoid([0, sh * 0.8, chestZ + 0.1], [bw * 0.6, 0.17, 0.09]), { color: c, soft: soft(0.02) });
        break;
      case 'belly':
        S.paint(shape.ellipsoid([0, sh * 0.32, 0.1], [bw * 0.62, sh * 0.34, 0.14]), { color: c, soft: soft(0.02) });
        break;
      case 'collar':
        S.paint(shape.torus([0, sh + 0.03, 0.04], bw * 0.72, 0.045, [0.25, 0, 0]), { color: c, soft: soft(0.015) });
        break;
      case 'paws':
        for (const p of [V(-bw * 0.44, 0, 0.18), V(bw * 0.44, 0, 0.18), V(-thigh.x, 0, 0.1), V(thigh.x, 0, 0.1)]) {
          S.paint(shape.ellipsoid([p.x, 0.04, p.z], [0.075, 0.07, 0.11]), { color: c, soft: soft(0.01) });
        }
        break;
      case 'legs':
        for (const side of [-1, 1]) {
          S.paint(shape.capsule([side * bw * 0.46, 0.0, 0.17], [side * bw * 0.48, sh * 0.62, 0.11], legR * 1.5), { color: c, soft: soft(0.01) });
          S.paint(shape.ellipsoid([side * thigh.x, 0.04, 0.1], [0.075, 0.08, 0.12]), { color: c, soft: soft(0.01) });
        }
        break;
      case 'saddle':
        S.paint(shape.ellipsoid([0, sh * 0.72, -torsoLen * 0.95], [bw * 1.4, sh * 0.5, torsoLen * 0.95]), { color: c, soft: soft(0.03) });
        S.paint(shape.ellipsoid([0, sh * 0.82, -torsoLen * 0.5], [bw * 1.1, sh * 0.32, torsoLen * 0.7]), { color: c, soft: soft(0.03) });
        break;
      case 'back':
        S.paint(shape.ellipsoid([0, sh * 0.82, -torsoLen * 0.85], [bw * 0.8, sh * 0.42, torsoLen * 0.65]), { color: c, soft: soft(0.03) });
        break;
      case 'points':
        // Puntas de los siameses: cara, orejas, patas y cola, difuminadas.
        S.paint(shape.ellipsoid([0, mC.y + mh * 0.1, mC.z + halfL * 0.3], [mw * 1.35, mh * 1.9, halfL * 1.1 + mh * 0.5]), { color: c, soft: soft(0.045) });
        for (const p of [V(-bw * 0.44, 0, 0.18), V(bw * 0.44, 0, 0.18), V(-thigh.x, 0, 0.1), V(thigh.x, 0, 0.1)]) {
          S.paint(shape.ellipsoid([p.x, 0.05, p.z], [0.08, 0.1, 0.12]), { color: c, soft: soft(0.04) });
        }
        break;
      case 'cap':
        // Solo lo alto de la cabeza (los van turcos).
        S.paint(shape.ellipsoid([0, headC.y + hr * 0.75, headC.z - hr * 0.1], [hd.rx * 1.05, hr * 0.55, hd.rz * 1.1]), { color: c, soft: soft(0.02) });
        break;
      case 'eyePatch': {
        const p = S.project(onHead(-(0.4 + m.eyeGap), 0.16)).point;
        S.paint(shape.sphere(p.toArray(), hr * 0.32), { color: c, soft: soft(0.01) });
        break;
      }
      case 'spots':
        for (let i = 0; i < 130; i++) {
          const ang = rng.range(0, PI * 2);
          const y = 0.04 + rng.next() * (headC.y + hr * 0.9);
          const p = surfaceAt(y, ang);
          if (p.y > headC.y - hr * 0.3 && p.z > headC.z + hr * 0.45) continue;
          S.paint(shape.sphere(p.toArray(), rng.range(0.013, 0.024)), { color: c, soft: 0.004 });
        }
        break;
      case 'rosettes':
        for (let i = 0; i < 60; i++) {
          const ang = rng.range(0, PI * 2);
          const y = 0.06 + rng.next() * sh * 1.05;
          const p = surfaceAt(y, ang);
          const r = rng.range(0.024, 0.036);
          S.paint(shape.sphere(p.toArray(), r), { color: c, soft: 0.004 });
          S.paint(shape.sphere(p.toArray(), r * 0.55), { color: base.clone().lerp(c, 0.35), soft: 0.004 });
        }
        break;
      case 'stripes': {
        // Rayas de atigrado: la M de la frente, mejillas, lomo, patas y cola.
        const w = hr * 0.045;
        // La cara solo la rayan los gatos; en los perros es atigrado de cuerpo.
        if (cat) {
          for (const x of [-0.18, 0, 0.18]) paintPath([onHead(x, 0.5), onHead(x * 1.2, 0.72), onHead(x * 1.3, 0.95)], w, c, 0.006);
          for (const side of [-1, 1]) {
            for (const dy of [0, -0.12]) paintPath([onHead(side * 0.95, 0.1 + dy), onHead(side * 1.25, 0.02 + dy), onHead(side * 1.5, -0.05 + dy)], w * 0.9, c, 0.006);
          }
        }
        for (let i = 0; i < 7; i++) {
          const z = -torsoLen * 0.9 + i * 0.045;
          const pts = [];
          for (let a = -1.6; a <= 1.6; a += 0.4) pts.push(V(sin(a) * (bw + 0.1), sh * 0.6 + cos(a) * 0.25, z + abs(a) * 0.02));
          paintPath(pts, w * 1.15, c, 0.007);
        }
        for (const side of [-1, 1]) {
          for (let i = 0; i < 3; i++) {
            const y = 0.08 + i * 0.07;
            S.paint(shape.torus([side * bw * 0.46, y, 0.15], legR * 1.02, w * 0.8, [0.15, 0, 0]), { color: c, soft: 0.005 });
          }
        }
        break;
      }
      default:
        break;
    }
  }
  // Punto de la superficie visto desde fuera, a la altura `y` y en el ángulo `ang`.
  function surfaceAt(y, ang) {
    const dir = V(sin(ang), 0, cos(ang));
    return S.cast(V(0, y, -0.03).addScaledVector(dir, 0.9), dir.clone().negate(), 1.2).point;
  }
  function paintPatches(colors, count) {
    for (let i = 0; i < count; i++) {
      const ang = rng.range(0, PI * 2);
      const y = 0.08 + rng.next() * (headC.y + hr * 0.6);
      const p = surfaceAt(y, ang);
      if (p.z > headC.z + hr * 0.4 && p.y > headC.y - hr * 0.6) continue;
      S.paint(shape.sphere(p.toArray(), rng.range(0.07, 0.12)), { color: col(colors[i % colors.length]), soft: 0.012 });
    }
  }

  // Marcas de las formas ocultas.
  if (m.look === 'radiante' || m.look === 'lunar') {
    const markColor = m.look === 'radiante' ? col(GOLD) : col(NIGHT.mark);
    const at = S.project(onHead(0, 0.62));
    const q = faceFrame(at.normal);
    if (m.look === 'radiante') {
      for (let i = 0; i < 5; i++) {
        const a = (i / 5) * PI * 2;
        const tip = at.point.clone().add(V(sin(a) * hr * 0.16, cos(a) * hr * 0.16, 0).applyQuaternion(q));
        S.paint(shape.capsule(at.point.toArray(), tip.toArray(), hr * 0.05), { color: markColor, soft: 0.004 });
      }
    } else {
      S.paint(shape.sphere(at.point.toArray(), hr * 0.15), { color: markColor, soft: 0.004 });
      S.paint(shape.sphere(at.point.clone().add(V(hr * 0.07, hr * 0.05, 0).applyQuaternion(q)).toArray(), hr * 0.14), { color: col(coat.base), soft: 0.004 });
    }
  }

  // ── Malla y huesos ──
  const finish = m.coatType === 'hairless' ? 'gloss' : fur.plush ? 'plush' : 'matte';
  const bodyGeometry = once(geo, 'body', () => S.build({ cell, bones: BONES, aoReach: 0.12, ao: detail === 'tiny' ? 0 : 1 }));
  const { mesh, bones } = skinned(bodyGeometry, clay({ finish, wrap: 0.36, sss: '#ff9d80' }), bonePos);

  const root = new THREE.Group();
  const bounce = new THREE.Group();
  const squash = new THREE.Group();
  root.add(bounce);
  bounce.add(squash);
  squash.add(mesh);
  root.scale.setScalar(P.scale);
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
      const g = surfacePatch(S, center, normal, up, width, height, 8, 0.0015);
      g.translate(-headBase.x, -headBase.y, -headBase.z);
      return g;
    });
    const mm = new THREE.Mesh(geometry, material);
    mm.renderOrder = 2;
    headPivot.add(mm);
    return mm;
  };

  // ── Nariz ──
  const noseAt = S.project(V(0, mC.y + mh * 0.42, tipZ + 0.02));
  const ns = cat ? mw * 0.3 : mw * (0.5 + (1 - m.snout) * 0.05);
  const noseColor = m.look === 'radiante' ? '#3a2a20' : (P.breed.nose ?? '#2a2523');
  const nose = new THREE.Mesh(once(geo, 'nose', () => {
    const N = new Sculpt();
    if (cat) {
      N.add(shape.ellipsoid([0, 0, 0], [ns, ns * 0.62, ns * 0.55]), { color: noseColor, k: 0 });
      N.add(shape.cone([0, -ns * 0.2, ns * 0.1], [0, -ns * 0.62, ns * 0.2], ns * 0.42, ns * 0.12), { color: noseColor, k: 0.02 });
      for (const side of [-1, 1]) N.sub(shape.ellipsoid([side * ns * 0.42, -ns * 0.18, ns * 0.42], [ns * 0.16, ns * 0.12, ns * 0.2]), { k: 0.01 });
    } else {
      N.add(shape.ellipsoid([0, 0, 0], [ns, ns * 0.66, ns * 0.62]), { color: noseColor, k: 0 });
      N.add(shape.ellipsoid([0, ns * 0.12, ns * 0.12], [ns * 0.9, ns * 0.5, ns * 0.55]), { color: noseColor, k: 0.03 });
      for (const side of [-1, 1]) N.sub(shape.ellipsoid([side * ns * 0.4, -ns * 0.1, ns * 0.55], [ns * 0.2, ns * 0.13, ns * 0.22], [0, 0, side * -0.5]), { k: 0.012 });
      N.sub(shape.capsule([0, -ns * 0.2, ns * 0.62], [0, -ns * 0.7, ns * 0.5], ns * 0.05), { k: 0.008 });
    }
    return N.build({ cell: ns * 0.09, ao: 0.6, aoReach: ns * 0.4 });
  }), clay({ finish: 'glaze', wrap: 0.1 }));
  nose.position.copy(noseAt.point).addScaledVector(noseAt.normal, ns * 0.12);
  nose.quaternion.copy(faceFrame(V(0, 0.25, 1).lerp(noseAt.normal, 0.5).normalize()));
  nose.castShadow = true;
  toHead(nose);

  // ── Boca ──
  const mouthW = cat ? mw * 1.3 : mw * 1.55;
  const mouthH = mouthW * (160 / 256);
  const noseBottom = noseAt.point.clone().add(V(0, -ns * (cat ? 0.55 : 0.6), 0));
  const mouthAt = S.project(noseBottom.clone().add(V(0, -mouthH * 0.5, 0.0)));
  const species = P.species;
  const mouthKinds = ['smile', 'sad', 'open', 'flat'];
  const mouths = Object.fromEntries(mouthKinds.map((kind) => [kind, petMouthTexture(species, kind)]));
  const mouth = decal('mouth', mouthAt.point, mouthAt.normal, mouthW, mouthH, own(decalMaterial(mouths.smile)));

  // Bigotes de los gatos: tres puntos en cada almohadilla.
  if (cat) {
    for (const side of [-1, 1]) {
      for (let i = 0; i < 3; i++) {
        const p = S.project(V(side * mw * (0.45 + (i % 2) * 0.12), mC.y - mh * 0.15 - i * mh * 0.12, tipZ)).point;
        const dot = new THREE.Mesh(new THREE.SphereGeometry(1, 8, 6), own(new THREE.MeshStandardMaterial({ color: base.clone().lerp(col('#2a1d18'), 0.55), roughness: 0.8 })));
        own(dot.geometry);
        dot.scale.setScalar(hr * 0.012);
        dot.position.copy(p);
        toHead(dot);
      }
    }
  }

  // ── Ojos ──
  const irisList = P.breed.eyes;
  let iris = irisList[clamp(genes.eyes ?? 0, 0, irisList.length - 1)];
  const eyeR = hr * (cat ? 0.19 : 0.175) * (1 + P.kid * 0.2) * m.eyes * (m.snout < 0.15 ? 1.12 : 1);
  const gap = (cat ? 0.42 : 0.4) + m.eyeGap + (m.snout < 0.15 ? 0.04 : 0);
  const eyePitch = cat ? 0.16 : 0.14;
  const eyes = [];
  for (const side of [-1, 1]) {
    const { point, normal } = S.project(onHead(side * gap, eyePitch));
    const skin = S.colorNear(point.clone().add(V(0, eyeR * 1.2, 0)));
    const irisColor = iris === 'odd' ? (side < 0 ? '#4a9ad8' : '#9a6a2a') : m.look === 'radiante' ? '#d9a52b' : iris;
    const eye = makeEye({
      point, normal, r: eyeR, tall: cat ? 1.02 : 1.08, iris: irisColor, kind: cat ? 'slit' : 'round', skin,
      tilt: cat ? -side * 0.16 : -side * 0.04, sparkle: m.look === 'radiante',
    });
    toHead(eye.group);
    const lidMat = own(decalMaterial(lidTexture('sleep')));
    const closed = decal(`lid${side}`, point, normal, eyeR * 3, eyeR * 2.2, lidMat);
    closed.visible = false;
    if (m.brows) eye.lid.rotation.z = side * 0.32;
    eyes.push({ group: eye.group, open: eye.open, closed, side, r: eyeR, tall: eye.tall, content: m.look === 'sibarita', restLid: m.brows ? max(m.lids, 0.22) : m.lids, lidValue: 0, lid: eye.lid, ball: eye.bead });
  }
  if (m.look === 'sibarita') for (const eye of eyes) { eye.open.visible = false; eye.closed.visible = true; }

  // Coloretes: apenas, solo cuando está feliz.
  const cheeks = [];
  for (const side of [-1, 1]) {
    const { point, normal } = S.project(onHead(side * (gap + 0.38), -0.1));
    const material = own(decalMaterial(blushTexture(), { color: '#ff8f9f', opacity: 0 }));
    cheeks.push(decal(`cheek${side}`, point, normal, hr * 0.4, hr * 0.26, material));
  }

  if (m.brows) {
    for (const side of [-1, 1]) {
      const { point, normal } = S.project(onHead(side * gap * 0.92, eyePitch + 0.36));
      decal(`brow${side}`, point, normal, eyeR * 2.6, eyeR * 1.3, own(decalMaterial(browTexture(side))));
    }
  }
  if (m.scar) {
    const { point, normal } = S.project(onHead(-(gap + 0.04), eyePitch + 0.02));
    decal('scar', point, normal, eyeR * 1.2, eyeR * 3.2, own(decalMaterial(scarTexture())));
  }

  // Aureola de la radiante.
  let halo = null;
  if (m.look === 'radiante') {
    halo = new THREE.Mesh(own(new THREE.TorusGeometry(hr * 0.5, hr * 0.06, 16, 48)), own(new THREE.MeshStandardMaterial({ color: '#ffe08a', emissive: '#ffcf5c', emissiveIntensity: 0.9, roughness: 0.3, metalness: 0.2 })));
    halo.rotation.set(PI / 2 - 0.22, 0, 0.1);
    halo.position.set(0, headC.y + hr * 1.5, headC.z - hr * 0.1);
    toHead(halo);
    halo.userData.y = halo.position.y;
  }

  // Pose: el atleta saluda con la pata.
  const arms = [ARM_L, ARM_R].map((i, k) => ({ pivot: bones[i], side: k ? 1 : -1, rest: 0, restX: 0, leg: true }));
  if (m.pose === 'paw') arms[1].restX = -0.95;

  // ── Anclajes para la ropa ──
  const hostLocal = { center: headC.clone().sub(headBase), rx: head.rx, ry: head.ry, rz: head.rz };
  let neckR = Infinity;
  let neckY = sh + 0.04;
  for (let y = sh - 0.04; y < sh + 0.12; y += 0.01) {
    const r = radiusAt(S, y, 1, 0);
    if (r > 0 && r < neckR) { neckR = r; neckY = y; }
  }
  const anchors = {
    hat: { y: hostLocal.center.y + head.ry * 0.96, r: head.rx },
    face: { y: hostLocal.center.y + head.ry * sin(eyePitch), z: head.rz, gap, r: head.rx, host: hostLocal, pitch: eyePitch },
    neck: { y: neckY, rx: neckR + 0.012, rz: (radiusAt(S, neckY, 0, 1) + radiusAt(S, neckY, 0, -1)) / 2 + 0.012 },
  };

  const height = (headC.y + head.ry) * P.scale;
  const plan = {
    species, family: P.family, stage: P.stage, scale: P.scale, blob: false,
    body: { w: bw * 2.2, h: sh }, head: { rx: head.rx }, finish,
  };

  return {
    object: root,
    plan,
    height,
    anchors,
    rig: {
      root, bounce, squash, headPivot, eyes, mouths, mouth, cheeks,
      ears: [{ pivot: bones[EAR_L], side: -1, rest: 0 }, { pivot: bones[EAR_R], side: 1, rest: 0 }],
      arms, feet: [], tail: bones[TAIL], wings: [], top: new THREE.Group(), halo, mesh,
      blush: { rest: 0, happy: 0.34 }, pet: species,
    },
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

// Cicatriz: un tajo con dos puntadas, sobre un ojo.
function scarTexture() {
  const key = 'scar';
  if (scarTexture.map) return scarTexture.map;
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 160;
  const ctx = canvas.getContext('2d');
  ctx.strokeStyle = 'rgba(70,30,30,0.85)';
  ctx.lineCap = 'round';
  ctx.lineWidth = 9;
  ctx.beginPath();
  ctx.moveTo(26, 12);
  ctx.lineTo(38, 148);
  ctx.stroke();
  ctx.lineWidth = 6;
  for (const y of [55, 100]) {
    ctx.beginPath();
    ctx.moveTo(18, y);
    ctx.lineTo(48, y + 4);
    ctx.stroke();
  }
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.userData.shared = true;
  texture.userData.key = key;
  scarTexture.map = texture;
  return texture;
}
