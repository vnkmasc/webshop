import { Link, useLocation, useParams } from 'react-router-dom';
import { Button, Result } from 'antd';
import type { Order } from '@/api/types';
import { useAuth } from '@/store/auth';
import { OrderView } from './components/OrderView';

export default function OrderSuccessPage() {
  const { code } = useParams();
  const { user } = useAuth();
  const order = (useLocation().state as { order?: Order } | null)?.order;
  return (
    <div style={{ maxWidth: 860, margin: '24px auto' }}>
      <Result status="success" title="Đặt hàng thành công!"
        subTitle={<>Mã đơn hàng <b>{code}</b>. Chúng tôi sẽ gọi xác nhận trong ít phút.</>}
        extra={[
          <Link key="shop" to="/san-pham"><Button>Tiếp tục mua sắm</Button></Link>,
          user
            ? <Link key="orders" to={`/tai-khoan/don-hang/${code}`}><Button type="primary">Xem đơn hàng</Button></Link>
            : <Link key="track" to={`/tra-cuu-don-hang?code=${code}`}><Button type="primary">Tra cứu đơn hàng</Button></Link>,
        ]} />
      {order && <OrderView order={order} showHistory={false} />}
    </div>
  );
}
