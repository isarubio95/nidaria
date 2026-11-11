// Hoja inferior. Una sola abierta a la vez: sube blanda sobre un velo,
// atrapa el foco y se cierra con Escape, con el botón o tocando fuera.

import { h, icon } from '../dom.js';

const root = document.getElementById('sheets');
const scrim = document.getElementById('scrim');
const app = document.getElementById('app');
const closeMs = 350;

let current = null;
const watchers = new Set();
const notify = () => {
  for (const fn of watchers) fn();
};

// Avisa cuando se abre o se cierra una hoja, para recolocar la escena.
export function onSheetChange(fn) {
  watchers.add(fn);
  return () => watchers.delete(fn);
}

// Borde superior de la hoja abierta en su posición final, o null.
export function sheetTop() {
  if (!current) return null;
  const el = current.el;
  return el.offsetTop;
}

function focusables(el) {
  return [...el.querySelectorAll('button:not(:disabled), input, [tabindex="0"]')].filter((node) => node.offsetParent !== null);
}

export function closeSheet() {
  if (!current) return Promise.resolve();
  const sheet = current;
  current = null;
  sheet.el.dataset.open = 'false';
  scrim.dataset.open = 'false';
  app.inert = false;
  document.removeEventListener('keydown', sheet.onKey);
  sheet.resizer?.disconnect();
  sheet.onClose?.();
  notify();
  sheet.returnFocus?.focus?.({ preventScroll: true });
  return new Promise((resolve) => setTimeout(() => {
    sheet.el.remove();
    resolve();
  }, closeMs));
}

// openSheet({ title, sub, tabs, body, foot, onClose }) -> { el, body, foot, close }
export function openSheet({ title, sub, tabs, body, foot, onClose }) {
  const previous = current;
  if (previous) {
    current = null;
    previous.el.dataset.open = 'false';
    document.removeEventListener('keydown', previous.onKey);
    previous.resizer?.disconnect();
    previous.onClose?.();
    setTimeout(() => previous.el.remove(), closeMs);
  }
  const titleId = `hoja-${Date.now()}`;
  const bodyEl = h('div', { class: 'sheet-body' }, body);
  const footEl = h('div', { class: 'sheet-foot', hidden: !foot }, foot);
  const el = h('section', {
    class: 'sheet t-panel-slide', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': titleId, dataset: { open: 'false' },
  },
    h('header', { class: 'sheet-head' },
      h('h2', { id: titleId }, title),
      h('button', { class: 'icon-btn', 'aria-label': 'Cerrar', onclick: () => closeSheet() }, icon('x')),
    ),
    sub ? h('p', { class: 'sheet-sub' }, sub) : null,
    tabs ? h('div', { class: 'sheet-tabs' }, tabs) : null,
    bodyEl,
    footEl,
  );
  const sheet = {
    el,
    body: bodyEl,
    foot: footEl,
    onClose,
    returnFocus: previous?.returnFocus ?? document.activeElement,
    close: closeSheet,
    setFoot(content) {
      footEl.replaceChildren(...[content].flat().filter(Boolean));
      footEl.hidden = !content;
    },
    onKey(event) {
      if (event.key === 'Escape') {
        event.preventDefault();
        closeSheet();
        return;
      }
      if (event.key !== 'Tab') return;
      const list = focusables(el);
      if (!list.length) return;
      const first = list[0];
      const last = list[list.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    },
  };
  root.append(el);
  current = sheet;
  app.inert = true;
  scrim.dataset.open = 'true';
  delete scrim.dataset.layer;
  document.addEventListener('keydown', sheet.onKey);
  void el.offsetWidth;
  el.dataset.open = 'true';
  el.querySelector('.icon-btn').focus({ preventScroll: true });
  sheet.resizer = new ResizeObserver(notify);
  sheet.resizer.observe(el);
  notify();
  return sheet;
}

export const sheetOpen = () => current !== null;

scrim.addEventListener('click', () => {
  if (scrim.dataset.layer !== 'dialog') closeSheet();
});
