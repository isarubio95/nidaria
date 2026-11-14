// Criatura de modelo (un .glb con esqueleto, ver models.js) con la misma
// forma que devuelven buildPet y buildMonster, para que Actor la anime igual.
//
// Se monta en el acto con los datos de model-data.js (huesos, ojos, boca,
// llamas). Los huesos se rehacen con skinned() (orientación neutra, que es
// lo que espera Actor al poner giros absolutos) y los ojos son los 3D de
// siempre (makeEye), con párpados para parpadear y dormir. La malla, la
// textura y la boca llegan al descargarse el archivo, como las esculpidas
// cuando acaba el Worker: `userData.sculpting` lleva la promesa y nadie la
// enseña antes (ver whenSculpted).

import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { getForm } from '/shared/forms.js';
import { FAMILY_INFO, speciesOf } from '/shared/dna.js';
import {
  V, Y, HEAD, EAR_L, EAR_R, ARM_L, ARM_R, TAIL,
  skinned, makeEye, decalMaterial, lidTexture, faceTexture, INK,
} from './creature-kit.js';
import { makeFlame, FLAME_POWER } from './fire.js';

const ORDER = ['body', 'head', 'ear_l', 'ear_r', 'arm_l', 'arm_r', 'tail'];

// ── El archivo ────────────────────────────────────────────────

const loader = new GLTFLoader();
const sources = new Map();

// Cada archivo se carga una vez: su geometría en la postura del atado, con
// los índices de hueso en el orden del juego, y su material (las texturas
// son de todas las criaturas que lo usen: nadie las libera).
function source(file) {
  if (!sources.has(file)) {
    const loading = loader.loadAsync(file).then((gltf) => {
      gltf.scene.updateMatrixWorld(true);
      let mesh = null;
      gltf.scene.traverse((node) => {
        if (node.isSkinnedMesh) mesh = node;
      });
      if (!mesh) throw new Error(`${file}: falta la malla con esqueleto`);
      const geometry = mesh.geometry.clone();
      geometry.applyMatrix4(mesh.bindMatrix);
      const remap = mesh.skeleton.bones.map((bone) => Math.max(0, ORDER.indexOf(bone.name)));
      const index = geometry.attributes.skinIndex;
      for (let i = 0; i < index.count; i++) {
        for (let k = 0; k < 4; k++) index.setComponent(i, k, remap[index.getComponent(i, k)]);
      }
      return { geometry, material: mesh.material };
    });
    // Si falla (sin conexión), que el siguiente intento vuelva a pedirlo.
    loading.catch(() => sources.delete(file));
    sources.set(file, loading);
  }
  return sources.get(file);
}

// Copia propia de la geometría (compartir los mismos atributos entre
// mallas que se liberan por separado deja a las otras sin sus búferes).
function fill(target, from) {
  for (const [name, attribute] of Object.entries(from.attributes)) target.setAttribute(name, attribute.clone());
  if (from.index) target.setIndex(from.index.clone());
  target.computeBoundingSphere();
  target.computeBoundingBox();
}

// ── Color según el ADN ────────────────────────────────────────

// El tono del modelo se mueve poco dentro de su banda de la familia (de
// 4 a 40 en las pelusas: rojizas o anaranjadas) y entero si el ADN cae en
// otra banda (las pelusas azules). Saturación y luz, un poco.
function tintFor(model, genes, family) {
  const bands = FAMILY_INFO[family]?.hues ?? [];
  const home = bands.find(([lo, hi]) => model.hue >= lo && model.hue <= hi) ?? bands[0];
  let shift = 0;
  if (home && typeof genes?.hue === 'number') {
    const middle = (home[0] + home[1]) / 2;
    // Dentro de su banda, poco; fuera, hasta el tono del ADN desde el del modelo.
    shift = genes.hue >= home[0] && genes.hue <= home[1]
      ? (genes.hue - middle) * 0.6
      : ((genes.hue - model.hue + 540) % 360) - 180;
  }
  let sat = typeof genes?.sat === 'number' ? 0.82 + ((genes.sat - 52) / 22) * 0.33 : 1;
  let light = typeof genes?.light === 'number' ? 0.95 + ((genes.light - 62) / 12) * 0.1 : 1;
  // Girado del todo (un naranja vuelto azul) el color sale chillón: se apaga un poco.
  if (Math.abs(shift) > 60) sat *= 0.6;
  return { shift, sat, light };
}

