import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api, errorMessage, post, type StudentCard } from '../api';
import { money, plural } from '../format';
import { IconRedo } from '../icons';
import { haptic } from '../platform';
import { ErrorCard, Screen, Skeletons, useToast } from '../ui';

const QUICK = [4, 8, 12, 16];

export function TutorPayment({ studentId, onDone }: { studentId: number; onDone: () => void }) {
  const q = useQuery({ queryKey: ['student', studentId], queryFn: () => api<StudentCard>(`/api/tutor/students/${studentId}`) });
  const [lessons, setLessons] = useState(8);
  const [amount, setAmount] = useState('');
  const toast = useToast();
  const client = useQueryClient();
  const d = q.data;

  useEffect(() => {
    if (d?.lastTerms) {
      setLessons(d.lastTerms.lessons);
      setAmount(String(d.lastTerms.amount));
    }
  }, [d]);

  const options = QUICK.includes(lessons) ? QUICK : [...QUICK.slice(0, 3), lessons];
  const idx = options.indexOf(lessons);
  const sum = Number(amount.replace(/\D/g, '') || 0);
  const open = d?.subscription;

  const pay = useMutation({
    mutationFn: () => post(`/api/tutor/students/${studentId}/payment`, { lessons, amount: sum, closeCurrent: !!open }),
    onSuccess: () => {
      haptic();
      toast('Абонемент открыт — ученик и родители получили уведомление');
      client.invalidateQueries();
      onDone();
    },
    onError: (e) => toast(errorMessage(e)),
  });

  const lessonsText = `${lessons} ${plural(lessons, 'занятие', 'занятия', 'занятий')}`;
  return (
    <Screen caps={d ? d.name : 'Ученик'} title="Оплата получена">
      {q.isPending && <Skeletons count={3} />}
      {q.isError && <ErrorCard onRetry={() => q.refetch()} />}
      {d && (
        <>
          {open && (
            <p className="card rise pad warn-text" style={{ margin: 0 }}>
              Текущий абонемент {open.used}/{open.total} ещё открыт. Он закроется досрочно, неиспользованные занятия пропадут.
            </p>
          )}
          {d.lastTerms && (
            <button type="button" className="card tap rise row-card" style={{ animationDelay: '.06s' }}
                    onClick={() => { haptic('select'); setLessons(d.lastTerms!.lessons); setAmount(String(d.lastTerms!.amount)); }}>
              <span className="ico wait-soft"><IconRedo /></span>
              <span className="row-main">
                <span className="row-title">Как в прошлый раз</span>
                <span className="foot row-sub">
                  {d.lastTerms.lessons} {plural(d.lastTerms.lessons, 'занятие', 'занятия', 'занятий')} · {money(d.lastTerms.amount)}
                </span>
              </span>
            </button>
          )}

          <section className="card rise pad" style={{ animationDelay: '.12s' }}>
            <div className="label" id="count-label">Занятий в абонементе</div>
            <div className="seg" role="radiogroup" aria-labelledby="count-label" style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}>
              <span className="ind accent" aria-hidden="true" style={{ width: `calc((100% - ${8 + (options.length - 1) * 4}px) / ${options.length})`, transform: `translateX(calc(${idx} * (100% + 4px)))` }} />
              {options.map((n) => (
                <button key={n} type="button" role="radio" aria-checked={n === lessons} className={n === lessons ? 'on big' : 'big'}
                        onClick={() => { haptic('select'); setLessons(n); }}>{n}</button>
              ))}
            </div>
          </section>

          <label className="card rise pad amount" style={{ animationDelay: '.18s' }}>
            <span className="label">Сумма</span>
            <span className="amount-line">
              <input inputMode="numeric" value={sum ? new Intl.NumberFormat('ru-RU').format(sum).replace(/ /g, ' ') : ''}
                     placeholder="0" onChange={(e) => setAmount(e.target.value.replace(/\D/g, '').slice(0, 8))} />
              <span className="ruble">₽</span>
            </span>
            <span className="foot">{sum > 0 ? `Это ${money(Math.round(sum / lessons))} за занятие` : 'Бесплатный абонемент — оставьте 0'}</span>
          </label>

          <section className="card rise pad" style={{ animationDelay: '.24s' }}>
            <div className="notify-line"><b>Ученик</b><span className="foot">«Абонемент: 0 из {lessons}» — без суммы</span></div>
            <div className="notify-line"><b>Родители</b><span className="foot">то же и сумма: {lessonsText}, {money(sum)}</span></div>
          </section>

          <div className="bottom-action">
            <button type="button" className="btn primary" disabled={pay.isPending} onClick={() => pay.mutate()}>
              {open ? 'Закрыть текущий и открыть новый' : `Открыть абонемент на ${lessonsText}`}
            </button>
          </div>
        </>
      )}
    </Screen>
  );
}
