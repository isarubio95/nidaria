// Piezas pequeñas compartidas: pestañas con píldora deslizante y número
// que cambia en sitio (ambas de transitions.dev).

import { h } from '../dom.js';

// tabs([{ id, label }], activeId, onChange) -> elemento
export function tabs(options, activeId, onChange, label = 'Secciones') {
  const pill = h('span', { class: 't-tabs-pill', 'aria-hidden': 'true' });
  const buttons = options.map((option) => h('button', {
    class: 't-tab', role: 'tab', type: 'button',
    'aria-selected': String(option.id === activeId),
    dataset: { id: option.id },
  }, option.label));
  const bar = h('div', { class: 't-tabs', role: 'tablist', 'aria-label': label }, pill, buttons);

  const active = () => buttons.find((tab) => tab.getAttribute('aria-selected') === 'true') ?? buttons[0];
  const moveTo = (tab, animate) => {
    if (!animate) {
      const prev = pill.style.transition;
      pill.style.transition = 'none';
      pill.style.transform = `translateX(${tab.offsetLeft}px)`;
      pill.style.width = `${tab.offsetWidth}px`;
      void pill.offsetWidth;
      pill.style.transition = prev;
    } else {
      pill.style.transform = `translateX(${tab.offsetLeft}px)`;
      pill.style.width = `${tab.offsetWidth}px`;
    }
  };

  const select = (tab, animate = true) => {
    buttons.forEach((other) => other.setAttribute('aria-selected', other === tab ? 'true' : 'false'));
    moveTo(tab, animate);
    tab.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  };

  buttons.forEach((tab, index) => {
    tab.addEventListener('click', () => {
      if (tab.getAttribute('aria-selected') === 'true') return;
      select(tab);
      onChange(tab.dataset.id);
    });
    tab.addEventListener('keydown', (event) => {
      const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
      if (!step) return;
      event.preventDefault();
      const next = buttons[(index + step + buttons.length) % buttons.length];
      next.focus();
      next.click();
    });
  });

  const place = () => moveTo(active(), false);
  requestAnimationFrame(place);
  // La fuente puede cargar después y cambiar los anchos.
  document.fonts?.ready.then(place);
  const observer = new ResizeObserver(place);
  observer.observe(bar);
  bar.setActive = (id) => {
    const tab = buttons.find((button) => button.dataset.id === id);
    if (tab) select(tab, false);
  };
  return bar;
}

const FORMAT = new Intl.NumberFormat('es-ES');

// Número cuyas cifras cambiadas se funden en sitio.
export function subtleNumber(initial) {
  let value = initial;
  const el = h('span', { class: 't-subtle-number' }, FORMAT.format(initial));
  el.set = (next) => {
    if (next === value) return;
    const prev = FORMAT.format(value);
    const str = FORMAT.format(next);
    const sameLength = prev.length === str.length;
    el.style.setProperty('--subtle-dir', next >= value ? '1' : '-1');
    el.classList.remove('is-animating');
    el.replaceChildren();
    [...str].forEach((ch, i) => {
      if (sameLength && prev[i] === ch) {
        el.append(ch);
        return;
      }
      const slot = h('span', { class: 't-subtle-slot' });
      if (sameLength) slot.append(h('span', { class: 't-subtle-old', 'aria-hidden': 'true' }, prev[i]));
      slot.append(h('span', { class: 't-subtle-new' }, ch));
      el.append(slot);
    });
    value = next;
    void el.offsetHeight;
    el.classList.add('is-animating');
  };
  return el;
}
