import dayjs from 'dayjs';

const vnd = new Intl.NumberFormat('vi-VN');

/** 1290000 -> "1.290.000₫" */
export function money(value: number | null | undefined): string {
  if (value === null || value === undefined) return '';
  return `${vnd.format(Math.round(value))}₫`;
}

function oneDecimal(n: number): string {
  return n.toFixed(1).replace(/\.0$/, '').replace('.', ',');
}

export function shortMoney(value: number): string {
  if (Math.abs(value) >= 1_000_000_000) return `${oneDecimal(value / 1_000_000_000)} tỷ`;
  if (Math.abs(value) >= 1_000_000) return `${oneDecimal(value / 1_000_000)} tr`;
  if (Math.abs(value) >= 1_000) return `${Math.round(value / 1_000)}k`;
  return `${value}`;
}

export function dateTime(value: string | null | undefined): string {
  return value ? dayjs(value).format('DD/MM/YYYY HH:mm') : '';
}

export function date(value: string | null | undefined): string {
  return value ? dayjs(value).format('DD/MM/YYYY') : '';
}

export function discountPercent(price: number, salePrice: number | null): number | null {
  if (!salePrice || salePrice >= price) return null;
  return Math.round((1 - salePrice / price) * 100);
}
