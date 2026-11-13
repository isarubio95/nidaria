// La habitación fija: pared con papel, friso de madera, suelo de tarima,
// la ventana (con hueco de verdad, cortinas y el sol que entra), una
// estantería, un puf y un cuadro. Lo que cambia con la decoración (papel,
// alfombra, muebles) lo pone room.js.

import * as THREE from 'three';
import { RoundedBoxGeometry } from 'three/addons/geometries/RoundedBoxGeometry.js';
import { createRng } from '/shared/rng.js';
import { Sculpt, shape } from './sdf.js';
import { clay, plain, canvasTexture, contactShadow } from './materials.js';

export const WALL_Z = -2.4;
export const WAINSCOT = 0.62;
// Ventana: centro en x, altura del alféizar, ancho del hueco y tramo recto.
export const WINDOW = { x: -0.68, y: 0.98, width: 0.86, straight: 0.62, depth: 0.14 };

const W = 60;
const H = 24;

function archShape(width, straight, cx = 0, cy = 0) {
  const r = width / 2;
  const s = new THREE.Shape();
  s.moveTo(cx - r, cy);
  s.lineTo(cx - r, cy + straight);
  s.absarc(cx, cy + straight, r, Math.PI, 0, true);
  s.lineTo(cx + r, cy);
  s.closePath();
  return s;
}

function archPath(width, straight, cx = 0, cy = 0) {
  const r = width / 2;
  const p = new THREE.Path();
  p.moveTo(cx - r, cy);
  p.lineTo(cx - r, cy + straight);
  p.absarc(cx, cy + straight, r, Math.PI, 0, true);
  p.lineTo(cx + r, cy);
  p.closePath();
  return p;
}

const rounded = (w, h, d, r, color, finish = 'matte') => {
  const mesh = new THREE.Mesh(new RoundedBoxGeometry(w, h, d, 3, r), plain(color, finish));
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  return mesh;
};

// ── Texturas ──

function woodFloor(anisotropy) {
  const size = 1024;
  const boards = 8;
  const bw = size / boards;
  const rng = createRng(90210);
  const texture = canvasTexture(size, size, (ctx) => {
    for (let b = 0; b < boards; b++) {
      const x = b * bw;
      let y = -rng.range(0, 700);
      while (y < size) {
        const len = rng.range(420, 900);
        const l = rng.range(83, 88);
        const hue = rng.range(30, 36);
        const sat = rng.range(52, 64);
        const draw = (oy) => {
          ctx.fillStyle = `hsl(${hue} ${sat}% ${l}%)`;
          ctx.fillRect(x, y + oy, bw, len);
          // Vetas: líneas finas que ondulan a lo largo de la tabla.
          ctx.save();
          ctx.beginPath();
          ctx.rect(x, y + oy, bw, len);
          ctx.clip();
          for (let g = 0; g < 9; g++) {
            const gx = x + rng.range(6, bw - 6);
            ctx.strokeStyle = `hsla(${hue - 4} ${sat + 6}% ${l - rng.range(8, 16)}% / ${rng.range(0.12, 0.28)})`;
            ctx.lineWidth = rng.range(0.8, 2.2);
            ctx.beginPath();
            const amp = rng.range(1, 5);
            const freq = rng.range(0.004, 0.012);
            const phase = rng.range(0, 6);
            for (let t = 0; t <= len; t += 12) ctx.lineTo(gx + Math.sin(t * freq + phase) * amp, y + oy + t);
            ctx.stroke();
          }
          // Un brillo suave hacia el centro de la tabla.
          const shine = ctx.createLinearGradient(x, 0, x + bw, 0);
          shine.addColorStop(0, 'rgba(120,70,40,0.07)');
          shine.addColorStop(0.5, 'rgba(255,250,240,0.06)');
          shine.addColorStop(1, 'rgba(120,70,40,0.08)');
          ctx.fillStyle = shine;
          ctx.fillRect(x, y + oy, bw, len);
          ctx.restore();
          // Junta del final de la tabla.
          ctx.fillStyle = 'rgba(110,70,45,0.45)';
          ctx.fillRect(x, y + oy + len - 2, bw, 2);
        };
        draw(0);
        if (y + len > size) draw(-size);
        if (y < 0) draw(size);
        y += len;
      }
      // Junta entre tablas, con un filo de luz al lado.
      ctx.fillStyle = 'rgba(105,65,40,0.5)';
      ctx.fillRect(x, 0, 2.5, size);
      ctx.fillStyle = 'rgba(255,248,236,0.35)';
      ctx.fillRect(x + 2.5, 0, 1.5, size);
    }
  }, { repeat: true });
  texture.anisotropy = anisotropy;
  return texture;
}

