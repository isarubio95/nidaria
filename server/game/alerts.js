// Qué merece un aviso al móvil. Función pura: recibe la criatura tal como
// está guardada (`saved`, lo último que vio el jugador) y simulada hasta
// ahora (`live`), y devuelve los avisos vigentes de más a menos urgente.
// Cada aviso lleva una clave estable: mientras siga vigente no se repite.

import { CRITICAL, NEEDS } from '../../shared/needs.js';
import { ticketsNow, TICKETS_MAX } from '../../shared/economy.js';
import { localDay, localHour, dayAfter } from '../../shared/daily.js';

// Sin avisos de 23:00 a 8:00 (si el jugador no lo desactiva). Lo que
// quede pendiente sale por la mañana si sigue haciendo falta.
export const QUIET_FROM = 23;
export const QUIET_TO = 8;
export const isQuietHour = (hour) => hour >= QUIET_FROM || hour < QUIET_TO;

// A partir de esta hora se avisa de que la racha se pierde hoy.
export const STREAK_REMINDER_HOUR = 20;

// Grupos que el jugador puede apagar por separado.
export const ALERT_GROUPS = ['care', 'growth', 'streak', 'games', 'quiet'];
export const DEFAULT_PREFS = { care: true, growth: true, streak: true, games: true, quiet: true };

const NEED_TEXT = {
  hunger: ['tiene mucha hambre', 'Dale algo de comer.'],
  energy: ['está agotada', 'Necesita dormir un rato.'],
  hygiene: ['necesita un baño', 'Está muy sucia y empieza a encontrarse mal.'],
  fun: ['se aburre muchísimo', 'Juega un rato con ella.'],
};

const days = (n) => `${n} ${n === 1 ? 'día' : 'días'}`;

export function alertsFor({ saved, live, user, daily, now, timeZone, prefs, scale = 1 }) {
  const list = [];
  const add = (group, key, title, body) => {
    if (prefs[group]) list.push({ group, key, title, body });
  };

  if (saved && live) {
    const name = live.name;
    if (!live.alive && saved.alive) {
      add('care', `died:${live.id}`, `${name} se ha ido`, live.deathCause === 'vejez'
        ? 'Ha tenido una vida larga. Entra para despedirte.'
        : 'Entra para despedirte y elegir un huevo nuevo.');
    } else if (live.alive && live.stage !== 'huevo') {
      if (live.health < 30) add('care', `weak:${live.id}`, `${name} está muy débil`, 'Si no la cuidas pronto, puede morir.');
      if (live.sick) add('care', `sick:${live.id}`, `${name} se ha puesto enferma`, 'Dale medicina para que se recupere.');
      for (const need of NEEDS) {
        if (need === 'energy' && live.asleep) continue;
        if (live[need] < CRITICAL) add('care', `need:${need}:${live.id}`, `${name} ${NEED_TEXT[need][0]}`, NEED_TEXT[need][1]);
      }
      if (live.formId !== saved.formId) {
        if (saved.stage === 'huevo') add('growth', `form:${live.id}:${live.formId}`, `¡${name} ha nacido!`, 'Entra a conocerla.');
        else add('growth', `form:${live.id}:${live.formId}`, `${name} está cambiando`, 'Entra a ver en qué se ha convertido.');
      }
    }
  }

  if (daily && daily.streak > 0) {
    const today = localDay(now, timeZone);
    const atRisk = daily.streak_day === dayAfter(today, -1);
    if (atRisk && localHour(now, timeZone) >= STREAK_REMINDER_HOUR) {
      add('streak', `streak:${today}`, `Tu racha de ${days(daily.streak)} está en juego`, 'Completa las misiones de hoy antes de medianoche.');
    }
  }

  // Solo tras gastar casi toda la energía de juego, para no avisar después
  // de cada partida suelta.
  if (live?.alive && user.tickets <= 1) {
    const tickets = ticketsNow(user.tickets, user.tickets_at, now, scale);
    if (tickets.tickets >= TICKETS_MAX) {
      add('games', `tickets:${user.tickets_at}`, 'Ya puedes volver a jugar', 'Tienes la energía de juego al máximo.');
    }
  }

  return list;
}

export function cleanPrefs(input, base = DEFAULT_PREFS) {
  const prefs = { ...DEFAULT_PREFS, ...base };
  if (input && typeof input === 'object') {
    for (const key of ALERT_GROUPS) {
      if (typeof input[key] === 'boolean') prefs[key] = input[key];
    }
  }
  return prefs;
}
