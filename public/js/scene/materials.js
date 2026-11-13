// Materiales compartidos de la escena. El de arcilla es el de las
// criaturas y los objetos esculpidos: la luz "envuelve" la forma (la
// penumbra es suave y algo cálida, como en un juguete de vinilo blando)
// y la oclusión horneada en la malla oscurece los pliegues.

import * as THREE from 'three';

const DIRECT_LINE = 'vec3 irradiance = dotNL * directLight.color;';

function patchClay(material, { wrap, sss, ao }) {
  const sssColor = new THREE.Color(sss);
  material.onBeforeCompile = (shader) => {
    if (ao) {
      shader.vertexShader = shader.vertexShader
        .replace('#include <common>', '#include <common>\nattribute float ao;\nvarying float vAoBake;')
        .replace('#include <begin_vertex>', '#include <begin_vertex>\nvAoBake = ao;');
      shader.fragmentShader = shader.fragmentShader
        .replace('#include <common>', '#include <common>\nvarying float vAoBake;')
        .replace('#include <aomap_fragment>', `#include <aomap_fragment>
          float bakedAo = clamp( vAoBake, 0.0, 1.0 );
          reflectedLight.indirectDiffuse *= bakedAo;
          reflectedLight.indirectSpecular *= mix( 1.0, bakedAo, 0.85 );
          reflectedLight.directDiffuse *= mix( 1.0, bakedAo, 0.45 );
          #ifdef USE_SHEEN
            sheenSpecularIndirect *= bakedAo;
          #endif`);
    }
    if (wrap > 0) {
      const chunk = THREE.ShaderChunk.lights_physical_pars_fragment.replace(DIRECT_LINE, `
        float wrapNL = saturate( ( dot( geometryNormal, directLight.direction ) + ${wrap.toFixed(3)} ) / ${(1 + wrap).toFixed(3)} );
        vec3 irradiance = ( dotNL + ( wrapNL - dotNL ) * vec3( ${sssColor.r.toFixed(3)}, ${sssColor.g.toFixed(3)}, ${sssColor.b.toFixed(3)} ) ) * directLight.color;`);
      shader.fragmentShader = shader.fragmentShader.replace('#include <lights_physical_pars_fragment>', chunk);
    }
  };
  material.customProgramCacheKey = () => `clay:${wrap}:${sss}:${ao}`;
  return material;
}

const FINISH = {
  // Tela de peluche: mate con un brillo aterciopelado en los bordes.
  plush: { roughness: 0.86, sheen: 0.7, sheenRoughness: 0.55, sheenColor: '#ffffff', sheenTint: 0.35 },
  // Arcilla o vinilo mate.
  matte: { roughness: 0.68, sheen: 0.25, sheenRoughness: 0.6, sheenColor: '#ffffff', sheenTint: 0.2 },
  // Vinilo con algo de barniz.
  gloss: { roughness: 0.42, clearcoat: 0.55, clearcoatRoughness: 0.32 },
  // Cerámica o caramelo.
  glaze: { roughness: 0.22, clearcoat: 1, clearcoatRoughness: 0.12 },
  // Madera, cartón: sin brillo.
  wood: { roughness: 0.62 },
  fabric: { roughness: 0.92, sheen: 1, sheenRoughness: 0.5, sheenColor: '#ffffff', sheenTint: 0.45 },
  metal: { roughness: 0.28, metalness: 0.85 },
};

const cache = new Map();

// Material de arcilla. Con `vertexColors` toma el color de la malla
// esculpida; si no, `color`. Se comparte: no se debe tocar ni liberar.
export function clay({ finish = 'matte', color = '#ffffff', vertexColors = true, ao = true, wrap = 0.32, sss = '#ff9d80', side = THREE.FrontSide } = {}) {
  const key = `${finish}|${color}|${vertexColors}|${ao}|${wrap}|${sss}|${side}`;
  if (cache.has(key)) return cache.get(key);
  const spec = FINISH[finish] ?? FINISH.matte;
  const params = {
    color,
    vertexColors,
    side,
    roughness: spec.roughness,
    metalness: spec.metalness ?? 0,
  };
  if (spec.sheen) {
    params.sheen = spec.sheen;
    params.sheenRoughness = spec.sheenRoughness;
    params.sheenColor = new THREE.Color(spec.sheenColor).multiplyScalar(spec.sheenTint);
  }
  if (spec.clearcoat) {
    params.clearcoat = spec.clearcoat;
    params.clearcoatRoughness = spec.clearcoatRoughness;
  }
  const material = patchClay(new THREE.MeshPhysicalMaterial(params), { wrap, sss, ao });
  material.userData.shared = true;
  cache.set(key, material);
  return material;
}

// Material simple sin oclusión ni vértices de color, para piezas sueltas
// hechas con geometrías normales (marcos, tablas, cristal…).
export function plain(color, finish = 'matte', extra = {}) {
  const key = `plain|${color}|${finish}|${JSON.stringify(extra)}`;
  if (cache.has(key)) return cache.get(key);
  const spec = FINISH[finish] ?? FINISH.matte;
  const params = { color, roughness: spec.roughness, metalness: spec.metalness ?? 0, ...extra };
  if (spec.sheen && extra.sheen === undefined) {
    params.sheen = spec.sheen;
    params.sheenRoughness = spec.sheenRoughness;
    params.sheenColor = new THREE.Color(color).lerp(new THREE.Color('#ffffff'), 0.5).multiplyScalar(spec.sheenTint);
  }
  if (spec.clearcoat && extra.clearcoat === undefined) {
    params.clearcoat = spec.clearcoat;
    params.clearcoatRoughness = spec.clearcoatRoughness;
  }
  const material = patchClay(new THREE.MeshPhysicalMaterial(params), { wrap: 0.3, sss: '#ffd0b8', ao: false });
  material.userData.shared = true;
  cache.set(key, material);
  return material;
}

// Lienzo convertido en textura de color.
export function canvasTexture(width, height, draw, { repeat = false } = {}) {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  draw(canvas.getContext('2d'), width, height);
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  if (repeat) texture.wrapS = texture.wrapT = THREE.RepeatWrapping;
  return texture;
}

// Sombra de contacto: una mancha suave para debajo de los objetos.
let contact = null;
export function contactTexture() {
  if (!contact) {
    contact = canvasTexture(128, 128, (ctx, w, h) => {
      const g = ctx.createRadialGradient(w / 2, h / 2, 0, w / 2, h / 2, w / 2);
      g.addColorStop(0, 'rgba(70,40,25,0.55)');
      g.addColorStop(0.45, 'rgba(70,40,25,0.28)');
      g.addColorStop(1, 'rgba(70,40,25,0)');
      ctx.fillStyle = g;
      ctx.fillRect(0, 0, w, h);
    });
  }
  return contact;
}

export function contactShadow(width, depth, opacity = 1) {
  const material = new THREE.MeshBasicMaterial({ map: contactTexture(), transparent: true, depthWrite: false, opacity, toneMapped: false });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(width, depth), material);
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.y = 0.004;
  mesh.renderOrder = 1;
  mesh.userData.contact = true;
  return mesh;
}
