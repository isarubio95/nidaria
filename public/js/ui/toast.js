import { h } from '../dom.js';

const root = document.getElementById('toasts');
const closeMs = 250;

// Aviso breve que sube, se queda un momento y se va solo.
export function toast(message, tone = 'info', holdMs = 2600) {
  const el = h('div', { class: 't-toast toast', dataset: { tone } }, message);
  root.append(el);
  while (root.children.length > 2) root.firstElementChild.remove();
  void el.offsetWidth;
  el.classList.add('is-open');
  setTimeout(() => {
    el.classList.remove('is-open');
    setTimeout(() => el.remove(), closeMs + 40);
  }, holdMs);
}
