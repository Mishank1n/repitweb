import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api, errorMessage, post, type LessonDetail, type Mark, type MaterialItem, type MaterialsSent } from '../api';
import { Composer, useAttachments } from '../attach';
import { day, plural, time } from '../format';
import { IconVideo } from '../icons';
import { haptic, openLink } from '../platform';
import { PartsView } from '../student/parts';
import { ErrorCard, FormError, Screen, Skeletons, useToast } from '../ui';
import { Avatar, endTime } from './common';
import { Confirm, useAction } from './forms';

const OPTIONS: { id: Mark; label: string }[] = [
  { id: 'P', label: 'Был' },
  { id: 'C', label: 'Пропуск, списать' },
  { id: 'F', label: 'Не списывать' },
];

/** Что станет с абонементом при отметке (для ещё не отмеченного занятия). */
function effect(mark: Mark, subscription: string | null): { text: string; warn: boolean } {
  if (mark === 'F') return { text: 'Абонемент не меняется', warn: false };
  if (!subscription) return { text: 'Абонемента нет — занятие пройдёт вне абонемента', warn: true };
  const [used, total] = subscription.split('/').map(Number);
  if (used + 1 >= total) return { text: `Станет ${used + 1} из ${total} — абонемент закончится`, warn: true };
  return { text: `Станет ${used + 1} из ${total}`, warn: false };
}

export function TutorLessonCard({ lessonId, onDone, onMove, onMaterials, onHomework }: {
  lessonId: number;
  onDone: () => void;
  onMove: (d: LessonDetail) => void;
  onMaterials: (d: LessonDetail) => void;
  onHomework: (groupId: number) => void;
}) {
  const q = useQuery({ queryKey: ['lesson', lessonId], queryFn: () => api<LessonDetail>(`/api/tutor/lessons/${lessonId}`) });
  const cancel = useAction(() => post(`/api/tutor/lessons/${lessonId}/cancel`), 'Занятие отменено — ученики получили уведомление');
  const [marks, setMarks] = useState<Record<number, Mark>>({});
  const toast = useToast();
  const client = useQueryClient();
  const d = q.data;

  useEffect(() => {
    if (d) setMarks(Object.fromEntries(d.roster.map((r) => [r.studentId, r.status])));
  }, [d]);

  const save = useMutation({
    mutationFn: () => post<LessonDetail>(`/api/tutor/lessons/${lessonId}/attendance`, { marks }),
    onSuccess: () => {
      haptic();
      toast('Отметки сохранены');
      client.invalidateQueries();
      onDone();
    },
    onError: (e) => toast(errorMessage(e)),
  });

  const l = d?.lesson;
  const charged = d ? d.roster.filter((r) => (marks[r.studentId] ?? r.status) !== 'F' && r.subscription).length : 0;
  const place = d?.place === 'online' ? 'онлайн' : d?.place === 'offline' ? 'очно' : '';
  return (
    <Screen caps={l ? `${day(l.startsAt)}, ${time(l.startsAt)}–${endTime(l)}${place ? ' · ' + place : ''}` : 'Занятие'}
            title={l ? l.group : '…'}>
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && d.lesson.status === 'cancelled' && (
        <section className="card rise empty">
          <div className="empty-title">Занятие отменено</div>
          <div className="sub">Ученики и родители получили уведомление.</div>
        </section>
      )}
      {d && d.lesson.status !== 'cancelled' && (
        <>
          {d.series && <p className="foot rise hint">Из регулярного расписания: {d.series}</p>}
          {d.place === 'online' && d.meetingUrl && !d.marked && (
            <button type="button" className="btn soft rise" onClick={() => openLink(d.meetingUrl!)}>
              <IconVideo /> Открыть созвон
            </button>
          )}
          {!d.canMark ? (
            <section className="card rise pad">
              <div className="label">Ученики · {d.roster.length}</div>
              {d.roster.map((r) => (
                <div key={r.studentId} className="person">
                  <Avatar id={r.studentId} name={r.name} />
                  <span className="row-title" style={{ flex: 1 }}>{r.name}</span>
                  {r.warned ? <span className="foot warned">не придёт</span>
                    : <span className="foot">{r.subscription ?? 'без абонемента'}</span>}
                </div>
              ))}
              <p className="foot" style={{ margin: 0 }}>Отметить посещаемость можно, когда занятие начнётся.</p>
            </section>
          ) : (
            <>
              <p className="foot rise hint">
                {d.marked ? 'Посещаемость уже отмечена — можно исправить, абонементы пересчитаются.'
                  : 'Все отмечены как «Был». Отметьте только тех, кого не было.'}
              </p>
              {d.roster.map((r, i) => {
                const mark = marks[r.studentId] ?? r.status;
                const idx = OPTIONS.findIndex((o) => o.id === mark);
                const eff = effect(mark, r.subscription);
                return (
                  <section key={r.studentId} className="card rise pad tight" style={{ animationDelay: `${0.06 + i * 0.05}s` }}>
                    <div className="person">
                      <Avatar id={r.studentId} name={r.name} />
                      <span className="row-title" style={{ flex: 1 }}>{r.name}</span>
                      <span className="foot">{r.subscription ?? 'без абонемента'}</span>
                    </div>
                    {r.warned && <div className="foot warned">Предупредил(а) заранее, что не придёт</div>}
                    <div className="seg" role="radiogroup" aria-label={r.name} style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
                      <span className="ind" aria-hidden="true" style={{ width: 'calc((100% - 16px) / 3)', transform: `translateX(calc(${idx} * (100% + 4px)))` }} />
                      {OPTIONS.map((o) => (
                        <button key={o.id} type="button" role="radio" aria-checked={mark === o.id}
                                className={mark === o.id ? 'on mark-' + o.id : ''}
                                onClick={() => { haptic('select'); setMarks({ ...marks, [r.studentId]: o.id }); }}>
                          {o.label}
                        </button>
                      ))}
                    </div>
                    {!d.marked && <div className={eff.warn ? 'foot effect warn' : 'foot effect'}>{eff.text}</div>}
                  </section>
                );
              })}
              {!d.marked && d.roster.length > 0 && (
                <div className="card rise summary">
                  <span>Спишется из абонементов</span>
                  <b>{charged} {plural(charged, 'занятие', 'занятия', 'занятий')}</b>
                </div>
              )}
              {d.roster.length > 0 && (
                <div className="bottom-action">
                  <button type="button" className="btn primary" disabled={save.isPending} onClick={() => save.mutate()}>
                    {d.marked ? 'Сохранить изменения' : 'Сохранить отметки'}
                  </button>
                </div>
              )}
            </>
          )}
          <h2 className="h2 rise section-pad">Материалы и ДЗ</h2>
          <Materials items={d.materials} />
          <div className="grid2">
            <button type="button" className="btn soft rise" onClick={() => onMaterials(d)}>+ Материалы</button>
            <button type="button" className="btn soft rise" onClick={() => onHomework(d.lesson.groupId)}>+ Выдать ДЗ</button>
          </div>
          {d.canMove && (
            <>
              <button type="button" className="btn soft rise" onClick={() => onMove(d)}>Перенести</button>
              <Confirm label="Отменить занятие" confirm="Да, отменить" busy={cancel.isPending} onConfirm={() => cancel.mutate()}
                       text="Ученики и родители получат уведомление об отмене. Регулярное расписание не изменится — только это занятие." />
            </>
          )}
        </>
      )}
    </Screen>
  );
}

