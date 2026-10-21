import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { api, post, type Feed as FeedData } from './api';
import { day, time } from './format';
import { IconBell } from './icons';
import { haptic, loadSetting, saveSetting } from './platform';
import { isAndroid, isIOS, usePush, type PushState } from './push';
import { Chevron, Empty, ErrorCard, Screen, Skeletons } from './ui';

export const FEED_KEY = ['feed'];

/** Лента и счётчик непрочитанных: раз в минуту, а при push открытая вкладка обновляется сразу. */
export const useFeed = (enabled = true) =>
  useQuery({ queryKey: FEED_KEY, queryFn: () => api<FeedData>('/api/notifications'), refetchInterval: 60_000, enabled });

/** Уведомление ведёт на свой экран; отчёт родителю и прочие «просто прочитать» раскрываются здесь же. */
const opens = (url: string | null) => !!url && url !== '/feed' && url !== '/';

/** Лента уведомлений: всё, что приходило push, лежит здесь, даже если push не дошёл. */
export function Feed({ onOpen }: { onOpen: (url: string) => void }) {
  const q = useFeed();
  const client = useQueryClient();
  const fresh = useRef<Set<number> | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);

  if (q.data && !fresh.current) fresh.current = new Set(q.data.items.filter((i) => !i.read).map((i) => i.id));

  // Открыл ленту — всё прочитано; новые остаются выделенными до ухода с экрана.
  useEffect(() => {
    if (!q.data) return;
    navigator.clearAppBadge?.().catch(() => {});
    if (q.data.unread > 0) {
      post('/api/notifications/read').then(() => client.invalidateQueries({ queryKey: FEED_KEY }), () => {});
    }
  }, [q.data?.unread]);

  return (
    <Screen caps="Всё, что приходило" title="Уведомления">
      <PushCard />
      {q.isPending && <Skeletons count={4} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {q.data && q.data.items.length === 0 && (
        <Empty title="Уведомлений пока нет" text="Здесь будут напоминания о занятиях, задания, оценки и отчёты." />
      )}
      {q.data && q.data.items.length > 0 && (
        <div className="card rise list">
          {q.data.items.map((n) => {
            const isFresh = fresh.current?.has(n.id);
            const link = opens(n.url);
            const open = expanded === n.id;
            return (
              <button key={n.id} type="button" className={isFresh ? 'row feed-row fresh' : 'row feed-row'}
                      aria-expanded={link ? undefined : open}
                      onClick={() => {
                        haptic('select');
                        if (link) onOpen(n.url!);
                        else setExpanded(open ? null : n.id);
                      }}>
                <span className="row-main">
                  <span className="row-title">{n.title}</span>
                  {n.body && <span className={open ? 'feed-body open' : 'feed-body'}>{n.body}</span>}
                  <span className="foot row-sub">{day(n.createdAt)}, {time(n.createdAt)}</span>
                </span>
                {link && <Chevron />}
              </button>
            );
          })}
        </div>
      )}
    </Screen>
  );
}

const HIDE_CARD = 'push-card';

/** Что сказать человеку о push на этом устройстве. */
function explain(state: PushState): { title: string; text: string } | null {
  switch (state) {
    case 'off':
      return { title: 'Включите уведомления', text: 'Напоминания о занятиях, новые задания и оценки будут приходить сразу, даже когда сайт закрыт.' };
    case 'ios-install':
      return {
        title: 'Уведомления на iPhone',
        text: 'Добавьте сайт на экран «Домой»: кнопка «Поделиться» внизу Safari → «На экран „Домой“». Потом откройте сайт с этой иконки и включите уведомления.',
      };
    case 'in-app':
      return { title: 'Откройте сайт в браузере', text: 'Во встроенном браузере Telegram, VK и Instagram уведомления не работают. Нажмите ⋯ → «Открыть в браузере».' };
    case 'denied':
      return {
        title: 'Уведомления запрещены',
        text: isIOS
          ? 'Разрешите их: Настройки iPhone → Уведомления → Репетитор.'
          : 'Разрешите их для этого сайта: значок слева от адреса → Уведомления → Разрешить, затем обновите страницу.',
      };
    case 'unsupported':
      return { title: 'Браузер не присылает уведомления', text: 'Откройте сайт в Chrome, Safari, Firefox или Яндекс Браузере.' };
    case 'insecure':
      return { title: 'Уведомления пока недоступны', text: 'Они заработают, когда сайт откроется по защищённому адресу (https).' };
    default:
      return null;
  }
}

/** Карточка на главной и в ленте: включить уведомления или как это сделать на этом устройстве. Можно скрыть. */
export function PushCard() {
  const push = usePush();
  const [hidden, setHidden] = useState(() => loadSetting(HIDE_CARD) === 'hidden');
  const info = explain(push.state);
  if (hidden || !info || !['off', 'ios-install', 'in-app'].includes(push.state)) return null;
  const hide = () => {
    saveSetting(HIDE_CARD, 'hidden');
    setHidden(true);
  };
  return (
    <section className="card rise pad push-card">
      <div className="push-head">
        <span className="ico wait-soft"><IconBell /></span>
        <span className="row-main">
          <span className="row-title">{info.title}</span>
          <span className="foot">{info.text}</span>
        </span>
      </div>
      <div className="confirm-actions">
        <button type="button" className="btn soft" onClick={hide}>{push.state === 'off' ? 'Не сейчас' : 'Понятно'}</button>
        {push.state === 'off' && (
          <button type="button" className="btn" disabled={push.busy} onClick={push.enable}>Включить</button>
        )}
      </div>
    </section>
  );
}

/** Раздел настроек: уведомления на этом устройстве. */
export function PushSettings() {
  const push = usePush();
  const info = explain(push.state);
  return (
    <section className="card rise pad">
      <div className="status-line">
        <span className="h2">Уведомления</span>
        <span className={push.state === 'on' ? 'status ok' : 'status muted'}>
          {push.state === 'on' ? 'включены' : push.state === 'loading' ? '' : 'выключены'}
        </span>
      </div>
      {push.state === 'on' ? (
        <>
          <div className="foot">На этом устройстве. На телефоне и компьютере они включаются отдельно.</div>
          {isAndroid && (
            <div className="foot">Если уведомления опаздывают — отключите для браузера экономию батареи (особенно Xiaomi, Huawei, Honor, Samsung).</div>
          )}
          <div className="confirm-actions">
            <button type="button" className="btn soft" disabled={push.busy} onClick={push.test}>Проверить</button>
            <button type="button" className="btn soft" disabled={push.busy} onClick={push.disable}>Выключить</button>
          </div>
        </>
      ) : info && (
        <>
          <div className="foot">{push.state === 'off' ? info.text : <><b>{info.title}.</b> {info.text}</>}</div>
          {push.state === 'off' && (
            <button type="button" className="btn wide" disabled={push.busy} onClick={() => {
              saveSetting(HIDE_CARD, null);
              push.enable();
            }}>Включить уведомления</button>
          )}
        </>
      )}
    </section>
  );
}
