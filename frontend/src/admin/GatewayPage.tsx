import { useEffect } from 'react';
import { Alert, App, Button, Card, Col, Form, InputNumber, Row, Slider, Statistic, Switch } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi, type GatewaySettings } from '@/api/admin';
import { errorMessage } from '@/api/client';

const PRESETS: { label: string; values: Partial<GatewaySettings> }[] = [
  { label: 'Bình thường', values: { extraLatencyMs: 0, errorRatePercent: 0, declineRatePercent: 3, outage: false } },
  { label: 'Ngân hàng chậm (+4s)', values: { extraLatencyMs: 4000, errorRatePercent: 0, outage: false } },
  { label: 'Quá thời gian chờ (+10s)', values: { extraLatencyMs: 10000, errorRatePercent: 0, outage: false } },
  { label: 'Lỗi 30%', values: { extraLatencyMs: 0, errorRatePercent: 30, outage: false } },
  { label: 'Ngừng hoạt động', values: { outage: true } },
];

export default function GatewayPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<GatewaySettings>();
  const { data, error, refetch } = useQuery({ queryKey: ['gateway'], queryFn: adminApi.gateway, refetchInterval: 15_000 });
  useEffect(() => {
    if (data && !form.isFieldsTouched()) form.setFieldsValue(data);
  }, [data, form]);

  const save = useMutation({
    mutationFn: (v: GatewaySettings) => adminApi.updateGateway(v),
    onSuccess: (s) => { message.success('Đã lưu cấu hình cổng thanh toán'); form.resetFields(); form.setFieldsValue(s); queryClient.setQueryData(['gateway'], s); },
    onError: (e) => message.error(errorMessage(e)),
  });

  const applyPreset = (values: Partial<GatewaySettings>) => {
    const next = { ...form.getFieldsValue(), ...values } as GatewaySettings;
    form.setFieldsValue(next);
    save.mutate(next);
  };

  return (
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={14}>
        <Card title="Hành vi cổng thanh toán thẻ">
          {error && <Alert type="error" showIcon message={errorMessage(error)} style={{ marginBottom: 16 }} action={<Button onClick={() => refetch()}>Thử lại</Button>} />}
          <Alert type="info" showIcon style={{ marginBottom: 16 }}
            message="Cổng thanh toán là một dịch vụ riêng. Backend chờ tối đa 8 giây, sau đó coi là lỗi và hoàn kho cho đơn." />
          <div style={{ marginBottom: 16, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {PRESETS.map((p) => <Button key={p.label} onClick={() => applyPreset(p.values)}>{p.label}</Button>)}
          </div>
          <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
            <Form.Item name="baseLatencyMs" label="Thời gian xử lý bình thường (ms)"><InputNumber min={0} max={5000} step={50} /></Form.Item>
            <Form.Item name="extraLatencyMs" label="Độ trễ thêm (ms)"><Slider min={0} max={15000} step={500} marks={{ 0: '0', 8000: '8s (timeout)', 15000: '15s' }} /></Form.Item>
            <Form.Item name="errorRatePercent" label="Tỷ lệ lỗi hệ thống (%)"><Slider min={0} max={100} marks={{ 0: '0', 50: '50', 100: '100' }} /></Form.Item>
            <Form.Item name="declineRatePercent" label="Tỷ lệ ngân hàng từ chối (%)"><Slider min={0} max={100} marks={{ 0: '0', 50: '50', 100: '100' }} /></Form.Item>
            <Form.Item name="outage" label="Ngừng hoạt động (mọi yêu cầu trả 503)" valuePropName="checked"><Switch /></Form.Item>
            <Button type="primary" htmlType="submit" loading={save.isPending}>Lưu cấu hình</Button>
          </Form>
        </Card>
      </Col>
      <Col xs={24} lg={10}>
        <Card title="1 giờ gần nhất">
          <Row gutter={16}>
            <Col span={8}><Statistic title="Thành công" value={data?.lastHour?.approved ?? 0} /></Col>
            <Col span={8}><Statistic title="Từ chối" value={data?.lastHour?.declined ?? 0} /></Col>
            <Col span={8}><Statistic title="Xử lý TB" value={data?.lastHour?.avg_ms ?? 0} suffix="ms" /></Col>
          </Row>
          <p style={{ color: '#8a857c', marginTop: 16 }}>Lỗi hệ thống và ngừng hoạt động không được ghi vào bảng giao dịch của cổng, mà hiện ở đơn hàng "Thanh toán thất bại".</p>
        </Card>
      </Col>
    </Row>
  );
}
