// Ajustes del dispositivo, al final de la ficha: avisos, clima real e
// instalar el juego como app.

import { h } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import {
  pushSupport, pushPermission, pushEnabled, enablePush, disablePush,
  installMode, install, onInstallChange,
} from '../pwa.js';
import { weatherOn, enableWeather, disableWeather, onWeather, WEATHER_NAMES } from '../weather.js';
import { toggle } from './widgets.js';
import { toast } from './toast.js';

const PUSH_GROUPS = [
  { key: 'care', label: 'Cuando necesite algo', hint: 'Hambre, sueño, suciedad, aburrimiento o si enferma.' },
  { key: 'growth', label: 'Cuando nazca o evolucione' },
  { key: 'streak', label: 'Si la racha está en peligro', hint: 'A partir de las 20:00, si no has completado las misiones.' },
  { key: 'games', label: 'Energía de juego llena', hint: 'Después de gastarla casi toda.' },
  { key: 'quiet', label: 'Silencio de 23:00 a 8:00', hint: 'Lo que pase de noche te llega por la mañana.' },
];

const note = (text) => h('p', { class: 'field-hint setting-note' }, text);

const PUSH_BLOCKED = {
  insecure: 'Los avisos necesitan que el juego se abra con https. Con esta dirección, el navegador no los permite.',
  ios: 'En iPhone y iPad, primero añade Nidaria a la pantalla de inicio (botón Compartir y «Añadir a pantalla de inicio») y ábrela desde ahí.',
  unsupported: 'Este navegador no puede recibir avisos.',
};

// Devuelve { el, destroy }. Se pinta por partes porque mirar si hay
// suscripción es asíncrono.
export function settingsSection() {
  const pushBox = h('div', { class: 'settings' });
  const weatherBox = h('div', { class: 'settings' });
  const installBox = h('div', { class: 'settings' });
  const installTitle = h('h3', { class: 'section-title' }, 'Instalar');
  let destroyed = false;

  async function paintPush() {
    const push = store.state?.push;
    if (!push) {
      pushBox.replaceChildren(note('Este servidor no tiene los avisos activados.'));
      return;
    }
    const support = pushSupport();
    if (support !== 'ready') {
      pushBox.replaceChildren(note(PUSH_BLOCKED[support]));
      return;
    }
    if (pushPermission() === 'denied') {
      pushBox.replaceChildren(note('Has bloqueado los avisos de Nidaria. Para recibirlos, permítelos en los ajustes del navegador.'));
      return;
    }
    const on = await pushEnabled();
    if (destroyed) return;
    const name = store.state.creature?.name ?? 'tu criatura';
    if (!on) {
      pushBox.replaceChildren(
        note(`Te avisamos cuando ${name} te necesite, aunque tengas el juego cerrado.`),
        h('button', {
          class: 'btn btn-pink', type: 'button',
          onclick: async (event) => {
            event.currentTarget.disabled = true;
            const result = await enablePush(push.key);
            if (result === 'on') toast('Listo. Te avisaremos en este dispositivo.');
            else if (result === 'denied') toast('El navegador ha bloqueado los avisos.', 'error');
            else if (result === 'error') toast('No se han podido activar los avisos. Inténtalo de nuevo.', 'error');
            paintPush();
          },
        }, 'Activar avisos'),
      );
      return;
    }
    const prefs = push.prefs;
    pushBox.replaceChildren(
      ...PUSH_GROUPS.map((group) => toggle({
        label: group.label,
        hint: group.hint,
        checked: prefs[group.key],
        onChange: async (value) => Boolean(await run(api.settings({ notify: { [group.key]: value } }))),
      })),
      h('button', {
        class: 'btn btn-quiet btn-small', type: 'button',
        onclick: async (event) => {
          event.currentTarget.disabled = true;
          await disablePush();
          toast('Este dispositivo ya no recibirá avisos.');
          paintPush();
        },
      }, 'Desactivar en este dispositivo'),
    );
  }

  function paintWeather(weather) {
    const now = weather ? `Ahora: ${WEATHER_NAMES[weather.kind]}, ${weather.temp} °C.` : null;
    weatherBox.replaceChildren(toggle({
      label: 'Ver por la ventana el tiempo que hace',
      hint: `Usa tu ubicación aproximada para preguntar el tiempo a Open-Meteo. No se guarda en Nidaria.${now ? ` ${now}` : ''}`,
      checked: weatherOn(),
      onChange: async (value) => {
        if (!value) {
          disableWeather();
          return true;
        }
        try {
          await enableWeather();
          return true;
        } catch {
          toast('No se ha podido saber dónde estás. Revisa el permiso de ubicación.', 'error', 3600);
          return false;
        }
      },
    }));
  }

  function paintInstall() {
    const mode = installMode();
    installTitle.hidden = !mode;
    installBox.hidden = !mode;
    if (mode === 'prompt') {
      installBox.replaceChildren(
        note('Añade Nidaria a tu pantalla de inicio para abrirla como una app más.'),
        h('button', { class: 'btn btn-mint', type: 'button', onclick: () => install() }, 'Instalar'),
      );
    } else if (mode === 'ios') {
      installBox.replaceChildren(note('Toca el botón Compartir del navegador y elige «Añadir a pantalla de inicio».'));
    }
  }

  paintPush();
  const offWeather = onWeather(paintWeather);
  const offInstall = onInstallChange(paintInstall);
  paintInstall();

  return {
    el: [
      h('h3', { class: 'section-title' }, 'Avisos'),
      pushBox,
      h('h3', { class: 'section-title' }, 'Clima real'),
      weatherBox,
      installTitle,
      installBox,
    ],
    destroy() {
      destroyed = true;
      offWeather();
      offInstall();
    },
  };
}
