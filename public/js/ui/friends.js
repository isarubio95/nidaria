// Amigos: los regalos que te han dejado, las solicitudes, la lista para
// ir de visita, añadir a alguien por su código y el tuyo para compartir.

import { h, icon, duration } from '../dom.js';
import { api } from '../api.js';
import { store, run } from '../store.js';
import { getItem } from '/shared/catalog.js';
import { normalizeFriendCode } from '/shared/social.js';
import { openSheet } from './sheet.js';
import { dialog } from './dialog.js';
import { toast } from './toast.js';
import { visitFriend } from './visit.js';
import { creatureThumb, itemThumb, lazyThumbs } from '../scene/thumbs.js';
import { sfx } from '../audio/sfx.js';

const ago = (at) => {
  const ms = store.now() - at;
  return ms < 60 * 1000 ? 'hace un momento' : `hace ${duration(ms)}`;
};

// Quién vive en la habitación de un amigo, según su lista.
function tenant(creature) {
  if (!creature) return 'Aún no tiene criatura';
  if (!creature.alive) return 'Esperando a su próxima criatura';
  return `${creature.name} · ${creature.stage === 'huevo' ? 'Huevo' : creature.formName}`;
}

function copy(text, done) {
  if (!navigator.clipboard) {
    toast('No se ha podido copiar. Apúntalo a mano.', 'error');
    return;
  }
  navigator.clipboard.writeText(text).then(() => toast(done), () => toast('No se ha podido copiar. Apúntalo a mano.', 'error'));
}

async function shareCode(code) {
  const text = `Añádeme en Nidaria: mi código de amigo es ${code}.`;
  if (navigator.share) {
    try {
      await navigator.share({ title: 'Nidaria', text, url: location.origin });
      return;
    } catch (error) {
      if (error?.name === 'AbortError') return;
    }
  }
  copy(text, 'Copiado. Pégalo donde quieras mandarlo.');
}

