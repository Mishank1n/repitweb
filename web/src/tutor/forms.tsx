import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent, type ReactNode } from 'react';
import { errorMessage, type Place } from '../api';
import { haptic } from '../platform';
import { Field, FormError, Screen, useToast } from '../ui';

// ---------- действие с откликом ----------

/**
 * Действие репетитора: при успехе — вибрация, всплывающее подтверждение, обновление всех экранов;
 * при ошибке — текст от сервера («Ссылка должна начинаться с https://…»).
 */
export function useAction<T, V = void>(fn: (vars: V) => Promise<T>, done: string, after?: (result: T) => void) {
  const toast = useToast();
  const client = useQueryClient();
  return useMutation<T, unknown, V>({
    mutationFn: fn,
    onSuccess: (result) => {
      haptic();
      toast(done);
      client.invalidateQueries();
      after?.(result);
    },
    onError: (e) => toast(errorMessage(e)),
  });
}

// ---------- подтверждение опасного действия ----------

/**
 * Сначала кнопка; по нажатию — что именно произойдёт и кнопка подтверждения.
 * compact — небольшая кнопка в строке списка («Удалить» у занятия, «Отвязать» у родителя).
 */
export function Confirm({ label, text, confirm, busy, compact = false, onConfirm }: {
  label: string;
  text: ReactNode;
  confirm: string;
  busy?: boolean;
  compact?: boolean;
  onConfirm: () => void;
}) {
  const [open, setOpen] = useState(false);
  if (!open) {
    return <button type="button" className={compact ? 'btn soft' : 'btn soft wide'} onClick={() => setOpen(true)}>{label}</button>;
  }
  return (
    <div className="confirm rise">
      <div className="foot">{text}</div>
      <div className="confirm-actions">
        <button type="button" className="btn soft" onClick={() => setOpen(false)}>Отмена</button>
        <button type="button" className="btn danger" disabled={busy} onClick={onConfirm}>{confirm}</button>
      </div>
    </div>
  );
}

// ---------- выбор из нескольких вариантов (сегменты) ----------

