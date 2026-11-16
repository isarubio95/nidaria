// La habitación horneada: la pared, el suelo, la ventana, la estantería, el
// puf y el cuadro vienen de Blender (art/room/build_room.py) con la luz ya
// calculada por Cycles: la que entra por la ventana, las sombras suaves de
// los rincones y los rebotes entre paredes. Lo que cambia con el juego se
// pone encima en tiempo real:
//
// - El color de cada superficie: el papel pintado que toque, la tarima
//   (textura CC0 de Poly Haven), el friso, las cortinas, el cuadro.
// - La mezcla de luces: cada mapa de luz trae tres canales (sol, cielo y
//   lámpara) que se tiñen y se suman según la hora, el tiempo y si la
//   criatura duerme (ver setLighting).
// - Las sombras de lo que se mueve (la criatura, los muebles del jugador):
//   dos planos que solo reciben sombra, sobre el suelo y la pared.
//
// Si algo no carga, se monta la habitación de siempre (house.js) con la
// misma interfaz, para que el juego no se quede sin habitación.

import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import {
  buildHouse, archShape, beadboard, beamTexture, glassGlint, paintingTexture,
  WINDOW, WALL_Z, WAINSCOT,
} from './house.js';
import { canvasTexture } from './materials.js';

const BASE = '/rooms/';

// ── Material de superficie horneada ──
// color = albedo (color × textura) × (sol·L.r + cielo·L.g + lámpara·L.b).
// Las coordenadas de la textura de color salen de la posición en el mundo
// (en metros) para las superficies grandes, así el papel pintado empalma
// igual que antes; las demás usan las del modelo.
const PLANAR = { uv: 0, xy: 1, xz: 2, zy: 3 };

const VERTEX = /* glsl */ `
attribute vec2 uv1;
uniform mat3 mapTransform;
varying vec2 vMapUv;
varying vec2 vLightUv;
void main() {
  vec4 world = modelMatrix * vec4( position, 1.0 );
  #if PLANAR == 1
    vec2 base = world.xy;
  #elif PLANAR == 2
    vec2 base = world.xz;
  #elif PLANAR == 3
    vec2 base = world.zy;
  #else
    vec2 base = uv;
  #endif
  vMapUv = ( mapTransform * vec3( base, 1.0 ) ).xy;
  vLightUv = uv1;
  gl_Position = projectionMatrix * viewMatrix * world;
}`;

const FRAGMENT = /* glsl */ `
uniform vec3 color;
uniform sampler2D albedoMap;
uniform sampler2D lightMap;
uniform vec3 lightMax;
uniform float lightGamma;
uniform vec3 sunLight;
uniform vec3 skyLight;
uniform vec3 lampLight;
varying vec2 vMapUv;
varying vec2 vLightUv;
void main() {
  vec3 albedo = color;
  #ifdef USE_ALBEDO_MAP
    albedo *= texture2D( albedoMap, vMapUv ).rgb;
  #endif
  vec3 received = pow( texture2D( lightMap, vLightUv ).rgb, vec3( lightGamma ) ) * lightMax;
  vec3 light = sunLight * received.r + skyLight * received.g + lampLight * received.b;
  gl_FragColor = vec4( albedo * light, 1.0 );
  #include <tonemapping_fragment>
  #include <colorspace_fragment>
}`;

function bakedMaterial({ color, map = null, planar = 'uv', lightMap, lightMax, gamma, shared }) {
  if (map) map.updateMatrix();
  return new THREE.ShaderMaterial({
    name: 'horneado',
    defines: { PLANAR: PLANAR[planar], ...(map ? { USE_ALBEDO_MAP: '' } : {}) },
    uniforms: {
      color: { value: color.clone() },
      albedoMap: { value: map },
      mapTransform: { value: map ? map.matrix : new THREE.Matrix3() },
      lightMap: { value: lightMap },
      lightMax: { value: new THREE.Vector3(...lightMax) },
      lightGamma: { value: gamma },
      // Los tres tintes se comparten entre todos los materiales.
      sunLight: shared.sun,
      skyLight: shared.sky,
      lampLight: shared.lamp,
    },
    vertexShader: VERTEX,
    fragmentShader: FRAGMENT,
  });
}

