import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

// Muebles de modelo (public/js/scene/item-models.js y su gemelo en Android,
// scene/ItemModels.kt): cada uno tiene su .glb con textura.
const source = readFileSync(new URL('../public/js/scene/item-models.js', import.meta.url), 'utf8');
const ids = JSON.parse(source.match(/new Set\((\[[^\]]+\])\)/)[1].replace(/'/g, '"'));
const kotlin = readFileSync(new URL('../android/app/src/main/kotlin/com/nidaria/app/scene/ItemModels.kt', import.meta.url), 'utf8');

function gltf(id) {
  const bytes = readFileSync(new URL(`../public/models/items/${id}.glb`, import.meta.url));
  const length = bytes.readUInt32LE(12);
  return JSON.parse(bytes.subarray(20, 20 + length).toString('utf8'));
}

test('cada mueble de modelo tiene su .glb con malla y textura', () => {
  assert.ok(ids.length >= 9);
  for (const id of ids) {
    const doc = gltf(id);
    assert.equal(doc.meshes.length, 1, id);
    const primitive = doc.meshes[0].primitives[0];
    for (const key of ['POSITION', 'NORMAL', 'TEXCOORD_0']) assert.ok(key in primitive.attributes, `${id}: falta ${key}`);
    assert.ok(doc.materials[0].pbrMetallicRoughness.baseColorTexture, `${id}: sin textura`);
  }
});

test('la web y Android usan modelo para los mismos muebles', () => {
  const android = JSON.parse(kotlin.match(/setOf\(([^)]+)\)/)[0].replace('setOf(', '[').replace(')', ']'));
  assert.deepEqual([...android].sort(), [...ids].sort());
});
