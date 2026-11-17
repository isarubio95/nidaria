import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// Artículos de modelo (public/js/scene/item-models.js y su gemelo en
// Android, scene/ItemModels.kt): cada uno tiene su .glb con textura,
// cuantizado con art/props/pack_glb.py, y la misma rugosidad en los dos.
const source = readFileSync(new URL('../public/js/scene/item-models.js', import.meta.url), 'utf8');
const web = new Map([...source.match(/new Map\(\[([\s\S]+?)\]\);/)[1].matchAll(/\['([a-z]+)', ([0-9.]+)\]/g)].map((m) => [m[1], Number(m[2])]));
const ids = [...web.keys()];
const kotlin = readFileSync(new URL('../android/app/src/main/kotlin/com/nidaria/app/scene/ItemModels.kt', import.meta.url), 'utf8');
const android = new Map([...kotlin.match(/mapOf\(([\s\S]+?)\n\)/)[1].matchAll(/"([a-z]+)" to ([0-9.]+)f/g)].map((m) => [m[1], Number(m[2])]));

function gltf(id) {
  const bytes = readFileSync(new URL(`../public/models/items/${id}.glb`, import.meta.url));
  const length = bytes.readUInt32LE(12);
  return JSON.parse(bytes.subarray(20, 20 + length).toString('utf8'));
}

test('cada artículo de modelo tiene su .glb cuantizado, con malla y textura', () => {
  assert.ok(ids.length >= 26);
  for (const id of ids) {
    const doc = gltf(id);
    assert.equal(doc.meshes.length, 1, id);
    const primitive = doc.meshes[0].primitives[0];
    for (const key of ['POSITION', 'NORMAL', 'TEXCOORD_0']) assert.ok(key in primitive.attributes, `${id}: falta ${key}`);
    assert.ok(doc.materials[0].pbrMetallicRoughness.baseColorTexture, `${id}: sin textura`);
    assert.ok(doc.extensionsRequired?.includes('KHR_mesh_quantization'), `${id}: sin cuantizar (pack_glb.py)`);
    assert.equal(doc.accessors[primitive.attributes.POSITION].componentType, 5122, id);
  }
});

test('la web y Android usan modelo para los mismos artículos, con la misma rugosidad', () => {
  assert.deepEqual([...android.entries()].sort(), [...web.entries()].sort());
  for (const value of web.values()) assert.ok(value > 0 && value <= 1);
});
