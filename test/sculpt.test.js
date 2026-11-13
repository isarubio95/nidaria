import test from 'node:test';
import assert from 'node:assert/strict';
import * as THREE from 'three';
import { Field, shape, meshJob, fingerprint } from '../public/js/scene/sdf-core.js';

// Una figura pequeña con todas las formas, giros, restas, pintura y huesos.
function figure() {
  const field = new Field({ ground: 0, groundK: 0.03 });
  const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(0.3, -0.5, 0.2));
  field.addShape(shape.ellipsoid([0, 0.3, 0], [0.3, 0.25, 0.22], [0.1, 0.2, 0.3]), { color: [1, 0.5, 0.4], k: 0 });
  field.addShape(shape.sphere([0, 0.6, 0.05], 0.18), { color: [0.9, 0.8, 0.7], k: 0.08, bone: 1 });
  field.addShape(shape.cone([0.2, 0.6, 0], [0.35, 0.8, 0], 0.06, 0.02), { color: [0.2, 0.6, 0.3], k: 0.03, bone: 2, split: { bone: 1, from: 0.6, to: 0.8 } });
  field.addShape(shape.box([-0.25, 0.2, 0], [0.08, 0.06, 0.08], 0.02, [0, 0.4, 0, 'YXZ']), { color: [0.3, 0.3, 0.9], k: 0.02 });
  field.addShape(shape.cylinder([0, 0.05, 0.2], 0.08, 0.05, 0.02), { color: [0.9, 0.9, 0.2], k: 0.02 });
  field.addShape(shape.torus([0, 0.45, 0], 0.22, 0.03, q, 0.9), { color: [0.8, 0.2, 0.4], k: 0.02 });
  field.addShape(shape.leaf([0, 0.75, 0], q, 0.25, 0.12, 0.02, 0.2), { color: [0.3, 0.7, 0.4], k: 0.02 });
  field.subShape(shape.sphere([0, 0.62, 0.2], 0.05), { k: 0.02 });
  field.paintShape(shape.capsule([-0.1, 0.3, 0.2], [0.1, 0.3, 0.2], 0.03), { color: [1, 1, 1], soft: 0.01 });
  return field;
}

const OPTIONS = { cell: 0.03, bones: 3, ao: 1, aoReach: 0.1 };

test('el encargo pasado por JSON da la misma malla, byte a byte', () => {
  const field = figure();
  const direct = field.mesh(OPTIONS);
  const job = JSON.parse(JSON.stringify(field.toJob(OPTIONS)));
  const viaJob = meshJob(job);
  assert.ok(direct.position.length > 0);
  for (const name of Object.keys(direct)) {
    assert.deepEqual(Buffer.from(viaJob[name].buffer), Buffer.from(direct[name].buffer), name);
  }
});

test('el encargo no depende de lo que se haya consultado antes', () => {
  const field = figure();
  const before = fingerprint(JSON.stringify(field.toJob(OPTIONS)));
  // Consultar el campo deja valores de paso en las formas: no deben contar.
  for (let i = 0; i < 50; i++) field.sdf(Math.sin(i), Math.cos(i), i / 50);
  assert.equal(fingerprint(JSON.stringify(field.toJob(OPTIONS))), before);
});

test('los giros salen igual que con Three.js', () => {
  const orders = ['XYZ', 'YXZ', 'ZXY', 'ZYX', 'YZX', 'XZY'];
  orders.forEach((order, i) => {
    const angles = [0.3 + i * 0.4, -1.1 + i * 0.3, 2.2 - i * 0.5];
    const ours = shape.ellipsoid([0, 0, 0], [1, 1, 1], [...angles, order]).m;
    const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(...angles, order));
    const e = new THREE.Matrix4().makeRotationFromQuaternion(q).elements;
    assert.deepEqual(ours, [e[0], e[1], e[2], e[4], e[5], e[6], e[8], e[9], e[10]], order);
    assert.deepEqual(shape.box([0, 0, 0], [1, 1, 1], 0, q).m, ours, `${order} con cuaternión`);
  });
});

test('la huella distingue encargos distintos y repite los iguales', () => {
  const a = JSON.stringify(figure().toJob(OPTIONS));
  const b = JSON.stringify(figure().toJob({ ...OPTIONS, cell: 0.031 }));
  assert.equal(fingerprint(a), fingerprint(a));
  assert.notEqual(fingerprint(a), fingerprint(b));
  assert.match(fingerprint(a), /^[0-9a-f]{26}$/);
});
