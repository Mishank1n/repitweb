import { useQuery } from '@tanstack/react-query';
import { api, type MaterialDetail, type MaterialLesson } from '../api';
import { day, plural, time } from '../format';
import { Chevron, Empty, ErrorCard, Screen, Skeletons, Top } from '../ui';
import { PartsView } from './parts';

const shortDate = (iso: string) =>
  new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short' }).format(new Date(iso)).replace('.', '');

export function StudentMaterials({ studentId, onOpen }: { studentId: number; onOpen: (lessonId: number) => void }) {
  const q = useQuery({ queryKey: ['materials', studentId], queryFn: () => api<MaterialLesson[]>(`/api/students/${studentId}/materials`) });
  return (
    <Screen caps="Конспекты, файлы, записи" title="Материалы">
      <Top />
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {q.data && q.data.length === 0 && (
        <Empty title="Материалов пока нет" text="После занятия репетитор пришлёт конспект или запись — они появятся здесь." />
      )}
      {q.data && q.data.length > 0 && (
        <div className="card rise list">
          {q.data.map((m) => (
            <button key={m.lessonId} type="button" className="row" onClick={() => onOpen(m.lessonId)}>
              <span className="date-pill">{shortDate(m.startsAt)}</span>
              <span className="row-main">
                <span className="row-title">{m.group}</span>
                <span className="foot row-sub">{day(m.startsAt)} · {m.parts} {plural(m.parts, 'часть', 'части', 'частей')}</span>
              </span>
              <Chevron />
            </button>
          ))}
        </div>
      )}
    </Screen>
  );
}

export function StudentMaterialDetail({ studentId, lessonId }: { studentId: number; lessonId: number }) {
  const base = `/api/students/${studentId}/materials/${lessonId}`;
  const q = useQuery({ queryKey: ['material', studentId, lessonId], queryFn: () => api<MaterialDetail>(base) });
  const d = q.data;
  return (
    <Screen caps={d ? `${d.lesson.group} · ${day(d.lesson.startsAt)}, ${time(d.lesson.startsAt)}` : 'Занятие'} title="Материалы занятия">
      {q.isPending && <Skeletons count={2} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && (
        <>
          <section className="card rise pad" style={{ animationDelay: '.08s' }}>
            <PartsView parts={d.parts} mediaPath={(id) => `/api/media/mat/${id}`} />
          </section>
        </>
      )}
    </Screen>
  );
}
