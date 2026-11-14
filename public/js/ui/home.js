// Pantalla principal: monedas y tienda arriba, la criatura en el centro
// (la dibuja el motor 3D) y, abajo, sus necesidades y las acciones.

import { breedOf } from '/shared/dna.js';
import { h, icon, coin, fmt, duration } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { NEED_INFO, CRITICAL, LOW, moodOf, nextStageAt, stageDuration } from '/shared/needs.js';
import { STAGE_INFO } from '/shared/forms.js';
import { TRICKS, TRICK_LINES, TRICK_CHANCE, TRICK_GAP_MS, bondOf, bondLevel } from '/shared/bond.js';
import { subtleNumber } from './widgets.js';
import { sheetOpen, onSheetChange } from './sheet.js';
import { speechBubble, frameCreature } from './bubble.js';
import { dialog } from './dialog.js';
import { toast } from './toast.js';
import { line, idleLine } from './speech.js';
import { openShop } from './shop.js';
import { openFood, openHeal } from './care.js';
import { openPlay } from './play.js';
import { openWardrobe } from './wardrobe.js';
import { openDecor } from './decor.js';
import { openCriadex } from './criadex.js';
import { openProfile } from './profile.js';
import { openDaily, claimable } from './daily.js';
import { openFriends } from './friends.js';
import { sfx } from '../audio/sfx.js';

const NEED_ICONS = { hunger: 'food', energy: 'moon', hygiene: 'drop', fun: 'ball', health: 'heart' };
const MOOD_TEXT = {
  feliz: 'feliz', bien: 'contenta', regular: 'regular', mal: 'lo pasa mal', fatal: 'muy mal',
  enferma: 'enferma', dormida: 'dormida', huevo: 'incubando', muerta: '',
};
const DAY_MS = 24 * 60 * 60 * 1000;

