import { lazy, Suspense, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { ShopLayout } from '@/shop/ShopLayout';
import { HomePage } from '@/shop/HomePage';
import { useAuth } from '@/store/auth';

const ProductListPage = lazy(() => import('@/shop/ProductListPage'));
const ProductPage = lazy(() => import('@/shop/ProductPage'));
const CartPage = lazy(() => import('@/shop/CartPage'));
const CheckoutPage = lazy(() => import('@/shop/CheckoutPage'));
const OrderSuccessPage = lazy(() => import('@/shop/OrderSuccessPage'));
const LoginPage = lazy(() => import('@/shop/LoginPage'));
const RegisterPage = lazy(() => import('@/shop/RegisterPage'));
const AccountPage = lazy(() => import('@/shop/AccountPage'));
const MyOrderPage = lazy(() => import('@/shop/MyOrderPage'));
const TrackOrderPage = lazy(() => import('@/shop/TrackOrderPage'));
const NotFoundPage = lazy(() => import('@/shop/NotFoundPage'));

const AdminLayout = lazy(() => import('@/admin/AdminLayout'));
const AdminLoginPage = lazy(() => import('@/admin/AdminLoginPage'));
const DashboardPage = lazy(() => import('@/admin/DashboardPage'));
const AdminProductsPage = lazy(() => import('@/admin/ProductsPage'));
const AdminOrdersPage = lazy(() => import('@/admin/OrdersPage'));
const AdminCustomersPage = lazy(() => import('@/admin/CustomersPage'));
const AdminCouponsPage = lazy(() => import('@/admin/CouponsPage'));
const GatewayPage = lazy(() => import('@/admin/GatewayPage'));
const SimulationPage = lazy(() => import('@/admin/SimulationPage'));

function Loading() {
  return <div className="page-loading"><Spin size="large" /></div>;
}

function RequireUser({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to={`/dang-nhap?next=${encodeURIComponent(location.pathname)}`} replace />;
  return <>{children}</>;
}

function RequireAdmin({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  if (!user || user.role !== 'ADMIN') return <Navigate to="/admin/dang-nhap" replace />;
  return <>{children}</>;
}

export function AppRouter() {
  return (
    <BrowserRouter>
      <Suspense fallback={<Loading />}>
        <Routes>
          <Route element={<ShopLayout />}>
            <Route index element={<HomePage />} />
            <Route path="san-pham" element={<ProductListPage />} />
            <Route path="danh-muc/:category" element={<ProductListPage />} />
            <Route path="san-pham/:slug" element={<ProductPage />} />
            <Route path="gio-hang" element={<CartPage />} />
            <Route path="thanh-toan" element={<CheckoutPage />} />
            <Route path="dat-hang-thanh-cong/:code" element={<OrderSuccessPage />} />
            <Route path="dang-nhap" element={<LoginPage />} />
            <Route path="dang-ky" element={<RegisterPage />} />
            <Route path="tra-cuu-don-hang" element={<TrackOrderPage />} />
            <Route path="tai-khoan" element={<RequireUser><AccountPage /></RequireUser>} />
            <Route path="tai-khoan/don-hang/:code" element={<RequireUser><MyOrderPage /></RequireUser>} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
          <Route path="admin/dang-nhap" element={<AdminLoginPage />} />
          <Route path="admin" element={<RequireAdmin><AdminLayout /></RequireAdmin>}>
            <Route index element={<DashboardPage />} />
            <Route path="san-pham" element={<AdminProductsPage />} />
            <Route path="don-hang" element={<AdminOrdersPage />} />
            <Route path="khach-hang" element={<AdminCustomersPage />} />
            <Route path="ma-giam-gia" element={<AdminCouponsPage />} />
            <Route path="cong-thanh-toan" element={<GatewayPage />} />
            <Route path="mo-phong-su-co" element={<SimulationPage />} />
          </Route>
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}
