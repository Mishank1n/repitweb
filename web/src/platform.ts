// Мелочи «как в приложении» для обычного браузера: вибрация, настройки устройства, стек экранов с «Назад».
import { useEffect, useRef, useState } from 'react';

/** Лёгкая вибрация на Android (iPhone из браузера вибрировать не умеет — просто ничего не будет). */
export function haptic(kind: 'select' | 'tap' = 'tap') {
  try {
    navigator.vibrate?.(kind === 'select' ? 5 : 10);
  } catch {
    // нет вибрации — не страшно
  }
}

/** Настройка этого устройства (тема, выбранный ребёнок). */
export function loadSetting(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function saveSetting(key: string, value: string | null) {
  try {
    if (value === null) {
      localStorage.removeItem(key);
    } else {
      localStorage.setItem(key, value);
    }
  } catch {
    // Хранилище недоступно (приватный режим) — настройка просто не запомнится.
  }
}

/**
 * Стек вложенных экранов, связанный с историей браузера: каждый открытый экран — запись в истории,
 * поэтому «Назад» браузера и жест «назад» на Android закрывают его. pop() тоже идёт через историю.
 * name — своё поле в записи истории: стеки приложения ('screen') и окон поверх него (настройки, лента —
 * 'overlay') не путают глубину друг друга.
 */
export function useStack<T>(name = 'screen') {
  const [stack, setStack] = useState<T[]>([]);
  const ref = useRef<T[]>([]);
  /** Экраны, которые нужно открыть, когда история вернётся к началу стека (см. open). */
  const pending = useRef<T[] | null>(null);

  const record = (depth: number) => ({ ...(history.state ?? {}), [name]: depth });
  const pushAll = (items: T[]) => {
    for (const item of items) {
      ref.current = [...ref.current, item];
      history.pushState(record(ref.current.length), '');
    }
  };

  useEffect(() => {
    const onPop = (e: PopStateEvent) => {
      const depth = typeof e.state?.[name] === 'number' ? e.state[name] : 0;
      ref.current = ref.current.slice(0, depth);
      if (pending.current && depth === 0) {
        const items = pending.current;
        pending.current = null;
        pushAll(items);
      }
      setStack(ref.current);
    };
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  const push = (item: T) => {
    pushAll([item]);
    setStack(ref.current);
  };
  const pop = () => {
    if (ref.current.length) history.back();
  };
  /** Заменить верхний экран другим (после «Создать группу» — сама группа), не добавляя шаг в историю. */
  const replace = (item: T) => {
    ref.current = [...ref.current.slice(0, -1), item];
    history.replaceState(record(ref.current.length), '');
    setStack(ref.current);
  };
  /** Закрыть все вложенные экраны (переход в раздел из боковой панели). */
  const clear = () => open([]);
  /**
   * Показать ровно эти экраны (переход по уведомлению). Если что-то уже открыто, сначала история
   * возвращается к началу стека, и только потом добавляются новые записи — иначе «Назад» вёл бы не туда.
   */
  const open = (items: T[]) => {
    const depth = ref.current.length;
    if (depth === 0) {
      pushAll(items);
      setStack(ref.current);
      return;
    }
    pending.current = items.length ? items : null;
    ref.current = [];
    setStack([]);
    history.go(-depth);
  };
  return { stack, top: stack[stack.length - 1] as T | undefined, push, pop, replace, clear, open };
}

/** Внешняя ссылка (созвон) — в новой вкладке, сайт остаётся открытым. */
export function openLink(url: string) {
  window.open(url, '_blank', 'noopener');
}
