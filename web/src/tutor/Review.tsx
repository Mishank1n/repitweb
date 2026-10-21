import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage, post, type QueueRow, type SubmissionDetail } from '../api';
import { day, time } from '../format';
import { PartsView } from '../student/parts';
import { haptic } from '../platform';
import { Chevron, Empty, ErrorCard, Screen, Skeletons, useToast } from '../ui';
import { Avatar } from './common';

const when = (iso: string | null) => (iso ? `${day(iso)}, ${time(iso)}` : '');

export function TutorReview({ onOpen }: { onOpen: (id: number) => void }) {
  const q = useQuery({ queryKey: ['queue'], queryFn: () => api<QueueRow[]>('/api/tutor/queue') });
  return (
    <Screen caps="Сначала самые давние" title="Проверка">
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {q.data && q.data.length === 0 && (
        <Empty title="Всё проверено" text="Когда ученик сдаст работу, она появится здесь, и придёт уведомление." />
      )}
      {q.data && q.data.length > 0 && (
        <div className="card rise list">
          {q.data.map((s) => (
            <button key={s.id} type="button" className="row" onClick={() => onOpen(s.id)}>
              <Avatar id={s.studentId} name={s.student} />
              <span className="row-main">
                <span className="row-title">{s.student}</span>
                <span className="foot row-sub">{s.homework} · {when(s.submittedAt)}</span>
              </span>
              {s.late ? <span className="status warn">после срока</span>
                : s.attempt > 1 ? <span className="status wait">попытка {s.attempt}</span> : null}
              <Chevron />
            </button>
          ))}
        </div>
      )}
    </Screen>
  );
}

export function TutorSubmission({ submissionId, onDone }: { submissionId: number; onDone: () => void }) {
  const base = `/api/tutor/submissions/${submissionId}`;
  const q = useQuery({ queryKey: ['submission', submissionId], queryFn: () => api<SubmissionDetail>(base) });
  const [score, setScore] = useState<number | null>(null);
  const [comment, setComment] = useState('');
  const toast = useToast();
  const client = useQueryClient();
  const done = (text: string) => {
    haptic();
    toast(text);
    client.invalidateQueries();
    onDone();
  };
  const grade = useMutation({
    mutationFn: () => post(base + '/grade', { score, comment }),
    onSuccess: () => done('Оценено — ученик и родители получили результат'),
    onError: (e) => toast(errorMessage(e)),
  });
  const back = useMutation({
    mutationFn: () => post(base + '/return', { comment }),
    onSuccess: () => done('Вернули на доработку — ученик получил комментарий'),
    onError: (e) => toast(errorMessage(e)),
  });

  const d = q.data;
  const s = d?.submission;
  const max = d?.maxScore ?? 10;
  const quick = [...new Set([max, Math.round(max * 0.8), Math.round(max * 0.6), Math.round(max * 0.4)])].filter((n) => n >= 0);
  const step = (delta: number) => {
    haptic('select');
    setScore((v) => Math.min(max, Math.max(0, (v ?? max) + delta)));
  };
  const busy = grade.isPending || back.isPending;
  return (
    <Screen caps={s ? `${s.student} · ${s.group}` : 'Работа'} title={s ? s.homework : '…'}>
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && s && (
        <>
          <div className="chips rise" style={{ animationDelay: '.06s' }}>
            <span className="chip">сдано {when(s.submittedAt).toLowerCase()}</span>
            {s.late && <span className="chip warn">после срока</span>}
            {s.attempt > 1 && <span className="chip">попытка {s.attempt}</span>}
            <span className="chip">из {max} баллов</span>
          </div>

          <section className="card rise pad" style={{ animationDelay: '.12s' }}>
            <div className="label">Ответ</div>
            {d.parts.length === 0
              ? <p className="sub" style={{ margin: 0 }}>Ответ пустой.</p>
              : <PartsView parts={d.parts} mediaPath={(id) => `/api/tutor/media/sub/${id}`} />}
          </section>

          {d.canReview ? (
            <section className="card rise pad" style={{ animationDelay: '.18s' }}>
              <div className="label">Оценка</div>
              <div className="score">
                <button type="button" className="score-btn" aria-label="Меньше" onClick={() => step(-1)}>−</button>
                <span className="score-value">{score ?? '—'}<span className="stat-of">/{max}</span></span>
                <button type="button" className="score-btn" aria-label="Больше" onClick={() => step(1)}>+</button>
              </div>
              <div className="chips">
                {quick.map((n) => (
                  <button key={n} type="button" className={score === n ? 'chip on' : 'chip'} onClick={() => { haptic('select'); setScore(n); }}>{n}</button>
                ))}
              </div>
              <textarea className="comment-input" rows={3} maxLength={2000} value={comment}
                        placeholder="Комментарий: что хорошо, что исправить" onChange={(e) => setComment(e.target.value)} />
              <button type="button" className="btn soft" disabled={busy || !comment.trim()}
                      onClick={() => back.mutate()}>
                Вернуть на доработку
              </button>
              {!comment.trim() && <p className="foot" style={{ margin: 0 }}>Чтобы вернуть на доработку, напишите комментарий.</p>}
            </section>
          ) : (
            <section className="card rise pad">
              <div className="status-line">
                <span className="status-title">{d.status === 'CHECKED' ? 'Проверено' : d.status === 'RETURNED' ? 'На доработке' : 'Не сдано'}</span>
                {d.score !== null && <span className="status ok">{d.score}/{max}</span>}
              </div>
              {d.comment && <p className="comment">«{d.comment}»</p>}
            </section>
          )}

          {d.canReview && (
            <div className="bottom-action">
              <button type="button" className="btn primary" disabled={busy || score === null} onClick={() => grade.mutate()}>
                {score === null ? 'Выберите баллы' : `Оценить: ${score} из ${max}`}
              </button>
            </div>
          )}
        </>
      )}
    </Screen>
  );
}
