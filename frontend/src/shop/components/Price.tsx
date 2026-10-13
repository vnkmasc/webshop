import { money } from '@/utils/format';

export function Price({ price, salePrice }: { price: number; salePrice: number | null }) {
  const onSale = salePrice !== null && salePrice < price;
  return (
    <div className="price">
      <span className={`now${onSale ? ' sale' : ''}`}>{money(onSale ? salePrice : price)}</span>
      {onSale && <span className="was">{money(price)}</span>}
    </div>
  );
}
