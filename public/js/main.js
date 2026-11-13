// Arranque: crea la escena 3D, pide el estado al servidor y decide qué
// pantalla toca (entrada, huevos, despedida o la principal).

import { gsap } from 'gsap';
import { h, reducedMotion } from './dom.js';
import { api, ApiError, localTimeZone } from './api.js';
import { store, run } from './store.js';
import { registerWorker, clearNotifications, syncPush, pushSupport, pushPermission, enablePush } from './pwa.js';
import { startWeather, onWeather } from './weather.js';
import { watchMissions } from './ui/daily.js';
import { createRoom } from './scene/room.js';
import { creatureThumb } from './scene/thumbs.js';
import { getForm, BRANCH_INFO, SECRET_INFO } from '/shared/forms.js';
import { FAMILY_INFO } from '/shared/dna.js';
import { BOND_LEVELS } from '/shared/bond.js';
import { mountAuth } from './ui/auth.js';
import { mountEggs } from './ui/eggs.js';
import { mountHome } from './ui/home.js';
import { mountFarewell } from './ui/farewell.js';
import { bindVisits } from './ui/visit.js';
import { dialog } from './ui/dialog.js';
import { toast } from './ui/toast.js';
import { closeSheet } from './ui/sheet.js';
import { shareCard } from './ui/card.js';
import { line, bondLine } from './ui/speech.js';
import { sfx } from './audio/sfx.js';

const app = document.getElementById('app');
const canvas = document.getElementById('stage');

function message(title, text, action) {
  app.replaceChildren(h('section', { class: 'card-screen' },
    h('h1', {}, title),
    h('p', { class: 'lead' }, text),
    action ?? null,
  ));
}

let room = null;
try {
  room = createRoom(canvas);
} catch (error) {
  console.error(error);
  message('No se puede dibujar la escena', 'Tu navegador no tiene activados los gráficos 3D (WebGL). Actívalos o prueba con otro navegador.');
}

registerWorker();
watchMissions();
if (room) {
  startWeather();
  onWeather((weather) => room.setWeather(weather?.kind ?? null));
}
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) clearNotifications();
});
clearNotifications();

// Una vez por visita, tras entrar: la zona horaria del dispositivo y, si
// ya tenía los avisos, ligarlos a esta sesión.
let linked = false;
function linkDevice(state) {
  if (linked || !state) return;
  linked = true;
  const timeZone = localTimeZone();
  if (timeZone && state.user.timeZone !== timeZone) run(api.settings({ timeZone }), { silent: true });
  syncPush(state.push?.key);
}

// Tras la primera eclosión o evolución, una sola vez, se ofrecen los avisos.
const OFFERED = 'nidaria:avisos:ofrecidos';
async function offerPush(creature) {
  const key = store.state?.push?.key;
  if (!key || pushSupport() !== 'ready' || pushPermission() !== 'default') return;
  try {
    if (localStorage.getItem(OFFERED)) return;
    localStorage.setItem(OFFERED, '1');
  } catch {
    return;
  }
  const yes = await dialog({
    title: `¿Te aviso si ${creature.name} te necesita?`,
    text: 'Te mandaremos un aviso si tiene hambre, se pone enferma o va a evolucionar, aunque tengas el juego cerrado. De noche, nunca.',
    actions: [{ label: 'Sí, avísame', value: true, tone: 'pink' }, { label: 'Ahora no', value: false }],
  });
  if (!yes) return;
  const result = await enablePush(key);
  if (result === 'on') toast('Listo. Te avisaremos en este dispositivo.');
  else if (result === 'denied') toast('El navegador ha bloqueado los avisos. Puedes cambiarlo en sus ajustes.', 'error', 3600);
}

let screen = null;
let screenName = '';
let animating = false;
let queue = Promise.resolve();
const seenFarewell = new Set();

// De visita en casa de un amigo, la habitación es la suya: lo que pase con
// la propia criatura mientras tanto se guarda y se cuenta al volver.
let visiting = false;
let missed = [];

function show(name, mount) {
  screen?.destroy();
  screen = mount();
  screenName = name;
  refocus(false);
}

// La escena se centra en el hueco que deja libre la tarjeta inferior.
function refocus(animate = true) {
  if (!screen?.focusTop) return;
  room.setFocus(16, screen.focusTop() - 8, animate);
}
window.addEventListener('resize', () => refocus(false));

function enter() {
  if (reducedMotion()) return;
  gsap.from('.dock > *, .acts > *', { y: 18, autoAlpha: 0, duration: 0.5, ease: 'power3.out', stagger: 0.04, clearProps: 'all' });
  gsap.from('.hud-top, .plate', { y: -12, autoAlpha: 0, duration: 0.45, ease: 'power3.out', stagger: 0.06, clearProps: 'all' });
}

// En los momentos grandes se ofrece hacer la tarjeta para compartir.
const PHOTO = { label: 'Hacer una foto', value: 'foto' };

