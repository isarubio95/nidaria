// Escultor de formas blandas. Un modelo se describe como una suma de
// formas sencillas (esferas, elipsoides, conos redondeados, hojas…) que
// se funden con suavidad, como plastilina, y se convierte en una sola
// malla lisa y sin costuras. La malla sale con su color pintado por
// vértice, su oclusión ambiental (las sombras de los pliegues) y, si se
// pide, los pesos de los huesos para animarla.
//
// La superficie se extrae con "surface nets" sobre una rejilla y luego
// cada vértice se pega a la superficie exacta, así que la malla queda
// suave aunque la rejilla sea gruesa.

import * as THREE from 'three';

const { sqrt, abs, max, min } = Math;
const hypot = (x, y, z = 0) => sqrt(x * x + y * y + z * z);
const ADD = 0;
const SUB = 1;

export function smin(a, b, k) {
  if (k <= 0) return a < b ? a : b;
  const h = max(k - abs(a - b), 0) / k;
  return (a < b ? a : b) - h * h * k * 0.25;
}

function smax(a, b, k) {
  return -smin(-a, -b, k);
}

// ── Formas ────────────────────────────────────────────────────
// Cada forma sabe su distancia con signo (negativa dentro) y lleva una
// esfera que la envuelve, para no calcularla cuando está lejos.

const euler = new THREE.Euler();
const quat = new THREE.Quaternion();
const mat4 = new THREE.Matrix4();

// Matriz que pasa del mundo al espacio propio de la forma (la traspuesta
// de su giro). `rot` es [x, y, z] en radianes o un cuaternión.
function inverseRotation(rot) {
  if (!rot) return null;
  if (rot.isQuaternion) quat.copy(rot);
  else quat.setFromEuler(euler.set(rot[0] ?? 0, rot[1] ?? 0, rot[2] ?? 0, rot[3] ?? 'XYZ'));
  const e = mat4.makeRotationFromQuaternion(quat).elements;
  return [e[0], e[1], e[2], e[4], e[5], e[6], e[8], e[9], e[10]];
}

class Shape {
  constructor(center, rot, radius) {
    this.cx = center[0];
    this.cy = center[1];
    this.cz = center[2];
    this.m = inverseRotation(rot);
    this.bx = this.cx;
    this.by = this.cy;
    this.bz = this.cz;
    this.br = radius;
  }

  // Deja en lx, ly, lz el punto en coordenadas de la forma.
  local(x, y, z) {
    const dx = x - this.cx;
    const dy = y - this.cy;
    const dz = z - this.cz;
    const m = this.m;
    if (m) {
      this.lx = m[0] * dx + m[1] * dy + m[2] * dz;
      this.ly = m[3] * dx + m[4] * dy + m[5] * dz;
      this.lz = m[6] * dx + m[7] * dy + m[8] * dz;
    } else {
      this.lx = dx;
      this.ly = dy;
      this.lz = dz;
    }
  }
}

class Ellipsoid extends Shape {
  constructor(center, radii, rot) {
    super(center, rot, max(radii[0], radii[1], radii[2]));
    [this.rx, this.ry, this.rz] = radii;
  }

  d(x, y, z) {
    this.local(x, y, z);
    const ax = this.lx / this.rx;
    const ay = this.ly / this.ry;
    const az = this.lz / this.rz;
    const k0 = sqrt(ax * ax + ay * ay + az * az);
    const bx = ax / this.rx;
    const by = ay / this.ry;
    const bz = az / this.rz;
    const k1 = sqrt(bx * bx + by * by + bz * bz);
    if (k1 < 1e-9) return -min(this.rx, this.ry, this.rz);
    return (k0 * (k0 - 1)) / k1;
  }
}

class Sphere extends Shape {
  constructor(center, r) {
    super(center, null, r);
    this.r = r;
  }

  d(x, y, z) {
    return hypot(x - this.cx, y - this.cy, z - this.cz) - this.r;
  }
}