function beadboard(anisotropy) {
  const texture = canvasTexture(128, 256, (ctx, w, h) => {
    ctx.fillStyle = '#fbf2e4';
    ctx.fillRect(0, 0, w, h);
    const g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, 'rgba(110,70,40,0.16)');
    g.addColorStop(0.08, 'rgba(110,70,40,0)');
    g.addColorStop(0.85, 'rgba(110,70,40,0)');
    g.addColorStop(1, 'rgba(110,70,40,0.18)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
    // Ranura de la tabla: sombra a un lado y luz al otro.
    ctx.fillStyle = 'rgba(150,110,80,0.35)';
    ctx.fillRect(0, 0, 4, h);
    ctx.fillStyle = 'rgba(255,255,255,0.7)';
    ctx.fillRect(4, 0, 3, h);
    ctx.fillStyle = 'rgba(150,110,80,0.12)';
    ctx.fillRect(w - 6, 0, 6, h);
  });
  texture.wrapS = THREE.RepeatWrapping;
  texture.anisotropy = anisotropy;
  return texture;
}

// Mancha de luz de la ventana: el arco con la cruz de los palillos y los
// bordes difuminados.
function sunPatchTexture() {
  return canvasTexture(256, 320, (ctx, w, h) => {
    ctx.filter = 'blur(14px)';
    ctx.fillStyle = '#ffffff';
    const r = w * 0.36;
    const cx = w / 2;
    const base = h * 0.92;
    const straight = h * 0.42;
    ctx.beginPath();
    ctx.moveTo(cx - r, base);
    ctx.lineTo(cx - r, base - straight);
    ctx.arc(cx, base - straight, r, Math.PI, 0);
    ctx.lineTo(cx + r, base);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = '#000000';
    ctx.globalCompositeOperation = 'destination-out';
    ctx.fillRect(cx - 5, base - straight - r, 10, straight + r);
    ctx.fillRect(cx - r, base - straight - 5, r * 2, 10);
  });
}

// Haz de luz: más denso arriba, en la ventana, y se apaga hacia el suelo.
function beamTexture() {
  return canvasTexture(64, 256, (ctx, w, h) => {
    const g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, 'rgba(255,255,255,0.9)');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
    const side = ctx.createLinearGradient(0, 0, w, 0);
    side.addColorStop(0, 'rgba(0,0,0,1)');
    side.addColorStop(0.25, 'rgba(0,0,0,0)');
    side.addColorStop(0.75, 'rgba(0,0,0,0)');
    side.addColorStop(1, 'rgba(0,0,0,1)');
    ctx.globalCompositeOperation = 'destination-out';
    ctx.fillStyle = side;
    ctx.fillRect(0, 0, w, h);
  });
}

function glassGlint() {
  return canvasTexture(128, 160, (ctx, w, h) => {
    ctx.filter = 'blur(3px)';
    ctx.fillStyle = 'rgba(255,255,255,0.9)';
    ctx.beginPath();
    ctx.moveTo(w * 0.15, h * 0.75);
    ctx.lineTo(w * 0.35, h * 0.75);
    ctx.lineTo(w * 0.75, h * 0.2);
    ctx.lineTo(w * 0.62, h * 0.2);
    ctx.closePath();
    ctx.fill();
    ctx.fillStyle = 'rgba(255,255,255,0.5)';
    ctx.beginPath();
    ctx.moveTo(w * 0.42, h * 0.75);
    ctx.lineTo(w * 0.48, h * 0.75);
    ctx.lineTo(w * 0.86, h * 0.24);
    ctx.lineTo(w * 0.8, h * 0.24);
    ctx.closePath();
    ctx.fill();
  });
}

