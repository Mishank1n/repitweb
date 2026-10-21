import { createContext, useContext, useEffect, useState, type InputHTMLAttributes, type ReactNode } from 'react';
import { IconBack, IconBell, IconGear } from './icons';
import { haptic } from './platform';
import { Scene } from './theme/Scene';
import { ThemeToggle } from './theme/ThemeToggle';
import type { Override, Sky } from './theme/sky';

// ---------- тема: доступна всем экранам ----------

type Theme = { sky: Sky; override: Override; pin: (v: Override) => void };
export const ThemeContext = createContext<Theme | null>(null);
const useTheme = () => useContext(ThemeContext)!;

// ---------- навигация: «Назад» на вложенных экранах, настройки и уведомления на главных ----------

type Nav = { onBack?: () => void; onSettings?: () => void; onFeed?: () => void; unread?: number };
export const NavContext = createContext<Nav>({});

// ---------- каркас экрана: пейзаж, заголовок, лист ----------

/** С какой длины заголовок набирается мельче (названия групп и ДЗ бывают до 100 символов). */
const LONG_TITLE = 24;
/** Длиннее этого заголовок в шапке обрезается — полное название показывает сам экран. */
export const CLIPPED_TITLE = 60;

/**
 * tall — главные экраны (много неба), иначе — вложенные и списки (горы покороче).
 * narrow — формы (вход, приглашение, настройки): на компьютере узкая колонка по центру.
 */
export function Screen({ caps, title, tall = false, narrow = false, children }: {
  caps: ReactNode;
  title: ReactNode;
  tall?: boolean;
  narrow?: boolean;
  children: ReactNode;
}) {
  const { sky, override, pin } = useTheme();
  const { onBack, onSettings, onFeed, unread = 0 } = useContext(NavContext);
  return (
    <div className={narrow ? 'screen narrow' : 'screen'}>
      <Scene height={tall ? 330 : 220} sky={sky.style} />
      <header className="header" style={{ height: tall ? 196 : 150 }}>
        <div className="rise" style={{ minWidth: 0 }}>
          {onBack && (
            <button type="button" className="back" onClick={() => { haptic('select'); onBack(); }}>
              <IconBack size={16} /> Назад
            </button>
          )}
          <div className="caps">{caps}</div>
          <h1 className={(tall ? 'title' : 'title small') + (typeof title === 'string' && title.length > LONG_TITLE ? ' long' : '')}>
            {title}
          </h1>
        </div>
        <div className="header-tools">
          {onFeed && !onBack && (
            <button type="button" className="icon-btn glass bell" onClick={() => { haptic('select'); onFeed(); }}
                    aria-label={unread ? `Уведомления, новых: ${unread}` : 'Уведомления'}>
              <IconBell />
              {unread > 0 && <span className="bell-badge" aria-hidden="true">{unread > 99 ? '99+' : unread}</span>}
            </button>
          )}
          {onSettings && !onBack && (
            <button type="button" className="icon-btn glass gear" aria-label="Настройки" onClick={() => { haptic('select'); onSettings(); }}>
              <IconGear />
            </button>
          )}
          <ThemeToggle night={sky.uiNight} override={override} pin={pin} />
        </div>
      </header>
      <main className="sheet" style={{ marginTop: tall ? 76 : 30 }}>
        {children}
      </main>
    </div>
  );
}

// ---------- блок над содержимым вкладок (у родителя — выбор ребёнка) ----------

export const TopContext = createContext<ReactNode>(null);
export const Top = () => <>{useContext(TopContext)}</>;

// ---------- кто вошёл: для боковой панели на компьютере ----------

type Account = { name: string; role: string; onSettings: () => void };
export const AccountContext = createContext<Account | null>(null);

// ---------- разделы: нижняя панель на телефоне, боковая — на компьютере ----------

export type Tab = { id: string; label: string; icon: ReactNode; badge?: number };

/**
 * nested — открыт вложенный экран: на телефоне панель прячется (там «Назад»), на компьютере остаётся,
 * и нажатие на раздел (даже текущий) возвращает к нему.
 */
export function TabBar({ tabs, active, nested = false, onChange }: {
  tabs: Tab[];
  active: string;
  nested?: boolean;
  onChange: (id: string) => void;
}) {
  const account = useContext(AccountContext);
  return (
    <nav className={nested ? 'tabbar nested' : 'tabbar'} aria-label="Разделы">
      <div className="brand side-only">
        <span className="brand-mark" aria-hidden="true" />
        <span>
          <span className="brand-name">Репетитор</span>
          {account && <span className="foot brand-role">{account.role}</span>}
        </span>
      </div>
      {tabs.map((t) => (
        <button
          key={t.id}
          type="button"
          className={t.id === active && !nested ? 'tab on' : t.id === active ? 'tab here' : 'tab'}
          aria-current={t.id === active ? 'page' : undefined}
          title={t.label}
          onClick={() => {
            if (t.id !== active || nested) {
              haptic('select');
              onChange(t.id);
            }
          }}
        >
          <span className="pill">{t.icon}</span>
          {t.badge ? <span className="badge" aria-hidden="true">{t.badge}</span> : null}
          <span className="tab-label">{t.label}</span>
        </button>
      ))}
      {account && (
        <button type="button" className="side-account side-only" onClick={() => { haptic('select'); account.onSettings(); }}>
          <span className="avatar side-avatar" aria-hidden="true">{account.name.charAt(0)}</span>
          <span className="row-main">
            <span className="row-title">{account.name}</span>
            <span className="foot">Настройки</span>
          </span>
          <IconGear size={18} />
        </button>
      )}
    </nav>
  );
}

