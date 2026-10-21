import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, post, type HomeworkDetail, type HomeworkRow } from '../api';
import { Composer, useAttachments } from '../attach';
import { due } from '../format';
import { haptic } from '../platform';
import { useAction } from '../tutor/forms';
import { CLIPPED_TITLE, Empty, ErrorCard, FormError, Screen, Skeletons, Top } from '../ui';
import { PartsView } from './parts';
import { hwLook, HomeworkRowView } from './status';

const ACTIVE = new Set(['TODO', 'RETURNED', 'SUBMITTED']);

export function StudentHomeworkList({ studentId, onOpen }: { studentId: number; onOpen: (id: number) => void }) {
  const q = useQuery({ queryKey: ['homework', studentId], queryFn: () => api<HomeworkRow[]>(`/api/students/${studentId}/homework`) });
  const [tab, setTab] = useState(0);
  const active = (q.data ?? []).filter((r) => ACTIVE.has(r.status));
  const checked = (q.data ?? []).filter((r) => r.status === 'CHECKED');
  const rows = tab === 0 ? active : checked;
  const tabs = [`Активные · ${active.length}`, `Проверенные · ${checked.length}`];
  return (
    <Screen caps="Задания и оценки" title="Домашние задания">
      <Top />
      <div className="seg rise" role="tablist" style={{ gridTemplateColumns: 'repeat(2, minmax(0, 1fr))' }}>
        <span className="ind" aria-hidden="true" style={{ width: 'calc((100% - 12px) / 2)', transform: `translateX(calc(${tab} * (100% + 4px)))` }} />
        {tabs.map((t, i) => (
          <button key={t} type="button" role="tab" aria-selected={tab === i} className={tab === i ? 'on' : ''}
                  onClick={() => { haptic('select'); setTab(i); }}>{t}</button>
        ))}
      </div>
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {q.data && rows.length === 0 && (
        tab === 0
          ? <Empty title="Всё сдано" text="Новые задания появятся здесь — придёт уведомление." />
          : <Empty title="Пока ничего не проверено" text="Здесь будут проверенные работы с баллами и комментариями." />
      )}
      {rows.length > 0 && (
        <div key={tab} className="card rise list">
          {rows.map((r) => <HomeworkRowView key={r.id} row={r} onOpen={() => onOpen(r.id)} />)}
        </div>
      )}
    </Screen>
  );
}

export function StudentHomeworkDetail({ studentId, homeworkId }: { studentId: number; homeworkId: number }) {
  const base = `/api/students/${studentId}/homework/${homeworkId}`;
  const q = useQuery({ queryKey: ['hw', studentId, homeworkId], queryFn: () => api<HomeworkDetail>(base) });
  const d = q.data;
  const hw = d?.homework;
  const look = hw ? hwLook(hw) : null;
  return (
    <Screen caps={hw ? hw.group : 'Домашнее задание'} title={hw ? hw.title : '…'}>
      {q.isPending && <Skeletons count={2} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && hw && look && (
        <>
          <div className="chips rise" style={{ animationDelay: '.06s' }}>
            <span className={'chip ' + (hw.status === 'TODO' ? 'warn' : '')}>{due(hw.dueAt)}</span>
            <span className="chip">из {hw.maxScore} баллов</span>
          </div>

          <section className="card rise pad" style={{ animationDelay: '.12s' }}>
            <div className="label">Задание</div>
            {hw.title.length > CLIPPED_TITLE && <div className="h2">{hw.title}</div>}
            {d.contentHidden
              ? <p className="sub" style={{ margin: 0 }}>Само задание видит только ученик — здесь его статус и оценка.</p>
              : <PartsView parts={d.parts} mediaPath={(id) => `/api/media/hw/${id}`} />}
          </section>

          <section className="card rise pad" style={{ animationDelay: '.18s' }}>
            <div className="status-line">
              <span className="status-title">{
                hw.status === 'CHECKED' ? 'Проверено' : hw.status === 'SUBMITTED' ? 'Ждёт проверки'
                  : hw.status === 'RETURNED' ? 'Нужно доработать' : 'Ещё не сдано'}</span>
              <span className={'status ' + look.tone}>{look.label}</span>
            </div>
            {hw.comment && <p className="comment">«{hw.comment}»</p>}
            {d.answer.length > 0 && (
              <div className="pad answer" style={{ padding: '12px 0 0' }}>
                <div className="label">Ваш ответ{d.attempt > 1 ? ` · попытка ${d.attempt}` : ''}</div>
                <PartsView parts={d.answer} mediaPath={(id) => `/api/media/sub/${id}`} />
              </div>
            )}
          </section>

          {d.canSubmit && <SubmitForm url={base + '/submit'} again={hw.status === 'RETURNED'} />}

        </>
      )}
    </Screen>
  );
}

/** Ответ одной формой: текст и фото/файлы сразу уходят на проверку, репетитор получает уведомление. */
function SubmitForm({ url, again }: { url: string; again: boolean }) {
  const [text, setText] = useState('');
  const att = useAttachments();
  const [error, setError] = useState<string | null>(null);
  const send = useAction(() => post(url, { text, files: att.ids }), 'Работа отправлена — репетитор получил уведомление');
  return (
    <form className="form-stack" onSubmit={(e) => {
      e.preventDefault();
      if (!text.trim() && att.ids.length === 0) {
        setError('Напишите ответ или прикрепите фото тетради');
      } else if (att.failed) {
        setError('Уберите файлы, которые не загрузились');
      } else {
        setError(null);
        send.mutate();
      }
    }}>
      <Composer label={again ? 'Исправленный ответ' : 'Ответ'} text={text} onText={setText} att={att}
                placeholder="Ответ текстом, а решение в тетради — фото" />
      <FormError text={error} />
      <button type="submit" className="btn primary" disabled={send.isPending || att.busy}>
        {att.busy ? 'Файлы загружаются…' : again ? 'Отправить исправленную работу' : 'Отправить на проверку'}
      </button>
    </form>
  );
}
