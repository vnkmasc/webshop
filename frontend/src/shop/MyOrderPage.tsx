import { Link, useParams } from 'react-router-dom';
import { App, Breadcrumb, Button, Popconfirm, Result, Skeleton } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage } from '@/api/client';
import { shopApi } from '@/api/shop';
import { OrderView } from './components/OrderView';

export default function MyOrderPage() {
  const { code = '' } = useParams();
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { data, isLoading, isError } = useQuery({ queryKey: ['my-order', code], queryFn: () => shopApi.myOrder(code) });
  const cancel = useMutation({
    mutationFn: () => shopApi.cancelMyOrder(code),
    onSuccess: (o) => {
      queryClient.setQueryData(['my-order', code], o);
      queryClient.invalidateQueries({ queryKey: ['my-orders'] });
      message.success('Đã hủy đơn hàng');
    },
    onError: (e) => message.error(errorMessage(e)),
  });

  if (isLoading) return <Skeleton active style={{ marginTop: 32 }} />;
  if (isError || !data) return <Result status="404" title="Không tìm thấy đơn hàng" />;
  return (
    <div style={{ maxWidth: 900, margin: '16px auto 48px' }}>
      <Breadcrumb items={[{ title: <Link to="/tai-khoan">Tài khoản</Link> }, { title: data.code }]} />
      <h1>Đơn hàng {data.code}</h1>
      <OrderView order={data} />
      {data.status === 'PENDING' && (
        <Popconfirm title="Hủy đơn hàng này?" okText="Hủy đơn" cancelText="Không" onConfirm={() => cancel.mutate()}>
          <Button danger loading={cancel.isPending}>Hủy đơn hàng</Button>
        </Popconfirm>
      )}
    </div>
  );
}
