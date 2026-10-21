import type { Part, PartKind } from '../api';
import { IconDownload, IconFile } from '../icons';
import { Photo } from '../ui';

const FILE_LABEL: Record<Exclude<PartKind, 'TEXT' | 'PHOTO'>, string> = {
  DOCUMENT: 'Файл',
  VIDEO: 'Видео',
  AUDIO: 'Аудио',
};

/**
 * Содержимое задания или материалов: текст, сетка фото (превью грузится с проверкой доступа),
 * остальные файлы — строками со скачиванием (сервер тоже проверяет доступ).
 */
export function PartsView({ parts, mediaPath }: { parts: Part[]; mediaPath: (id: number) => string }) {
  const texts = parts.filter((p) => p.kind === 'TEXT' && p.text);
  const photos = parts.filter((p) => p.kind === 'PHOTO' && p.mediaId !== null);
  const files = parts.filter((p) => p.kind !== 'TEXT' && p.kind !== 'PHOTO');
  const captions = photos.filter((p) => p.text);
  return (
    <>
      {texts.map((p, i) => (
        <p key={'t' + i} className="part-text">{p.text}</p>
      ))}
      {photos.length > 0 && (
        <div className="thumbs">
          {photos.map((p, i) => (
            <Photo key={p.mediaId} src={mediaPath(p.mediaId!)} label={`Фото ${i + 1}${p.text ? ': ' + p.text : ''}`} />
          ))}
        </div>
      )}
      {captions.map((p, i) => (
        <p key={'c' + i} className="foot" style={{ margin: 0 }}>Фото: {p.text}</p>
      ))}
      {files.map((p, i) => (
        <a key={'f' + i} className="file-row file-link" href={p.mediaId !== null ? mediaPath(p.mediaId) : undefined} download>
          <span className="ico warn-soft"><IconFile /></span>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div className="file-name">{p.name || p.text || FILE_LABEL[p.kind as keyof typeof FILE_LABEL]}</div>
            <div className="foot">{p.name && p.text ? p.text : FILE_LABEL[p.kind as keyof typeof FILE_LABEL] + ' · скачать'}</div>
          </div>
          <IconDownload />
        </a>
      ))}
    </>
  );
}