// Cortinas de lino crudo con un ribete teja (sin lunares).
function curtainTexture() {
  const texture = canvasTexture(128, 512, (ctx, w, h) => {
    ctx.fillStyle = '#f1e9dc';
    ctx.fillRect(0, 0, w, h);
    // Trama de la tela.
    for (let x = 0; x < w; x += 2) {
      ctx.fillStyle = `rgba(120,90,60,${0.03 + 0.03 * ((x * 7) % 5) / 5})`;
      ctx.fillRect(x, 0, 1, h);
    }
    for (let y = 0; y < h; y += 2) {
      ctx.fillStyle = 'rgba(120,90,60,0.03)';
      ctx.fillRect(0, y, w, 1);
    }
    ctx.fillStyle = '#b8532f';
    ctx.fillRect(0, h - 44, w, 24);
    ctx.fillStyle = '#8f3d22';
    ctx.fillRect(0, h - 20, w, 4);
  });
  texture.anisotropy = 4;
  return texture;
}

// ── Luces según la hora ──
const SUN_TINT = {
  day: new THREE.Color('#fff0d8'), dawn: new THREE.Color('#ffc796'), dusk: new THREE.Color('#ffae78'), night: new THREE.Color('#9fb0ff'),
};
const SKY_DAY = new THREE.Color('#f2f6ff');
const SKY_GREY = new THREE.Color('#e4e8ee');
const SKY_NIGHT = new THREE.Color('#5d6fb8');
const LAMP_TINT = new THREE.Color('#ffcf9e');
// Cuánto aporta cada fuente. Se calibran con las medidas del horneado (ver
// room.json): el cielo deja el suelo junto a la criatura a SKY_LEVEL, el sol
// suma SUN_LEVEL en lo más claro de su mancha y la lámpara deja el suelo a
// LAMP_LEVEL.
const SKY_LEVEL = 0.95;
const SUN_LEVEL = 2.6;
const LAMP_LEVEL = 0.7;
const SUN_SHARE = { day: 1, dawn: 0.55, dusk: 0.6, night: 0 };
const SKY_SHARE = { day: 1, dawn: 0.75, dusk: 0.7, night: 0.22 };

function shadowCatcher(width, height, opacity) {
  const mesh = new THREE.Mesh(
    new THREE.PlaneGeometry(width, height),
    new THREE.ShadowMaterial({ color: '#2a1c14', opacity, depthWrite: false }),
  );
  mesh.receiveShadow = true;
  mesh.renderOrder = 1;
  return mesh;
}

// Cielo de la ventana (un lienzo que pinta room.js), el brillo del cristal
// y el haz de sol.
function windowExtras(sky) {
  const { x, y, width, straight, depth } = WINDOW;
  const group = new THREE.Group();
  const paneGeo = new THREE.ShapeGeometry(archShape(width, straight), 24);
  const uv = paneGeo.attributes.uv;
  const pp = paneGeo.attributes.position;
  for (let i = 0; i < pp.count; i++) uv.setXY(i, (pp.getX(i) + width / 2) / width, pp.getY(i) / (straight + width / 2));
  const pane = new THREE.Mesh(paneGeo, new THREE.MeshBasicMaterial({ map: sky, toneMapped: false }));
  pane.position.set(x, y, WALL_Z - depth - 0.002);

  const glint = new THREE.Mesh(
    new THREE.PlaneGeometry(width * 0.9, straight + width * 0.4),
    new THREE.MeshBasicMaterial({ map: glassGlint(), transparent: true, opacity: 0.12, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false }),
  );
  glint.position.set(x, y + (straight + width / 2) / 2, WALL_Z - depth * 0.62);

  // Haz: del hueco de la ventana a la mancha de sol del suelo.
  const top = y + straight * 0.95;
  const pts = [
    [x - 0.34, top, WALL_Z - 0.02], [x + 0.34, top, WALL_Z - 0.02],
    [x + 0.5, 0.02, WALL_Z + 1.95], [x - 0.22, 0.02, WALL_Z + 1.95],
  ];
  const beamGeo = new THREE.BufferGeometry();
  beamGeo.setAttribute('position', new THREE.Float32BufferAttribute(pts.flat(), 3));
  beamGeo.setAttribute('uv', new THREE.Float32BufferAttribute([0, 1, 1, 1, 1, 0, 0, 0], 2));
  beamGeo.setIndex([0, 3, 1, 1, 3, 2]);
  const beamMat = new THREE.MeshBasicMaterial({ map: beamTexture(), color: '#fff2da', transparent: true, opacity: 0.08, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, toneMapped: false });
  const beam = new THREE.Mesh(beamGeo, beamMat);
  beam.renderOrder = 3;
  group.add(pane, glint, beam);
  return { group, beamMat };
}

