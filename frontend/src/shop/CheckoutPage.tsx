import { useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { Alert, Button, Col, Form, Input, Radio, Row, Select, Skeleton } from 'antd';
import { LockOutlined } from '@ant-design/icons';
import { useQueryClient } from '@tanstack/react-query';
import { errorCode, errorMessage } from '@/api/client';
import { shopApi } from '@/api/shop';
import type { CardInfo, CustomerInfo, PaymentMethod } from '@/api/types';
import { useAuth } from '@/store/auth';
import { useCart } from '@/store/cart';
import { PROVINCES } from '@/utils/labels';
import { sessionStorageGet, sessionStorageSet, Summary, useQuote } from './CartPage';

interface FormValues extends CustomerInfo {
  paymentMethod: PaymentMethod;
  card?: CardInfo;
}

export default function CheckoutPage() {
  const { items, dispatch } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const coupon = sessionStorageGet('sb_coupon');
  const { data: quote, isLoading, refetch } = useQuote(coupon);
  const [form] = Form.useForm<FormValues>();
  const method = Form.useWatch('paymentMethod', form);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (items.length === 0) return <Navigate to="/gio-hang" replace />;

  const submit = async (v: FormValues) => {
    setSubmitting(true);
    setError(null);
    try {
      const order = await shopApi.placeOrder({
        items: items.map((i) => ({ variantId: i.variantId, quantity: i.quantity })),
        couponCode: quote?.couponApplied ? quote.couponCode ?? undefined : undefined,
        customer: { name: v.name, phone: v.phone, email: v.email, address: v.address, province: v.province, note: v.note },
        paymentMethod: v.paymentMethod,
        card: v.paymentMethod === 'CARD' ? v.card : undefined,
      });
      dispatch({ type: 'clear' });
      sessionStorageSet('sb_coupon', '');
      queryClient.invalidateQueries({ queryKey: ['products'] });
      navigate(`/dat-hang-thanh-cong/${order.code}`, { state: { order } });
    } catch (err) {
      const code = errorCode(err);
      setError(errorMessage(err));
      if (code === 'OUT_OF_STOCK' || code === 'ITEM_UNAVAILABLE' || code === 'COUPON_INVALID') refetch();
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <h1 style={{ marginTop: 24 }}>Thanh toán</h1>
      {error && <Alert type="error" showIcon message="Chưa đặt được hàng" description={error} style={{ marginBottom: 16 }} closable onClose={() => setError(null)} />}
      <Form form={form} layout="vertical" onFinish={submit} requiredMark={false}
        initialValues={{
          paymentMethod: 'COD',
          name: user?.fullName, phone: user?.phone ?? undefined, email: user?.email,
          address: user?.address ?? undefined, province: user?.province ?? undefined,
        }}>
        <div className="two-col">
          <div>
            {!user && (
              <Alert type="info" showIcon style={{ marginBottom: 16 }}
                message={<>Đã có tài khoản? <Link to="/dang-nhap?next=/thanh-toan">Đăng nhập</Link> để theo dõi đơn dễ hơn.</>} />
            )}
            <h3>Thông tin giao hàng</h3>
            <Row gutter={12}>
              <Col xs={24} md={12}>
                <Form.Item name="name" label="Họ và tên" rules={[{ required: true, message: 'Nhập họ tên người nhận' }]}>
                  <Input autoComplete="name" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, pattern: /^0\d{9}$/, message: 'Số điện thoại gồm 10 chữ số, bắt đầu bằng 0' }]}>
                  <Input autoComplete="tel" inputMode="tel" />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="email" label="Email (nhận xác nhận đơn hàng)" rules={[{ type: 'email', message: 'Email không hợp lệ' }]}>
              <Input autoComplete="email" />
            </Form.Item>
            <Row gutter={12}>
              <Col xs={24} md={16}>
                <Form.Item name="address" label="Địa chỉ" rules={[{ required: true, message: 'Nhập địa chỉ giao hàng' }]}>
                  <Input autoComplete="street-address" placeholder="Số nhà, đường, phường/xã, quận/huyện" />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="province" label="Tỉnh / thành phố" rules={[{ required: true, message: 'Chọn tỉnh/thành' }]}>
                  <Select showSearch options={PROVINCES.map((p) => ({ value: p, label: p }))} />
                </Form.Item>
              </Col>
            </Row>
            <Form.Item name="note" label="Ghi chú">
              <Input.TextArea rows={2} maxLength={500} placeholder="Ví dụ: giao giờ hành chính" />
            </Form.Item>

            <h3>Phương thức thanh toán</h3>
            <Form.Item name="paymentMethod">
              <Radio.Group style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <Radio value="COD">Thanh toán khi nhận hàng (COD)</Radio>
                <Radio value="CARD">Thẻ tín dụng / ghi nợ (Visa, Mastercard, JCB, Napas)</Radio>
              </Radio.Group>
            </Form.Item>
            {method === 'CARD' && (
              <div style={{ background: '#faf9f7', border: '1px solid var(--line)', borderRadius: 12, padding: 16 }}>
                <Form.Item name={['card', 'number']} label="Số thẻ" rules={[{ required: true, pattern: /^[0-9 ]{13,23}$/, message: 'Số thẻ không hợp lệ' }]}>
                  <Input inputMode="numeric" autoComplete="cc-number" placeholder="4111 1111 1111 1111" prefix={<LockOutlined />} />
                </Form.Item>
                <Form.Item name={['card', 'holder']} label="Tên chủ thẻ" rules={[{ required: true, message: 'Nhập tên in trên thẻ' }]}>
                  <Input autoComplete="cc-name" placeholder="NGUYEN VAN A" />
                </Form.Item>
                <Row gutter={12}>
                  <Col span={12}>
                    <Form.Item name={['card', 'expiry']} label="Hết hạn" rules={[{ required: true, pattern: /^(0[1-9]|1[0-2])\/\d{2}$/, message: 'Dạng MM/YY' }]}>
                      <Input autoComplete="cc-exp" placeholder="12/28" />
                    </Form.Item>
                  </Col>
                  <Col span={12}>
                    <Form.Item name={['card', 'cvv']} label="CVV" rules={[{ required: true, pattern: /^\d{3,4}$/, message: '3-4 chữ số' }]}>
                      <Input.Password autoComplete="cc-csc" placeholder="123" maxLength={4} />
                    </Form.Item>
                  </Col>
                </Row>
                <small style={{ color: 'var(--muted)' }}>
                  Môi trường thử nghiệm: thẻ 4111 1111 1111 1111 thành công, 4000 0000 0000 0002 bị từ chối, 4000 0000 0000 9995 không đủ số dư.
                </small>
              </div>
            )}
          </div>
          {isLoading ? <Skeleton active /> : (
            <Summary quote={quote}>
              <div style={{ margin: '12px 0', fontSize: 13, color: 'var(--muted)' }}>{items.length} sản phẩm · <Link to="/gio-hang">Sửa giỏ hàng</Link></div>
              <Button type="primary" htmlType="submit" size="large" block loading={submitting} disabled={!quote?.valid}>
                Đặt hàng
              </Button>
            </Summary>
          )}
        </div>
      </Form>
    </>
  );
}