export function openFriends() {
  const body = h('div', { class: 'friends' }, h('p', { class: 'memory-note' }, 'Buscando a tus amigos…'));
  let data = null;
  let stop = null;
  let gone = false;
  const sheet = openSheet({
    title: 'Amigos',
    body,
    onClose: () => {
      gone = true;
      stop?.();
    },
  });

  async function load() {
    const payload = await run(api.friends());
    if (gone) return;
    if (!payload) {
      if (!data) body.replaceChildren(h('p', { class: 'memory-note' }, 'No se han podido cargar tus amigos. Cierra y vuelve a intentarlo.'));
      return;
    }
    data = payload.result;
    render();
  }

  // Desactiva el botón mientras dura la llamada y recarga la lista al acabar.
  const busy = (fn) => async (event) => {
    const button = event.currentTarget;
    button.disabled = true;
    const ok = await fn();
    if (gone) return;
    if (ok) await load();
    else button.disabled = false;
  };

  async function openGift(gift) {
    const payload = await run(api.openGift({ id: gift.id }));
    if (!payload) return false;
    sfx('vinculo');
    const item = getItem(payload.result.itemId);
    await dialog({
      title: `Un regalo de ${payload.result.from}`,
      text: `Te ha dejado: ${item?.name ?? 'algo'}. Ya está entre tus cosas.`,
      art: itemThumb(payload.result.itemId) || null,
      actions: [{ label: '¡Gracias!', value: true, tone: 'sun' }],
    });
    return true;
  }

  async function accept(request) {
    const payload = await run(api.acceptFriend({ id: request.id }));
    if (payload) toast(`${payload.result.name} y tú ya sois amigos.`);
    return Boolean(payload);
  }

  async function forget(person, done) {
    const payload = await run(api.removeFriend({ id: person.id }));
    if (payload && done) toast(done);
    return Boolean(payload);
  }

  async function renewCode() {
    const yes = await dialog({
      title: '¿Cambiar tu código?',
      text: 'El de ahora dejará de valer. Tus amigos y las solicitudes que ya tienes se quedan como están.',
      actions: [{ label: 'Cambiar el código', value: true, tone: 'pink' }, { label: 'Ahora no', value: false }],
    });
    if (!yes) return false;
    const payload = await run(api.renewFriendCode());
    if (payload) toast('Ya tienes un código nuevo.');
    return Boolean(payload);
  }

  async function go(friend, button) {
    button.disabled = true;
    const ok = await visitFriend(friend.id);
    if (!ok && !gone) button.disabled = false;
  }

  function addForm() {
    const input = h('input', {
      type: 'text', autocomplete: 'off', autocapitalize: 'characters', spellcheck: 'false',
      maxlength: '12', placeholder: 'K7QM-4XPA', 'aria-label': 'Código de amigo',
    });
    const submit = h('button', { class: 'btn btn-pink', type: 'submit' }, 'Añadir');
    return h('form', {
      class: 'add-friend',
      onsubmit: async (event) => {
        event.preventDefault();
        if (!normalizeFriendCode(input.value)) {
          toast('Un código de amigo tiene 8 letras y números, como K7QM-4XPA.', 'error');
          input.focus();
          return;
        }
        submit.disabled = true;
        const payload = await run(api.addFriend({ code: input.value }));
        submit.disabled = false;
        if (!payload || gone) return;
        const { name, friends } = payload.result;
        toast(friends ? `${name} y tú ya sois amigos.` : `Solicitud enviada a ${name}. Cuando la acepte, podréis visitaros.`, 'info', 3600);
        await load();
      },
    }, h('div', { class: 't-input' }, input), submit);
  }

  function render() {
    stop?.();
    const jobs = [];
    const thumb = (make) => {
      if (!make) return h('span', { class: 'friend-avatar' }, icon('home'));
      const img = h('img', { alt: '', width: '56', height: '56' });
      jobs.push({ img, make });
      return h('span', { class: 'friend-avatar' }, img);
    };
    const title = (text) => h('h3', { class: 'section-title' }, text);
    const list = (label, rows) => h('ul', { class: 'friend-list', 'aria-label': label }, rows);
    const text = (name, ...lines) => h('span', { class: 'memory-text' }, h('strong', {}, name), ...lines.map((line) => line && h('small', {}, line)));

    const gifts = data.gifts.map((gift) => h('li', { class: 'friend-row' },
      h('span', { class: 'friend-avatar friend-gift' }, icon('gift')),
      text(`Un regalo de ${gift.from}`, ago(gift.at)),
      h('button', { class: 'btn btn-sun btn-small', type: 'button', onclick: busy(() => openGift(gift)) }, 'Abrir'),
    ));

    const incoming = data.incoming.map((request) => h('li', { class: 'friend-row' },
      h('span', { class: 'friend-avatar' }, icon('friends')),
      text(request.name, `Te lo pidió ${ago(request.at)}`),
      h('span', { class: 'friend-actions' },
        h('button', { class: 'btn btn-mint btn-small', type: 'button', onclick: busy(() => accept(request)) }, 'Aceptar'),
        h('button', {
          class: 'btn btn-quiet btn-small', type: 'button', 'aria-label': `Rechazar a ${request.name}`,
          onclick: busy(() => forget(request)),
        }, 'No'),
      ),
    ));

    const friends = data.friends.map((friend) => {
      const creature = friend.creature?.alive ? friend.creature : null;
      return h('li', {},
        h('button', { class: 'memory friend', type: 'button', onclick: (event) => go(friend, event.currentTarget) },
          thumb(creature ? () => creatureThumb(creature.formId, creature.genes) : null),
          text(
            friend.name,
            tenant(friend.creature),
            friend.visitedAt ? `Vino a verte ${ago(friend.visitedAt)}` : null,
            friend.giftedToday ? 'Hoy ya le has dejado un regalo' : null,
          ),
          h('span', { class: 'friend-go' }, 'Visitar'),
        ));
    });

    const outgoing = data.outgoing.map((request) => h('li', { class: 'friend-row' },
      h('span', { class: 'friend-avatar' }, icon('clock')),
      text(request.name, `Se lo pediste ${ago(request.at)}`),
      h('button', {
        class: 'btn btn-quiet btn-small', type: 'button', 'aria-label': `Cancelar la solicitud a ${request.name}`,
        onclick: busy(() => forget(request, `Has cancelado la solicitud a ${request.name}.`)),
      }, 'Cancelar'),
    ));

    const code = store.state?.social?.code ?? '';
    body.replaceChildren(
      ...(gifts.length ? [title('Regalos para ti'), list('Regalos sin abrir', gifts)] : []),
      ...(incoming.length ? [title('Quieren ser tus amigos'), list('Solicitudes', incoming)] : []),
      title(friends.length ? `Tus amigos · ${friends.length} de ${data.max}` : 'Tus amigos'),
      friends.length
        ? list('Tus amigos', friends)
        : h('p', { class: 'memory-note' }, 'Todavía no tienes amigos aquí. Pásale tu código a alguien o escribe abajo el suyo. Entre amigos os podéis visitar y dejaros un regalo cada día.'),
      title('Añadir a alguien'),
      addForm(),
      h('p', { class: 'field-hint friend-hint' }, 'Escribe su código de amigo. Le llegará una solicitud y, cuando la acepte, podréis visitaros.'),
      title('Tu código'),
      h('p', { class: 'recovery-code friend-code num', translate: 'no', 'aria-label': `Tu código: ${code.split('').join(' ')}` }, code),
      h('div', { class: 'friend-code-actions' },
        h('button', { class: 'btn btn-quiet', type: 'button', onclick: () => shareCode(code) }, 'Compartir'),
        h('button', { class: 'btn btn-quiet', type: 'button', onclick: () => copy(code, 'Código copiado.') }, 'Copiar'),
      ),
      h('button', { class: 'text-link', type: 'button', onclick: busy(renewCode) }, 'Cambiar mi código'),
      ...(outgoing.length ? [title('Esperando respuesta'), list('Solicitudes enviadas', outgoing)] : []),
    );
    stop = lazyThumbs(jobs);
  }

  load();
  return sheet;
}
