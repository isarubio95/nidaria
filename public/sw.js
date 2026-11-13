// Service worker de Nidaria. Solo enseña los avisos que manda el servidor
// y abre el juego al tocarlos. No guarda nada en caché: el juego necesita
// el servidor para funcionar y así nunca se queda una versión vieja.

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data?.json() ?? {};
  } catch {
    data = { body: event.data?.text() };
  }
  event.waitUntil((async () => {
    // Con el juego abierto y a la vista, el aviso sobra.
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    if (windows.some((client) => client.visibilityState === 'visible')) return;
    await self.registration.showNotification(data.title || 'Nidaria', {
      body: data.body || '',
      icon: '/icons/icon-192.png',
      badge: '/icons/badge-96.png',
      tag: data.tag || 'nidaria',
      renotify: true,
      lang: 'es',
      data: { url: '/' },
    });
  })());
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const open = windows.find((client) => new URL(client.url).origin === self.location.origin);
    if (open) return open.focus();
    return self.clients.openWindow(event.notification.data?.url ?? '/');
  })());
});