export function mountHome(app, room) {
  let state = store.state;
  let live = store.live().creature;
  let syncing = false;
  let lastSync = Date.now();
  let lastPet = 0;
  let petLine = 0;
  let lastTrick = 0;
  let eggTaps = 0;
  let eggTimer = null;
  let chatterAt = Date.now() + 9000;
  let destroyed = false;

  // ── Cabecera ──
  const coins = subtleNumber(state.user.coins);
  const coinsPill = h('div', { class: 'coins', role: 'status', 'aria-label': `Monedas: ${fmt(state.user.coins)}` }, coin(), coins);
  const piggyAmount = h('span', { class: 'num' });
  const piggy = h('button', {
    class: 'piggy', type: 'button', hidden: true,
    onclick: async () => {
      piggy.disabled = true;
      const payload = await run(api.collect());
      piggy.disabled = false;
      if (payload) sfx('monedas');
      if (payload) toast(`Has recogido ${fmt(payload.result.coins)} monedas.`);
    },
  }, coin(true), h('span', { class: 'sr-only' }, 'Recoger '), '+', piggyAmount);

  const shopButton = h('button', { class: 'btn btn-primary', type: 'button', onclick: () => openShop() }, icon('bag'), 'Tienda');
  const dailyBadge = h('span', { class: 'badge num', 'aria-hidden': 'true', hidden: true });
  const dailyLabel = h('span', { class: 'sr-only' });
  const dailyButton = h('button', { class: 'side-btn', type: 'button', onclick: () => openDaily() }, icon('target'), 'Misiones', dailyBadge, dailyLabel);
  // Solicitudes y regalos que esperan en la hoja de amigos.
  const friendsBadge = h('span', { class: 'badge num', 'aria-hidden': 'true', hidden: true });
  const friendsLabel = h('span', { class: 'sr-only' });
  const side = h('nav', { class: 'side', 'aria-label': 'Más' },
    dailyButton,
    h('button', { class: 'side-btn', type: 'button', onclick: () => openWardrobe() }, icon('hanger'), 'Armario'),
    h('button', { class: 'side-btn', type: 'button', onclick: () => openDecor() }, icon('home'), 'Casa'),
    h('button', { class: 'side-btn', type: 'button', onclick: () => openFriends() }, icon('friends'), 'Amigos', friendsBadge, friendsLabel),
    h('button', { class: 'side-btn', type: 'button', onclick: () => openCriadex() }, icon('tree'), 'Criadex'),
  );

  const plateName = h('span', { class: 'plate-name' });
  const plateSub = h('span', { class: 'plate-sub' });
  const plate = h('button', { class: 'plate', type: 'button', onclick: () => openProfile(room), 'aria-label': 'Ficha de la criatura' }, plateName, plateSub);

  const bubble = speechBubble(room, plate);

  // ── Muelle ──
  const meters = {};
  const needRows = ['hunger', 'energy', 'hygiene', 'fun', 'health'].map((need) => {
    const fill = h('div', { class: 'bar-fill' });
    const bar = h('div', { class: 'bar', role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-label': NEED_INFO[need].name }, fill);
    const row = h('div', { class: 'need', dataset: { need } }, icon(NEED_ICONS[need]), h('span', { class: 'need-name' }, NEED_INFO[need].name), bar);
    meters[need] = { row, bar, fill, shown: -1 };
    return row;
  });
  const needs = h('section', { class: 'needs', 'aria-label': 'Necesidades' }, needRows);

  const eggFill = h('div', { class: 'bar-fill' });
  const eggBar = h('div', { class: 'bar', role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-label': 'Incubación' }, eggFill);
  const eggNote = h('p', { class: 'field-hint' });
  const incubator = h('section', { class: 'needs', 'aria-label': 'Incubación', hidden: true },
    h('p', { class: 'tickets-label' }, 'Toca el huevo para darle calor'),
    h('div', { class: 'need', dataset: { need: 'hunger' } }, icon('sun'), h('span', { class: 'need-name' }, 'Calor'), eggBar),
    eggNote,
  );

  const act = (id, iconName, label, onclick) => {
    const disc = h('span', { class: 'act-disc' }, icon(iconName));
    const text = h('span', { class: 'act-label' }, label);
    const button = h('button', { class: 'act', type: 'button', dataset: { act: id }, onclick }, disc, text);
    return { button, disc, text };
  };
  const acts = {
    feed: act('feed', 'food', 'Comer', () => openFood(room, say)),
    sleep: act('sleep', 'moon', 'Dormir', toggleSleep),
    bathe: act('bathe', 'drop', 'Baño', bathe),
    play: act('play', 'ball', 'Jugar', () => openPlay(room, say)),
    heal: act('heal', 'heal', 'Curar', () => openHeal(room, say)),
  };
  const actsNav = h('nav', { class: 'acts', 'aria-label': 'Cuidados' }, Object.values(acts).map((item) => item.button));
  const dock = h('footer', { class: 'dock' }, needs, incubator, actsNav);

  const top = h('header', { class: 'hud-top' },
    h('div', { class: 'hud-left' }, coinsPill, piggy),
    h('div', { class: 'hud-right' }, shopButton, side),
  );
  app.replaceChildren(top, plate, bubble.el, dock);

  // ── Acciones ──
  async function toggleSleep() {
    const payload = await run(api.sleep({ asleep: !live.asleep }));
    if (!payload) return;
    sfx(payload.state.creature?.asleep ? 'dormir' : 'despertar');
    say(line(payload.result.reaction));
  }

  async function bathe() {
    const soap = state.inventory.jabon ?? 0;
    let itemId = null;
    if (soap > 0) {
      const choice = await dialog({
        title: '¿Cómo la bañamos?',
        text: `El jabón de burbujas la deja limpia del todo y le divierte. Te ${soap === 1 ? 'queda 1' : `quedan ${soap}`}.`,
        actions: [
          { label: 'Con jabón de burbujas', value: 'jabon', tone: 'mint' },
          { label: 'Baño rápido', value: 'rapido', tone: 'sun' },
          { label: 'Ahora no', value: null },
        ],
      });
      if (!choice) return;
      if (choice === 'jabon') itemId = 'jabon';
    }
    const payload = await run(api.bathe({ itemId }));
    if (!payload) return;
    room.bath();
    say(line('bano'));
  }

  function say(text, seconds = 2.6) {
    if (destroyed) return;
    if (bubble.say(text, seconds)) chatterAt = Date.now() + 14000 + Math.random() * 12000;
  }

  room.on('pet', () => {
    if (!live || live.asleep || !live.alive) return;
    const now = Date.now();
    if (now - lastPet < 1500) return;
    lastPet = now;
    run(api.pet(), { silent: true });
    if (now - petLine > 6000) {
      petLine = now;
      say(line('caricia'), 1.6);
    }
  });
  // Con confianza, al tocarla a veces enseña un truco.
  function trick() {
    const level = Math.min(3, bondLevel(bondOf(live)));
    const known = TRICKS[level];
    const now = Date.now();
    if (!known || now - lastTrick < TRICK_GAP_MS || Math.random() >= TRICK_CHANCE) return false;
    lastTrick = now;
    room.trick(known[Math.floor(Math.random() * known.length)]);
    say(TRICK_LINES[Math.floor(Math.random() * TRICK_LINES.length)], 1.8);
    return true;
  }

  room.on('tap', () => {
    if (!live || !live.alive) return;
    if (live.asleep) return say('Zzz…', 1.4);
    if (live.stage !== 'huevo' && trick()) return;
    say(idleLine(live, moodOf(live), store.state.user.name));
  });
  room.on('eggTap', () => {
    eggTaps += 1;
    clearTimeout(eggTimer);
    eggTimer = setTimeout(async () => {
      const taps = eggTaps;
      eggTaps = 0;
      await run(api.warmEgg({ taps }), { silent: true });
    }, 350);
  });

  // ── Pintado ──
  const layout = () => frameCreature(room, plate, dock);

  function paint() {
    if (!live) return;
    const now = store.now();
    const scale = state.timeScale;
    const egg = live.stage === 'huevo';
    const mood = moodOf(live);

    plateName.textContent = live.name;
    if (egg) {
      const left = Math.max(0, nextStageAt(live, scale) - now);
      plateSub.textContent = left > 0 ? `Huevo · nace en ${duration(left)}` : 'Huevo · a punto de nacer';
      const done = 1 - left / stageDuration('huevo', scale);
      eggFill.style.setProperty('--v', String(Math.round(done * 100)));
      eggBar.setAttribute('aria-valuenow', String(Math.round(done * 100)));
      eggNote.textContent = left > 0 ? `Si no haces nada, nace sola en ${duration(left)}.` : 'Ya casi está.';
      room.setEggProgress(done);
    } else {
      const day = Math.floor(((now - live.bornAt) * scale) / DAY_MS) + 1;
      // En perros y gatos, la raza va delante de la etapa.
      const breed = breedOf(live.genes)?.name;
      plateSub.textContent = `${breed ? `${breed} · ` : ''}${STAGE_INFO[live.stage].name} · día ${day} · ${MOOD_TEXT[mood]}`;
    }
    needs.hidden = egg;
    actsNav.hidden = egg;
    incubator.hidden = !egg;
    side.hidden = egg;

    for (const [need, meter] of Object.entries(meters)) {
      const value = Math.round(live[need]);
      if (value === meter.shown) continue;
      meter.shown = value;
      meter.fill.style.setProperty('--v', String(value));
      meter.bar.setAttribute('aria-valuenow', String(value));
      const level = value < CRITICAL ? 'critical' : value < LOW ? 'low' : 'ok';
      meter.row.dataset.level = level;
      meter.bar.setAttribute('aria-valuetext', level === 'critical' ? `${value} de 100, crítico` : `${value} de 100`);
    }

    const asleep = live.asleep;
    acts.sleep.text.textContent = asleep ? 'Despertar' : 'Dormir';
    acts.sleep.disc.replaceChildren(icon(asleep ? 'sun' : 'moon'));
    acts.feed.button.disabled = asleep;
    acts.bathe.button.disabled = asleep;
    acts.play.button.disabled = asleep;
    room.setNight(asleep);
    room.setMood(mood);

    const income = store.income();
    piggy.hidden = income < 1;
    piggyAmount.textContent = fmt(income);

    const ready = claimable(state.daily);
    dailyBadge.hidden = ready === 0;
    dailyBadge.textContent = String(ready);
    dailyLabel.textContent = ready ? `, ${ready === 1 ? '1 premio' : `${ready} premios`} por recoger` : '';

    const requests = state.social?.requests ?? 0;
    const gifts = state.social?.gifts ?? 0;
    friendsBadge.hidden = requests + gifts === 0;
    friendsBadge.textContent = String(requests + gifts);
    const waiting = [
      requests && (requests === 1 ? '1 solicitud' : `${requests} solicitudes`),
      gifts && (gifts === 1 ? '1 regalo' : `${gifts} regalos`),
    ].filter(Boolean);
    friendsLabel.textContent = waiting.length ? `, ${waiting.join(' y ')}` : '';
  }

  function update(next) {
    state = next;
    live = store.live().creature;
    coins.set(state.user.coins);
    coinsPill.setAttribute('aria-label', `Monedas: ${fmt(state.user.coins)}`);
    paint();
    layout();
  }

  async function sync() {
    if (syncing) return;
    syncing = true;
    lastSync = Date.now();
    await run(api.state(), { silent: true });
    syncing = false;
  }

  const ticker = setInterval(() => {
    if (destroyed || !store.state?.creature) return;
    const result = store.live();
    live = result.creature;
    paint();
    const overdue = Date.now() - lastSync > 45000;
    if (result.events.length || overdue) sync();
    if (Date.now() > chatterAt && !sheetOpen() && live.alive && !live.asleep && live.stage !== 'huevo' && !document.hidden) {
      say(idleLine(live, moodOf(live), store.state.user.name));
    }
  }, 1000);

  const onVisible = () => {
    if (!document.hidden) sync();
  };
  document.addEventListener('visibilitychange', onVisible);
  const resizer = new ResizeObserver(layout);
  resizer.observe(dock);
  const offSheet = onSheetChange(layout);
  window.addEventListener('resize', layout);

  paint();
  layout();

  return {
    update,
    say,
    destroy() {
      destroyed = true;
      clearInterval(ticker);
      bubble.destroy();
      clearTimeout(eggTimer);
      document.removeEventListener('visibilitychange', onVisible);
      window.removeEventListener('resize', layout);
      resizer.disconnect();
      offSheet();
      for (const name of ['pet', 'tap', 'eggTap']) room.on(name, null);
    },
  };
}
