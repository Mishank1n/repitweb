import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { api, type Today as TodayData } from '../api';
import { day, plural, time } from '../format';
import { PushCard } from '../feed';
import { IconAlert, IconChart } from '../icons';
import { Chevron, Empty, ErrorCard, Screen, Skeletons } from '../ui';
import { LessonRow } from './common';

const todayCaps = () => {
  const s = new Intl.DateTimeFormat('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
  return s.charAt(0).toUpperCase() + s.slice(1);
};

export function TutorToday({ title, onLesson, onStudent, onReview, onReports }: {
  title: ReactNode;
  onLesson: (id: number) => void;
  onStudent: (id: number) => void;
  onReview: () => void;
  onReports: () => void;
}) {
  const q = useQuery({ queryKey: ['today'], queryFn: () => api<TodayData>('/api/tutor/today') });
  const d = q.data;
  const next = d?.lessons.find((l) => l.status === 'upcoming' || l.status === 'ongoing');
  return (
    <Screen tall caps={todayCaps()} title={title}>
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      <PushCard />
      {d && (
        <div className="cols">
          <div className="col">
            <div className="grid2">
              <div className="card rise stat" style={{ animationDelay: '.08s' }}>
                <span className="stat-big">{d.lessons.length}</span>
                <span>
                  <span className="stat-num">{plural(d.lessons.length, 'занятие', 'занятия', 'занятий')}</span>
                  <span className="foot">{next ? (next.status === 'ongoing' ? 'одно идёт сейчас' : 'следующее в ' + time(next.startsAt)) : 'на сегодня всё'}</span>
                </span>
              </div>
              <button type="button" className="card tap rise stat" style={{ animationDelay: '.14s' }} onClick={onReview}>
                <span className="stat-big accent-text">{d.queue}</span>
                <span>
                  <span className="stat-num">{plural(d.queue, 'работа', 'работы', 'работ')}</span>
                  <span className="foot">{d.queue ? 'ждут проверки' : 'всё проверено'}</span>
                </span>
              </button>
          </div>

          {d.unmarked.length > 0 && (
            <div className="card rise list" style={{ animationDelay: '.2s' }}>
              <div className="row static">
                <span className="ico warn-soft"><IconAlert /></span>
                <span className="row-main">
                  <span className="row-title">Не отмечено: {d.unmarked.length}</span>
                  <span className="foot row-sub">пока не отмечено, абонементы не списываются</span>
                </span>
              </div>
              {d.unmarked.slice(0, 5).map((l) => (
                <button key={l.id} type="button" className="row" onClick={() => onLesson(l.id)}>
                  <span className="date-pill">{day(l.startsAt).replace(/,.*/, '')}</span>
                  <span className="row-main">
                    <span className="row-title">{l.group}</span>
                    <span className="foot row-sub">{day(l.startsAt)}, {time(l.startsAt)}</span>
                  </span>
                  <span className="status warn">Отметить</span>
                  <Chevron />
                </button>
              ))}
            </div>
          )}

          <button type="button" className="card tap rise row reports-link" style={{ animationDelay: '.22s' }} onClick={onReports}>
            <span className="ico wait-soft"><IconChart /></span>
            <span className="row-main">
              <span className="row-title">Отчёты родителям</span>
              <span className="foot row-sub">за неделю: комментарии и предпросмотр</span>
            </span>
            <Chevron />
          </button>
          </div>

          <div className="col">
            <h2 className="h2 rise section-pad" style={{ animationDelay: '.26s' }}>Занятия</h2>
            {d.lessons.length === 0 ? (
              <Empty title="Сегодня занятий нет" text="Можно выдохнуть. Расписание на неделю — во вкладке «Неделя»." />
            ) : (
              <div className="card rise list" style={{ animationDelay: '.32s' }}>
                {d.lessons.map((l) => <LessonRow key={l.id} lesson={l} onOpen={() => onLesson(l.id)} />)}
              </div>
            )}

            {d.ending.length > 0 && (
              <>
                <h2 className="h2 rise section-pad" style={{ animationDelay: '.38s' }}>Абонементы заканчиваются</h2>
                <div className="card rise list" style={{ animationDelay: '.44s' }}>
                  {d.ending.map((s) => (
                    <button key={s.id} type="button" className="row" onClick={() => onStudent(s.id)}>
                      <span className="row-main"><span className="row-title">{s.name}</span></span>
                      <span className="status warn">
                        {s.used >= s.total ? 'закончился' : `осталось ${s.total - s.used}`} · {s.used}/{s.total}
                      </span>
                      <Chevron />
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </Screen>
  );
}
