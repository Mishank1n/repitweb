import { haptic } from '../platform';
import type { Override } from './sky';

/** Солнце/луна: закрепляет тему вручную; «Авто» возвращает тему по времени суток. */
export function ThemeToggle({ night, override, pin }: { night: boolean; override: Override; pin: (v: Override) => void }) {
  return (
    <div className="toggle-box">
      <button
        type="button"
        className="toggle"
        role="switch"
        aria-checked={night}
        aria-label="Ночная тема"
        onClick={() => {
          haptic('select');
          pin(night ? 'day' : 'night');
        }}
      >
        <span className="t-stars" aria-hidden="true">
          <i style={{ left: 11, top: 9, transitionDelay: '.2s' }} />
          <i style={{ left: 21, top: 21, transitionDelay: '.3s' }} />
          <i style={{ left: 29, top: 10, transitionDelay: '.4s' }} />
        </span>
        <span className="t-cloud" aria-hidden="true" />
        <span className="knob" aria-hidden="true">
          <span className="shade" />
        </span>
      </button>
      {override !== null && (
        <button type="button" className="auto" onClick={() => pin(null)} aria-label="Вернуть тему по времени суток">
          <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round" aria-hidden="true">
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3 2" />
          </svg>
          Авто
        </button>
      )}
    </div>
  );
}
