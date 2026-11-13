// Amigos, visitas y regalos: el primer paso hacia el mundo compartido.
// Cada jugador tiene un código para que otros le añadan. Añadir manda una
// solicitud que el otro acepta; entre amigos se visitan las habitaciones
// y se dejan un regalo al día sacado de lo que cada uno tiene.

import { getItem } from './catalog.js';

// Lo más que caben en la lista y las solicitudes que se pueden tener
// enviadas a la vez sin respuesta.
export const MAX_FRIENDS = 50;
export const MAX_OUTGOING = 20;

// Regalos sin abrir que se le pueden acumular a alguien.
export const MAX_UNOPENED = 30;

// Se regala comida y cuidados, de lo que uno tiene. Lo demás (juguetes,
// ropa, casa) se tiene una sola vez y no se puede quedar sin ello.
export const GIFT_KINDS = ['comida', 'cuidado'];

export function canGift(itemId) {
  const item = getItem(itemId);
  return Boolean(item && GIFT_KINDS.includes(item.kind));
}

// Código de amigo: 8 caracteres del alfabeto de Crockford (sin I, L, O ni
// U), en dos grupos: «K7QM-4XPA». Es el mismo alfabeto que el código de
// recuperación, pero este se comparte; no da acceso a nada.
export const CODE_ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
export const CODE_LENGTH = 8;

// Lo que escribe el jugador, sin guiones ni espacios y en mayúsculas. Las
// letras que se confunden con cifras cuentan como esas cifras. null si no
// tiene la forma de un código.
export function normalizeFriendCode(input) {
  if (typeof input !== 'string') return null;
  const clean = input.toUpperCase().replace(/[\s-]/g, '').replace(/O/g, '0').replace(/[IL]/g, '1');
  if (clean.length !== CODE_LENGTH) return null;
  for (const char of clean) if (!CODE_ALPHABET.includes(char)) return null;
  return clean;
}

// «K7QM4XPA» -> «K7QM-4XPA».
export const formatFriendCode = (code) => (code ? `${code.slice(0, 4)}-${code.slice(4)}` : '');
