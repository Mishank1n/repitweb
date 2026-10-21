import { useQuery } from '@tanstack/react-query';
import { useContext, useEffect, useState, type ReactNode } from 'react';
import { api, type Home } from '../api';
import { IconBook, IconCalendar, IconDoc, IconHome } from '../icons';
import { haptic, useStack } from '../platform';
import { NavContext, TabBar, TopContext } from '../ui';
import { StudentHome } from './Home';
import { StudentHomeworkDetail, StudentHomeworkList } from './Homework';
import { StudentMaterialDetail, StudentMaterials } from './Materials';
import { StudentSchedule } from './Schedule';

type Detail = { kind: 'hw'; id: number } | { kind: 'material'; id: number };

/**
 * Экраны ученика: 4 вкладки и вложенные карточки (задание, материалы занятия).
 * Тот же набор позже покажет родителю по выбранному ребёнку (title и header — свои).
 */
export function StudentApp({ studentId, title, top, parent = false, link, onLink }: {
  studentId: number;
  title: ReactNode;
  /** Над содержимым вкладок (переключатель детей у родителя). */
  top?: ReactNode;
  parent?: boolean;
  /** Экран из уведомления: /hw/12, /materials/5, /schedule; onLink — ссылка открыта. */
  link: string | null;
  onLink: () => void;
}) {
  const [tab, setTab] = useState('home');
  const nav = useContext(NavContext);
  const { top: detail, push, pop, clear, open: openStack } = useStack<Detail>();

  useEffect(() => {
    if (!link) return;
    const [, section, rawId] = link.split('/');
    const id = Number(rawId);
    if (section === 'hw' && id) {
      setTab('homework');
      openStack([{ kind: 'hw', id }]);
    } else if (section === 'materials' && id) {
      setTab('materials');
      openStack([{ kind: 'material', id }]);
    } else {
      setTab(section === 'schedule' ? 'schedule' : 'home');
      openStack([]);
    }
    onLink();
  }, [link]);
  const home = useQuery({ queryKey: ['home', studentId], queryFn: () => api<Home>(`/api/students/${studentId}/home`) });

  // Фигурные скобки обязательны: в новых браузерах scrollTo возвращает Promise, а эффект не должен ничего возвращать.
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [tab, detail]);

  const open = (d: Detail) => {
    haptic('select');
    push(d);
  };

  let screen: ReactNode;
  if (detail?.kind === 'hw') {
    screen = <StudentHomeworkDetail studentId={studentId} homeworkId={detail.id} />;
  } else if (detail?.kind === 'material') {
    screen = <StudentMaterialDetail studentId={studentId} lessonId={detail.id} />;
  } else if (tab === 'schedule') {
    screen = <StudentSchedule studentId={studentId} />;
  } else if (tab === 'homework') {
    screen = <StudentHomeworkList studentId={studentId} onOpen={(id) => open({ kind: 'hw', id })} />;
  } else if (tab === 'materials') {
    screen = <StudentMaterials studentId={studentId} onOpen={(id) => open({ kind: 'material', id })} />;
  } else {
    screen = <StudentHome studentId={studentId} title={title} parent={parent} onTab={setTab}
                          onHomework={(id) => open({ kind: 'hw', id })} />;
  }

  return (
    <NavContext.Provider value={detail ? { onBack: pop } : nav}>
    <TopContext.Provider value={detail ? null : top}>
      <div key={detail ? detail.kind + detail.id : tab}>{screen}</div>
      <TabBar
        active={tab}
        nested={!!detail}
        onChange={(id) => { clear(); setTab(id); }}
        tabs={[
          { id: 'home', label: 'Главная', icon: <IconHome /> },
          { id: 'schedule', label: 'Расписание', icon: <IconCalendar /> },
          { id: 'homework', label: 'ДЗ', icon: <IconBook />, badge: home.data?.homeworkTodo || undefined },
          { id: 'materials', label: 'Материалы', icon: <IconDoc /> },
        ]}
      />
    </TopContext.Provider>
    </NavContext.Provider>
  );
}
