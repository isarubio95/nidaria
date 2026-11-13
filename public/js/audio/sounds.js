// Los efectos de sonido, hechos con código: cada receta suma tonos y
// ruido filtrado. No hay ficheros de audio. No toca el DOM, así que
// también corre en Node.
//
// La app de Android tiene una copia en Kotlin (audio/Sounds.kt). Si se
// cambia una receta aquí, hay que cambiarla allí y volver a generar
// golden.json (android/tools/golden.mjs): una prueba compara las muestras.
//
// El ruido sale de un generador con semilla fija: el mismo sonido da
// siempre las mismas muestras.

// Frecuencia de una nota MIDI (60 = do central, 69 = la 440).
const note = (midi) => 440 * 2 ** ((midi - 69) / 12);

function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function seedOf(name) {
  let hash = 2166136261;
  for (const char of name) hash = Math.imul(hash ^ char.charCodeAt(0), 16777619);
  return hash >>> 0;
}

// Formas de onda como armónicos [múltiplo, volumen]. Solo senos sumados:
// así nunca hay armónicos por encima de lo que la frecuencia de muestreo
// puede representar (se saltan los que no caben).
const WAVES = {
  sine: [[1, 1]],
  soft: [[1, 1], [2, 0.3], [3, 0.12]],
  tri: [[1, 1], [3, -1 / 9], [5, 1 / 25]],
};

// Una receta llama a estas funciones; primero se apuntan las piezas y
// luego se suman en el búfer, que ya sabe cuánto ha de durar.
function recorder(rand) {
  const parts = [];
  return {
    parts,
    rand,
    // Tono con deslizamiento exponencial de `f` a `to` en `glide` segundos
    // (por defecto, toda la nota), vibrato opcional que se apaga con
    // `vibDecay` y envolvente de ataque lineal y caída (1 - t/dur)^curve.
    tone({ at = 0, dur, f, to = f, glide = dur, wave = 'sine', gain = 0.3, attack = 0.005, curve = 2, vib = 0, vibRate = 6, vibDecay = 0 }) {
      parts.push({ kind: 'tone', at, dur, f, to, glide, wave, gain, attack, curve, vib, vibRate, vibDecay });
    },
    // Campanita: un tono con parciales que se apagan antes cuanto más agudos.
    bell({ at = 0, dur = 0.8, f, gain = 0.15 }) {
      const partials = [[1, 1, 1], [2, 0.32, 1.8], [3, 0.12, 2.6], [4.2, 0.05, 3.4]];
      for (const [ratio, amp, speed] of partials) {
        parts.push({ kind: 'tone', at, dur: dur / speed, f: f * ratio, to: f * ratio, glide: dur, wave: 'sine', gain: gain * amp, attack: 0.002, curve: 3, vib: 0, vibRate: 0, vibDecay: 0 });
      }
    },
    // Ruido blanco con un paso bajo (que puede moverse de `lp` a `lpTo`) y
    // un paso alto opcional.
    noise({ at = 0, dur, gain = 0.2, lp = 4000, lpTo = lp, hp = 0, attack = 0.002, curve = 2 }) {
      parts.push({ kind: 'noise', at, dur, gain, lp, lpTo, hp, attack, curve });
    },
  };
}

const envelope = (t, dur, attack, curve) => (t < attack ? t / attack : 1) * Math.max(0, 1 - t / dur) ** curve;

