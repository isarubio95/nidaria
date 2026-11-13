// Diálogo centrado para momentos que piden parar: una evolución, el
// resultado de un minijuego, una despedida. Devuelve una promesa con el
// valor del botón pulsado.

import { h } from '../dom.js';

const root = document.getElementById('dialogs');
const scrim = document.getElementById('scrim');
const closeMs = 150;

// La imagen puede ser una dirección o la promesa de una (las miniaturas
// de la escena llegan así). Si al final no hay, no se deja el hueco.
function picture(art) {
  const img = h('img', { class: 'dialog-art', alt: '' });
  Promise.resolve(art).then((url) => {
    if (url) img.src = url;
    else img.remove();
  }, () => img.remove());
  return img;
}

// dialog({ title, text, art, extra, actions: [{ label, value, tone, run, keep }] })
// `run` se llama dentro del toque (hace falta para compartir o descargar)
// y, con `keep`, el diálogo sigue abierto.
export function dialog({ title, text, art, extra, actions = [{ label: 'Vale', value: true, tone: 'pink' }] }) {
  return new Promise((resolve) => {
    const titleId = `dialogo-${Date.now()}`;
    const hadSheet = scrim.dataset.open === 'true';
    const returnFocus = document.activeElement;
    let closed = false;

    const close = (value) => {
      if (closed) return;
      closed = true;
      el.classList.remove('is-open');
      el.classList.add('is-closing');
      document.removeEventListener('keydown', onKey);
      if (hadSheet) delete scrim.dataset.layer;
      else scrim.dataset.open = 'false';
      setTimeout(() => {
        el.remove();
        delete scrim.dataset.layer;
        returnFocus?.focus?.({ preventScroll: true });
        resolve(value);
      }, closeMs);
    };

    const buttons = actions.map((action, index) => h('button', {
      class: `btn btn-wide ${action.tone ? `btn-${action.tone}` : 'btn-quiet'}`,
      onclick: () => {
        action.run?.();
        if (!action.keep) close(action.value);
      },
      dataset: { primary: index === 0 },
    }, action.label));

    const el = h('div', { class: 't-modal dialog', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': titleId },
      art ? picture(art) : null,
      h('h2', { id: titleId }, title),
      text ? h('p', {}, text) : null,
      extra ?? null,
      h('div', { class: 'dialog-actions' }, buttons),
    );

    function onKey(event) {
      if (event.key === 'Escape') {
        event.preventDefault();
        close(actions[actions.length - 1].value);
      } else if (event.key === 'Tab') {
        const index = buttons.indexOf(document.activeElement);
        const next = (index + (event.shiftKey ? -1 : 1) + buttons.length) % buttons.length;
        event.preventDefault();
        buttons[next].focus();
      }
    }

    root.append(el);
    scrim.dataset.open = 'true';
    scrim.dataset.layer = 'dialog';
    document.addEventListener('keydown', onKey);
    void el.offsetWidth;
    el.classList.add('is-open');
    buttons[0].focus({ preventScroll: true });
  });
}
