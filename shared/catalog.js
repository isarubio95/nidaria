// Catálogo de la tienda. Los efectos se suman a las necesidades (0 a 100).

export const CATEGORIES = [
  { id: 'comida', name: 'Comida' },
  { id: 'cuidado', name: 'Cuidado' },
  { id: 'juguete', name: 'Juguetes' },
  { id: 'ropa', name: 'Ropa' },
];

export const SLOTS = [
  { id: 'cabeza', name: 'Cabeza' },
  { id: 'cara', name: 'Cara' },
  { id: 'cuello', name: 'Cuello' },
];

const food = (id, name, price, hunger, extra = {}) => ({
  id, name, price, kind: 'comida', hunger, fun: 0, health: 0, sweet: false, ...extra,
});
const care = (id, name, price, extra) => ({ id, name, price, kind: 'cuidado', ...extra });
const toy = (id, name, price, fun, energy, blurb) => ({
  id, name, price, kind: 'juguete', fun, energy, blurb, reusable: true,
});
const wear = (id, name, price, slot, blurb) => ({
  id, name, price, kind: 'ropa', slot, blurb, reusable: true,
});

export const ITEMS = [
  food('manzana', 'Manzana', 4, 14, { health: 1, blurb: 'Crujiente y sana.' }),
  food('zanahoria', 'Zanahoria', 4, 12, { health: 2, blurb: 'Buena para todo.' }),
  food('pan', 'Pan', 6, 20, { blurb: 'Recién hecho.' }),
  food('arroz', 'Bol de arroz', 10, 30, { blurb: 'Llena de verdad.' }),
  food('sopa', 'Sopa', 14, 26, { health: 4, blurb: 'Calentita; sienta bien.' }),
  food('pescado', 'Pescado', 16, 34, { health: 1, blurb: 'A la plancha.' }),
  food('galleta', 'Galleta', 5, 7, { fun: 6, sweet: true, blurb: 'Un capricho pequeño.' }),
  food('helado', 'Helado', 12, 8, { fun: 16, sweet: true, blurb: 'Dulce y frío.' }),
  food('tarta', 'Tarta', 18, 18, { fun: 14, sweet: true, blurb: 'Para celebrar.' }),
  food('festin', 'Festín', 40, 60, { fun: 10, health: 5, blurb: 'De todo un poco.' }),

  care('medicina', 'Medicina', 30, { cures: true, health: 15, blurb: 'Cura cuando está enferma.' }),
  care('vitaminas', 'Vitaminas', 22, { health: 12, blurb: 'Sube la salud.' }),
  care('jabon', 'Jabón de burbujas', 10, { bath: true, fun: 6, blurb: 'Baño completo y divertido.' }),

  toy('pelota', 'Pelota', 60, 16, 6, 'Bota mucho.'),
  toy('peonza', 'Peonza', 90, 20, 5, 'Gira sin parar.'),
  toy('peluche', 'Peluche', 110, 14, 0, 'Para abrazar; no cansa.'),
  toy('cometa', 'Cometa', 140, 28, 9, 'Vuela alto.'),

  wear('lazo', 'Lazo', 70, 'cabeza', 'Un lazo grande.'),
  wear('gorro', 'Gorro de lana', 80, 'cabeza', 'Con pompón.'),
  wear('gorra', 'Gorra', 100, 'cabeza', 'Con visera.'),
  wear('paja', 'Sombrero de paja', 120, 'cabeza', 'Para el sol.'),
  wear('flor', 'Flor', 90, 'cabeza', 'Una margarita.'),
  wear('corona', 'Corona', 400, 'cabeza', 'Para criaturas con clase.'),
  wear('gafas', 'Gafas redondas', 110, 'cara', 'De pasta fina.'),
  wear('gafasol', 'Gafas de sol', 150, 'cara', 'Muy oscuras.'),
  wear('bigote', 'Bigote', 95, 'cara', 'De quita y pon.'),
  wear('panuelo', 'Pañuelo', 60, 'cuello', 'Atado al cuello.'),
  wear('pajarita', 'Pajarita', 75, 'cuello', 'Elegante.'),
  wear('bufanda', 'Bufanda', 85, 'cuello', 'Larga y a rayas.'),
  wear('cascabel', 'Cascabel', 100, 'cuello', 'Suena al andar.'),
];

const BY_ID = new Map(ITEMS.map((item) => [item.id, item]));

export function getItem(id) {
  return BY_ID.get(id) ?? null;
}

export function itemsOfKind(kind) {
  return ITEMS.filter((item) => item.kind === kind);
}

export const STARTING_COINS = 60;
export const STARTING_INVENTORY = { manzana: 3, pan: 2, galleta: 1 };

export const TOY_COOLDOWN_MS = 20 * 60 * 1000;
export const BATH_COOLDOWN_MS = 5 * 60 * 1000;
export const MAX_STACK = 99;
