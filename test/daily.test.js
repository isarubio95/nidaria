import test from 'node:test';
import assert from 'node:assert/strict';
import {
  rollMissions, trackMissions, getMission, streakBonus, currentStreak, nextStreak,
  localDay, localHour, nextDayAt, dayAfter, isTimeZone, MISSIONS, MISSION_REWARDS,
} from '../shared/daily.js';
import { alertsFor, isQuietHour, cleanPrefs, DEFAULT_PREFS } from '../server/game/alerts.js';
import { createGenes } from '../shared/dna.js';
import { simulate, newCreature } from '../shared/needs.js';
import { TICKETS_MAX, TICKET_REGEN_MS } from '../shared/economy.js';

const HOUR = 60 * 60 * 1000;
// 15 de enero de 2027, 10:30 en Madrid (09:30 UTC).
const T0 = Date.UTC(2027, 0, 15, 9, 30);

test('las misiones del día salen una por dificultad y siempre las mismas', () => {
  const a = rollMissions(42);
  assert.deepEqual(a, rollMissions(42));
  assert.equal(a.length, 3);
  a.forEach((entry, tier) => {
    assert.equal(getMission(entry.id).tier, tier);
    assert.equal(entry.reward, MISSION_REWARDS[tier]);
  });
  // Sin juguetes nunca sale la de jugar con uno.
  for (let seed = 0; seed < 300; seed++) {
    assert.ok(!rollMissions(seed).some((entry) => entry.id === 'juguete'));
  }
  assert.ok(Array.from({ length: 300 }, (_, seed) => rollMissions(seed, { hasToy: true })).some((list) => list.some((entry) => entry.id === 'juguete')));
  assert.equal(new Set(MISSIONS.map((m) => m.id)).size, MISSIONS.length);
});

test('el progreso de una misión se queda en su objetivo', () => {
  const list = [
    { id: 'comer', goal: 3, progress: 0, reward: 15, claimed: false },
    { id: 'partidas', goal: 3, progress: 0, reward: 25, claimed: false },
  ];
  assert.deepEqual(trackMissions(list, 'feed'), []);
  assert.deepEqual(trackMissions(list, 'feed', 5), ['comer']);
  assert.equal(list[0].progress, 3);
  assert.deepEqual(trackMissions(list, 'feed'), []);
  assert.equal(list[1].progress, 0);
});

test('la racha crece día a día y se rompe si se salta uno', () => {
  assert.equal(nextStreak(0, null, '2027-01-15'), 1);
  assert.equal(nextStreak(4, '2027-01-14', '2027-01-15'), 5);
  assert.equal(nextStreak(4, '2027-01-13', '2027-01-15'), 1);
  assert.equal(nextStreak(4, '2027-01-15', '2027-01-15'), 4);
  assert.equal(currentStreak(4, '2027-01-14', '2027-01-15'), 4);
  assert.equal(currentStreak(4, '2027-01-13', '2027-01-15'), 0);
  assert.equal(streakBonus(1), 20);
  assert.equal(streakBonus(7), 80);
  assert.equal(streakBonus(30), 80);
});

test('el calendario va en la zona horaria del jugador', () => {
  assert.equal(localDay(T0, 'Europe/Madrid'), '2027-01-15');
  // En Ciudad de México aún es el 15 a las 3:30; en Tokio, el 15 por la noche.
  assert.equal(localDay(T0, 'America/Mexico_City'), '2027-01-15');
  assert.equal(localDay(Date.UTC(2027, 0, 15, 23, 30), 'Europe/Madrid'), '2027-01-16');
  assert.equal(localHour(T0, 'Europe/Madrid'), 10.5);
  assert.equal(nextDayAt(T0, 'Europe/Madrid'), Date.UTC(2027, 0, 15, 23, 0));
  assert.equal(dayAfter('2026-12-31'), '2027-01-01');
  assert.equal(dayAfter('2027-03-01', -1), '2027-02-28');
  assert.ok(isTimeZone('America/Bogota'));
  assert.ok(!isTimeZone('Marte/Olimpo'));
  assert.ok(!isTimeZone(42));
  // Una zona desconocida no rompe nada: se usa la de por defecto.
  assert.equal(localDay(T0, 'Marte/Olimpo'), '2027-01-15');
});