function renderTone(out, sr, part) {
  const start = Math.round(part.at * sr);
  const count = Math.min(out.length - start, Math.round(part.dur * sr));
  const harmonics = WAVES[part.wave] ?? WAVES.sine;
  const norm = harmonics.reduce((sum, [, amp]) => sum + Math.abs(amp), 0);
  const ratio = part.to / part.f;
  let phase = 0;
  for (let i = 0; i < count; i++) {
    const t = i / sr;
    const progress = Math.min(1, t / part.glide);
    let freq = part.f * ratio ** progress;
    if (part.vib) freq *= 1 + part.vib * Math.exp(-part.vibDecay * t) * Math.sin(2 * Math.PI * part.vibRate * t);
    phase += (2 * Math.PI * freq) / sr;
    let sample = 0;
    for (const [multiple, amp] of harmonics) {
      if (freq * multiple < sr * 0.45) sample += amp * Math.sin(phase * multiple);
    }
    out[start + i] += (sample / norm) * part.gain * envelope(t, part.dur, part.attack, part.curve);
  }
}

function renderNoise(out, sr, part, rand) {
  const start = Math.round(part.at * sr);
  const count = Math.min(out.length - start, Math.round(part.dur * sr));
  let low = 0;
  let floor = 0;
  const hpCoef = part.hp ? 1 - Math.exp((-2 * Math.PI * part.hp) / sr) : 0;
  for (let i = 0; i < count; i++) {
    const t = i / sr;
    const cutoff = part.lp * (part.lpTo / part.lp) ** (t / part.dur);
    const lpCoef = 1 - Math.exp((-2 * Math.PI * Math.min(cutoff, sr * 0.45)) / sr);
    low += lpCoef * (rand() * 2 - 1 - low);
    let sample = low;
    if (hpCoef) {
      floor += hpCoef * (sample - floor);
      sample -= floor;
    }
    out[start + i] += sample * part.gain * 1.8 * envelope(t, part.dur, part.attack, part.curve);
  }
}

const range = (n) => Array.from({ length: n }, (_, i) => i);

// Volumen de todos los sonidos. Las recetas usan cifras pequeñas y aquí
// se suben a un nivel que se oye bien en el altavoz de un móvil.
const LEVEL = 1.6;

