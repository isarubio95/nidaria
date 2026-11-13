// La habitación: escena 3D, cámara, luces, la criatura o los huevos que
// hay sobre la alfombra, las partículas y las secuencias (comer, baño,
// eclosión, evolución). La interfaz solo habla con el objeto que
// devuelve `createRoom`.

import * as THREE from 'three';
import { gsap } from 'gsap';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { createGenes } from '/shared/dna.js';
import { FORM_LIST } from '/shared/forms.js';
import { clamp } from '/shared/rng.js';
import { buildCreature, Actor } from './creature.js';
import { buildEgg, buildItem, buildWear } from './props.js';

const FOV = 30;
const FLOOR_Y = 0.03;
const EGG_SCALE = 1.08;

const DAY = {
  wall: new THREE.Color('#ffffff'), floor: new THREE.Color('#ffffff'),
  rug: new THREE.Color('#ff8fb1'), rugLight: new THREE.Color('#ffabc5'),
  trim: new THREE.Color('#fffdf8'), key: new THREE.Color('#fff4e2'), back: new THREE.Color('#bfe3ff'),
};
const NIGHT = {
  wall: new THREE.Color('#5c6ca8'), floor: new THREE.Color('#6d6b99'),
  rug: new THREE.Color('#8d5884'), rugLight: new THREE.Color('#9d6a95'),
  trim: new THREE.Color('#8d93bd'), key: new THREE.Color('#aebfff'), back: new THREE.Color('#47619f'),
};

