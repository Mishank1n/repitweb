import { useQuery } from '@tanstack/react-query';
import { useContext, useEffect, useState, type ReactNode } from 'react';
import { api, post, type GroupDetail, type InviteLink, type LessonDetail, type Today } from '../api';
import { IconCalendar, IconInbox, IconPeople, IconSun } from '../icons';
import { haptic, useStack } from '../platform';
import { NavContext, TabBar } from '../ui';
import { LessonForm, NameForm, PlaceForm, SeriesForm } from './forms';
import { TutorGroup } from './Group';
import { TutorGroups } from './Groups';
import { InviteScreen } from './Invite';
import { TutorHomework, HomeworkForm } from './Homework';
import { MaterialsForm, TutorLessonCard } from './Lesson';
import { TutorPayment } from './Payment';
import { TutorReports } from './Reports';
import { TutorReview, TutorSubmission } from './Review';
import { TutorStudentCard } from './StudentCard';
import { TutorToday } from './Today';
import { TutorWeek } from './Week';

type Detail =
  | { kind: 'lesson'; id: number }
  | { kind: 'student'; id: number }
  | { kind: 'payment'; id: number }
  | { kind: 'submission'; id: number }
  | { kind: 'group'; id: number }
  | { kind: 'newGroup' }
  | { kind: 'rename'; group: GroupDetail }
  | { kind: 'series'; group: GroupDetail }
  | { kind: 'oneOff'; group: GroupDetail }
  | { kind: 'place'; group: GroupDetail }
  | { kind: 'move'; lesson: LessonDetail }
  | { kind: 'invite'; invite: InviteLink }
  | { kind: 'homework'; id: number }
  | { kind: 'issue'; groupId: number }
  | { kind: 'materials'; lesson: LessonDetail }
  | { kind: 'reports' };

