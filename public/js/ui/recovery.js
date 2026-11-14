// Código de recuperación: el diálogo que lo enseña (una sola vez) y la
// parte de la ficha para crear uno nuevo.

import { h } from '../dom.js';
import { api, ApiError } from '../api.js';
import { store } from '../store.js';
import { dialog } from './dialog.js';
import { toast } from './toast.js';
import { field } from './widgets.js';

const TEXTS = {
  register: {
    title: 'Guarda tu código de recuperación',
    text: 'Si un día olvidas la contraseña, podrás entrar con tu nombre y este código. Solo te lo enseñamos ahora: apúntalo o haz una captura.',
  },
  recovered: {
    title: 'Ya puedes jugar',
    text: 'El código que has usado ya no vale y hemos cerrado la cuenta en los demás dispositivos. Este es tu código nuevo: guárdalo igual que el otro.',
  },
  renewed: {
    title: 'Tu código nuevo',
    text: 'El anterior ya no vale. Apunta este o haz una captura: no lo volveremos a enseñar.',
  },
};

function copy(code) {
  if (!navigator.clipboard) {
    toast('No se ha podido copiar. Apúntalo a mano.', 'error');
    return;
  }
  navigator.clipboard.writeText(code).then(
    () => toast('Código copiado.'),
    () => toast('No se ha podido copiar. Apúntalo a mano.', 'error'),
  );
}

// reason: 'register' | 'recovered' | 'renewed'
export function showRecoveryCode(code, reason) {
  const { title, text } = TEXTS[reason];
  return dialog({
    title,
    text,
    extra: h('p', { class: 'recovery-code num', translate: 'no', 'aria-label': `Código: ${code.split('').join(' ')}` }, code),
    actions: [
      { label: 'Ya lo he guardado', value: true, tone: 'primary' },
      { label: 'Copiar', keep: true, run: () => copy(code) },
    ],
  });
}

// Parte de «Tu cuenta» en la ficha. Crear un código pide la contraseña.
export function recoverySection() {
  const box = h('div', { class: 'settings' });
  const note = (text) => h('p', { class: 'field-hint setting-note' }, text);

  function paint() {
    const has = store.state?.user?.hasRecovery;
    box.replaceChildren(
      note(has
        ? 'Si olvidas la contraseña, entras con tu nombre y tu código. Si lo has perdido, crea otro: el anterior dejará de valer.'
        : 'Tu cuenta aún no tiene código. Créalo y guárdalo: si olvidas la contraseña, es la única forma de volver a entrar.'),
      h('button', { class: `btn btn-small ${has ? 'btn-quiet' : 'btn-mint'}`, type: 'button', onclick: ask }, has ? 'Crear un código nuevo' : 'Crear código'),
    );
  }

  function ask() {
    const pass = field({ id: 'clave-codigo', label: 'Tu contraseña', type: 'password', autocomplete: 'current-password' });
    const submit = h('button', { class: 'btn btn-mint btn-small', type: 'submit' }, 'Crear código');
    const form = h('form', {
      class: 'settings', novalidate: true,
      onsubmit: async (event) => {
        event.preventDefault();
        if (!pass.input.value) return pass.fail('Escribe tu contraseña.');
        submit.disabled = true;
        try {
          const payload = await api.renewRecovery({ password: pass.input.value });
          store.apply(payload);
          paint();
          await showRecoveryCode(payload.result.recoveryCode, 'renewed');
        } catch (error) {
          if (error instanceof ApiError && error.status === 401) {
            store.clear();
            return;
          }
          pass.fail(error instanceof ApiError ? error.message : 'Algo ha fallado. Inténtalo de nuevo.');
          submit.disabled = false;
        }
      },
    },
      note('Para crear un código, escribe la contraseña con la que entras.'),
      pass.wrap,
      h('div', { class: 'row' },
        submit,
        h('button', { class: 'btn btn-quiet btn-small', type: 'button', onclick: paint }, 'Cancelar'),
      ),
    );
    box.replaceChildren(form);
    pass.input.focus();
  }

  paint();
  return box;
}
