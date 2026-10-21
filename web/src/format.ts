// Даты и время по-русски в часовом поясе репетитора (как в уведомлениях).

let zone = 'Europe/Moscow';
export const setZone = (tz: string) => {
  zone = tz;
};

const ymd = (d: Date) =>
  new Intl.DateTimeFormat('en-CA', { timeZone: zone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(d);

export function time(iso: string): string {
  return new Intl.DateTimeFormat('ru-RU', { timeZone: zone, hour: '2-digit', minute: '2-digit' }).format(new Date(iso));
}

/** «Сегодня», «Завтра», «Вчера» или «пн, 20 октября». */
export function day(iso: string): string {
  const d = new Date(iso);
  const today = new Date();
  const key = ymd(d);
  if (key === ymd(today)) return 'Сегодня';
  if (key === ymd(new Date(today.getTime() + 86_400_000))) return 'Завтра';
  if (key === ymd(new Date(today.getTime() - 86_400_000))) return 'Вчера';
  return new Intl.DateTimeFormat('ru-RU', { timeZone: zone, weekday: 'short', day: 'numeric', month: 'long' }).format(d);
}

/** Ключ дня для группировки расписания. */
export const dayKey = (iso: string) => ymd(new Date(iso));

/** «до ср, 22 октября, 18:00» / «без срока». */
export function due(iso: string | null): string {
  if (!iso) return 'без срока';
  return 'до ' + day(iso).replace(/^С/, 'с').replace(/^З/, 'з') + ', ' + time(iso);
}

export function money(n: number): string {
  return new Intl.NumberFormat('ru-RU').format(n).replace(/ /g, ' ') + ' ₽';
}

/** 1 часть, 2 части, 5 частей. */
export function plural(n: number, one: string, few: string, many: string): string {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m100 >= 11 && m100 <= 14) return many;
  if (m10 === 1) return one;
  if (m10 >= 2 && m10 <= 4) return few;
  return many;
}
