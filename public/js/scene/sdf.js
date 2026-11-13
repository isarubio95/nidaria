// Escultor de formas blandas. Un modelo se describe como una suma de
// formas sencillas (esferas, elipsoides, conos redondeados, hojas…) que
// se funden con suavidad, como plastilina, y se convierte en una sola
// malla lisa y sin costuras. La malla sale con su color pintado por
// vértice, su oclusión ambiental (las sombras de los pliegues) y, si se
// pide, los pesos de los huesos para animarla.
//
// La superficie se extrae con "surface nets" sobre una rejilla y luego
// cada vértice se pega a la superficie exacta, así que la malla queda
// suave aunque la rejilla sea gruesa. Las cuentas están en sdf-core.js;
// aquí van los colores, las utilidades con vectores de Three.js y el paso
// a geometría, que se esculpe en segundo plano (ver sculpt-pool.js).

import * as THREE from 'three';
import { Field, shape, smin } from './sdf-core.js';
import { geometryFor } from './sculpt-pool.js';

export { shape, smin };

const tmpColor = new THREE.Color();
const toLinear = (color) => {
  tmpColor.set(color);
  return [tmpColor.r, tmpColor.g, tmpColor.b];
};

export class Sculpt extends Field {
  // `k` es cuánto se funde con lo anterior; `colorK`, lo ancho del paso
  // de su color al de al lado; `bone`, el hueso que la mueve.
  add(form, { color = '#ffffff', ...rest } = {}) {
    return this.addShape(form, { ...rest, color: toLinear(color) });
  }

  sub(form, options) {
    return this.subShape(form, options);
  }

  // Pinta sin cambiar la forma: lo que cae dentro de `form` toma el color.
  paint(form, { color, ...rest } = {}) {
    return this.paintShape(form, { ...rest, color: toLinear(color) });
  }

  // Lleva un punto a la superficie siguiendo el gradiente. Devuelve el
  // punto y la normal.
  project(point, steps = 4) {
    const p = point.clone();
    const n = new THREE.Vector3();
    const h = 0.002;
    for (let i = 0; i < steps; i++) {
      const d = this.sdf(p.x, p.y, p.z);
      this.gradient(p.x, p.y, p.z, h, n);
      p.addScaledVector(n, -d);
    }
    this.gradient(p.x, p.y, p.z, h, n);
    return { point: p, normal: n };
  }

  gradient(x, y, z, h, out) {
    out.set(
      this.sdf(x + h, y, z) - this.sdf(x - h, y, z),
      this.sdf(x, y + h, z) - this.sdf(x, y - h, z),
      this.sdf(x, y, z + h) - this.sdf(x, y, z - h),
    );
    const l = out.length();
    if (l > 1e-12) out.divideScalar(l);
    else out.set(0, 1, 0);
    return out;
  }

  // Punto de la superficie que se ve desde `from` mirando hacia `dir`.
  // Avanza por la distancia (sphere tracing).
  cast(from, dir, far = 3) {
    const p = from.clone();
    let t = 0;
    for (let i = 0; i < 96 && t < far; i++) {
      const d = this.sdf(p.x, p.y, p.z);
      if (Math.abs(d) < 0.0005) break;
      t += d * 0.8;
      p.copy(from).addScaledVector(dir, t);
    }
    return this.project(p, 2);
  }

  // Convierte la escultura en geometría (ver Field.mesh para las
  // opciones). Puede llegar vacía y rellenarse después: ver whenSculpted.
  build(options) {
    return geometryFor(this.toJob(options));
  }
}

// Rejilla que se adapta a la superficie, para pintar encima (la boca, los
// coloretes, los ojos cerrados). `center` y `normal` marcan dónde va;
// `up` es hacia dónde queda arriba en la textura. Devuelve la geometría
// en las coordenadas de la escultura.
export function surfacePatch(sculpt, center, normal, up, width, height, segments = 10, lift = 0.0025) {
  const n = normal.clone().normalize();
  const right = new THREE.Vector3().crossVectors(up, n).normalize();
  const top = new THREE.Vector3().crossVectors(n, right).normalize();
  const geometry = new THREE.PlaneGeometry(width, height, segments, segments);
  const pos = geometry.attributes.position;
  const p = new THREE.Vector3();
  const g = new THREE.Vector3();
  for (let i = 0; i < pos.count; i++) {
    p.copy(center).addScaledVector(right, pos.getX(i)).addScaledVector(top, pos.getY(i)).addScaledVector(n, 0.02);
    for (let it = 0; it < 4; it++) {
      const d = sculpt.sdf(p.x, p.y, p.z);
      sculpt.gradient(p.x, p.y, p.z, 0.002, g);
      p.addScaledVector(g, -d);
    }
    sculpt.gradient(p.x, p.y, p.z, 0.002, g);
    p.addScaledVector(g, lift);
    pos.setXYZ(i, p.x, p.y, p.z);
  }
  geometry.computeVertexNormals();
  return geometry;
}
