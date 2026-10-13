import { useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Badge, Button, Drawer, Dropdown, Input } from 'antd';
import {
  MenuOutlined, SearchOutlined, ShoppingOutlined, UserOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { shopApi } from '@/api/shop';
import { useAuth } from '@/store/auth';
import { useCart } from '@/store/cart';
import { LogoMark } from './components/Logo';

export function ShopLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const onSalePage = location.pathname === '/san-pham' && new URLSearchParams(location.search).get('onSale') === 'true';
  const { user, logout } = useAuth();
  const { count } = useCart();
  const [menuOpen, setMenuOpen] = useState(false);
  const { data: categories = [] } = useQuery({ queryKey: ['categories'], queryFn: shopApi.categories, staleTime: 300_000 });

  const search = (q: string) => {
    if (q.trim()) navigate(`/san-pham?q=${encodeURIComponent(q.trim())}`);
  };

  const accountItems = user
    ? [
        { key: 'account', label: <Link to="/tai-khoan">Tài khoản & đơn hàng</Link> },
        ...(user.role === 'ADMIN' ? [{ key: 'admin', label: <Link to="/admin">Trang quản trị</Link> }] : []),
        { type: 'divider' as const },
        { key: 'logout', label: 'Đăng xuất', onClick: () => { logout(); navigate('/'); } },
      ]
    : [
        { key: 'login', label: <Link to="/dang-nhap">Đăng nhập</Link> },
        { key: 'register', label: <Link to="/dang-ky">Tạo tài khoản</Link> },
        { key: 'track', label: <Link to="/tra-cuu-don-hang">Tra cứu đơn hàng</Link> },
      ];

  const catLinks = (
    <>
      <Link to="/san-pham" className={location.pathname === '/san-pham' && !onSalePage ? 'active' : ''}>Tất cả</Link>
      {categories.map((c) => <NavLink key={c.slug} to={`/danh-muc/${c.slug}`}>{c.name}</NavLink>)}
      <Link to="/san-pham?onSale=true" className={`sale${onSalePage ? ' active' : ''}`}>Khuyến mãi</Link>
    </>
  );

  return (
    <>
      <div className="promo-bar">Miễn phí giao hàng cho đơn từ 1.000.000₫ · Đổi size miễn phí trong 7 ngày</div>
      <header className="shop-header">
        <div className="container">
          <div className="row">
            <Button className="mobile-only" type="text" icon={<MenuOutlined />} onClick={() => setMenuOpen(true)}
              aria-label="Danh mục" />
            <Link to="/" className="logo"><LogoMark />Sải <span>Bước</span></Link>
            <div className="header-search">
              <Input.Search placeholder="Tìm giày theo tên, hãng, màu..." allowClear enterButton={<SearchOutlined />}
                onSearch={search} size="large" />
            </div>
            <div className="header-actions">
              <Dropdown menu={{ items: accountItems }} placement="bottomRight" trigger={['click']}>
                <Button type="text" size="large" icon={<UserOutlined />}>
                  <span className="hide-sm">{user ? user.fullName.split(' ').slice(-1)[0] : 'Tài khoản'}</span>
                </Button>
              </Dropdown>
              <Link to="/gio-hang" aria-label="Giỏ hàng">
                <Badge count={count} size="small" color="#e4572e">
                  <Button type="text" size="large" icon={<ShoppingOutlined style={{ fontSize: 22 }} />} />
                </Badge>
              </Link>
            </div>
          </div>
          <nav className="cat-nav">{catLinks}</nav>
        </div>
      </header>
      <Drawer open={menuOpen} onClose={() => setMenuOpen(false)} placement="left" title="Danh mục" width={280}>
        <div className="cat-nav" style={{ flexDirection: 'column' }} onClick={() => setMenuOpen(false)}>{catLinks}</div>
      </Drawer>
      <main className="container">
        <Outlet />
      </main>
      <footer className="shop-footer">
        <div className="container">
          <div className="cols">
            <div>
              <h4>Sải Bước</h4>
              <p>Giày chạy bộ, sneaker, giày tây và phụ kiện chính hãng. Giao nhanh 63 tỉnh thành, thanh toán khi nhận hàng.</p>
              <p>Hotline: 1900 6868 · Email: hotro@saibuoc.vn</p>
            </div>
            <div>
              <h4>Mua sắm</h4>
              {categories.slice(0, 5).map((c) => <Link key={c.slug} to={`/danh-muc/${c.slug}`}>{c.name}</Link>)}
            </div>
            <div>
              <h4>Hỗ trợ</h4>
              <Link to="/tra-cuu-don-hang">Tra cứu đơn hàng</Link>
              <Link to="/tai-khoan">Tài khoản của tôi</Link>
              <a href="#doi-tra">Chính sách đổi trả</a>
              <a href="#bao-hanh">Bảo hành</a>
            </div>
            <div>
              <h4>Cửa hàng</h4>
              <span>12 Lê Lợi, Quận 1, TP. HCM</span><br />
              <span>45 Tràng Tiền, Hoàn Kiếm, Hà Nội</span>
            </div>
          </div>
          <div className="copy">© {new Date().getFullYear()} Sải Bước. Trang web minh họa, sản phẩm và thương hiệu là giả lập.</div>
        </div>
      </footer>
    </>
  );
}
