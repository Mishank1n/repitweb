import { useQuery } from '@tanstack/react-query';
import { api, post, type Lesson } from '../api';
import { day, dayKey, time } from '../format';
import { IconVideo } from '../icons';
import { openLink } from '../platform';
import { Confirm, useAction } from '../tutor/forms';
import { Empty, ErrorCard, Screen, Skeletons, Top } from '../ui';

export function StudentSchedule({ studentId }: { studentId: number }) {
  const q = useQuery({ queryKey: ['schedule', studentId], queryFn: () => api<Lesson[]>(`/api/students/${studentId}/schedule`) });
  const skip = useAction((lessonId: number) => post(`/api/students/${studentId}/lessons/${lessonId}/skip`),
    'Репетитор предупреждён');
  const days: { key: string; label: string; lessons: Lesson[] }[] = [];
  for (const l of q.data ?? []) {
    const key = dayKey(l.startsAt);
    const last = days[days.length - 1];
    if (last && last.key === key) last.lessons.push(l);
    else days.push({ key, label: day(l.startsAt), lessons: [l] });
  }
  return (
    <Screen caps="Ближайшие 2 недели" title="Расписание">
      <Top />
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {q.data && days.length === 0 && (
        <Empty title="Занятий нет" text="На ближайшие две недели занятий не запланировано." />
      )}
      {days.map((d, i) => (
        <section key={d.key} className="rise" style={{ animationDelay: `${0.06 + i * 0.06}s` }}>
          <h2 className="day-head">{d.label}</h2>
          <div className="card list">
            {d.lessons.map((l) => (
              <div key={l.id} className="row static series-row">
                <span className={l.ongoing ? 'time-pill now' : 'time-pill'}>{time(l.startsAt)}</span>
                <span className="row-main">
                  <span className="row-title">{l.group}</span>
                  <span className="foot row-sub">
                    {l.durationMin} мин{l.place === 'online' ? ' · онлайн' : l.place === 'offline' ? ' · ' + (l.address ?? 'очно') : ''}
                  </span>
                </span>
                {l.ongoing && <span className="status ok">идёт</span>}
                {l.skipped && <span className="status warn">не приду</span>}
                {l.place === 'online' && l.meetingUrl && (
                  <button type="button" className="icon-btn" aria-label="Войти на занятие" onClick={() => openLink(l.meetingUrl!)}>
                    <IconVideo />
                  </button>
                )}
                {l.canSkip && !l.skipped && (
                  <Confirm compact label="Не смогу" confirm="Предупредить" busy={skip.isPending} onConfirm={() => skip.mutate(l.id)}
                           text={`Репетитор получит уведомление, что на занятие ${day(l.startsAt).toLowerCase()} в ${time(l.startsAt)} не придут. Занятие останется в расписании.`} />
                )}
              </div>
            ))}
          </div>
        </section>
      ))}
    </Screen>
  );
}
