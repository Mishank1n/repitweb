import type { TutorLesson } from '../api';
import { day, plural, time } from '../format';
import { Chevron } from '../ui';

const AVATARS = [
  'linear-gradient(135deg, #FFD9C7, #F7A99A)',
  'linear-gradient(135deg, #DCD6F7, #A99FDA)',
  'linear-gradient(135deg, #FFE7B8, #F6C27E)',
  'linear-gradient(135deg, #CDEBDD, #8CCFB0)',
  'linear-gradient(135deg, #F9D3E3, #E99BBB)',
];

/** Кружок с первой буквой имени; цвет стабилен для одного человека. */
export function Avatar({ id, name }: { id: number; name: string }) {
  return (
    <span className="avatar" aria-hidden="true" style={{ background: AVATARS[id % AVATARS.length] }}>
      {name.trim().charAt(0).toUpperCase() || '?'}
    </span>
  );
}

export const endTime = (l: TutorLesson) =>
  time(new Date(new Date(l.startsAt).getTime() + l.durationMin * 60_000).toISOString());

function statusLook(l: TutorLesson): { label: string; tone: string } {
  switch (l.status) {
    case 'done':
      return { label: 'Проведено', tone: 'ok' };
    case 'unmarked':
      return { label: 'Отметить', tone: 'warn' };
    case 'ongoing':
      return { label: 'Идёт', tone: 'ok' };
    default: {
      const minutes = Math.round((new Date(l.startsAt).getTime() - Date.now()) / 60_000);
      if (minutes < 60) return { label: `через ${Math.max(1, minutes)} мин`, tone: 'muted' };
      if (minutes < 24 * 60) {
        const h = Math.round(minutes / 60);
        return { label: `через ${h} ${plural(h, 'час', 'часа', 'часов')}`, tone: 'muted' };
      }
      return { label: '', tone: 'muted' };
    }
  }
}

/** withDate — в списке за несколько дней (группа) рядом со временем нужен день. */
export function LessonRow({ lesson, onOpen, withDate = false }: { lesson: TutorLesson; onOpen: () => void; withDate?: boolean }) {
  const look = statusLook(lesson);
  return (
    <button type="button" className="row" onClick={onOpen}>
      <span className={lesson.status === 'ongoing' ? 'time-pill now' : 'time-pill'}>{time(lesson.startsAt)}</span>
      <span className="row-main">
        <span className="row-title">{lesson.group}</span>
        <span className="foot row-sub">
          {withDate && `${day(lesson.startsAt)} · `}
          {lesson.students} {plural(lesson.students, 'ученик', 'ученика', 'учеников')} · до {endTime(lesson)}
        </span>
      </span>
      {look.label && <span className={'status ' + look.tone}>{look.label}</span>}
      <Chevron />
    </button>
  );
}
