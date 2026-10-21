import { useEffect, useLayoutEffect, useState } from 'react';
import { loadSetting, saveSetting } from '../platform';
import { computeSky, nowHour, type Override } from './sky';

const KEY = 'theme';

/** Тема по часам устройства + ручное закрепление (запоминается на устройстве). */
export function useDayTheme() {
  const [hour, setHour] = useState(nowHour);
  const [override, setOverride] = useState<Override>(() => {
    const v = loadSetting(KEY);
    return v === 'day' || v === 'night' ? v : null;
  });

  useEffect(() => {
    const timer = setInterval(() => setHour(nowHour()), 30_000);
    return () => clearInterval(timer);
  }, []);

  const sky = computeSky(hour, override);

  // До отрисовки: ночью страница сразу открывается ночной, без вспышки дневных цветов.
  useLayoutEffect(() => {
    document.documentElement.classList.toggle('night', sky.uiNight);
    // Цвет строки состояния и панели браузера на телефоне — под небо.
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', sky.headerColor);
  }, [sky.uiNight, sky.headerColor]);

  const pin = (value: Override) => {
    setOverride(value);
    saveSetting(KEY, value);
  };

  return { sky, override, pin };
}