function announce(creature, first) {
  const form = getForm(creature.formId);
  const art = creatureThumb(creature.formId, creature.genes);
  if (first.from === 'huevo' && form.stage === 'cria') {
    return dialog({
      title: `¡${creature.name} ha nacido!`,
      text: `Es ${form.name}, de la familia ${FAMILY_INFO[form.family].name}. Según cómo la cuides, crecerá por una rama u otra.`,
      art,
      actions: [{ label: 'Conocerla', value: true, tone: 'pink' }, PHOTO],
    });
  }
  if (form.secret) {
    return dialog({
      title: '¡Una forma oculta!',
      text: `${creature.name} ahora es ${form.name}. ${SECRET_INFO[form.secret].reason}`,
      art,
      actions: [{ label: 'Verla', value: true, tone: 'pink' }, PHOTO],
    });
  }
  return dialog({
    title: `¡${creature.name} ha evolucionado!`,
    text: `Ahora es ${form.name}. La has criado ${BRANCH_INFO[form.branch].name.toLowerCase()} y por eso ha seguido esa rama.`,
    art,
    actions: [{ label: 'Verla', value: true, tone: 'pink' }, PHOTO],
  });
}

function route(state, payload = {}) {
  if (!room) return;
  const events = payload.events ?? [];

  if (!state) {
    linked = false;
    visiting = false;
    missed = [];
    closeSheet();
    room.setNight(false);
    room.setDecor(null);
    room.showDemo();
    show('auth', () => mountAuth(app));
    return;
  }

  if (visiting) {
    missed.push(...events);
    screen?.update(state);
    return;
  }

  // La habitación es del jugador: su decoración vale en todas las pantallas.
  room.setDecor(state.decor);
  const creature = state.creature;
  if (!creature || (!creature.alive && seenFarewell.has(creature.id))) {
    if (screenName !== 'eggs') {
      closeSheet();
      room.setNight(false);
      show('eggs', () => mountEggs(app, room, state));
    }
    return;
  }

  if (!creature.alive) {
    if (screenName === 'farewell' || animating) return;
    const wasHome = screenName === 'home';
    animating = true;
    queue = queue.then(async () => {
      await closeSheet();
      screen?.destroy();
      screen = null;
      screenName = 'farewell';
      app.replaceChildren();
      room.setNight(false);
      if (wasHome && events.some((event) => event.type === 'died')) await room.farewell();
      animating = false;
      show('farewell', () => mountFarewell(app, state, () => {
        seenFarewell.add(creature.id);
        route(store.state);
      }));
    });
    return;
  }

  const fresh = screenName !== 'home';
  if (fresh) {
    // La entrada animada solo al llegar desde otra pantalla, no al recargar.
    const arriving = screenName !== '';
    show('home', () => mountHome(app, room));
    if (arriving) enter();
  } else {
    screen.update(state);
  }

  const evolved = events.filter((event) => event.type === 'evolved');
  if (evolved.length && !fresh && !animating) {
    animating = true;
    queue = queue.then(async () => {
      await closeSheet();
      if (evolved[0].from === 'huevo') await room.hatch(creature);
      else await room.evolve(creature);
      animating = false;
      room.showCreature(store.state.creature ?? creature);
      const choice = await announce(creature, evolved[0]);
      screen?.say?.(line('nace'));
      if (choice === 'foto') await shareCard(room, store.state.creature ?? creature);
      await offerPush(creature);
    });
  } else if (!animating) {
    room.showCreature(creature);
  }

  for (const event of events) {
    if (event.type === 'sick') toast(`${creature.name} se ha puesto enferma. Dale medicina.`, 'error', 4200);
    else if (event.type === 'collapsed') toast(`${creature.name} se ha dormido de puro cansancio.`, 'info', 3600);
    else if (event.type === 'woke') toast(`${creature.name} se ha despertado.`);
    else if (event.type === 'bond') {
      sfx('vinculo', { delay: 0.5 });
      toast(BOND_LEVELS[event.level].reached.replace('{name}', creature.name), 'info', 4000);
      const fond = bondLine(creature, state.user.name, event.level);
      if (fond && !animating) screen?.say?.(fond);
    }
  }
}

bindVisits({
  enter(mount) {
    visiting = true;
    missed = [];
    show('visit', () => mount(app, room));
    enter();
  },
  leave() {
    if (!visiting) return;
    visiting = false;
    const events = missed;
    missed = [];
    closeSheet();
    route(store.state, { events });
  },
});

store.onChange((state, payload) => {
  route(state, payload);
  linkDevice(state);
});

async function boot() {
  if (!room) return;
  try {
    store.apply(await api.state());
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      route(null);
      return;
    }
    room.showDemo();
    message('No hay conexión', error.message, h('button', { class: 'btn btn-pink btn-wide', type: 'button', onclick: boot }, 'Reintentar'));
  }
}

boot();