function canvasTexture(width, height, draw) {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  draw(ctx, width, height);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function archShape(width, straight) {
  const r = width / 2;
  const shape = new THREE.Shape();
  shape.moveTo(-r, 0);
  shape.lineTo(-r, straight);
  shape.absarc(0, straight, r, Math.PI, 0, true);
  shape.lineTo(r, 0);
  shape.closePath();
  return shape;
}

// Cielo de la ventana según la hora del jugador y, si lo ha activado,
// el tiempo que hace donde está. `t` (segundos) mueve la lluvia y la nieve.
const SKY = {
  clear: { night: ['#1c2756', '#44539a'], dawn: ['#86b8ee', '#ffd9b8'], day: ['#62b8f2', '#d4f0ff'], dusk: ['#5f6fc4', '#ffb48c'] },
  grey: { night: ['#232a45', '#3e4668'], dawn: ['#94a3bd', '#d6cbc4'], day: ['#a3b6ca', '#e1e8ef'], dusk: ['#6f7298', '#c8a99f'] },
  storm: { night: ['#191c2e', '#2d3250'], dawn: ['#6c7789', '#a7acb6'], day: ['#75839a', '#b2bcc8'], dusk: ['#55577a', '#8e8193'] },
};
const GREY_SKIES = new Set(['cubierto', 'niebla', 'lluvia', 'nieve', 'tormenta']);
export const MOVING_SKIES = new Set(['lluvia', 'nieve', 'tormenta']);
// Posiciones fijas de gotas y copos: así no tiemblan entre dibujos.
const DROPS = Array.from({ length: 28 }, (_, i) => [Math.abs(Math.sin(i * 12.9898) * 43758.5453) % 1, Math.abs(Math.sin(i * 78.233) * 12543.123) % 1]);

function drawSky(ctx, w, h, date, weather = null, t = 0, still = false) {
  const hour = date.getHours() + date.getMinutes() / 60;
  const phase = hour < 6 || hour >= 21 ? 'night' : hour < 8.5 ? 'dawn' : hour < 18.5 ? 'day' : 'dusk';
  const grey = GREY_SKIES.has(weather);
  const colors = SKY[weather === 'tormenta' ? 'storm' : grey ? 'grey' : 'clear'][phase];
  const gradient = ctx.createLinearGradient(0, 0, 0, h);
  gradient.addColorStop(0, colors[0]);
  gradient.addColorStop(1, colors[1]);
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, w, h);
  const cloud = (x, y, s, color) => {
    ctx.fillStyle = color;
    ctx.beginPath();
    ctx.arc(x, y, 10 * s, 0, Math.PI * 2);
    ctx.arc(x + 12 * s, y - 5 * s, 12 * s, 0, Math.PI * 2);
    ctx.arc(x + 26 * s, y, 10 * s, 0, Math.PI * 2);
    ctx.rect(x, y, 26 * s, 10 * s);
    ctx.fill();
  };
  if (grey) {
    drawWeather(ctx, w, h, phase, weather, t, still, cloud);
    return phase;
  }
  if (phase === 'night') {
    ctx.fillStyle = '#fff8d6';
    for (let i = 0; i < 16; i++) {
      const x = (Math.sin(i * 12.9898) * 43758.5453) % 1;
      const y = (Math.sin(i * 78.233) * 12543.123) % 1;
      ctx.beginPath();
      ctx.arc(Math.abs(x) * w, Math.abs(y) * h * 0.8, 1 + (i % 3) * 0.5, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.beginPath();
    ctx.arc(w * 0.66, h * 0.3, 15, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = '#263468';
    ctx.beginPath();
    ctx.arc(w * 0.66 + 7, h * 0.3 - 4, 13, 0, Math.PI * 2);
    ctx.fill();
  } else if (phase === 'day') {
    const progress = (hour - 8.5) / 10;
    ctx.fillStyle = '#fff3b0';
    ctx.beginPath();
    ctx.arc(w * (0.2 + progress * 0.6), h * (0.3 - Math.sin(progress * Math.PI) * 0.12), 15, 0, Math.PI * 2);
    ctx.fill();
    cloud(w * 0.12, h * 0.56, 1, '#ffffff');
    cloud(w * 0.56, h * 0.74, 0.8, '#ffffff');
    if (weather === 'nubes') {
      cloud(w * 0.42, h * 0.3, 1.1, '#ffffff');
      cloud(w * 0.02, h * 0.18, 0.8, '#f4f8fc');
    }
  } else {
    ctx.fillStyle = phase === 'dawn' ? '#fff0c4' : '#ffe0a6';
    ctx.beginPath();
    ctx.arc(w * (phase === 'dawn' ? 0.3 : 0.7), h * 0.82, 17, 0, Math.PI * 2);
    ctx.fill();
    cloud(w * 0.2, h * 0.5, 0.9, phase === 'dawn' ? '#fff1e6' : '#ffd0c0');
    if (weather === 'nubes') cloud(w * 0.5, h * 0.28, 1, phase === 'dawn' ? '#fff1e6' : '#ffd0c0');
  }
  if (weather === 'nubes' && phase === 'night') {
    cloud(w * 0.1, h * 0.5, 1.1, '#3a4677');
    cloud(w * 0.5, h * 0.42, 0.9, '#3a4677');
  }
  return phase;
}

// Cielo tapado: nubes grises, y encima lluvia, nieve, niebla o tormenta.
function drawWeather(ctx, w, h, phase, weather, t, still, cloud) {
  const dark = phase === 'night';
  const tone = weather === 'tormenta' ? (dark ? '#2b3049' : '#8a93a3') : dark ? '#3c4566' : '#eef2f6';
  const shade = weather === 'tormenta' ? (dark ? '#22263b' : '#7a8394') : dark ? '#323a58' : '#dde3ea';
  cloud(w * -0.08, h * 0.12, 1.3, shade);
  cloud(w * 0.42, h * 0.06, 1.4, shade);
  cloud(w * 0.08, h * 0.3, 1.2, tone);
  cloud(w * 0.55, h * 0.26, 1.1, tone);

  if (weather === 'lluvia' || weather === 'tormenta') {
    ctx.strokeStyle = dark ? 'rgb(200 214 255 / 0.55)' : 'rgb(255 255 255 / 0.75)';
    ctx.lineWidth = 1.6;
    ctx.lineCap = 'round';
    ctx.beginPath();
    for (const [a, b] of DROPS) {
      const x = (a * w + t * 18) % w;
      const y = (b * h + t * h * 1.4) % h;
      ctx.moveTo(x, y);
      ctx.lineTo(x - 3, y + 10);
    }
    ctx.stroke();
  } else if (weather === 'nieve') {
    ctx.fillStyle = '#ffffff';
    DROPS.forEach(([a, b], i) => {
      const y = (b * h + t * 16 * (0.7 + a * 0.6)) % h;
      const x = a * w + Math.sin(t * 0.9 + i) * 4;
      ctx.beginPath();
      ctx.arc(x, y, 1.6 + (i % 3) * 0.6, 0, Math.PI * 2);
      ctx.fill();
    });
  } else if (weather === 'niebla') {
    ctx.fillStyle = dark ? 'rgb(150 160 190 / 0.35)' : 'rgb(255 255 255 / 0.5)';
    for (let i = 0; i < 4; i++) ctx.fillRect(0, h * (0.3 + i * 0.18), w, h * 0.1);
    ctx.fillStyle = dark ? 'rgb(150 160 190 / 0.2)' : 'rgb(255 255 255 / 0.3)';
    ctx.fillRect(0, 0, w, h);
  }

  // Un relámpago suave cada pocos segundos. Sin él si se pide menos movimiento.
  if (weather === 'tormenta' && !still && t % 9 < 0.14) {
    ctx.fillStyle = 'rgb(255 255 255 / 0.45)';
    ctx.fillRect(0, 0, w, h);
  }
}

export function createRoom(canvas) {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFShadowMap;
  renderer.toneMapping = THREE.NoToneMapping;

  const scene = new THREE.Scene();
  scene.background = DAY.back.clone();
  const pmrem = new THREE.PMREMGenerator(renderer);
  scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
  scene.environmentIntensity = 0.5;
  pmrem.dispose();

  const camera = new THREE.PerspectiveCamera(FOV, 1, 0.1, 80);

  // ── Luces ──
  const hemi = new THREE.HemisphereLight('#ffffff', '#ffe6cc', 1.05);
  const key = new THREE.DirectionalLight(DAY.key, 2.1);
  key.position.set(-2.6, 5.2, 4.2);
  key.target.position.set(0, 0.5, 0);
  key.castShadow = true;
  key.shadow.mapSize.set(1024, 1024);
  key.shadow.camera.left = -2.4;
  key.shadow.camera.right = 2.4;
  key.shadow.camera.top = 2.6;
  key.shadow.camera.bottom = -1.6;
  key.shadow.camera.near = 1;
  key.shadow.camera.far = 14;
  key.shadow.bias = -0.0006;
  key.shadow.normalBias = 0.02;
  const rim = new THREE.DirectionalLight('#cfe6ff', 0.7);
  rim.position.set(3.2, 2.6, -2.2);
  scene.add(hemi, key, key.target, rim);

  // ── Habitación ──
  const wallMap = canvasTexture(96, 96, (ctx, w, h) => {
    ctx.fillStyle = '#bfe3ff';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#cdeaff';
    for (const [x, y] of [[w / 2, h / 2], [0, 0], [w, 0], [0, h], [w, h]]) {
      ctx.beginPath();
      ctx.arc(x, y, 7, 0, Math.PI * 2);
      ctx.fill();
    }
  });
  wallMap.wrapS = wallMap.wrapT = THREE.RepeatWrapping;
  wallMap.repeat.set(60 / 0.5, 24 / 0.5);
  wallMap.anisotropy = renderer.capabilities.getMaxAnisotropy();
  const wallMat = new THREE.MeshBasicMaterial({ map: wallMap });
  const wall = new THREE.Mesh(new THREE.PlaneGeometry(60, 24), wallMat);
  wall.position.set(0, 12, -2.4);

  const floorMap = canvasTexture(256, 256, (ctx, w, h) => {
    ctx.fillStyle = '#fff8ec';
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = '#f5ead6';
    for (let x = 0; x < w; x += 64) ctx.fillRect(x, 0, 3, h);
  });
  floorMap.wrapS = floorMap.wrapT = THREE.RepeatWrapping;
  floorMap.repeat.set(60 / 1.4, 40 / 1.4);
  floorMap.anisotropy = renderer.capabilities.getMaxAnisotropy();
  const floorMat = new THREE.MeshBasicMaterial({ map: floorMap });
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(60, 40), floorMat);
  floor.rotation.x = -Math.PI / 2;
  floor.position.z = 17.6;

  const trimMat = new THREE.MeshBasicMaterial({ color: DAY.trim });
  const baseboard = new THREE.Mesh(new THREE.BoxGeometry(60, 0.16, 0.06), trimMat);
  baseboard.position.set(0, 0.08, -2.37);

  const rugMat = new THREE.MeshBasicMaterial({ color: DAY.rug });
  const rugLightMat = new THREE.MeshBasicMaterial({ color: DAY.rugLight });
  const rug = new THREE.Group();
  const disc = new THREE.CylinderGeometry(1, 1, 0.01, 56);
  [[1, rugMat, 0.02], [0.88, rugLightMat, 0.022], [0.78, rugMat, 0.024]].forEach(([scale, material, y]) => {
    const mesh = new THREE.Mesh(disc, material);
    mesh.scale.set(0.84 * scale, 1, 0.52 * scale - (1 - scale) * 0.1);
    mesh.position.y = y;
    rug.add(mesh);
  });

  const shadowMat = new THREE.ShadowMaterial({ color: '#5b3a29', opacity: 0.22 });
  const shadowPlane = new THREE.Mesh(new THREE.PlaneGeometry(12, 10), shadowMat);
  shadowPlane.rotation.x = -Math.PI / 2;
  shadowPlane.position.y = FLOOR_Y + 0.004;
  shadowPlane.receiveShadow = true;

  // Ventana de arco
  const win = new THREE.Group();
  win.position.set(-0.68, 0.98, -2.39);
  const paneShape = archShape(0.86, 0.62);
  const paneGeo = new THREE.ShapeGeometry(paneShape, 20);
  {
    const pos = paneGeo.attributes.position;
    const uv = paneGeo.attributes.uv;
    for (let i = 0; i < pos.count; i++) uv.setXY(i, (pos.getX(i) + 0.43) / 0.86, pos.getY(i) / 1.05);
  }
  const skyCanvas = document.createElement('canvas');
  skyCanvas.width = 128;
  skyCanvas.height = 160;
  const skyMap = new THREE.CanvasTexture(skyCanvas);
  skyMap.colorSpace = THREE.SRGBColorSpace;
  const pane = new THREE.Mesh(paneGeo, new THREE.MeshBasicMaterial({ map: skyMap }));
  pane.position.z = 0.012;
  const frameShape = archShape(1.02, 0.62);
  frameShape.holes.push(new THREE.Path(archShape(0.86, 0.62).getPoints(20)));
  const frameMat = new THREE.MeshStandardMaterial({ color: '#fffdf8', roughness: 0.75 });
  const casing = new THREE.Mesh(new THREE.ExtrudeGeometry(frameShape, { depth: 0.07, bevelEnabled: false, curveSegments: 20 }), frameMat);
  const barV = new THREE.Mesh(new THREE.BoxGeometry(0.035, 1.04, 0.035), frameMat);
  barV.position.set(0, 0.52, 0.04);
  const barH = new THREE.Mesh(new THREE.BoxGeometry(0.86, 0.035, 0.035), frameMat);
  barH.position.set(0, 0.62, 0.04);
  const sill = new THREE.Mesh(new THREE.BoxGeometry(1.2, 0.07, 0.18), frameMat);
  sill.position.set(0, -0.035, 0.07);
  win.add(pane, casing, barV, barH, sill);

  // Luz de la ventana sobre el suelo
  const patchGeo = new THREE.ShapeGeometry(archShape(0.8, 0.5), 16);
  patchGeo.rotateX(Math.PI / 2);
  {
    const pos = patchGeo.attributes.position;
    for (let i = 0; i < pos.count; i++) pos.setX(i, pos.getX(i) + pos.getZ(i) * 0.55);
  }
  const patchMat = new THREE.MeshBasicMaterial({ color: '#fffdf2', transparent: true, opacity: 0.5, side: THREE.DoubleSide, depthWrite: false });
  const patch = new THREE.Mesh(patchGeo, patchMat);
  patch.position.set(-0.62, 0.006, -2.1);

  // Muebles: solo asoman en pantallas anchas.
  const lit = (color, roughness = 0.7) => new THREE.MeshStandardMaterial({ color, roughness });
  const plant = new THREE.Group();
  const pot = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.15, 0.3, 24), lit('#fffdf8'));
  pot.position.y = 0.15;
  const band = new THREE.Mesh(new THREE.CylinderGeometry(0.205, 0.19, 0.08, 24), lit('#ffd166'));
  band.position.y = 0.24;
  plant.add(pot, band);
  const leafShape = new THREE.Shape();
  leafShape.moveTo(0, 0);
  leafShape.bezierCurveTo(0.14, 0.12, 0.12, 0.5, 0, 0.62);
  leafShape.bezierCurveTo(-0.12, 0.5, -0.14, 0.12, 0, 0);
  const leafGeo = new THREE.ExtrudeGeometry(leafShape, { depth: 0.02, bevelEnabled: false, curveSegments: 10 });
  for (let i = 0; i < 6; i++) {
    const leaf = new THREE.Mesh(leafGeo, lit(i % 2 ? '#6cc47f' : '#4fae7c'));
    leaf.position.y = 0.28;
    leaf.rotation.order = 'YXZ';
    leaf.rotation.y = (i / 6) * Math.PI * 2;
    leaf.rotation.x = 0.35 + (i % 3) * 0.16;
    leaf.scale.setScalar(0.9 + (i % 2) * 0.25);
    leaf.castShadow = true;
    plant.add(leaf);
  }
  pot.castShadow = true;
  plant.position.set(1.0, 0, -1.8);

  const shelf = new THREE.Group();
  const board = new THREE.Mesh(new THREE.BoxGeometry(1.0, 0.05, 0.24), lit('#e2b279'));
  shelf.add(board);
  [['#ff8fb1', 0.26, -0.34, 0], ['#9db7ff', 0.3, -0.24, 0], ['#ffd166', 0.22, -0.15, 0], ['#7ed6a5', 0.26, -0.02, 0.32]].forEach(([color, tall, x, tilt]) => {
    const book = new THREE.Mesh(new THREE.BoxGeometry(0.07, tall, 0.17), lit(color));
    book.position.set(x, 0.025 + tall / 2, 0);
    book.rotation.z = -tilt;
    shelf.add(book);
  });
  const globe = new THREE.Mesh(new THREE.SphereGeometry(0.1, 20, 14), lit('#ff7a7a', 0.5));
  globe.position.set(0.3, 0.125, 0);
  shelf.add(globe);
  shelf.position.set(1.9, 1.5, -2.27);

  const pouf = new THREE.Group();
  const cushion = new THREE.Mesh(new THREE.SphereGeometry(1, 28, 18), lit('#7ed6a5', 0.9));
  cushion.scale.set(0.42, 0.24, 0.42);
  cushion.position.y = 0.24;
  cushion.castShadow = true;
  const button = new THREE.Mesh(new THREE.SphereGeometry(0.05, 12, 8), lit('#4fae7c'));
  button.position.y = 0.47;
  pouf.add(cushion, button);
  pouf.position.set(-1.75, 0, -1.25);

  scene.add(wall, floor, baseboard, patch, rug, shadowPlane, win, plant, shelf, pouf);

  // ── Soporte de la criatura ──
  const mount = new THREE.Group();
  mount.position.y = FLOOR_Y;
  const fx = new THREE.Group();
  scene.add(mount, fx);

  const blobMap = canvasTexture(64, 64, (ctx) => {
    const g = ctx.createRadialGradient(32, 32, 2, 32, 32, 32);
    g.addColorStop(0, 'rgba(91,58,41,0.42)');
    g.addColorStop(1, 'rgba(91,58,41,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 64, 64);
  });
  const blobMat = new THREE.MeshBasicMaterial({ map: blobMap, transparent: true, depthWrite: false });
  const blob = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), blobMat);
  blob.rotation.x = -Math.PI / 2;
  blob.position.y = FLOOR_Y + 0.008;
  blob.visible = false;
  scene.add(blob);

  const hitGeo = new THREE.SphereGeometry(1, 12, 8);
  const hitMat = new THREE.MeshBasicMaterial({ visible: false });
  const hits = [];
  const setHits = (list) => {
    for (const mesh of hits.splice(0)) scene.remove(mesh);
    list.forEach(({ x, y, rx, ry, rz }, index) => {
      const mesh = new THREE.Mesh(hitGeo, hitMat);
      mesh.position.set(x, FLOOR_Y + y, 0);
      mesh.scale.set(rx, ry, rz);
      mesh.userData.index = index;
      scene.add(mesh);
      hits.push(mesh);
    });
  };

  let actor = null;
  let eggs = [];
  let mode = 'none';
  let currentKey = '';
  let eggProgress = 0;
  let selectedEgg = -1;
  let night = false;
  let paused = false;
  const handlers = { pet: null, tap: null, eggTap: null, eggPick: null };

  function clearMount() {
    if (actor) {
      actor.timeline?.kill();
      mount.remove(actor.object);
      actor.dispose();
      actor = null;
    }
    for (const egg of eggs) {
      gsap.killTweensOf(egg.scale);
      gsap.killTweensOf(egg.position);
      gsap.killTweensOf(egg.rotation);
      mount.remove(egg);
      egg.traverse((node) => {
        if (node.geometry && !node.geometry.userData.shared) node.geometry.dispose();
      });
    }
    eggs = [];
    blob.visible = false;
  }

  function dress(target, equipped) {
    for (const itemId of Object.values(equipped ?? {})) {
      if (!itemId) continue;
      const wear = buildWear(itemId, target.anchors);
      if (!wear) continue;
      if (wear.parent === 'head') {
        target.rig.headPivot.add(wear.object);
      } else if (target.plan.blob) {
        // En las crías el cuerpo cuelga de la cabeza: mismo sitio, otro padre.
        wear.object.position.y -= target.rig.headPivot.position.y;
        target.rig.headPivot.add(wear.object);
      } else {
        target.rig.squash.add(wear.object);
      }
    }
  }

  function putActor(formId, genes, equipped, mood) {
    actor = new Actor(buildCreature(formId, genes));
    dress(actor, equipped);
    mount.add(actor.object);
    actor.setMood(mood ?? 'bien');
    const width = actor.plan.body.w * actor.plan.scale;
    setHits([{ x: 0, y: actor.height * 0.5, rx: width * 0.62 + 0.12, ry: actor.height * 0.56, rz: 0.5 }]);
    blob.visible = true;
    blob.scale.set(width * 1.5, width * 1.1, 1);
    blob.userData.base = width;
    const wide = Math.max(actor.plan.body.w, actor.plan.head.rx * 2) * actor.plan.scale;
    setSubject(clamp(actor.height + 0.44, 1.12, 1.9), clamp(wide + 0.52, 1.0, 1.7));
  }

  function putEgg(genes) {
    const egg = buildEgg(genes);
    egg.scale.setScalar(EGG_SCALE);
    mount.add(egg);
    eggs = [egg];
    setHits([{ x: 0, y: 0.5, rx: 0.5, ry: 0.6, rz: 0.5 }]);
    blob.visible = true;
    blob.scale.set(1.15, 0.85, 1);
    blob.userData.base = 0.8;
    setSubject(1.4, 1.15);
  }

  const wearKey = (view) => `${view.equipped?.cabeza}|${view.equipped?.cara}|${view.equipped?.cuello}`;

  function showCreature(view) {
    const key = `${view.id}|${view.formId}|${wearKey(view)}`;
    if (key === currentKey) {
      actor?.setMood(view.mood ?? 'bien');
      return;
    }
    const redress = actor && currentKey.startsWith(`${view.id}|${view.formId}|`);
    currentKey = key;
    clearMount();
    if (view.stage === 'huevo') {
      mode = 'egg';
      putEgg(view.genes);
    } else {
      mode = 'creature';
      putActor(view.formId, view.genes, view.equipped, view.mood);
      if (redress && !reduce) actor.hop(1, 0.12);
    }
  }

  function showDemo() {
    const adults = FORM_LIST.filter((form) => form.stage === 'adulto');
    const form = adults[Math.floor(Math.random() * adults.length)];
    const genes = createGenes(Math.floor(Math.random() * 4294967296), form.family);
    currentKey = 'demo';
    clearMount();
    mode = 'demo';
    putActor(form.id, genes, {}, 'feliz');
  }

  function showEggs(offer) {
    const key = `eggs|${offer.map((egg) => `${egg.hue}-${egg.hue2}`).join('|')}`;
    if (key === currentKey) return;
    currentKey = key;
    clearMount();
    mode = 'eggs';
    selectedEgg = -1;
    eggs = offer.map((genes, index) => {
      const egg = buildEgg(genes);
      egg.position.x = (index - 1) * 0.7;
      egg.scale.setScalar(0.8);
      mount.add(egg);
      return egg;
    });
    setSubject(1.3, 2.25);
    setHits(offer.map((_, index) => ({ x: (index - 1) * 0.7, y: 0.42, rx: 0.33, ry: 0.48, rz: 0.4 })));
  }

  function selectEgg(index) {
    selectedEgg = index;
    eggs.forEach((egg, i) => {
      const scale = i === index ? 0.98 : 0.78;
      gsap.to(egg.scale, { x: scale, y: scale, z: scale, duration: reduce ? 0 : 0.5, ease: 'back.out(2.2)', overwrite: true });
      if (i === index && !reduce) {
        gsap.fromTo(egg.position, { y: 0 }, { y: 0.16, duration: 0.18, ease: 'power2.out', yoyo: true, repeat: 1, overwrite: true });
      }
    });
  }

  // ── Partículas ──
  const heartGeo = (() => {
    const s = new THREE.Shape();
    s.moveTo(0, -0.45);
    s.bezierCurveTo(-0.95, 0.1, -0.5, 0.78, 0, 0.3);
    s.bezierCurveTo(0.5, 0.78, 0.95, 0.1, 0, -0.45);
    const g = new THREE.ExtrudeGeometry(s, { depth: 0.14, bevelEnabled: true, bevelSize: 0.05, bevelThickness: 0.05, bevelSegments: 2 });
    g.center();
    return g;
  })();
  const starGeo = (() => {
    const s = new THREE.Shape();
    for (let i = 0; i < 8; i++) {
      const r = i % 2 ? 0.17 : 0.5;
      const a = (i / 8) * Math.PI * 2 + Math.PI / 2;
      if (i === 0) s.moveTo(Math.cos(a) * r, Math.sin(a) * r);
      else s.lineTo(Math.cos(a) * r, Math.sin(a) * r);
    }
    s.closePath();
    const g = new THREE.ExtrudeGeometry(s, { depth: 0.08, bevelEnabled: false });
    g.center();
    return g;
  })();
  const dotGeo = new THREE.SphereGeometry(0.5, 14, 10);
  const zMap = canvasTexture(64, 64, (ctx) => {
    ctx.font = '900 54px "Zen Maru Gothic", system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = '#fffdf8';
    ctx.fillText('z', 32, 30);
  });

  const rand = (a, b) => a + Math.random() * (b - a);

  function particle(geometry, color, position, opacity = 1) {
    const mesh = new THREE.Mesh(geometry, new THREE.MeshBasicMaterial({ color, transparent: true, opacity, depthWrite: false }));
    mesh.position.copy(position);
    mesh.scale.setScalar(0.001);
    fx.add(mesh);
    return mesh;
  }
  const retire = (object) => () => {
    fx.remove(object);
    object.material.dispose();
  };

  function hearts(position, count = 1) {
    if (reduce) return;
    for (let i = 0; i < count; i++) {
      const mesh = particle(heartGeo, '#ff6f9c', position.clone().add(new THREE.Vector3(rand(-0.15, 0.15), rand(-0.05, 0.1), 0.1)));
      const size = rand(0.1, 0.15);
      gsap.timeline({ onComplete: retire(mesh), delay: i * 0.07 })
        .to(mesh.scale, { x: size, y: size, z: size, duration: 0.25, ease: 'back.out(2.5)' })
        .to(mesh.position, { y: `+=${rand(0.4, 0.65)}`, x: `+=${rand(-0.2, 0.2)}`, duration: 1.1, ease: 'power1.out' }, 0)
        .to(mesh.rotation, { z: rand(-0.5, 0.5), duration: 1.1 }, 0)
        .to(mesh.material, { opacity: 0, duration: 0.4, ease: 'power1.in' }, 0.7);
    }
  }

  function sparks(center, count = 10, spread = 0.7, color = '#ffd166') {
    if (reduce) return;
    for (let i = 0; i < count; i++) {
      const mesh = particle(starGeo, i % 3 === 0 ? '#fffdf8' : color, center);
      const angle = (i / count) * Math.PI * 2 + rand(-0.2, 0.2);
      const reach = spread * rand(0.7, 1.15);
      const size = rand(0.09, 0.16);
      gsap.timeline({ onComplete: retire(mesh) })
        .to(mesh.scale, { x: size, y: size, z: size, duration: 0.2, ease: 'back.out(3)' })
        .to(mesh.position, { x: center.x + Math.cos(angle) * reach, y: center.y + Math.sin(angle) * reach * 0.9, duration: 0.8, ease: 'power3.out' }, 0)
        .to(mesh.rotation, { z: rand(-2, 2), duration: 0.9 }, 0)
        .to(mesh.scale, { x: 0.001, y: 0.001, z: 0.001, duration: 0.3, ease: 'power2.in' }, 0.6);
    }
  }

  function bubbles(count = 14) {
    if (reduce) return;
    const top = actor ? actor.height : 1;
    for (let i = 0; i < count; i++) {
      const start = new THREE.Vector3(rand(-0.55, 0.55), FLOOR_Y + rand(0.1, top * 0.9), rand(0.25, 0.55));
      const mesh = particle(dotGeo, i % 2 ? '#ffffff' : '#e3f6ff', start, 0.75);
      const size = rand(0.07, 0.17);
      gsap.timeline({ onComplete: retire(mesh), delay: i * 0.08 })
        .to(mesh.scale, { x: size, y: size, z: size, duration: 0.3, ease: 'back.out(2)' })
        .to(mesh.position, { y: `+=${rand(0.35, 0.7)}`, x: `+=${rand(-0.12, 0.12)}`, duration: 1.1, ease: 'sine.out' }, 0)
        .to(mesh.scale, { x: size * 1.35, y: size * 1.35, z: size * 1.35, duration: 0.08 }, 1.02)
        .to(mesh.material, { opacity: 0, duration: 0.1 }, 1.04);
    }
  }

  function crumbs(position, color = '#e2b279') {
    if (reduce) return;
    for (let i = 0; i < 5; i++) {
      const mesh = particle(dotGeo, color, position);
      const size = rand(0.03, 0.05);
      mesh.scale.setScalar(size);
      gsap.timeline({ onComplete: retire(mesh) })
        .to(mesh.position, { x: `+=${rand(-0.28, 0.28)}`, duration: 0.5, ease: 'power1.out' }, 0)
        .to(mesh.position, { y: `+=${rand(0.1, 0.22)}`, duration: 0.18, ease: 'power2.out' }, 0)
        .to(mesh.position, { y: FLOOR_Y + 0.03, duration: 0.3, ease: 'power2.in' }, 0.18)
        .to(mesh.material, { opacity: 0, duration: 0.25 }, 0.5);
    }
  }

  function snore() {
    if (reduce || !actor) return;
    const material = new THREE.SpriteMaterial({ map: zMap, transparent: true, opacity: 0, depthWrite: false });
    const sprite = new THREE.Sprite(material);
    sprite.position.set(0.3, FLOOR_Y + actor.height * 0.92, 0.3);
    sprite.scale.setScalar(0.12);
    fx.add(sprite);
    gsap.timeline({ onComplete: retire(sprite) })
      .to(material, { opacity: 0.95, duration: 0.3 })
      .to(sprite.position, { x: '+=0.3', y: '+=0.5', duration: 2.2, ease: 'sine.out' }, 0)
      .to(sprite.scale, { x: 0.26, y: 0.26, duration: 2.2, ease: 'sine.out' }, 0)
      .to(material, { opacity: 0, duration: 0.6 }, 1.6);
  }

  const headCenter = () => new THREE.Vector3(0, FLOOR_Y + (actor ? actor.height * 0.78 : 0.7), 0.35);
  const bodyCenter = () => new THREE.Vector3(0, FLOOR_Y + (actor ? actor.height * 0.5 : 0.55), 0.2);

  // ── Secuencias ──

  function feed(itemId, reaction) {
    return new Promise((resolve) => {
      if (!actor) return resolve();
      const item = buildItem(itemId);
      if (!item || reduce) {
        if (reaction === 'asco') actor.shake();
        return resolve();
      }
      const holder = new THREE.Group();
      holder.add(item);
      holder.scale.setScalar(0.36);
      holder.position.set(0, FLOOR_Y + 0.9, 0.62);
      scene.add(holder);
      const done = () => {
        scene.remove(holder);
        resolve();
      };
      gsap.timeline()
        .to(holder.position, { y: FLOOR_Y, duration: 0.42, ease: 'bounce.out' })
        .call(() => {
          const mouthful = new THREE.Vector3(0, FLOOR_Y + 0.25, 0.6);
          actor.eat((bite) => {
            const left = 0.36 * [0.72, 0.42, 0.001][bite];
            gsap.to(holder.scale, { x: left, y: left, z: left, duration: 0.12 });
            crumbs(mouthful);
          }).eventCallback('onComplete', () => {
            actor.busy = false;
            actor.setMouth(actor.restMouth());
            if (reaction === 'favorita') {
              hearts(headCenter(), 4);
              actor.hop(2, 0.22);
            } else if (reaction === 'asco') {
              actor.shake();
            }
            done();
          });
        });
    });
  }

  function playToy(itemId) {
    return new Promise((resolve) => {
      const item = buildItem(itemId);
      if (!actor || !item || reduce) return resolve();
      const holder = new THREE.Group();
      holder.add(item);
      holder.scale.setScalar(0.001);
      holder.position.set(0.5, FLOOR_Y, 0.42);
      scene.add(holder);
      actor.hop(3, 0.24);
      gsap.timeline({ onComplete: () => { scene.remove(holder); resolve(); } })
        .to(holder.scale, { x: 0.42, y: 0.42, z: 0.42, duration: 0.3, ease: 'back.out(2)' })
        .to(holder.position, { y: FLOOR_Y + 0.5, duration: 0.24, ease: 'power2.out', yoyo: true, repeat: 5 }, 0.2)
        .to(holder.rotation, { y: Math.PI * 2, duration: 1.4, ease: 'none' }, 0.2)
        .call(() => sparks(bodyCenter(), 8, 0.6), null, 1.0)
        .to(holder.scale, { x: 0.001, y: 0.001, z: 0.001, duration: 0.25, ease: 'back.in(2)' }, 1.7);
    });
  }

  function bath() {
    if (!actor) return;
    bubbles(16);
    actor.wash();
    if (!reduce) gsap.delayedCall(1.3, () => sparks(bodyCenter(), 8, 0.65, '#bfe3ff'));
  }

  function heal() {
    if (!actor) return;
    sparks(bodyCenter(), 10, 0.6, '#7ed6a5');
    actor.hop(1, 0.2);
  }

  function cheer(won) {
    if (!actor) return;
    if (won) {
      actor.spin();
      sparks(bodyCenter(), 12, 0.8);
    } else {
      actor.shake();
    }
  }

  function swapIn(view) {
    currentKey = '';
    showCreature(view);
    if (!actor) return;
    const scale = actor.plan.scale;
    actor.rig.root.scale.setScalar(scale * 0.35);
    gsap.to(actor.rig.root.scale, { x: scale, y: scale, z: scale, duration: 0.9, ease: 'elastic.out(1, 0.5)' });
  }

  function hatch(view) {
    return new Promise((resolve) => {
      const egg = eggs[0];
      if (!egg || mode !== 'egg' || reduce) {
        currentKey = '';
        showCreature(view);
        return resolve();
      }
      mode = 'busy';
      const shellColor = egg.children[0].material.color.clone();
      gsap.timeline({ onComplete: resolve })
        .to(egg.rotation, { z: 0.16, duration: 0.07, ease: 'sine.inOut', yoyo: true, repeat: 13 })
        .to(egg.scale, { x: EGG_SCALE * 1.1, y: EGG_SCALE * 0.9, z: EGG_SCALE * 1.1, duration: 0.12, ease: 'power2.in' })
        .call(() => {
          const center = new THREE.Vector3(0, FLOOR_Y + 0.55, 0.2);
          for (let i = 0; i < 9; i++) {
            const shard = particle(dotGeo, shellColor, center);
            shard.scale.set(rand(0.1, 0.16), rand(0.08, 0.12), 0.03);
            const angle = (i / 9) * Math.PI * 2;
            gsap.timeline({ onComplete: retire(shard) })
              .to(shard.position, { x: Math.cos(angle) * rand(0.5, 0.9), z: 0.2 + Math.sin(angle) * 0.3, duration: 0.7, ease: 'power2.out' }, 0)
              .to(shard.position, { y: center.y + rand(0.3, 0.6), duration: 0.25, ease: 'power2.out' }, 0)
              .to(shard.position, { y: FLOOR_Y + 0.03, duration: 0.45, ease: 'bounce.out' }, 0.25)
              .to(shard.rotation, { z: rand(-3, 3), x: rand(-3, 3), duration: 0.7 }, 0)
              .to(shard.material, { opacity: 0, duration: 0.5 }, 1.1);
          }
          sparks(center, 12, 0.8);
          swapIn(view);
        })
        .to({}, { duration: 0.8 })
        .call(() => actor?.hop(2, 0.2))
        .to({}, { duration: 0.9 });
    });
  }

  function evolve(view) {
    return new Promise((resolve) => {
      if (!actor || reduce) {
        currentKey = '';
        showCreature(view);
        return resolve();
      }
      const old = actor;
      old.timeline?.kill();
      old.busy = true;
      old.setMouth('open');
      const size = old.height * 1.5;
      const glow = new THREE.Mesh(dotGeo, new THREE.MeshBasicMaterial({ color: '#ffffff', transparent: true, opacity: 0, depthWrite: false }));
      glow.position.set(0, FLOOR_Y + old.height * 0.55, 0.1);
      glow.scale.setScalar(0.3);
      scene.add(glow);
      gsap.timeline({
        onComplete: () => {
          scene.remove(glow);
          glow.material.dispose();
          resolve();
        },
      })
        .to(old.rig.bounce.position, { y: 0.22, duration: 0.5, ease: 'power2.out' }, 0)
        .to(old.rig.root.rotation, { y: Math.PI * 6, duration: 1.3, ease: 'power3.in' }, 0)
        .to(old.rig.squash.scale, { x: 0.9, y: 1.12, z: 0.9, duration: 0.22, ease: 'sine.inOut', yoyo: true, repeat: 3 }, 0.2)
        .to(glow.material, { opacity: 1, duration: 0.5, ease: 'power2.in' }, 0.8)
        .to(glow.scale, { x: size, y: size, z: size, duration: 0.5, ease: 'power2.in' }, 0.8)
        .call(() => {
          swapIn(view);
          sparks(new THREE.Vector3(0, FLOOR_Y + 0.7, 0.3), 16, 1);
        })
        .to(glow.material, { opacity: 0, duration: 0.6, ease: 'power2.out' })
        .to(glow.scale, { x: size * 1.5, y: size * 1.5, z: size * 1.5, duration: 0.6, ease: 'power2.out' }, '<')
        .call(() => actor?.hop(2, 0.24))
        .to({}, { duration: 1 });
    });
  }

  function farewell() {
    return new Promise((resolve) => {
      if (!actor || reduce) return resolve();
      const gone = actor;
      gone.timeline?.kill();
      gone.busy = true;
      const light = new THREE.Mesh(dotGeo, new THREE.MeshBasicMaterial({ color: '#fffbe6', transparent: true, opacity: 0, depthWrite: false }));
      light.position.set(0, FLOOR_Y + gone.height * 0.5, 0.1);
      light.scale.setScalar(0.3);
      scene.add(light);
      gsap.timeline({
        onComplete: () => {
          scene.remove(light);
          light.material.dispose();
          resolve();
        },
      })
        .to(gone.rig.root.scale, { x: 0.001, y: 0.001, z: 0.001, duration: 1.1, ease: 'power2.in' }, 0)
        .to(light.material, { opacity: 0.95, duration: 0.8 }, 0.2)
        .to(light.position, { x: -0.68, y: 1.7, z: -2.2, duration: 1.8, ease: 'power1.inOut' }, 1.0)
        .to(light.scale, { x: 0.08, y: 0.08, z: 0.08, duration: 1.8, ease: 'power1.in' }, 1.0)
        .to(light.material, { opacity: 0, duration: 0.4 }, 2.5);
    });
  }

  // ── Día y noche ──
  const dim = { k: 0 };
  let skyPhase = 'day';
  function applyDim() {
    const k = dim.k;
    wallMat.color.lerpColors(DAY.wall, NIGHT.wall, k);
    floorMat.color.lerpColors(DAY.floor, NIGHT.floor, k);
    rugMat.color.lerpColors(DAY.rug, NIGHT.rug, k);
    rugLightMat.color.lerpColors(DAY.rugLight, NIGHT.rugLight, k);
    trimMat.color.lerpColors(DAY.trim, NIGHT.trim, k);
    scene.background.lerpColors(DAY.back, NIGHT.back, k);
    key.color.lerpColors(DAY.key, NIGHT.key, k);
    key.intensity = 2.1 - 1.55 * k;
    hemi.intensity = 1.05 - 0.6 * k;
    rim.intensity = 0.7 + 0.2 * k;
    scene.environmentIntensity = 0.5 - 0.36 * k;
    shadowMat.opacity = 0.22 - 0.1 * k;
    const sun = skyPhase === 'day' ? 0.5 : skyPhase === 'night' ? 0.14 : 0.3;
    // Con el cielo tapado entra menos luz por la ventana.
    const cover = GREY_SKIES.has(weather) ? 0.4 : 1;
    patchMat.opacity = sun * cover * (1 - k * 0.5);
  }

  function setNight(on) {
    if (on === night) return;
    night = on;
    gsap.to(dim, { k: on ? 1 : 0, duration: reduce ? 0 : 0.9, ease: 'power2.inOut', onUpdate: applyDim });
  }

  let weather = null;
  let skyDrawnAt = 0;
  function drawWindow(t = performance.now() / 1000) {
    skyPhase = drawSky(skyCanvas.getContext('2d'), skyCanvas.width, skyCanvas.height, new Date(), weather, t, reduce);
    skyMap.needsUpdate = true;
    skyDrawnAt = t;
  }

  function refreshSky() {
    drawWindow();
    patchMat.color.set(skyPhase === 'night' ? '#cfdcff' : skyPhase === 'day' ? '#fffdf2' : '#ffe9cf');
    applyDim();
  }

  // El tiempo que hace fuera: 'lluvia', 'nieve'… o null para el cielo de siempre.
  function setWeather(kind) {
    if (kind === weather) return;
    weather = kind ?? null;
    refreshSky();
  }
  refreshSky();
  const skyTimer = setInterval(refreshSky, 60 * 1000);

  // ── Encuadre ──
  const focus = { top: 0, bottom: 0 };
  // Alto y ancho, en unidades de escena, de lo que hay que encuadrar.
  const subject = { h: 1.55, w: 1.3 };
  function setSubject(h, w) {
    if (reduce || !focus.bottom) {
      subject.h = h;
      subject.w = w;
      if (focus.bottom) frame();
      return;
    }
    gsap.to(subject, { h, w, duration: 0.7, ease: 'power3.out', onUpdate: frame, overwrite: true });
  }
  let width = 1;
  let height = 1;

  function frame() {
    const bottom = focus.bottom || height;
    const free = Math.max(140, bottom - focus.top);
    const perUnit = Math.min((free * 0.8) / subject.h, (width * 0.78) / subject.w);
    const distance = height / perUnit / (2 * Math.tan(THREE.MathUtils.degToRad(FOV / 2)));
    camera.position.set(0, 0.5 + subject.h * 0.68, distance);
    camera.lookAt(0, subject.h * 0.47, 0);
    // La alfombra acompaña al encuadre para verse siempre entera.
    rug.scale.setScalar(clamp(subject.w / 1.52, 0.6, 1.6));
    camera.aspect = width / height;
    const center = focus.top + free * 0.5;
    camera.setViewOffset(width, height, 0, Math.round(height / 2 - center), width, height);
    camera.updateProjectionMatrix();
  }

  function resize() {
    width = Math.max(1, canvas.clientWidth);
    height = Math.max(1, canvas.clientHeight);
    renderer.setSize(width, height, false);
    frame();
  }
  const observer = new ResizeObserver(resize);
  observer.observe(canvas);
  resize();

  function setFocus(top, bottom, animate = true) {
    if (!animate || reduce) {
      focus.top = top;
      focus.bottom = bottom;
      frame();
      return;
    }
    if (!focus.bottom) focus.bottom = height;
    gsap.to(focus, { top, bottom, duration: 0.45, ease: 'power3.out', onUpdate: frame, overwrite: true });
  }

  // ── Puntero ──
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  let press = null;

  function hitAt(event) {
    const rect = canvas.getBoundingClientRect();
    pointer.set(((event.clientX - rect.left) / rect.width) * 2 - 1, -((event.clientY - rect.top) / rect.height) * 2 + 1);
    raycaster.setFromCamera(pointer, camera);
    const found = raycaster.intersectObjects(hits, false)[0];
    return found ? found.object.userData.index : -1;
  }

  function headPoint(lift = 0.14) {
    const point = new THREE.Vector3(0, FLOOR_Y + (actor ? actor.height : eggs.length ? 1.2 : 1) + lift, 0).project(camera);
    return { x: (point.x * 0.5 + 0.5) * width, y: (-point.y * 0.5 + 0.5) * height };
  }

  function worldAt(event, depth = 0.45) {
    const rect = canvas.getBoundingClientRect();
    const ndc = new THREE.Vector3(((event.clientX - rect.left) / rect.width) * 2 - 1, -((event.clientY - rect.top) / rect.height) * 2 + 1, 0.5).unproject(camera);
    const direction = ndc.sub(camera.position).normalize();
    const t = (depth - camera.position.z) / direction.z;
    return camera.position.clone().addScaledVector(direction, t);
  }

  canvas.addEventListener('pointerdown', (event) => {
    const index = hitAt(event);
    press = { id: event.pointerId, x: event.clientX, y: event.clientY, moved: 0, stroke: 0, index, strokes: 0 };
    canvas.setPointerCapture?.(event.pointerId);
  });

  canvas.addEventListener('pointermove', (event) => {
    if (actor) {
      const head = headPoint(-0.3);
      actor.lookAt((event.clientX - head.x) / (width * 0.45), (head.y - event.clientY) / (height * 0.3));
    }
    if (!press || press.id !== event.pointerId) return;
    const step = Math.hypot(event.clientX - press.x, event.clientY - press.y);
    press.x = event.clientX;
    press.y = event.clientY;
    press.moved += step;
    if (mode !== 'creature' && mode !== 'demo') return;
    if (!actor || hitAt(event) < 0) return;
    const head = headPoint(-0.3);
    actor.leanTarget = clamp((event.clientX - head.x) / 70, -1, 1);
    press.stroke += step;
    if (press.stroke > 34) {
      press.stroke = 0;
      press.strokes += 1;
      if (actor.mood !== 'dormida') {
        if (press.strokes === 1) actor.nuzzle();
        hearts(worldAt(event), 1);
        for (const cheek of actor.rig.cheeks) cheek.material.opacity = 0.95;
      }
      handlers.pet?.();
    }
  });

  const release = (event) => {
    if (!press || press.id !== event.pointerId) return;
    const tapped = press.moved < 9;
    const index = tapped ? hitAt(event) : -1;
    if (actor) {
      actor.leanTarget = 0;
      for (const cheek of actor.rig.cheeks) cheek.material.opacity = actor.mood === 'feliz' ? 0.8 : 0.62;
    }
    press = null;
    if (index < 0) return;
    if (mode === 'eggs') handlers.eggPick?.(index);
    else if (mode === 'egg') warm();
    else if (mode === 'creature' || mode === 'demo') {
      if (actor && actor.mood !== 'dormida' && !actor.busy) actor.hop(1, 0.16);
      handlers.tap?.();
    }
  };
  canvas.addEventListener('pointerup', release);
  canvas.addEventListener('pointercancel', release);

  function warm() {
    const egg = eggs[0];
    if (!egg || mode !== 'egg') return;
    if (!reduce) {
      gsap.fromTo(egg.scale, { x: EGG_SCALE * 1.09, y: EGG_SCALE * 0.92, z: EGG_SCALE * 1.09 }, { x: EGG_SCALE, y: EGG_SCALE, z: EGG_SCALE, duration: 0.5, ease: 'elastic.out(1, 0.4)', overwrite: true });
      sparks(new THREE.Vector3(rand(-0.25, 0.25), FLOOR_Y + rand(0.6, 1.1), 0.45), 3, 0.25, '#ffb45c');
    }
    handlers.eggTap?.();
  }

  // ── Bucle ──
  let last = 0;
  let snoreAt = 0;
  renderer.setAnimationLoop((ms) => {
    const t = ms / 1000;
    const dt = Math.min(0.05, t - last);
    last = t;
    if (paused) return;
    // La lluvia y la nieve se mueven a unos doce dibujos por segundo.
    if (!reduce && MOVING_SKIES.has(weather) && t - skyDrawnAt > 0.08) drawWindow(t);
    if (actor) {
      actor.update(t, dt);
      const lift = actor.rig.bounce.position.y;
      const base = blob.userData.base ?? 1;
      const shrink = 1 - clamp(lift, 0, 0.6) * 0.7;
      blob.scale.set(base * 1.5 * shrink, base * 1.1 * shrink, 1);
      blobMat.opacity = shrink;
      if (actor.mood === 'dormida' && t > snoreAt) {
        snore();
        snoreAt = t + 1.9;
      }
    }
    if (mode === 'egg' && eggs[0]) {
      const egg = eggs[0];
      const urge = 1 + eggProgress * 2.5;
      egg.rotation.z = Math.sin(t * 2.4) * 0.035 * urge + (eggProgress > 0.75 ? Math.sin(t * 31) * 0.02 : 0);
    } else if (mode === 'eggs') {
      eggs.forEach((egg, index) => {
        egg.rotation.z = Math.sin(t * 2 + index * 1.7) * (index === selectedEgg ? 0.09 : 0.035);
      });
    }
    renderer.render(scene, camera);
  });

  return {
    on(name, fn) { handlers[name] = fn; },
    setFocus,
    showCreature,
    showDemo,
    showEggs,
    selectEgg,
    setEggProgress(value) { eggProgress = clamp(value, 0, 1); },
    warm,
    hasActor: () => actor !== null,
    setMood(mood) { actor?.setMood(mood); },
    say(seconds) { actor?.say(seconds); },
    headPoint,
    feed,
    playToy,
    bath,
    heal,
    cheer,
    hatch,
    evolve,
    farewell,
    setNight,
    setWeather,
    setPaused(value) { paused = value; },
    destroy() {
      clearInterval(skyTimer);
      observer.disconnect();
      renderer.setAnimationLoop(null);
      renderer.dispose();
    },
  };
}