async function loadBaked({ anisotropy, sky, shared, wallpaperNow }) {
  const textures = new THREE.TextureLoader();
  const info = await fetch(`${BASE}room.json`).then((res) => {
    if (!res.ok) throw new Error(`room.json: ${res.status}`);
    return res.json();
  });
  const lightTexture = async (file) => {
    const texture = await textures.loadAsync(BASE + file);
    texture.flipY = false;
    texture.colorSpace = THREE.NoColorSpace;
    texture.anisotropy = Math.min(4, anisotropy);
    texture.needsUpdate = true;
    return texture;
  };
  const [gltf, floorWood, ...lightMaps] = await Promise.all([
    new GLTFLoader().loadAsync(`${BASE}room.glb`),
    textures.loadAsync(BASE + info.floorTexture.file),
    ...Object.values(info.maps).map((map) => lightTexture(map.file)),
  ]);
  const maps = Object.fromEntries(Object.keys(info.maps).map((name, i) => [name, { texture: lightMaps[i], max: info.maps[name].max }]));

  floorWood.colorSpace = THREE.SRGBColorSpace;
  floorWood.wrapS = floorWood.wrapT = THREE.RepeatWrapping;
  floorWood.repeat.setScalar(1 / info.floorTexture.meters);
  floorWood.anisotropy = anisotropy;

  const wainscot = beadboard(anisotropy);
  wainscot.wrapT = THREE.RepeatWrapping;
  wainscot.repeat.set(1 / 0.12, 1 / WAINSCOT);
  // Las del modelo van con las coordenadas de glTF (sin voltear).
  const curtain = curtainTexture();
  curtain.flipY = false;
  const painting = paintingTexture();
  painting.flipY = false;

  const wallpapered = [];
  gltf.scene.traverse((node) => {
    if (!node.isMesh) return;
    let owner = node;
    while (owner && !maps[owner.name]) owner = owner.parent;
    const target = owner?.name;
    if (!target) return;
    const source = node.material;
    const base = { lightMap: maps[target].texture, lightMax: maps[target].max, gamma: info.gamma, shared };
    let spec;
    switch (source.name) {
      case 'floor':
        // La tarima de la foto es algo oscura: se aclara y se calienta.
        spec = { color: new THREE.Color('#fff0e0').multiplyScalar(1.12), map: floorWood, planar: 'xz' };
        break;
      case 'wallpaper':
        spec = { color: new THREE.Color('#ffffff'), map: null, planar: target === 'shell' ? 'zy' : 'xy' };
        break;
      case 'wainscot':
        spec = { color: new THREE.Color('#ffffff'), map: wainscot, planar: 'xy' };
        break;
      case 'curtain':
        spec = { color: new THREE.Color('#ffffff'), map: curtain };
        break;
      case 'painting_art':
        spec = { color: new THREE.Color('#ffffff'), map: painting };
        break;
      default:
        spec = { color: source.color ?? new THREE.Color('#ffffff') };
    }
    node.material = bakedMaterial({ ...base, ...spec, map: spec.map ?? (source.name === 'wallpaper' ? wallpaperNow() ?? wainscot : null) });
    if (source.name === 'wallpaper') wallpapered.push(node.material);
    source.dispose();
    node.castShadow = false;
    node.receiveShadow = false;
  });

  const group = new THREE.Group();
  const extras = windowExtras(sky);
  const floorShadow = shadowCatcher(10, 9.4, 0.26);
  floorShadow.rotation.x = -Math.PI / 2;
  floorShadow.position.set(0, 0.002, WALL_Z + 4.7);
  const wallShadow = shadowCatcher(10, 3.2, 0.2);
  wallShadow.position.set(0, 1.6, WALL_Z + 0.014);
  group.add(gltf.scene, extras.group, floorShadow, wallShadow);

  const gain = {
    sky: SKY_LEVEL / info.skyAtCreature,
    sun: SUN_LEVEL / info.sunPeak,
    lamp: LAMP_LEVEL / info.lampAtCreature,
  };

  return {
    group,
    setWallpaper(map) {
      map.updateMatrix();
      for (const material of wallpapered) {
        material.uniforms.albedoMap.value = map;
        material.uniforms.mapTransform.value = map.matrix;
      }
    },
    // `night`: 0 de día, 1 con la luz apagada (la criatura duerme).
    // `phase`: la hora en el cielo ('day', 'dawn', 'dusk', 'night').
    // `cover`: < 1 con el cielo tapado.
    setLighting({ night, phase, cover }) {
      const day = 1 - night;
      const grey = cover < 1;
      const sun = day * (SUN_SHARE[phase] ?? 1) * (grey ? 0.12 : 1);
      shared.sun.value.copy(SUN_TINT[phase] ?? SUN_TINT.day).multiplyScalar(gain.sun * sun);
      const skyAmount = day * (SKY_SHARE[phase] ?? 1) * (grey ? 0.85 : 1) + night * 0.16;
      const skyTint = shared.skyTint.lerpColors(grey ? SKY_GREY : SKY_DAY, SKY_NIGHT, phase === 'night' ? Math.max(night, 0.85) : night);
      shared.sky.value.copy(skyTint).multiplyScalar(gain.sky * skyAmount);
      // De noche la lámpara está encendida; si la criatura duerme, más.
      const lamp = Math.max(night, phase === 'night' ? 0.75 : 0);
      shared.lamp.value.copy(LAMP_TINT).multiplyScalar(gain.lamp * lamp);
      extras.beamMat.opacity = 0.08 * sun;
      wallShadow.material.opacity = 0.2 * (0.5 + 0.5 * day);
      floorShadow.material.opacity = 0.26 * (0.6 + 0.4 * day);
    },
  };
}

