// Тема по времени суток: 00:00 ночь, 06:00 рассвет (ровно посередине), 12:00–18:00 день,
// с 18:00 вечереет, к 00:00 снова ночь. Чистые функции — их легко проверить и поменять.

export type Override = 'day' | 'night' | null;

export type SkyStyle = {
  night: number;
  sunY: number;
  sunO: number;
  moonY: number;
  moonO: number;
  glow: number;
};

export type Sky = {
  /** Интерфейс в ночной палитре: переключается на середине перехода, чтобы текст всегда читался. */
  uiNight: boolean;
  style: SkyStyle;
  greeting: string;
  clock: string;
  /** Цвет панели браузера на телефоне (theme-color): верх неба. */
  headerColor: string;
};

export function nowHour(date = new Date()): number {
  return date.getHours() + date.getMinutes() / 60;
}

/** 0 — ночь, 1 — день; кусочно-линейно по точкам 0/6/12/18/24 ч. */
export function dayness(hour: number): number {
  const h = ((hour % 24) + 24) % 24;
  if (h < 6) return h / 12;
  if (h < 12) return 0.5 + (h - 6) / 12;
  if (h < 18) return 1;
  return 1 - (h - 18) / 6;
}

export function greeting(hour: number): string {
  const h = Math.floor(((hour % 24) + 24) % 24);
  if (h < 5) return 'Доброй ночи';
  if (h < 11) return 'Доброе утро';
  if (h < 17) return 'Добрый день';
  if (h < 23) return 'Добрый вечер';
  return 'Доброй ночи';
}

const clamp = (x: number) => Math.max(0, Math.min(1, x));

/** Смесь двух цветов #RRGGBB: t=0 — первый, t=1 — второй. */
export function mixHex(a: string, b: string, t: number): string {
  const pa = [1, 3, 5].map((i) => parseInt(a.slice(i, i + 2), 16));
  const pb = [1, 3, 5].map((i) => parseInt(b.slice(i, i + 2), 16));
  return '#' + pa.map((v, i) => Math.round(v + (pb[i] - v) * clamp(t)).toString(16).padStart(2, '0')).join('');
}

export function computeSky(hour: number, override: Override): Sky {
  const base = dayness(hour);
  const t = override === null ? base : override === 'night' ? 0.04 : 1;
  const h = ((hour % 24) + 24) % 24;
  return {
    uiNight: override === null ? base < 0.5 : override === 'night',
    style: {
      night: 1 - t,
      sunY: (1 - t) * 110,
      sunO: clamp((t - 0.25) / 0.35),
      moonY: t * 110,
      moonO: clamp((0.75 - t) / 0.35),
      glow: override === null ? 3 * base * (1 - base) : 0,
    },
    greeting: greeting(hour),
    clock: String(Math.floor(h)).padStart(2, '0') + ':' + String(Math.floor((h % 1) * 60)).padStart(2, '0'),
    headerColor: mixHex('#1E1B44', '#FFEBDD', t),
  };
}
