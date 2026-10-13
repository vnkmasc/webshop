import { useState } from 'react';
import { Card, Input, Table, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { adminApi, type CustomerRow } from '@/api/admin';
import { date, dateTime, money } from '@/utils/format';

export default function CustomersPage() {
  const [filters, setFilters] = useState({ q: '', page: 0, size: 20 });
  const { data, isFetching } = useQuery({ queryKey: ['admin-customers', filters], queryFn: () => adminApi.customers(filters) });
  return (
    <Card>
      <Input.Search placeholder="Email, tên, số điện thoại" allowClear style={{ width: 300, marginBottom: 16 }}
        onSearch={(q) => setFilters({ ...filters, q, page: 0 })} />
      <Table<CustomerRow> rowKey="id" loading={isFetching} dataSource={data?.content} size="middle" scroll={{ x: 900 }}
        pagination={{ current: filters.page + 1, pageSize: filters.size, total: data?.totalElements,
          onChange: (page, size) => setFilters({ ...filters, page: page - 1, size }) }}
        columns={[
          { title: 'Khách hàng', render: (_, r) => <><b>{r.fullName}</b><br /><small>{r.email}</small></> },
          { title: 'Điện thoại', dataIndex: 'phone' },
          { title: 'Tỉnh/thành', dataIndex: 'province' },
          { title: 'Đơn hàng', dataIndex: 'orders', align: 'right' },
          { title: 'Đã chi', dataIndex: 'spent', align: 'right', render: money },
          { title: 'Ngày tạo', dataIndex: 'createdAt', render: date },
          { title: 'Đăng nhập gần nhất', dataIndex: 'lastLoginAt', render: dateTime },
          { title: '', dataIndex: 'active', render: (a) => (a ? null : <Tag>Đã khóa</Tag>) },
        ]} />
    </Card>
  );
}
