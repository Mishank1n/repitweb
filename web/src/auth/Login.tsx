import { useState, type FormEvent, type ReactNode } from 'react';
import { errorMessage, post } from '../api';
import { haptic } from '../platform';
import { Field, FormError, Screen } from '../ui';

/** Вход по логину и паролю. После входа страница перезагружается — все данные грузятся заново уже от имени пользователя. */
export function Login({ greeting }: { greeting: ReactNode }) {
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await post('/api/auth/login', { login, password });
      haptic();
      window.location.replace('/');
    } catch (err) {
      setError(errorMessage(err, 'Не удалось войти. Проверьте интернет и попробуйте ещё раз.'));
      setBusy(false);
    }
  };

  return (
    <Screen tall narrow caps="Вход" title={greeting}>
      <form className="card rise pad" onSubmit={submit}>
        <Field label="Логин" name="username" autoComplete="username" autoCapitalize="none" autoCorrect="off"
               spellCheck={false} value={login} onChange={(e) => setLogin(e.target.value)} required />
        <Field label="Пароль" name="password" type="password" autoComplete="current-password"
               value={password} onChange={(e) => setPassword(e.target.value)} required />
        <FormError text={error} />
        <button type="submit" className="btn wide" disabled={busy}>Войти</button>
      </form>
      <section className="card rise pad" style={{ animationDelay: '.08s' }}>
        <div className="sub">Нет аккаунта? Попросите у репетитора ссылку-приглашение.</div>
        <div className="foot">Забыли пароль — репетитор пришлёт ссылку, по которой можно задать новый.</div>
      </section>
    </Screen>
  );
}