// Giro de tono en YIQ (sobre el color aproximado en sRGB), más saturación
// y luz. Se aplica a la textura y a las brasas.
const TINT = /* glsl */ `
  uniform vec3 uTint;
  vec3 nidTint(vec3 c) {
    vec3 s = pow(max(c, 0.0), vec3(1.0 / 2.2));
    const mat3 toYiq = mat3(0.299, 0.596, 0.211, 0.587, -0.274, -0.523, 0.114, -0.322, 0.312);
    const mat3 toRgb = mat3(1.0, 1.0, 1.0, 0.956, -0.272, -1.106, 0.621, -0.647, 1.703);
    vec3 yiq = toYiq * s;
    // En YIQ el ángulo baja cuando el tono sube (rojo, amarillo, verde): se gira al revés.
    float cs = cos(uTint.x), sn = -sin(uTint.x);
    yiq.yz = mat2(cs, sn, -sn, cs) * yiq.yz * uTint.y;
    yiq.x *= uTint.z;
    return pow(max(toRgb * yiq, 0.0), vec3(2.2));
  }
`;

function applyTint(material, { shift, sat, light }) {
  if (Math.abs(shift) < 0.5 && Math.abs(sat - 1) < 0.01 && Math.abs(light - 1) < 0.01) return;
  const value = new THREE.Vector3(THREE.MathUtils.degToRad(shift), sat, light);
  material.onBeforeCompile = (shader) => {
    shader.uniforms.uTint = { value };
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', `#include <common>\n${TINT}`)
      .replace('#include <map_fragment>', '#include <map_fragment>\n  diffuseColor.rgb = nidTint(diffuseColor.rgb);')
      .replace('#include <emissivemap_fragment>', '#include <emissivemap_fragment>\n  totalEmissiveRadiance = nidTint(totalEmissiveRadiance);');
  };
  material.customProgramCacheKey = () => 'nidaria-tint';
}

// ── Boca ──────────────────────────────────────────────────────