// Cono de puntas redondas entre a (radio ra) y b (radio rb). Con los dos
// radios iguales es una cápsula.
class RoundCone extends Shape {
  constructor(a, b, ra, rb) {
    const mid = [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2];
    super(mid, null, hypot(b[0] - a[0], b[1] - a[1], b[2] - a[2]) / 2 + max(ra, rb));
    this.ax = a[0];
    this.ay = a[1];
    this.az = a[2];
    this.bax = b[0] - a[0];
    this.bay = b[1] - a[1];
    this.baz = b[2] - a[2];
    this.r1 = ra;
    this.r2 = rb;
    this.l2 = this.bax * this.bax + this.bay * this.bay + this.baz * this.baz || 1e-9;
    this.rr = ra - rb;
    this.a2 = this.l2 - this.rr * this.rr;
    this.il2 = 1 / this.l2;
  }

  d(x, y, z) {
    const { bax, bay, baz, l2, rr, a2, il2 } = this;
    const pax = x - this.ax;
    const pay = y - this.ay;
    const paz = z - this.az;
    const yy = pax * bax + pay * bay + paz * baz;
    const zz = yy - l2;
    const qx = pax * l2 - bax * yy;
    const qy = pay * l2 - bay * yy;
    const qz = paz * l2 - baz * yy;
    const x2 = qx * qx + qy * qy + qz * qz;
    const y2 = yy * yy * l2;
    const z2 = zz * zz * l2;
    const k = Math.sign(rr) * rr * rr * x2;
    if (Math.sign(zz) * a2 * z2 > k) return sqrt(x2 + z2) * il2 - this.r2;
    if (Math.sign(yy) * a2 * y2 < k) return sqrt(x2 + y2) * il2 - this.r1;
    return (sqrt(x2 * a2 * il2) + yy * rr) * il2 - this.r1;
  }
}

class RoundBox extends Shape {
  constructor(center, half, round, rot) {
    super(center, rot, hypot(half[0], half[1], half[2]));
    [this.hx, this.hy, this.hz] = half;
    this.r = min(round, half[0], half[1], half[2]);
  }

  d(x, y, z) {
    this.local(x, y, z);
    const r = this.r;
    const qx = abs(this.lx) - this.hx + r;
    const qy = abs(this.ly) - this.hy + r;
    const qz = abs(this.lz) - this.hz + r;
    return hypot(max(qx, 0), max(qy, 0), max(qz, 0)) + min(max(qx, qy, qz), 0) - r;
  }
}

// Cilindro de eje y, con los cantos redondeados.
class Cylinder extends Shape {
  constructor(center, radius, half, round, rot) {
    super(center, rot, hypot(radius, half));
    this.r = radius;
    this.h = half;
    this.round = min(round, radius, half);
  }

  d(x, y, z) {
    this.local(x, y, z);
    const rr = this.round;
    const dx = hypot(this.lx, this.lz) - this.r + rr;
    const dy = abs(this.ly) - this.h + rr;
    return min(max(dx, dy), 0) + hypot(max(dx, 0), max(dy, 0)) - rr;
  }
}

// Rosco de eje y: radio mayor R, grosor r.
class Torus extends Shape {
  constructor(center, R, r, rot, scaleY = 1) {
    super(center, rot, R + r);
    this.R = R;
    this.r = r;
    this.sy = scaleY;
  }

  d(x, y, z) {
    this.local(x, y, z);
    const q = hypot(this.lx, this.lz) - this.R;
    return (hypot(q, this.ly / this.sy) - this.r) * min(1, this.sy);
  }
}

// Hoja: una vesica (dos arcos que se juntan en punta) con grosor y los
// bordes redondos. Nace en `base`, crece por su eje y y es plana en z.
// `bend` la curva hacia +z en la punta.
class Leaf extends Shape {
  constructor(base, rot, length, width, thick, bend = 0) {
    super(base, rot, length);
    // La esfera envolvente se centra a media hoja.
    const m = this.m;
    const half = length / 2;
    // Eje y de la forma en el mundo: la segunda fila de la traspuesta.
    this.bx = base[0] + (m ? m[3] : 0) * half;
    this.by = base[1] + (m ? m[4] : 1) * half;
    this.bz = base[2] + (m ? m[5] : 0) * half;
    this.br = half + thick + abs(bend) * length;
    this.len = length;
    this.hw = width / 2;
    this.t = thick / 2;
    this.bend = bend;
    const b = half;
    const w = this.hw;
    this.vr = (b * b + w * w) / (2 * w);
    this.vd = this.vr - w;
    this.vb = b;
  }

