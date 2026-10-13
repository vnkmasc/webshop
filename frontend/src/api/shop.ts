import { getData, postData, putData } from './client';
import type {
  CardInfo, CartLine, Category, CustomerInfo, Facets, Home, Order, OrderSummary, Page, PaymentMethod,
  ProductCard, ProductDetail, Quote, TokenResponse, User,
} from './types';

export interface ProductQuery {
  category?: string;
  brand?: string[];
  gender?: string[];
  size?: string[];
  minPrice?: number;
  maxPrice?: number;
  q?: string;
  onSale?: boolean;
  sort?: string;
  page?: number;
  pageSize?: number;
}

export const shopApi = {
  siteConfig: () => getData<{ frontendError: boolean }>('/site-config'),
  home: () => getData<Home>('/home'),
  categories: () => getData<Category[]>('/catalog/categories'),
  facets: () => getData<Facets>('/catalog/facets'),
  products: (q: ProductQuery) => getData<Page<ProductCard>>('/products', q),
  product: (slug: string) => getData<ProductDetail>(`/products/${encodeURIComponent(slug)}`),
  quote: (items: CartLine[], couponCode?: string) => postData<Quote>('/cart/quote', { items, couponCode }),
  placeOrder: (body: {
    items: CartLine[];
    couponCode?: string;
    customer: CustomerInfo;
    paymentMethod: PaymentMethod;
    card?: CardInfo;
  }) => postData<Order>('/orders', body),
  lookup: (code: string, phone: string) => getData<Order>('/orders/lookup', { code, phone }),
  login: (email: string, password: string) => postData<TokenResponse>('/auth/login', { email, password }),
  register: (body: { email: string; password: string; fullName: string; phone?: string }) =>
    postData<TokenResponse>('/auth/register', body),
  me: () => getData<User>('/auth/me'),
  updateProfile: (body: { fullName: string; phone?: string; address?: string; province?: string }) =>
    putData<User>('/account/profile', body),
  myOrders: () => getData<OrderSummary[]>('/account/orders'),
  myOrder: (code: string) => getData<Order>(`/account/orders/${code}`),
  cancelMyOrder: (code: string) => postData<Order>(`/account/orders/${code}/cancel`),
};
