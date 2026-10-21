import { useState, type FormEvent, type ReactNode } from 'react';
import { errorMessage } from '../api';
import { haptic } from '../platform';
import { Field, FormError } from '../ui';

export type Account = { firstName: string; lastName: string; login: string; password: string };

/**
 * Имя, логин и пароль нового аккаунта (приглашение и первая настройка репетитора).
 * Пароль вводится дважды; правила логина и пароля проверяет сервер и присылает понятный текст.
 */
export function AccountForm({ submitText, extra, canSubmit = true, onSubmit }: {
  submitText: string;
  /** Над кнопкой (галочка согласия). */
  extra?: ReactNode;
  canSubmit?: boolean;
  onSubmit: (a: Account) => Promise<unknown>;
}) {
  const [a, setA] = useState<Account>({ firstName: '', lastName: '', login: '', password: '' });
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof Account) => (e: { target: { value: string } }) => setA({ ...a, [k]: e.target.value });

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (a.password !== repeat) {
      setError('Пароли не совпадают');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await onSubmit(a);
      haptic();
      window.location.replace('/');
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  };

  return (
    <form className="card rise pad" onSubmit={submit} style={{ animationDelay: '.08s' }}>
      <Field label="Имя" autoComplete="given-name" value={a.firstName} onChange={set('firstName')} maxLength={100} required />
      <Field label="Фамилия" autoComplete="family-name" value={a.lastName} onChange={set('lastName')} maxLength={100} />
      <Field label="Логин" name="username" autoComplete="username" autoCapitalize="none" autoCorrect="off" spellCheck={false}
             value={a.login} onChange={set('login')} maxLength={32} required
             hint="Латинские буквы и цифры, например anna.k — по нему вы будете входить" />
      <Field label="Пароль" type="password" autoComplete="new-password" value={a.password} onChange={set('password')}
             minLength={8} required hint="Не короче 8 символов" />
      <Field label="Пароль ещё раз" type="password" autoComplete="new-password" value={repeat}
             onChange={(e) => setRepeat(e.target.value)} required />
      {extra}
      <FormError text={error} />
      <button type="submit" className="btn wide" disabled={busy || !canSubmit}>{submitText}</button>
    </form>
  );
}
