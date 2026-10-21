import { useQuery } from '@tanstack/react-query';
import { api, post, type InviteLink, type StudentCard as Card } from '../api';
import { day, money, plural } from '../format';
import { HomeworkRowView } from '../student/status';
import { Chevron, ErrorCard, Ring, Screen, Skeletons } from '../ui';
import { Avatar } from './common';
import { Confirm, useAction } from './forms';

const short = (iso: string) => new Intl.DateTimeFormat('ru-RU', { day: '2-digit', month: '2-digit', year: '2-digit' }).format(new Date(iso));

export function TutorStudentCard({ studentId, onPayment, onInvite, onDeleted, onHomework }: {
  studentId: number;
  onPayment: () => void;
  onInvite: (invite: InviteLink) => void;
  onDeleted: () => void;
  onHomework: (id: number) => void;
}) {
  const base = `/api/tutor/students/${studentId}`;
  const q = useQuery({ queryKey: ['student', studentId], queryFn: () => api<Card>(base) });
  const invites = useQuery({ queryKey: ['parent-invites', studentId], queryFn: () => api<InviteLink[]>(base + '/parent-invites') });
  const parentInvite = useAction(() => post<InviteLink>(base + '/parent-invite'), 'Ссылка готова', onInvite);
  const resetLink = useAction(() => post<InviteLink>(`/api/tutor/users/${studentId}/reset-link`), 'Ссылка готова', onInvite);
  const unlink = useAction((parentId: number) => post(`${base}/parents/${parentId}/unlink`), 'Родитель отвязан');
  const leave = useAction((groupId: number) => post(`/api/tutor/groups/${groupId}/students/${studentId}/remove`),
    'Ученик убран из группы');
  const remove = useAction(() => post(`/api/tutor/users/${studentId}/delete`), 'Данные ученика удалены', onDeleted);
  const d = q.data;
  return (
    <Screen caps={d ? d.groups.map((g) => g.name).join(' · ') || 'без группы' : 'Ученик'} title={d ? d.name : '…'}>
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && (
        <>
          <section className="card rise sub-card" style={{ animationDelay: '.06s' }}>
            {d.subscription ? (
              <>
                <Ring used={d.subscription.used} total={d.subscription.total} size={72} />
                <div>
                  <div className="label">Абонемент</div>
                  <div className="stat-num big">{d.subscription.used} из {d.subscription.total}</div>
                  <div className="foot">{money(d.subscription.amount ?? 0)} · с {day(d.subscription.paidAt).toLowerCase()}</div>
                </div>
              </>
            ) : (
              <div>
                <div className="label">Абонемент</div>
                <div className="stat-num big">Абонемента нет</div>
                <div className={d.outside ? 'foot warn-text' : 'foot'}>
                  {d.outside ? `Вне абонемента: ${d.outside} ${plural(d.outside, 'занятие', 'занятия', 'занятий')}` : 'Нажмите «Оплата получена», когда ученик оплатит'}
                </div>
              </div>
            )}
          </section>

          <div className="grid2">
            <div className="card rise stat" style={{ animationDelay: '.12s' }}>
              <span className="stat-big">{d.attendance.present}<span className="stat-of">/{d.attendance.total}</span></span>
              <span>
                <span className="stat-num">Посещаемость</span>
                <span className="foot">в этом месяце</span>
              </span>
            </div>
            <div className="card rise stat" style={{ animationDelay: '.16s' }}>
              <span className="stat-big">{d.parents.length}</span>
              <span>
                <span className="stat-num">{plural(d.parents.length, 'родитель', 'родителя', 'родителей')}</span>
                <span className="foot row-sub">{d.parents.map((p) => p.name).join(', ') || 'пока не подключены'}</span>
              </span>
            </div>
          </div>
          {d.deletionRequested && (
            <p className="foot rise hint warn-text">
              Ученик отозвал согласие и просит удалить данные. По закону это нужно сделать в течение 30 дней — кнопка внизу.
            </p>
          )}
          {!d.consent && !d.deletionRequested && <p className="foot rise hint warn-text">Ученик отозвал согласие: уведомления ему не приходят.</p>}

          {d.homework.length > 0 && (
            <>
              <h2 className="h2 rise section-pad" style={{ animationDelay: '.2s' }}>Последние задания</h2>
              <div className="card rise list" style={{ animationDelay: '.24s' }}>
                {d.homework.map((r) => <HomeworkRowView key={r.id} row={r} onOpen={() => onHomework(r.id)} />)}
              </div>
            </>
          )}

          {d.history.length > 0 && (
            <>
              <h2 className="h2 rise section-pad" style={{ animationDelay: '.28s' }}>Абонементы</h2>
              <div className="card rise list" style={{ animationDelay: '.32s' }}>
                {d.history.map((h) => (
                  <div key={h.paidAt} className="row static">
                    <span className="row-main">
                      <span className="row-title">{h.total} {plural(h.total, 'занятие', 'занятия', 'занятий')} · {money(h.amount)}</span>
                      <span className="foot row-sub">оплачен {short(h.paidAt)}{h.closedAt ? ` · закрыт ${short(h.closedAt)}` : ''}</span>
                    </span>
                    <span className={'status ' + (h.closedAt ? 'muted' : 'ok')}>{h.used}/{h.total}</span>
                  </div>
                ))}
              </div>
            </>
          )}

          <h2 className="h2 rise section-pad">Родители</h2>
          <div className="card rise list">
            {d.parents.length === 0 && <div className="row static"><span className="foot">Родители не подключены</span></div>}
            {d.parents.map((p) => (
              <div key={p.id} className="row static series-row">
                <Avatar id={p.id} name={p.name} />
                <span className="row-main"><span className="row-title">{p.name}</span></span>
                <Confirm compact label="Отвязать" confirm="Отвязать" busy={unlink.isPending} onConfirm={() => unlink.mutate(p.id)}
                         text="Родитель перестанет видеть этого ученика. Если других детей у него нет, его данные удалятся автоматически в течение суток." />
              </div>
            ))}
            {(invites.data ?? []).map((i) => (
              <button key={i.id} type="button" className="row" onClick={() => onInvite(i)}>
                <span className="row-main">
                  <span className="row-title">Ссылка для родителя</span>
                  <span className="foot row-sub">ещё не использована</span>
                </span>
                <Chevron />
              </button>
            ))}
          </div>
          <button type="button" className="btn soft rise" disabled={parentInvite.isPending} onClick={() => parentInvite.mutate()}>
            + Пригласить родителя
          </button>

          <h2 className="h2 rise section-pad">Вход</h2>
          <section className="card rise pad tight">
            <div className="sub">Логин: <b>{d.login}</b></div>
            <div className="foot">Забыл пароль? Создайте ссылку — по ней ученик задаст новый, ссылка действует сутки.</div>
            <button type="button" className="btn soft" disabled={resetLink.isPending} onClick={() => resetLink.mutate()}>
              Ссылка для нового пароля
            </button>
          </section>

          <h2 className="h2 rise section-pad">Группы</h2>
          <div className="card rise list">
            {d.groups.length === 0 && <div className="row static"><span className="foot">Не состоит ни в одной группе</span></div>}
            {d.groups.map((g) => (
              <div key={g.id} className="row static series-row">
                <span className="row-main"><span className="row-title">{g.name}</span></span>
                <Confirm compact label="Убрать" confirm="Убрать" busy={leave.isPending} onConfirm={() => leave.mutate(g.id)}
                         text="Ученик пропадёт из этой группы и её расписания. Сам он и его данные останутся." />
              </div>
            ))}
          </div>

          <Confirm label="Удалить ученика и все данные" confirm="Да, удалить" busy={remove.isPending} onConfirm={() => remove.mutate()}
                   text="Удалятся аккаунт, членство в группах, связи с родителями, абонементы, посещаемость и сданные работы. Отменить нельзя." />

          <div className="bottom-action">
            <button type="button" className="btn primary" onClick={onPayment}>Оплата получена</button>
          </div>
        </>
      )}
    </Screen>
  );
}