const isoDate = (iso: string) => {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
const hhmm = (iso: string) => {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

/** Репетитор: 4 вкладки и стек вложенных экранов (группа → ученик → оплата, занятие → перенос, ссылки, формы). */
export function TutorApp({ title, link, onLink }: {
  title: ReactNode;
  /** Экран из уведомления: /lesson/5, /review/3, /students/7, /reports…; onLink — ссылка открыта. */
  link: string | null;
  onLink: () => void;
}) {
  const [tab, setTab] = useState('today');
  const nav = useContext(NavContext);
  const { stack, top: detail, push: pushStack, pop, replace, clear, open } = useStack<Detail>();
  const today = useQuery({ queryKey: ['today'], queryFn: () => api<Today>('/api/tutor/today') });

  useEffect(() => {
    if (!link) return;
    const [, section, rawId] = link.split('/');
    const id = Number(rawId);
    const go = (t: string, screens: Detail[]) => {
      setTab(t);
      open(screens);
    };
    if (section === 'lesson' && id) go('today', [{ kind: 'lesson', id }]);
    else if (section === 'review' && id) go('review', [{ kind: 'submission', id }]);
    else if (section === 'students' && id) go('groups', [{ kind: 'student', id }]);
    else if (section === 'students') go('groups', []);
    else if (section === 'hw' && id) go('groups', [{ kind: 'homework', id }]);
    else if (section === 'reports') go('today', [{ kind: 'reports' }]);
    else if (section === 'schedule') go('week', []);
    else go('today', []);
    onLink();
  }, [link]);

  // Фигурные скобки обязательны: в новых браузерах scrollTo возвращает Promise, а эффект не должен ничего возвращать.
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [tab, stack.length]);

  const push = (d: Detail) => {
    haptic('select');
    pushStack(d);
  };
  const toLesson = (id: number) => push({ kind: 'lesson', id });
  const toStudent = (id: number) => push({ kind: 'student', id });
  const toInvite = (invite: InviteLink) => push({ kind: 'invite', invite });
  const toHomework = (id: number) => push({ kind: 'homework', id });
  const toIssue = (groupId: number) => push({ kind: 'issue', groupId });

  let screen: ReactNode;
  if (detail?.kind === 'lesson') {
    screen = <TutorLessonCard lessonId={detail.id} onDone={pop} onMove={(lesson) => push({ kind: 'move', lesson })}
                              onMaterials={(lesson) => push({ kind: 'materials', lesson })} onHomework={toIssue} />;
  } else if (detail?.kind === 'student') {
    screen = <TutorStudentCard studentId={detail.id} onPayment={() => push({ kind: 'payment', id: detail.id })}
                               onInvite={toInvite} onDeleted={pop} onHomework={toHomework} />;
  } else if (detail?.kind === 'payment') {
    screen = <TutorPayment studentId={detail.id} onDone={pop} />;
  } else if (detail?.kind === 'submission') {
    screen = <TutorSubmission submissionId={detail.id} onDone={pop} />;
  } else if (detail?.kind === 'group') {
    screen = (
      <TutorGroup groupId={detail.id} nav={{
        student: toStudent,
        lesson: toLesson,
        invite: toInvite,
        series: (group) => push({ kind: 'series', group }),
        oneOff: (group) => push({ kind: 'oneOff', group }),
        place: (group) => push({ kind: 'place', group }),
        rename: (group) => push({ kind: 'rename', group }),
        deleted: pop,
        homework: toHomework,
        issue: toIssue,
      }} />
    );
  } else if (detail?.kind === 'newGroup') {
    screen = (
      <NameForm caps="Группы" title="Новая группа" submit="Создать"
                onSubmit={(name) => post<GroupDetail>('/api/tutor/groups', { name })
                  .then((g) => replace({ kind: 'group', id: g.id }))} />
    );
  } else if (detail?.kind === 'rename') {
    screen = (
      <NameForm caps={detail.group.name} title="Переименовать" initial={detail.group.name} submit="Сохранить"
                onSubmit={(name) => post(`/api/tutor/groups/${detail.group.id}/rename`, { name }).then(pop)} />
    );
  } else if (detail?.kind === 'series') {
    screen = (
      <SeriesForm groupName={detail.group.name}
                  onSubmit={(v) => post(`/api/tutor/groups/${detail.group.id}/series`, v).then(pop)} />
    );
  } else if (detail?.kind === 'oneOff') {
    screen = (
      <LessonForm caps={detail.group.name} title="Разовое занятие" submitText="Добавить"
                  done="Занятие добавлено — ученики получили уведомление"
                  onSubmit={(v) => post(`/api/tutor/groups/${detail.group.id}/lessons`, v).then(pop)} />
    );
  } else if (detail?.kind === 'move') {
    const l = detail.lesson.lesson;
    screen = (
      <LessonForm move caps={l.group} title="Перенос занятия" submitText="Перенести"
                  initialDate={isoDate(l.startsAt)} initialTime={hhmm(l.startsAt)}
                  done="Занятие перенесено — ученики получили уведомление"
                  onSubmit={(v) => post(`/api/tutor/lessons/${l.id}/move`, { date: v.date, time: v.time }).then(pop)} />
    );
  } else if (detail?.kind === 'place') {
    const g = detail.group;
    screen = (
      <PlaceForm groupName={g.name} place={g.place} meetingUrl={g.meetingUrl} address={g.address}
                 onSubmit={(v) => post(`/api/tutor/groups/${g.id}/place`, v).then(pop)} />
    );
  } else if (detail?.kind === 'homework') {
    screen = <TutorHomework homeworkId={detail.id} onSubmission={(id) => push({ kind: 'submission', id })} onDeleted={pop} />;
  } else if (detail?.kind === 'issue') {
    screen = <HomeworkForm groupId={detail.groupId} onIssued={(id) => replace({ kind: 'homework', id })} />;
  } else if (detail?.kind === 'materials') {
    screen = <MaterialsForm lesson={detail.lesson} onDone={pop} />;
  } else if (detail?.kind === 'reports') {
    screen = <TutorReports />;
  } else if (detail?.kind === 'invite') {
    screen = <InviteScreen invite={detail.invite} onRevoked={pop} />;
  } else if (tab === 'week') {
    screen = <TutorWeek onLesson={toLesson} />;
  } else if (tab === 'groups') {
    screen = <TutorGroups onGroup={(id) => push({ kind: 'group', id })} onStudent={toStudent}
                          onNewGroup={() => push({ kind: 'newGroup' })} />;
  } else if (tab === 'review') {
    screen = <TutorReview onOpen={(id) => push({ kind: 'submission', id })} />;
  } else {
    screen = (
      <TutorToday
        title={title}
        onLesson={toLesson}
        onStudent={toStudent}
        onReview={() => setTab('review')}
        onReports={() => push({ kind: 'reports' })}
      />
    );
  }

  return (
    <NavContext.Provider value={detail ? { onBack: pop } : nav}>
      <div key={detail ? `${stack.length}-${detail.kind}` : tab}>{screen}</div>
      <TabBar
        active={tab}
        nested={!!detail}
        onChange={(id) => { clear(); setTab(id); }}
        tabs={[
          { id: 'today', label: 'Сегодня', icon: <IconSun /> },
          { id: 'week', label: 'Неделя', icon: <IconCalendar /> },
          { id: 'groups', label: 'Группы', icon: <IconPeople /> },
          { id: 'review', label: 'Проверка', icon: <IconInbox />, badge: today.data?.queue || undefined },
        ]}
      />
    </NavContext.Provider>
  );
}