  d(x, y, z) {
    this.local(x, y, z);
    const len = this.len;
    const u = this.ly / len;
    const lz = this.lz - this.bend * len * u * u;
    const px = abs(this.lx);
    const py = abs(this.ly - len / 2);
    const { vr, vd, vb } = this;
    const d2 = (py - vb) * vd > px * vb ? hypot(px, py - vb) : hypot(px + vd, py) - vr;
    const rr = this.t * 0.92;
    const wx = d2 + rr;
    const wy = abs(lz) - this.t + rr;
    return min(max(wx, wy), 0) + hypot(max(wx, 0), max(wy, 0)) - rr;
  }
}

export const shape = {
  sphere: (c, r) => new Sphere(c, r),
  ellipsoid: (c, radii, rot) => new Ellipsoid(c, radii, rot),
  cone: (a, b, ra, rb) => new RoundCone(a, b, ra, rb),
  capsule: (a, b, r) => new RoundCone(a, b, r, r),
  box: (c, half, round = 0, rot) => new RoundBox(c, half, round, rot),
  cylinder: (c, radius, half, round = 0, rot) => new Cylinder(c, radius, half, round, rot),
  torus: (c, R, r, rot, scaleY) => new Torus(c, R, r, rot, scaleY),
  leaf: (base, rot, length, width, thick, bend) => new Leaf(base, rot, length, width, thick, bend),
};

// ── Escultura ────────────────────────────────────────────────

const tmpColor = new THREE.Color();
const toLinear = (color) => {
  tmpColor.set(color);
  return [tmpColor.r, tmpColor.g, tmpColor.b];
};

// Las 12 aristas de una celda como pares de esquinas (bit 1 = x, 2 = y, 4 = z).
const EDGES = [];
for (let a = 0; a < 8; a++) {
  for (const bit of [1, 2, 4]) if (!(a & bit)) EDGES.push([a, a | bit]);
}

export class Sculpt {
  // `ground`: altura del suelo. Lo que quede por debajo se corta con un
  // canto suave, como una figura apoyada, y el suelo oscurece los bajos.
  constructor({ ground = null, groundK = 0.03 } = {}) {
    this.ops = [];
    this.paints = [];
    this.ground = ground;
    this.groundK = groundK;
  }

  // `k` es cuánto se funde con lo anterior; `colorK`, lo ancho del paso
  // de su color al de al lado; `bone`, el hueso que la mueve.
  add(form, { color = '#ffffff', k = 0.04, bone = 0, colorK, split } = {}) {
    this.ops.push({ op: ADD, form, color: toLinear(color), k, bone, colorK: colorK ?? min(k * 0.5, 0.02) + 0.002, split });
    return this;
  }

  sub(form, { k = 0.02 } = {}) {
    this.ops.push({ op: SUB, form, k });
    return this;
  }

  // Pinta sin cambiar la forma: lo que cae dentro de `form` toma el color.
  paint(form, { color, soft = 0.008, amount = 1 } = {}) {
    this.paints.push({ form, color: toLinear(color), soft, amount });
    return this;
  }

