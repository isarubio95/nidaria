// App instalable y avisos al móvil, del lado del navegador: registra el
// service worker, guarda la invitación a instalar y suscribe este
// dispositivo a los avisos del servidor.

import { api } from './api.js';

const isIos = () => /iPad|iPhone|iPod/.test(navigator.userAgent)
  || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);

export const standalone = () => matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;

// ── Instalar ──

let installEvent = null;
const watchers = new Set();
const notify = () => {
  for (const fn of watchers) fn();
};

window.addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault();
  installEvent = event;
  notify();
});
window.addEventListener('appinstalled', () => {
  installEvent = null;
  notify();
});

export function onInstallChange(fn) {
  watchers.add(fn);
  return () => watchers.delete(fn);
}

// 'prompt': el navegador deja instalar con un botón. 'ios': hay que
// hacerlo a mano desde Compartir. null: ya está instalada o no se puede.
export function installMode() {
  if (standalone()) return null;
  if (installEvent) return 'prompt';
  if (isIos()) return 'ios';
  return null;
}

export async function install() {
  const event = installEvent;
  if (!event) return false;
  installEvent = null;
  notify();
  await event.prompt();
  const choice = await event.userChoice;
  return choice.outcome === 'accepted';
}

// ── Service worker ──

let registration = null;

export function registerWorker() {
  if (!window.isSecureContext || !('serviceWorker' in navigator)) return Promise.resolve(null);
  registration ??= navigator.serviceWorker.register('/sw.js').catch((error) => {
    console.warn('Service worker:', error);
    registration = null;
    return null;
  });
  return registration;
}

// Al volver al juego, los avisos que quedaran en la bandeja ya sobran.
export function clearNotifications() {
  registerWorker()
    .then((reg) => reg?.getNotifications())
    .then((list) => list?.forEach((notification) => notification.close()))
    .catch(() => {});
}

// ── Avisos ──

// 'ready', o por qué no se puede: 'insecure' (sin https), 'ios' (en
// iPhone hay que instalarla antes) o 'unsupported'.
export function pushSupport() {
  if (!window.isSecureContext) return 'insecure';
  if (isIos() && !standalone()) return 'ios';
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) return 'unsupported';
  return 'ready';
}

export const pushPermission = () => ('Notification' in window ? Notification.permission : 'default');

function keyBytes(base64url) {
  const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(base64url.length / 4) * 4, '=');
  return Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
}

function keyText(buffer) {
  return btoa(String.fromCharCode(...new Uint8Array(buffer))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function subscription() {
  if (pushSupport() !== 'ready') return null;
  const reg = await registerWorker();
  if (!reg) return null;
  await navigator.serviceWorker.ready;
  return reg.pushManager.getSubscription();
}

// Suscripción con la clave actual del servidor. Si el servidor cambió de
// clave, la vieja ya no sirve y se cambia por otra.
async function subscribeWith(key) {
  const reg = await registerWorker();
  if (!reg) throw new Error('Sin service worker');
  await navigator.serviceWorker.ready;
  let sub = await reg.pushManager.getSubscription();
  const own = sub?.options?.applicationServerKey;
  if (sub && own && keyText(own) !== key) {
    await sub.unsubscribe();
    sub = null;
  }
  sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: keyBytes(key) });
  await api.pushSubscribe(sub.toJSON());
  return sub;
}

export async function pushEnabled() {
  if (pushPermission() !== 'granted') return false;
  try {
    return Boolean(await subscription());
  } catch {
    return false;
  }
}

// Pide permiso y suscribe. Devuelve 'on', 'denied', 'dismissed' o 'error'.
export async function enablePush(key) {
  if (!key || pushSupport() !== 'ready') return 'error';
  const permission = await Notification.requestPermission();
  if (permission === 'denied') return 'denied';
  if (permission !== 'granted') return 'dismissed';
  try {
    await subscribeWith(key);
    return 'on';
  } catch (error) {
    console.warn('Avisos:', error);
    return 'error';
  }
}

export async function disablePush() {
  try {
    const sub = await subscription();
    if (!sub) return;
    try {
      await api.pushUnsubscribe({ endpoint: sub.endpoint });
    } finally {
      await sub.unsubscribe();
    }
  } catch (error) {
    console.warn('Avisos:', error);
  }
}

// Al entrar: si este dispositivo ya tenía los avisos, se vuelven a ligar
// a la sesión de ahora (la anterior pudo caducar).
export async function syncPush(key) {
  if (!key || !(await pushEnabled())) return;
  try {
    await subscribeWith(key);
  } catch (error) {
    console.warn('Avisos:', error);
  }
}
