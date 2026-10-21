import { useQuery } from '@tanstack/react-query';
import { api, post } from '../api';
import { Empty, Screen, Skeletons } from '../ui';
import { AccountForm } from './AccountForm';

/** Первая настройка сайта /setup/CODE: репетитор задаёт себе имя, логин и пароль (ссылка — в логе сервера). */
export function Setup({ code }: { code: string }) {
  const q = useQuery({ queryKey: ['setup'], queryFn: () => api<{ needed: boolean }>('/api/auth/setup') });
  return (
    <Screen tall narrow caps="Первая настройка" title="Аккаунт репетитора">
      {q.isPending && <Skeletons count={2} />}
      {q.data && !q.data.needed && (
        <Empty title="Сайт уже настроен" text="Войдите со своим логином и паролем на главной странице." />
      )}
      {q.data?.needed && (
        <AccountForm submitText="Создать аккаунт" onSubmit={(a) => post(`/api/auth/setup/${code}`, a)} />
      )}
    </Screen>
  );
}