  sdf(x, y, z) {
    let d = 1e9;
    const ops = this.ops;
    for (let i = 0; i < ops.length; i++) {
      const op = ops[i];
      const f = op.form;
      const bd = hypot(x - f.bx, y - f.by, z - f.bz) - f.br;
      if (op.op === ADD) {
        if (bd > d + op.k) continue;
        d = smin(d, f.d(x, y, z), op.k);
      } else {
        if (bd > op.k - d) continue;
        d = smax(d, -f.d(x, y, z), op.k);
      }
    }
    if (this.ground !== null) d = smax(d, this.ground - y, this.groundK);
    return d;
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
      if (abs(d) < 0.0005) break;
      t += d * 0.8;
      p.copy(from).addScaledVector(dir, t);
    }
    return this.project(p, 2);
  }

  // Convierte la escultura en malla.
  //   cell: tamaño de la rejilla (más pequeño, más detalle).
  //   bones: número de huesos (si hace falta peso de huesos).
  //   ao: intensidad de la oclusión; aoReach: hasta dónde mira.
  build({ cell = 0.02, bones = 0, ao = 1, aoReach = 0.18 } = {}) {
    const adds = this.ops.filter((op) => op.op === ADD);
    if (!adds.length) return new THREE.BufferGeometry();

    // Caja de trabajo
    const lo = [Infinity, Infinity, Infinity];
    const hi = [-Infinity, -Infinity, -Infinity];
    for (const op of adds) {
      const f = op.form;
      const r = f.br + op.k;
      lo[0] = min(lo[0], f.bx - r); hi[0] = max(hi[0], f.bx + r);
      lo[1] = min(lo[1], f.by - r); hi[1] = max(hi[1], f.by + r);
      lo[2] = min(lo[2], f.bz - r); hi[2] = max(hi[2], f.bz + r);
    }
    if (this.ground !== null) lo[1] = max(lo[1], this.ground - cell * 2);
    const pad = cell * 2;
    const ox = lo[0] - pad;
    const oy = lo[1] - pad;
    const oz = lo[2] - pad;
    const nx = Math.ceil((hi[0] - lo[0] + pad * 2) / cell) + 1;
    const ny = Math.ceil((hi[1] - lo[1] + pad * 2) / cell) + 1;
    const nz = Math.ceil((hi[2] - lo[2] + pad * 2) / cell) + 1;
    const field = new Float32Array(nx * ny * nz);

    // Campo de distancias. Se mira primero el centro de cada bloque de
    // 4×4×4: si está lejos de la superficie, todo el bloque tiene su signo.
    const B = 4;
    const reach = ((B - 1) / 2) * cell * Math.sqrt(3) * 1.35 + cell;
    for (let bz = 0; bz < nz; bz += B) {
      for (let by = 0; by < ny; by += B) {
        for (let bx = 0; bx < nx; bx += B) {
          const dc = this.sdf(ox + (bx + (B - 1) / 2) * cell, oy + (by + (B - 1) / 2) * cell, oz + (bz + (B - 1) / 2) * cell);
          const far = abs(dc) > reach;
          const ez = min(bz + B, nz);
          const ey = min(by + B, ny);
          const ex = min(bx + B, nx);
          for (let k = bz; k < ez; k++) {
            for (let j = by; j < ey; j++) {
              let idx = bx + nx * (j + ny * k);
              for (let i = bx; i < ex; i++, idx++) {
                field[idx] = far ? dc : this.sdf(ox + i * cell, oy + j * cell, oz + k * cell);
              }
            }
          }
        }
      }
    }

    // Un vértice por celda que cruza la superficie.
    const cx = nx - 1;
    const cy = ny - 1;
    const cz = nz - 1;
    const cellVertex = new Int32Array(cx * cy * cz).fill(-1);
    const positions = [];
    const corner = new Float32Array(8);
    const offsets = [0, 1, nx, nx + 1, nx * ny, nx * ny + 1, nx * ny + nx, nx * ny + nx + 1];
    for (let k = 0; k < cz; k++) {
      for (let j = 0; j < cy; j++) {
        for (let i = 0; i < cx; i++) {
          const base = i + nx * (j + ny * k);
          let mask = 0;
          for (let c = 0; c < 8; c++) {
            corner[c] = field[base + offsets[c]];
            if (corner[c] < 0) mask |= 1 << c;
          }
          if (mask === 0 || mask === 255) continue;
          let sx = 0;
          let sy = 0;
          let sz = 0;
          let count = 0;
          for (const [a, b] of EDGES) {
            const da = corner[a];
            const db = corner[b];
            if ((da < 0) === (db < 0)) continue;
            const t = da / (da - db);
            sx += (a & 1) + (((b & 1) - (a & 1)) * t);
            sy += ((a >> 1) & 1) + ((((b >> 1) & 1) - ((a >> 1) & 1)) * t);
            sz += ((a >> 2) & 1) + ((((b >> 2) & 1) - ((a >> 2) & 1)) * t);
            count++;
          }
          cellVertex[i + cx * (j + cy * k)] = positions.length / 3;
          positions.push(ox + (i + sx / count) * cell, oy + (j + sy / count) * cell, oz + (k + sz / count) * cell);
        }
      }
    }

    // Caras: cada arista de la rejilla que cruza la superficie une las
    // cuatro celdas que la rodean.
    const indices = [];
    const cellAt = (i, j, k) => cellVertex[i + cx * (j + cy * k)];
    const quad = (a, b, c, d, flip) => {
      if (a < 0 || b < 0 || c < 0 || d < 0) return;
      if (flip) indices.push(a, c, b, a, d, c);
      else indices.push(a, b, c, a, c, d);
    };
    for (let k = 0; k < nz; k++) {
      for (let j = 0; j < ny; j++) {
        for (let i = 0; i < nx; i++) {
          const idx = i + nx * (j + ny * k);
          const inside = field[idx] < 0;
          if (i < cx && j > 0 && k > 0 && j < cy && k < cz && inside !== (field[idx + 1] < 0)) {
            quad(cellAt(i, j - 1, k - 1), cellAt(i, j, k - 1), cellAt(i, j, k), cellAt(i, j - 1, k), !inside);
          }
          if (j < cy && i > 0 && k > 0 && i < cx && k < cz && inside !== (field[idx + nx] < 0)) {
            quad(cellAt(i - 1, j, k - 1), cellAt(i - 1, j, k), cellAt(i, j, k), cellAt(i, j, k - 1), !inside);
          }
          if (k < cz && i > 0 && j > 0 && i < cx && j < cy && inside !== (field[idx + nx * ny] < 0)) {
            quad(cellAt(i - 1, j - 1, k), cellAt(i, j - 1, k), cellAt(i, j, k), cellAt(i - 1, j, k), !inside);
          }
        }
      }
    }

    // Pegar cada vértice a la superficie y sacar su normal.
    const count = positions.length / 3;
    const pos = new Float32Array(positions);
    const nor = new Float32Array(count * 3);
    const h = cell * 0.3;
    const T = [[1, -1, -1], [-1, -1, 1], [-1, 1, -1], [1, 1, 1]];
    for (let v = 0; v < count; v++) {
      let x = pos[v * 3];
      let y = pos[v * 3 + 1];
      let z = pos[v * 3 + 2];
      let gx = 0;
      let gy = 0;
      let gz = 0;
      for (let it = 0; it < 2; it++) {
        let dsum = 0;
        gx = gy = gz = 0;
        for (const [a, b, c] of T) {
          const f = this.sdf(x + a * h, y + b * h, z + c * h);
          dsum += f;
          gx += a * f;
          gy += b * f;
          gz += c * f;
        }
        const d = dsum / 4;
        const gl = hypot(gx, gy, gz);
        if (gl < 1e-12) break;
        gx /= gl;
        gy /= gl;
        gz /= gl;
        if (it === 1) break;
        const step = Math.max(-cell, Math.min(cell, d));
        x -= gx * step;
        y -= gy * step;
        z -= gz * step;
      }
      pos[v * 3] = x;
      pos[v * 3 + 1] = y;
      pos[v * 3 + 2] = z;
      nor[v * 3] = gx;
      nor[v * 3 + 1] = gy;
      nor[v * 3 + 2] = gz;
    }

    // Color, huesos y oclusión.
    const col = new Float32Array(count * 3);
    const aoAttr = new Float32Array(count);
    const skinIndex = bones ? new Uint16Array(count * 4) : null;
    const skinWeight = bones ? new Float32Array(count * 4) : null;
    const dist = new Float32Array(adds.length);
    const boneW = new Float32Array(Math.max(1, bones));
    for (let v = 0; v < count; v++) {
      const x = pos[v * 3];
      const y = pos[v * 3 + 1];
      const z = pos[v * 3 + 2];
      let dmin = Infinity;
      for (let i = 0; i < adds.length; i++) {
        dist[i] = max(adds[i].form.d(x, y, z), 0);
        if (dist[i] < dmin) dmin = dist[i];
      }
      let r = 0;
      let g = 0;
      let b = 0;
      let wsum = 0;
      if (bones) boneW.fill(0);
      for (let i = 0; i < adds.length; i++) {
        const op = adds[i];
        const gap = dist[i] - dmin;
        const wc = max(0, 1 - gap / op.colorK);
        if (wc > 0) {
          const w = wc * wc;
          r += op.color[0] * w;
          g += op.color[1] * w;
          b += op.color[2] * w;
          wsum += w;
        }
        if (bones) {
          const wb = max(0, 1 - gap / (op.k + 0.015));
          if (wb > 0) {
            const w = wb * wb;
            if (op.split) {
              const { bone, from, to } = op.split;
              const t = Math.min(1, Math.max(0, (y - from) / (to - from)));
              const s = t * t * (3 - 2 * t);
              boneW[op.bone] += w * (1 - s);
              boneW[bone] += w * s;
            } else {
              boneW[op.bone] += w;
            }
          }
        }
      }
      r /= wsum;
      g /= wsum;
      b /= wsum;
      for (const paint of this.paints) {
        const pd = paint.form.d(x, y, z);
        if (pd > paint.soft) continue;
        const t0 = Math.min(1, Math.max(0, 0.5 - pd / (2 * paint.soft)));
        const t = t0 * t0 * (3 - 2 * t0) * paint.amount;
        r += (paint.color[0] - r) * t;
        g += (paint.color[1] - g) * t;
        b += (paint.color[2] - b) * t;
      }
      col[v * 3] = r;
      col[v * 3 + 1] = g;
      col[v * 3 + 2] = b;

      if (bones) {
        // Los cuatro huesos que más pesan.
        for (let slot = 0; slot < 4; slot++) {
          let best = -1;
          let bestW = 0;
          for (let bi = 0; bi < bones; bi++) {
            if (boneW[bi] > bestW) {
              best = bi;
              bestW = boneW[bi];
            }
          }
          if (best < 0) break;
          skinIndex[v * 4 + slot] = best;
          skinWeight[v * 4 + slot] = bestW;
          boneW[best] = 0;
        }
        const total = skinWeight[v * 4] + skinWeight[v * 4 + 1] + skinWeight[v * 4 + 2] + skinWeight[v * 4 + 3];
        if (total > 0) for (let s = 0; s < 4; s++) skinWeight[v * 4 + s] /= total;
        else skinWeight[v * 4] = 1;
      }

      // Oclusión: cuánto se mete la forma (y el suelo) en el camino de la normal.
      if (ao > 0) {
        const nx0 = nor[v * 3];
        const ny0 = nor[v * 3 + 1];
        const nz0 = nor[v * 3 + 2];
        let occ = 0;
        let sca = 1;
        for (let i = 1; i <= 4; i++) {
          const hr = aoReach * (0.08 + 0.92 * (i / 4) ** 1.6);
          const px = x + nx0 * hr;
          const py = y + ny0 * hr;
          const pz = z + nz0 * hr;
          let d = this.sdf(px, py, pz);
          if (this.ground !== null) d = min(d, py - this.ground);
          occ += max(0, hr - d) * sca;
          sca *= 0.68;
        }
        aoAttr[v] = Math.max(0, 1 - (occ / aoReach) * 1.05 * ao);
      } else {
        aoAttr[v] = 1;
      }
    }

    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    geometry.setAttribute('normal', new THREE.BufferAttribute(nor, 3));
    geometry.setAttribute('color', new THREE.BufferAttribute(col, 3));
    geometry.setAttribute('ao', new THREE.BufferAttribute(aoAttr, 1));
    if (bones) {
      geometry.setAttribute('skinIndex', new THREE.BufferAttribute(skinIndex, 4));
      geometry.setAttribute('skinWeight', new THREE.BufferAttribute(skinWeight, 4));
    }
    geometry.setIndex(count > 65535 ? new THREE.BufferAttribute(new Uint32Array(indices), 1) : new THREE.BufferAttribute(new Uint16Array(indices), 1));
    geometry.computeBoundingSphere();
    geometry.computeBoundingBox();
    return geometry;
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
