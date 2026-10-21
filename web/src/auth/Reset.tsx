import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, ApiError, errorMessage, post, type ResetInfo } from '../api';
import { haptic } from '../platform';
import { Empty, ErrorCard, Field, FormError, Screen, Skeletons } from '../ui';

/** Новый пароль по ссылке /reset/CODE от репетитора. Остальные устройства после этого выходят из аккаунта. */
export function Reset({ code }: { code: string }) {
  const q = useQuery({ queryKey: ['reset', code], queryFn: () => api<ResetInfo>(`/api/auth/reset/${code}`), retry: false });
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (password !== repeat) {
      setError('Пароли не совпадают');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await post(`/api/auth/reset/${code}`, { password });
      haptic();
      window.location.replace('/');
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  const d = q.data;
  return (
    <Screen tall narrow caps="Новый пароль" title={d ? d.firstName : 'Новый пароль'}>
      {q.isPending && <Skeletons count={1} />}
      {q.isError && (q.error instanceof ApiError && q.error.status === 404
        ? <Empty title="Ссылка недействительна" text="Она уже использована или устарела (ссылка действует сутки). Попросите у репетитора новую." />
        : <ErrorCard onRetry={() => q.refetch()} />)}
      {d && (
        <form className="card rise pad" onSubmit={submit}>
          <div className="sub">Ваш логин: <b>{d.login}</b>. Придумайте новый пароль.</div>
          <input type="text" name="username" autoComplete="username" value={d.login} readOnly hidden />
          <Field label="Новый пароль" type="password" autoComplete="new-password" value={password}
                 onChange={(e) => setPassword(e.target.value)} minLength={8} required hint="Не короче 8 символов" />
          <Field label="Пароль ещё раз" type="password" autoComplete="new-password" value={repeat}
                 onChange={(e) => setRepeat(e.target.value)} required />
          <FormError text={error} />
          <button type="submit" className="btn wide" disabled={busy}>Сохранить и войти</button>
        </form>
      )}
    </Screen>
  );
}
