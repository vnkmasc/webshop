import { Descriptions, Space, Table, Tag, Timeline } from 'antd';
import type { Order } from '@/api/types';
import { dateTime, money } from '@/utils/format';
import { FULFILLMENT, ORDER_STATUS, PAYMENT_METHOD, PAYMENT_STATUS, RISK_LEVEL, RISK_REASON } from '@/utils/labels';

export function StatusTags({ order }: { order: Pick<Order, 'status' | 'paymentStatus'> }) {
  return (
    <Space size={4}>
      <Tag color={ORDER_STATUS[order.status].color}>{ORDER_STATUS[order.status].label}</Tag>
      <Tag color={PAYMENT_STATUS[order.paymentStatus].color}>{PAYMENT_STATUS[order.paymentStatus].label}</Tag>
    </Space>
  );
}

export function OrderView({ order, showHistory = true }: { order: Order; showHistory?: boolean }) {
  return (
    <>
      <Descriptions size="small" column={{ xs: 1, sm: 2 }} bordered style={{ marginBottom: 16 }}>
        <Descriptions.Item label="Mã đơn"><b>{order.code}</b></Descriptions.Item>
        <Descriptions.Item label="Ngày đặt">{dateTime(order.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="Trạng thái"><StatusTags order={order} /></Descriptions.Item>
        <Descriptions.Item label="Thanh toán">{PAYMENT_METHOD[order.paymentMethod]}</Descriptions.Item>
        <Descriptions.Item label="Người nhận">{order.customerName} · {order.phone}</Descriptions.Item>
        <Descriptions.Item label="Địa chỉ">{order.address}, {order.province}</Descriptions.Item>
        {order.note && <Descriptions.Item label="Ghi chú" span={2}>{order.note}</Descriptions.Item>}
        {order.paymentError && <Descriptions.Item label="Lỗi thanh toán" span={2}>{order.paymentError}</Descriptions.Item>}
        {order.riskLevel && (
          <Descriptions.Item label="Chấm điểm rủi ro" span={2}>
            <Tag color={RISK_LEVEL[order.riskLevel].color}>{RISK_LEVEL[order.riskLevel].label}</Tag> {order.riskScore} điểm
            {order.riskReasons?.length ? <> · {order.riskReasons.map((r) => RISK_REASON[r] ?? r).join(', ')}</> : null}
          </Descriptions.Item>
        )}
        {order.fulfillmentStatus && (
          <Descriptions.Item label="Kho" span={2}>
            <Tag color={FULFILLMENT[order.fulfillmentStatus].color}>{FULFILLMENT[order.fulfillmentStatus].label}</Tag>
            {order.fulfillmentAt && <> lúc {dateTime(order.fulfillmentAt)}</>}
          </Descriptions.Item>
        )}
      </Descriptions>
      <Table size="small" pagination={false} rowKey="variantId" dataSource={order.items} scroll={{ x: 500 }}
        columns={[
          {
            title: 'Sản phẩm', render: (_, i) => (
              <Space><img src={i.image} alt="" className="thumb-sm" /><span>{i.productName}<br /><small>Size {i.size}</small></span></Space>
            ),
          },
          { title: 'Đơn giá', dataIndex: 'unitPrice', align: 'right', render: money },
          { title: 'SL', dataIndex: 'quantity', align: 'center' },
          { title: 'Thành tiền', dataIndex: 'lineTotal', align: 'right', render: money },
        ]}
        summary={() => (
          <>
            <Table.Summary.Row><Table.Summary.Cell index={0} colSpan={3} align="right">Tạm tính</Table.Summary.Cell><Table.Summary.Cell index={1} align="right">{money(order.subtotal)}</Table.Summary.Cell></Table.Summary.Row>
            {order.discount > 0 && <Table.Summary.Row><Table.Summary.Cell index={0} colSpan={3} align="right">Giảm giá ({order.couponCode})</Table.Summary.Cell><Table.Summary.Cell index={1} align="right">-{money(order.discount)}</Table.Summary.Cell></Table.Summary.Row>}
            <Table.Summary.Row><Table.Summary.Cell index={0} colSpan={3} align="right">Phí giao hàng</Table.Summary.Cell><Table.Summary.Cell index={1} align="right">{order.shippingFee === 0 ? 'Miễn phí' : money(order.shippingFee)}</Table.Summary.Cell></Table.Summary.Row>
            <Table.Summary.Row><Table.Summary.Cell index={0} colSpan={3} align="right"><b>Tổng cộng</b></Table.Summary.Cell><Table.Summary.Cell index={1} align="right"><b>{money(order.total)}</b></Table.Summary.Cell></Table.Summary.Row>
          </>
        )} />
      {showHistory && order.history.length > 0 && (
        <>
          <h4 style={{ marginTop: 20 }}>Lịch sử đơn hàng</h4>
          <Timeline items={order.history.map((h) => ({
            children: (
              <>
                <b>{ORDER_STATUS[h.toStatus as keyof typeof ORDER_STATUS]?.label ?? h.toStatus}</b> · {dateTime(h.changedAt)}
                {h.note && <div style={{ color: 'var(--muted)' }}>{h.note}</div>}
              </>
            ),
          }))} />
        </>
      )}
    </>
  );
}
