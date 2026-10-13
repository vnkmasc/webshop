export function LogoMark({ className = 'logo-mark' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 64 64" aria-hidden>
      <rect width="64" height="64" rx="14" fill="#e4572e" />
      <path d="M10 40 L14 26 Q16 20 24 20 L30 21 Q34 16 40 16 L44 17 Q47 25 52 28 Q58 31 58 40 Z" fill="#fff" />
      <rect x="10" y="40" width="48" height="6" rx="3" fill="#ffd6c9" />
    </svg>
  );
}
