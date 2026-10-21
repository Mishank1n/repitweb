// Линейные иконки 24×24, цвет — currentColor.
const base = { width: 22, height: 22, viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 2,
  strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const, 'aria-hidden': true };

export const IconHome = () => (
  <svg {...base} fill="currentColor" stroke="none"><path d="M12 3.2l9 7V20a1 1 0 0 1-1 1h-5.5v-6h-5v6H4a1 1 0 0 1-1-1v-9.8z" /></svg>
);
export const IconCalendar = () => (
  <svg {...base}><rect x="3" y="5" width="18" height="16" rx="4" /><path d="M3 10h18M8 3v4M16 3v4" /></svg>
);
export const IconBook = () => (
  <svg {...base}><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H19v15H6.5A2.5 2.5 0 0 0 4 20.5z" /><path d="M4 20.5V5.5M19 18v3H6.5" /></svg>
);
export const IconDoc = () => (
  <svg {...base}><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5M9 13h6M9 17h4" /></svg>
);
export const IconVideo = ({ size = 18 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.4}><rect x="2" y="6" width="14" height="12" rx="3" /><path d="M16 10.5l6-3.5v10l-6-3.5" /></svg>
);
export const IconClock = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.4}><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>
);
export const IconSend = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.4}><path d="M22 2L11 13" /><path d="M22 2l-7 20-4-9-9-4z" /></svg>
);
export const IconCheck = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.6}><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
);
export const IconRedo = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.4}><path d="M3.5 12a8.5 8.5 0 1 0 2.8-6.3L3.5 8.3" /><path d="M3.5 3.5v4.8h4.8" /></svg>
);
export const IconFile = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.2}><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5" /></svg>
);
export const IconPin = ({ size = 16 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.4}><path d="M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21z" /><circle cx="12" cy="9.5" r="2.5" /></svg>
);
export const IconSun = () => (
  <svg {...base} fill="currentColor" stroke="none"><circle cx="12" cy="12" r="4.5" /><path d="M12 1.8v2.6M12 19.6v2.6M4.8 4.8l1.8 1.8M17.4 17.4l1.8 1.8M1.8 12h2.6M19.6 12h2.6M4.8 19.2l1.8-1.8M17.4 6.6l1.8-1.8" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" /></svg>
);
export const IconPeople = () => (
  <svg {...base}><circle cx="9" cy="8" r="3.5" /><path d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.5a3.5 3.5 0 0 1 0 7M18.5 14.5A6.5 6.5 0 0 1 21.5 20" /></svg>
);
export const IconInbox = () => (
  <svg {...base}><path d="M3 13h5l1.5 3h5L16 13h5" /><path d="M5.5 5h13l2.5 8v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-6z" /></svg>
);
export const IconAlert = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.8}><path d="M12 6v8M12 18.5v.5" /></svg>
);
export const IconBack = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.6}><path d="M15 5l-7 7 7 7" /></svg>
);
export const IconGear = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.2}>
    <circle cx="12" cy="12" r="3" />
    <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />
  </svg>
);
export const IconDownload = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.4}><path d="M12 3v12M7 10l5 5 5-5M5 21h14" /></svg>
);
export const IconBell = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.2}>
    <path d="M6 9a6 6 0 0 1 12 0c0 6 2.5 7.5 2.5 7.5h-17S6 15 6 9" /><path d="M10 20a2.2 2.2 0 0 0 4 0" />
  </svg>
);
export const IconChart = ({ size = 20 }: { size?: number }) => (
  <svg {...base} width={size} height={size} strokeWidth={2.4}><path d="M5 20V11M12 20V5M19 20v-6" /></svg>
);
