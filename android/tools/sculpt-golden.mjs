// Vectores de referencia del escultor: los encargos que hacen los modelos
// de la web (criaturas, artículos, ropa, huevos y la casa) y la huella de
// algunas mallas, para que SculptParityTest compruebe que Kotlin esculpe
// lo mismo. Uso: node tools/sculpt-golden.mjs  (desde android/)

import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { registerHooks } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.join(here, '..', '..');

// Los módulos de la escena importan '/shared/…' como en el navegador.
registerHooks({
  resolve(specifier, context, next) {
    if (specifier.startsWith('/shared/')) return next(pathToFileURL(path.join(root, specifier)).href, context);
    return next(specifier, context);
  },
});

// Lienzos de mentira: las texturas no entran en la comparación.
const pen = new Proxy({}, { get: () => () => ({ addColorStop() {} }) });
globalThis.document = { createElement: () => ({ getContext: () => pen, width: 0, height: 0 }) };

const scene = (file) => import(pathToFileURL(path.join(root, 'public/js/scene', file)).href);
const THREE = await import('three');
const { Sculpt } = await scene('sdf.js');
const { meshJob } = await scene('sdf-core.js');
const { buildCreature } = await scene('creature.js');
const { buildItem, buildWear, buildEgg, isWear } = await scene('props.js');
const { buildHouse } = await scene('house.js');
const { FORM_LIST } = await import(pathToFileURL(path.join(root, 'shared/forms.js')).href);
const { ITEMS } = await import(pathToFileURL(path.join(root, 'shared/catalog.js')).href);
const { createGenes } = await import(pathToFileURL(path.join(root, 'shared/dna.js')).href);

// Cada build() deja aquí su encargo. Mientras se capturan no se esculpen:
// solo hacen falta los encargos.
let captured = null;
const build = Sculpt.prototype.build;
Sculpt.prototype.build = function capture(options) {
  if (!captured) return build.call(this, options);
  captured.push(this.toJob(options));
  return new THREE.BufferGeometry();
};
function jobsOf(make) {
  captured = [];
  const result = make();
  const jobs = captured;
  captured = null;
  return { result, jobs };
}

const round = (v) => (Array.isArray(v) ? v.map(round) : v);
const vec = (v) => [v.x, v.y, v.z];

// Huella de una malla, igual que SculptMesh.fingerprint en Kotlin.
function fingerprint(mesh) {
  const hash = createHash('sha256');
  const floats = (a) => hash.update(Buffer.from(new Float32Array(a).buffer));
  const ints = (a) => hash.update(Buffer.from(new Int32Array(a).buffer));
  floats(mesh.position); floats(mesh.normal); floats(mesh.color); floats(mesh.ao); ints(mesh.index);
  if (mesh.skinIndex) ints(mesh.skinIndex);
  if (mesh.skinWeight) floats(mesh.skinWeight);
  return hash.digest('hex').slice(0, 24);
}

// ── Criaturas: todas las formas, con ADN variado ──
// Las criaturas nuevas de la web (monster.js con los monstruos rehechos y
// pet.js con perros y gatos) aún no están portadas a Kotlin. Hasta que lo
// estén no se comparan; al portarlas, PORTED = true.
const PORTED = false;
const creatures = !PORTED ? [] : FORM_LIST.filter((form) => form.stage !== 'huevo').map((form, i) => {
  const seed = 1000 + i * 7919;
  const family = form.family ?? ['pelusa', 'hoja', 'gota'][i % 3];
  const genes = createGenes(seed, family);
  const { result: built, jobs } = jobsOf(() => buildCreature(form.id, genes));
  const head = built.rig.headPivot;
  const decals = [];
  head.traverse((node) => {
    if (!node.isMesh || node.renderOrder !== 2) return;
    const p = node.geometry.attributes.position.array;
    decals.push({ count: p.length / 3, first: Array.from(p.slice(0, 3)), last: Array.from(p.slice(-3)) });
  });
  const a = built.anchors;
  return {
    formId: form.id,
    seed,
    family,
    jobs,
    height: built.height,
    bones: built.rig.mesh.skeleton.bones.map((bone) => vec(bone.position)),
    eyes: built.rig.eyes.map((eye) => ({ position: vec(eye.group.position), quaternion: [eye.group.quaternion.x, eye.group.quaternion.y, eye.group.quaternion.z, eye.group.quaternion.w] })),
    decals,
    anchors: {
      hat: [a.hat.y, a.hat.r],
      face: [a.face.y, a.face.z, a.face.gap, a.face.r, a.face.pitch, ...vec(a.face.host.center), a.face.host.rx, a.face.host.ry, a.face.host.rz],
      neck: [a.neck.y, a.neck.rx, a.neck.rz],
    },
  };
});

// ── Artículos (en el maniquí, como en la tienda) ──
// La planta de siempre no se vende, pero está en la habitación.
const itemIds = [...(Array.isArray(ITEMS) ? ITEMS : Object.values(ITEMS)).map((item) => item.id), 'planta'];
const items = itemIds.map((id) => ({ id, jobs: jobsOf(() => buildItem(id)).jobs }));

// ── Ropa puesta en criaturas de verdad ──
const wearIds = itemIds.filter((id) => isWear(id));
const worn = !PORTED ? [] : [0, 5, 40].map((index) => {
  const source = creatures[index];
  const built = buildCreature(source.formId, createGenes(source.seed, source.family));
  return { formId: source.formId, seed: source.seed, family: source.family, items: wearIds.map((id) => ({ id, jobs: jobsOf(() => buildWear(id, built.anchors)).jobs })) };
});

// ── Huevos y casa ──
const eggs = [11, 222, 3333, 44444].map((seed) => {
  const genes = createGenes(seed);
  return { seed, jobs: jobsOf(() => buildEgg(genes)).jobs };
});
const house = jobsOf(() => buildHouse({ anisotropy: 1, sky: null })).jobs;

// ── Mallas: el escultor de Kotlin tiene que dar estas huellas exactas ──
const pick = [
  ...(PORTED ? [creatures[0].jobs[0], creatures[30].jobs[0]] : [items.find((item) => item.id === 'pan').jobs[0]]),
  items.find((item) => item.id === 'arroz').jobs[1],
  items.find((item) => item.id === 'cometa').jobs[0],
  items.find((item) => item.id === 'girasol').jobs[1],
  eggs[0].jobs[0],
  ...(PORTED ? [worn[1].items.find((item) => item.id === 'bufanda').jobs[0]] : [items.find((item) => item.id === 'bufanda').jobs[0]]),
  house[1],
];
const meshes = pick.map((job) => ({ job, fingerprint: fingerprint(meshJob(job)) }));

const file = path.join(here, '..', 'app', 'src', 'test', 'resources', 'sculpt.json');
fs.writeFileSync(file, JSON.stringify({ creatures, items, worn, eggs, house, meshes }));
console.log(`sculpt.json ${fs.statSync(file).size} bytes; ${creatures.length} criaturas, ${items.length} artículos, ${meshes.length} mallas`);