// La habitación de siempre, con la misma interfaz que la horneada.
function classicHouse({ anisotropy, sky }) {
  const house = buildHouse({ anisotropy, sky });
  const { wallMat, floorMat, trimMat, patchMat, beamMat } = house;
  const NIGHT = { wall: new THREE.Color('#c6cdf0'), floor: new THREE.Color('#d2c8e2'), trim: new THREE.Color('#d8d8f0') };
  const WHITE = new THREE.Color('#ffffff');
  return {
    group: house.group,
    setWallpaper(map) {
      wallMat.map = map;
      wallMat.needsUpdate = true;
    },
    setLighting({ night, phase, cover }) {
      wallMat.color.lerpColors(WHITE, NIGHT.wall, night);
      floorMat.color.lerpColors(WHITE, NIGHT.floor, night);
      trimMat.color.lerpColors(WHITE, NIGHT.trim, night);
      const sun = phase === 'day' ? 1 : phase === 'night' ? 0.3 : 0.7;
      patchMat.color.set(phase === 'night' ? '#b9c8ff' : phase === 'day' ? '#fff2d2' : '#ffd9b0');
      beamMat.color.copy(patchMat.color);
      patchMat.opacity = 0.22 * sun * cover * (1 - night * 0.6);
      beamMat.opacity = 0.12 * sun * cover * (1 - night * 0.7);
    },
  };
}

// La habitación: `group` se llena cuando ha cargado (`ready`). Hasta
// entonces, setWallpaper y setLighting se guardan y se aplican al llegar.
export function createHouse({ anisotropy = 4, sky }) {
  const group = new THREE.Group();
  const shared = {
    sun: { value: new THREE.Color() },
    sky: { value: new THREE.Color() },
    lamp: { value: new THREE.Color() },
    skyTint: new THREE.Color(),
  };
  let impl = null;
  let wallpaper = null;
  let lighting = { night: 0, phase: 'day', cover: 1 };

  const ready = loadBaked({ anisotropy, sky, shared, wallpaperNow: () => wallpaper })
    .catch((error) => {
      console.warn('Habitación horneada no disponible; se usa la de siempre.', error);
      return classicHouse({ anisotropy, sky });
    })
    .then((house) => {
      impl = house;
      group.add(house.group);
      if (wallpaper) house.setWallpaper(wallpaper);
      house.setLighting(lighting);
    });

  return {
    group,
    ready,
    setWallpaper(map) {
      wallpaper = map;
      impl?.setWallpaper(map);
    },
    setLighting(next) {
      lighting = next;
      impl?.setLighting(next);
    },
  };
}
