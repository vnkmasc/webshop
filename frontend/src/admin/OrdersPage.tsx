import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { App, Button, Card, DatePicker, Drawer, Input, Select, Space, Table, Tabs, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { adminApi, type OrderRow } from '@/api/admin';
import { errorMessage } from '@/api/client';
import type { OrderStatus } from '@/api/types';
import { OrderView } from '@/shop/components/OrderView';
import { dateTime, money } from '@/utils/format';
import { ORDER_STATUS, PAYMENT_STATUS, RISK_LEVEL } from '@/utils/labels';

const ACTION_LABEL: Partial<Record<OrderStatus, string>> = {
  CONFIRMED: 'Xác nhận đơn', SHIPPING: 'Giao cho vận chuyển', DELIVERED: 'Đã giao thành công', CANCELLED: 'Hủy đơn',
};

export default function OrdersPage() {
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const [filters, setFilters] = useState({ status: params.get('status') ?? '', q: '', payment: '', risk: '', page: 0, size: 20,
    from: undefined as string | undefined, to: undefined as string | undefined });
  const openCode = params.get('code');

  const { data: counts = [] } = useQuery({ queryKey: ['admin-order-counts'], queryFn: adminApi.statusCounts });
  const { data, isFetching } = useQuery({ queryKey: ['admin-orders', filters], queryFn: () => adminApi.orders(filters) });
  const { data: detail } = useQuery({ queryKey: ['admin-order', openCode], queryFn: () => adminApi.order(openCode!), enabled: !!openCode });

  useEffect(() => {
    const s = params.get('status');
    if (s !== null && s !== filters.status) setFilters((f) => ({ ...f, status: s, page: 0 }));
  }, [params]); // eslint-disable-line react-hooks/exhaustive-deps

  const change = useMutation({
    mutationFn: ({ status, note }: { status: OrderStatus; note?: string }) => adminApi.changeStatus(openCode!, status, note),
    onSuccess: (res) => {
      queryClient.setQueryData(['admin-order', openCode], res);
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
      queryClient.invalidateQueries({ queryKey: ['admin-order-counts'] });
      message.success(`Đơn ${res.order.code}: ${ORDER_STATUS[res.order.status].label}`);
    },
    onError: (e) => message.error(errorMessage(e)),
  });

  const act = (status: OrderStatus) => {
    if (status === 'CANCELLED') {
      let note = '';
      modal.confirm({
        title: 'Hủy đơn hàng?',
        content: <Input.TextArea placeholder="Lý do hủy" onChange={(e) => { note = e.target.value; }} />,
        okText: 'Hủy đơn', okButtonProps: { danger: true }, cancelText: 'Không',
        onOk: () => change.mutateAsync({ status, note }),
      });
    } else {
      change.mutate({ status });
    }
  };

  const total = counts.reduce((n, c) => n + c.count, 0);
  const tabs = [{ key: '', label: `Tất cả (${total})` },
    ...(Object.keys(ORDER_STATUS) as OrderStatus[]).map((s) => ({
      key: s, label: `${ORDER_STATUS[s].label} (${counts.find((c) => c.status === s)?.count ?? 0})`,
    }))];

  const setOpen = (code: string | null) => {
    const next = new URLSearchParams(params);
    if (code) next.set('code', code); else next.delete('code');
    setParams(next);
  };

  return (
    <Card>
      <Tabs activeKey={filters.status} items={tabs} onChange={(status) => {
        setFilters({ ...filters, status, page: 0 });
        const next = new URLSearchParams(params);
        if (status) next.set('status', status); else next.delete('status');
        setParams(next);
      }} />
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search placeholder="Mã đơn, SĐT, tên, email" allowClear style={{ width: 260 }} onSearch={(q) => setFilters({ ...filters, q, page: 0 })} />
        <Select style={{ width: 200 }} value={filters.payment} onChange={(payment) => setFilters({ ...filters, payment, page: 0 })}
          options={[{ value: '', label: 'Mọi hình thức thanh toán' }, { value: 'COD', label: 'COD' }, { value: 'CARD', label: 'Thẻ' }]} />
        <Select style={{ width: 200 }} value={filters.risk} onChange={(risk) => setFilters({ ...filters, risk, page: 0 })}
          options={[{ value: '', label: 'Mọi mức rủi ro' }, { value: 'HIGH', label: 'Rủi ro cao (cần kiểm tra)' },
            { value: 'MEDIUM', label: 'Rủi ro trung bình' }, { value: 'LOW', label: 'Rủi ro thấp' }]} />
        <DatePicker.RangePicker format="DD/MM/YYYY" onChange={(r) => setFilters({ ...filters, page: 0,
          from: (r?.[0] as Dayjs | null)?.format('YYYY-MM-DD'), to: (r?.[1] as Dayjs | null)?.format('YYYY-MM-DD') })} />
      </Space>
      <Table<OrderRow> rowKey="code" loading={isFetching} dataSource={data?.content} size="middle" scroll={{ x: 1000 }}
        onRow={(r) => ({ onClick: () => setOpen(r.code), style: { cursor: 'pointer' } })}
        pagination={{ current: filters.page + 1, pageSize: filters.size, total: data?.totalElements, showSizeChanger: true,
          onChange: (page, size) => setFilters({ ...filters, page: page - 1, size }) }}
        columns={[
          { title: 'Mã đơn', dataIndex: 'code', render: (c) => <b>{c}</b> },
          { title: 'Thời gian', dataIndex: 'createdAt', render: dateTime },
          { title: 'Khách hàng', render: (_, r) => <>{r.customerName}<br /><small>{r.phone} · {r.province}</small></> },
          { title: 'SL', dataIndex: 'itemCount', align: 'center' },
          { title: 'Tổng tiền', dataIndex: 'total', align: 'right', render: (v, r) => <>{money(v)}{r.couponCode && <><br /><Tag>{r.couponCode}</Tag></>}</> },
          { title: 'Thanh toán', render: (_, r) => <>{r.paymentMethod === 'CARD' ? 'Thẻ' : 'COD'}<br />
            <Tag color={PAYMENT_STATUS[r.paymentStatus as keyof typeof PAYMENT_STATUS].color}>{PAYMENT_STATUS[r.paymentStatus as keyof typeof PAYMENT_STATUS].label}</Tag></> },
          { title: 'Trạng thái', dataIndex: 'status', render: (s: OrderStatus) => <Tag color={ORDER_STATUS[s].color}>{ORDER_STATUS[s].label}</Tag> },
          { title: 'Rủi ro', dataIndex: 'riskLevel', render: (l: string | null, r) => (l
            ? <Tag color={RISK_LEVEL[l].color}>{l === 'HIGH' ? 'Cần kiểm tra' : RISK_LEVEL[l].label.replace('Rủi ro ', '')} · {r.riskScore}</Tag>
            : <span style={{ color: '#bbb' }}>-</span>) },
        ]} />
      <Drawer open={!!openCode} onClose={() => setOpen(null)} width={760} title={`Đơn hàng ${openCode ?? ''}`}
        extra={detail && (
          <Space>
            {detail.nextStatuses.map((s) => (
              <Button key={s} danger={s === 'CANCELLED'} type={s === 'CANCELLED' ? 'default' : 'primary'}
                loading={change.isPending && change.variables?.status === s} onClick={() => act(s)}>
                {ACTION_LABEL[s] ?? s}
              </Button>
            ))}
          </Space>
        )}>
        {detail && <OrderView order={detail.order} />}
      </Drawer>
    </Card>
  );
}
