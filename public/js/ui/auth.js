// Entrada: entrar con una cuenta, crear una nueva o recuperarla con el
// código de recuperación si se ha olvidado la contraseña.

import { h } from '../dom.js';
import { api, ApiError, localTimeZone } from '../api.js';
import { store } from '../store.js';
import { tabs, field } from './widgets.js';
import { showRecoveryCode } from './recovery.js';

const LEAD = {
  login: 'Cría una criatura única. Lo que hagas decide en qué se convierte.',
  register: 'Cría una criatura única. Lo que hagas decide en qué se convierte.',
  recover: 'Escribe tu nombre, el código que te dimos al crear la cuenta y una contraseña nueva.',
};

export function mountAuth(app) {
  let mode = 'login';
  const name = field({ id: 'nombre', label: 'Nombre', type: 'text', autocomplete: 'username' });
  const code = field({ id: 'codigo', label: 'Código de recuperación', type: 'text', autocomplete: 'off' });
  code.input.autocapitalize = 'characters';
  code.input.classList.add('code', 'num');
  code.input.placeholder = 'ABCD-EFGH-JKMN-PQRS';
  const pass = field({ id: 'clave', label: 'Contraseña', type: 'password', autocomplete: 'current-password' });
  const hint = h('p', { class: 'field-hint', hidden: true });
  pass.wrap.insertBefore(hint, pass.wrap.lastElementChild);
  const submit = h('button', { class: 'btn btn-primary btn-wide', type: 'submit' }, 'Entrar');
  const title = h('h1', { class: 'wordmark' }, 'Nidaria');
  const lead = h('p', { class: 'lead' }, LEAD.login);
  const switcher = tabs([{ id: 'login', label: 'Ya tengo cuenta' }, { id: 'register', label: 'Crear cuenta' }], mode, (next) => setMode(next), 'Tipo de entrada');
  const forgot = h('button', { class: 'text-link', type: 'button', onclick: () => setMode('recover') }, '¿Has olvidado la contraseña?');
  const back = h('button', { class: 'text-link', type: 'button', hidden: true, onclick: () => setMode('login') }, 'Volver a entrar con la contraseña');

  function setMode(next) {
    mode = next;
    const recover = mode === 'recover';
    title.textContent = recover ? 'Recuperar la cuenta' : 'Nidaria';
    title.className = recover ? '' : 'wordmark';
    lead.textContent = LEAD[mode];
    switcher.hidden = recover;
    code.wrap.hidden = !recover;
    forgot.hidden = mode !== 'login';
    back.hidden = !recover;
    pass.label.textContent = recover ? 'Contraseña nueva' : 'Contraseña';
    pass.input.autocomplete = mode === 'login' ? 'current-password' : 'new-password';
    hint.hidden = mode === 'login';
    hint.textContent = recover
      ? 'Al menos 8 caracteres. Al entrar te daremos un código nuevo.'
      : 'Al menos 8 caracteres. Al crear la cuenta te daremos un código para recuperarla si la olvidas.';
    submit.textContent = { login: 'Entrar', register: 'Crear cuenta', recover: 'Cambiar contraseña y entrar' }[mode];
    for (const item of [name, code, pass]) item.clearError();
    if (recover) code.input.value = '';
  }

  function showError(message) {
    if (/código/i.test(message)) code.fail(message);
    else if (/nombre/i.test(message) && !/contraseña/i.test(message)) name.fail(message);
    else pass.fail(message);
  }

  const form = h('form', {
    class: 'card-screen', novalidate: true,
    onsubmit: async (event) => {
      event.preventDefault();
      const user = name.input.value.trim();
      const secret = pass.input.value;
      if (user.length < 3) return name.fail('Escribe un nombre de al menos 3 letras.');
      if (mode === 'recover' && code.input.value.replace(/[\s-]/g, '').length !== 16) {
        return code.fail('El código tiene 16 letras y números, como ABCD-EFGH-JKMN-PQRS.');
      }
      if (secret.length < 8) return pass.fail('La contraseña debe tener al menos 8 caracteres.');
      submit.disabled = true;
      try {
        const body = { name: user, password: secret, timeZone: localTimeZone() };
        if (mode === 'recover') body.code = code.input.value;
        const call = { login: api.login, register: api.register, recover: api.recover }[mode];
        const was = mode;
        const payload = await call(body);
        store.apply(payload);
        const recoveryCode = payload.result?.recoveryCode;
        if (recoveryCode) showRecoveryCode(recoveryCode, was === 'register' ? 'register' : 'recovered');
      } catch (error) {
        showError(error instanceof ApiError ? error.message : 'Algo ha fallado. Inténtalo de nuevo.');
      } finally {
        submit.disabled = false;
      }
    },
  },
    title,
    lead,
    switcher,
    name.wrap,
    code.wrap,
    pass.wrap,
    submit,
    forgot,
    back,
  );
  setMode('login');
  app.replaceChildren(form);
  return {
    focusTop: () => form.getBoundingClientRect().top,
    destroy() {},
  };
}
