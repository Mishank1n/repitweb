import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, post, type GroupDetail, type HomeworkCard, type HomeworkStudent, type Issued } from '../api';
import { Composer, useAttachments } from '../attach';
import { day, due, plural, time } from '../format';
import { haptic } from '../platform';
import { PartsView } from '../student/parts';
import { Chevron, CLIPPED_TITLE, Empty, ErrorCard, Field, FormError, Screen, Skeletons } from '../ui';
import { Avatar } from './common';
import { Confirm, Segments, useAction } from './forms';

type Due = 'lesson' | 'week' | 'date' | 'none';

const SCORES = [5, 10, 20, 100];

const isoDay = (offset: number) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/**
 * Выдача ДЗ одной формой: текст и файлы, кому (по умолчанию всей группе), максимум баллов, срок
 * («к следующему занятию» — если оно есть). Ученики и родители получают уведомление сразу.
 */
export function HomeworkForm({ groupId, onIssued }: { groupId: number; onIssued: (id: number) => void }) {
  const q = useQuery({ queryKey: ['group', groupId], queryFn: () => api<GroupDetail>(`/api/tutor/groups/${groupId}`) });
  const [title, setTitle] = useState('');
  const [text, setText] = useState('');
  const att = useAttachments();
  const [excluded, setExcluded] = useState<Set<number>>(new Set());
  const [maxScore, setMaxScore] = useState(10);
  const [dueKind, setDueKind] = useState<Due | null>(null);
  const [date, setDate] = useState(isoDay(7));
  const [dueTime, setDueTime] = useState('20:00');
  const [error, setError] = useState<string | null>(null);

  const d = q.data;
  const next = d?.upcoming.find((l) => l.status === 'upcoming') ?? null;
  const kind: Due = dueKind ?? (next ? 'lesson' : 'week');
  const chosen = (d?.students ?? []).filter((s) => !excluded.has(s.id));

  const issue = useAction(() => post<Issued>(`/api/tutor/groups/${groupId}/homework`, {
    title,
    text,
    files: att.ids,
    assignees: chosen.map((s) => s.id),
    maxScore,
    ...(kind === 'lesson' && next ? { dueAt: next.startsAt } : {}),
    ...(kind === 'week' ? { dueDate: isoDay(7), dueTime: '20:00' } : {}),
    ...(kind === 'date' ? { dueDate: date, dueTime } : {}),
  }), 'ДЗ выдано — ученики и родители получили уведомление', (r) => onIssued(r.id));

  const submit = (e: FormEvent) => {
    e.preventDefault();
    if (!text.trim() && att.ids.length === 0) {
      setError('Добавьте текст задания или файлы');
    } else if (att.failed) {
      setError('Уберите файлы, которые не загрузились');
    } else if (chosen.length === 0) {
      setError('Выберите хотя бы одного ученика');
    } else {
      setError(null);
      issue.mutate();
    }
  };

  const toggle = (id: number) => {
    haptic('select');
    const nextSet = new Set(excluded);
    if (!nextSet.delete(id)) nextSet.add(id);
    setExcluded(nextSet);
  };

  const dueOptions: { id: Due; label: string }[] = [
    ...(next ? [{ id: 'lesson' as const, label: 'К занятию' }] : []),
    { id: 'week', label: 'Неделя' },
    { id: 'date', label: 'Дата' },
    { id: 'none', label: 'Без срока' },
  ];
  const customScore = !SCORES.includes(maxScore);

  return (
    <Screen narrow caps={d ? d.name : 'Группа'} title="Новое ДЗ">
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && d.students.length === 0 && (
        <Empty title="В группе нет учеников" text="Сначала пригласите ученика — задание выдаётся ученикам группы." />
      )}
      {d && d.students.length > 0 && (
        <form onSubmit={submit} className="form-stack">
          <section className="card rise pad">
            <Field label="Название" value={title} maxLength={100} placeholder="Необязательно"
                   onChange={(e) => setTitle(e.target.value)}
                   hint="Если пусто — первая строка задания или «ДЗ от» и дата" />
          </section>
          <Composer label="Задание" text={text} onText={setText} att={att} autoFocus
                    placeholder="Например: № 112–118, выучить слова из урока 5" />

          <section className="card rise pad">
            <div className="label">Кому · {chosen.length} из {d.students.length}</div>
            <div className="chips">
              {d.students.map((s) => (
                <button key={s.id} type="button" className={excluded.has(s.id) ? 'chip tap' : 'chip tap on'}
                        aria-pressed={!excluded.has(s.id)} onClick={() => toggle(s.id)}>
                  {s.name}
                </button>
              ))}
            </div>
          </section>

          <section className="card rise pad">
            <div className="label">Срок сдачи</div>
            <Segments label="Срок сдачи" options={dueOptions} value={kind} onChange={setDueKind} />
            {kind === 'lesson' && next && <p className="foot" style={{ margin: 0 }}>{due(next.startsAt)} — начало занятия</p>}
            {kind === 'week' && <p className="foot" style={{ margin: 0 }}>{due(`${isoDay(7)}T20:00:00`)}</p>}
            {kind === 'date' && (
              <div className="grid2">
                <Field label="Дата" type="date" value={date} min={isoDay(0)} onChange={(e) => setDate(e.target.value)} required />
                <Field label="Время" type="time" value={dueTime} onChange={(e) => setDueTime(e.target.value)} required />
              </div>
            )}
            {kind === 'none' && <p className="foot" style={{ margin: 0 }}>Напоминаний о сроке не будет.</p>}
          </section>

          <section className="card rise pad">
            <div className="label">Максимум баллов</div>
            <Segments label="Максимум баллов" accent options={SCORES.map((n) => ({ id: n, label: String(n) }))}
                      value={customScore ? null : maxScore} onChange={setMaxScore} />
            <Field label="Или своё число" type="number" inputMode="numeric" min={1} max={1000}
                   value={customScore ? String(maxScore) : ''} placeholder="например, 12"
                   onChange={(e) => setMaxScore(Number(e.target.value) || 0)} />
          </section>

          <FormError text={error} />
          <button type="submit" className="btn primary" disabled={issue.isPending || att.busy}>
            {att.busy ? 'Файлы загружаются…' : `Выдать ${chosen.length} ${plural(chosen.length, 'ученику', 'ученикам', 'ученикам')}`}
          </button>
        </form>
      )}
    </Screen>
  );
}

