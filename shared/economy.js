// Economía: dinero que llega con el tiempo, energía de juego que limita
// las partidas y recompensa de los minijuegos según la dificultad.

export const INCOME_PER_HOUR = 6;
export const INCOME_CAP_HOURS = 12;
export const INCOME_CAP = INCOME_PER_HOUR * INCOME_CAP_HOURS;

export const TICKETS_MAX = 5;
export const TICKET_REGEN_MS = 15 * 60 * 1000;

const HOUR = 60 * 60 * 1000;

export function pendingIncome(incomeAt, now, scale = 1) {
  const hours = Math.max(0, ((now - incomeAt) * scale) / HOUR);
  return Math.min(INCOME_CAP, Math.floor(hours * INCOME_PER_HOUR));
}

// Devuelve la energía de juego actual y el momento de la siguiente recarga.
export function ticketsNow(tickets, ticketsAt, now, scale = 1) {
  if (tickets >= TICKETS_MAX) return { tickets: TICKETS_MAX, ticketsAt: now, nextAt: null };
  const regen = TICKET_REGEN_MS / scale;
  const gained = Math.floor((now - ticketsAt) / regen);
  const total = Math.min(TICKETS_MAX, tickets + gained);
  if (total >= TICKETS_MAX) return { tickets: TICKETS_MAX, ticketsAt: now, nextAt: null };
  const at = ticketsAt + gained * regen;
  return { tickets: total, ticketsAt: at, nextAt: at + regen };
}

export const DIFFICULTIES = {
  facil: { name: 'Fácil', mult: 1 },
  normal: { name: 'Normal', mult: 2 },
  dificil: { name: 'Difícil', mult: 3.5 },
};

// `target` es la puntuación para ganar. `minMs` y `maxScore` son los
// límites que el servidor acepta como partida creíble. `branch`: solo
// pueden jugarlo las criaturas que se han criado por esa rama.
export const MINIGAMES = [
  {
    id: 'fruta',
    name: 'Lluvia de fruta',
    blurb: 'Mueve la cesta y recoge la fruta buena. La podrida quita una vida.',
    base: 14,
    levels: {
      facil: { target: 12, seconds: 30, fall: 190, every: 900, rotten: 0.12, lives: 3 },
      normal: { target: 18, seconds: 30, fall: 260, every: 700, rotten: 0.2, lives: 3 },
      dificil: { target: 24, seconds: 30, fall: 340, every: 540, rotten: 0.28, lives: 2 },
    },
    minMs: 12000,
    maxScore: 70,
  },
  {
    id: 'parejas',
    name: 'Parejas',
    blurb: 'Destapa dos cartas iguales. Encuentra todas antes de que acabe el tiempo.',
    base: 15,
    levels: {
      facil: { target: 6, pairs: 6, cols: 3, seconds: 60 },
      normal: { target: 8, pairs: 8, cols: 4, seconds: 60 },
      dificil: { target: 10, pairs: 10, cols: 4, seconds: 55 },
    },
    minMs: 6000,
    maxScore: 10,
  },
  {
    id: 'charcos',
    name: 'Salta charcos',
    blurb: 'Toca para saltar cada charco. Si pisas uno, se acabó.',
    base: 16,
    levels: {
      facil: { target: 12, speed: 230, gap: [1.5, 2.2], grow: 3 },
      normal: { target: 20, speed: 290, gap: [1.2, 1.9], grow: 5 },
      dificil: { target: 30, speed: 350, gap: [1.0, 1.6], grow: 7 },
    },
    minMs: 8000,
    maxScore: 30,
  },
  {
    id: 'eco',
    name: 'Eco',
    blurb: 'Mira la secuencia de luces y repítela. Cada ronda añade una más.',
    base: 15,
    levels: {
      facil: { target: 5, beat: 620 },
      normal: { target: 7, beat: 470 },
      dificil: { target: 9, beat: 350 },
    },
    minMs: 8000,
    maxScore: 9,
  },
  {
    id: 'burbujas',
    name: 'Explota burbujas',
    blurb: 'Explota las burbujas de color. Las grises restan dos puntos.',
    base: 14,
    levels: {
      facil: { target: 14, seconds: 30, every: 760, life: 1500, grey: 0.12 },
      normal: { target: 22, seconds: 30, every: 560, life: 1150, grey: 0.2 },
      dificil: { target: 30, seconds: 30, every: 420, life: 900, grey: 0.28 },
    },
    minMs: 12000,
    maxScore: 80,
  },
  {
    id: 'escondite',
    name: 'Al escondite',
    blurb: 'Se esconde bajo un cojín y los cojines se mezclan. Encuéntrala.',
    base: 18,
    branch: 'jugueton',
    // `swaps` son los cambios de sitio de la primera ronda, que dura
    // `swapMs` cada uno; cada ronda añade uno más y va un poco más rápido.
    levels: {
      facil: { target: 5, cushions: 3, swaps: 3, swapMs: 560, lives: 1 },
      normal: { target: 7, cushions: 4, swaps: 5, swapMs: 440, lives: 1 },
      dificil: { target: 9, cushions: 5, swaps: 7, swapMs: 340, lives: 1 },
    },
    minMs: 10000,
    maxScore: 9,
  },
];

