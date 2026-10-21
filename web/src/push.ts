// Push-уведомления на этом устройстве: можно ли их включить, включены ли, включить и выключить.
import { useEffect, useState } from 'react';
import { api, errorMessage, post } from './api';
import { haptic } from './platform';
import { useToast } from './ui';

/**
 * on / off — включены или нет; denied — запрещены в настройках браузера;
 * ios-install — iPhone: уведомления только у сайта, добавленного на экран «Домой»;
 * in-app — встроенный браузер Telegram, VK, Instagram: там push не работает;
 * unsupported — браузер не умеет; insecure — открыто не по https (кроме localhost).
 */
export type PushState = 'loading' | 'on' | 'off' | 'denied' | 'ios-install' | 'in-app' | 'unsupported' | 'insecure';

const ua = navigator.userAgent;
export const isIOS = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
export const isAndroid = /Android/.test(ua);
const inApp = /Instagram|FBAN|FBAV|VKClient|Telegram|; wv\)/.test(ua);
const standalone = () =>
  matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true;
const supported = () => 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;

const bytes = (b64url: string) => {
  const raw = atob(b64url.replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(raw, (c) => c.charCodeAt(0));
};

/** Активный service worker (регистрируется при запуске сайта); ждём не дольше 10 секунд. */
async function registration(): Promise<ServiceWorkerRegistration> {
  const timeout = new Promise<never>((_, reject) =>
    setTimeout(() => reject(new Error('Сервис уведомлений не запустился — обновите страницу')), 10_000));
  return Promise.race([navigator.serviceWorker.ready, timeout]);
}

let synced = false;

async function detect(): Promise<PushState> {
  if (inApp) return 'in-app';
  if (!window.isSecureContext) return 'insecure';
  if (!supported()) return isIOS && !standalone() ? 'ios-install' : 'unsupported';
  if (Notification.permission === 'denied') return 'denied';
  const sub = await (await registration()).pushManager.getSubscription();
  if (!sub || Notification.permission !== 'granted') return 'off';
  // Раз за запуск напоминаем серверу об этой подписке: после смены аккаунта на устройстве она переходит к новому.
  if (!synced) {
    synced = true;
    post('/api/push/subscribe', sub.toJSON()).catch(() => {});
  }
  return 'on';
}

async function enable(): Promise<PushState> {
  // Разрешение спрашиваем только по нажатию: браузеры блокируют сайты, которые спрашивают сразу.
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') return permission === 'denied' ? 'denied' : 'off';
  const reg = await registration();
  const { key } = await api<{ key: string }>('/api/push/key');
  const serverKey = bytes(key);
  let sub = await reg.pushManager.getSubscription();
  const old = sub?.options.applicationServerKey;
  if (sub && (!old || new Uint8Array(old).join() !== serverKey.join())) {
    await sub.unsubscribe(); // подписка на старый ключ сервера уже не работает
    sub = null;
  }
  sub ??= await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: serverKey });
  await post('/api/push/subscribe', sub.toJSON());
  synced = true;
  return 'on';
}

/** Выключить на этом устройстве (и при выходе из аккаунта — чтобы чужие уведомления сюда не шли). */
export async function disablePush(): Promise<PushState> {
  if (!supported() || !window.isSecureContext) return 'off';
  const sub = await (await registration()).pushManager.getSubscription();
  if (sub) {
    await post('/api/push/unsubscribe', { endpoint: sub.endpoint }).catch(() => {});
    await sub.unsubscribe();
  }
  return 'off';
}

export function usePush() {
  const [state, setState] = useState<PushState>('loading');
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  useEffect(() => {
    detect().then(setState, () => setState('unsupported'));
  }, []);

  const run = async (action: () => Promise<PushState>, done?: string) => {
    setBusy(true);
    try {
      const next = await action();
      setState(next);
      if (next === 'on' && done) {
        haptic();
        toast(done);
      }
    } catch (e) {
      toast(errorMessage(e, e instanceof Error && e.message ? e.message : 'Не получилось, попробуйте ещё раз'));
    }
    setBusy(false);
  };

  return {
    state,
    busy,
    enable: () => run(enable, 'Уведомления включены'),
    disable: () => run(disablePush),
    test: () => run(async () => {
      const { devices } = await post<{ devices: number }>('/api/push/test');
      toast(devices ? 'Отправлено — уведомление придёт через пару секунд' : 'На этом аккаунте нет устройств с уведомлениями');
      return 'on';
    }),
  };
}
