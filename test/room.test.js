import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';

// La habitación horneada (art/room/build_room.py + pack_lightmaps.py).
const DIR = new URL('../public/rooms/', import.meta.url);
const info = JSON.parse(readFileSync(new URL('room.json', DIR), 'utf8'));

function gltf() {
  const bytes = readFileSync(new URL('room.glb', DIR));
  const length = bytes.readUInt32LE(12);
  return JSON.parse(bytes.subarray(20, 20 + length).toString('utf8'));
}

test('room.json apunta a archivos que existen', () => {
  for (const map of Object.values(info.maps)) {
    assert.ok(existsSync(new URL(map.file, DIR)), map.file);
    assert.equal(map.max.length, 3, `${map.file}: un máximo por canal (sol, cielo, lámpara)`);
    assert.ok(map.max.every((v) => v > 0), map.file);
  }
  assert.ok(existsSync(new URL(info.floorTexture.file, DIR)));
  for (const key of ['skyAtCreature', 'sunPeak', 'lampAtCreature']) assert.ok(info[key] > 0, key);
});

test('cada pieza del modelo tiene su mapa de luz y una segunda capa de UV', () => {
  const doc = gltf();
  const names = doc.nodes.map((node) => node.name).sort();
  assert.deepEqual(names, Object.keys(info.maps).sort());
  for (const mesh of doc.meshes) {
    for (const primitive of mesh.primitives) {
      assert.ok('TEXCOORD_1' in primitive.attributes, `${mesh.name}: falta la UV del mapa de luz`);
    }
  }
  const materials = new Set(doc.materials.map((m) => m.name));
  for (const name of ['floor', 'wallpaper', 'wainscot', 'curtain', 'painting_art']) assert.ok(materials.has(name), name);
});
