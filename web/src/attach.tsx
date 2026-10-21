import { useEffect, useRef, useState, type ClipboardEvent, type DragEvent } from 'react';
import { errorMessage, uploadFile, type Uploaded } from './api';
import { IconFile } from './icons';
import { haptic } from './platform';
import { useToast } from './ui';

/** Сколько файлов за раз: сервер принимает до 20 частей вместе с текстом. */
const MAX_FILES = 19;
/** Что предлагать в окне выбора (сервер всё равно проверяет содержимое). */
const ACCEPT = 'image/*,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,audio/*,video/*';

type Item = {
  key: number;
  name: string;
  size: number;
  /** превью фото до загрузки (object URL) */
  preview: string | null;
  progress: number;
  uploaded: Uploaded | null;
  error: string | null;
};

const size = (bytes: number) =>
  bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} КБ` : `${(bytes / 1024 / 1024).toFixed(1).replace('.', ',')} МБ`;

let nextKey = 1;

/**
 * Файлы формы: каждый загружается сразу после выбора (с прогрессом), в форму уходят только id.
 * Пока что-то грузится — busy, отправлять рано.
 */
export function useAttachments() {
  const [items, setItems] = useState<Item[]>([]);
  const toast = useToast();
  const previews = useRef(new Set<string>());

  useEffect(() => () => previews.current.forEach((u) => URL.revokeObjectURL(u)), []);

  const patch = (key: number, p: Partial<Item>) => setItems((list) => list.map((i) => (i.key === key ? { ...i, ...p } : i)));

  const add = (files: FileList | File[]) => {
    const list = Array.from(files);
    if (list.length === 0) return;
    const room = MAX_FILES - items.length;
    if (list.length > room) toast(`Не больше ${MAX_FILES} файлов за раз`);
    for (const file of list.slice(0, Math.max(0, room))) {
      const key = nextKey++;
      const preview = file.type.startsWith('image/') && file.type !== 'image/heic' ? URL.createObjectURL(file) : null;
      if (preview) previews.current.add(preview);
      setItems((l) => [...l, { key, name: file.name, size: file.size, preview, progress: 0, uploaded: null, error: null }]);
      uploadFile(file, (share) => patch(key, { progress: share }))
        .then((uploaded) => patch(key, { uploaded, progress: 1, size: uploaded.size }))
        .catch((e) => patch(key, { error: errorMessage(e, 'Не загрузился — удалите и добавьте снова') }));
    }
  };

  const remove = (key: number) => {
    haptic('select');
    setItems((list) => {
      const gone = list.find((i) => i.key === key);
      if (gone?.preview) {
        URL.revokeObjectURL(gone.preview);
        previews.current.delete(gone.preview);
      }
      return list.filter((i) => i.key !== key);
    });
  };

  return {
    items,
    add,
    remove,
    ids: items.flatMap((i) => (i.uploaded ? [i.uploaded.id] : [])),
    busy: items.some((i) => !i.uploaded && !i.error),
    failed: items.some((i) => i.error),
  };
}

export type Attachments = ReturnType<typeof useAttachments>;

/**
 * Текст и файлы одной карточкой: «Прикрепить» (на телефоне — галерея, камера, файлы),
 * на компьютере файлы можно перетащить в карточку или вставить картинку из буфера.
 */
export function Composer({ label, text, onText, att, placeholder, autoFocus = false }: {
  label: string;
  text: string;
  onText: (v: string) => void;
  att: Attachments;
  placeholder: string;
  autoFocus?: boolean;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  const drop = (e: DragEvent) => {
    e.preventDefault();
    setOver(false);
    att.add(e.dataTransfer.files);
  };
  const paste = (e: ClipboardEvent) => {
    if (e.clipboardData.files.length > 0) {
      e.preventDefault();
      att.add(e.clipboardData.files);
    }
  };
  return (
    <section className={over ? 'card rise pad composer over' : 'card rise pad composer'}
             onDragOver={(e) => { e.preventDefault(); setOver(true); }} onDragLeave={() => setOver(false)} onDrop={drop}>
      <div className="label">{label}</div>
      <textarea className="comment-input" rows={4} maxLength={4096} value={text} placeholder={placeholder}
                autoFocus={autoFocus} onChange={(e) => onText(e.target.value)} onPaste={paste} />
      {att.items.length > 0 && (
        <ul className="att-list">
          {att.items.map((i) => (
            <li key={i.key} className={i.error ? 'att fail' : 'att'}>
              <span className="att-thumb">{i.preview ? <img src={i.preview} alt="" /> : <IconFile />}</span>
              <span className="row-main">
                <span className="att-name">{i.name}</span>
                <span className={i.error ? 'foot att-error' : 'foot'}>
                  {i.error ?? (i.uploaded ? size(i.size) : `загрузка ${Math.round(i.progress * 100)}% · ${size(i.size)}`)}
                </span>
                {!i.uploaded && !i.error && (
                  <span className="att-bar" aria-hidden="true"><span style={{ transform: `scaleX(${i.progress})` }} /></span>
                )}
              </span>
              <button type="button" className="att-remove" aria-label={`Убрать ${i.name}`} onClick={() => att.remove(i.key)}>×</button>
            </li>
          ))}
        </ul>
      )}
      <input ref={input} type="file" multiple accept={ACCEPT} hidden
             onChange={(e) => { att.add(e.target.files ?? []); e.target.value = ''; }} />
      <button type="button" className="btn soft" onClick={() => input.current?.click()}>
        <IconFile size={18} /> Прикрепить файлы
      </button>
      <p className="foot" style={{ margin: 0 }}>Фото, PDF, Word, Excel, PowerPoint, аудио и видео — до 20 МБ каждый.</p>
    </section>
  );
}
