// Llama de dibujo animado (puntas de cola, crines de ascuas...). TRELLIS no
// conserva el fuego de los bocetos (lo hornea como un color pálido), así que
// va aparte: un plano que siempre mira a la cámara y un sombreador que pinta
// la llama en tres capas de color plano (roja, naranja, amarilla) ondulada
// con un ruido que sube, unas chispas y un halo suave. Sin luz de verdad:
// una luz más en la escena obligaría a recompilar todos los materiales.
//
// El origen del objeto es el centro de la bola de la base: se cuelga de un
// hueso en la punta de la cola. `size` es la altura de la llama entera y
// `hue` gira el tono de todos sus colores (las pelusas azules, fuego azul).

import * as THREE from 'three';

const NOISE = /* glsl */ `
  float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
  }
  float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
    return v;
  }
`;

// Plano en espacio de vista: siempre de frente a la cámara y con la escala
// del mundo (la del tamaño de la criatura y su aplastamiento).
const BILLBOARD = /* glsl */ `
  uniform float uPower;
  uniform float uLift;
  varying vec2 vUv;
  void main() {
    vUv = uv;
    vec2 scale = vec2(length(modelMatrix[0].xyz), length(modelMatrix[1].xyz));
    vec4 mv = modelViewMatrix * vec4(0.0, 0.0, 0.0, 1.0);
    mv.xy += position.xy * scale * uPower;
    // Un poco hacia la cámara: así la llama envuelve la punta de la cola
    // (que si no asoma por delante) sin dejar de taparla el cuerpo.
    mv.z += uLift * scale.x;
    gl_Position = projectionMatrix * mv;
  }
`;

// Medidas en unidades de `size`: el plano va de x -0.4 a 0.4 y de y -0.25
// a 1.25; la bola de la base está centrada en el origen.
const FLAME = /* glsl */ `
  uniform float uTime;
  uniform float uSeed;
  uniform vec3 uOuter;
  uniform vec3 uMid;
  uniform vec3 uCore;
  varying vec2 vUv;
  ${NOISE}

  // Gota: bola de radio R abajo que se afila hasta la punta (y = tip). > 0 dentro.
  float drop(vec2 p, float R, float tip) {
    float w = p.y < 0.0 ? sqrt(max(R * R - p.y * p.y, 0.0)) : R * pow(1.0 - clamp(p.y / tip, 0.0, 1.0), 0.7);
    return w - abs(p.x);
  }

  float edge(float d) { return smoothstep(-0.75, 0.75, d / fwidth(d)); }

  void main() {
    float t = uTime;
    vec2 p = vec2((vUv.x - 0.5) * 0.8, vUv.y * 1.5 - 0.25);
    // Una sola deformación para las tres capas (así van una dentro de
    // otra): se mece más cuanto más arriba, y el borde se rompe en lenguas.
    float h = clamp(p.y / 0.9, 0.0, 1.0);
    float n = fbm(vec2(p.x * 3.0 + uSeed, p.y * 2.6 - t * 2.2));
    p.x -= ((n - 0.5) * 0.3 + sin(t * 2.3 + uSeed) * 0.04) * h * h;
    float tongue = (fbm(vec2(p.x * 8.0 + uSeed * 3.0, p.y * 4.0 - t * 4.2)) - 0.5) * 0.13 * smoothstep(0.02, 0.5, p.y);
    float flick = 0.06 * sin(t * 7.0 + uSeed) + 0.04 * sin(t * 11.3 + uSeed * 2.0);
    // El - 0.006 quita las hebras finas que dejaría el ruido en la punta.
    float aOut = edge(drop(p, 0.24, 0.88 + flick) + tongue - 0.006);
    float aMid = edge(drop(p + vec2(0.0, 0.035), 0.17, 0.6 + flick * 0.8) + tongue * 0.8 - 0.006);
    float aCore = edge(drop(p + vec2(0.0, 0.07), 0.1, 0.34 + flick * 0.5) + tongue * 0.5 - 0.006);
    vec3 col = mix(mix(uOuter, uMid, aMid), uCore, aCore);
    float alpha = aOut;

    // Chispas que se sueltan por arriba y se apagan al subir.
    for (int i = 0; i < 4; i++) {
      float fi = float(i);
      float ph = fract(t * 0.5 + fi * 0.27 + uSeed * 0.13);
      vec2 sp = vec2(sin(fi * 12.9 + uSeed + ph * 5.0) * 0.12, 0.45 + ph * 0.7);
      float d = 0.026 * (1.0 - ph) - length(p - sp);
      float a = edge(d) * (1.0 - ph * ph);
      col = mix(col, ph < 0.45 ? uCore : uMid, a * (1.0 - alpha));
      alpha = max(alpha, a);
    }
    if (alpha < 0.01) discard;
    gl_FragColor = vec4(col, alpha);
    #include <colorspace_fragment>
  }
`;

