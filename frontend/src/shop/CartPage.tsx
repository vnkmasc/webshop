import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Empty, Input, InputNumber, Skeleton, Typography } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { shopApi } from '@/api/shop';
import { useCart } from '@/store/cart';
import { money } from '@/utils/format';

export function useQuote(coupon?: string) {
  const { items } = useCart();
  const lines = items.map((i) => ({ variantId: i.variantId, quantity: i.quantity }));
  return useQuery({
    queryKey: ['quote', lines, coupon ?? ''],
    queryFn: () => shopApi.quote(lines, coupon || undefined),
    enabled: lines.length > 0,
  });
}

export function Summary({ quote, children }: { quote?: { subtotal: number; discount: number; shippingFee: number; total: number; couponCode: string | null; couponApplied: boolean }; children?: React.ReactNode }) {
  return (
    <div className="summary">
      <h3 style={{ marginTop: 0 }}>Tóm tắt đơn hàng</h3>
      <div className="line"><span>Tạm tính</span><span>{money(quote?.subtotal ?? 0)}</span></div>
      {quote && quote.discount > 0 && (
        <div className="line" style={{ color: 'var(--brand)' }}>
          <span>Giảm giá {quote.couponApplied ? `(${quote.couponCode})` : ''}</span><span>-{money(quote.discount)}</span>
        </div>
      )}
      <div className="line"><span>Phí giao hàng</span><span>{quote ? (quote.shippingFee === 0 ? 'Miễn phí' : money(quote.shippingFee)) : '-'}</span></div>
      <div className="line total"><span>Tổng cộng</span><span>{money(quote?.total ?? 0)}</span></div>
      {children}
    </div>
  );
}

export default function CartPage() {
  const { items, dispatch } = useCart();
  const navigate = useNavigate();
  const [couponInput, setCouponInput] = useState(() => sessionStorageGet('sb_coupon'));
  const [coupon, setCoupon] = useState(couponInput);
  const { data: quote, isLoading, isError } = useQuote(coupon);

  if (items.length === 0) {
    return (
      <Empty style={{ margin: '80px 0' }} description="Giỏ hàng của bạn đang trống">
        <Link to="/san-pham"><Button type="primary">Tiếp tục mua sắm</Button></Link>
      </Empty>
    );
  }

  const applyCoupon = (value: string) => {
    setCoupon(value.trim());
    sessionStorageSet('sb_coupon', value.trim());
  };

  return (
    <>
      <h1 style={{ marginTop: 24 }}>Giỏ hàng</h1>
      <div className="two-col">
        <div>
          {isError && <Alert type="error" showIcon message="Không tải được giá mới nhất, vui lòng thử lại" />}
          {items.map((i) => {
            const line = quote?.lines.find((l) => l.variantId === i.variantId);
            return (
              <div className="cart-line" key={i.variantId} data-testid="cart-line">
                <Link to={`/san-pham/${i.slug}`}><img src={i.image} alt={i.name} width={110} height={82} /></Link>
                <div>
                  <Link to={`/san-pham/${i.slug}`}><b>{i.name}</b></Link>
                  <div style={{ color: 'var(--muted)' }}>Size {i.size} · {money(line?.unitPrice ?? i.price)}</div>
                  {line?.message && <Typography.Text type="danger">{line.message}</Typography.Text>}
                  <div style={{ marginTop: 8, display: 'flex', gap: 8, alignItems: 'center' }}>
                    <InputNumber min={1} max={10} value={i.quantity}
                      onChange={(v) => dispatch({ type: 'setQty', variantId: i.variantId, quantity: v ?? 1 })} />
                    <Button type="text" icon={<DeleteOutlined />} onClick={() => dispatch({ type: 'remove', variantId: i.variantId })}>Xóa</Button>
                  </div>
                </div>
                <div className="cart-line-total" style={{ fontWeight: 700 }}>{money(line?.lineTotal ?? i.price * i.quantity)}</div>
              </div>
            );
          })}
        </div>
        {isLoading ? <Skeleton active /> : (
          <Summary quote={quote}>
            <Input.Search placeholder="Mã giảm giá" enterButton="Áp dụng" value={couponInput}
              onChange={(e) => setCouponInput(e.target.value.toUpperCase())} onSearch={applyCoupon} style={{ marginTop: 12 }} />
            {quote?.couponMessage && (
              <div style={{ marginTop: 6, fontSize: 13, color: quote.couponApplied ? '#2f7d4a' : '#c62f2f' }}>{quote.couponMessage}</div>
            )}
            <Button type="primary" size="large" block style={{ marginTop: 16 }} disabled={!quote?.valid}
              onClick={() => navigate('/thanh-toan')}>Tiến hành thanh toán</Button>
            {quote && !quote.valid && <div style={{ marginTop: 8, fontSize: 13 }}>Vui lòng điều chỉnh các sản phẩm không đủ hàng.</div>}
            <div style={{ marginTop: 12, fontSize: 13, color: 'var(--muted)' }}>Thử mã <b>CHAOBAN</b> giảm 10% cho đơn từ 500.000₫</div>
          </Summary>
        )}
      </div>
    </>
  );
}

export function sessionStorageGet(key: string): string {
  try {
    return sessionStorage.getItem(key) ?? '';
  } catch {
    return '';
  }
}

export function sessionStorageSet(key: string, value: string) {
  try {
    if (value) sessionStorage.setItem(key, value);
    else sessionStorage.removeItem(key);
  } catch {
    // ignore
  }
}
