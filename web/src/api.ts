/** Ошибка API: HTTP-статус и код из ответа сервера (unauthorized, no_consent, invalid…). */
export class ApiError extends Error {
  constructor(public status: number, public code: string, public text?: string) {
    super(text ?? code);
  }
}

/** Текст ошибки действия или формы для показа человеку (сервер присылает его в message). */
export const errorMessage = (e: unknown, fallback = 'Не получилось, попробуйте ещё раз') =>
  e instanceof ApiError && e.text ? e.text : fallback;

/** Событие «вход потерян» (сессия истекла или закрыта на другом устройстве): приложение покажет вход. */
export const AUTH_LOST = 'auth-lost';

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  // Вход — cookie сессии: браузер отправляет её сам, скрипты страницы её не видят.
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
      ...init.headers,
    },
  });
  if (!response.ok) {
    let code = 'error';
    let text: string | undefined;
    try {
      const body = await response.json();
      code = body.error ?? code;
      text = body.message;
    } catch {
      // тело не JSON — оставляем общий код
    }
    if (response.status === 401 && !path.startsWith('/api/auth/')) {
      window.dispatchEvent(new Event(AUTH_LOST));
    }
    throw new ApiError(response.status, code, text);
  }
  return response.json() as Promise<T>;
}

export const api = <T,>(path: string) => request<T>(path);
export const post = <T,>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });

export type Role = 'TUTOR' | 'STUDENT' | 'PARENT';

export type Me = {
  userId: number;
  login: string;
  role: Role;
  firstName: string;
  displayName: string;
  timezone: string;
  children: { id: number; firstName: string; displayName: string }[];
};

export type Lesson = {
  id: number;
  startsAt: string;
  durationMin: number;
  group: string;
  place: 'online' | 'offline' | null;
  meetingUrl: string | null;
  address: string | null;
  ongoing: boolean;
  /** уже предупредили «не смогу прийти» */
  skipped: boolean;
  /** занятие ещё не началось — можно предупредить */
  canSkip: boolean;
};

export type Subscription = { used: number; total: number; amount: number | null; paidAt: string };

export type HwStatus = 'TODO' | 'SUBMITTED' | 'RETURNED' | 'CHECKED';

export type HomeworkRow = {
  id: number;
  title: string;
  group: string;
  issuedAt: string | null;
  dueAt: string | null;
  overdue: boolean;
  maxScore: number;
  status: HwStatus;
  score: number | null;
  comment: string | null;
};

export type PartKind = 'TEXT' | 'PHOTO' | 'DOCUMENT' | 'VIDEO' | 'AUDIO';
/** mediaId — у файлов: превью фото и скачивание остального; name — имя файла. */
export type Part = { kind: PartKind; text: string | null; mediaId: number | null; name: string | null };

export type HomeworkDetail = {
  homework: HomeworkRow; parts: Part[]; contentHidden: boolean; canSubmit: boolean;
  /** свой ответ последней попытки (только ученику) */
  answer: Part[];
  attempt: number;
};

export type Home = {
  next: Lesson | null;
  subscription: Subscription | null;
  homeworkTodo: number;
  nearestDue: string | null;
  homework: HomeworkRow[];
  attendance: { present: number; total: number };
};

export type MaterialLesson = { lessonId: number; startsAt: string; group: string; parts: number };
export type MaterialDetail = { lesson: MaterialLesson; parts: Part[] };

// ---------- репетитор ----------

export type LessonStatus = 'upcoming' | 'ongoing' | 'unmarked' | 'done' | 'cancelled';
export type TutorLesson = {
  id: number; startsAt: string; durationMin: number; groupId: number; group: string; status: LessonStatus; students: number;
};
export type StudentChip = { id: number; name: string; used: number; total: number };
export type Today = { lessons: TutorLesson[]; unmarked: TutorLesson[]; queue: number; ending: StudentChip[] };
export type Mark = 'P' | 'C' | 'F';
/** warned — предупредил «не смогу прийти» */
export type RosterRow = { studentId: number; name: string; status: Mark; subscription: string | null; warned: boolean };
export type MaterialItem = { id: number; part: Part };
export type Place = 'online' | 'offline' | null;
export type LessonDetail = {
  lesson: TutorLesson; place: Place; meetingUrl: string | null; address: string | null;
  canMark: boolean; marked: boolean; roster: RosterRow[]; materials: MaterialItem[];
  /** будущее занятие: можно перенести или отменить */
  canMove: boolean;
  /** «еженедельно, пн в 18:00, 60 мин» или null для разового */
  series: string | null;
};
export type GroupStudent = { id: number; name: string; login: string; subscription: string | null; parents: number; consent: boolean };
export type Group = { id: number; name: string; place: Place; next: TutorLesson | null; students: GroupStudent[] };
export type Ref = { id: number; name: string };
export type SubscriptionRow = { paidAt: string; total: number; used: number; amount: number; closedAt: string | null };
export type StudentCard = {
  id: number; name: string; login: string; consent: boolean; deletionRequested: boolean; groups: Ref[]; parents: Ref[];
  subscription: Subscription | null; history: SubscriptionRow[]; outside: number;
  lastTerms: { lessons: number; amount: number } | null; homework: HomeworkRow[];
  attendance: { present: number; total: number };
};
export type QueueRow = {
  id: number; studentId: number; student: string; homework: string; group: string; submittedAt: string | null; late: boolean; attempt: number;
};
export type SubmissionDetail = {
  submission: QueueRow; maxScore: number; status: HwStatus | 'DRAFT'; score: number | null; comment: string | null;
  parts: Part[]; canReview: boolean;
};

