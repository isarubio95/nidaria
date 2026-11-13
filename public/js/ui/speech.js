// Lo que dice la criatura. Frases cortas, en primera persona.

import { LOW } from '/shared/needs.js';
import { BOND_LINES, bondOf, bondLevel } from '/shared/bond.js';
import { currentWeather } from '../weather.js';

const LINES = {
  hunger: ['Tengo hambre.', '¿Hay algo de comer?', 'Me suenan las tripas.'],
  energy: ['Qué sueño…', 'Se me cierran los ojos.', 'Quiero dormir.'],
  hygiene: ['Necesito un baño.', 'Me pica todo.', 'Huelo raro.'],
  fun: ['Me aburro.', '¿Jugamos a algo?', 'No hay nada que hacer.'],
  sick: ['No me encuentro bien.', 'Me duele la tripa.', 'Tengo frío.'],
  happy: ['¡Qué bien estoy!', 'Hoy es un buen día.', 'Me gusta estar aquí.'],
  calm: ['Hola.', '¿Qué hacemos?', 'Mmm.'],
  favorita: ['¡Mi favorita!', '¡Qué rico!'],
  asco: ['Puaj.', 'Esto no me gusta.'],
  come: ['Ñam.', 'Gracias.', 'Qué bueno.'],
  caricia: ['Ji, ji.', 'Más.', 'Qué gustito.'],
  bano: ['¡Burbujas!', 'Qué limpita estoy.'],
  cura: ['Ya estoy mejor.', 'Gracias.'],
  duerme: ['Buenas noches.'],
  despierta: ['¡Buenos días!'],
  grunona: ['Cinco minutos más…'],
  juega: ['¡Otra vez!', '¡Yupi!'],
  gana: ['¡Hemos ganado!', '¡Qué bien jugamos!'],
  pierde: ['Otra vez será.', 'Casi.'],
  nace: ['¡Hola!'],
};

const TRAIT_LINES = {
  gloton: ['¿Y la merienda?', 'Pienso en tarta.'],
  dormilon: ['Echaría una siesta.'],
  travieso: ['Tengo una idea…', '¿Y si lo tiramos?'],
  mimoso: ['¿Me haces mimos?'],
  presumido: ['¿Estoy guapa?'],
  fuerte: ['¡Mira qué fuerte soy!'],
  timido: ['…hola.'],
  curioso: ['¿Qué es eso?', '¿Y eso para qué sirve?'],
};

// Lo que comenta de la hora del jugador y del tiempo que hace fuera.
const HOUR_LINES = {
  manana: ['¡Buenos días!', 'Qué bien se ha dormido.'],
  noche: ['Qué tarde es…', 'Ya es de noche.', '¿Tú no duermes?'],
  sueno: ['¿Nos vamos a dormir?', 'Es hora de dormir, ¿no?'],
};
const WEATHER_LINES = {
  despejado: ['Hace un día precioso.', 'Mira qué sol.'],
  estrellas: ['Hay muchas estrellas.', 'Qué noche tan clara.'],
  nubes: ['Hay nubes con formas.', 'Esa nube parece un pez.'],
  cubierto: ['Qué día más gris.'],
  niebla: ['No se ve nada por la ventana.'],
  lluvia: ['Está lloviendo.', 'Me gusta el ruido de la lluvia.'],
  tormenta: ['¿Has oído ese trueno?', 'Menos mal que estamos en casa.'],
  nieve: ['¡Está nevando!', 'Quiero tocar la nieve.'],
  calor: ['Qué calor hace hoy.'],
  frio: ['Brrr. Fuera hace frío.'],
};

const pick = (list) => list[Math.floor(Math.random() * list.length)];

function contextLines(creature, date = new Date()) {
  const hour = date.getHours();
  const night = hour >= 23 || hour < 6;
  const lines = [];
  if (night) lines.push(...(creature.energy < 90 ? HOUR_LINES.sueno : HOUR_LINES.noche));
  else if (hour >= 7 && hour < 11) lines.push(...HOUR_LINES.manana);
  const weather = currentWeather();
  if (weather) {
    const day = hour >= 8 && hour < 20;
    const kind = weather.kind === 'despejado' && !day ? 'estrellas' : weather.kind;
    lines.push(...(WEATHER_LINES[kind] ?? []));
    if (weather.temp >= 30) lines.push(...WEATHER_LINES.calor);
    if (weather.temp <= 3) lines.push(...WEATHER_LINES.frio);
  }
  return lines;
}

export const line = (kind) => pick(LINES[kind] ?? LINES.calm);

// Lo que dice la criatura de un amigo cuando vas de visita. `visitor` es
// quien la visita.
const VISIT_LINES = ['¡Una visita!', '¿Vienes a jugar?', '¿Has venido a verme?', '¡Hola, {visitor}!'];

export function visitLine(creature, visitor) {
  if (creature.sick) return pick(LINES.sick);
  const worst = ['hunger', 'energy', 'hygiene', 'fun'].sort((a, b) => creature[a] - creature[b])[0];
  if (creature[worst] < LOW) return pick(LINES[worst]);
  if (creature.genes.traits.includes('timido')) return pick(TRAIT_LINES.timido);
  return pick(VISIT_LINES).replace('{visitor}', visitor ?? '');
}

// Lo que dice por el cariño que te tiene, de su nivel y los anteriores.
export function bondLine(creature, player, level = bondLevel(bondOf(creature))) {
  const lines = [];
  for (let i = 1; i <= level; i++) lines.push(...(BOND_LINES[i] ?? []));
  return lines.length ? pick(lines).replace('{player}', player ?? '') : null;
}

// Frase espontánea: primero lo que más le falta, luego el cariño que te
// tiene, lo que pasa fuera y su carácter. `player` es quien la cuida.
export function idleLine(creature, mood, player) {
  if (creature.sick) return pick(LINES.sick);
  const worst = ['hunger', 'energy', 'hygiene', 'fun'].sort((a, b) => creature[a] - creature[b])[0];
  if (creature[worst] < LOW) return pick(LINES[worst]);
  const fond = bondLine(creature, player);
  if (fond && Math.random() < 0.3) return fond;
  const context = contextLines(creature);
  if (context.length && Math.random() < 0.35) return pick(context);
  if (Math.random() < 0.5) return pick(TRAIT_LINES[pick(creature.genes.traits)]);
  return pick(mood === 'feliz' ? LINES.happy : LINES.calm);
}
