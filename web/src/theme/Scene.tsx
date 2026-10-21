import { useEffect, useState } from 'react';
import type { SkyStyle } from './sky';

/** С этой ширины окна сайт в компьютерном виде: пейзаж — обои на всё окно. */
export const DESKTOP_MIN = 768;
/** Шире обычного телефона (планшет, узкое окно): рисунок расширяется, чтобы солнце не обрезалось. */
const PHONE_MAX = 430;

function useViewport() {
  const [size, setSize] = useState(() => ({ w: window.innerWidth, h: window.innerHeight }));
  useEffect(() => {
    const onResize = () => setSize({ w: window.innerWidth, h: window.innerHeight });
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return size;
}

const STARS: [number, number, number, number][] = [
  [40, 40, 1.4, 0], [78, 22, 1, -1.1], [120, 58, 1.6, -2.3], [160, 30, 1, -0.6], [200, 72, 1.2, -1.8],
  [232, 24, 1.5, -2.9], [334, 40, 1.1, -0.3], [356, 80, 1.6, -1.5], [18, 112, 1, -2.1], [100, 122, 1.2, -0.9],
  [190, 130, 1, -2.6], [362, 152, 1.2, -1.2],
];

const RIDGES = [
  'M0 210C40 180 70 150 110 168C140 182 165 120 205 140C240 158 270 110 310 132C345 150 370 140 390 150V330H0Z',
  'M0 238C50 210 90 200 130 222C165 240 200 196 245 210C290 224 320 200 360 214C375 219 385 222 390 224V330H0Z',
  'M0 262C60 246 110 252 160 266C210 280 260 250 310 258C350 264 375 270 390 268V330H0Z',
  'M0 292C70 278 140 286 200 296C260 306 330 286 390 290V330H0Z',
];
const PINES = 'M326 292l8-26 8 26zM344 295l11-34 11 34zM364 292l7-22 7 22z';
const BASE_W = 390;
const BASE_H = 330;
const DAY = ['#F2B9B3', '#D9A1B9', '#9590C4', '#454C86'];
const NIGHT = ['#5B4985', '#473C7D', '#34306A', '#1E1B43'];

/**
 * Пейзаж: слои гор, солнце и луна. Высота задаёт, сколько неба видно (низ всегда горы).
 * На телефоне рисунок 390×330 обрезается по высоте. Шире телефона рисунок расширяется под пропорции
 * пейзажа (горы тянутся по ширине, ели остаются у правого края), а солнце и луна сохраняют форму и стоят
 * в небе справа от заголовка. На компьютере пейзаж — обои на всё окно, над горами и листом с содержимым.
 */
export function Scene({ height, sky }: { height: number; sky: SkyStyle }) {
  const vp = useViewport();
  const desktop = vp.w >= DESKTOP_MIN;
  const wide = desktop || vp.w > PHONE_MAX;
  const sceneH = desktop ? vp.h : height;
  const w = wide ? Math.max(BASE_W, (BASE_H * vp.w) / sceneH) : BASE_W;
  const stretch = w / BASE_W;

  // Солнце (центр 276,118, r 42) и луна (282,106): радиус до 50 px, центр не ниже ~120 px от верха пейзажа.
  let sunAt: string | undefined;
  let moonAt: string | undefined;
  if (wide) {
    const unit = BASE_H / sceneH;
    const k = (Math.min(50, (42 * sceneH) / BASE_H) * unit) / 42;
    const cx = w * 0.72;
    const cy = Math.min(118 * sceneH / BASE_H, 120) * unit;
    sunAt = `translate(${cx - 276 * k} ${cy - 118 * k}) scale(${k})`;
    moonAt = `translate(${cx + 6 * k - 282 * k} ${cy - 12 * k - 106 * k}) scale(${k})`;
  }
  const drift = wide ? ({ ['--drift' as string]: `${w + 80}px` }) : undefined;

  const ridges = (colors: string[], pine: string) => (
    <>
      <g transform={wide ? `scale(${stretch} 1)` : undefined}>
        {RIDGES.map((d, i) => (
          <path key={i} className={'ridge r' + (i + 1)} d={d} fill={colors[i]} />
        ))}
      </g>
      <g transform={wide ? `translate(${w - BASE_W} 0)` : undefined}>
        <path className="ridge r4" d={PINES} fill={pine} />
      </g>
    </>
  );
  return (
    <div className="scene" aria-hidden="true" style={{ height }}>
      <svg viewBox={`0 0 ${w} ${BASE_H}`} width="100%" height={height} preserveAspectRatio="xMidYMax slice">
        <defs>
          <linearGradient id="skyDay" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#FFEBDD" />
            <stop offset=".55" stopColor="#FBD3C6" />
            <stop offset="1" stopColor="#F1C1CF" />
          </linearGradient>
          <linearGradient id="skyNight" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#1E1B44" />
            <stop offset=".6" stopColor="#3A2E6C" />
            <stop offset="1" stopColor="#5C467E" />
          </linearGradient>
          <radialGradient id="sunGlow">
            <stop offset="0" stopColor="#FFF3DA" stopOpacity=".95" />
            <stop offset="1" stopColor="#FFE3C4" stopOpacity="0" />
          </radialGradient>
          <radialGradient id="moonGlow">
            <stop offset="0" stopColor="#FFF4E2" stopOpacity=".35" />
            <stop offset="1" stopColor="#FFF4E2" stopOpacity="0" />
          </radialGradient>
          <linearGradient id="dusk" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0" stopColor="#FF9E7A" stopOpacity="0" />
            <stop offset=".55" stopColor="#FF9E7A" stopOpacity=".6" />
            <stop offset=".8" stopColor="#F27C8C" stopOpacity=".35" />
            <stop offset="1" stopColor="#F27C8C" stopOpacity="0" />
          </linearGradient>
        </defs>

        <g>
          <rect width={w} height={BASE_H} fill="url(#skyDay)" />
          <g transform={sunAt}>
            <g className="sun" style={{ transform: `translateY(${sky.sunY}px)`, opacity: sky.sunO }}>
              <circle cx="276" cy="118" r="92" fill="url(#sunGlow)" />
              <circle cx="276" cy="118" r="42" fill="#FFE5C0" />
            </g>
          </g>
          <g className="cloud" style={drift}>
            <path d="M10 92c0-9 8-15 17-13 3-9 16-12 23-4 9-3 18 4 17 13 6 1 9 6 7 10H12c-4-1-4-5-2-6z" fill="#FFFFFF" opacity=".8" />
          </g>
          <g className="cloud c2" style={drift}>
            <path d="M30 150c0-7 6-12 13-10 3-7 13-9 18-3 7-2 14 3 13 10 5 1 7 5 5 8H31c-3-1-3-4-1-5z" fill="#FFFFFF" opacity=".6" />
          </g>
          {ridges(DAY, '#323A70')}
        </g>

        <g className="layer-night" style={{ opacity: sky.night }}>
          <rect width={w} height={BASE_H} fill="url(#skyNight)" />
          {STARS.map(([cx, cy, r, delay], i) => (
            <circle key={i} className="star" cx={cx * stretch} cy={cy} r={r} fill="#FFFFFF" style={{ animationDelay: `${delay}s` }} />
          ))}
          <g transform={moonAt}>
            <g className="moon" style={{ transform: `translateY(${sky.moonY}px)`, opacity: sky.moonO }}>
              <circle cx="282" cy="106" r="74" fill="url(#moonGlow)" />
              <circle cx="282" cy="106" r="30" fill="#FFF1DC" />
              <circle cx="272" cy="98" r="5" fill="#F1DCC0" />
              <circle cx="292" cy="115" r="3.5" fill="#F1DCC0" />
            </g>
          </g>
          {ridges(NIGHT, '#16143A')}
        </g>

        <rect className="glow" x="0" y="60" width={w} height="230" fill="url(#dusk)" style={{ opacity: sky.glow }} />
      </svg>
    </div>
  );
}
