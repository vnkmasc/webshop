import { Link } from 'react-router-dom';
import { App, Button, Card, Col, Empty, Form, Input, List, Row, Select, Skeleton } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { errorMessage } from '@/api/client';
import { shopApi } from '@/api/shop';
import { useAuth } from '@/store/auth';
import { dateTime, money } from '@/utils/format';
import { PROVINCES } from '@/utils/labels';
import { StatusTags } from './components/OrderView';

export default function AccountPage() {
  const { user, updateUser } = useAuth();
  const { message } = App.useApp();
  const { data: orders, isLoading } = useQuery({ queryKey: ['my-orders'], queryFn: shopApi.myOrders });
  const save = useMutation({
    mutationFn: shopApi.updateProfile,
    onSuccess: (u) => { updateUser(u); message.success('Đã lưu thông tin'); },
    onError: (e) => message.error(errorMessage(e)),
  });

  return (
    <Row gutter={24} style={{ margin: '24px 0' }}>
      <Col xs={24} lg={15}>
        <Card title="Đơn hàng của tôi">
          {isLoading ? <Skeleton active /> : orders && orders.length > 0 ? (
            <List dataSource={orders} renderItem={(o) => (
              <List.Item actions={[<Link key="v" to={`/tai-khoan/don-hang/${o.code}`}>Chi tiết</Link>]}>
                <List.Item.Meta
                  avatar={o.firstImage ? <img src={o.firstImage} alt="" className="thumb-sm" /> : null}
                  title={<Link to={`/tai-khoan/don-hang/${o.code}`}>{o.code}</Link>}
                  description={<>{dateTime(o.createdAt)} · {o.itemCount} sản phẩm · <b>{money(o.total)}</b></>} />
                <StatusTags order={o} />
              </List.Item>
            )} />
          ) : <Empty description="Bạn chưa có đơn hàng nào"><Link to="/san-pham"><Button type="primary">Mua sắm ngay</Button></Link></Empty>}
        </Card>
      </Col>
      <Col xs={24} lg={9}>
        <Card title="Thông tin tài khoản">
          <Form layout="vertical" initialValues={{ ...user, phone: user?.phone ?? undefined }} onFinish={(v) => save.mutate(v)}>
            <Form.Item label="Email"><Input value={user?.email} disabled /></Form.Item>
            <Form.Item name="fullName" label="Họ và tên" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="phone" label="Số điện thoại" rules={[{ pattern: /^0\d{9}$/, message: '10 chữ số, bắt đầu bằng 0' }]}><Input /></Form.Item>
            <Form.Item name="address" label="Địa chỉ mặc định"><Input /></Form.Item>
            <Form.Item name="province" label="Tỉnh / thành phố">
              <Select allowClear showSearch options={PROVINCES.map((p) => ({ value: p, label: p }))} />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={save.isPending}>Lưu</Button>
          </Form>
        </Card>
      </Col>
    </Row>
  );
}
