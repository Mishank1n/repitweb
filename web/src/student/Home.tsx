import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { api, type Home as HomeData, type Lesson } from '../api';
import { day, money, plural, time } from '../format';
import { IconPin, IconVideo } from '../icons';
import { openLink } from '../platform';
import { Empty, ErrorCard, Ring, Screen, Skeletons, Top } from '../ui';
import { PushCard } from '../feed';
import { HomeworkRowView } from './status';

const today = () => {
  const s = new Intl.DateTimeFormat('ru-RU', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date());
  return s.charAt(0).toUpperCase() + s.slice(1);
};

export function NextLesson({ lesson }: { lesson: Lesson }) {
  const place = lesson.place === 'online' ? 'онлайн' : lesson.place === 'offline' ? 'очно' : '';
  return (
    <section className="card rise lesson-card" aria-label="Ближайшее занятие" style={{ animationDelay: '.1s' }}>
      <div style={{ minWidth: 0 }}>
        <div className="caps dot">{lesson.ongoing ? 'Идёт сейчас' : day(lesson.startsAt)}{place && ' · ' + place}</div>
        <div className="big-time">{time(lesson.startsAt)}</div>
        <div className="sub">{lesson.group} · {lesson.durationMin} мин</div>
        {lesson.place === 'offline' && lesson.address && (
          <div className="foot address"><IconPin /> {lesson.address}</div>
        )}
      </div>
      {lesson.place === 'online' && lesson.meetingUrl && (
        <button type="button" className="btn join" onClick={() => openLink(lesson.meetingUrl!)}>
          <IconVideo /> Войти
        </button>
      )}
    </section>
  );
}

export function StudentHome({ studentId, title, parent, onTab, onHomework }: {
  studentId: number;
  title: ReactNode;
  parent: boolean;
  onTab: (tab: string) => void;
  onHomework: (id: number) => void;
}) {
  const q = useQuery({ queryKey: ['home', studentId], queryFn: () => api<HomeData>(`/api/students/${studentId}/home`) });
  const d = q.data;
  return (
    <Screen tall caps={today()} title={title}>
      <Top />
      <PushCard />
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && (
        <div className="cols">
          <div className="col">
            {d.next ? <NextLesson lesson={d.next} /> : (
              <Empty title="Ближайших занятий нет" text="Когда репетитор добавит занятие, оно появится здесь." />
            )}

            <div className="grid2">
              <div className="card rise stat" style={{ animationDelay: '.18s' }}>
                {d.subscription ? (
                  <>
                    <Ring used={d.subscription.used} total={d.subscription.total} />
                    <span>
                      <span className="stat-num">{d.subscription.used} из {d.subscription.total}</span>
                      <span className="foot">
                        {d.subscription.amount !== null
                          ? `${money(d.subscription.amount)} · с ${day(d.subscription.paidAt).toLowerCase()}`
                          : 'занятий абонемента'}
                      </span>
                    </span>
                  </>
                ) : (
                  <>
                    <span className="stat-big muted">—</span>
                    <span>
                      <span className="stat-num">Абонемента нет</span>
                      <span className="foot">спросите у репетитора</span>
                    </span>
                  </>
                )}
              </div>
              {parent ? (
                <div className="card rise stat" style={{ animationDelay: '.24s' }}>
                  <span className="stat-big">
                    {d.attendance.present}<span className="stat-of">/{d.attendance.total}</span>
                  </span>
                  <span>
                    <span className="stat-num">Посещаемость</span>
                    <span className="foot">{d.attendance.total === 0 ? 'в этом месяце занятий не было' : 'занятий в этом месяце'}</span>
                  </span>
                </div>
              ) : (
              <button type="button" className="card tap rise stat" style={{ animationDelay: '.24s' }} onClick={() => onTab('homework')}>
                <span className="stat-big">{d.homeworkTodo}</span>
                <span>
                  <span className="stat-num">{d.homeworkTodo === 0 ? 'Всё сдано' : plural(d.homeworkTodo, 'задание ждёт', 'задания ждут', 'заданий ждут')}</span>
                  <span className="foot">{d.nearestDue ? 'ближайший срок — ' + day(d.nearestDue).toLowerCase() : 'новых заданий нет'}</span>
                </span>
              </button>
              )}
          </div>
          </div>

          <div className="col">
            <div className="section-head rise" style={{ animationDelay: '.3s' }}>
              <h2 className="h2">Домашние задания</h2>
              <button type="button" className="link" onClick={() => onTab('homework')}>Все</button>
          </div>
          {d.homework.length === 0 ? (
            <Empty title="Заданий пока нет" text="Новые задания появятся здесь — придёт уведомление." />
          ) : (
            <div className="card rise list" style={{ animationDelay: '.36s' }}>
              {d.homework.map((r) => <HomeworkRowView key={r.id} row={r} onOpen={() => onHomework(r.id)} />)}
            </div>
          )}
          </div>
        </div>
      )}
    </Screen>
  );
}
