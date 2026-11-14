// Visita a la habitación de un amigo: su decoración, su criatura tal como
// está ahora y la opción de dejarle un regalo. Nada de lo que se hace aquí
// toca su criatura: las caricias y los toques son para verla reaccionar.

import { h, icon } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { LOW } from '/shared/needs.js';
import { itemsOfKind } from '/shared/catalog.js';
import { GIFT_KINDS } from '/shared/social.js';
import { openSheet, closeSheet, onSheetChange } from './sheet.js';
import { dialog } from './dialog.js';
import { toast } from './toast.js';
import { openShop } from './shop.js';
import { ownedTiles } from './care.js';
import { speechBubble, frameCreature } from './bubble.js';
import { line, visitLine } from './speech.js';
import { sfx } from '../audio/sfx.js';

// main.js decide cómo se entra y se sale de la pantalla de visita.
let hooks = null;
export function bindVisits(next) {
  hooks = next;
}

// Pide la habitación del amigo y, si llega, cambia de pantalla.
export async function visitFriend(id) {
  if (!hooks) return false;
  const payload = await run(api.visit({ id }));
  if (!payload) return false;
  await closeSheet();
  hooks.enter((app, room) => mountVisit(app, room, payload.result.visit, hooks.leave));
  return true;
}

// Qué tal está la criatura del amigo y qué le vendría bien.
const WANTS = {
  hunger: 'Tiene hambre: algo de comer le vendría bien.',
  energy: 'Está muy cansada. Necesita dormir.',
  hygiene: 'Necesita un baño. Un jabón de burbujas le vendría bien.',
  fun: 'Se aburre un poco.',
};

function status(creature, owner) {
  if (!creature) return `${owner} no tiene ninguna criatura ahora mismo.`;
  if (creature.stage === 'huevo') return `Todavía es un huevo. ${owner} lo está incubando.`;
  if (creature.asleep) return 'Está durmiendo. Habla bajito.';
  if (creature.sick) return 'Está enferma. Una medicina le vendría bien.';
  const worst = ['hunger', 'energy', 'hygiene', 'fun'].sort((a, b) => creature[a] - creature[b])[0];
  if (creature[worst] < LOW) return WANTS[worst];
  if (creature.mood === 'feliz') return 'Está feliz. Se nota que la cuidan bien.';
  return 'Está tranquila. Le hace ilusión que vengas.';
}

export function mountVisit(app, room, visit, leave) {
  const creature = visit.creature;
  let giftedToday = visit.giftedToday;
  let petLine = 0;
  let destroyed = false;

  const back = h('button', { class: 'pill-btn', type: 'button', onclick: () => leave() }, icon('back'), 'Volver a casa');
  const top = h('header', { class: 'hud-top' }, h('div', { class: 'hud-left' }, back));

  const plate = h('div', { class: 'plate plate-visit' },
    h('span', { class: 'plate-name' }, creature ? creature.name : `Casa de ${visit.name}`),
    h('span', { class: 'plate-sub' }, creature
      ? `En casa de ${visit.name} · ${creature.stage === 'huevo' ? 'Huevo' : creature.formName}`
      : 'No hay nadie en casa'),
  );
  const bubble = speechBubble(room, plate);

  const note = h('p', { class: 'visit-status' }, status(creature, visit.name));
  const giftButton = h('button', { class: 'btn btn-primary btn-wide', type: 'button', onclick: chooseGift });
  const remove = h('button', { class: 'text-link', type: 'button', onclick: removeFriend }, 'Quitar de amigos');
  const dock = h('footer', { class: 'dock' }, h('section', { class: 'visit-card', 'aria-label': `Casa de ${visit.name}` }, note, giftButton, remove));
  app.replaceChildren(top, plate, bubble.el, dock);

  function paint() {
    giftButton.disabled = giftedToday;
    giftButton.replaceChildren(icon('gift'), giftedToday ? 'Hoy ya le has dejado un regalo' : 'Dejar un regalo');
  }

  const say = (text, seconds) => {
    if (!destroyed) bubble.say(text, seconds);
  };

  // ── La habitación del amigo ──
  room.setDecor(visit.decor);
  if (creature) {
    room.showCreature(creature);
    room.setNight(creature.asleep);
  } else {
    room.showEmpty();
    room.setNight(false);
  }
  const awake = creature && creature.stage !== 'huevo' && !creature.asleep;
  const greeting = awake ? setTimeout(() => say(visitLine(creature, store.state?.user?.name)), 700) : null;

  room.on('tap', () => {
    if (!awake) return say('Zzz…', 1.4);
    say(visitLine(creature, store.state?.user?.name));
  });
  room.on('pet', () => {
    if (!awake || Date.now() - petLine < 6000) return;
    petLine = Date.now();
    say(line('caricia'), 1.6);
  });
  // El huevo de otro no se calienta desde aquí.
  room.on('eggTap', null);

  // ── Regalo ──
  function chooseGift() {
    const owned = GIFT_KINDS.flatMap(itemsOfKind).filter((item) => (store.state.inventory[item.id] ?? 0) > 0);
    const body = owned.length
      ? h('div', { class: 'items' }, ownedTiles(owned, give))
      : h('div', { class: 'empty' },
        h('p', {}, 'No tienes comida ni cuidados que regalar. Compra algo en la tienda y vuelve.'),
        h('button', { class: 'btn btn-primary', type: 'button', onclick: () => openShop('comida') }, 'Ir a la tienda'),
      );
    openSheet({
      title: `Un regalo para ${visit.name}`,
      sub: owned.length ? 'Elige algo tuyo. Le llegará envuelto y lo abrirá cuando entre. Puedes dejarle uno al día.' : null,
      body,
    });
  }

  async function give(item) {
    closeSheet();
    giftButton.disabled = true;
    const payload = await run(api.gift({ id: visit.id, itemId: item.id }));
    if (destroyed) return;
    if (!payload) {
      paint();
      return;
    }
    giftedToday = true;
    paint();
    sfx('vinculo');
    room.cheer(true);
    if (awake) say('¡Un regalo! Gracias.', 2);
    toast(`Le has dejado ${item.name} a ${visit.name}. Lo verá al entrar.`, 'info', 3400);
  }

  async function removeFriend() {
    const sure = await dialog({
      title: `¿Quitar a ${visit.name} de tus amigos?`,
      text: 'Dejaréis de poder visitaros y de dejaros regalos. Para volver a ser amigos, uno tendrá que añadir otra vez al otro.',
      actions: [{ label: 'Seguir siendo amigos', value: false, tone: 'primary' }, { label: 'Quitar', value: true }],
    });
    if (!sure || destroyed) return;
    const payload = await run(api.removeFriend({ id: visit.id }));
    if (!payload || destroyed) return;
    toast(`${visit.name} ya no está entre tus amigos.`);
    leave();
  }

  // ── Encuadre ──
  const layout = () => frameCreature(room, plate, dock);
  const resizer = new ResizeObserver(layout);
  resizer.observe(dock);
  const offSheet = onSheetChange(layout);
  window.addEventListener('resize', layout);
  paint();
  layout();

  return {
    update() {},
    destroy() {
      destroyed = true;
      clearTimeout(greeting);
      bubble.destroy();
      resizer.disconnect();
      offSheet();
      window.removeEventListener('resize', layout);
      for (const name of ['pet', 'tap', 'eggTap']) room.on(name, null);
    },
  };
}
