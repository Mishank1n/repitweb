import type { HomeworkRow } from '../api';
import { due } from '../format';
import { IconCheck, IconClock, IconRedo, IconSend } from '../icons';
import { Chevron } from '../ui';

/** Как показать статус ДЗ: подпись справа, цвет, иконка и строка под названием. */
export function hwLook(r: HomeworkRow) {
  switch (r.status) {
    case 'TODO':
      return { label: r.overdue ? 'Срок прошёл' : 'Не сдано', tone: 'warn', icon: <IconClock />, sub: due(r.dueAt) };
    case 'RETURNED':
      return { label: 'Доработать', tone: 'warn', icon: <IconRedo />, sub: r.comment ? `«${r.comment}»` : 'нужно доработать' };
    case 'SUBMITTED':
      return { label: 'Проверка', tone: 'wait', icon: <IconSend />, sub: 'ждёт проверки' };
    case 'CHECKED':
      return { label: `${r.score}/${r.maxScore}`, tone: 'ok', icon: <IconCheck />, sub: r.comment ? `«${r.comment}»` : 'проверено' };
  }
}

/** Строка задания; без onOpen — просто строка (например, в карточке ученика у репетитора). */
export function HomeworkRowView({ row, onOpen }: { row: HomeworkRow; onOpen?: () => void }) {
  const look = hwLook(row);
  return (
    <button type="button" className={onOpen ? 'row' : 'row static'} onClick={onOpen} disabled={!onOpen}>
      <span className={'ico ' + look.tone + '-soft'}>{look.icon}</span>
      <span className="row-main">
        <span className="row-title">{row.title}</span>
        <span className="foot row-sub">{look.sub}</span>
      </span>
      <span className={'status ' + look.tone}>{look.label}</span>
      {onOpen && <Chevron />}
    </button>
  );
}
