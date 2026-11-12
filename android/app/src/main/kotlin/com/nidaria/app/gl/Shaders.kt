// Sombreadores GLSL ES 3.0. El modelo de luz reproduce el material físico
// de Three.js: difusa de Lambert, especular GGX, brillo de tela (sheen),
// barniz (clearcoat), luz de hemisferio y luz del entorno.

package com.nidaria.app.gl

internal object Shaders {
    const val VERTEX = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
uniform mat4 uModel;
uniform mat4 uViewProj;
uniform mat3 uNormalMatrix;
uniform mat4 uShadowMatrix;
uniform float uNormalBias;
uniform vec2 uUvScale;
out vec3 vWorldPos;
out vec3 vNormal;
out vec2 vUv;
out vec4 vShadowCoord;
void main() {
  vec4 world = uModel * vec4(aPosition, 1.0);
  vWorldPos = world.xyz;
  vNormal = normalize(uNormalMatrix * aNormal);
  vUv = aUv * uUvScale;
  vShadowCoord = uShadowMatrix * vec4(world.xyz + vNormal * uNormalBias, 1.0);
  gl_Position = uViewProj * world;
}
"""

    const val DEPTH_VERTEX = """#version 300 es
layout(location = 0) in vec3 aPosition;
uniform mat4 uModel;
uniform mat4 uViewProj;
void main() {
  gl_Position = uViewProj * uModel * vec4(aPosition, 1.0);
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
in vec4 vShadowCoord;
out vec4 fragColor;
uniform vec3 uColor;
uniform float uOpacity;
uniform sampler2DShadow uShadowMap;
uniform float uReceiveShadow;
uniform float uShadowBias;
uniform float uShadowTexel;

vec3 linearToSrgb(vec3 c) {
  c = max(c, vec3(0.0));
  return mix(c * 12.92, 1.055 * pow(c, vec3(0.41666)) - 0.055, step(vec3(0.0031308), c));
}

float shadowFactor() {
  if (uReceiveShadow < 0.5) return 1.0;
  vec3 c = vShadowCoord.xyz / vShadowCoord.w;
  if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 1.0;
  c.z += uShadowBias;
  float sum = 0.0;
  for (int x = -1; x <= 1; x++) {
    for (int y = -1; y <= 1; y++) {
      sum += texture(uShadowMap, vec3(c.xy + vec2(float(x), float(y)) * uShadowTexel, c.z));
    }
  }
  return sum / 9.0;
}
"""

    const val BASIC_FRAGMENT = COMMON + """
uniform sampler2D uMap;
uniform float uUseMap;
void main() {
  vec4 c = vec4(uColor, uOpacity);
  if (uUseMap > 0.5) c *= texture(uMap, vUv);
  fragColor = vec4(linearToSrgb(c.rgb), c.a);
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
uniform vec3 uHemiSky;
uniform vec3 uHemiGround;
uniform vec3 uLightDir[2];
uniform vec3 uLightColor[2];
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

void main() {
  vec3 N = normalize(vNormal);
  if (!gl_FrontFacing) N = -N;
  vec3 V = normalize(uCameraPos - vWorldPos);
  vec3 dxy = max(abs(dFdx(N)), abs(dFdy(N)));
  float geometryRoughness = max(max(dxy.x, dxy.y), dxy.z);

  vec3 diffuseColor = uColor * (1.0 - uMetalness);
  float roughness = min(max(uRoughness, 0.0525) + geometryRoughness, 1.0);
  vec3 specularColor = mix(vec3(0.04), uColor, uMetalness);
  float ccRoughness = min(max(uClearcoatRoughness, 0.0525) + geometryRoughness, 1.0);
  float sheenRoughness = clamp(uSheenRoughness, 0.07, 1.0);
  bool useSheen = max(max(uSheenColor.r, uSheenColor.g), uSheenColor.b) > 0.0;
  bool useCoat = uClearcoat > 0.0;

  vec3 directDiffuse = vec3(0.0);
  vec3 directSpecular = vec3(0.0);
  vec3 sheenDirect = vec3(0.0);
  vec3 coatDirect = vec3(0.0);
  float shadow = shadowFactor();
  for (int i = 0; i < 2; i++) {
    vec3 L = uLightDir[i];
    vec3 lightColor = uLightColor[i];
    if (i == 0) lightColor *= shadow;
    vec3 irradiance = clamp(dot(N, L), 0.0, 1.0) * lightColor;
    if (useCoat) coatDirect += irradiance * BRDF_GGX(L, V, N, vec3(0.04), 1.0, ccRoughness);
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

  vec3 outgoing = directDiffuse + indirectDiffuse + directSpecular + indirectSpecular;
  if (useSheen) {
    float comp = 1.0 - 0.157 * max(max(uSheenColor.r, uSheenColor.g), uSheenColor.b);
    outgoing = outgoing * comp + sheenDirect + sheenIndirect;
  }
  if (useCoat) {
    vec3 Fcc = F_Schlick(vec3(0.04), 1.0, clamp(dot(N, V), 0.0, 1.0));
    outgoing = outgoing * (1.0 - uClearcoat * Fcc) + (coatDirect + coatIndirect) * uClearcoat;
  }
  fragColor = vec4(linearToSrgb(outgoing), uOpacity);
}
"""
}
