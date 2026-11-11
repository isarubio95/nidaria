// Estado de la partida en el navegador. Guarda la última foto del
// servidor y, entre visitas, mueve las necesidades con la misma
// simulación que usa el servidor.

import { simulate } from '/shared/needs.js';
import { ticketsNow, pendingIncome, TICKET_REGEN_MS } from '/shared/economy.js';
import { ApiError } from './api.js';
import { toast } from './ui/toast.js';

const listeners = new Set();
const eventListeners = new Set();

export const store = {
  state: null,
  skew: 0,

  // Hora del servidor, corregida con el desfase del reloj local.
  now() {
    return Date.now() + this.skew;
  },

  apply(payload) {
    this.state = payload.state;
    this.skew = payload.state.now - Date.now();
    for (const fn of listeners) fn(this.state, payload);
    if (payload.events?.length) for (const fn of eventListeners) fn(payload.events, payload);
  },

  clear() {
    this.state = null;
    for (const fn of listeners) fn(null, {});
  },

  onChange(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },

  onEvents(fn) {
    eventListeners.add(fn);
    return () => eventListeners.delete(fn);
  },

  // Criatura adelantada hasta ahora. `events` avisa de que ha pasado algo
  // (evolución, muerte) que conviene confirmar con el servidor.
  live() {
    const creature = this.state?.creature;
    if (!creature) return { creature: null, events: [] };
    return simulate(creature, this.now(), { scale: this.state.timeScale });
  },

  tickets() {
    const user = this.state.user;
    if (user.tickets >= user.ticketsMax) return { tickets: user.ticketsMax, nextAt: null };
    const at = user.ticketsNextAt - TICKET_REGEN_MS / this.state.timeScale;
    return ticketsNow(user.tickets, at, this.now(), this.state.timeScale);
  },

  income() {
    return pendingIncome(this.state.user.incomeAt, this.now(), this.state.timeScale);
  },
};

// Ejecuta una llamada a la API, aplica su resultado y enseña el error si
// lo hay. Devuelve el payload o null si falló.
export async function run(promise, { silent = false } = {}) {
  try {
    const payload = await promise;
    if (payload?.state) store.apply(payload);
    return payload;
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      store.clear();
      return null;
    }
    if (!silent) toast(error.message ?? 'Algo ha fallado. Inténtalo de nuevo.', 'error');
    return null;
  }
}