export function Segments<T extends string | number>({ label, options, value, onChange, accent = false }: {
  label: string;
  options: { id: T; label: string }[];
  value: T | null;
  onChange: (v: T) => void;
  accent?: boolean;
}) {
  const idx = options.findIndex((o) => o.id === value);
  const n = options.length;
  return (
    <div className="seg" role="radiogroup" aria-label={label} style={{ gridTemplateColumns: `repeat(${n}, minmax(0, 1fr))` }}>
      {idx >= 0 && (
        <span className={accent ? 'ind accent' : 'ind'} aria-hidden="true"
              style={{ width: `calc((100% - ${8 + (n - 1) * 4}px) / ${n})`, transform: `translateX(calc(${idx} * (100% + 4px)))` }} />
      )}
      {options.map((o) => (
        <button key={String(o.id)} type="button" role="radio" aria-checked={o.id === value}
                className={o.id === value ? 'on' : ''} onClick={() => { haptic('select'); onChange(o.id); }}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

// ---------- длительность ----------

const DURATIONS = [45, 60, 90, 120];

function DurationPicker({ value, onChange }: { value: number; onChange: (v: number) => void }) {
  const custom = !DURATIONS.includes(value);
  return (
    <section className="card rise pad">
      <div className="label">Длительность, минут</div>
      <Segments label="Длительность" accent options={DURATIONS.map((d) => ({ id: d, label: String(d) }))}
                value={custom ? null : value} onChange={onChange} />
      <Field label="Или своё число" type="number" inputMode="numeric" min={15} max={300}
             value={custom ? String(value) : ''} placeholder="например, 75"
             onChange={(e) => onChange(Number(e.target.value) || 0)} />
    </section>
  );
}

// ---------- название группы ----------

export function NameForm({ caps, title, initial = '', submit, onSubmit }: {
  caps: string;
  title: string;
  initial?: string;
  submit: string;
  onSubmit: (name: string) => Promise<unknown>;
}) {
  const [name, setName] = useState(initial);
  const action = useAction(() => onSubmit(name.trim()), 'Сохранено');
  return (
    <Screen narrow caps={caps} title={title}>
      <form className="card rise pad" onSubmit={(e) => { e.preventDefault(); action.mutate(); }}>
        <Field label="Название" value={name} onChange={(e) => setName(e.target.value)} maxLength={100} required autoFocus
               hint="Для индивидуального ученика назовите группу его именем, например «Иван Петров, ЕГЭ»" />
        <button type="submit" className="btn wide" disabled={action.isPending || !name.trim()}>{submit}</button>
      </form>
    </Screen>
  );
}

// ---------- регулярное занятие ----------

const DAYS = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'].map((label, i) => ({ id: i + 1, label }));

export function SeriesForm({ groupName, onSubmit }: {
  groupName: string;
  onSubmit: (v: { day: number; time: string; duration: number }) => Promise<unknown>;
}) {
  const [day, setDay] = useState<number | null>(null);
  const [time, setTime] = useState('18:00');
  const [duration, setDuration] = useState(60);
  const [error, setError] = useState<string | null>(null);
  const action = useAction(() => onSubmit({ day: day!, time, duration }), 'Регулярное занятие добавлено — ученики получили уведомление');
  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!day) {
      setError('Выберите день недели');
      return;
    }
    setError(null);
    action.mutate();
  };
  return (
    <Screen narrow caps={groupName} title="Регулярное занятие">
      <form onSubmit={submit} className="form-stack">
        <section className="card rise pad">
          <div className="label">Каждую неделю по</div>
          <Segments label="День недели" options={DAYS} value={day} onChange={setDay} />
          <Field label="Во сколько" type="time" value={time} onChange={(e) => setTime(e.target.value)} required />
        </section>
        <DurationPicker value={duration} onChange={setDuration} />
        <p className="foot rise hint">Занятия появятся в расписании на 4 недели вперёд и дальше будут добавляться сами.</p>
        <FormError text={error} />
        <button type="submit" className="btn primary" disabled={action.isPending}>Добавить</button>
      </form>
    </Screen>
  );
}

// ---------- разовое занятие и перенос ----------

const isoDay = (offset: number) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** Разовое занятие (с длительностью) или перенос существующего (move: дата и время). */
export function LessonForm({ caps, title, move = false, initialDate, initialTime, submitText, done, onSubmit }: {
  caps: string;
  title: string;
  move?: boolean;
  initialDate?: string;
  initialTime?: string;
  submitText: string;
  done: string;
  onSubmit: (v: { date: string; time: string; duration: number }) => Promise<unknown>;
}) {
  const [date, setDate] = useState(initialDate ?? isoDay(1));
  const [time, setTime] = useState(initialTime ?? '18:00');
  const [duration, setDuration] = useState(60);
  const action = useAction(() => onSubmit({ date, time, duration }), done);
  const quick = [{ id: isoDay(0), label: 'Сегодня' }, { id: isoDay(1), label: 'Завтра' }, { id: isoDay(2), label: 'Послезавтра' }];
  return (
    <Screen narrow caps={caps} title={title}>
      <form onSubmit={(e) => { e.preventDefault(); action.mutate(); }} className="form-stack">
        <section className="card rise pad">
          <div className="label">Когда</div>
          <Segments label="Дата" options={quick} value={quick.some((q) => q.id === date) ? date : null} onChange={setDate} />
          <Field label="Дата" type="date" value={date} min={isoDay(0)} onChange={(e) => setDate(e.target.value)} required />
          <Field label="Во сколько" type="time" value={time} onChange={(e) => setTime(e.target.value)} required />
        </section>
        {!move && <DurationPicker value={duration} onChange={setDuration} />}
        <button type="submit" className="btn primary" disabled={action.isPending}>{submitText}</button>
      </form>
    </Screen>
  );
}

// ---------- место занятий ----------

export function PlaceForm({ groupName, place, meetingUrl, address, onSubmit }: {
  groupName: string;
  place: Place;
  meetingUrl: string | null;
  address: string | null;
  onSubmit: (v: { type: 'online' | 'offline'; value: string }) => Promise<unknown>;
}) {
  const [type, setType] = useState<'online' | 'offline'>(place === 'offline' ? 'offline' : 'online');
  const [url, setUrl] = useState(meetingUrl ?? '');
  const [addr, setAddr] = useState(address ?? '');
  const value = type === 'online' ? url : addr;
  const action = useAction(() => onSubmit({ type, value: value.trim() }), 'Место занятий сохранено');
  return (
    <Screen narrow caps={groupName} title="Место занятий">
      <form className="card rise pad" onSubmit={(e) => { e.preventDefault(); action.mutate(); }}>
        <Segments label="Где проходят занятия" options={[{ id: 'online', label: 'Онлайн' }, { id: 'offline', label: 'Очно' }]}
                  value={type} onChange={setType} />
        {type === 'online' ? (
          <Field label="Ссылка на созвон" type="url" inputMode="url" placeholder="https://telemost.yandex.ru/…" value={url}
                 onChange={(e) => setUrl(e.target.value)} required
                 hint="Постоянная ссылка: ученики увидят её в напоминаниях и на главной" />
        ) : (
          <Field label="Адрес" placeholder="ул. Ленина, 1, кв. 5" value={addr} onChange={(e) => setAddr(e.target.value)}
                 maxLength={300} required />
        )}
        <button type="submit" className="btn wide" disabled={action.isPending || !value.trim()}>Сохранить</button>
      </form>
    </Screen>
  );
}
