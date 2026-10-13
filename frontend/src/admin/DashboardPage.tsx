import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Card, Col, Empty, Row, Segmented, Skeleton, Statistic, Table, Tag } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import dayjs from 'dayjs';
import { adminApi } from '@/api/admin';
import { dateTime, money, shortMoney } from '@/utils/format';
import { ORDER_STATUS } from '@/utils/labels';

const SERIES = '#e4572e';
const GRID = '#ece9e2';
const AXIS = { fontSize: 12, fill: '#8a857c' };

function Delta({ now, before }: { now: number; before: number }) {
  if (!before) return null;
  const pct = Math.round(((now - before) / before) * 100);
  const up = pct >= 0;
  return (
    <span style={{ fontSize: 13, color: up ? '#2f7d4a' : '#c62f2f' }}>
      {up ? <ArrowUpOutlined /> : <ArrowDownOutlined />} {Math.abs(pct)}% so với kỳ trước
    </span>
  );
}

export default function DashboardPage() {
  const [days, setDays] = useState(30);
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard', days],
    queryFn: () => adminApi.dashboard(days),
    refetchInterval: 60_000,
  });

  if (isLoading || !data) return <Skeleton active paragraph={{ rows: 14 }} />;
  const k = data.kpis;
  const failRate = k.cardAttempts ? Math.round((k.failedPayments / k.cardAttempts) * 1000) / 10 : 0;

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <Segmented value={days} onChange={(v) => setDays(v as number)}
          options={[{ label: 'Hôm nay', value: 1 }, { label: '7 ngày', value: 7 }, { label: '30 ngày', value: 30 }, { label: '90 ngày', value: 90 }]} />
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <Card className="kpi"><Statistic title="Doanh thu" value={money(k.revenue)} /><Delta now={k.revenue} before={k.revenuePrevious} /></Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card className="kpi"><Statistic title="Đơn hàng" value={k.orders} /><Delta now={k.orders} before={k.ordersPrevious} /></Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card className="kpi"><Statistic title="Giá trị đơn trung bình" value={money(k.avgOrderValue)} />
            <span style={{ fontSize: 13, color: '#8a857c' }}>{k.itemsSold} đôi đã bán</span></Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card className="kpi"><Statistic title="Thanh toán thẻ thất bại" value={`${failRate}%`} />
            <span style={{ fontSize: 13, color: '#8a857c' }}>{k.failedPayments} / {k.cardAttempts} giao dịch · {k.newCustomers} khách mới</span></Card>
        </Col>

        <Col xs={24} xl={16}>
          <Card title={`Doanh thu theo ngày (${days === 1 ? 'hôm nay' : `${days} ngày`})`}>
            <ResponsiveContainer width="100%" height={280}>
              <AreaChart data={data.series} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="rev" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={SERIES} stopOpacity={0.25} />
                    <stop offset="100%" stopColor={SERIES} stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={GRID} vertical={false} />
                <XAxis dataKey="day" tick={AXIS} tickLine={false} axisLine={{ stroke: GRID }}
                  tickFormatter={(d) => dayjs(d).format('DD/MM')} minTickGap={24} />
                <YAxis tick={AXIS} tickLine={false} axisLine={false} tickFormatter={shortMoney} width={64} />
                <Tooltip formatter={(v: number, name) => (name === 'revenue' ? [money(v), 'Doanh thu'] : [v, 'Đơn'])}
                  labelFormatter={(d) => dayjs(d).format('dddd DD/MM/YYYY')} cursor={{ stroke: '#bdb7ac' }} />
                <Area isAnimationActive={false} type="monotone" dataKey="revenue" stroke={SERIES} strokeWidth={2} fill="url(#rev)"
                  activeDot={{ r: 5, stroke: '#fff', strokeWidth: 2 }} />
              </AreaChart>
            </ResponsiveContainer>
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card title="Đơn theo trạng thái" style={{ height: '100%' }}>
            {data.statuses.length === 0 ? <Empty /> : data.statuses.map((s) => (
              <div key={s.status} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderBottom: '1px solid #f2f0ec' }}>
                <Link to={`/admin/don-hang?status=${s.status}`}><Tag color={ORDER_STATUS[s.status].color}>{ORDER_STATUS[s.status].label}</Tag></Link>
                <b>{s.count}</b>
              </div>
            ))}
          </Card>
        </Col>

        <Col xs={24} lg={12}>
          <Card title="Sản phẩm bán chạy (số đôi)">
            {data.topProducts.length === 0 ? <Empty /> : (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={data.topProducts} layout="vertical" margin={{ left: 8, right: 24 }}>
                  <CartesianGrid stroke={GRID} horizontal={false} />
                  <XAxis type="number" tick={AXIS} tickLine={false} axisLine={false} allowDecimals={false} />
                  <YAxis type="category" dataKey="name" tick={AXIS} tickLine={false} axisLine={false} width={150} />
                  <Tooltip formatter={(v: number, _n, item) => [`${v} đôi · ${money(item.payload.revenue)}`, 'Đã bán']}
                    cursor={{ fill: '#f6f4ef' }} />
                  <Bar isAnimationActive={false} dataKey="quantity" fill={SERIES} radius={[0, 4, 4, 0]} barSize={16} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Doanh thu theo danh mục">
            {data.categories.length === 0 ? <Empty /> : (
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={data.categories} layout="vertical" margin={{ left: 8, right: 24 }}>
                  <CartesianGrid stroke={GRID} horizontal={false} />
                  <XAxis type="number" tick={AXIS} tickLine={false} axisLine={false} tickFormatter={shortMoney} />
                  <YAxis type="category" dataKey="category" tick={AXIS} tickLine={false} axisLine={false} width={140} />
                  <Tooltip formatter={(v: number) => [money(v), 'Doanh thu']} cursor={{ fill: '#f6f4ef' }} />
                  <Bar isAnimationActive={false} dataKey="revenue" fill={SERIES} radius={[0, 4, 4, 0]} barSize={16} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </Card>
        </Col>

        <Col xs={24} lg={14}>
          <Card title="Đơn hàng mới nhất" extra={<Link to="/admin/don-hang">Tất cả</Link>}>
            <Table size="small" rowKey="code" pagination={false} dataSource={data.recent} scroll={{ x: 560 }}
              columns={[
                { title: 'Mã', dataIndex: 'code', render: (c) => <Link to={`/admin/don-hang?code=${c}`}>{c}</Link> },
                { title: 'Khách', dataIndex: 'customerName' },
                { title: 'Tổng', dataIndex: 'total', align: 'right', render: money },
                { title: 'Trạng thái', dataIndex: 'status', render: (s: keyof typeof ORDER_STATUS) => <Tag color={ORDER_STATUS[s].color}>{ORDER_STATUS[s].label}</Tag> },
                { title: 'Lúc', dataIndex: 'createdAt', render: dateTime },
              ]} />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="Sắp hết hàng (≤ 3 đôi)" extra={<Link to="/admin/san-pham?lowStock=true">Quản lý tồn kho</Link>}>
            <Table size="small" rowKey={(r) => `${r.productId}-${r.size}`} pagination={false} dataSource={data.lowStock}
              columns={[
                { title: 'Sản phẩm', dataIndex: 'productName' },
                { title: 'Size', dataIndex: 'size', align: 'center' },
                { title: 'Còn', dataIndex: 'stock', align: 'right', render: (n: number) => <b style={{ color: n === 0 ? '#c62f2f' : undefined }}>{n}</b> },
              ]} />
          </Card>
        </Col>
      </Row>
    </>
  );
}