const GAMES_BY_ID = new Map(MINIGAMES.map((game) => [game.id, game]));

export function getGame(id) {
  return GAMES_BY_ID.get(id) ?? null;
}

// Juego del día: paga más y cambia cada día, para que no compense jugar
// siempre al mismo. Rota por todos los juegos que puede jugar cualquier
// criatura, así que dos días seguidos nunca toca el mismo.
export const FEATURED_MULT = 1.5;

const DAY_MS = 24 * 60 * 60 * 1000;

// `day` es la fecha local del jugador, 'AAAA-MM-DD'.
export function featuredGame(day) {
  const pool = MINIGAMES.filter((game) => !game.branch);
  const n = Math.floor(Date.parse(`${day}T00:00:00Z`) / DAY_MS);
  return pool[((n % pool.length) + pool.length) % pool.length].id;
}

// Monedas por ganar con la puntuación justa, sin bonificación. `mult`
// es el extra del juego del día.
export function baseReward(game, difficulty, mult = 1) {
  return Math.round(game.base * DIFFICULTIES[difficulty].mult * mult);
}

// Resultado de una partida. Superar el objetivo da hasta un 50 % más.
export function gameReward(game, difficulty, score, mult = 1) {
  const level = game.levels[difficulty];
  const won = score >= level.target;
  if (!won) return { won: false, coins: 0 };
  const over = Math.min(1, (score - level.target) / level.target);
  return { won: true, coins: Math.round(baseReward(game, difficulty, mult) * (1 + 0.5 * over)) };
}

// Ventajas según el carácter: cada rasgo ayuda en un juego. Cambian el
// nivel (`set` pone un valor, `scale` multiplica uno que ya tiene), nunca
// el objetivo ni el premio. Los valores que el nivel no trae valen:
// basket 1, shield 0, jump 1, peek 0.
export const PERKS = {
  gloton: { game: 'fruta', text: 'Atrapa la fruta desde más lejos.', set: { basket: 1.35 } },
  timido: { game: 'fruta', text: 'Cae menos fruta podrida.', scale: { rotten: 0.6 } },
  fuerte: { game: 'charcos', text: 'Aguanta un charco sin caerse.', set: { shield: 1 } },
  travieso: { game: 'charcos', text: 'Salta más alto.', set: { jump: 1.12 } },
  curioso: { game: 'parejas', text: 'Mira todas las cartas un momento al empezar.', set: { peek: 1.6 } },
  dormilon: { game: 'eco', text: 'La secuencia va más despacio.', scale: { beat: 1.2 } },
  presumido: { game: 'burbujas', text: 'Salen menos burbujas grises.', scale: { grey: 0.5 } },
  mimoso: { game: 'escondite', text: 'Si fallas una vez, te deja buscarla otra vez.', set: { lives: 2 } },
};

// Rasgos de la criatura que ayudan en este juego.
export function perksFor(gameId, genes) {
  if (!genes) return [];
  return genes.traits
    .filter((trait) => PERKS[trait]?.game === gameId)
    .map((trait) => ({ trait, text: PERKS[trait].text }));
}

// Nivel con las ventajas de la criatura ya aplicadas.
export function levelFor(game, difficulty, genes) {
  const level = { ...game.levels[difficulty] };
  for (const { trait } of perksFor(game.id, genes)) {
    const perk = PERKS[trait];
    for (const [key, value] of Object.entries(perk.set ?? {})) level[key] = value;
    for (const [key, factor] of Object.entries(perk.scale ?? {})) level[key] = +(level[key] * factor).toFixed(4);
  }
  return level;
}
