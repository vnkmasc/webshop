import { Link } from 'react-router-dom';
import { StarFilled } from '@ant-design/icons';
import type { ProductCard as Card } from '@/api/types';
import { discountPercent } from '@/utils/format';
import { GENDER } from '@/utils/labels';
import { Price } from './Price';

export function ProductCard({ p }: { p: Card }) {
  const off = discountPercent(p.price, p.salePrice);
  return (
    <Link to={`/san-pham/${p.slug}`} className="product-card" data-testid="product-card">
      <div className="thumb">
        <img src={p.image} alt={p.name} loading="lazy" width={400} height={300} />
        <div className="badges">
          {off && <span className="badge">-{off}%</span>}
          {p.isNew && <span className="badge new">Mới</span>}
          {!p.inStock && <span className="badge out">Hết hàng</span>}
        </div>
      </div>
      <div className="meta">
        <div className="brand">{p.brand}</div>
        <div className="name">{p.name} - {p.color}</div>
        <Price price={p.price} salePrice={p.salePrice} />
        <div className="sub">
          <StarFilled style={{ color: '#f2b631' }} /> {p.rating.toFixed(1)} · Đã bán {p.soldCount} · {GENDER[p.gender]}
        </div>
      </div>
    </Link>
  );
}

export function ProductGrid({ items }: { items: Card[] }) {
  return (
    <div className="product-grid">
      {items.map((p) => <ProductCard key={p.id} p={p} />)}
    </div>
  );
}
