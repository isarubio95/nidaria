// Sombreadores GLSL ES 3.0. El modelo de luz reproduce el material físico
// de Three.js: difusa de Lambert, especular GGX, brillo de tela (sheen),
// barniz (clearcoat), luz de hemisferio, luz del entorno, una lámpara y el
// tono neutro. Encima, lo que añade la web a la arcilla (materials.js):
// color por vértice, oclusión horneada y una luz que envuelve la forma.

package com.nidaria.app.gl

internal object Shaders {
    private const val ATTRIBUTES = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
layout(location = 3) in vec3 aColor;
layout(location = 4) in float aAo;
layout(location = 5) in vec4 aSkinIndex;
layout(location = 6) in vec4 aSkinWeight;
uniform mat4 uModel;
uniform mat4 uViewProj;
uniform bool uSkinning;
// Huesos en coordenadas del mundo: el hueso ya movido por su postura de reposo.
uniform mat4 uBones[8];

mat4 skinMatrix() {
  return uBones[int(aSkinIndex.x)] * aSkinWeight.x
    + uBones[int(aSkinIndex.y)] * aSkinWeight.y
    + uBones[int(aSkinIndex.z)] * aSkinWeight.z
    + uBones[int(aSkinIndex.w)] * aSkinWeight.w;
}
"""

    const val VERTEX = ATTRIBUTES + """
uniform mat3 uNormalMatrix;
uniform mat4 uShadowMatrix;
uniform float uNormalBias;
uniform vec2 uUvScale;
uniform vec2 uBumpUvScale;
uniform bool uVertexColors;
uniform bool uBakedAo;
out vec3 vWorldPos;
out vec3 vNormal;
out vec2 vUv;
out vec2 vBumpUv;
out vec4 vShadowCoord;
out vec3 vColor;
out float vAo;
void main() {
  vec4 world;
  if (uSkinning) {
    mat4 skin = skinMatrix();
    world = skin * vec4(aPosition, 1.0);
    vNormal = normalize(mat3(skin) * aNormal);
  } else {
    world = uModel * vec4(aPosition, 1.0);
    vNormal = normalize(uNormalMatrix * aNormal);
  }
  vWorldPos = world.xyz;
  vUv = aUv * uUvScale;
  vBumpUv = aUv * uBumpUvScale;
  vColor = uVertexColors ? aColor : vec3(1.0);
  vAo = uBakedAo ? aAo : 1.0;
  vShadowCoord = uShadowMatrix * vec4(world.xyz + vNormal * uNormalBias, 1.0);
  gl_Position = uViewProj * world;
}
"""

    const val DEPTH_VERTEX = ATTRIBUTES + """
void main() {
  vec4 world = uSkinning ? skinMatrix() * vec4(aPosition, 1.0) : uModel * vec4(aPosition, 1.0);
  gl_Position = uViewProj * world;
}
"""

    const val DEPTH_FRAGMENT = """#version 300 es
precision mediump float;
void main() {}
"""

    private const val COMMON = """#version 300 es
precision highp float;
precision highp sampler2DShadow;
in vec3 vWorldPos;
in vec3 vNormal;
in vec2 vUv;
in vec2 vBumpUv;
in vec4 vShadowCoord;
in vec3 vColor;
in float vAo;
out vec4 fragColor;
uniform vec3 uColor;
uniform float uOpacity;
uniform sampler2DShadow uShadowMap;
uniform float uReceiveShadow;
uniform float uShadowBias;
uniform float uShadowTexel;
uniform float uShadowRadius;
uniform float uShadowIntensity;
uniform sampler2D uMap;
uniform float uUseMap;
uniform bool uToneMapped;
uniform float uExposure;

vec3 linearToSrgb(vec3 c) {
  c = max(c, vec3(0.0));
  return mix(c * 12.92, 1.055 * pow(c, vec3(0.41666)) - 0.055, step(vec3(0.0031308), c));
}

// NeutralToneMapping de Three.js (Khronos PBR Neutral).
vec3 neutral(vec3 color) {
  const float StartCompression = 0.8 - 0.04;
  const float Desaturation = 0.15;
  color *= uExposure;
  float x = min(color.r, min(color.g, color.b));
  float offset = x < 0.08 ? x - 6.25 * x * x : 0.04;
  color -= offset;
  float peak = max(color.r, max(color.g, color.b));
  if (peak < StartCompression) return color;
  float d = 1.0 - StartCompression;
  float newPeak = 1.0 - d * d / (peak + d - StartCompression);
  color *= newPeak / peak;
  float g = 1.0 - 1.0 / (Desaturation * (peak - newPeak) + 1.0);
  return mix(color, vec3(newPeak), g);
}

vec4 finish(vec3 color, float alpha) {
  if (uToneMapped) color = neutral(color);
  return vec4(linearToSrgb(color), alpha);
}

float shadowFactor() {
  if (uReceiveShadow < 0.5) return 1.0;
  vec3 c = vShadowCoord.xyz / vShadowCoord.w;
  if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 1.0;
  c.z += uShadowBias;
  float spread = uShadowTexel * uShadowRadius;
  float sum = 0.0;
  for (int x = -1; x <= 1; x++) {
    for (int y = -1; y <= 1; y++) {
      sum += texture(uShadowMap, vec3(c.xy + vec2(float(x), float(y)) * spread, c.z));
    }
  }
  return mix(1.0, sum / 9.0, uShadowIntensity);
}
"""

    const val BASIC_FRAGMENT = COMMON + """
void main() {
  vec4 c = vec4(uColor, uOpacity);
  if (uUseMap > 0.5) c *= texture(uMap, vUv);
  fragColor = finish(c.rgb, c.a);
}
"""

    const val SHADOW_FRAGMENT = COMMON + """
void main() {
  fragColor = vec4(linearToSrgb(uColor), uOpacity * (1.0 - shadowFactor()));
}
"""

    const val LIT_FRAGMENT = COMMON + """
uniform vec3 uCameraPos;
uniform float uRoughness;
uniform float uMetalness;
uniform vec3 uSheenColor;
uniform float uSheenRoughness;
uniform float uClearcoat;
uniform float uClearcoatRoughness;
uniform vec3 uEmissive;
uniform float uWrap;
uniform vec3 uSss;
uniform sampler2D uBumpMap;
uniform float uUseBump;
uniform float uBumpScale;
uniform vec3 uHemiSky;
uniform vec3 uHemiGround;
uniform vec3 uLightDir[2];
uniform vec3 uLightColor[2];
uniform vec3 uPointPos;
uniform vec3 uPointColor;
uniform float uPointDistance;
uniform float uPointDecay;
uniform float uEnvIntensity;
uniform samplerCube uEnvMap;
uniform float uEnvMaxLod;
uniform vec3 uSH[9];

const float PI = 3.141592653589793;
const float RECIPROCAL_PI = 0.3183098861837907;

float pow2(float x) { return x * x; }

vec3 F_Schlick(vec3 f0, float f90, float dotVH) {
  float fresnel = exp2((-5.55473 * dotVH - 6.98316) * dotVH);
  return f0 * (1.0 - fresnel) + f90 * fresnel;
}

float V_GGX_SmithCorrelated(float alpha, float dotNL, float dotNV) {
  float a2 = pow2(alpha);
  float gv = dotNL * sqrt(a2 + (1.0 - a2) * pow2(dotNV));
  float gl = dotNV * sqrt(a2 + (1.0 - a2) * pow2(dotNL));
  return 0.5 / max(gv + gl, 1e-6);
}

float D_GGX(float alpha, float dotNH) {
  float a2 = pow2(alpha);
  float denom = pow2(dotNH) * (a2 - 1.0) + 1.0;
  return RECIPROCAL_PI * a2 / pow2(denom);
}

vec3 BRDF_GGX(vec3 L, vec3 V, vec3 N, vec3 f0, float f90, float roughness) {
  float alpha = pow2(roughness);
  vec3 H = normalize(L + V);
  float dotNL = clamp(dot(N, L), 0.0, 1.0);
  float dotNV = clamp(dot(N, V), 0.0, 1.0);
  float dotNH = clamp(dot(N, H), 0.0, 1.0);
  float dotVH = clamp(dot(V, H), 0.0, 1.0);
  return F_Schlick(f0, f90, dotVH) * (V_GGX_SmithCorrelated(alpha, dotNL, dotNV) * D_GGX(alpha, dotNH));
}

float D_Charlie(float roughness, float dotNH) {
  float alpha = pow2(roughness);
  float invAlpha = 1.0 / alpha;
  float cos2h = dotNH * dotNH;
  float sin2h = max(1.0 - cos2h, 0.0078125);
  return (2.0 + invAlpha) * pow(sin2h, invAlpha * 0.5) / (2.0 * PI);
}

float V_Neubelt(float dotNV, float dotNL) {
  return clamp(1.0 / (4.0 * (dotNL + dotNV - dotNL * dotNV)), 0.0, 1.0);
}

vec3 BRDF_Sheen(vec3 L, vec3 V, vec3 N, vec3 sheenColor, float sheenRoughness) {
  vec3 H = normalize(L + V);
  float dotNL = clamp(dot(N, L), 0.0, 1.0);
  float dotNV = clamp(dot(N, V), 0.0, 1.0);
  float dotNH = clamp(dot(N, H), 0.0, 1.0);
  return sheenColor * (D_Charlie(sheenRoughness, dotNH) * V_Neubelt(dotNV, dotNL));
}

float IBLSheenBRDF(vec3 N, vec3 V, float roughness) {
  float dotNV = clamp(dot(N, V), 0.0, 1.0);
  float r2 = roughness * roughness;
  float a = roughness < 0.25 ? -339.2 * r2 + 161.4 * roughness - 25.9 : -8.48 * r2 + 14.3 * roughness - 9.95;
  float b = roughness < 0.25 ? 44.0 * r2 - 23.7 * roughness + 3.26 : 1.97 * r2 - 3.27 * roughness + 0.72;
  float DG = exp(a * dotNV + b) + (roughness < 0.25 ? 0.0 : 0.1 * (roughness - 0.25));
  return clamp(DG * RECIPROCAL_PI, 0.0, 1.0);
}

vec2 DFGApprox(vec3 N, vec3 V, float roughness) {
  float dotNV = clamp(dot(N, V), 0.0, 1.0);
  const vec4 c0 = vec4(-1.0, -0.0275, -0.572, 0.022);
  const vec4 c1 = vec4(1.0, 0.0425, 1.04, -0.04);
  vec4 r = roughness * c0 + c1;
  float a004 = min(r.x * r.x, exp2(-9.28 * dotNV)) * r.x + r.y;
  return vec2(-1.04, 1.04) * a004 + r.zw;
}

// Radiancia media del entorno ponderada por el coseno (irradiancia / PI).
vec3 envDiffuse(vec3 n) {
  return uSH[0] * 0.282095
    + uSH[1] * (0.488603 * n.y) + uSH[2] * (0.488603 * n.z) + uSH[3] * (0.488603 * n.x)
    + uSH[4] * (1.092548 * n.x * n.y) + uSH[5] * (1.092548 * n.y * n.z)
    + uSH[6] * (0.315392 * (3.0 * n.z * n.z - 1.0))
    + uSH[7] * (1.092548 * n.x * n.z) + uSH[8] * (0.546274 * (n.x * n.x - n.y * n.y));
}

vec3 envSpecular(vec3 V, vec3 N, float roughness) {
  vec3 R = reflect(-V, N);
  R = normalize(mix(R, N, roughness * roughness));
  return textureLod(uEnvMap, R, roughness * uEnvMaxLod).rgb;
}

// Relieve (bumpMap de Three.js: perturbNormalArb).
vec3 bumped(vec3 N) {
  vec2 dSTdx = dFdx(vBumpUv);
  vec2 dSTdy = dFdy(vBumpUv);
  float Hll = uBumpScale * texture(uBumpMap, vBumpUv).x;
  float dBx = uBumpScale * texture(uBumpMap, vBumpUv + dSTdx).x - Hll;
  float dBy = uBumpScale * texture(uBumpMap, vBumpUv + dSTdy).x - Hll;
  vec3 sx = normalize(dFdx(vWorldPos));
  vec3 sy = normalize(dFdy(vWorldPos));
  vec3 R1 = cross(sy, N);
  vec3 R2 = cross(N, sx);
  float det = dot(sx, R1) * (gl_FrontFacing ? 1.0 : -1.0);
  vec3 grad = sign(det) * (dBx * R1 + dBy * R2);
  return normalize(abs(det) * N - grad);
}

void main() {
  vec3 N = normalize(vNormal);
  if (!gl_FrontFacing) N = -N;
  if (uUseBump > 0.5) N = bumped(N);
  vec3 V = normalize(uCameraPos - vWorldPos);
  vec3 dxy = max(abs(dFdx(N)), abs(dFdy(N)));
  float geometryRoughness = max(max(dxy.x, dxy.y), dxy.z);

  vec4 base = vec4(uColor * vColor, uOpacity);
  if (uUseMap > 0.5) base *= texture(uMap, vUv);
  vec3 diffuseColor = base.rgb * (1.0 - uMetalness);
  float roughness = min(max(uRoughness, 0.0525) + geometryRoughness, 1.0);
  vec3 specularColor = mix(vec3(0.04), base.rgb, uMetalness);
  float ccRoughness = min(max(uClearcoatRoughness, 0.0525) + geometryRoughness, 1.0);
  float sheenRoughness = clamp(uSheenRoughness, 0.07, 1.0);
  bool useSheen = max(max(uSheenColor.r, uSheenColor.g), uSheenColor.b) > 0.0;
  bool useCoat = uClearcoat > 0.0;

  vec3 directDiffuse = vec3(0.0);
  vec3 directSpecular = vec3(0.0);
  vec3 sheenDirect = vec3(0.0);
  vec3 coatDirect = vec3(0.0);
  float shadow = shadowFactor();
  for (int i = 0; i < 3; i++) {
    vec3 L;
    vec3 lightColor;
    if (i < 2) {
      L = uLightDir[i];
      lightColor = uLightColor[i];
      if (i == 0) lightColor *= shadow;
    } else {
      // La lámpara: luz de punto con caída, como PointLight de Three.js.
      vec3 toLight = uPointPos - vWorldPos;
      float distance = length(toLight);
      L = toLight / max(distance, 1e-4);
      float falloff = 1.0 / max(pow(distance, uPointDecay), 0.01);
      if (uPointDistance > 0.0) falloff *= pow2(clamp(1.0 - pow2(pow2(distance / uPointDistance)), 0.0, 1.0));
      lightColor = uPointColor * falloff;
    }
    if (max(max(lightColor.r, lightColor.g), lightColor.b) <= 0.0) continue;
    float dotNL = clamp(dot(N, L), 0.0, 1.0);
    vec3 irradiance = dotNL * lightColor;
    // Arcilla: la penumbra envuelve la forma y tira a cálida.
    if (uWrap > 0.0) {
      float wrapNL = clamp((dot(N, L) + uWrap) / (1.0 + uWrap), 0.0, 1.0);
      irradiance = (dotNL + (wrapNL - dotNL) * uSss) * lightColor;
    }
    if (useCoat) coatDirect += dotNL * lightColor * BRDF_GGX(L, V, N, vec3(0.04), 1.0, ccRoughness);
    if (useSheen) sheenDirect += irradiance * BRDF_Sheen(L, V, N, uSheenColor, sheenRoughness);
    directSpecular += irradiance * BRDF_GGX(L, V, N, specularColor, 1.0, roughness);
    directDiffuse += irradiance * diffuseColor * RECIPROCAL_PI;
  }

  vec3 hemi = mix(uHemiGround, uHemiSky, 0.5 * N.y + 0.5);
  vec3 indirectDiffuse = hemi * diffuseColor * RECIPROCAL_PI;

  vec3 irradiance = envDiffuse(N) * uEnvIntensity;
  vec3 radiance = envSpecular(V, N, roughness) * uEnvIntensity;
  vec3 sheenIndirect = vec3(0.0);
  vec3 coatIndirect = vec3(0.0);
  if (useCoat) {
    vec2 fabCoat = DFGApprox(N, V, ccRoughness);
    coatIndirect += envSpecular(V, N, ccRoughness) * uEnvIntensity * (vec3(0.04) * fabCoat.x + fabCoat.y);
  }
  if (useSheen) sheenIndirect += irradiance * PI * uSheenColor * IBLSheenBRDF(N, V, sheenRoughness);

  vec2 fab = DFGApprox(N, V, roughness);
  vec3 FssEss = specularColor * fab.x + fab.y;
  float Ess = fab.x + fab.y;
  float Ems = 1.0 - Ess;
  vec3 Favg = specularColor + (1.0 - specularColor) * 0.047619;
  vec3 Fms = FssEss * Favg / (1.0 - Ems * Favg);
  vec3 singleScattering = FssEss;
  vec3 multiScattering = Fms * Ems;
  vec3 totalScattering = singleScattering + multiScattering;
  vec3 diffuse = diffuseColor * (1.0 - max(max(totalScattering.r, totalScattering.g), totalScattering.b));
  vec3 indirectSpecular = radiance * singleScattering + multiScattering * irradiance;
  indirectDiffuse += diffuse * irradiance;

  // Oclusión horneada en la malla: oscurece los pliegues.
  float ao = clamp(vAo, 0.0, 1.0);
  indirectDiffuse *= ao;
  indirectSpecular *= mix(1.0, ao, 0.85);
  directDiffuse *= mix(1.0, ao, 0.45);
  sheenIndirect *= ao;

  vec3 outgoing = directDiffuse + indirectDiffuse + directSpecular + indirectSpecular;
  if (useSheen) {
    float comp = 1.0 - 0.157 * max(max(uSheenColor.r, uSheenColor.g), uSheenColor.b);
    outgoing = outgoing * comp + sheenDirect + sheenIndirect;
  }
  if (useCoat) {
    vec3 Fcc = F_Schlick(vec3(0.04), 1.0, clamp(dot(N, V), 0.0, 1.0));
    outgoing = outgoing * (1.0 - uClearcoat * Fcc) + (coatDirect + coatIndirect) * uClearcoat;
  }
  outgoing += uEmissive;
  fragColor = finish(outgoing, base.a);
}
"""
}
