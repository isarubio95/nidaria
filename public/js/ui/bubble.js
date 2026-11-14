// El bocadillo de lo que dice la criatura y el encuadre de la escena. Los
// comparten la pantalla principal y la visita a un amigo.

import { h } from '../dom.js';
import { sheetTop } from './sheet.js';
import { zoomLevel } from '../zoom.js';

// Se coloca sobre su cabeza, sin tapar la placa con el nombre, y la sigue
// mientras está abierto. `say` devuelve si ha llegado a decirlo.
export function speechBubble(room, plate) {
  const el = h('div', { class: 'bubble', 'aria-hidden': 'true' });
  let timer = null;
  let stopped = false;

  // La cabeza llega en píxeles de pantalla; el bocadillo se coloca en los
  // de la interfaz, que con el zoom van encogidos.
  function place() {
    const zoom = zoomLevel();
    const point = room.headPoint(0.16);
    const half = el.offsetWidth / 2 + 12;
    const x = Math.max(half, Math.min(window.innerWidth / zoom - half, point.x / zoom));
    el.style.left = `${x}px`;
    el.style.top = `${Math.max(point.y / zoom, plate.getBoundingClientRect().bottom / zoom + el.offsetHeight + 8)}px`;
  }

  function say(text, seconds = 2.6) {
    if (stopped || !room.hasActor()) return false;
    clearTimeout(timer);
    el.textContent = text;
    place();
    el.classList.add('is-open');
    room.say(Math.min(1.4, seconds));
    timer = setTimeout(() => el.classList.remove('is-open'), seconds * 1000);
    return true;
  }

  const follow = () => {
    if (stopped) return;
    if (el.classList.contains('is-open')) place();
    requestAnimationFrame(follow);
  };
  requestAnimationFrame(follow);

  return {
    el,
    say,
    destroy() {
      stopped = true;
      clearTimeout(timer);
    },
  };
}

// Encuadra a la criatura entre la placa y el muelle. Con una hoja abierta
// sube al hueco que queda encima; si la hoja es tan alta que no deja hueco,
// el encuadre no se toca: la criatura queda detrás en vez de encogerse
// hasta desaparecer.
export function frameCreature(room, plate, dock) {
  const plateBox = plate.getBoundingClientRect();
  const dockBox = dock.getBoundingClientRect();
  const sheet = sheetTop();
  const fits = sheet !== null && sheet - plateBox.bottom >= 200;
  const bottom = fits ? Math.min(dockBox.top, sheet) : dockBox.top;
  room.setFocus(plateBox.bottom + 6, bottom - 8);
}
