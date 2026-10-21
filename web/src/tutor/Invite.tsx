import qrcode from 'qrcode-generator';
import { useMemo, useState } from 'react';
import { post, type InviteLink } from '../api';
import { haptic } from '../platform';
import { Screen, useToast } from '../ui';
import { Confirm, useAction } from './forms';

const TITLE = { STUDENT: 'Приглашение ученика', PARENT: 'Приглашение родителя', RESET: 'Ссылка для нового пароля' } as const;
const HINT = {
  STUDENT: 'Отправьте ссылку ученику. По ней он зарегистрируется и попадёт в группу.',
  PARENT: 'Отправьте ссылку родителю. По ней он зарегистрируется и будет видеть расписание, ДЗ и отчёты ребёнка.',
  RESET: 'Отправьте ссылку человеку, который забыл пароль. По ней он задаст новый, а прежние входы на его устройствах закроются.',
} as const;

/** Текст сообщения, с которым ссылка уходит получателю через «Поделиться». */
const SHARE = {
  STUDENT: 'Ссылка для регистрации на сайте репетитора',
  PARENT: 'Ссылка для регистрации родителя на сайте репетитора',
  RESET: 'Ссылка, чтобы задать новый пароль на сайте репетитора',
} as const;

const until = (iso: string) =>
  new Intl.DateTimeFormat('ru-RU', { day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }).format(new Date(iso));

/** QR-код ссылки (SVG): для встречи вживую — навести камеру телефона. */
function Qr({ text }: { text: string }) {
  const svg = useMemo(() => {
    const qr = qrcode(0, 'M');
    qr.addData(text);
    qr.make();
    return qr.createSvgTag({ cellSize: 6, margin: 2, scalable: true });
  }, [text]);
  return <div className="qr" role="img" aria-label="QR-код ссылки" dangerouslySetInnerHTML={{ __html: svg }} />;
}

/** Готовая ссылка: поделиться, скопировать, показать QR, отозвать. */
export function InviteScreen({ invite, onRevoked }: { invite: InviteLink; onRevoked: () => void }) {
  const toast = useToast();
  const [copied, setCopied] = useState(false);
  const canShare = typeof navigator.share === 'function';
  const revoke = useAction(() => post(`/api/tutor/invites/${invite.id}/revoke`), 'Ссылка отозвана', onRevoked);

  const share = async () => {
    try {
      await navigator.share({ title: TITLE[invite.type], text: SHARE[invite.type], url: invite.link });
    } catch {
      // человек закрыл окно «Поделиться» — ничего страшного
    }
  };
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(invite.link);
      haptic();
      setCopied(true);
      toast('Ссылка скопирована');
    } catch {
      toast('Не получилось скопировать — выделите ссылку вручную');
    }
  };

  return (
    <Screen narrow caps={invite.target} title={TITLE[invite.type]}>
      <section className="card rise pad">
        <div className="sub">{HINT[invite.type]}</div>
        <div className="link-box" aria-label="Ссылка">{invite.link}</div>
        <div className="foot">
          Одноразовая, действует до {until(invite.expiresAt)}.
        </div>
        <div className="confirm-actions">
          {canShare && <button type="button" className="btn" onClick={share}>Поделиться</button>}
          <button type="button" className={canShare ? 'btn soft' : 'btn'} onClick={copy}>{copied ? 'Скопировано' : 'Скопировать'}</button>
        </div>
      </section>
      {invite.type !== 'RESET' && (
        <section className="card rise pad qr-card" style={{ animationDelay: '.08s' }}>
          <Qr text={invite.link} />
          <div className="foot">Встречаетесь вживую? Пусть наведут камеру телефона на код.</div>
        </section>
      )}
      <Confirm label="Отозвать ссылку" confirm="Отозвать" busy={revoke.isPending} onConfirm={() => revoke.mutate()}
               text="По ссылке больше нельзя будет войти. Если по ней уже вошли, отозвать её нельзя." />
    </Screen>
  );
}
