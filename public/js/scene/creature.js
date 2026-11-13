// Construye la criatura 3D a partir de su plano y la anima. Todo son
// primitivas generadas por código: no hay modelos ni texturas externas.

import * as THREE from 'three';
import { gsap } from 'gsap';
import { createRng, clamp } from '/shared/rng.js';
import { planFor } from './plan.js';

const Z = new THREE.Vector3(0, 0, 1);
const Y = new THREE.Vector3(0, 1, 0);
const tmp = new THREE.Vector3();

const hslColor = (h, s, l) => new THREE.Color().setHSL(((h % 360) + 360) % 360 / 360, clamp(s, 0, 100) / 100, clamp(l, 0, 100) / 100);

function makeMaterials(genes, plan) {
  const s = genes.sat + plan.tone.sat;
  const l = genes.light + plan.tone.light;
  const body = hslColor(genes.hue, s, l);
  const light = hslColor(genes.hue, s - 14, l + 14);
  const accent = hslColor(genes.hue2, s + 6, l - 4);
  const finish = {
    plush: { roughness: 0.82, sheen: 1, sheenRoughness: 0.45, clearcoat: 0 },
    matte: { roughness: 0.66, sheen: 0.35, sheenRoughness: 0.6, clearcoat: 0 },
    gloss: { roughness: 0.3, sheen: 0, sheenRoughness: 0.5, clearcoat: 0.7 },
  }[plan.finish];
  const skin = (color) => new THREE.MeshPhysicalMaterial({
    color,
    roughness: finish.roughness,
    sheen: finish.sheen,
    sheenRoughness: finish.sheenRoughness,
    sheenColor: color.clone().lerp(new THREE.Color('#ffffff'), 0.55),
    clearcoat: finish.clearcoat,
    clearcoatRoughness: 0.25,
  });
  return {
    body: skin(body),
    light: skin(light),
    accent: skin(accent),
    eye: new THREE.MeshPhysicalMaterial({ color: '#3b2419', roughness: 0.12, clearcoat: 1, clearcoatRoughness: 0.05 }),
    white: new THREE.MeshBasicMaterial({ color: '#ffffff' }),
    ivory: new THREE.MeshStandardMaterial({ color: '#fff6e6', roughness: 0.5 }),
    mouth: new THREE.MeshStandardMaterial({ color: '#8a3443', roughness: 0.6 }),
    tongue: new THREE.MeshStandardMaterial({ color: '#ff8fa6', roughness: 0.6 }),
    cheek: new THREE.MeshStandardMaterial({ color: '#ff8fa9', roughness: 0.9, transparent: true, opacity: 0.62, depthWrite: false }),
    line: new THREE.MeshStandardMaterial({ color: '#4a2d20', roughness: 0.7 }),
    bubble: new THREE.MeshPhysicalMaterial({ color: light, roughness: 0.1, clearcoat: 1, transparent: true, opacity: 0.72 }),
    center: new THREE.MeshStandardMaterial({ color: '#ffd166', roughness: 0.6 }),
    // Sin luces: la aureola y la luna parecen brillar con luz propia.
    glow: new THREE.MeshBasicMaterial({ color: '#ffd76e' }),
    moon: new THREE.MeshBasicMaterial({ color: '#fff0b8' }),
    cherry: new THREE.MeshPhysicalMaterial({ color: '#e5465e', roughness: 0.25, clearcoat: 0.8, clearcoatRoughness: 0.1 }),
  };
}

// ── Geometrías ────────────────────────────────────────────────

const SPHERE = new THREE.SphereGeometry(1, 40, 28);
const SPHERE_LOW = new THREE.SphereGeometry(1, 20, 14);
SPHERE.userData.shared = true;
SPHERE_LOW.userData.shared = true;

function ball(material, rx, ry = rx, rz = rx, low = false) {
  const mesh = new THREE.Mesh(low ? SPHERE_LOW : SPHERE, material);
  mesh.scale.set(rx, ry, rz);
  mesh.castShadow = true;
  return mesh;
}

// Cuerpo en forma de pera: más ancho abajo, apoyado en el suelo.
function pearGeometry(w, h, d, taper = 0.26) {
  const geometry = new THREE.SphereGeometry(1, 44, 30);
  const pos = geometry.attributes.position;
  for (let i = 0; i < pos.count; i++) {
    const y = pos.getY(i);
    const widen = 1 + taper * (-y) * 0.5 + (y < -0.6 ? (y + 0.6) * 0.5 : 0);
    pos.setXYZ(i, pos.getX(i) * widen * (w / 2), y * (h / 2), pos.getZ(i) * widen * (d / 2));
  }
  geometry.computeVertexNormals();
  return geometry;
}

