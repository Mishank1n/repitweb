import { useEffect, useState, type ReactNode } from 'react';
import { api, type Me } from '../api';
import { StudentApp } from '../student/StudentApp';
import { haptic, loadSetting, saveSetting } from '../platform';
import { Empty, Screen } from '../ui';

const KEY = 'child';

/**
 * Родитель: те же экраны, что у ученика, по выбранному ребёнку. Если детей несколько —
 * переключатель сверху, выбор запоминается. Содержимое заданий родитель не видит (сервер их не отдаёт).
 */
export function ParentApp({ me, title, link, onLink }: { me: Me; title: ReactNode; link: string | null; onLink: () => void }) {
  const kids = me.children;
  const [childId, setChildId] = useState<number | null>(kids[0]?.id ?? null);
  /** Ссылка для экранов выбранного ребёнка — после того как понятно, о каком ребёнке она. */
  const [childLink, setChildLink] = useState<string | null>(null);

  useEffect(() => {
    const saved = kids.find((k) => String(k.id) === loadSetting(KEY));
    if (saved) setChildId(saved.id);
  }, []);

  // Уведомление о задании или материалах — про одного из детей: ищем, кому из них оно открывается.
  useEffect(() => {
    if (!link) return;
    onLink();
    const [, section, id] = link.split('/');
    const path = section === 'hw' ? 'homework' : section === 'materials' ? 'materials' : null;
    if (!path || !id || kids.length < 2) {
      setChildLink(link);
      return;
    }
    (async () => {
      for (const k of kids) {
        const ok = await api(`/api/students/${k.id}/${path}/${id}`).then(() => true, () => false);
        if (ok) {
          setChildId(k.id);
          break;
        }
      }
      setChildLink(link);
    })();
  }, [link]);

  if (kids.length === 0 || childId === null) {
    return (
      <Screen tall caps="Родитель" title={title}>
        <Empty title="Пока никого" text="Попросите у репетитора ссылку для родителя и откройте её." />
      </Screen>
    );
  }

  const index = Math.max(0, kids.findIndex((k) => k.id === childId));
  const pick = (id: number) => {
    if (id === childId) return;
    haptic('select');
    setChildId(id);
    saveSetting(KEY, String(id));
  };

  const switcher = kids.length < 2 ? (
    <div className="kid-solo rise">{kids[0].displayName}</div>
  ) : (
    <div className="seg rise" role="tablist" aria-label="Ребёнок"
         style={{ gridTemplateColumns: `repeat(${kids.length}, minmax(0, 1fr))` }}>
      <span className="ind" aria-hidden="true" style={{
        width: `calc((100% - ${8 + (kids.length - 1) * 4}px) / ${kids.length})`,
        transform: `translateX(calc(${index} * (100% + 4px)))`,
      }} />
      {kids.map((k) => (
        <button key={k.id} type="button" role="tab" aria-selected={k.id === childId}
                className={k.id === childId ? 'on' : ''} onClick={() => pick(k.id)}>
          {k.firstName}
        </button>
      ))}
    </div>
  );

  return <StudentApp key={childId} studentId={childId} title={title} top={switcher} parent
                     link={childLink} onLink={() => setChildLink(null)} />;
}
