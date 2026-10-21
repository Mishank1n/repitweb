// Service worker сайта: только уведомления. Страницы и файлы он не кэширует —
// после обновления сайта у всех сразу новая версия, а не застрявшая старая.

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (event) => event.waitUntil(self.clients.claim()));

const windows = () => self.clients.matchAll({ type: 'window', includeUncontrolled: true });

// Push от сервера: {title, body, url, unread}. Показываем уведомление, обновляем значок на иконке
// и говорим открытым вкладкам обновить данные (новое ДЗ, оценка — без перезагрузки).
self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { body: event.data ? event.data.text() : '' };
  }
  event.waitUntil((async () => {
    await self.registration.showNotification(data.title || 'Репетитор', {
      body: data.body || '',
      icon: '/icon-192.png',
      badge: '/badge-96.png',
      lang: 'ru',
      data: { url: data.url || '/' },
    });
    if (typeof data.unread === 'number' && self.navigator.setAppBadge) {
      await (data.unread > 0 ? self.navigator.setAppBadge(data.unread) : self.navigator.clearAppBadge()).catch(() => {});
    }
    for (const client of await windows()) client.postMessage({ type: 'push' });
  })());
});

// Нажатие на уведомление: открытая вкладка сайта показывает нужный экран, иначе открываем новую.
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || '/';
  event.waitUntil((async () => {
    const client = (await windows()).find((c) => new URL(c.url).origin === self.location.origin);
    if (client) {
      await client.focus();
      client.postMessage({ type: 'open', url });
    } else {
      await self.clients.openWindow(url);
    }
  })());
});

// Браузер сам сменил подписку (истекла, обновился ключ): подписываемся заново и сообщаем серверу.
self.addEventListener('pushsubscriptionchange', (event) => {
  event.waitUntil((async () => {
    const response = await fetch('/api/push/key', { credentials: 'same-origin' });
    if (!response.ok) return;
    const { key } = await response.json();
    const raw = atob(key.replace(/-/g, '+').replace(/_/g, '/'));
    const sub = await self.registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: Uint8Array.from(raw, (c) => c.charCodeAt(0)),
    });
    await fetch('/api/push/subscribe', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(sub),
    });
  })());
});
