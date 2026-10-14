import { getData, postData, putData } from './client';
import type { Order, OrderStatus, Page } from './types';

export interface Dashboard {
  days: number;
  kpis: {
    revenue: number; orders: number; avgOrderValue: number; itemsSold: number; newCustomers: number;
    failedPayments: number; cardAttempts: number; revenuePrevious: number; ordersPrevious: number;
  };
  series: { day: string; revenue: number; orders: number }[];
  statuses: { status: OrderStatus; count: number }[];
  topProducts: { productId: number; name: string; image: string; quantity: number; revenue: number }[];
  categories: { category: string; revenue: number }[];
  lowStock: { productId: number; productName: string; size: string; stock: number }[];
  recent: { code: string; customerName: string; total: number; status: OrderStatus; paymentMethod: string; createdAt: string }[];
}

export interface ProductRow {
  id: number; slug: string; name: string; brand: string; category: string; gender: string; color: string;
  price: number; salePrice: number | null; image: string; active: boolean; featured: boolean; soldCount: number;
  totalStock: number; sizesOutOfStock: number;
}

export interface AdminProduct {
  id: number; slug: string; name: string; brand: string; categoryId: number; gender: string; color: string;
  description: string; price: number; salePrice: number | null; image: string; active: boolean; featured: boolean;
  rating: number; soldCount: number; createdAt: string;
  variants: { id: number | null; size: string; sku: string; stock: number }[];
}

export type ProductInput = Omit<AdminProduct, 'id' | 'slug' | 'soldCount' | 'createdAt' | 'variants'>;

export interface OrderRow {
  code: string; customerName: string; phone: string; province: string; total: number; status: OrderStatus;
  paymentMethod: string; paymentStatus: string; itemCount: number; couponCode: string | null; createdAt: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | null; riskScore: number | null;
}

export interface CustomerRow {
  id: number; email: string; fullName: string; phone: string | null; province: string | null; active: boolean;
  createdAt: string; lastLoginAt: string | null; orders: number; spent: number;
}

export interface Coupon {
  id: number; code: string; description: string; type: 'PERCENT' | 'FIXED'; value: number; minOrder: number;
  maxDiscount: number | null; startsOn: string; endsOn: string; usageLimit: number | null; usedCount: number;
  active: boolean;
}

export type CouponInput = Omit<Coupon, 'id' | 'usedCount'>;

export interface GatewaySettings {
  baseLatencyMs: number; extraLatencyMs: number; errorRatePercent: number; declineRatePercent: number;
  outage: boolean; lastHour?: { approved: number; declined: number; avg_ms: number };
}

export interface ScenarioState {
  scenario: string; active: boolean; params: Record<string, unknown>; expiresAt: string | null;
}

export const adminApi = {
  dashboard: (days: number) => getData<Dashboard>('/admin/dashboard', { days }),
  products: (q: object) => getData<Page<ProductRow>>('/admin/products', q),
  product: (id: number) => getData<AdminProduct>(`/admin/products/${id}`),
  createProduct: (body: ProductInput) => postData<AdminProduct>('/admin/products', body),
  updateProduct: (id: number, body: ProductInput) => putData<AdminProduct>(`/admin/products/${id}`, body),
  setStock: (id: number, lines: { size: string; stock: number }[]) => putData<AdminProduct>(`/admin/products/${id}/stock`, { lines }),
  orders: (q: object) => getData<Page<OrderRow>>('/admin/orders', q),
  statusCounts: () => getData<{ status: OrderStatus; count: number }[]>('/admin/orders/status-counts'),
  order: (code: string) => getData<{ order: Order; nextStatuses: OrderStatus[] }>(`/admin/orders/${code}`),
  changeStatus: (code: string, status: OrderStatus, note?: string) =>
    postData<{ order: Order; nextStatuses: OrderStatus[] }>(`/admin/orders/${code}/status`, { status, note }),
  customers: (q: object) => getData<Page<CustomerRow>>('/admin/customers', q),
  coupons: () => getData<Coupon[]>('/admin/coupons'),
  createCoupon: (body: CouponInput) => postData<Coupon>('/admin/coupons', body),
  updateCoupon: (id: number, body: CouponInput) => putData<Coupon>(`/admin/coupons/${id}`, body),
  gateway: () => getData<GatewaySettings>('/admin/payment-gateway'),
  updateGateway: (body: GatewaySettings) => putData<GatewaySettings>('/admin/payment-gateway', body),
  simulation: () => getData<{ scenarios: ScenarioState[]; retainedMb: number; taggedSeries: number }>('/admin/simulation'),
  setScenario: (scenario: string, body: { active: boolean; params?: Record<string, unknown>; durationMinutes?: number }) =>
    putData<ScenarioState>(`/admin/simulation/${scenario}`, body),
  releaseMemory: () => postData<number>('/admin/simulation/release-memory'),
};
