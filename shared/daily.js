// Misiones del día y racha. Cada día del jugador (a su medianoche) trae
// tres encargos, uno fácil, uno medio y uno difícil. Recoger los tres
// suma un día a la racha, que da un premio extra cada vez mayor.
//
// También viven aquí las cuentas de calendario en la zona horaria del
// jugador, porque las usan las misiones y los avisos.

import { createRng } from './rng.js';

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

export const DEFAULT_TIME_ZONE = 'Europe/Madrid';

// Monedas por misión según su dificultad.
export const MISSION_REWARDS = [15, 25, 40];

// `track` es lo que cuenta para avanzarla. `needsToy`: solo sale si el
// jugador tiene algún juguete.
export const MISSIONS = [
  { id: 'comer', tier: 0, track: 'feed', goal: 3, text: 'Dale de comer 3 veces' },
  { id: 'mimos', tier: 0, track: 'pet', goal: 10, text: 'Hazle 10 mimos' },
  { id: 'bano', tier: 0, track: 'bathe', goal: 1, text: 'Dale un baño' },
  { id: 'hucha', tier: 0, track: 'collect', goal: 1, text: 'Recoge las monedas de la hucha' },

  { id: 'partidas', tier: 1, track: 'play', goal: 3, text: 'Juega 3 minijuegos' },
  { id: 'victorias', tier: 1, track: 'win', goal: 2, text: 'Gana 2 minijuegos' },
  { id: 'juguete', tier: 1, track: 'toy', goal: 2, text: 'Juega 2 veces con un juguete', needsToy: true },
  { id: 'siesta', tier: 1, track: 'woke', goal: 1, text: 'Déjala dormir hasta que se despierte sola' },

  { id: 'reto', tier: 2, track: 'winHard', goal: 1, text: 'Gana un minijuego en Normal o Difícil' },
  { id: 'feliz', tier: 2, track: 'happy', goal: 1, text: 'Consigue que esté feliz' },
  { id: 'favorita', tier: 2, track: 'favorite', goal: 1, text: 'Dale su comida favorita' },
];

const BY_ID = new Map(MISSIONS.map((mission) => [mission.id, mission]));

export function getMission(id) {
  return BY_ID.get(id) ?? null;
}

// Las tres misiones de un día. La semilla sale del jugador y del día,
// así que repetir la pregunta da las mismas.
export function rollMissions(seed, { hasToy = false } = {}) {
  const rng = createRng(seed);
  return MISSION_REWARDS.map((reward, tier) => {
    const pool = MISSIONS.filter((mission) => mission.tier === tier && (!mission.needsToy || hasToy));
    const mission = rng.pick(pool);
    return { id: mission.id, goal: mission.goal, progress: 0, reward, claimed: false };
  });
}

// Suma progreso a las misiones que cuentan `kind`. Modifica la lista y
// devuelve las que se acaban de cumplir.
export function trackMissions(list, kind, amount = 1) {
  const done = [];
  for (const entry of list) {
    const mission = getMission(entry.id);
    if (!mission || mission.track !== kind || entry.progress >= entry.goal) continue;
    entry.progress = Math.min(entry.goal, entry.progress + amount);
    if (entry.progress >= entry.goal) done.push(entry.id);
  }
  return done;
}

// Premio extra por recoger las tres: 20 el primer día y 10 más por cada
// día seguido, hasta 80 a partir del séptimo.
export function streakBonus(streak) {
  return 20 + 10 * (Math.min(Math.max(streak, 1), 7) - 1);
}

// La racha sigue viva si el último día completo fue hoy o ayer.
export function currentStreak(streak, streakDay, today) {
  return streakDay === today || streakDay === dayAfter(today, -1) ? streak : 0;
}

// Racha que queda al completar hoy.
export function nextStreak(streak, streakDay, today) {
  if (streakDay === today) return streak;
  return streakDay === dayAfter(today, -1) ? streak + 1 : 1;
}

// ── Calendario en la zona del jugador ──

const formatters = new Map();

function formatter(timeZone) {
  let format = formatters.get(timeZone);
  if (!format) {
    format = new Intl.DateTimeFormat('en-US', {
      timeZone, hourCycle: 'h23',
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
    formatters.set(timeZone, format);
  }
  return format;
}

export function isTimeZone(value) {
  if (typeof value !== 'string' || !value || value.length > 64) return false;
  try {
    formatter(value);
    return true;
  } catch {
    return false;
  }
}

function clock(now, timeZone) {
  const zone = isTimeZone(timeZone) ? timeZone : DEFAULT_TIME_ZONE;
  const parts = {};
  for (const { type, value } of formatter(zone).formatToParts(now)) parts[type] = value;
  return parts;
}

// Fecha local como 'AAAA-MM-DD'. Se compara bien como texto.
export function localDay(now, timeZone) {
  const p = clock(now, timeZone);
  return `${p.year}-${p.month}-${p.day}`;
}

// Hora local con decimales: 20.5 son las ocho y media de la tarde.
export function localHour(now, timeZone) {
  const p = clock(now, timeZone);
  return Number(p.hour) + Number(p.minute) / 60;
}

// Cuándo empieza el día siguiente del jugador. En los días de cambio de
// hora se desvía una hora como mucho.
export function nextDayAt(now, timeZone) {
  const p = clock(now, timeZone);
  const elapsed = ((Number(p.hour) * 60 + Number(p.minute)) * 60 + Number(p.second)) * 1000 + (now % 1000);
  return now - elapsed + DAY;
}

// Suma (o resta) días a una fecha 'AAAA-MM-DD'.
export function dayAfter(day, days = 1) {
  return new Date(Date.parse(`${day}T00:00:00Z`) + days * DAY).toISOString().slice(0, 10);
}
