import { useState } from 'react';
import { Alert, App, Button, Card, Col, Form, InputNumber, Row, Select, Space, Switch, Tag, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { adminApi, type ScenarioState } from '@/api/admin';
import { errorMessage } from '@/api/client';

const TARGETS = [
  { value: 'ALL', label: 'Toàn bộ API' },
  { value: 'CATALOG', label: 'Danh mục & sản phẩm' },
  { value: 'CART', label: 'Giỏ hàng' },
  { value: 'CHECKOUT', label: 'Đặt hàng' },
  { value: 'ACCOUNT', label: 'Đăng nhập & tài khoản' },
  { value: 'ADMIN', label: 'Trang quản trị' },
];

type Field = {
  name: string;
  label: string;
  kind: 'number' | 'target' | 'select';
  def: number | string;
  max?: number;
  options?: { value: string | number; label: string }[];
};

const SCENARIOS: Record<string, { title: string; effect: string; fields: Field[] }> = {
  LATENCY: { title: 'API chậm', effect: 'Thêm độ trễ ngẫu nhiên cho một nhóm API.', fields: [
    { name: 'target', label: 'Nhóm API', kind: 'target', def: 'CATALOG' },
    { name: 'minMs', label: 'Tối thiểu (ms)', kind: 'number', def: 800, max: 30000 },
    { name: 'maxMs', label: 'Tối đa (ms)', kind: 'number', def: 2500, max: 30000 }] },
  HTTP_ERROR: { title: 'Lỗi 500', effect: 'Một tỷ lệ request ném exception thật, trả HTTP 500.', fields: [
    { name: 'target', label: 'Nhóm API', kind: 'target', def: 'CHECKOUT' },
    { name: 'ratePercent', label: 'Tỷ lệ (%)', kind: 'number', def: 20, max: 100 }] },
  SLOW_SQL: { title: 'SQL chậm', effect: 'Danh sách và tìm kiếm sản phẩm chạy thêm một câu SQL chậm.', fields: [
    { name: 'sleepMs', label: 'Thời gian (ms)', kind: 'number', def: 2000, max: 30000 }] },
  N_PLUS_ONE: { title: 'Truy vấn N+1', effect: 'Danh sách sản phẩm nạp size của từng sản phẩm bằng một truy vấn riêng.', fields: [] },
  POOL_EXHAUSTION: { title: 'Cạn connection pool', effect: 'Request giữ kết nối DB lâu, các request khác phải chờ.', fields: [
    { name: 'target', label: 'Nhóm API', kind: 'target', def: 'ALL' },
    { name: 'holdMs', label: 'Giữ kết nối (ms)', kind: 'number', def: 3000, max: 30000 },
    { name: 'ratePercent', label: 'Tỷ lệ (%)', kind: 'number', def: 30, max: 100 }] },
  HIGH_CPU: { title: 'CPU cao', effect: 'Trang chi tiết sản phẩm tính gợi ý tốn CPU.', fields: [
    { name: 'cpuMs', label: 'CPU mỗi request (ms)', kind: 'number', def: 800, max: 10000 }] },
  MEMORY_LEAK: { title: 'Rò bộ nhớ', effect: 'Mỗi request giữ lại một ít bộ nhớ, có giới hạn trên.', fields: [
    { name: 'kbPerRequest', label: 'KB mỗi request', kind: 'number', def: 256, max: 10240 },
    { name: 'capMb', label: 'Giới hạn (MB)', kind: 'number', def: 1500, max: 2500 }] },
  FRONTEND_ERROR: { title: 'Lỗi JavaScript', effect: 'Nút "Thêm vào giỏ" ném lỗi JavaScript, khách không thêm được hàng.', fields: [] },
  PAYMENT_BUSINESS_ERROR: { title: 'Lỗi nghiệp vụ trong body', effect: 'Cổng thanh toán trả HTTP 200 nhưng responseCode khác 00 (kiểu core banking); đơn thẻ thất bại.', fields: [
    { name: 'code', label: 'Mã phản hồi', kind: 'select', def: '99', options: [
      { value: '99', label: '99 - Core bận' }, { value: '68', label: '68 - Tài khoản không đủ điều kiện' }, { value: '12', label: '12 - Sai định dạng' }] },
    { name: 'percent', label: 'Tỷ lệ (%)', kind: 'number', def: 40, max: 100 }] },
  ODD_STATUS: { title: 'Mã HTTP lạ', effect: 'Một tỷ lệ request trả 423/402/451/429 kèm body JSON giải thích (423, 429 có Retry-After).', fields: [
    { name: 'code', label: 'Mã HTTP', kind: 'select', def: 423, options: [
      { value: 423, label: '423 - Tài khoản bị khoá' }, { value: 402, label: '402 - Cần thanh toán' },
      { value: 451, label: '451 - Giữ theo yêu cầu pháp lý' }, { value: 429, label: '429 - Quá nhiều yêu cầu' }] },
    { name: 'target', label: 'Nhóm API', kind: 'target', def: 'CHECKOUT' },
    { name: 'percent', label: 'Tỷ lệ (%)', kind: 'number', def: 40, max: 100 }] },
  EMIT_PII: { title: 'Lộ dữ liệu nhạy cảm (giả)', effect: 'Thanh toán thẻ đính email, số thẻ, CVV giả (4111…, demo@example.com, 000) vào URL, header và log để thử tính năng che dữ liệu.', fields: [] },
  READINESS_FAIL: { title: 'Readiness hỏng', effect: '/actuator/health/readiness báo DOWN (liveness vẫn UP); bộ cân bằng tải ngừng gửi request tới backend.', fields: [] },
};

function ScenarioCard({ s, onChange, busy }: { s: ScenarioState; onChange: (active: boolean, params?: Record<string, unknown>, minutes?: number) => void; busy: boolean }) {
  const spec = SCENARIOS[s.scenario];
  const [form] = Form.useForm();
  const [minutes, setMinutes] = useState(15);
  if (!spec) return null;
  const initial = Object.fromEntries(spec.fields.map((f) => [f.name, s.active && s.params[f.name] !== undefined ? s.params[f.name] : f.def]));
  return (
    <Card size="small" title={spec.title}
      extra={s.active ? <Tag color="red">Đang bật đến {dayjs(s.expiresAt).format('HH:mm')}</Tag> : <Tag>Tắt</Tag>}
      style={{ height: '100%', borderColor: s.active ? '#e4572e' : undefined }}>
      <Typography.Text type="secondary" code style={{ fontSize: 12 }}>{s.scenario}</Typography.Text>
      <p style={{ color: '#6b6760', minHeight: 44 }}>{spec.effect}</p>
      <Form form={form} layout="vertical" initialValues={initial} disabled={s.active}>
        <Row gutter={8}>
          {spec.fields.map((f) => (
            <Col span={f.kind === 'number' ? 12 : 24} key={f.name}>
              <Form.Item name={f.name} label={f.label} style={{ marginBottom: 8 }}>
                {f.kind === 'target' ? <Select options={TARGETS} />
                  : f.kind === 'select' ? <Select options={f.options} />
                    : <InputNumber min={0} max={f.max} style={{ width: '100%' }} />}
              </Form.Item>
            </Col>
          ))}
        </Row>
      </Form>
      <Space style={{ marginTop: 8 }}>
        {!s.active && <InputNumber min={1} max={240} value={minutes} onChange={(v) => setMinutes(v ?? 15)} addonAfter="phút" style={{ width: 130 }} />}
        <Switch checked={s.active} loading={busy} checkedChildren="Bật" unCheckedChildren="Tắt"
          onChange={(on) => onChange(on, on ? form.getFieldsValue() : undefined, minutes)} />
      </Space>
    </Card>
  );
}

export default function SimulationPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const { data, error } = useQuery({ queryKey: ['simulation'], queryFn: adminApi.simulation, refetchInterval: 10_000, retry: false });
  const set = useMutation({
    mutationFn: ({ scenario, active, params, minutes }: { scenario: string; active: boolean; params?: Record<string, unknown>; minutes?: number }) =>
      adminApi.setScenario(scenario, { active, params, durationMinutes: minutes }),
    onSuccess: (s) => {
      message.success(`${SCENARIOS[s.scenario]?.title ?? s.scenario}: ${s.active ? 'đã bật' : 'đã tắt'}`);
      queryClient.invalidateQueries({ queryKey: ['simulation'] });
    },
    onError: (e) => message.error(errorMessage(e)),
  });
  const release = useMutation({
    mutationFn: adminApi.releaseMemory,
    onSuccess: (mb) => { message.success(`Đã giải phóng ${mb} MB`); queryClient.invalidateQueries({ queryKey: ['simulation'] }); },
  });

  if (error) {
    return <Alert type="warning" showIcon message="Tính năng mô phỏng sự cố đang tắt"
      description="Đặt APP_SIMULATION_ENABLED=true trong cấu hình backend rồi khởi động lại dịch vụ." />;
  }
  return (
    <>
      <Alert type="warning" showIcon style={{ marginBottom: 16 }}
        message="Chỉ dùng trên môi trường thử nghiệm. Mỗi kịch bản tự tắt khi hết thời gian; không kịch bản nào sửa dữ liệu bán hàng."
        action={<Button size="small" onClick={() => release.mutate()} loading={release.isPending}>Giải phóng bộ nhớ ({data?.retainedMb ?? 0} MB)</Button>} />
      <Row gutter={[16, 16]}>
        {(data?.scenarios ?? []).map((s) => (
          <Col xs={24} md={12} xl={6} key={s.scenario}>
            <ScenarioCard s={s} busy={set.isPending && set.variables?.scenario === s.scenario}
              onChange={(active, params, minutes) => set.mutate({ scenario: s.scenario, active, params, minutes })} />
          </Col>
        ))}
      </Row>
    </>
  );
}