const GLOW = /* glsl */ `
  uniform float uTime;
  uniform float uSeed;
  uniform float uGlow;
  uniform vec3 uColor;
  varying vec2 vUv;
  void main() {
    vec2 p = vUv - 0.5;
    float r = length(p) * 2.0;
    float flick = 0.85 + 0.15 * sin(uTime * 9.0 + uSeed) * sin(uTime * 5.3 + uSeed * 1.7);
    float a = exp(-r * r * 4.0) * uGlow * flick;
    gl_FragColor = vec4(uColor, a);
    #include <colorspace_fragment>
  }
`;

// Cuánto arde según el humor de Actor.
export const FLAME_POWER = { feliz: 1.15, bien: 1, regular: 0.85, mal: 0.65, fatal: 0.5, enferma: 0.55, dormida: 0.42 };

export function makeFlame({
  size = 0.2,
  scale = 1,
  seed = 0,
  outer = '#f04a1a',
  mid = '#ff9a1f',
  core = '#ffe27a',
  glow = 0.45,
  hue = 0,
} = {}) {
  const object = new THREE.Group();
  const color = (hex) => new THREE.Color(hex).offsetHSL(hue / 360, 0, 0);
  // Compartidos por los dos materiales (el reparto copia las referencias).
  const uniforms = {
    uTime: { value: 0 },
    uSeed: { value: seed },
    uPower: { value: 1 },
    uLift: { value: size * 0.3 },
  };

  const flameGeo = new THREE.PlaneGeometry(size * 0.8, size * 1.5);
  flameGeo.translate(0, size * 0.5, 0);
  const flameMat = new THREE.ShaderMaterial({
    uniforms: {
      ...uniforms,
      uOuter: { value: color(outer) },
      uMid: { value: color(mid) },
      uCore: { value: color(core) },
    },
    vertexShader: BILLBOARD,
    fragmentShader: FLAME,
    transparent: true,
    depthWrite: false,
  });
  const flame = new THREE.Mesh(flameGeo, flameMat);
  flame.frustumCulled = false;
  flame.renderOrder = 4;

  const glowGeo = new THREE.PlaneGeometry(size * 1.5, size * 1.5);
  glowGeo.translate(0, size * 0.25, 0);
  const glowMat = new THREE.ShaderMaterial({
    uniforms: { ...uniforms, uGlow: { value: glow }, uColor: { value: color('#ff7a26') } },
    vertexShader: BILLBOARD,
    fragmentShader: GLOW,
    transparent: true,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
  });
  const halo = new THREE.Mesh(glowGeo, glowMat);
  halo.frustumCulled = false;
  halo.renderOrder = 3;
  object.add(halo, flame);

  let power = 1;
  let target = 1;
  let last = performance.now() / 1000;
  flame.onBeforeRender = () => {
    const now = performance.now() / 1000;
    const dt = Math.min(now - last, 0.1);
    last = now;
    power += (target - power) * (1 - Math.exp(-dt * 3));
    uniforms.uTime.value = now;
    uniforms.uPower.value = power;
  };

  return {
    object,
    setPower(value) { target = value; },
    dispose() {
      flameGeo.dispose();
      flameMat.dispose();
      glowGeo.dispose();
      glowMat.dispose();
    },
  };
}
