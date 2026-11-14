import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { MODEL_DATA } from '../public/js/scene/model-data.js';

const DIR = new URL('../public/models/', import.meta.url);
const BONES = ['body', 'head', 'ear_l', 'ear_r', 'arm_l', 'arm_r', 'tail'];

// El JSON de un .glb: cabecera de 12 bytes y el primer trozo (JSON).
function gltfOf(file) {
  const bytes = readFileSync(new URL(file, DIR));
  const length = bytes.readUInt32LE(12);
  return JSON.parse(bytes.subarray(20, 20 + length).toString('utf8'));
}

const glbs = readdirSync(DIR).filter((name) => name.endsWith('.glb'));

test('cada modelo de public/models tiene sus datos en model-data.js, y al revés', () => {
  assert.deepEqual(Object.keys(MODEL_DATA).sort(), glbs.map((name) => name.replace(/\.glb$/, '')).sort());
});

test('model-data.js está al día con los datos de atado de cada .glb', () => {
  for (const name of glbs) {
    const gltf = gltfOf(name);
    const node = gltf.nodes.find((n) => n.extras?.nidaria);
    assert.ok(node, `${name}: sin datos de atado`);
    const { box, ...data } = MODEL_DATA[name.replace(/\.glb$/, '')];
    assert.deepEqual(data, JSON.parse(node.extras.nidaria), `${name}: vuelve a generar model-data.js`);
    assert.equal(box.length, 2);
  }
});

test('cada modelo trae lo que necesita la criatura: huesos, dos ojos, boca y huesos de las llamas', () => {
  for (const [name, data] of Object.entries(MODEL_DATA)) {
    assert.deepEqual(Object.keys(data.bones).sort(), [...BONES].sort(), name);
    assert.equal(data.eyes.length, 2, name);
    for (const eye of data.eyes) {
      assert.match(eye.iris, /^#[0-9a-f]{6}$/, name);
      assert.match(eye.skin, /^#[0-9a-f]{6}$/, name);
      assert.ok(eye.r > 0 && eye.r < 0.1, name);
    }
    assert.ok(data.mouth?.width > 0, name);
    assert.ok(Math.abs(data.height - 1) < 0.01, `${name}: los modelos van a 1 de alto`);
    for (const flame of data.flames ?? []) assert.ok(BONES.includes(flame.bone), name);
  }
});
