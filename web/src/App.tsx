import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { api, ApiError, AUTH_LOST, post, type Me } from './api';
import { Join } from './auth/Join';
import { Login } from './auth/Login';
import { Reset } from './auth/Reset';
import { Settings } from './auth/Settings';
import { Setup } from './auth/Setup';
import { Feed, useFeed } from './feed';
import { setZone } from './format';
import { ParentApp } from './parent/ParentApp';
import { useStack } from './platform';
import { StudentApp } from './student/StudentApp';
import { TutorApp } from './tutor/TutorApp';
import { useDayTheme } from './theme/useDayTheme';
import { AccountContext, NavContext, Screen, ThemeContext, ToastHost, Skeletons } from './ui';

const CABINET = { TUTOR: 'кабинет репетитора', STUDENT: 'кабинет ученика', PARENT: 'кабинет родителя' } as const;

/** Экраны, на которые ведут уведомления (/hw/12, /lesson/5…): такой адрес открывает сайт сразу на нужном месте. */
const APP_LINK = /^\/(feed|today|schedule|hw|materials|lesson|review|students|reports)(\/\d+)?\/?$/;

type Overlay = 'settings' | 'feed';

const client = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // 401/403/404 повторять бессмысленно; остальное — один повтор (сеть).
      retry: (count, e) => !(e instanceof ApiError && [401, 403, 404].includes(e.status)) && count < 1,
    },
  },
});

/** Открытые страницы по ссылкам: приглашение, новый пароль, первая настройка. */
function publicPage(path: string) {
  const m = path.match(/^\/(join|reset|setup)\/([A-Za-z0-9_-]{8,64})\/?$/);
  if (!m) return null;
  const code = m[2];
  if (m[1] === 'join') return <Join code={code} />;
  if (m[1] === 'reset') return <Reset code={code} />;
  return <Setup code={code} />;
}

function Root() {
  const theme = useDayTheme();
  const page = publicPage(window.location.pathname);
  const me = useQuery({ queryKey: ['me'], queryFn: () => api<Me>('/api/me'), enabled: !page });
  const overlay = useStack<Overlay>('overlay');
  const feed = useFeed(!!me.data);
  if (me.data) setZone(me.data.timezone);

  // Ссылка, которую должно открыть приложение. Адрес из строки браузера (нажали на уведомление, когда
  // сайт был закрыт) забираем один раз и возвращаем строке «/»: дальше экраны живут в истории браузера.
  const [link, setLink] = useState<string | null>(() => {
    const path = window.location.pathname;
    if (page || !APP_LINK.test(path)) return null;
    history.replaceState(history.state, '', '/');
    return path;
  });
  const waiting = useRef<string | null>(null);

  const openUrl = (url: string) => {
    if (url === '/feed') {
      if (overlay.top !== 'feed') overlay.push('feed');
    } else if (overlay.top) {
      // Сначала закрываем настройки или ленту, потом приложение откроет экран (см. эффект ниже).
      waiting.current = url;
      overlay.clear();
    } else {
      setLink(url);
    }
  };
  const openRef = useRef(openUrl);
  openRef.current = openUrl;

  useEffect(() => {
    if (!overlay.top && waiting.current) {
      setLink(waiting.current);
      waiting.current = null;
    }
    window.scrollTo(0, 0);
  }, [overlay.top]);

  useEffect(() => {
    if (link === '/feed' && me.data) {
      setLink(null);
      overlay.push('feed');
    }
  }, [link, me.data]);

  // Service worker: пришёл push — открытая вкладка обновляет данные; нажали на уведомление — нужный экран.
  useEffect(() => {
    if (!('serviceWorker' in navigator)) return;
    const onMessage = (e: MessageEvent) => {
      if (e.data?.type === 'push') client.invalidateQueries();
      if (e.data?.type === 'open' && typeof e.data.url === 'string') openRef.current(e.data.url);
    };
    navigator.serviceWorker.addEventListener('message', onMessage);
    return () => navigator.serviceWorker.removeEventListener('message', onMessage);
  }, []);

  // Сессия истекла или закрыта на другом устройстве — перепроверяем вход, покажется экран входа.
  useEffect(() => {
    const lost = () => client.invalidateQueries({ queryKey: ['me'] });
    window.addEventListener(AUTH_LOST, lost);
    return () => window.removeEventListener(AUTH_LOST, lost);
  }, []);

  const greeting = (
    <>
      {theme.sky.greeting}
      {me.data && <>,<br />{me.data.firstName}</>}
    </>
  );

  let body;
  if (page) {
    body = page;
  } else if (me.isError && me.error instanceof ApiError && me.error.status === 401) {
    body = <Login greeting={greeting} />;
  } else if (me.isError && me.error instanceof ApiError && me.error.code === 'no_consent') {
    body = (
      <Screen tall caps="Доступ закрыт" title={greeting}>
        <section className="card rise empty">
          <div className="empty-title">Вы отозвали согласие на обработку данных</div>
          <div className="sub">Чтобы вернуться, попросите у репетитора новую ссылку-приглашение.</div>
          <button type="button" className="btn soft" style={{ marginTop: 12 }}
                  onClick={() => post('/api/auth/logout').finally(() => window.location.replace('/'))}>Выйти</button>
        </section>
      </Screen>
    );
  } else if (me.data) {
    // Ссылку получает приложение, только когда поверх него ничего не открыто.
    const appLink = link && link !== '/feed' && !overlay.top ? link : null;
    const linkDone = () => setLink(null);
    const app = me.data.role === 'STUDENT'
      ? <StudentApp studentId={me.data.userId} title={greeting} link={appLink} onLink={linkDone} />
      : me.data.role === 'PARENT' ? <ParentApp me={me.data} title={greeting} link={appLink} onLink={linkDone} />
        : <TutorApp title={greeting} link={appLink} onLink={linkDone} />;
    const openSettings = () => overlay.push('settings');
    const nav = { onSettings: openSettings, onFeed: () => overlay.push('feed'), unread: feed.data?.unread ?? 0 };
    // Приложение остаётся под настройками и лентой (скрытым): после «Назад» всё на своих местах.
    body = (
      <AccountContext.Provider value={{ name: me.data.displayName, role: CABINET[me.data.role], onSettings: openSettings }}>
        <div className="app-layer" hidden={!!overlay.top}>
          <NavContext.Provider value={nav}>{app}</NavContext.Provider>
        </div>
        {overlay.top && (
          <NavContext.Provider value={{ onBack: overlay.pop }}>
            {overlay.top === 'settings' ? <Settings me={me.data} /> : <Feed onOpen={openUrl} />}
          </NavContext.Provider>
        )}
      </AccountContext.Provider>
    );
  } else {
    body = (
      <Screen tall caps={`Сейчас ${theme.sky.clock}`} title={greeting}>
        {me.isPending && <Skeletons count={2} />}
        {me.isError && (
          <section className="card rise empty">
            <div className="empty-title">Не удалось загрузить</div>
            <div className="sub">Проверьте интернет и обновите страницу.</div>
          </section>
        )}
      </Screen>
    );
  }

  return (
    <ThemeContext.Provider value={theme}>
      <ToastHost>
        <div className="app">{body}</div>
      </ToastHost>
    </ThemeContext.Provider>
  );
}

export function App() {
  return (
    <QueryClientProvider client={client}>
      <Root />
    </QueryClientProvider>
  );
}
