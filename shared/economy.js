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
// límites que el servidor acepta como partida creíble.
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
];

const GAMES_BY_ID = new Map(MINIGAMES.map((game) => [game.id, game]));

export function getGame(id) {
  return GAMES_BY_ID.get(id) ?? null;
}

// Monedas por ganar con la puntuación justa, sin bonificación.
export function baseReward(game, difficulty) {
  return Math.round(game.base * DIFFICULTIES[difficulty].mult);
}

// Resultado de una partida. Superar el objetivo da hasta un 50 % más.
export function gameReward(game, difficulty, score) {
  const level = game.levels[difficulty];
  const won = score >= level.target;
  if (!won) return { won: false, coins: 0 };
  const over = Math.min(1, (score - level.target) / level.target);
  return { won: true, coins: Math.round(baseReward(game, difficulty) * (1 + 0.5 * over)) };
}