// Radio del cuerpo de pera a la altura `y` (misma fórmula que su geometría).
function pearRadius(y, h, half, taper = 0.26) {
  const yn = (y - h / 2) / (h / 2);
  if (Math.abs(yn) >= 1) return 0;
  const widen = 1 + taper * (-yn) * 0.5 + (yn < -0.6 ? (yn + 0.6) * 0.5 : 0);
  return half * widen * Math.sqrt(1 - yn * yn);
}

// Altura y radios del cuello: donde el cuerpo empieza a ser más ancho
// que la cabeza. Coordenadas del grupo `squash`.
function neckOf(plan, host, headY) {
  const { w, h, d } = plan.body;
  if (plan.blob) {
    const y = h * 0.3;
    return { y, rx: pearRadius(y, h, w / 2) + 0.005, rz: pearRadius(y, h, d / 2) + 0.005 };
  }
  const centerY = headY + host.center.y;
  const headAt = (y, r) => r * Math.sqrt(Math.max(0, 1 - ((y - centerY) / host.ry) ** 2));
  let y = h * 0.75;
  for (let probe = h; probe > h * 0.5; probe -= 0.01) {
    if (pearRadius(probe, h, w / 2) >= headAt(probe, host.rx)) {
      y = probe;
      break;
    }
  }
  return {
    y,
    rx: Math.max(pearRadius(y, h, w / 2), headAt(y, host.rx)) + 0.012,
    rz: Math.max(pearRadius(y, h, d / 2), headAt(y, host.rz)) + 0.012,
  };
}

function leafGeometry(length, width, thick = 0.05) {
  const shape = new THREE.Shape();
  shape.moveTo(0, 0);
  shape.bezierCurveTo(width * 0.75, length * 0.2, width * 0.55, length * 0.8, 0, length);
  shape.bezierCurveTo(-width * 0.55, length * 0.8, -width * 0.75, length * 0.2, 0, 0);
  const geometry = new THREE.ExtrudeGeometry(shape, {
    depth: thick, bevelEnabled: true, bevelThickness: thick * 0.5, bevelSize: thick * 0.5, bevelSegments: 3, curveSegments: 14,
  });
  geometry.translate(0, 0, -thick / 2);
  return geometry;
}

function finGeometry(length, width, thick = 0.05) {
  const shape = new THREE.Shape();
  shape.moveTo(-width * 0.5, 0);
  shape.quadraticCurveTo(-width * 0.1, length * 0.7, width * 0.35, length);
  shape.quadraticCurveTo(width * 0.6, length * 0.35, width * 0.5, 0);
  shape.quadraticCurveTo(0, -length * 0.12, -width * 0.5, 0);
  const geometry = new THREE.ExtrudeGeometry(shape, {
    depth: thick, bevelEnabled: true, bevelThickness: thick * 0.5, bevelSize: thick * 0.5, bevelSegments: 3, curveSegments: 12,
  });
  geometry.translate(0, 0, -thick / 2);
  return geometry;
}

// Gota con la punta hacia arriba; la base queda en y = 0.
function dropGeometry(radius, height) {
  const points = [];
  for (let i = 0; i <= 24; i++) {
    const t = (i / 24) * Math.PI;
    points.push(new THREE.Vector2(radius * Math.sin(t) * Math.pow(Math.sin(t / 2), 1.25) * 1.35, height * (1 - (1 - Math.cos(t)) / 2)));
  }
  points.reverse();
  return new THREE.LatheGeometry(points, 22);
}

function cone(material, radius, height) {
  const mesh = new THREE.Mesh(new THREE.ConeGeometry(radius, height, 18, 1), material);
  mesh.geometry.translate(0, height / 2, 0);
  mesh.castShadow = true;
  return mesh;
}

function shaped(geometry, material) {
  const mesh = new THREE.Mesh(geometry, material);
  mesh.castShadow = true;
  return mesh;
}

