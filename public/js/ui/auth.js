// Entrada: entrar con una cuenta o crear una nueva.

import { h } from '../dom.js';
import { api, ApiError, localTimeZone } from '../api.js';
import { store } from '../store.js';
import { tabs } from './widgets.js';

function field({ id, label, type, autocomplete, hint }) {
  const input = h('input', { id, name: id, type, autocomplete, required: true, autocapitalize: 'none', spellcheck: 'false' });
  const box = h('div', { class: 't-input' }, input);
  const error = h('p', { class: 't-error-msg', id: `${id}-error`, role: 'alert' });
  input.setAttribute('aria-describedby', `${id}-error`);
  const wrap = h('div', { class: 'field t-input-wrap' },
    h('label', { for: id }, label),
    box,
    hint ? h('p', { class: 'field-hint' }, hint) : null,
    error,
  );
  const clearError = () => {
    wrap.classList.remove('is-error');
    box.classList.remove('is-error');
  };
  input.addEventListener('input', clearError);
  return {
    wrap,
    input,
    fail(message) {
      error.textContent = message;
      wrap.classList.add('is-error');
      box.classList.add('is-error');
      box.classList.remove('is-shaking');
      void box.offsetWidth;
      box.classList.add('is-shaking');
      setTimeout(() => box.classList.remove('is-shaking'), 300);
    },
    clearError,
  };
}

export function mountAuth(app) {
  let mode = 'login';
  const name = field({ id: 'nombre', label: 'Nombre', type: 'text', autocomplete: 'username' });
  const pass = field({ id: 'clave', label: 'Contraseña', type: 'password', autocomplete: 'current-password' });
  const hint = h('p', { class: 'field-hint', hidden: true }, 'Al menos 8 caracteres. No hay forma de recuperarla, así que apúntala.');
  pass.wrap.insertBefore(hint, pass.wrap.lastElementChild);
  const submit = h('button', { class: 'btn btn-pink btn-wide', type: 'submit' }, 'Entrar');
  const lead = h('p', { class: 'lead' }, 'Cría una criatura única. Lo que hagas decide en qué se convierte.');

  const setMode = (next) => {
    mode = next;
    submit.textContent = mode === 'login' ? 'Entrar' : 'Crear cuenta';
    pass.input.autocomplete = mode === 'login' ? 'current-password' : 'new-password';
    hint.hidden = mode === 'login';
    name.clearError();
    pass.clearError();
  };

  const form = h('form', {
    class: 'card-screen', novalidate: true,
    onsubmit: async (event) => {
      event.preventDefault();
      const user = name.input.value.trim();
      const secret = pass.input.value;
      if (user.length < 3) return name.fail('Escribe un nombre de al menos 3 letras.');
      if (secret.length < 8) return pass.fail('La contraseña debe tener al menos 8 caracteres.');
      submit.disabled = true;
      try {
        const payload = await (mode === 'login' ? api.login : api.register)({ name: user, password: secret, timeZone: localTimeZone() });
        store.apply(payload);
      } catch (error) {
        const message = error instanceof ApiError ? error.message : 'Algo ha fallado. Inténtalo de nuevo.';
        if (/nombre/i.test(message) && !/contraseña/i.test(message)) name.fail(message);
        else pass.fail(message);
      } finally {
        submit.disabled = false;
      }
    },
  },
    h('h1', { class: 'wordmark' }, 'Nidaria'),
    lead,
    tabs([{ id: 'login', label: 'Ya tengo cuenta' }, { id: 'register', label: 'Crear cuenta' }], mode, setMode, 'Tipo de entrada'),
    name.wrap,
    pass.wrap,
    submit,
  );
  app.replaceChildren(form);
  return {
    focusTop: () => form.getBoundingClientRect().top,
    destroy() {},
  };
}
