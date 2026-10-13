import { useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Button, Drawer, Grid, Layout, Menu, Space } from 'antd';
import {
  AppstoreOutlined, BugOutlined, CreditCardOutlined, DashboardOutlined, GiftOutlined, LogoutOutlined,
  MenuOutlined, ShopOutlined, ShoppingCartOutlined, TeamOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/store/auth';
import { LogoMark } from '@/shop/components/Logo';

const ITEMS = [
  { key: '/admin', icon: <DashboardOutlined />, label: 'Tổng quan' },
  { key: '/admin/don-hang', icon: <ShoppingCartOutlined />, label: 'Đơn hàng' },
  { key: '/admin/san-pham', icon: <AppstoreOutlined />, label: 'Sản phẩm & tồn kho' },
  { key: '/admin/khach-hang', icon: <TeamOutlined />, label: 'Khách hàng' },
  { key: '/admin/ma-giam-gia', icon: <GiftOutlined />, label: 'Mã giảm giá' },
  { key: '/admin/cong-thanh-toan', icon: <CreditCardOutlined />, label: 'Cổng thanh toán' },
  { key: '/admin/mo-phong-su-co', icon: <BugOutlined />, label: 'Mô phỏng sự cố' },
];

export default function AdminLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const screens = Grid.useBreakpoint();
  const [open, setOpen] = useState(false);
  const selected = ITEMS.map((i) => i.key).filter((k) => pathname === k || pathname.startsWith(`${k}/`)).pop() ?? '/admin';

  const menu = (
    <Menu theme="dark" mode="inline" selectedKeys={[selected]} items={ITEMS}
      onClick={(e) => { navigate(e.key); setOpen(false); }} />
  );

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {screens.lg ? (
        <Layout.Sider width={230} style={{ position: 'sticky', top: 0, height: '100vh' }}>
          <div className="admin-logo"><LogoMark className="logo-mark" />Sải <span>Bước</span></div>
          {menu}
        </Layout.Sider>
      ) : (
        <Drawer open={open} onClose={() => setOpen(false)} placement="left" width={240}
          styles={{ body: { padding: 0, background: '#001529' }, header: { display: 'none' } }}>
          <div className="admin-logo"><LogoMark className="logo-mark" />Sải <span>Bước</span></div>
          {menu}
        </Drawer>
      )}
      <Layout>
        <Layout.Header style={{ background: '#fff', padding: '0 16px', display: 'flex', alignItems: 'center', gap: 12, borderBottom: '1px solid #eee' }}>
          {!screens.lg && <Button icon={<MenuOutlined />} onClick={() => setOpen(true)} aria-label="Menu" />}
          <b style={{ fontSize: 16 }}>{ITEMS.find((i) => i.key === selected)?.label}</b>
          <Space style={{ marginLeft: 'auto' }}>
            <Link to="/"><Button icon={<ShopOutlined />}>{screens.md ? 'Xem cửa hàng' : null}</Button></Link>
            {screens.md && <span>{user?.fullName}</span>}
            <Button icon={<LogoutOutlined />} onClick={() => { logout(); navigate('/admin/dang-nhap'); }} aria-label="Đăng xuất" />
          </Space>
        </Layout.Header>
        <Layout.Content style={{ padding: screens.md ? 24 : 12, background: '#f5f5f4' }}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