function studentLook(s: HomeworkStudent, max: number, overdue: boolean): { label: string; tone: string; sub: string } {
  const extra = [s.late ? 'после срока' : '', s.attempt > 1 ? `попытка ${s.attempt}` : ''].filter(Boolean).join(', ');
  switch (s.status) {
    case 'SUBMITTED':
      return { label: 'Проверить', tone: 'wait', sub: extra || 'ждёт проверки' };
    case 'RETURNED':
      return { label: 'Доработка', tone: 'warn', sub: 'вернули на доработку' };
    case 'CHECKED':
      return { label: `${s.score}/${max}`, tone: 'ok', sub: extra || 'проверено' };
    default:
      return { label: overdue ? 'Срок прошёл' : 'Не сдано', tone: overdue ? 'warn' : 'muted', sub: 'ещё не сдал(а)' };
  }
}

/** Карточка выданного ДЗ: задание, кто сдал и что ждёт проверки, удаление. */
export function TutorHomework({ homeworkId, onSubmission, onDeleted }: {
  homeworkId: number;
  onSubmission: (id: number) => void;
  onDeleted: () => void;
}) {
  const q = useQuery({ queryKey: ['tutor-hw', homeworkId], queryFn: () => api<HomeworkCard>(`/api/tutor/homework/${homeworkId}`) });
  const remove = useAction(() => post(`/api/tutor/homework/${homeworkId}/delete`), 'ДЗ удалено', onDeleted);
  const d = q.data;
  const hw = d?.homework;
  return (
    <Screen caps={d ? d.group.name : 'Домашнее задание'} title={hw ? hw.title : '…'}>
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && hw && (
        <div className="cols">
          <div className="col">
            <div className="chips rise">
              <span className={hw.overdue ? 'chip warn' : 'chip'}>{due(hw.dueAt)}</span>
              <span className="chip">из {hw.maxScore} баллов</span>
              {hw.issuedAt && <span className="chip">выдано {day(hw.issuedAt).toLowerCase()}, {time(hw.issuedAt)}</span>}
            </div>
            <section className="card rise pad">
              <div className="label">Задание</div>
              {hw.title.length > CLIPPED_TITLE && <div className="h2">{hw.title}</div>}
              <PartsView parts={d.parts} mediaPath={(id) => `/api/media/hw/${id}`} />
            </section>
          </div>
          <div className="col">
            <div className="section-head rise">
              <h2 className="h2">Сдали {hw.handedIn} из {hw.assigned}</h2>
            </div>
            <div className="card rise list">
              {d.students.map((s) => {
                const look = studentLook(s, hw.maxScore, hw.overdue);
                const open = s.submissionId;
                return (
                  <button key={s.id} type="button" className={open ? 'row' : 'row static'} disabled={!open}
                          onClick={() => open && onSubmission(open)}>
                    <Avatar id={s.id} name={s.name} />
                    <span className="row-main">
                      <span className="row-title">{s.name}</span>
                      <span className="foot row-sub">{look.sub}</span>
                    </span>
                    <span className={'status ' + look.tone}>{look.label}</span>
                    {open && <Chevron />}
                  </button>
                );
              })}
            </div>
            <Confirm label="Удалить ДЗ" confirm="Да, удалить" busy={remove.isPending} onConfirm={() => remove.mutate()}
                     text={<>Задание исчезнет у учеников вместе со сданными работами и оценками. Отменить нельзя.</>} />
          </div>
        </div>
      )}
    </Screen>
  );
}