// Las recetas. Volúmenes bajos a propósito: el juego se oye a ratos y a
// menudo sin cascos, y nada debería sobresaltar.
const RECIPES = {
  // Tocar a la criatura.
  toque: (s) => s.tone({ dur: 0.11, f: note(74), to: note(81), glide: 0.06, wave: 'soft', gain: 0.3, curve: 1.6 }),
  // Acariciarla.
  caricia: (s) => {
    s.tone({ dur: 0.16, f: note(81), gain: 0.14, curve: 2.2, attack: 0.01 });
    s.tone({ at: 0.07, dur: 0.22, f: note(86), gain: 0.12, curve: 2.4, attack: 0.01 });
  },
  // Cada mordisco al comer.
  bocado: (s) => {
    s.noise({ dur: 0.07, gain: 0.26, lp: 1800, lpTo: 600 });
    s.tone({ dur: 0.08, f: 200, to: 110, gain: 0.32 });
  },
  // Su comida favorita.
  encanta: (s) => {
    [84, 88, 91, 96].forEach((midi, i) => s.bell({ at: i * 0.075, dur: 0.55, f: note(midi), gain: 0.15 }));
  },
  // La comida que no soporta.
  puaj: (s) => {
    s.tone({ dur: 0.28, f: note(64), to: 250, wave: 'soft', gain: 0.24, curve: 0.8, vib: 0.04, vibRate: 9, attack: 0.02 });
    s.tone({ at: 0.26, dur: 0.34, f: note(60), to: 175, wave: 'soft', gain: 0.24, curve: 1.2, vib: 0.05, vibRate: 9, attack: 0.02 });
  },
  // El baño: pompas que suben y un poco de agua.
  burbujas: (s) => {
    const times = range(13).map(() => s.rand() * 1.2).sort((a, b) => a - b);
    for (const at of times) {
      const f = 350 + s.rand() * 500;
      const dur = 0.06 + s.rand() * 0.05;
      s.tone({ at, dur, f, to: f * 2.4, gain: 0.1 + s.rand() * 0.09, attack: 0.003, curve: 1.5 });
    }
    s.noise({ dur: 1.35, gain: 0.03, lp: 3000, hp: 800, attack: 0.2, curve: 1 });
  },
  // Una sola pompa (el juego de las burbujas).
  pompa: (s) => s.tone({ dur: 0.08, f: 420 + s.rand() * 60, to: 1100, gain: 0.22, attack: 0.003, curve: 1.4 }),
  // Medicina o vitaminas.
  cura: (s) => {
    s.bell({ dur: 0.9, f: note(88), gain: 0.19 });
    s.bell({ at: 0.14, dur: 1.1, f: note(95), gain: 0.16 });
  },
  // Un juguete que bota.
  juguete: (s) => s.tone({ dur: 0.5, f: 180, to: 420, glide: 0.08, wave: 'soft', gain: 0.26, curve: 1.5, vib: 0.18, vibRate: 14, vibDecay: 6 }),
  // Un truco: escalera rápida de notas.
  truco: (s) => {
    [79, 81, 84, 86, 88, 91, 93, 96].forEach((midi, i) => s.bell({ at: i * 0.045, dur: 0.35, f: note(midi), gain: 0.1 }));
  },
  // Monedas: recoger, comprar, premios.
  monedas: (s) => {
    s.tone({ dur: 0.08, f: note(83), wave: 'soft', gain: 0.18, curve: 0.6 });
    s.tone({ at: 0.075, dur: 0.38, f: note(88), wave: 'soft', gain: 0.18, curve: 2 });
    s.bell({ at: 0.075, dur: 0.5, f: note(88), gain: 0.07 });
  },
  dormir: (s) => {
    [79, 76, 72].forEach((midi, i) => s.tone({ at: i * 0.2, dur: 0.45, f: note(midi), wave: 'soft', gain: 0.15, curve: 1.6, attack: 0.02 }));
  },
  despertar: (s) => {
    [72, 76, 79, 84].forEach((midi, i) => s.tone({ at: i * 0.09, dur: 0.3, f: note(midi), wave: 'soft', gain: 0.14, curve: 1.8, attack: 0.01 }));
  },
  // El huevo se resquebraja.
  crac: (s) => {
    s.noise({ dur: 0.035, gain: 0.32, lp: 6000, hp: 1500, attack: 0.001, curve: 3 });
    s.tone({ dur: 0.03, f: 1400, to: 900, gain: 0.07 });
  },
  // Nace o termina de evolucionar.
  nace: (s) => {
    [72, 76, 79, 84, 88].forEach((midi, i) => s.bell({ at: i * 0.085, dur: 0.7, f: note(midi), gain: 0.14 }));
    [84, 88, 91].forEach((midi) => s.tone({ at: 0.42, dur: 0.95, f: note(midi), wave: 'soft', gain: 0.06, attack: 0.03, curve: 1.4 }));
    [96, 100, 103].forEach((midi, i) => s.bell({ at: 0.5 + i * 0.05, dur: 0.5, f: note(midi), gain: 0.05 }));
  },
  // Mientras evoluciona: sube y se corta justo al cambiar de forma.
  evoluciona: (s) => {
    s.tone({ dur: 1.3, f: 180, to: 1100, gain: 0.15, attack: 0.4, curve: 0.4, vib: 0.02, vibRate: 7 });
    s.tone({ dur: 1.3, f: 270, to: 1650, gain: 0.07, attack: 0.5, curve: 0.4 });
    s.noise({ dur: 1.3, gain: 0.025, lp: 500, lpTo: 5000, attack: 0.8, curve: 0.3 });
  },
  // Sube el vínculo.
  vinculo: (s) => {
    s.bell({ dur: 0.8, f: note(79), gain: 0.16 });
    s.bell({ at: 0.12, dur: 0.8, f: note(86), gain: 0.14 });
    s.bell({ at: 0.24, dur: 1.1, f: note(91), gain: 0.13 });
  },
  // Despedida.
  adios: (s) => {
    [76, 72, 69].forEach((midi, i) => s.bell({ at: i * 0.32, dur: 1.2, f: note(midi), gain: 0.11 }));
  },
  // Minijuegos.
  punto: (s) => s.tone({ dur: 0.07, f: note(84), to: note(91), glide: 0.04, wave: 'soft', gain: 0.16, curve: 1.5 }),
  fallo: (s) => s.tone({ dur: 0.18, f: 220, to: 150, wave: 'tri', gain: 0.26, curve: 1.5 }),
  salto: (s) => s.tone({ dur: 0.14, f: 330, to: 660, glide: 0.1, gain: 0.18, curve: 1.3 }),
  carta: (s) => s.noise({ dur: 0.06, gain: 0.2, lp: 5000, lpTo: 2000, hp: 900, attack: 0.01 }),
  gana: (s) => {
    [72, 76, 79].forEach((midi, i) => s.tone({ at: i * 0.1, dur: 0.16, f: note(midi), wave: 'soft', gain: 0.16, curve: 1 }));
    [84, 88, 91].forEach((midi) => s.tone({ at: 0.3, dur: 0.85, f: note(midi), wave: 'soft', gain: 0.08, attack: 0.01, curve: 1.6 }));
    s.bell({ at: 0.3, dur: 1, f: note(96), gain: 0.09 });
  },
  pierde: (s) => {
    [67, 66, 65].forEach((midi, i) => s.tone({ at: i * 0.26, dur: 0.3, f: note(midi), wave: 'soft', gain: 0.15, curve: 1.2, vib: 0.015, attack: 0.02 }));
    s.tone({ at: 0.78, dur: 0.6, f: note(64), wave: 'soft', gain: 0.15, curve: 1.2, vib: 0.03, vibRate: 6, attack: 0.02 });
  },
  // Darle calor al huevo.
  calor: (s) => {
    s.tone({ dur: 0.09, f: 240, to: 340, gain: 0.17, curve: 1.5 });
    s.noise({ dur: 0.06, gain: 0.04, lp: 1200 });
  },
  // Las cuatro luces de Eco, de grave a aguda.
  ...Object.fromEntries([72, 76, 79, 84].map((midi, index) => [`nota${index}`, (s) => {
    s.tone({ dur: 0.42, f: note(midi), wave: 'soft', gain: 0.2, attack: 0.006, curve: 1.6 });
    s.bell({ dur: 0.42, f: note(midi), gain: 0.05 });
  }])),
  // Hacer la foto de la tarjeta.
  foto: (s) => {
    s.noise({ dur: 0.025, gain: 0.28, lp: 7000, hp: 2000 });
    s.noise({ at: 0.07, dur: 0.04, gain: 0.22, lp: 5000, hp: 1200 });
  },
};

export const SOUND_NAMES = Object.keys(RECIPES);

// Muestras del sonido, mono y entre -1 y 1, o null si no existe.
export function renderSound(name, sampleRate = 44100) {
  const recipe = RECIPES[name];
  if (!recipe) return null;
  const rand = mulberry32(seedOf(name));
  const s = recorder(rand);
  recipe(s);
  const seconds = Math.max(...s.parts.map((part) => part.at + part.dur)) + 0.01;
  const out = new Float32Array(Math.ceil(seconds * sampleRate));
  for (const part of s.parts) {
    if (part.kind === 'tone') renderTone(out, sampleRate, part);
    else renderNoise(out, sampleRate, part, rand);
  }
  // Por si las piezas se suman de más: nunca pasar de 0,9.
  let peak = 0;
  for (const sample of out) peak = Math.max(peak, Math.abs(sample) * LEVEL);
  const scale = peak > 0.9 ? (0.9 / peak) * LEVEL : LEVEL;
  for (let i = 0; i < out.length; i++) out[i] *= scale;
  return out;
}
