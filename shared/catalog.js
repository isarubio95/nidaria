// Catálogo de la tienda. Los efectos se suman a las necesidades (0 a 100).

export const CATEGORIES = [
  { id: 'comida', name: 'Comida' },
  { id: 'cuidado', name: 'Cuidado' },
  { id: 'juguete', name: 'Juguetes' },
  { id: 'ropa', name: 'Ropa' },
  { id: 'casa', name: 'Casa' },
];

export const SLOTS = [
  { id: 'cabeza', name: 'Cabeza' },
  { id: 'cara', name: 'Cara' },
  { id: 'cuello', name: 'Cuello' },
];

// Sitios de la habitación que se pueden decorar. La habitación es del
// jugador, no de la criatura: se queda igual cuando llega otra. `base` es
// lo que hay si no se ha puesto nada (null: el sitio queda vacío).
export const SPOTS = [
  { id: 'pared', name: 'Pared', base: 'Tablas azules' },
  { id: 'alfombra', name: 'Alfombra', base: 'Alfombra marinera' },
  { id: 'rincon', name: 'Rincón', base: 'Planta' },
  { id: 'ventana', name: 'Ventana', base: null },
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
const decor = (id, name, price, spot, blurb) => ({
  id, name, price, kind: 'casa', spot, blurb, reusable: true,
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

  // La decoración es lo más caro de la tienda: es la meta a largo plazo.
  decor('papel-rayas', 'Rayas de menta', 180, 'pared', 'Rayas finas, frescas y tranquilas.'),
  decor('papel-vichy', 'Cuadros', 240, 'pared', 'Como un mantel de merienda.'),
  decor('papel-flores', 'Florecitas', 300, 'pared', 'Margaritas por todas partes.'),
  decor('papel-olas', 'Olas', 360, 'pared', 'Para soñar con el mar.'),
  decor('papel-estrellas', 'Noche de estrellas', 450, 'pared', 'Lila, con estrellas de oro.'),

  decor('alfombra-menta', 'Alfombra menta', 180, 'alfombra', 'Redonda y suave.'),
  decor('alfombra-flor', 'Alfombra flor', 300, 'alfombra', 'Con pétalos alrededor.'),
  decor('alfombra-arcoiris', 'Alfombra arcoíris', 380, 'alfombra', 'Un color en cada vuelta.'),
  decor('alfombra-nube', 'Alfombra nube', 480, 'alfombra', 'Mullida como una nube.'),

  // Junto a la ventana caben cosas altas; en el rincón, cosas bajas, que
  // en el móvil quedan por debajo de los botones de la derecha.
  decor('cactus', 'Cactus', 200, 'ventana', 'No hay que regarlo casi nunca.'),
  decor('girasol', 'Girasol', 280, 'ventana', 'Siempre mira hacia la luz.'),
  decor('lampara', 'Lámpara de pie', 420, 'ventana', 'Luz cálida para leer.'),
  decor('pecera', 'Pecera', 650, 'ventana', 'Con un pez que saluda.'),

  decor('cojin', 'Cojín grande', 160, 'rincon', 'Para tumbarse a descansar.'),
  decor('mesita', 'Mesita con flores', 300, 'rincon', 'Un jarrón con tulipanes.'),
  decor('baul', 'Baúl de juguetes', 450, 'rincon', 'Siempre se queda algo fuera.'),
  decor('camita', 'Camita', 600, 'rincon', 'Un nido blandito con su manta.'),
];

const BY_ID = new Map(ITEMS.map((item) => [item.id, item]));

export function getItem(id) {
  return BY_ID.get(id) ?? null;
}

export function itemsOfKind(kind) {
  return ITEMS.filter((item) => item.kind === kind);
}

// Lo que hay puesto en cada sitio de la habitación (null: lo de base).
// Limpia lo guardado: lo que ya no exista o no vaya ahí, fuera.
export function cleanDecor(raw) {
  const decor = {};
  for (const spot of SPOTS) {
    const item = getItem(raw?.[spot.id]);
    decor[spot.id] = item && item.kind === 'casa' && item.spot === spot.id ? item.id : null;
  }
  return decor;
}

// Pone un artículo en su sitio, o deja el sitio como venía con null.
// Devuelve { ok, error? }; quien llama comprueba que sea del jugador.
export function placeDecor(decor, spot, itemId) {
  if (!SPOTS.some((entry) => entry.id === spot)) return { ok: false, error: 'Ese sitio no existe.' };
  if (itemId === null) {
    decor[spot] = null;
    return { ok: true };
  }
  const item = getItem(itemId);
  if (!item || item.kind !== 'casa' || item.spot !== spot) return { ok: false, error: 'Eso no va ahí.' };
  decor[spot] = item.id;
  return { ok: true };
}

export const STARTING_COINS = 60;
export const STARTING_INVENTORY = { manzana: 3, pan: 2, galleta: 1 };

export const TOY_COOLDOWN_MS = 20 * 60 * 1000;
export const BATH_COOLDOWN_MS = 5 * 60 * 1000;
export const MAX_STACK = 99;
