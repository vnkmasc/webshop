export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message: string;
}

export interface ApiError {
  success: false;
  errorCode: string;
  message: string;
  details: { field: string; message: string }[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  address: string | null;
  province: string | null;
  role: 'CUSTOMER' | 'ADMIN';
}

export interface TokenResponse {
  accessToken: string;
  expiresIn: number;
  user: User;
}

export interface ProductCard {
  id: number;
  slug: string;
  name: string;
  brand: string;
  categorySlug: string;
  categoryName: string;
  gender: 'MEN' | 'WOMEN' | 'UNISEX' | 'KIDS';
  color: string;
  price: number;
  salePrice: number | null;
  image: string;
  rating: number;
  soldCount: number;
  isNew: boolean;
  sizes: string[];
  inStock: boolean;
}

export interface Variant {
  id: number;
  size: string;
  sku: string;
  stock: number;
}

export interface ProductDetail {
  product: ProductCard;
  description: string;
  variants: Variant[];
  related: ProductCard[];
}

export interface Category {
  id: number;
  slug: string;
  name: string;
  description: string;
  productCount: number;
}

export interface Facets {
  brands: string[];
  sizes: string[];
  colors: string[];
  minPrice: number;
  maxPrice: number;
}

export interface Home {
  categories: Category[];
  featured: ProductCard[];
  bestsellers: ProductCard[];
  newest: ProductCard[];
  onSale: ProductCard[];
}

export interface CartLine {
  variantId: number;
  quantity: number;
}

export interface QuoteLine {
  variantId: number;
  productId: number;
  slug: string | null;
  name: string;
  brand: string;
  image: string;
  size: string;
  unitPrice: number;
  originalPrice: number;
  quantity: number;
  stock: number;
  available: boolean;
  lineTotal: number;
  message: string | null;
}

export interface Quote {
  lines: QuoteLine[];
  subtotal: number;
  discount: number;
  shippingFee: number;
  total: number;
  couponCode: string | null;
  couponApplied: boolean;
  couponMessage: string | null;
  valid: boolean;
}

export type OrderStatus = 'PENDING' | 'PAYMENT_FAILED' | 'CONFIRMED' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED';
export type PaymentMethod = 'COD' | 'CARD';
export type PaymentStatus = 'UNPAID' | 'PAID' | 'FAILED' | 'REFUNDED';

export interface Order {
  code: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  paymentRef: string | null;
  paymentError: string | null;
  customerName: string;
  phone: string;
  email: string | null;
  address: string;
  province: string;
  note: string | null;
  subtotal: number;
  discount: number;
  shippingFee: number;
  total: number;
  couponCode: string | null;
  userId: number | null;
  createdAt: string;
  updatedAt: string;
  items: {
    productId: number;
    variantId: number;
    productName: string;
    size: string;
    image: string;
    unitPrice: number;
    quantity: number;
    lineTotal: number;
  }[];
  history: { fromStatus: string | null; toStatus: string; note: string | null; changedBy: string | null; changedAt: string }[];
  /** Admin views only. */
  riskScore?: number | null;
  riskLevel?: RiskLevel | null;
  riskReasons?: string[] | null;
}

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';

export interface OrderSummary {
  code: string;
  status: OrderStatus;
  paymentMethod: PaymentMethod;
  paymentStatus: PaymentStatus;
  total: number;
  itemCount: number;
  firstImage: string | null;
  createdAt: string;
}

export interface CustomerInfo {
  name: string;
  phone: string;
  email?: string;
  address: string;
  province: string;
  note?: string;
}

export interface CardInfo {
  number: string;
  holder: string;
  expiry: string;
  cvv: string;
}
