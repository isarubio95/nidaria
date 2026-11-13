// Cliente de la API. Cada llamada devuelve { state, events, result }
// o lanza un ApiError con un mensaje listo para enseñar.

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

async function call(method, url, body) {
  let response;
  try {
    response = await fetch(`/api${url}`, {
      method,
      headers: body ? { 'content-type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      credentials: 'same-origin',
    });
  } catch {
    throw new ApiError('No hay conexión con el servidor. Comprueba tu red e inténtalo de nuevo.', 0);
  }
  let data = null;
  try {
    data = await response.json();
  } catch {
    data = null;
  }
  if (!response.ok) throw new ApiError(data?.error ?? 'Algo ha fallado. Inténtalo de nuevo.', response.status);
  return data;
}

const post = (url) => (body) => call('POST', url, body ?? {});

// Zona horaria del dispositivo, p. ej. 'Europe/Madrid'. Decide cuándo
// cambian las misiones y cuándo es de noche para los avisos.
export function localTimeZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone ?? null;
  } catch {
    return null;
  }
}

export const api = {
  state: () => call('GET', '/state'),
  memorial: () => call('GET', '/memorial'),
  register: post('/auth/register'),
  login: post('/auth/login'),
  recover: post('/auth/recover'),
  renewRecovery: post('/account/recovery'),
  logout: post('/auth/logout'),
  pickEgg: post('/egg/pick'),
  warmEgg: post('/egg/warm'),
  feed: post('/creature/feed'),
  sleep: post('/creature/sleep'),
  bathe: post('/creature/bathe'),
  heal: post('/creature/heal'),
  pet: post('/creature/pet'),
  toy: post('/creature/toy'),
  rename: post('/creature/rename'),
  equip: post('/creature/equip'),
  decorate: post('/decor/place'),
  buy: post('/shop/buy'),
  collect: post('/income/collect'),
  startGame: post('/games/start'),
  finishGame: post('/games/finish'),
  claim: post('/daily/claim'),
  friends: () => call('GET', '/friends'),
  addFriend: post('/friends/add'),
  acceptFriend: post('/friends/accept'),
  removeFriend: post('/friends/remove'),
  renewFriendCode: post('/friends/code'),
  visit: post('/friends/visit'),
  gift: post('/friends/gift'),
  openGift: post('/gifts/open'),
  settings: post('/settings'),
  pushSubscribe: post('/push/subscribe'),
  pushUnsubscribe: post('/push/unsubscribe'),
  devSkip: post('/dev/skip'),
};
