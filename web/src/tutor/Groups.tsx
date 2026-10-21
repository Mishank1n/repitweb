import { useQuery } from '@tanstack/react-query';
import { api, post, type DeletionRequest, type Group } from '../api';
import { day, plural, time } from '../format';
import { IconAlert } from '../icons';
import { Chevron, Empty, ErrorCard, Screen, Skeletons } from '../ui';
import { Avatar } from './common';
import { Confirm, useAction } from './forms';

const ending = (sub: string) => {
  const [used, total] = sub.split('/').map(Number);
  return used >= total - 1;
};

const PLACE = { online: 'онлайн', offline: 'очно' } as const;

/** Вкладка «Группы»: группы с учениками, новая группа, запросы на удаление данных. */
export function TutorGroups({ onGroup, onStudent, onNewGroup }: {
  onGroup: (id: number) => void;
  onStudent: (id: number) => void;
  onNewGroup: () => void;
}) {
  const q = useQuery({ queryKey: ['groups'], queryFn: () => api<Group[]>('/api/tutor/groups') });
  const requests = useQuery({ queryKey: ['deletion-requests'], queryFn: () => api<DeletionRequest[]>('/api/tutor/deletion-requests') });
  const remove = useAction((id: number) => post(`/api/tutor/users/${id}/delete`), 'Данные удалены');
  const count = (q.data ?? []).reduce((n, g) => n + g.students.length, 0);
  return (
    <Screen caps={q.data ? `${count} ${plural(count, 'ученик', 'ученика', 'учеников')} · ${q.data.length} ${plural(q.data.length, 'группа', 'группы', 'групп')}` : 'Группы'} title="Группы">
      {requests.data && requests.data.length > 0 && (
        <section className="card rise list">
          <div className="row static">
            <span className="ico warn-soft"><IconAlert /></span>
            <span className="row-main">
              <span className="row-title">Просят удалить данные: {requests.data.length}</span>
              <span className="foot row-sub">по закону — в течение 30 дней с запроса</span>
            </span>
          </div>
          {requests.data.map((r) => (
            <div key={r.id} className="row static series-row">
              <Avatar id={r.id} name={r.name} />
              <span className="row-main">
                <span className="row-title">{r.name}</span>
                <span className="foot row-sub">{r.role} · запрос {day(r.requestedAt).toLowerCase()}</span>
              </span>
              <Confirm compact label="Удалить" confirm="Удалить" busy={remove.isPending} onConfirm={() => remove.mutate(r.id)}
                       text="Все данные человека будут удалены: аккаунт, группы, связи, записи. Отменить нельзя." />
            </div>
          ))}
        </section>
      )}

      <button type="button" className="btn soft rise" onClick={onNewGroup}>+ Новая группа</button>

      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {q.data && q.data.length === 0 && (
        <Empty title="Групп пока нет" text="Создайте первую. Для индивидуального ученика — группа из одного человека, названная его именем." />
      )}
      {q.data?.map((g, i) => (
        <section key={g.id} className="card rise list" style={{ animationDelay: `${0.06 + i * 0.06}s` }}>
          <button type="button" className="row group-head" onClick={() => onGroup(g.id)}>
            <span className="row-main">
              <span className="row-title">{g.name}</span>
              <span className="foot row-sub">
                {g.students.length} {plural(g.students.length, 'ученик', 'ученика', 'учеников')}
                {g.place && ` · ${PLACE[g.place]}`}
                {g.next ? ` · ${day(g.next.startsAt).toLowerCase()} в ${time(g.next.startsAt)}` : ' · занятий нет'}
              </span>
            </span>
            <span className="status muted">Открыть</span>
            <Chevron />
          </button>
          {g.students.map((s) => (
            <button key={s.id} type="button" className="row" onClick={() => onStudent(s.id)}>
              <Avatar id={s.id} name={s.name} />
              <span className="row-main">
                <span className="row-title">{s.name}</span>
                <span className="foot row-sub">
                  {!s.consent ? 'отозвал согласие' : s.parents ? `${s.parents} ${plural(s.parents, 'родитель', 'родителя', 'родителей')}` : 'родитель не подключён'}
                  {` · ${s.login}`}
                </span>
              </span>
              <span className={'status ' + (!s.subscription ? 'muted' : ending(s.subscription) ? 'warn' : '')}>
                {s.subscription ?? 'нет абон.'}
              </span>
              <Chevron />
            </button>
          ))}
        </section>
      ))}
    </Screen>
  );
}