function cornerShade() {
  return canvasTexture(8, 128, (ctx, w, h) => {
    const g = ctx.createLinearGradient(0, 0, 0, h);
    g.addColorStop(0, 'rgba(80,45,25,0.42)');
    g.addColorStop(0.35, 'rgba(80,45,25,0.14)');
    g.addColorStop(1, 'rgba(80,45,25,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
  });
}

function curtainTexture() {
  const texture = canvasTexture(128, 512, (ctx, w, h) => {
    ctx.fillStyle = '#fff6ee';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = 'rgba(255,170,190,0.22)';
    for (let y = 18; y < h - 60; y += 36) {
      for (let x = (y / 36) % 2 ? 16 : 48; x < w; x += 64) {
        ctx.beginPath();
        ctx.arc(x, y, 5, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    // Ribete rosa abajo.
    ctx.fillStyle = '#ffb3c6';
    ctx.fillRect(0, h - 40, w, 26);
    ctx.fillStyle = '#ffd3de';
    ctx.fillRect(0, h - 14, w, 14);
    // Trama de la tela.
    ctx.fillStyle = 'rgba(120,80,60,0.035)';
    for (let x = 0; x < w; x += 3) ctx.fillRect(x, 0, 1, h);
    for (let y = 0; y < h; y += 3) ctx.fillRect(0, y, w, 1);
  });
  texture.anisotropy = 4;
  return texture;
}

function paintingTexture() {
  return canvasTexture(256, 200, (ctx, w, h) => {
    const sky = ctx.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, '#bfe3ff');
    sky.addColorStop(1, '#fff1d6');
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#ffd166';
    ctx.beginPath();
    ctx.arc(w * 0.72, h * 0.34, 26, 0, Math.PI * 2);
    ctx.fill();
    const hill = (y, color, amp, phase) => {
      ctx.fillStyle = color;
      ctx.beginPath();
      ctx.moveTo(0, h);
      for (let x = 0; x <= w; x += 8) ctx.lineTo(x, y + Math.sin(x * 0.02 + phase) * amp);
      ctx.lineTo(w, h);
      ctx.closePath();
      ctx.fill();
    };
    hill(h * 0.62, '#9ad9b3', 14, 1);
    hill(h * 0.75, '#7ec99c', 10, 3);
    ctx.fillStyle = '#ffffff';
    for (const [x, y, s] of [[60, 50, 1], [150, 70, 0.8]]) {
      ctx.beginPath();
      ctx.arc(x, y, 12 * s, 0, Math.PI * 2);
      ctx.arc(x + 14 * s, y - 6 * s, 14 * s, 0, Math.PI * 2);
      ctx.arc(x + 30 * s, y, 11 * s, 0, Math.PI * 2);
      ctx.fill();
    }
    // Pincelada: un poco de grano.
    const rng = createRng(7);
    for (let i = 0; i < 1600; i++) {
      ctx.fillStyle = rng.chance(0.5) ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.035)';
      ctx.fillRect(rng.range(0, w), rng.range(0, h), 2, 1);
    }
  });
}

// ── Piezas ──

// Una cortina: un paño con pliegues que se recoge con un alzapaño.
function curtain(width, height, side, material) {
  const geometry = new THREE.PlaneGeometry(width, height, 40, 48);
  const pos = geometry.attributes.position;
  const outer = side * width / 2;
  const tieY = -height / 2 + height * 0.36;
  for (let i = 0; i < pos.count; i++) {
    const x = pos.getX(i);
    const y = pos.getY(i);
    const u = (x + width / 2) / width;
    // Fruncido: el alzapaño junta la tela hacia el borde de fuera.
    const pinch = 1 - 0.62 * Math.exp(-(((y - tieY) / (height * 0.2)) ** 2));
    const below = y < tieY ? 1 + 0.25 * Math.min(1, (tieY - y) / (height * 0.3)) : 1;
    const nx = outer + (x - outer) * pinch * below;
    const folds = Math.sin(u * Math.PI * 9) * (0.022 + 0.012 * (1 - pinch)) + Math.sin(u * Math.PI * 4 + 1) * 0.008;
    pos.setXYZ(i, nx, y, folds + 0.03);
  }
  geometry.computeVertexNormals();
  const mesh = new THREE.Mesh(geometry, material);
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  const tie = new THREE.Mesh(new THREE.TorusGeometry(width * 0.2, 0.018, 10, 28), plain('#ff9fb8', 'fabric'));
  tie.scale.set(1, 0.42, 1);
  tie.rotation.x = Math.PI / 2;
  tie.position.set(outer - side * width * 0.2, tieY, 0.04);
  tie.castShadow = true;
  const group = new THREE.Group();
  group.add(mesh, tie);
  return group;
}

function buildWindow({ sky }) {
  const { x, y, width, straight, depth } = WINDOW;
  const win = new THREE.Group();
  win.position.set(x, y, WALL_Z);
  const frameColor = '#fffaf2';

  // Cielo, al fondo del hueco.
  const paneGeo = new THREE.ShapeGeometry(archShape(width, straight), 24);
  const uv = paneGeo.attributes.uv;
  const pp = paneGeo.attributes.position;
  for (let i = 0; i < pp.count; i++) uv.setXY(i, (pp.getX(i) + width / 2) / width, pp.getY(i) / (straight + width / 2));
  const pane = new THREE.Mesh(paneGeo, new THREE.MeshBasicMaterial({ map: sky, toneMapped: false }));
  pane.position.z = -depth;

  // Mocheta: el grosor del muro alrededor del hueco.
  const reveal = new THREE.Mesh(
    new THREE.ExtrudeGeometry((() => {
      const s = archShape(width + 0.04, straight);
      s.holes.push(archPath(width, straight));
      return s;
    })(), { depth, bevelEnabled: false, curveSegments: 24 }),
    plain('#f7eedf', 'matte'),
  );
  reveal.position.z = -depth;
  reveal.receiveShadow = true;

  // Marco de la hoja y los palillos, metidos en el hueco.
  const sashShape = archShape(width, straight);
  sashShape.holes.push(archPath(width - 0.07, straight - 0.0, 0, 0.035));
  const sash = new THREE.Mesh(new THREE.ExtrudeGeometry(sashShape, { depth: 0.03, bevelEnabled: true, bevelSize: 0.008, bevelThickness: 0.008, bevelSegments: 3, curveSegments: 24 }), plain(frameColor, 'gloss'));
  sash.position.z = -depth * 0.55;
  sash.castShadow = true;
  const barV = rounded(0.03, straight + width / 2, 0.03, 0.012, frameColor, 'gloss');
  barV.position.set(0, (straight + width / 2) / 2, -depth * 0.5);
  const barH = rounded(width - 0.04, 0.03, 0.03, 0.012, frameColor, 'gloss');
  barH.position.set(0, straight, -depth * 0.5);

  // Brillo del cristal.
  const glint = new THREE.Mesh(new THREE.PlaneGeometry(width * 0.9, straight + width * 0.4), new THREE.MeshBasicMaterial({ map: glassGlint(), transparent: true, opacity: 0.14, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false }));
  glint.position.set(0, (straight + width / 2) / 2, -depth * 0.6);

  // Moldura sobre la pared y alféizar.
  const casingShape = archShape(width + 0.2, straight);
  casingShape.holes.push(archPath(width, straight));
  const casing = new THREE.Mesh(new THREE.ExtrudeGeometry(casingShape, { depth: 0.025, bevelEnabled: true, bevelSize: 0.012, bevelThickness: 0.014, bevelSegments: 4, curveSegments: 28 }), plain(frameColor, 'gloss'));
  casing.castShadow = true;
  casing.receiveShadow = true;
  const sill = rounded(width + 0.36, 0.055, 0.2, 0.02, frameColor, 'gloss');
  sill.position.set(0, -0.02, 0.04);
  const apron = rounded(width + 0.2, 0.08, 0.03, 0.012, frameColor, 'gloss');
  apron.position.set(0, -0.085, 0.015);

  // Barra y cortinas.
  const top = y + straight + width / 2;
  const rodY = top - y + 0.17;
  const rod = new THREE.Mesh(new THREE.CylinderGeometry(0.014, 0.014, width + 0.62, 16), plain('#e2b279', 'gloss'));
  rod.rotation.z = Math.PI / 2;
  rod.position.set(0, rodY, 0.09);
  rod.castShadow = true;
  const finials = [-1, 1].map((side) => {
    const ball = new THREE.Mesh(new THREE.SphereGeometry(0.032, 20, 14), plain('#e2b279', 'gloss'));
    ball.position.set(side * (width / 2 + 0.33), rodY, 0.09);
    ball.castShadow = true;
    return ball;
  });
  const fabric = new THREE.MeshPhysicalMaterial({
    map: curtainTexture(),
    side: THREE.DoubleSide,
    roughness: 0.9,
    sheen: 0.8,
    sheenRoughness: 0.5,
    sheenColor: new THREE.Color('#ffffff').multiplyScalar(0.35),
  });
  const curtainH = rodY + 0.14;
  const curtains = [-1, 1].map((side) => {
    const c = curtain(0.4, curtainH, side, fabric);
    c.position.set(side * (width / 2 + 0.13), rodY - curtainH / 2 + 0.01, 0.07);
    return c;
  });

  win.add(pane, reveal, sash, barV, barH, glint, casing, sill, apron, rod, ...finials, ...curtains);
  return win;
}

function buildShelf() {
  const shelf = new THREE.Group();
  shelf.add(rounded(1.0, 0.045, 0.24, 0.014, '#e8bf8c', 'wood'));
  for (const side of [-1, 1]) {
    const bracket = rounded(0.03, 0.16, 0.2, 0.01, '#d9a874', 'wood');
    bracket.position.set(side * 0.36, -0.1, -0.01);
    shelf.add(bracket);
  }
  const books = [['#ff8fb1', 0.26, 0.07], ['#9db7ff', 0.3, 0.065], ['#ffd166', 0.22, 0.06], ['#7ed6a5', 0.27, 0.07]];
  let bx = -0.42;
  books.forEach(([color, tall, thick], i) => {
    const book = rounded(thick, tall, 0.17, 0.008, color, 'matte');
    const tilt = i === 3 ? 0.3 : 0;
    book.position.set(bx + thick / 2 + (i === 3 ? 0.05 : 0), 0.0225 + tall / 2 - (i === 3 ? 0.02 : 0), 0);
    book.rotation.z = -tilt;
    const band = rounded(thick + 0.004, 0.022, 0.172, 0.004, '#fffaf2', 'matte');
    band.position.y = tall * 0.28;
    book.add(band);
    shelf.add(book);
    bx += thick + 0.006;
  });
  // Maceta con una suculenta esculpida.
  const pot = new Sculpt();
  pot.add(shape.cylinder([0, 0.07, 0], 0.075, 0.07, 0.02), { color: '#ffffff', k: 0 });
  pot.add(shape.torus([0, 0.135, 0], 0.075, 0.016), { color: '#ffd166', k: 0.01 });
  pot.sub(shape.cylinder([0, 0.16, 0], 0.062, 0.04, 0.01), { k: 0.01 });
  pot.add(shape.cylinder([0, 0.125, 0], 0.062, 0.006, 0.004), { color: '#8a5a3c', k: 0 });
  for (let i = 0; i < 7; i++) {
    const a = (i / 7) * Math.PI * 2;
    const tilt = i === 0 ? 0 : 0.75;
    const dir = new THREE.Vector3(Math.sin(a) * Math.sin(tilt), Math.cos(tilt), Math.cos(a) * Math.sin(tilt));
    const base = new THREE.Vector3(0, 0.13, 0);
    pot.add(shape.cone(base.toArray(), base.clone().addScaledVector(dir, i === 0 ? 0.09 : 0.1).toArray(), 0.03, 0.012), { color: i % 2 ? '#7fcf9a' : '#5fb985', k: 0.015 });
  }
  const plant = new THREE.Mesh(pot.build({ cell: 0.006, aoReach: 0.05 }), clay({ finish: 'matte' }));
  plant.castShadow = true;
  plant.position.set(0.28, 0.0225, 0);
  shelf.add(plant);
  const ball = new THREE.Mesh(new THREE.SphereGeometry(0.06, 24, 16), plain('#ff7a7a', 'glaze'));
  ball.position.set(0.04, 0.0825, 0.02);
  ball.castShadow = true;
  shelf.add(ball);
  shelf.traverse((node) => { node.castShadow = true; node.receiveShadow = true; });
  return shelf;
}

function buildPouf() {
  const S = new Sculpt({ ground: 0, groundK: 0.03 });
  S.add(shape.cylinder([0, 0.2, 0], 0.4, 0.2, 0.16), { color: '#86dbaa', k: 0 });
  S.add(shape.torus([0, 0.205, 0], 0.39, 0.03, null, 1), { color: '#6cc794', k: 0.02 });
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2;
    S.sub(shape.sphere([Math.cos(a) * 0.2, 0.43, Math.sin(a) * 0.2], 0.035), { k: 0.06 });
  }
  S.sub(shape.sphere([0, 0.44, 0], 0.04), { k: 0.06 });
  for (let i = 0; i < 6; i++) {
    const a = (i / 6) * Math.PI * 2;
    S.add(shape.sphere([Math.cos(a) * 0.2, 0.385, Math.sin(a) * 0.2], 0.022), { color: '#4fae7c', k: 0.01 });
  }
  S.add(shape.sphere([0, 0.39, 0], 0.024), { color: '#4fae7c', k: 0.01 });
  const mesh = new THREE.Mesh(S.build({ cell: 0.014, aoReach: 0.12 }), clay({ finish: 'plush' }));
  mesh.castShadow = true;
  mesh.receiveShadow = true;
  const group = new THREE.Group();
  group.add(contactShadow(1.05, 1.05, 0.9), mesh);
  return group;
}

function buildPainting() {
  const group = new THREE.Group();
  const frameShape = new THREE.Shape();
  frameShape.moveTo(-0.24, -0.19);
  frameShape.lineTo(0.24, -0.19);
  frameShape.lineTo(0.24, 0.19);
  frameShape.lineTo(-0.24, 0.19);
  frameShape.closePath();
  const hole = new THREE.Path();
  hole.moveTo(-0.19, -0.14);
  hole.lineTo(0.19, -0.14);
  hole.lineTo(0.19, 0.14);
  hole.lineTo(-0.19, 0.14);
  hole.closePath();
  frameShape.holes.push(hole);
  const frame = new THREE.Mesh(new THREE.ExtrudeGeometry(frameShape, { depth: 0.02, bevelEnabled: true, bevelSize: 0.012, bevelThickness: 0.012, bevelSegments: 3 }), plain('#e8bf8c', 'wood'));
  frame.castShadow = true;
  const art = new THREE.Mesh(new THREE.PlaneGeometry(0.4, 0.3), new THREE.MeshStandardMaterial({ map: paintingTexture(), roughness: 0.8 }));
  art.position.z = 0.012;
  group.add(frame, art);
  return group;
}

// ── La habitación ──

export function buildHouse({ anisotropy = 4, sky }) {
  const group = new THREE.Group();

  // Pared con papel (con el hueco de la ventana) y friso de madera.
  const wallShape = new THREE.Shape();
  wallShape.moveTo(-W / 2, WAINSCOT);
  wallShape.lineTo(W / 2, WAINSCOT);
  wallShape.lineTo(W / 2, H);
  wallShape.lineTo(-W / 2, H);
  wallShape.closePath();
  wallShape.holes.push(archPath(WINDOW.width, WINDOW.straight, WINDOW.x, WINDOW.y));
  const wallGeo = new THREE.ShapeGeometry(wallShape, 24);
  const wallMat = new THREE.MeshStandardMaterial({ roughness: 0.9, color: '#ffffff' });
  const wall = new THREE.Mesh(wallGeo, wallMat);
  wall.position.z = WALL_Z;
  wall.receiveShadow = true;

  const wainscotMap = beadboard(anisotropy);
  wainscotMap.repeat.set(W / 0.12, 1);
  const trimMat = new THREE.MeshStandardMaterial({ map: wainscotMap, roughness: 0.62, color: '#ffffff' });
  const wainscot = new THREE.Mesh(new THREE.PlaneGeometry(W, WAINSCOT), trimMat);
  wainscot.position.set(0, WAINSCOT / 2, WALL_Z + 0.012);
  wainscot.receiveShadow = true;
  const rail = rounded(W, 0.05, 0.05, 0.018, '#fffaf2', 'gloss');
  rail.position.set(0, WAINSCOT, WALL_Z + 0.025);
  const baseboard = rounded(W, 0.12, 0.04, 0.012, '#fffaf2', 'gloss');
  baseboard.position.set(0, 0.06, WALL_Z + 0.03);

  // Suelo de tarima.
  const floorMap = woodFloor(anisotropy);
  floorMap.repeat.set(W / 1.6, 40 / 1.6);
  const floorMat = new THREE.MeshStandardMaterial({ map: floorMap, roughness: 0.5, color: '#ffffff', bumpMap: floorMap, bumpScale: 0.35 });
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(W, 40), floorMat);
  floor.rotation.x = -Math.PI / 2;
  floor.position.z = WALL_Z + 20;
  floor.receiveShadow = true;

  // Sombra del rincón entre suelo y pared.
  const corner = new THREE.Mesh(new THREE.PlaneGeometry(W, 0.9), new THREE.MeshBasicMaterial({ map: cornerShade(), transparent: true, depthWrite: false, toneMapped: false }));
  corner.rotation.x = -Math.PI / 2;
  corner.position.set(0, 0.003, WALL_Z + 0.45);

  const win = buildWindow({ sky });

  // Luz que entra por la ventana: una mancha en el suelo y un haz.
  const patchGeo = new THREE.PlaneGeometry(1.25, 1.5, 1, 8);
  {
    const pos = patchGeo.attributes.position;
    for (let i = 0; i < pos.count; i++) {
      const py = pos.getY(i);
      // Se estira hacia la cámara y se tuerce a la derecha, como el sol de la tarde.
      pos.setXYZ(i, pos.getX(i) + (0.55 - py) * 0.3, 0, -py);
    }
  }
  const patchMat = new THREE.MeshBasicMaterial({ map: sunPatchTexture(), color: '#fff2d2', transparent: true, opacity: 0.2, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false });
  const patch = new THREE.Mesh(patchGeo, patchMat);
  patch.position.set(WINDOW.x - 0.1, 0.006, WALL_Z + 0.85);
  patch.renderOrder = 1;

  const beamGeo = new THREE.BufferGeometry();
  {
    const top = WINDOW.y + WINDOW.straight * 0.9;
    const pts = [
      [WINDOW.x - 0.36, top, WALL_Z + 0.02], [WINDOW.x + 0.36, top, WALL_Z + 0.02],
      [WINDOW.x + 0.62, 0.02, WALL_Z + 1.35], [WINDOW.x - 0.08, 0.02, WALL_Z + 1.35],
    ];
    beamGeo.setAttribute('position', new THREE.Float32BufferAttribute(pts.flat(), 3));
    beamGeo.setAttribute('uv', new THREE.Float32BufferAttribute([0, 1, 1, 1, 1, 0, 0, 0], 2));
    beamGeo.setIndex([0, 3, 1, 1, 3, 2]);
  }
  const beamMat = new THREE.MeshBasicMaterial({ map: beamTexture(), color: '#fff4dc', transparent: true, opacity: 0.12, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, toneMapped: false });
  const beam = new THREE.Mesh(beamGeo, beamMat);
  beam.renderOrder = 3;

  const shelf = buildShelf();
  shelf.position.set(1.9, 1.5, WALL_Z + 0.13);
  const pouf = buildPouf();
  pouf.position.set(-1.75, 0, -1.25);
  const painting = buildPainting();
  painting.position.set(1.02, 1.36, WALL_Z + 0.02);

  group.add(wall, wainscot, rail, baseboard, floor, corner, win, patch, beam, shelf, pouf, painting);
  return { group, wallMat, floorMat, trimMat, patchMat, beamMat, wall };
}
