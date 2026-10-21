import { useQuery } from '@tanstack/react-query';
import { api, post, type GroupDetail, type InviteLink } from '../api';
import { due, plural } from '../format';
import { IconPin, IconVideo } from '../icons';
import { Chevron, Empty, ErrorCard, Screen, Skeletons } from '../ui';
import { Avatar, LessonRow } from './common';
import { Confirm, useAction } from './forms';

const until = (iso: string) => new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'short' }).format(new Date(iso));

/** Карточка группы: ученики, приглашения, расписание, место, переименование и удаление. */
export function TutorGroup({ groupId, nav }: {
  groupId: number;
  nav: {
    student: (id: number) => void;
    lesson: (id: number) => void;
    invite: (invite: InviteLink) => void;
    series: (g: GroupDetail) => void;
    oneOff: (g: GroupDetail) => void;
    place: (g: GroupDetail) => void;
    rename: (g: GroupDetail) => void;
    deleted: () => void;
    homework: (id: number) => void;
    issue: (groupId: number) => void;
  };
}) {
  const q = useQuery({ queryKey: ['group', groupId], queryFn: () => api<GroupDetail>(`/api/tutor/groups/${groupId}`) });
  const invite = useAction(() => post<InviteLink>(`/api/tutor/groups/${groupId}/invite`), 'Ссылка готова', nav.invite);
  const remove = useAction(() => post(`/api/tutor/groups/${groupId}/delete`), 'Группа удалена', nav.deleted);
  const removeSeries = useAction((id: number) => post(`/api/tutor/series/${id}/delete`),
    'Регулярное занятие удалено — ученики получили уведомление');
  const d = q.data;

  return (
    <Screen caps={d ? `${d.students.length} ${plural(d.students.length, 'ученик', 'ученика', 'учеников')}` : 'Группа'}
            title={d ? d.name : '…'}>
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && (
        <div className="cols">
          <div className="col">
            <div className="section-head rise">
              <h2 className="h2">Ученики</h2>
            </div>
            {d.students.length === 0 ? (
              <Empty title="Учеников пока нет" text="Нажмите «Пригласить ученика» и отправьте ссылку." />
            ) : (
              <div className="card rise list">
                {d.students.map((s) => (
                  <button key={s.id} type="button" className="row" onClick={() => nav.student(s.id)}>
                    <Avatar id={s.id} name={s.name} />
                    <span className="row-main">
                      <span className="row-title">{s.name}</span>
                      <span className="foot row-sub">
                        {!s.consent ? 'отозвал согласие' : s.parents.length ? s.parents.map((p) => p.name).join(', ') : 'родитель не подключён'}
                      </span>
                    </span>
                    <span className={'status ' + (s.subscription ? '' : 'muted')}>{s.subscription ?? 'нет абон.'}</span>
                    <Chevron />
                  </button>
                ))}
              </div>
            )}
            <button type="button" className="btn rise" disabled={invite.isPending} onClick={() => invite.mutate()}>
              + Пригласить ученика
            </button>
            {d.invites.length > 0 && (
              <div className="card rise list">
                <div className="row static"><span className="label">Действующие ссылки</span></div>
                {d.invites.map((i) => (
                  <button key={i.id} type="button" className="row" onClick={() => nav.invite(i)}>
                    <span className="row-main">
                      <span className="row-title">Ссылка для ученика</span>
                      <span className="foot row-sub">действует до {until(i.expiresAt)}, ещё не использована</span>
                    </span>
                    <Chevron />
                  </button>
                ))}
              </div>
            )}

            <div className="section-head rise section-pad">
              <h2 className="h2">Домашние задания</h2>
            </div>
            {d.homework.length > 0 && (
              <div className="card rise list">
                {d.homework.map((h) => (
                  <button key={h.id} type="button" className="row" onClick={() => nav.homework(h.id)}>
                    <span className="row-main">
                      <span className="row-title">{h.title}</span>
                      <span className="foot row-sub">{due(h.dueAt)} · сдали {h.handedIn} из {h.assigned}</span>
                    </span>
                    {h.waiting > 0 && <span className="status wait">проверить {h.waiting}</span>}
                    <Chevron />
                  </button>
                ))}
              </div>
            )}
            <button type="button" className="btn soft rise" disabled={d.students.length === 0}
                    onClick={() => nav.issue(d.id)}>+ Выдать ДЗ</button>

            <div className="section-head rise">
              <h2 className="h2">Место занятий</h2>
              <button type="button" className="link" onClick={() => nav.place(d)}>Изменить</button>
            </div>
            <section className="card rise pad tight">
              {d.place === 'online' && <div className="sub address"><IconVideo /> {d.meetingUrl}</div>}
              {d.place === 'offline' && <div className="sub address"><IconPin /> {d.address}</div>}
              {!d.place && <div className="foot">Не задано. Укажите ссылку на созвон или адрес — ученики увидят их в напоминаниях.</div>}
            </section>
          </div>

          <div className="col">
            <div className="section-head rise">
              <h2 className="h2">Расписание</h2>
            </div>
            <section className="card rise list">
              {d.series.length === 0 && (
                <div className="row static"><span className="foot">Регулярных занятий нет</span></div>
              )}
              {d.series.map((s) => (
                <div key={s.id} className="row static series-row">
                  <span className="row-main">
                    <span className="row-title">{s.line.replace(/^еженедельно, /, 'Каждую неделю: ')}</span>
                  </span>
                  <Confirm compact label="Удалить" confirm="Удалить"
                           busy={removeSeries.isPending} onConfirm={() => removeSeries.mutate(s.id)}
                           text="Будущие занятия этой серии удалятся, ученики и родители получат уведомление. Прошедшие останутся." />
                </div>
              ))}
            </section>
            <div className="grid2">
              <button type="button" className="btn soft rise" onClick={() => nav.series(d)}>+ Регулярное</button>
              <button type="button" className="btn soft rise" onClick={() => nav.oneOff(d)}>+ Разовое</button>
            </div>

            <h2 className="h2 rise section-pad">Ближайшие занятия</h2>
            {d.upcoming.length === 0 ? (
              <Empty title="Занятий нет" text="Добавьте регулярное или разовое занятие." />
            ) : (
              <div className="card rise list">
                {d.upcoming.map((l) => <LessonRow key={l.id} lesson={l} withDate onOpen={() => nav.lesson(l.id)} />)}
              </div>
            )}

            <h2 className="h2 rise section-pad">Группа</h2>
            <button type="button" className="btn soft rise" onClick={() => nav.rename(d)}>Переименовать</button>
            <Confirm label="Удалить группу" confirm="Да, удалить" busy={remove.isPending} onConfirm={() => remove.mutate()}
                     text={<>
                       Учеников: {d.students.length}, будущих занятий: {d.futureLessons}. Расписание и занятия группы удалятся
                       {d.futureLessons > 0 ? ', ученики и родители получат уведомление' : ''}. Сами ученики и родители
                       останутся — их можно пригласить в другую группу. Отменить нельзя.
                     </>} />
          </div>
        </div>
      )}
    </Screen>
  );
}
