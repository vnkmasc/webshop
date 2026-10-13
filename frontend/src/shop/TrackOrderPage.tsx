import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Alert, Button, Card, Form, Input } from 'antd';
import { errorMessage } from '@/api/client';
import { shopApi } from '@/api/shop';
import type { Order } from '@/api/types';
import { OrderView } from './components/OrderView';

export default function TrackOrderPage() {
  const [params] = useSearchParams();
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async (v: { code: string; phone: string }) => {
    setLoading(true);
    setError(null);
    try {
      setOrder(await shopApi.lookup(v.code, v.phone));
    } catch (err) {
      setOrder(null);
      setError(errorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 900, margin: '32px auto' }}>
      <Card title="Tra cứu đơn hàng">
        <Form layout="inline" onFinish={submit} initialValues={{ code: params.get('code') ?? '' }} style={{ rowGap: 12 }}>
          <Form.Item name="code" rules={[{ required: true, message: 'Nhập mã đơn' }]}><Input placeholder="Mã đơn (VD: SB100123)" /></Form.Item>
          <Form.Item name="phone" rules={[{ required: true, message: 'Nhập số điện thoại' }]}><Input placeholder="Số điện thoại đặt hàng" /></Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>Tra cứu</Button>
        </Form>
        {error && <Alert type="warning" showIcon message={error} style={{ marginTop: 16 }} />}
      </Card>
      {order && <Card style={{ marginTop: 16 }}><OrderView order={order} /></Card>}
    </div>
  );
}
