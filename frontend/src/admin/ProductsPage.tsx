import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  App, Button, Card, Checkbox, Col, Drawer, Form, Input, InputNumber, Modal, Rate, Row, Select, Space, Switch, Table, Tag,
} from 'antd';
import { EditOutlined, InboxOutlined, PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi, type AdminProduct, type ProductInput, type ProductRow } from '@/api/admin';
import { errorMessage } from '@/api/client';
import { shopApi } from '@/api/shop';
import { money } from '@/utils/format';
import { GENDER } from '@/utils/labels';

const STYLES = ['running', 'sneaker', 'oxford', 'boot', 'sandal', 'heel', 'football', 'kids'];
const COLORS = ['den', 'trang', 'xam', 'navy', 'do', 'nau', 'be', 'xanhla', 'hong', 'vang', 'cam', 'xanhduong'];
const IMAGES = STYLES.flatMap((s) => COLORS.map((c) => `/images/shoes/${s}-${c}.svg`));

export default function ProductsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const [filters, setFilters] = useState({ q: '', categoryId: undefined as number | undefined, active: undefined as boolean | undefined,
    lowStock: params.get('lowStock') === 'true', page: 0, size: 20 });
  const [editing, setEditing] = useState<AdminProduct | 'new' | null>(null);
  const [stockFor, setStockFor] = useState<AdminProduct | null>(null);
  const [form] = Form.useForm<ProductInput>();
  const [stockForm] = Form.useForm<{ lines: { size: string; stock: number }[] }>();

  const { data: categories = [] } = useQuery({ queryKey: ['categories'], queryFn: shopApi.categories });
  const { data, isFetching } = useQuery({ queryKey: ['admin-products', filters], queryFn: () => adminApi.products(filters) });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin-products'] });

  const save = useMutation({
    mutationFn: (v: ProductInput) => (editing && editing !== 'new' ? adminApi.updateProduct(editing.id, v) : adminApi.createProduct(v)),
    onSuccess: () => { message.success('Đã lưu sản phẩm'); setEditing(null); refresh(); },
    onError: (e) => message.error(errorMessage(e)),
  });
  const saveStock = useMutation({
    mutationFn: (v: { lines: { size: string; stock: number }[] }) => adminApi.setStock(stockFor!.id, v.lines),
    onSuccess: () => { message.success('Đã cập nhật tồn kho'); setStockFor(null); refresh(); },
    onError: (e) => message.error(errorMessage(e)),
  });

  const openEdit = async (row?: ProductRow) => {
    if (!row) {
      form.resetFields();
      form.setFieldsValue({ active: true, featured: false, gender: 'UNISEX', rating: 4.5, image: IMAGES[0] } as Partial<ProductInput>);
      setEditing('new');
      return;
    }
    const p = await adminApi.product(row.id);
    form.setFieldsValue(p);
    setEditing(p);
  };
  const openStock = async (row: ProductRow) => {
    const p = await adminApi.product(row.id);
    stockForm.setFieldsValue({ lines: p.variants.map((v) => ({ size: v.size, stock: v.stock })) });
    setStockFor(p);
  };

  return (
    <Card>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search placeholder="Tên, hãng, SKU" allowClear style={{ width: 240 }}
          onSearch={(q) => setFilters({ ...filters, q, page: 0 })} />
        <Select placeholder="Danh mục" allowClear style={{ width: 200 }} value={filters.categoryId}
          options={categories.map((c) => ({ value: c.id, label: c.name }))}
          onChange={(categoryId) => setFilters({ ...filters, categoryId, page: 0 })} />
        <Select placeholder="Trạng thái" allowClear style={{ width: 150 }} value={filters.active}
          options={[{ value: true, label: 'Đang bán' }, { value: false, label: 'Ngừng bán' }]}
          onChange={(active) => setFilters({ ...filters, active, page: 0 })} />
        <Checkbox checked={filters.lowStock} onChange={(e) => setFilters({ ...filters, lowStock: e.target.checked, page: 0 })}>Sắp hết hàng</Checkbox>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => openEdit()}>Thêm sản phẩm</Button>
      </Space>
      <Table<ProductRow> rowKey="id" loading={isFetching} dataSource={data?.content} size="middle" scroll={{ x: 1000 }}
        pagination={{ current: filters.page + 1, pageSize: filters.size, total: data?.totalElements, showSizeChanger: true,
          onChange: (page, size) => setFilters({ ...filters, page: page - 1, size }) }}
        columns={[
          { title: '', dataIndex: 'image', width: 72, render: (src) => <img src={src} alt="" className="thumb-sm" /> },
          { title: 'Sản phẩm', render: (_, r) => <><b>{r.name}</b> - {r.color}<br /><small>{r.brand} · {r.category} · {GENDER[r.gender]}</small></> },
          { title: 'Giá', align: 'right', render: (_, r) => r.salePrice ? <><b style={{ color: '#e4572e' }}>{money(r.salePrice)}</b><br /><s style={{ color: '#999' }}>{money(r.price)}</s></> : money(r.price) },
          { title: 'Tồn', dataIndex: 'totalStock', align: 'right', render: (n, r) => <>{n}{r.sizesOutOfStock > 0 && <><br /><Tag color="orange">{r.sizesOutOfStock} size hết</Tag></>}</> },
          { title: 'Đã bán', dataIndex: 'soldCount', align: 'right' },
          { title: 'Trạng thái', render: (_, r) => <>{r.active ? <Tag color="green">Đang bán</Tag> : <Tag>Ngừng bán</Tag>}{r.featured && <Tag color="gold">Nổi bật</Tag>}</> },
          { title: '', width: 180, render: (_, r) => (
            <Space>
              <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(r)}>Sửa</Button>
              <Button size="small" icon={<InboxOutlined />} onClick={() => openStock(r)}>Tồn kho</Button>
            </Space>
          ) },
        ]} />

      <Drawer open={editing !== null} onClose={() => setEditing(null)} width={560}
        title={editing === 'new' ? 'Thêm sản phẩm' : 'Sửa sản phẩm'}
        extra={<Button type="primary" loading={save.isPending} onClick={() => form.submit()}>Lưu</Button>}>
        <Form form={form} layout="vertical" onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Tên mẫu" rules={[{ required: true }]}><Input /></Form.Item>
          <Row gutter={12}>
            <Col span={12}><Form.Item name="brand" label="Thương hiệu" rules={[{ required: true }]}><Input /></Form.Item></Col>
            <Col span={12}><Form.Item name="color" label="Màu" rules={[{ required: true }]}><Input /></Form.Item></Col>
            <Col span={12}><Form.Item name="categoryId" label="Danh mục" rules={[{ required: true }]}>
              <Select options={categories.map((c) => ({ value: c.id, label: c.name }))} /></Form.Item></Col>
            <Col span={12}><Form.Item name="gender" label="Đối tượng" rules={[{ required: true }]}>
              <Select options={Object.entries(GENDER).map(([value, label]) => ({ value, label }))} /></Form.Item></Col>
            <Col span={12}><Form.Item name="price" label="Giá gốc" rules={[{ required: true }]}>
              <InputNumber<number> min={1000} step={10000} style={{ width: '100%' }} formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')} parser={(v) => Number((v ?? '').replace(/\./g, ''))} /></Form.Item></Col>
            <Col span={12}><Form.Item name="salePrice" label="Giá khuyến mãi">
              <InputNumber<number> min={1000} step={10000} style={{ width: '100%' }} formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')} parser={(v) => Number((v ?? '').replace(/\./g, ''))} /></Form.Item></Col>
          </Row>
          <Form.Item name="image" label="Ảnh" rules={[{ required: true }]}>
            <Select showSearch options={IMAGES.map((i) => ({ value: i, label: i.replace('/images/shoes/', '') }))} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(a, b) => a.image !== b.image}>
            {({ getFieldValue }) => getFieldValue('image') && <img src={getFieldValue('image')} alt="" style={{ width: 200, borderRadius: 8, marginBottom: 16 }} />}
          </Form.Item>
          <Form.Item name="description" label="Mô tả"><Input.TextArea rows={4} /></Form.Item>
          <Form.Item name="rating" label="Đánh giá"><Rate allowHalf /></Form.Item>
          <Space size="large">
            <Form.Item name="active" label="Đang bán" valuePropName="checked"><Switch /></Form.Item>
            <Form.Item name="featured" label="Nổi bật" valuePropName="checked"><Switch /></Form.Item>
          </Space>
        </Form>
      </Drawer>

      <Modal open={stockFor !== null} onCancel={() => setStockFor(null)} title={`Tồn kho: ${stockFor?.name ?? ''}`}
        onOk={() => stockForm.submit()} confirmLoading={saveStock.isPending} okText="Lưu">
        <Form form={stockForm} onFinish={(v) => saveStock.mutate(v)}>
          <Form.List name="lines">
            {(fields, { add, remove }) => (
              <>
                {fields.map((f) => (
                  <Space key={f.key} align="baseline">
                    <Form.Item name={[f.name, 'size']} rules={[{ required: true, pattern: /^\d{2}$/, message: '2 chữ số' }]}>
                      <Input addonBefore="Size" style={{ width: 130 }} />
                    </Form.Item>
                    <Form.Item name={[f.name, 'stock']} rules={[{ required: true }]}>
                      <InputNumber addonBefore="Còn" min={0} max={9999} style={{ width: 150 }} />
                    </Form.Item>
                    {stockFor?.variants.some((v) => v.size === stockForm.getFieldValue(['lines', f.name, 'size'])) ? null
                      : <Button type="link" onClick={() => remove(f.name)}>Bỏ</Button>}
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add({ size: '', stock: 0 })} block icon={<PlusOutlined />}>Thêm size</Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </Card>
  );
}