function pet(hoursAlone, seed = 3) {
  const egg = newCreature({ id: 'c1', name: 'Pipo', genes: createGenes(seed, 'pelusa'), now: T0 });
  const saved = simulate(egg, T0 + 60 * 1000).creature;
  const now = T0 + 60 * 1000 + hoursAlone * HOUR;
  return { saved, live: simulate(saved, now).creature, now };
}

const user = { tickets: TICKETS_MAX, tickets_at: T0 };
const ask = (extra) => alertsFor({ user, daily: null, timeZone: 'Europe/Madrid', prefs: DEFAULT_PREFS, ...extra });

test('avisos: necesidades, evolución y muerte, de más a menos urgente', () => {
  const fine = pet(0.1);
  assert.deepEqual(ask(fine), []);

  const hungry = pet(3);
  const keys = ask(hungry).map((alert) => alert.key);
  assert.ok(keys.includes('need:hunger:c1'), keys.join());
  // En tres horas también ha evolucionado de cría a joven.
  assert.ok(keys.includes(`form:c1:${hungry.live.formId}`));
  assert.ok(keys.indexOf('need:hunger:c1') < keys.indexOf(`form:c1:${hungry.live.formId}`));

  const gone = pet(24 * 5);
  const dead = ask(gone);
  assert.equal(dead.length, 1);
  assert.equal(dead[0].key, 'died:c1');
  // Una vez visto (guardado muerto), ya no hay nada que avisar.
  assert.deepEqual(ask({ ...gone, saved: gone.live }), []);

  const noCare = alertsFor({ ...hungry, user, daily: null, timeZone: 'Europe/Madrid', prefs: { ...DEFAULT_PREFS, care: false, growth: false } });
  assert.deepEqual(noCare, []);
});

test('avisos: racha en peligro por la tarde y energía de juego recargada', () => {
  const calm = pet(0.1);
  const evening = Date.UTC(2027, 0, 15, 20, 0); // 21:00 en Madrid
  const daily = { streak: 3, streak_day: '2027-01-14' };
  const atRisk = ask({ ...calm, now: evening, daily });
  assert.equal(atRisk[0].key, 'streak:2027-01-15');
  assert.match(atRisk[0].title, /3 días/);
  assert.deepEqual(ask({ ...calm, daily }), [], 'por la mañana todavía no se avisa');
  assert.deepEqual(ask({ ...calm, now: evening, daily: { streak: 3, streak_day: '2027-01-15' } }), []);

  const drained = { tickets: 0, tickets_at: calm.now - TICKETS_MAX * TICKET_REGEN_MS };
  const refill = alertsFor({ ...calm, user: drained, daily: null, timeZone: 'Europe/Madrid', prefs: DEFAULT_PREFS });
  assert.equal(refill[0].group, 'games');
  const half = { tickets: 0, tickets_at: calm.now - 2 * TICKET_REGEN_MS };
  assert.deepEqual(alertsFor({ ...calm, user: half, daily: null, timeZone: 'Europe/Madrid', prefs: DEFAULT_PREFS }), []);
});

test('avisos: horas de silencio y preferencias', () => {
  assert.ok(isQuietHour(23.5));
  assert.ok(isQuietHour(3));
  assert.ok(!isQuietHour(8));
  assert.ok(!isQuietHour(22.9));
  assert.deepEqual(cleanPrefs({ care: false, quiet: 'no', otra: true }), { ...DEFAULT_PREFS, care: false });
  assert.deepEqual(cleanPrefs({ games: false }, { ...DEFAULT_PREFS, care: false }), { ...DEFAULT_PREFS, care: false, games: false });
});
