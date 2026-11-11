// Lo que dice la criatura. Frases cortas, en primera persona.

import { LOW } from '/shared/needs.js';

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

const pick = (list) => list[Math.floor(Math.random() * list.length)];

export const line = (kind) => pick(LINES[kind] ?? LINES.calm);

// Frase espontánea: primero lo que más le falta, luego su carácter.
export function idleLine(creature, mood) {
  if (creature.sick) return pick(LINES.sick);
  const worst = ['hunger', 'energy', 'hygiene', 'fun'].sort((a, b) => creature[a] - creature[b])[0];
  if (creature[worst] < LOW) return pick(LINES[worst]);
  if (Math.random() < 0.5) return pick(TRAIT_LINES[pick(creature.genes.traits)]);
  return pick(mood === 'feliz' ? LINES.happy : LINES.calm);
}
