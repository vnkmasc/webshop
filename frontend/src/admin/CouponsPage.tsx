import { useState } from 'react';
import { App, Button, Card, Col, DatePicker, Form, Input, InputNumber, Modal, Row, Select, Switch, Table, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { adminApi, type Coupon, type CouponInput } from '@/api/admin';
import { errorMessage } from '@/api/client';
import { date, money } from '@/utils/format';

type FormValues = Omit<CouponInput, 'startsOn' | 'endsOn'> & { range: [Dayjs, Dayjs] };

export default function CouponsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Coupon | 'new' | null>(null);
  const [form] = Form.useForm<FormValues>();
  const { data, isFetching } = useQuery({ queryKey: ['admin-coupons'], queryFn: adminApi.coupons });

  const save = useMutation({
    mutationFn: ({ range, ...v }: FormValues) => {
      const body = { ...v, startsOn: range[0].format('YYYY-MM-DD'), endsOn: range[1].format('YYYY-MM-DD') };
      return editing && editing !== 'new' ? adminApi.updateCoupon(editing.id, body) : adminApi.createCoupon(body);
    },
    onSuccess: () => { message.success('Đã lưu mã giảm giá'); setEditing(null); queryClient.invalidateQueries({ queryKey: ['admin-coupons'] }); },
    onError: (e) => message.error(errorMessage(e)),
  });

  const open = (c?: Coupon) => {
    form.resetFields();
    if (c) form.setFieldsValue({ ...c, range: [dayjs(c.startsOn), dayjs(c.endsOn)] });
    else form.setFieldsValue({ type: 'PERCENT', active: true, minOrder: 0, range: [dayjs(), dayjs().add(30, 'day')] });
    setEditing(c ?? 'new');
  };

  const today = dayjs().format('YYYY-MM-DD');
  return (
    <Card extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => open()}>Tạo mã</Button>} title="Mã giảm giá">
      <Table<Coupon> rowKey="id" loading={isFetching} dataSource={data} size="middle" scroll={{ x: 900 }} pagination={false}
        onRow={(r) => ({ onClick: () => open(r), style: { cursor: 'pointer' } })}
        columns={[
          { title: 'Mã', dataIndex: 'code', render: (c) => <b>{c}</b> },
          { title: 'Mô tả', dataIndex: 'description' },
          { title: 'Giảm', render: (_, c) => (c.type === 'PERCENT' ? `${c.value}%${c.maxDiscount ? ` (tối đa ${money(c.maxDiscount)})` : ''}` : money(c.value)) },
          { title: 'Đơn tối thiểu', dataIndex: 'minOrder', render: money },
          { title: 'Hiệu lực', render: (_, c) => `${date(c.startsOn)} - ${date(c.endsOn)}` },
          { title: 'Đã dùng', render: (_, c) => `${c.usedCount}${c.usageLimit ? ` / ${c.usageLimit}` : ''}` },
          { title: 'Trạng thái', render: (_, c) => (!c.active ? <Tag>Tắt</Tag> : c.endsOn < today ? <Tag color="red">Hết hạn</Tag>
            : c.startsOn > today ? <Tag color="blue">Sắp diễn ra</Tag> : <Tag color="green">Đang áp dụng</Tag>) },
        ]} />
      <Modal open={editing !== null} onCancel={() => setEditing(null)} onOk={() => form.submit()} confirmLoading={save.isPending}
        title={editing === 'new' ? 'Tạo mã giảm giá' : 'Sửa mã giảm giá'} okText="Lưu" width={560}>
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Row gutter={12}>
            <Col span={12}><Form.Item name="code" label="Mã" rules={[{ required: true, pattern: /^[A-Za-z0-9_-]{3,30}$/, message: '3-30 ký tự chữ/số' }]}><Input style={{ textTransform: 'uppercase' }} /></Form.Item></Col>
            <Col span={12}><Form.Item name="type" label="Loại"><Select options={[{ value: 'PERCENT', label: 'Phần trăm' }, { value: 'FIXED', label: 'Số tiền cố định' }]} /></Form.Item></Col>
            <Col span={24}><Form.Item name="description" label="Mô tả"><Input /></Form.Item></Col>
            <Col span={8}><Form.Item name="value" label="Giá trị" rules={[{ required: true }]}><InputNumber min={1} style={{ width: '100%' }} /></Form.Item></Col>
            <Col span={8}><Form.Item name="minOrder" label="Đơn tối thiểu" rules={[{ required: true }]}><InputNumber min={0} step={100000} style={{ width: '100%' }} /></Form.Item></Col>
            <Col span={8}><Form.Item name="maxDiscount" label="Giảm tối đa"><InputNumber min={1} step={10000} style={{ width: '100%' }} /></Form.Item></Col>
            <Col span={16}><Form.Item name="range" label="Thời gian áp dụng" rules={[{ required: true }]}><DatePicker.RangePicker format="DD/MM/YYYY" style={{ width: '100%' }} /></Form.Item></Col>
            <Col span={8}><Form.Item name="usageLimit" label="Giới hạn lượt"><InputNumber min={1} style={{ width: '100%' }} /></Form.Item></Col>
            <Col span={24}><Form.Item name="active" label="Kích hoạt" valuePropName="checked"><Switch /></Form.Item></Col>
          </Row>
        </Form>
      </Modal>
    </Card>
  );
}