// ---------- мелкие элементы ----------

/** Кольцо абонемента: заполняется при появлении. */
export function Ring({ used, total, size = 58 }: { used: number; total: number; size?: number }) {
  const r = 24;
  const c = 2 * Math.PI * r;
  const offset = c * (1 - Math.min(1, used / Math.max(1, total)));
  return (
    <svg width={size} height={size} viewBox="0 0 58 58" role="img" aria-label={`Проведено ${used} из ${total} занятий`}>
      <defs>
        <linearGradient id="ringGrad" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#F7BBA4" />
          <stop offset="1" stopColor="#E77A86" />
        </linearGradient>
      </defs>
      <circle cx="29" cy="29" r={r} fill="none" strokeWidth="9" style={{ stroke: 'var(--track)' }} />
      <circle
        className="ringfill"
        cx="29" cy="29" r={r} fill="none" stroke="url(#ringGrad)" strokeWidth="9" strokeLinecap="round"
        strokeDasharray={c} strokeDashoffset={offset} transform="rotate(-90 29 29)"
        style={{ ['--ring-c' as string]: c }}
      />
    </svg>
  );
}

export const Chevron = () => (
  <svg className="chev" width="8" height="14" viewBox="0 0 8 14" fill="none" stroke="currentColor" strokeWidth="2.2"
       strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M1 1l6 6-6 6" />
  </svg>
);

export function Empty({ title, text }: { title: string; text: string }) {
  return (
    <section className="card rise empty">
      <div className="empty-title">{title}</div>
      <div className="sub">{text}</div>
    </section>
  );
}

export function Skeletons({ count = 3 }: { count?: number }) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => (
        <div key={i} className="skeleton" style={{ animationDelay: `${i * 0.12}s` }} />
      ))}
    </>
  );
}

export function ErrorCard({ onRetry }: { onRetry: () => void }) {
  return (
    <section className="card rise empty">
      <div className="empty-title">Не удалось загрузить</div>
      <div className="sub">Проверьте интернет. Если не поможет — закройте и откройте приложение заново.</div>
      <button type="button" className="btn soft" style={{ marginTop: 12, height: 44 }} onClick={onRetry}>
        Повторить
      </button>
    </section>
  );
}

// ---------- всплывающее подтверждение ----------

const ToastContext = createContext<(text: string) => void>(() => {});
export const useToast = () => useContext(ToastContext);

export function ToastHost({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<{ text: string; key: number } | null>(null);
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2600);
    return () => clearTimeout(t);
  }, [toast]);
  return (
    <ToastContext.Provider value={(text) => setToast({ text, key: Date.now() })}>
      {children}
      {toast && (
        <div key={toast.key} className="toast" role="status">
          {toast.text}
        </div>
      )}
    </ToastContext.Provider>
  );
}

// ---------- фото и просмотр на весь экран ----------

/** Превью фото: сервер отдаёт его только тем, кому можно (cookie сессии браузер отправляет сам). */
export function Photo({ src, label }: { src: string; label: string }) {
  const [state, setState] = useState<'wait' | 'ok' | 'fail'>('wait');
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" className="thumb" aria-label={label} onClick={() => state === 'ok' && setOpen(true)}
              disabled={state !== 'ok'}>
        <img src={src} alt="" onLoad={() => setState('ok')} onError={() => setState('fail')}
             style={state === 'ok' ? undefined : { display: 'none' }} />
        {state !== 'ok' && <span className={state === 'fail' ? 'thumb-fail' : 'thumb-wait'} />}
      </button>
      {open && (
        <div className="lightbox" role="dialog" aria-label={label} onClick={() => setOpen(false)}>
          <img src={src} alt={label} />
        </div>
      )}
    </>
  );
}

// ---------- поля форм ----------

export function Field({ label, hint, ...input }: { label: string; hint?: ReactNode } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="field">
      <span className="label">{label}</span>
      <input className="input" {...input} />
      {hint && <span className="foot">{hint}</span>}
    </label>
  );
}

export function FormError({ text }: { text: string | null }) {
  return text ? <p className="form-error" role="alert">{text}</p> : null;
}