// Para hocicos con nariz grande (el mismo trazo y colores que
// petMouthTexture): surco corto bajo la nariz y comisuras abiertas hasta
// los lados del hocico, que es lo que se ve con la cámara alta.
function wideMouthTexture(kind) {
  return faceTexture(`glbmouth:${kind}`, 256, 160, (ctx, w) => {
    const cx = w / 2;
    const drop = 34;
    const spread = 98;
    ctx.strokeStyle = INK;
    ctx.lineWidth = 9;
    ctx.beginPath();
    ctx.moveTo(cx, 2);
    ctx.lineTo(cx, drop);
    ctx.stroke();
    if (kind === 'open') {
      const wide = 70;
      const deep = 70;
      ctx.beginPath();
      ctx.moveTo(cx - wide, drop - 8);
      ctx.quadraticCurveTo(cx - wide * 0.4, drop + 8, cx, drop);
      ctx.quadraticCurveTo(cx + wide * 0.4, drop + 8, cx + wide, drop - 8);
      ctx.bezierCurveTo(cx + wide * 0.9, deep, cx - wide * 0.9, deep, cx - wide, drop - 8);
      ctx.closePath();
      ctx.fillStyle = '#6e2630';
      ctx.fill();
      ctx.save();
      ctx.clip();
      ctx.fillStyle = '#ef7f8e';
      ctx.beginPath();
      ctx.ellipse(cx, deep - 6, wide * 0.6, 18, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
      ctx.strokeStyle = INK;
      ctx.lineWidth = 8;
      ctx.stroke();
      return;
    }
    const lift = kind === 'smile' ? -22 : kind === 'sad' ? 18 : -4;
    const curve = kind === 'smile' ? 26 : kind === 'sad' ? -6 : 8;
    for (const side of [-1, 1]) {
      ctx.beginPath();
      ctx.moveTo(cx, drop);
      ctx.quadraticCurveTo(cx + side * spread * 0.45, drop + curve, cx + side * spread, drop + lift);
      ctx.stroke();
    }
  });
}

// Los triángulos de la malla que caen a menos de `radius` de `center`,
// como una malla suelta para lanzarle rayos sin recorrerla toda.
function nearMesh(geometry, center, radius) {
  const pos = geometry.attributes.position;
  const index = geometry.index;
  const count = index ? index.count : pos.count;
  const out = [];
  const a = V();
  const r2 = radius * radius;
  for (let i = 0; i < count; i += 3) {
    let near = false;
    for (let k = 0; k < 3 && !near; k++) {
      a.fromBufferAttribute(pos, index ? index.getX(i + k) : i + k);
      near = a.distanceToSquared(center) < r2;
    }
    if (!near) continue;
    for (let k = 0; k < 3; k++) {
      a.fromBufferAttribute(pos, index ? index.getX(i + k) : i + k);
      out.push(a.x, a.y, a.z);
    }
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(out, 3));
  return new THREE.Mesh(g, new THREE.MeshBasicMaterial({ side: THREE.DoubleSide }));
}

// Parche pegado a la superficie (como surfacePatch de sdf.js): una
// cuadrícula frente a `center` cuyos puntos se proyectan sobre la malla a
// lo largo de `normal`. De cada rayo vale el choque más cercano al plano.
function surfacePatch(geometry, center, normal, up, width, height, segments, lift) {
  const n = normal.clone().normalize();
  const right = V().crossVectors(up, n).normalize();
  const top = V().crossVectors(n, right).normalize();
  const target = nearMesh(geometry, center, Math.hypot(width, height) * 0.75 + 0.03);
  const patch = new THREE.PlaneGeometry(width, height, segments, segments);
  const pos = patch.attributes.position;
  const ray = new THREE.Raycaster();
  const back = n.clone().negate();
  const p = V();
  for (let i = 0; i < pos.count; i++) {
    p.copy(center).addScaledVector(right, pos.getX(i)).addScaledVector(top, pos.getY(i));
    ray.set(p.clone().addScaledVector(n, 0.08), back);
    ray.far = 0.16;
    let best = null;
    for (const hit of ray.intersectObject(target, false)) {
      if (!best || hit.point.distanceTo(p) < best.point.distanceTo(p)) best = hit;
    }
    if (best) p.copy(best.point).addScaledVector(n, lift);
    pos.setXYZ(i, p.x, p.y, p.z);
  }
  patch.computeVertexNormals();
  target.geometry.dispose();
  target.material.dispose();
  return patch;
}

// ── La criatura ───────────────────────────────────────────────

export function buildGlbCreature(formId, genes, model) {
  const form = getForm(formId);
  const family = form.family ?? genes?.family;
  const data = model.data;
  const tint = tintFor(model, genes, family);
  const owned = [];

  const geometry = new THREE.BufferGeometry();
  const material = new THREE.MeshStandardMaterial({ roughness: 0.85 });
  const bonePos = ORDER.map((name) => V(...data.bones[name]));
  const { mesh, bones } = skinned(geometry, material, bonePos);
  owned.push(geometry, material);

  const root = new THREE.Group();
  const bounce = new THREE.Group();
  const squash = new THREE.Group();
  root.add(bounce);
  bounce.add(squash);
  squash.add(mesh);
  const scale = model.height / data.height;
  root.scale.setScalar(scale);

  const headPivot = bones[HEAD];
  const headBase = bonePos[HEAD];
  const toHead = (object) => {
    object.position.sub(headBase);
    headPivot.add(object);
    return object;
  };

  // Ojos. Lo que se borró de la textura alrededor (el ojo pintado) toma el
  // color del pelo, que también cambia con el ADN.
  const skinOf = (hex) => new THREE.Color(hex).offsetHSL(tint.shift / 360, 0, 0);
  const eyes = data.eyes.map((e, i) => {
    const side = e.point[0] < 0 ? -1 : 1;
    const point = V(...e.point);
    const normal = V(...e.normal).lerp(V(0, 0, 1), model.eyeForward ?? 0.55).normalize();
    const skin = `#${skinOf(e.skin).getHexString()}`;
    const eye = makeEye({ point, normal, r: e.r, tall: e.tall, iris: e.iris, kind: 'round', skin, tilt: -side * 0.04 });
    toHead(eye.group);
    // Ojo cerrado: Actor esconde el globo y enseña `closed`, que aquí es la
    // línea de pestañas (su textura la cambia Actor: dormida o contenta)
    // sobre un párpado del color del pelo que tapa el hueco.
    const lidMat = decalMaterial(lidTexture('sleep'));
    const plane = new THREE.PlaneGeometry(e.r * 2.3, e.r * 1.6);
    plane.translate(0, -e.r * 0.1, e.r * 0.62 * 1.08);
    const coverMat = new THREE.MeshStandardMaterial({ color: skin, roughness: 0.85 });
    const coverGeo = new THREE.SphereGeometry(1, 24, 16);
    owned.push(plane, lidMat, coverMat, coverGeo);
    const closed = new THREE.Mesh(plane, lidMat);
    closed.renderOrder = 2;
    closed.position.copy(eye.group.position);
    closed.quaternion.copy(eye.group.quaternion);
    const cover = new THREE.Mesh(coverGeo, coverMat);
    cover.scale.set(e.r * 1.04, e.r * e.tall * 1.04, e.r * 0.62 * 1.04);
    closed.add(cover);
    closed.visible = false;
    headPivot.add(closed);
    return { group: eye.group, open: eye.open, closed, side, r: e.r, tall: eye.tall, content: false, restLid: 0, lidValue: 0, lid: eye.lid, ball: eye.bead, index: i };
  });

  // Boca: un calco sobre el hocico con las texturas que Actor cambia según
  // el humor. Su forma sale de la malla, así que se hace al llegar esta.
  const mouths = Object.fromEntries(['smile', 'sad', 'open', 'flat'].map((kind) => [kind, wideMouthTexture(kind)]));
  const mouthGeo = new THREE.BufferGeometry();
  const mouthMat = decalMaterial(mouths.smile, { polygonOffsetFactor: -4, polygonOffsetUnits: -4 });
  owned.push(mouthGeo, mouthMat);
  const mouth = new THREE.Mesh(mouthGeo, mouthMat);
  mouth.renderOrder = 2;
  toHead(mouth);

  // Llamas: cuelgan del hueso que diga el modelo (la cola), en su punta.
  const flames = (data.flames ?? []).map((f, i) => {
    const bone = ORDER.indexOf(f.bone);
    // El fuego solo cambia de color en las de otra banda (fuego azul).
    const fire = makeFlame({ size: f.size, seed: i * 7.31, hue: Math.abs(tint.shift) > 60 ? tint.shift : 0 });
    fire.object.position.copy(V(...f.point)).sub(bonePos[bone]);
    bones[bone].add(fire.object);
    owned.push(fire);
    return fire;
  });

  // Brasas pintadas (la textura de emisión de rig.py): laten despacio y
  // siguen el humor, como las llamas.
  const glow = { power: 1, target: 1 };
  const fx = {
    mood(name) {
      const power = FLAME_POWER[name] ?? 1;
      for (const f of flames) f.setPower(power);
      glow.target = power;
    },
  };

  const ready = source(model.file).then((from) => {
    fill(geometry, from.geometry);
    material.copy(from.material);
    material.envMapIntensity = 0.8;
    applyTint(material, tint);
    if (material.emissiveMap) {
      material.emissive.set('#ffffff');
      let last = performance.now() / 1000;
      mesh.onBeforeRender = () => {
        const now = performance.now() / 1000;
        const dt = Math.min(now - last, 0.1);
        last = now;
        glow.power += (glow.target - glow.power) * (1 - Math.exp(-dt * 3));
        material.emissiveIntensity = glow.power * (0.82 + 0.18 * Math.sin(now * 1.8));
      };
    }
    material.needsUpdate = true;
    if (data.mouth) {
      // Se proyecta desde donde mira la cámara del juego (delante y algo
      // arriba): así cae sobre la parte del hocico que se ve.
      const w = data.mouth.width;
      const n = V(...data.mouth.normal).lerp(V(0, 0.3, 1).normalize(), 0.8).normalize();
      const patch = surfacePatch(geometry, V(...data.mouth.point), n, Y, w, w * 0.625, 12, 0.005);
      fill(mouthGeo, patch);
      patch.dispose();
    }
  }).catch((error) => {
    console.error(`No se pudo cargar ${model.file}`, error);
  });
  geometry.userData.sculpting = ready;
  mouthGeo.userData.sculpting = ready;

  // ── Medidas para la habitación y anclajes para la ropa ──
  const [lo, hi] = data.box;
  const eyeY = (data.eyes[0].point[1] + data.eyes[1].point[1]) / 2;
  const eyeX = (data.eyes[0].point[0] + data.eyes[1].point[0]) / 2;
  const eyeZ = (data.eyes[0].point[2] + data.eyes[1].point[2]) / 2;
  const spacing = Math.abs(data.eyes[1].point[0] - data.eyes[0].point[0]);
  const mouthY = data.mouth?.point[1] ?? eyeY - spacing * 0.6;
  const crown = eyeY + (eyeY - mouthY) * 1.6;
  const headR = spacing * 1.5;
  const host = {
    center: V(eyeX - headBase.x, eyeY - headBase.y, 0),
    rx: headR, ry: crown - eyeY, rz: headR * 0.9,
  };
  const plan = {
    species: speciesOf(family), family, stage: form.stage, scale, blob: false, finish: 'glb',
    body: { w: (hi[0] - lo[0]) * 0.75, h: headBase.y }, head: { rx: headR },
  };
  const anchors = {
    hat: { y: crown - headBase.y, r: headR },
    face: { y: eyeY - headBase.y, z: eyeZ - headBase.z, gap: 0.4, r: headR, host, pitch: 0.14 },
    neck: { y: headBase.y, rx: headR * 0.75, rz: headR * 0.7 },
  };

  return {
    object: root,
    plan,
    height: model.height,
    anchors,
    rig: {
      root, bounce, squash, headPivot, eyes, mouths, mouth, cheeks: [],
      ears: model.ears ? [{ pivot: bones[EAR_L], side: -1, rest: 0 }, { pivot: bones[EAR_R], side: 1, rest: 0 }] : [],
      arms: [ARM_L, ARM_R].map((i, k) => ({ pivot: bones[i], side: k ? 1 : -1, rest: 0, restX: 0, leg: model.arms === 'legs' })),
      feet: [], tail: bones[TAIL], wings: [], top: new THREE.Group(), halo: null, mesh,
      blush: { rest: 0, happy: 0 }, fx,
    },
    dispose() {
      for (const thing of owned) thing.dispose?.();
      mesh.skeleton.dispose();
    },
  };
}
