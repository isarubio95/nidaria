// Ayudas mínimas para construir DOM sin plantillas de texto.

const SVG_NS = 'http://www.w3.org/2000/svg';

// h('button', { class: 'btn', onclick: fn, 'aria-label': 'x' }, 'Texto', nodo)
export function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props ?? {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key === 'dataset') Object.assign(el.dataset, value);
    else if (key === 'style') Object.assign(el.style, value);
    else if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else if (key === 'hidden' || key === 'disabled' || key === 'required') el[key] = Boolean(value);
    else el.setAttribute(key, value === true ? '' : String(value));
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
}

export function icon(name, extra = '') {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('class', `icon ${extra}`.trim());
  svg.setAttribute('aria-hidden', 'true');
  const use = document.createElementNS(SVG_NS, 'use');
  use.setAttribute('href', `#i-${name}`);
  svg.append(use);
  return svg;
}

export function coin(small = false) {
  return h('span', { class: small ? 'coin coin-sm' : 'coin', 'aria-hidden': 'true' }, icon('star', 'icon-fill'));
}

export function clear(el) {
  el.replaceChildren();
  return el;
}

export const $ = (selector, root = document) => root.querySelector(selector);

export const reducedMotion = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

const NUMBER = new Intl.NumberFormat('es-ES');
export const fmt = (value) => NUMBER.format(Math.round(value));

// "3 h 12 min", "45 s", "2 días"
export function duration(ms) {
  const s = Math.max(0, Math.round(ms / 1000));
  if (s < 60) return `${s} s`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m} min`;
  const hours = Math.floor(m / 60);
  if (hours < 24) return m % 60 ? `${hours} h ${m % 60} min` : `${hours} h`;
  const days = Math.floor(hours / 24);
  return days === 1 ? '1 día' : `${days} días`;
}
