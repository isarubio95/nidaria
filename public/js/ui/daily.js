// Misiones del día: tres encargos con su premio y, arriba, la racha.

import { h, icon, coin, fmt, duration } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { openSheet } from './sheet.js';
import { dialog } from './dialog.js';
import { toast } from './toast.js';

const TIERS = ['Fácil', 'Media', 'Difícil'];
const STREAK_PIPS = 7;
const days = (n) => `${n} ${n === 1 ? 'día' : 'días'}`;

// Misiones cumplidas cuyo premio está sin recoger.
export function claimable(daily) {
  return daily?.missions.filter((mission) => !mission.claimed && mission.progress >= mission.goal).length ?? 0;
}

function streakNote(daily) {
  if (daily.doneToday) {
    return daily.best > daily.streak ? `Hoy ya está. Tu mejor racha es de ${days(daily.best)}.` : 'Hoy ya está. Vuelve mañana para seguirla.';
  }
  if (daily.streak > 0) return `Recoge las tres de hoy para llegar a ${days(daily.streak + 1)} y ganar ${fmt(daily.bonus)} monedas más.`;
  return `Recoge las tres de hoy para empezar una racha y ganar ${fmt(daily.bonus)} monedas más.`;
}

function missionRow(mission, tier, onClaim) {
  const done = mission.progress >= mission.goal;
  const fill = h('div', { class: 'bar-fill' });
  fill.style.setProperty('--v', String(Math.round((mission.progress / mission.goal) * 100)));
  fill.hidden = mission.progress === 0;
  const bar = h('div', {
    class: 'bar', role: 'meter',
    'aria-valuemin': '0', 'aria-valuemax': String(mission.goal), 'aria-valuenow': String(mission.progress),
    'aria-label': `Progreso: ${mission.progress} de ${mission.goal}`,
  }, fill);

  let side;
  if (mission.claimed) {
    side = h('span', { class: 'mission-claimed' }, icon('check'), 'Recogida');
  } else if (done) {
    side = h('button', { class: 'btn btn-sun btn-small', type: 'button', onclick: (event) => onClaim(mission, event.currentTarget) },
      'Recoger', coin(true), h('span', { class: 'num' }, fmt(mission.reward)));
  } else {
    side = h('span', { class: 'mission-reward', 'aria-label': `Premio: ${fmt(mission.reward)} monedas` }, coin(true), h('span', { class: 'num' }, fmt(mission.reward)));
  }

  return h('article', { class: 'mission', dataset: { state: mission.claimed ? 'claimed' : done ? 'done' : 'open' } },
    h('div', { class: 'mission-main' },
      h('p', { class: 'mission-tier' }, TIERS[tier]),
      h('h3', {}, mission.text),
      h('div', { class: 'mission-progress' }, bar, h('span', { class: 'num' }, `${mission.progress} de ${mission.goal}`)),
    ),
    side,
  );
}

export function openDaily() {
  const body = h('div');
  let off = null;
  let timer = null;
  const sheet = openSheet({
    title: 'Misiones del día',
    sub: ' ',
    body,
    onClose: () => {
      off?.();
      clearInterval(timer);
    },
  });
  const reset = sheet.el.querySelector('.sheet-sub');

  const paintReset = () => {
    const daily = store.state?.daily;
    if (daily) reset.textContent = `Cambian en ${duration(daily.resetAt - store.now())}.`;
  };

  async function claim(mission, button) {
    button.disabled = true;
    const payload = await run(api.claim({ id: mission.id }));
    if (!payload) {
      button.disabled = false;
      return;
    }
    const { coins, bonus, streak } = payload.result;
    if (!bonus) {
      toast(`Has recogido ${fmt(coins)} monedas.`);
      return;
    }
    await dialog({
      title: '¡Misiones del día completas!',
      text: streak > 1 ? `Llevas una racha de ${days(streak)}. Mañana habrá tres más.` : 'Has empezado una racha. Mañana habrá tres más.',
      extra: h('p', { class: 'reward' }, coin(), h('span', { class: 'num' }, `+${fmt(coins + bonus)}`)),
      actions: [{ label: '¡Bien!', value: true, tone: 'sun' }],
    });
  }

  function render() {
    const daily = store.state?.daily;
    if (!daily) return;
    const lit = Math.min(daily.streak, STREAK_PIPS);
    const streak = h('section', { class: 'streak', 'aria-label': 'Racha' },
      h('span', { class: 'streak-badge', 'aria-hidden': 'true' }, icon('flame', 'icon-fill')),
      h('div', { class: 'streak-text' },
        h('p', { class: 'streak-label' }, daily.streak ? `Racha de ${days(daily.streak)}` : 'Sin racha todavía'),
        h('p', { class: 'streak-note' }, streakNote(daily)),
      ),
      h('div', { class: 'pips streak-pips', 'aria-hidden': 'true' },
        Array.from({ length: STREAK_PIPS }, (_, i) => h('span', { class: i < lit ? 'pip is-on' : 'pip' }))),
    );
    body.replaceChildren(
      streak,
      h('div', { class: 'missions' }, daily.missions.map((mission, tier) => missionRow(mission, tier, claim))),
    );
    paintReset();
  }

  off = store.onChange((state) => {
    if (state) render();
  });
  render();
  timer = setInterval(paintReset, 30 * 1000);
  return sheet;
}

// Avisa con un mensaje breve cuando una acción cumple una misión.
export function watchMissions() {
  let seen = null;
  store.onChange((state) => {
    const daily = state?.daily;
    if (!daily) {
      seen = null;
      return;
    }
    const done = new Set(daily.missions.filter((mission) => mission.progress >= mission.goal).map((mission) => mission.id));
    if (seen?.day === daily.day) {
      for (const mission of daily.missions) {
        if (done.has(mission.id) && !seen.done.has(mission.id) && !mission.claimed) {
          toast(`¡Misión cumplida! ${mission.text}.`, 'info', 3400);
        }
      }
    }
    seen = { day: daily.day, done };
  });
}