// ---------- вход ----------

export type InviteInfo = {
  type: 'STUDENT' | 'PARENT';
  groupName: string | null;
  studentName: string | null;
  tutorName: string | null;
  consent: string;
  current: { firstName: string; role: Role } | null;
};
export type ResetInfo = { firstName: string; login: string };

// ---------- файлы ----------

export type Uploaded = { id: string; name: string; mime: string; size: number; kind: PartKind };

export const MAX_FILE_BYTES = 20 * 1024 * 1024;
/** Фото шире или выше этого уменьшаются перед отправкой: снимок с телефона весит 4–12 МБ, а канал бывает медленным. */
const MAX_PHOTO_SIDE = 2560;

/** Уменьшает большое фото (JPEG/PNG/WebP) в браузере; остальные файлы и небольшие фото — как есть. */
async function shrinkPhoto(file: File): Promise<File> {
  if (!/^image\/(jpeg|png|webp)$/.test(file.type) || file.size < 1024 * 1024) return file;
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, MAX_PHOTO_SIDE / Math.max(bitmap.width, bitmap.height));
    if (scale === 1 && file.size <= 4 * 1024 * 1024) {
      bitmap.close();
      return file;
    }
    const canvas = document.createElement('canvas');
    canvas.width = Math.round(bitmap.width * scale);
    canvas.height = Math.round(bitmap.height * scale);
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.85));
    if (!blob || blob.size >= file.size) return file;
    return new File([blob], file.name.replace(/\.\w+$/, '') + '.jpg', { type: 'image/jpeg' });
  } catch {
    return file; // браузер не смог разобрать картинку — пусть сервер решит
  }
}

/**
 * Загружает файл; onProgress — доля от 0 до 1. Через XMLHttpRequest: у fetch нет прогресса отправки.
 * Ошибки — ApiError с текстом для человека (большой файл, неподдерживаемый тип).
 */
export async function uploadFile(original: File, onProgress?: (share: number) => void): Promise<Uploaded> {
  const file = await shrinkPhoto(original);
  if (file.size > MAX_FILE_BYTES) {
    throw new ApiError(400, 'too_large', `Файл «${original.name}» больше 20 МБ`);
  }
  const form = new FormData();
  form.append('file', file, file.name);
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/files');
    xhr.withCredentials = true;
    xhr.responseType = 'json';
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress?.(e.loaded / e.total);
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.response as Uploaded);
        return;
      }
      if (xhr.status === 401) window.dispatchEvent(new Event(AUTH_LOST));
      const body = xhr.response as { error?: string; message?: string } | null;
      reject(new ApiError(xhr.status, body?.error ?? 'error', body?.message));
    };
    xhr.onerror = () => reject(new ApiError(0, 'network', 'Не удалось отправить файл — проверьте интернет'));
    xhr.send(form);
  });
}

// ---------- группы, приглашения, расписание (репетитор) ----------

export type InviteType = 'STUDENT' | 'PARENT' | 'RESET';
/** Ссылка-приглашение или на новый пароль; target — группа, ученик или человек, которому она. */
export type InviteLink = { id: number; type: InviteType; link: string; expiresAt: string; target: string };
export type Series = { id: number; day: number; time: string; durationMin: number; line: string };
export type GroupMember = { id: number; name: string; login: string; subscription: string | null; parents: Ref[]; consent: boolean };
export type GroupDetail = {
  id: number; name: string; place: Place; meetingUrl: string | null; address: string | null;
  students: GroupMember[]; series: Series[]; upcoming: TutorLesson[]; invites: InviteLink[]; futureLessons: number;
  homework: HomeworkSummary[];
};

// ---------- ДЗ и материалы (репетитор) ----------

export type HomeworkSummary = {
  id: number; title: string; issuedAt: string | null; dueAt: string | null; overdue: boolean; maxScore: number;
  assigned: number; handedIn: number; waiting: number;
};
export type HomeworkStudent = {
  id: number; name: string; status: HwStatus; score: number | null; submissionId: number | null; late: boolean; attempt: number;
};
export type HomeworkCard = { homework: HomeworkSummary; group: Ref; parts: Part[]; students: HomeworkStudent[] };
export type Issued = { id: number; notified: number };
export type MaterialsSent = { students: number; parents: number };
export type DeletionRequest = { id: number; name: string; role: string; requestedAt: string };

// ---------- уведомления ----------

export type FeedItem = { id: number; title: string; body: string; url: string | null; createdAt: string; read: boolean };
export type Feed = { unread: number; items: FeedItem[] };

// ---------- отчёты родителям (репетитор) ----------

export type WeekReport = { id: number; studentId: number; name: string; comment: string | null; sent: boolean; preview: string };
export type ReportsScreen = { week: string; sendAt: string; canSendNow: boolean; unmarked: number; reports: WeekReport[] };
export type ReportsSent = { parents: number; students: number };
