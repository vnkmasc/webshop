import { Link } from 'react-router-dom';
import { Button, Skeleton } from 'antd';
import { CarOutlined, SafetyCertificateOutlined, SwapOutlined, CreditCardOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { shopApi } from '@/api/shop';
import { ProductGrid } from './components/ProductCard';

function Section({ title, link, children }: { title: string; link?: string; children: React.ReactNode }) {
  return (
    <section className="section">
      <div className="section-head">
        <h2>{title}</h2>
        {link && <Link to={link}>Xem tất cả →</Link>}
      </div>
      {children}
    </section>
  );
}

export function HomePage() {
  const { data, isLoading } = useQuery({ queryKey: ['home'], queryFn: shopApi.home });

  return (
    <>
      <div className="hero">
        <div className="hero-text">
          <h1>Mỗi bước chân, một câu chuyện.</h1>
          <p>Hơn 90 mẫu giày chạy bộ, sneaker, boot và giày tây. Đổi size miễn phí, giao nhanh toàn quốc.</p>
          <Link to="/danh-muc/giay-chay-bo"><Button type="primary" size="large">Mua giày chạy bộ</Button></Link>{' '}
          <Link to="/san-pham?onSale=true"><Button size="large">Đang giảm giá</Button></Link>
        </div>
        <div className="hero-img"><img src="/images/shoes/running-cam.svg" alt="" width={400} height={300} /></div>
      </div>

      <div className="perks">
        <div className="perk"><CarOutlined /><div><b>Giao nhanh</b><br /><small>Miễn phí từ 1.000.000₫</small></div></div>
        <div className="perk"><SwapOutlined /><div><b>Đổi size 7 ngày</b><br /><small>Không cần lý do</small></div></div>
        <div className="perk"><SafetyCertificateOutlined /><div><b>Chính hãng</b><br /><small>Bảo hành keo đế 6 tháng</small></div></div>
        <div className="perk"><CreditCardOutlined /><div><b>Thanh toán linh hoạt</b><br /><small>Thẻ hoặc khi nhận hàng</small></div></div>
      </div>

      {isLoading || !data ? (
        <Skeleton active paragraph={{ rows: 10 }} style={{ marginTop: 40 }} />
      ) : (
        <>
          <Section title="Danh mục">
            <div className="cat-grid">
              {data.categories.map((c) => (
                <Link key={c.slug} to={`/danh-muc/${c.slug}`} className="cat-tile">
                  <b>{c.name}</b>
                  <small>{c.description}</small>
                  <small>{c.productCount} mẫu</small>
                </Link>
              ))}
            </div>
          </Section>
          <Section title="Bán chạy nhất" link="/san-pham?sort=bestseller"><ProductGrid items={data.bestsellers} /></Section>
          <Section title="Đang giảm giá" link="/san-pham?onSale=true"><ProductGrid items={data.onSale} /></Section>
          <Section title="Hàng mới về" link="/san-pham?sort=newest"><ProductGrid items={data.newest} /></Section>
          {data.featured.length > 0 && (
            <Section title="Gợi ý cho bạn"><ProductGrid items={data.featured} /></Section>
          )}
        </>
      )}
    </>
  );
}
