import type { OrderStatus, PaymentStatus } from '@/api/types';

export const ORDER_STATUS: Record<OrderStatus, { label: string; color: string }> = {
  PENDING: { label: 'Chờ xác nhận', color: 'gold' },
  PAYMENT_FAILED: { label: 'Thanh toán thất bại', color: 'red' },
  CONFIRMED: { label: 'Đã xác nhận', color: 'blue' },
  SHIPPING: { label: 'Đang giao', color: 'cyan' },
  DELIVERED: { label: 'Đã giao', color: 'green' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
};

export const PAYMENT_STATUS: Record<PaymentStatus, { label: string; color: string }> = {
  UNPAID: { label: 'Chưa thanh toán', color: 'default' },
  PAID: { label: 'Đã thanh toán', color: 'green' },
  FAILED: { label: 'Thất bại', color: 'red' },
  REFUNDED: { label: 'Đã hoàn tiền', color: 'purple' },
};

export const PAYMENT_METHOD: Record<string, string> = {
  COD: 'Thanh toán khi nhận hàng',
  CARD: 'Thẻ tín dụng / ghi nợ',
};

export const GENDER: Record<string, string> = {
  MEN: 'Nam',
  WOMEN: 'Nữ',
  UNISEX: 'Unisex',
  KIDS: 'Trẻ em',
};

export const PROVINCES = [
  'Hà Nội', 'TP. Hồ Chí Minh', 'Đà Nẵng', 'Hải Phòng', 'Cần Thơ', 'An Giang', 'Bà Rịa - Vũng Tàu', 'Bắc Ninh',
  'Bình Dương', 'Bình Định', 'Đắk Lắk', 'Đồng Nai', 'Gia Lai', 'Hà Tĩnh', 'Hải Dương', 'Khánh Hòa', 'Kiên Giang',
  'Lâm Đồng', 'Long An', 'Nam Định', 'Nghệ An', 'Ninh Bình', 'Quảng Nam', 'Quảng Ngãi', 'Quảng Ninh', 'Thái Nguyên',
  'Thanh Hóa', 'Thừa Thiên Huế', 'Tiền Giang', 'Vĩnh Phúc',
];