// Punto y normal sobre un elipsoide. `yaw` gira desde el frente (+z)
// hacia la derecha; `pitch` sube desde el ecuador.
function onSurface(host, yaw, pitch) {
  const cp = Math.cos(pitch);
  const point = new THREE.Vector3(
    host.rx * Math.sin(yaw) * cp,
    host.ry * Math.sin(pitch),
    host.rz * Math.cos(yaw) * cp,
  );
  const normal = new THREE.Vector3(point.x / host.rx ** 2, point.y / host.ry ** 2, point.z / host.rz ** 2).normalize();
  point.add(host.center);
  return { point, normal };
}

function place(object, host, yaw, pitch, sink = 0, axis = Z) {
  const { point, normal } = onSurface(host, yaw, pitch);
  object.position.copy(point).addScaledVector(normal, -sink);
  object.quaternion.setFromUnitVectors(axis, normal);
  return normal;
}

// ── Construcción ──────────────────────────────────────────────

export function buildCreature(formId, genes) {
  const plan = planFor(formId, genes);
  const mats = makeMaterials(genes, plan);
  const rng = createRng(genes.seed ^ 0x9e3779b9);
  const sock = plan.pattern === 'calcetines' ? mats.accent : mats.body;

  const root = new THREE.Group();
  const bounce = new THREE.Group();
  const squash = new THREE.Group();
  const headPivot = new THREE.Group();
  root.add(bounce);
  bounce.add(squash);
  root.scale.setScalar(plan.scale);

  const { w, h, d } = plan.body;
  const body = shaped(pearGeometry(w, h, d), mats.body);
  body.position.y = h / 2;
  body.receiveShadow = true;

  // `host` es el elipsoide que lleva la cara: la cabeza, o el cuerpo
  // entero en las crías. Sus coordenadas son locales a headPivot.
  let host;
  if (plan.blob) {
    headPivot.position.set(0, h * 0.42, 0);
    body.position.y = h / 2 - h * 0.42;
    headPivot.add(body);
    host = { center: new THREE.Vector3(0, h * 0.08, 0), rx: w / 2, ry: h / 2, rz: d / 2 };
  } else {
    squash.add(body);
    const { rx, ry, rz } = plan.head;
    headPivot.position.set(0, h * 0.86, 0);
    const head = ball(mats.body, rx, ry, rz);
    head.position.y = ry * 0.72;
    head.receiveShadow = true;
    headPivot.add(head);
    host = { center: head.position.clone(), rx, ry, rz };
  }
  squash.add(headPivot);
  const unit = host.rx / 0.5;

  // Cara
  const facePitch = plan.blob ? 0.2 : 0.04;
  const eyeR = 0.074 * unit * plan.eyes.size;
  const eyes = [];
  for (const side of [-1, 1]) {
    const group = new THREE.Group();
    place(group, host, side * plan.eyes.gap, facePitch, eyeR * 0.28);
    const eyeball = ball(mats.eye, eyeR, eyeR * (plan.eyes.style === 1 ? 1.28 : 1.06), eyeR * 0.6, true);
    eyeball.castShadow = false;
    const glint = ball(mats.white, eyeR * 0.3, eyeR * 0.3, eyeR * 0.14, true);
    glint.position.set(-eyeR * 0.3, eyeR * 0.36, eyeR * 0.52);
    glint.castShadow = false;
    const open = new THREE.Group();
    open.add(eyeball, glint);
    if (plan.eyes.style === 2) {
      const spark = ball(mats.white, eyeR * 0.16, eyeR * 0.16, eyeR * 0.1, true);
      spark.position.set(eyeR * 0.28, -eyeR * 0.3, eyeR * 0.54);
      spark.castShadow = false;
      open.add(spark);
    }
    // Párpado: una tapa del color del cuerpo que baja sobre el ojo.
    const lid = ball(mats.body, eyeR * 1.1, eyeR * 1.1, eyeR * 0.66, true);
    lid.castShadow = false;
    lid.position.y = eyeR * (2.16 - plan.eyes.lids * 2.12);
    lid.visible = plan.eyes.lids > 0.03;
    open.add(lid);
    const closed = new THREE.Mesh(new THREE.TorusGeometry(eyeR * 0.82, eyeR * 0.2, 8, 20, Math.PI), mats.line);
    closed.rotation.z = Math.PI;
    closed.position.set(0, eyeR * 0.25, eyeR * 0.3);
    closed.visible = false;
    group.add(open, closed);
    headPivot.add(group);
    eyes.push({ group, open, closed, lid, side, r: eyeR, restLid: plan.eyes.lids, lidValue: plan.eyes.lids });
  }

  if (plan.brows) {
    for (const side of [-1, 1]) {
      // Por encima del párpado caído, si lo hay, para que no lo tape.
      const lift = plan.eyes.lids > 0.2 ? 0.5 : 0.3;
      const brow = new THREE.Mesh(new THREE.CapsuleGeometry(eyeR * 0.24, eyeR * 1.5, 4, 8), mats.line);
      place(brow, host, side * plan.eyes.gap * 0.92, facePitch + lift, -eyeR * 0.12);
      brow.rotateZ(Math.PI / 2 + side * 0.42);
      headPivot.add(brow);
    }
  }

  const mouthR = 0.062 * unit;
  const mouth = new THREE.Group();
  place(mouth, host, 0, facePitch - 0.27, 0);
  const arc = (flip) => {
    const mesh = new THREE.Mesh(new THREE.TorusGeometry(mouthR, mouthR * 0.24, 8, 20, Math.PI), mats.line);
    mesh.rotation.z = flip ? 0 : Math.PI;
    mesh.position.y = flip ? -mouthR * 0.5 : mouthR * 0.4;
    return mesh;
  };
  const mouthOpen = new THREE.Group();
  const cavity = ball(mats.mouth, mouthR * 1.05, mouthR * 0.9, mouthR * 0.3, true);
  const tongue = ball(mats.tongue, mouthR * 0.62, mouthR * 0.42, mouthR * 0.2, true);
  tongue.position.set(0, -mouthR * 0.42, mouthR * 0.2);
  cavity.castShadow = tongue.castShadow = false;
  mouthOpen.add(cavity, tongue);
  const flat = new THREE.Mesh(new THREE.CapsuleGeometry(mouthR * 0.22, mouthR * 1.3, 4, 8), mats.line);
  flat.rotation.z = Math.PI / 2;
  const mouths = { smile: arc(false), sad: arc(true), open: mouthOpen, flat };
  for (const [name, mesh] of Object.entries(mouths)) {
    mesh.visible = name === 'smile';
    mouth.add(mesh);
  }
  if (plan.fang) {
    const fang = cone(mats.ivory, mouthR * 0.26, mouthR * 0.7);
    fang.rotation.z = Math.PI;
    fang.position.set(mouthR * 0.62, mouthR * 0.34, mouthR * 0.1);
    fang.castShadow = false;
    mouth.add(fang);
  }
  headPivot.add(mouth);

  const cheeks = [];
  for (const side of [-1, 1]) {
    const cheek = ball(mats.cheek.clone(), 0.085 * unit * plan.cheeks, 0.055 * unit * plan.cheeks, 0.02 * unit, true);
    cheek.castShadow = false;
    place(cheek, host, side * (plan.eyes.gap + 0.36), facePitch - 0.2, -0.004);
    headPivot.add(cheek);
    cheeks.push(cheek);
  }

  // Orejas
  const ears = [];
  const earSize = 0.2 * unit * plan.ears.size;
  for (const side of [-1, 1]) {
    const pivot = new THREE.Group();
    let mesh;
    if (plan.ears.type === 'round') {
      place(pivot, host, side * 0.95, 0.92, earSize * 0.3, Y);
      mesh = new THREE.Group();
      const outer = ball(sock, earSize, earSize, earSize * 0.5);
      const inner = ball(mats.light, earSize * 0.58, earSize * 0.58, earSize * 0.3, true);
      inner.position.z = earSize * 0.3;
      inner.castShadow = false;
      outer.position.y = inner.position.y = earSize * 0.6;
      mesh.add(outer, inner);
    } else if (plan.ears.type === 'pointy') {
      place(pivot, host, side * 0.9, 0.9, earSize * 0.25, Y);
      mesh = cone(sock, earSize * 0.72, earSize * 2);
      mesh.scale.z = 0.6;
    } else if (plan.ears.type === 'leaf') {
      place(pivot, host, side * 1.05, 0.8, earSize * 0.2, Y);
      mesh = shaped(leafGeometry(earSize * 2.5, earSize * 1.25, earSize * 0.16), mats.accent);
    } else {
      place(pivot, host, side * 1.2, 0.62, earSize * 0.2, Y);
      mesh = shaped(finGeometry(earSize * 1.55, earSize * 1.05, earSize * 0.16), mats.accent);
      mesh.scale.x = side;
    }
    const rest = side * -(0.18 + plan.ears.droop * 1.25);
    pivot.rotateZ(rest * (plan.ears.type === 'fin' ? 0.3 : 1));
    pivot.add(mesh);
    headPivot.add(pivot);
    ears.push({ pivot, side, rest: pivot.rotation.z });
  }

  // Remate de la cabeza
  const top = new THREE.Group();
  place(top, host, 0, Math.PI / 2 - 0.12, 0.02 * unit, Y);
  const ts = plan.top.size * unit;
  if (plan.top.type === 'tuft') {
    for (const [x, tilt, len] of [[0, 0, 0.24], [-0.07, 0.5, 0.18], [0.07, -0.5, 0.18]]) {
      const lock = shaped(dropGeometry(0.075 * ts, len * ts), mats.body);
      lock.position.x = x * ts;
      lock.rotation.z = tilt;
      top.add(lock);
    }
  } else if (plan.top.type === 'sprout') {
    const stem = shaped(new THREE.CylinderGeometry(0.022 * ts, 0.028 * ts, 0.2 * ts, 8), mats.accent);
    stem.position.y = 0.1 * ts;
    top.add(stem);
    for (const side of [-1, 1]) {
      const leaf = shaped(leafGeometry(0.26 * ts, 0.15 * ts, 0.025 * ts), mats.accent);
      leaf.position.y = 0.19 * ts;
      leaf.rotation.set(0.3, 0, side * -1.05);
      top.add(leaf);
    }
  } else if (plan.top.type === 'drop') {
    const drop = shaped(dropGeometry(0.1 * ts, 0.3 * ts), mats.accent);
    drop.position.y = -0.03 * ts;
    top.add(drop);
  } else if (plan.top.type === 'cherry') {
    const cream = shaped(dropGeometry(0.12 * ts, 0.17 * ts), mats.ivory);
    cream.position.y = -0.02 * ts;
    const berry = ball(mats.cherry, 0.072 * ts, 0.068 * ts, 0.072 * ts, true);
    berry.position.y = 0.19 * ts;
    const stem = shaped(new THREE.CylinderGeometry(0.008 * ts, 0.011 * ts, 0.13 * ts, 6), mats.line);
    stem.position.set(0.03 * ts, 0.29 * ts, 0);
    stem.rotation.z = -0.4;
    top.add(cream, berry, stem);
  } else if (plan.top.type === 'moon') {
    // Media luna de pie, con las puntas hacia arriba y un poco de lado.
    const moon = shaped(new THREE.TorusGeometry(0.12 * ts, 0.042 * ts, 10, 28, Math.PI * 1.2), mats.moon);
    moon.rotation.z = Math.PI * 1.15;
    moon.position.y = 0.15 * ts;
    top.add(moon);
  } else {
    for (const [x, tilt, len] of [[-0.15, 0.5, 0.2], [0, 0, 0.3], [0.15, -0.5, 0.2]]) {
      const spike = cone(mats.accent, 0.07 * ts, len * ts);
      spike.position.x = x * ts;
      spike.rotation.z = tilt;
      top.add(spike);
    }
  }
  headPivot.add(top);

  // Dibujo del ADN
  if (plan.pattern === 'motas') {
    const count = 4 + Math.round(plan.patternAmt * 6);
    for (let i = 0; i < count; i++) {
      const size = rng.range(0.045, 0.085) * unit;
      const spot = ball(mats.accent, size, size, size * 0.2, true);
      spot.castShadow = false;
      const yaw = rng.range(0.85, 2.2) * (rng.chance(0.5) ? 1 : -1);
      place(spot, host, yaw, rng.range(-0.1, 1.05), -size * 0.02);
      headPivot.add(spot);
    }
  } else if (plan.pattern === 'rayas') {
    for (const yaw of [-0.42, 0, 0.42]) {
      const stripe = ball(mats.accent, 0.036 * unit, 0.13 * unit * (0.6 + plan.patternAmt * 0.5), 0.016 * unit, true);
      stripe.castShadow = false;
      place(stripe, host, yaw, plan.blob ? 1.02 : 0.98, -0.004);
      headPivot.add(stripe);
    }
  }

  if (plan.belly) {
    const belly = ball(mats.light, w * 0.3, h * (plan.blob ? 0.22 : 0.33), d * 0.1);
    belly.castShadow = false;
    belly.position.set(0, plan.blob ? h * 0.22 : h * 0.45, d * (plan.blob ? 0.415 : 0.43));
    belly.rotation.x = plan.blob ? 0.5 : -0.12;
    squash.add(belly);
  }

  // Extremidades
  const feet = [];
  for (const side of [-1, 1]) {
    const foot = ball(sock, 0.12 * plan.feet, 0.075 * plan.feet, 0.16 * plan.feet);
    foot.position.set(side * w * 0.24, 0.06 * plan.feet, d * 0.3);
    squash.add(foot);
    feet.push(foot);
  }
  const arms = [];
  if (plan.arms) {
    for (const side of [-1, 1]) {
      const pivot = new THREE.Group();
      pivot.position.set(side * w * 0.44, h * 0.62, d * 0.1);
      const arm = ball(sock, 0.075, 0.15, 0.085);
      arm.position.y = -0.11;
      pivot.add(arm);
      pivot.rotation.z = side * 0.5;
      squash.add(pivot);
      arms.push({ pivot, side, rest: side * 0.5 });
    }
  }

  // Cola: asoma por un lado para verse desde delante.
  const tail = new THREE.Group();
  tail.position.set(w * 0.36, h * 0.2, -d * 0.36);
  const tsz = plan.tail.size;
  if (plan.tail.type === 'pom') tail.add(ball(mats.light, 0.13 * tsz).translateX(0.1 * tsz));
  else if (plan.tail.type === 'leaf') {
    const leaf = shaped(leafGeometry(0.4 * tsz, 0.2 * tsz, 0.03), mats.accent);
    leaf.rotation.z = -1;
    tail.add(leaf);
  } else if (plan.tail.type === 'drop') {
    const drop = shaped(dropGeometry(0.1 * tsz, 0.34 * tsz), mats.accent);
    drop.rotation.z = -1.15;
    tail.add(drop);
  } else {
    const spike = cone(mats.accent, 0.09 * tsz, 0.38 * tsz);
    spike.rotation.z = -1.1;
    tail.add(spike);
  }
  squash.add(tail);

  // Pieza de adulta
  const wings = [];
  let halo = null;
  if (plan.extra === 'halo') {
    halo = shaped(new THREE.TorusGeometry(0.22 * unit, 0.024 * unit, 10, 36), mats.glow);
    halo.castShadow = false;
    halo.rotation.set(Math.PI / 2 - 0.22, 0, 0.1);
    // Por encima del remate de la cabeza; el brote de las hojas es más alto.
    const lift = plan.top.type === 'sprout' ? 0.46 : 0.32;
    halo.position.set(0, host.center.y + host.ry + lift * unit, -0.03 * unit);
    halo.userData.y = halo.position.y;
    headPivot.add(halo);
  } else if (plan.extra === 'mane') {
    const neckY = h * 0.9;
    const count = 11;
    for (let i = 0; i < count; i++) {
      const angle = (i / count) * Math.PI * 2;
      const radius = w * 0.4;
      let piece;
      if (plan.family === 'hoja') {
        piece = shaped(leafGeometry(0.24, 0.16, 0.03), mats.accent);
        piece.rotation.set(Math.PI / 2 - 0.35, 0, 0);
        piece.rotation.order = 'YXZ';
        piece.rotation.y = angle;
      } else {
        piece = ball(plan.family === 'gota' ? mats.bubble : mats.light, 0.11, 0.1, 0.11, true);
      }
      piece.position.set(Math.sin(angle) * radius, neckY, Math.cos(angle) * radius * 0.92);
      squash.add(piece);
    }
  } else if (plan.extra === 'wings') {
    for (const side of [-1, 1]) {
      const pivot = new THREE.Group();
      pivot.position.set(side * w * 0.34, h * 0.72, -d * 0.3);
      let wing;
      if (plan.family === 'pelusa') {
        wing = new THREE.Group();
        for (let i = 0; i < 3; i++) {
          const feather = ball(mats.light, 0.07, 0.2 - i * 0.035, 0.03, true);
          feather.position.set(0, 0.16 - i * 0.02, 0);
          feather.rotation.z = -i * 0.45;
          wing.add(feather);
        }
      } else if (plan.family === 'hoja') {
        wing = shaped(leafGeometry(0.48, 0.26, 0.03), mats.accent);
      } else {
        wing = shaped(finGeometry(0.44, 0.3, 0.03), mats.accent);
      }
      wing.scale.x = side;
      pivot.add(wing);
      pivot.rotation.z = side * -0.95;
      squash.add(pivot);
      wings.push({ pivot, side, rest: side * -0.95 });
    }
  } else if (plan.extra === 'crown') {
    const count = 7;
    for (let i = 0; i < count; i++) {
      const angle = (i / count) * Math.PI * 2;
      const piece = new THREE.Group();
      const bead = ball(plan.family === 'gota' ? mats.ivory : plan.family === 'hoja' ? mats.accent : mats.light, 0.06 * unit, 0.06 * unit, 0.06 * unit, true);
      piece.add(bead);
      if (plan.family === 'hoja') {
        const heart = ball(mats.center, 0.03 * unit, 0.03 * unit, 0.03 * unit, true);
        heart.position.z = 0.045 * unit;
        piece.add(heart);
      }
      const { point, normal } = onSurface(host, angle, 1.02);
      piece.position.copy(point);
      piece.quaternion.setFromUnitVectors(Z, normal);
      headPivot.add(piece);
    }
  } else if (plan.extra === 'horns') {
    for (const side of [-1, 1]) {
      const horn = new THREE.Group();
      place(horn, host, side * 0.62, 1.12, 0.03 * unit, Y);
      const lower = cone(mats.ivory, 0.075 * unit, 0.22 * unit);
      const upper = cone(mats.ivory, 0.05 * unit, 0.2 * unit);
      upper.position.y = 0.16 * unit;
      upper.rotation.z = side * 0.5;
      horn.add(lower, upper);
      horn.rotateZ(side * -0.2);
      headPivot.add(horn);
    }
    for (let i = 0; i < 3; i++) {
      const spike = cone(mats.accent, 0.07, 0.2 - i * 0.03);
      spike.position.set(0, h * (0.75 - i * 0.2), -d * (0.42 + i * 0.04));
      spike.rotation.x = -1.1 - i * 0.2;
      squash.add(spike);
    }
  }

  const hostTop = host.center.y + host.ry;
  const anchors = {
    // Todo en coordenadas de headPivot, salvo `neck`, que va en `squash`.
    hat: { y: hostTop, r: host.rx },
    face: { y: host.center.y + host.ry * Math.sin(facePitch), z: host.rz, gap: plan.eyes.gap, r: host.rx, host, pitch: facePitch },
    neck: neckOf(plan, host, headPivot.position.y),
  };

  const height = (headPivot.position.y + hostTop) * plan.scale;

  return {
    object: root,
    plan,
    height,
    anchors,
    rig: { root, bounce, squash, headPivot, eyes, mouths, mouth, cheeks, ears, arms, feet, tail, wings, top, halo },
    dispose() {
      root.traverse((node) => {
        if (node.geometry && !node.geometry.userData.shared) node.geometry.dispose();
      });
      for (const material of Object.values(mats)) material.dispose();
      for (const cheek of cheeks) cheek.material.dispose();
    },
  };
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
    this.face = 'smile';
    this.wear = new Map();
  }

  setMouth(name) {
    if (this.face === name) return;
    this.face = name;
    for (const [key, mesh] of Object.entries(this.rig.mouths)) mesh.visible = key === name;
  }

  setMood(mood) {
    this.mood = mood;
    const asleep = mood === 'dormida';
    for (const eye of this.rig.eyes) {
      eye.open.visible = !asleep;
      eye.closed.visible = asleep;
    }
    if (!this.busy) this.setMouth(this.restMouth());
    const pale = mood === 'enferma';
    for (const cheek of this.rig.cheeks) {
      cheek.material.color.set(pale ? '#9ec3ea' : '#ff8fa9');
      cheek.material.opacity = mood === 'feliz' ? 0.8 : 0.62;
    }
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
    const depth = asleep ? 0.034 : 0.02;
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
        this.lean * 0.22 + (asleep ? 0.1 : 0) + Math.sin(t * 0.7) * 0.02,
      );
    }
    for (const eye of rig.eyes) {
      eye.open.position.set(this.gaze.x * 0.012, this.gaze.y * 0.008, 0);
    }

    // Parpadeo
    if (!asleep && t > this.blinkAt) {
      this.blink = 0.13;
      this.blinkAt = t + 2.2 + Math.random() * 3.6;
    }
    this.blink = Math.max(0, this.blink - dt);
    const shut = this.blink > 0 ? 1 : low ? 0.3 : 0;
    for (const eye of rig.eyes) {
      const lid = Math.max(eye.restLid, shut);
      eye.lidValue += (lid - eye.lidValue) * (1 - Math.exp(-dt * 40));
      eye.lid.visible = eye.lidValue > 0.03;
      eye.lid.position.y = eye.r * (2.16 - eye.lidValue * 2.12);
    }

    // Orejas, cola, alas y brazos
    const wiggle = Math.sin(t * 1.3) * 0.04 + (happy ? Math.sin(t * 7) * 0.03 : 0);
    for (const ear of rig.ears) ear.pivot.rotation.z = ear.rest + ear.side * (wiggle - (low ? 0.22 : 0));
    rig.tail.rotation.y = Math.sin(t * (happy ? 7 : 2.2)) * (asleep ? 0.03 : happy ? 0.5 : 0.18);
    for (const wing of rig.wings) wing.pivot.rotation.z = wing.rest + wing.side * Math.sin(t * (happy ? 9 : 2.6)) * (asleep ? 0.03 : 0.22);
    for (const arm of rig.arms) arm.pivot.rotation.z = arm.rest + arm.side * Math.sin(t * 1.9 + arm.side) * 0.07;
    if (rig.halo) rig.halo.position.y = rig.halo.userData.y + Math.sin(t * 1.6) * 0.012;

    if (this.shiver > 0 || mood === 'enferma') {
      this.shiver = Math.max(0, this.shiver - dt);
      rig.bounce.position.x = Math.sin(t * 46) * 0.006;
    } else {
      rig.bounce.position.x = 0;
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
    const tl = gsap.timeline({
      onComplete: () => {
        this.busy = false;
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
    });
  }

  eat(onBite) {
    this.setMouth('open');
    return this.act((tl, rig) => {
      for (let i = 0; i < 3; i++) {
        tl.to(rig.headPivot.rotation, { x: 0.34, duration: 0.14, ease: 'power2.out' })
          .call(() => { this.setMouth('flat'); onBite?.(i); })
          .to(rig.squash.scale, { x: 1.06, y: 0.94, z: 1.06, duration: 0.08 }, '<')
          .to(rig.headPivot.rotation, { x: 0.02, duration: 0.16, ease: 'power2.inOut' })
          .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.14 }, '<')
          .call(() => this.setMouth(i < 2 ? 'open' : 'smile'));
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
      tl.to(rig.root.rotation, { z: 0.12, duration: 0.12, ease: 'power1.inOut', yoyo: true, repeat: 7 })
        .to(rig.root.rotation, { z: 0, duration: 0.2, ease: 'power2.out' })
        .to(rig.squash.scale, { x: 1.1, y: 0.9, z: 1.1, duration: 0.1 })
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.4, ease: 'elastic.out(1, 0.45)' });
    });
  }

  nuzzle() {
    this.setMouth('smile');
    return this.act((tl, rig) => {
      tl.to(rig.squash.scale, { x: 1.08, y: 0.92, z: 1.08, duration: 0.16, ease: 'power2.out' })
        .to(rig.headPivot.rotation, { z: this.leanTarget * 0.34, x: 0.1, duration: 0.2, ease: 'power2.out' }, '<')
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.5, ease: 'elastic.out(1, 0.4)' })
        .to(rig.headPivot.rotation, { z: 0, x: 0, duration: 0.35, ease: 'power2.inOut' }, '<0.1');
    });
  }

  spin() {
    this.setMouth('open');
    return this.act((tl, rig) => {
      tl.to(rig.bounce.position, { y: 0.34, duration: 0.26, ease: 'power2.out' })
        .to(rig.root.rotation, { y: Math.PI * 2, duration: 0.6, ease: 'power2.inOut' }, '<')
        .to(rig.bounce.position, { y: 0, duration: 0.22, ease: 'power2.in' }, '>-0.24')
        .set(rig.root.rotation, { y: 0 })
        .to(rig.squash.scale, { x: 1.14, y: 0.84, z: 1.14, duration: 0.08 })
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.4, ease: 'elastic.out(1, 0.45)' });
    });
  }

  say(seconds) {
    this.talk = seconds;
  }
}
