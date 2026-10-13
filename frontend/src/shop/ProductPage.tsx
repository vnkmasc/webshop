import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { App, Breadcrumb, Button, InputNumber, Result, Skeleton, Space } from 'antd';
import { CarOutlined, ShoppingCartOutlined, StarFilled, SwapOutlined, SafetyOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { shopApi } from '@/api/shop';
import type { ProductDetail } from '@/api/types';
import { useCart } from '@/store/cart';
import { useSiteConfig } from '@/store/siteConfig';
import { discountPercent } from '@/utils/format';
import { GENDER } from '@/utils/labels';
import { Price } from './components/Price';
import { ProductGrid } from './components/ProductCard';

interface AnalyticsLayer {
  queue: unknown[];
}

/**
 * Pushes an add-to-cart event to the page's analytics queue. While the FRONTEND_ERROR simulation is on, the queue
 * is looked up on window where it does not exist, so this throws a real TypeError and the item is not added.
 */
function trackAddToCart(d: ProductDetail, size: string, simulateBug: boolean) {
  const layer = simulateBug
    ? (window as unknown as { shopAnalytics: AnalyticsLayer }).shopAnalytics
    : { queue: [] as unknown[] };
  layer.queue.push({ event: 'add_to_cart', productId: d.product.id, size });
}

export default function ProductPage() {
  const { slug = '' } = useParams();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { dispatch } = useCart();
  const site = useSiteConfig();
  const [size, setSize] = useState<string | null>(null);
  const [qty, setQty] = useState(1);
  const { data, isLoading, isError } = useQuery({ queryKey: ['product', slug], queryFn: () => shopApi.product(slug) });

  if (isLoading) return <Skeleton active paragraph={{ rows: 12 }} style={{ marginTop: 32 }} />;
  if (isError || !data) {
    return <Result status="404" title="Không tìm thấy sản phẩm" extra={<Link to="/san-pham"><Button>Xem sản phẩm khác</Button></Link>} />;
  }
  const p = data.product;
  const variant = data.variants.find((v) => v.size === size);
  const off = discountPercent(p.price, p.salePrice);

  const add = (goToCart: boolean) => {
    if (!variant) {
      message.warning('Vui lòng chọn size');
      return;
    }
    trackAddToCart(data, variant.size, site.frontendError);
    dispatch({
      type: 'add',
      item: {
        variantId: variant.id, quantity: qty, slug: p.slug, name: `${p.name} - ${p.color}`, image: p.image,
        size: variant.size, price: p.salePrice ?? p.price,
      },
    });
    if (goToCart) navigate('/gio-hang');
    else message.success(`Đã thêm ${p.name} size ${variant.size} vào giỏ`);
  };

  return (
    <>
      <Breadcrumb style={{ marginTop: 16 }} items={[
        { title: <Link to="/">Trang chủ</Link> },
        { title: <Link to={`/danh-muc/${p.categorySlug}`}>{p.categoryName}</Link> },
        { title: p.name },
      ]} />
      <div className="pdp">
        <div className="pdp-img"><img src={p.image} alt={p.name} width={800} height={600} /></div>
        <div>
          <div className="brand" style={{ color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: 0.6 }}>{p.brand}</div>
          <h1>{p.name} - {p.color}</h1>
          <Space style={{ color: 'var(--muted)', marginBottom: 12, display: 'flex' }}>
            <span><StarFilled style={{ color: '#f2b631' }} /> {p.rating.toFixed(1)}</span>
            <span>· Đã bán {p.soldCount}</span>
            <span>· {GENDER[p.gender]}</span>
          </Space>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <Price price={p.price} salePrice={p.salePrice} />
            {off && <span className="badge">-{off}%</span>}
          </div>

          <h4 style={{ margin: '20px 0 8px' }}>Chọn size (EU)</h4>
          <div className="size-grid" data-testid="sizes">
            {data.variants.map((v) => (
              <div key={v.id}
                className={`size-chip${size === v.size ? ' on' : ''}${v.stock === 0 ? ' disabled' : ''}`}
                onClick={() => v.stock > 0 && setSize(v.size)}
                title={v.stock === 0 ? 'Hết hàng' : `Còn ${v.stock} đôi`}>
                {v.size}
              </div>
            ))}
          </div>
          {variant && variant.stock <= 3 && <div style={{ color: 'var(--brand)', marginTop: 8 }}>Chỉ còn {variant.stock} đôi size {variant.size}</div>}

          <Space style={{ marginTop: 20 }} wrap>
            <InputNumber min={1} max={Math.min(10, variant?.stock ?? 10)} value={qty} onChange={(v) => setQty(v ?? 1)} size="large" />
            <Button size="large" icon={<ShoppingCartOutlined />} onClick={() => add(false)} disabled={!p.inStock}>
              Thêm vào giỏ
            </Button>
            <Button size="large" type="primary" onClick={() => add(true)} disabled={!p.inStock}>Mua ngay</Button>
          </Space>
          {!p.inStock && <div style={{ marginTop: 8, color: 'var(--muted)' }}>Mẫu này tạm hết hàng.</div>}

          <div className="pdp-perks">
            <span><CarOutlined /> Giao 1-3 ngày, miễn phí cho đơn từ 1.000.000₫</span>
            <span><SwapOutlined /> Đổi size miễn phí trong 7 ngày</span>
            <span><SafetyOutlined /> Bảo hành keo đế 6 tháng</span>
          </div>
          <h4 style={{ marginTop: 24 }}>Mô tả</h4>
          <p style={{ lineHeight: 1.7 }}>{data.description}</p>
        </div>
      </div>
      {data.related.length > 0 && (
        <section className="section">
          <div className="section-head"><h2>Có thể bạn cũng thích</h2></div>
          <ProductGrid items={data.related} />
        </section>
      )}
    </>
  );
}
