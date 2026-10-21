import { useState, type FormEvent } from 'react';
import { errorMessage, post, type Me } from '../api';
import { PushSettings } from '../feed';
import { disablePush } from '../push';
import { haptic } from '../platform';
import { Field, FormError, Screen, useToast } from '../ui';

const leave = () => window.location.replace('/');

/** Настройки аккаунта: уведомления на этом устройстве, пароль, выход, выход на всех устройствах, отзыв согласия. */
export function Settings({ me }: { me: Me }) {
  const toast = useToast();
  const [current, setCurrent] = useState('');
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const changePassword = async (e: FormEvent) => {
    e.preventDefault();
    if (password !== repeat) {
      setError('Пароли не совпадают');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await post('/api/account/password', { current, password });
      haptic();
      setCurrent('');
      setPassword('');
      setRepeat('');
      toast('Пароль изменён. На других устройствах нужно войти заново');
    } catch (err) {
      setError(errorMessage(err));
    }
    setBusy(false);
  };

  const action = async (path: string) => {
    try {
      // Уведомления этого аккаунта на это устройство больше не нужны (здесь может войти другой человек).
      await disablePush().catch(() => {});
      await post(path);
      leave();
    } catch (err) {
      toast(errorMessage(err));
    }
  };

  return (
    <Screen narrow caps={`Логин: ${me.login}`} title="Настройки">
      <PushSettings />

      <form className="card rise pad" onSubmit={changePassword}>
        <div className="h2">Сменить пароль</div>
        <input type="text" name="username" autoComplete="username" value={me.login} readOnly hidden />
        <Field label="Текущий пароль" type="password" autoComplete="current-password" value={current}
               onChange={(e) => setCurrent(e.target.value)} required />
        <Field label="Новый пароль" type="password" autoComplete="new-password" value={password}
               onChange={(e) => setPassword(e.target.value)} minLength={8} required hint="Не короче 8 символов" />
        <Field label="Новый пароль ещё раз" type="password" autoComplete="new-password" value={repeat}
               onChange={(e) => setRepeat(e.target.value)} required />
        <FormError text={error} />
        <button type="submit" className="btn wide" disabled={busy}>Сохранить пароль</button>
      </form>

      <section className="card rise pad" style={{ animationDelay: '.06s' }}>
        <button type="button" className="btn soft wide" onClick={() => action('/api/auth/logout')}>Выйти</button>
        <button type="button" className="btn soft wide" onClick={() => action('/api/account/logout-all')}>
          Выйти на всех устройствах
        </button>
        <div className="foot">Если телефон потерян или вы входили с чужого устройства.</div>
      </section>

      {me.role !== 'TUTOR' && (
        <section className="card rise pad" style={{ animationDelay: '.12s' }}>
          <div className="h2">Удалить мои данные</div>
          <div className="foot">
            Согласие на обработку данных будет отозвано: уведомления прекратятся, вход закроется,
            а репетитор удалит ваши данные в течение 30 дней.
          </div>
          {confirmDelete ? (
            <button type="button" className="btn danger wide" onClick={() => action('/api/account/delete-me')}>
              Да, удалить мои данные
            </button>
          ) : (
            <button type="button" className="btn soft wide" onClick={() => setConfirmDelete(true)}>Удалить данные…</button>
          )}
        </section>
      )}
    </Screen>
  );
}