/** Материалы занятия, которые уже видят ученики; часть можно удалить (у учеников она тоже исчезнет). */
function Materials({ items }: { items: MaterialItem[] }) {
  const remove = useAction((id: number) => post(`/api/tutor/materials/${id}/delete`), 'Удалено');
  if (items.length === 0) {
    return <p className="foot rise hint">Материалов пока нет: конспект, фото доски или запись — ученики получат уведомление.</p>;
  }
  return (
    <section className="card rise list">
      {items.map((m) => (
        <div key={m.id} className="row static series-row">
          <span className="row-main material-part">
            <PartsView parts={[m.part]} mediaPath={(id) => `/api/media/mat/${id}`} />
          </span>
          <Confirm compact label="Удалить" confirm="Удалить" busy={remove.isPending} onConfirm={() => remove.mutate(m.id)}
                   text="Эта часть исчезнет из материалов у учеников и родителей." />
        </div>
      ))}
    </section>
  );
}

/** Материалы к занятию одной формой: текст и файлы сразу уходят ученикам группы и родителям. */
export function MaterialsForm({ lesson, onDone }: { lesson: LessonDetail; onDone: () => void }) {
  const [text, setText] = useState('');
  const att = useAttachments();
  const [error, setError] = useState<string | null>(null);
  const send = useAction(() => post<MaterialsSent>(`/api/tutor/lessons/${lesson.lesson.id}/materials`, { text, files: att.ids }),
    'Материалы отправлены — ученики и родители получили уведомление', onDone);
  const l = lesson.lesson;
  return (
    <Screen narrow caps={`${l.group} · ${day(l.startsAt)}, ${time(l.startsAt)}`} title="Материалы занятия">
      <form className="form-stack" onSubmit={(e) => {
        e.preventDefault();
        if (!text.trim() && att.ids.length === 0) {
          setError('Добавьте текст или файлы');
        } else if (att.failed) {
          setError('Уберите файлы, которые не загрузились');
        } else {
          setError(null);
          send.mutate();
        }
      }}>
        <Composer label="Что отправить" text={text} onText={setText} att={att} autoFocus
                  placeholder="Конспект, ссылки, что повторить" />
        <FormError text={error} />
        <button type="submit" className="btn primary" disabled={send.isPending || att.busy}>
          {att.busy ? 'Файлы загружаются…' : 'Отправить ученикам'}
        </button>
      </form>
    </Screen>
  );
}
