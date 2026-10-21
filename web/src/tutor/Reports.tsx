import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, post, type ReportsScreen, type ReportsSent, type WeekReport } from '../api';
import { plural } from '../format';
import { IconAlert } from '../icons';
import { haptic } from '../platform';
import { Chevron, Empty, ErrorCard, Screen, Skeletons } from '../ui';
import { Avatar } from './common';
import { Confirm, useAction } from './forms';

/**
 * Отчёты родителям за неделю: кому уйдут, комментарий к каждому и предпросмотр — ровно то, что получит родитель.
 * Уходят сами в день отчёта; в этот день их можно отправить и сразу.
 */
export function TutorReports() {
  const q = useQuery({ queryKey: ['reports'], queryFn: () => api<ReportsScreen>('/api/tutor/reports') });
  const send = useAction(() => post<ReportsSent>('/api/tutor/reports/send'), 'Отчёты отправлены родителям');
  const d = q.data;
  const left = d ? d.reports.filter((r) => !r.sent).length : 0;
  return (
    <Screen caps={d ? `Неделя ${d.week}` : 'Отчёты'} title="Отчёты родителям">
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && (
        <>
          <p className="foot rise hint">
            {left > 0 ? `Уйдут ${d.sendAt.toLowerCase()}. ` : ''}
            В отчёте — посещаемость, задания недели и абонемент. Комментарий не обязателен, он будет в конце.
          </p>
          {d.unmarked > 0 && (
            <section className="card rise list">
              <div className="row static">
                <span className="ico warn-soft"><IconAlert /></span>
                <span className="row-main">
                  <span className="row-title">Не отмечено занятий: {d.unmarked}</span>
                  <span className="foot row-sub">пока посещаемость не отмечена, этих занятий нет в отчётах</span>
                </span>
              </div>
            </section>
          )}
          {d.reports.length === 0 ? (
            <Empty title="Отчётов на этой неделе нет"
                   text="Отчёт получает родитель ученика, у которого за неделю были отмеченные занятия или задания." />
          ) : (
            d.reports.map((r) => <ReportCard key={r.id} report={r} />)
          )}
          {d.canSendNow && left > 0 && (
            <Confirm label={`Отправить сейчас · ${left} ${plural(left, 'отчёт', 'отчёта', 'отчётов')}`} confirm="Отправить"
                     busy={send.isPending} onConfirm={() => send.mutate()}
                     text="Родители получат отчёты сейчас, а не в назначенное время. Комментарии после отправки уже не добавить." />
          )}
        </>
      )}
    </Screen>
  );
}

function ReportCard({ report }: { report: WeekReport }) {
  const [open, setOpen] = useState(false);
  const [comment, setComment] = useState(report.comment ?? '');
  const save = useAction(() => post(`/api/tutor/reports/${report.id}/comment`, { comment }), 'Комментарий сохранён');
  const changed = comment.trim() !== (report.comment ?? '');
  return (
    <section className="card rise list">
      <button type="button" className="row" aria-expanded={open} onClick={() => { haptic('select'); setOpen(!open); }}>
        <Avatar id={report.studentId} name={report.name} />
        <span className="row-main">
          <span className="row-title">{report.name}</span>
          <span className="foot row-sub">{report.comment ? `«${report.comment}»` : 'без комментария'}</span>
        </span>
        <span className={report.sent ? 'status ok' : 'status muted'}>{report.sent ? 'отправлен' : open ? 'свернуть' : 'открыть'}</span>
        <Chevron />
      </button>
      {open && (
        <div className="pad">
          <div className="label">Так увидит родитель</div>
          <pre className="report-text">{report.preview}</pre>
          {!report.sent && (
            <>
              <textarea className="comment-input" rows={3} maxLength={1000} value={comment}
                        placeholder="Комментарий для родителей: успехи, на что обратить внимание"
                        onChange={(e) => setComment(e.target.value)} />
              <button type="button" className="btn soft" disabled={save.isPending || !changed} onClick={() => save.mutate()}>
                Сохранить комментарий
              </button>
            </>
          )}
        </div>
      )}
    </section>
  );
}
