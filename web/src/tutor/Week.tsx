import { useQuery } from '@tanstack/react-query';
import { api, type TutorLesson } from '../api';
import { day, dayKey } from '../format';
import { Empty, ErrorCard, Screen, Skeletons } from '../ui';
import { LessonRow } from './common';

export function TutorWeek({ onLesson }: { onLesson: (id: number) => void }) {
  const q = useQuery({ queryKey: ['week'], queryFn: () => api<TutorLesson[]>('/api/tutor/week') });
  const days: { key: string; label: string; lessons: TutorLesson[] }[] = [];
  for (const l of q.data ?? []) {
    const key = dayKey(l.startsAt);
    const last = days[days.length - 1];
    if (last && last.key === key) last.lessons.push(l);
    else days.push({ key, label: day(l.startsAt), lessons: [l] });
  }
  return (
    <Screen caps="Ближайшие 7 дней" title="Неделя">
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {q.data && days.length === 0 && (
        <Empty title="Занятий нет" text="Добавьте регулярные или разовые занятия в расписании группы." />
      )}
      {days.map((d, i) => (
        <section key={d.key} className="rise" style={{ animationDelay: `${0.06 + i * 0.06}s` }}>
          <h2 className="day-head">{d.label}</h2>
          <div className="card list">
            {d.lessons.map((l) => <LessonRow key={l.id} lesson={l} onOpen={() => onLesson(l.id)} />)}
          </div>
        </section>
      ))}
    </Screen>
  );
}
