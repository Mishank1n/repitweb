import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api, ApiError, errorMessage, post, type InviteInfo } from '../api';
import { haptic } from '../platform';
import { Empty, ErrorCard, FormError, Screen, Skeletons } from '../ui';
import { AccountForm } from './AccountForm';

/**
 * Страница приглашения /join/CODE: к кому приглашают, текст согласия, регистрация.
 * Если на устройстве уже вошли с подходящей ролью — можно привязать приглашение к этому аккаунту
 * (второй ребёнок родителя, вторая группа ученика).
 */
export function Join({ code }: { code: string }) {
  const q = useQuery({ queryKey: ['invite', code], queryFn: () => api<InviteInfo>(`/api/auth/invite/${code}`), retry: false });
  const [consent, setConsent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const d = q.data;
  const target = d?.type === 'PARENT' ? `Родитель ученика ${d.studentName}` : d ? `Группа «${d.groupName}»` : '';
  const sameRole = d?.current && d.current.role === d.type;

  const linkCurrent = async () => {
    if (!consent) {
      setError('Отметьте согласие на обработку данных');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await post(`/api/auth/invite/${code}`, { consent: true, useCurrent: true });
      haptic();
      window.location.replace('/');
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  };

  const consentBox = (
    <label className="check">
      <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
      <span>Я согласен(на) на обработку персональных данных на условиях выше</span>
    </label>
  );

  return (
    <Screen tall narrow caps="Приглашение" title={d ? target : 'Добро пожаловать'}>
      {q.isPending && <Skeletons count={2} />}
      {q.isError && (q.error instanceof ApiError && q.error.status === 404
        ? <Empty title="Ссылка недействительна" text="Она уже использована или устарела. Попросите у репетитора новую." />
        : <ErrorCard onRetry={() => q.refetch()} />)}
      {d && (
        <>
          <section className="card rise pad">
            <div className="h2">{d.tutorName ? `Репетитор ${d.tutorName}` : 'Сайт репетитора'}</div>
            <p className="consent">{d.consent}</p>
          </section>
          {sameRole ? (
            <section className="card rise pad" style={{ animationDelay: '.08s' }}>
              <div className="sub">Вы вошли как {d.current!.firstName}. Добавить это приглашение к вашему аккаунту?</div>
              {consentBox}
              <FormError text={error} />
              <button type="button" className="btn wide" disabled={busy || !consent} onClick={linkCurrent}>
                Добавить к моему аккаунту
              </button>
            </section>
          ) : (
            <AccountForm
              submitText="Зарегистрироваться"
              canSubmit={consent}
              extra={consentBox}
              onSubmit={(a) => post(`/api/auth/invite/${code}`, { ...a, consent, useCurrent: false })}
            />
          )}
        </>
      )}
    </Screen>
  );
}
