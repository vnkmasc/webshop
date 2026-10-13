import { useMemo, useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { Breadcrumb, Button, Checkbox, Drawer, Empty, Pagination, Radio, Select, Skeleton, Tag } from 'antd';
import { FilterOutlined } from '@ant-design/icons';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { shopApi } from '@/api/shop';
import { ProductGrid } from './components/ProductCard';

const PAGE_SIZE = 24;

const PRICE_RANGES = [
  { key: 'u500', label: 'Dưới 500.000₫', min: undefined, max: 500_000 },
  { key: '500-1m', label: '500.000₫ - 1.000.000₫', min: 500_000, max: 1_000_000 },
  { key: '1m-2m', label: '1.000.000₫ - 2.000.000₫', min: 1_000_000, max: 2_000_000 },
  { key: '2m-3m', label: '2.000.000₫ - 3.000.000₫', min: 2_000_000, max: 3_000_000 },
  { key: 'o3m', label: 'Trên 3.000.000₫', min: 3_000_000, max: undefined },
];

const SORTS = [
  { value: 'newest', label: 'Mới nhất' },
  { value: 'bestseller', label: 'Bán chạy' },
  { value: 'price_asc', label: 'Giá tăng dần' },
  { value: 'price_desc', label: 'Giá giảm dần' },
  { value: 'rating', label: 'Đánh giá cao' },
];

export default function ProductListPage() {
  const { category } = useParams();
  const [params, setParams] = useSearchParams();
  const [drawer, setDrawer] = useState(false);

  const list = (k: string) => params.getAll(k);
  const price = PRICE_RANGES.find((r) => r.key === params.get('price'));
  const query = {
    category,
    q: params.get('q') ?? undefined,
    onSale: params.get('onSale') === 'true' || undefined,
    sort: params.get('sort') ?? 'newest',
    brand: list('brand'),
    gender: list('gender'),
    size: list('size'),
    minPrice: price?.min,
    maxPrice: price?.max,
    page: Number(params.get('page') ?? 1) - 1,
    pageSize: PAGE_SIZE,
  };

  const { data: facets } = useQuery({ queryKey: ['facets'], queryFn: shopApi.facets, staleTime: 300_000 });
  const { data: categories = [] } = useQuery({ queryKey: ['categories'], queryFn: shopApi.categories, staleTime: 300_000 });
  const { data, isLoading, isFetching } = useQuery({
    queryKey: ['products', query],
    queryFn: () => shopApi.products(query),
    placeholderData: keepPreviousData,
  });
  const categoryName = useMemo(() => categories.find((c) => c.slug === category)?.name, [categories, category]);

  const update = (mutate: (p: URLSearchParams) => void) => {
    const next = new URLSearchParams(params);
    mutate(next);
    next.delete('page');
    setParams(next);
  };
  const setList = (key: string, values: string[]) => update((p) => {
    p.delete(key);
    values.forEach((v) => p.append(key, v));
  });
  const toggleSize = (s: string) => setList('size', query.size.includes(s) ? query.size.filter((x) => x !== s) : [...query.size, s]);

  const title = query.q ? `Kết quả cho "${query.q}"` : query.onSale ? 'Đang giảm giá' : categoryName ?? 'Tất cả sản phẩm';

  const filters = (
    <div className="filters">
      <h4>Đối tượng</h4>
      <Checkbox.Group value={query.gender} onChange={(v) => setList('gender', v as string[])}
        options={[{ label: 'Nam', value: 'MEN' }, { label: 'Nữ', value: 'WOMEN' }, { label: 'Trẻ em', value: 'KIDS' }]} />
      <h4>Size</h4>
      <div className="size-grid">
        {(facets?.sizes ?? []).map((s) => (
          <div key={s} className={`size-chip${query.size.includes(s) ? ' on' : ''}`} onClick={() => toggleSize(s)}>{s}</div>
        ))}
      </div>
      <h4>Khoảng giá</h4>
      <Radio.Group value={params.get('price') ?? ''} onChange={(e) => update((p) => {
        if (e.target.value) p.set('price', e.target.value); else p.delete('price');
      })} style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <Radio value="">Tất cả</Radio>
        {PRICE_RANGES.map((r) => <Radio key={r.key} value={r.key}>{r.label}</Radio>)}
      </Radio.Group>
      <h4>Thương hiệu</h4>
      <Checkbox.Group value={query.brand} onChange={(v) => setList('brand', v as string[])}
        style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
        options={(facets?.brands ?? []).map((b) => ({ label: b, value: b }))} />
      <div style={{ marginTop: 20 }}>
        <Button block onClick={() => setParams(query.q ? { q: query.q } : {})}>Xóa bộ lọc</Button>
      </div>
    </div>
  );

  const active = [
    ...query.gender.map((g) => ({ key: `gender:${g}`, label: { MEN: 'Nam', WOMEN: 'Nữ', KIDS: 'Trẻ em' }[g] ?? g, remove: () => setList('gender', query.gender.filter((x) => x !== g)) })),
    ...query.size.map((s) => ({ key: `size:${s}`, label: `Size ${s}`, remove: () => toggleSize(s) })),
    ...query.brand.map((b) => ({ key: `brand:${b}`, label: b, remove: () => setList('brand', query.brand.filter((x) => x !== b)) })),
    ...(price ? [{ key: 'price', label: price.label, remove: () => update((p) => p.delete('price')) }] : []),
  ];

  return (
    <>
      <Breadcrumb style={{ marginTop: 16 }} items={[{ title: <Link to="/">Trang chủ</Link> }, { title }]} />
      <div className="listing">
        <aside className="filters-desktop">{filters}</aside>
        <section>
          <div className="listing-top">
            <div>
              <h1 style={{ margin: 0, fontSize: 26 }}>{title}</h1>
              <span style={{ color: 'var(--muted)' }}>{data ? `${data.totalElements} sản phẩm` : ' '}</span>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button className="mobile-only" icon={<FilterOutlined />} onClick={() => setDrawer(true)}>Bộ lọc</Button>
              <Select value={query.sort} options={SORTS} style={{ width: 170 }}
                onChange={(v) => update((p) => p.set('sort', v))} />
            </div>
          </div>
          {active.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              {active.map((a) => <Tag key={a.key} closable onClose={a.remove}>{a.label}</Tag>)}
            </div>
          )}
          {isLoading ? (
            <Skeleton active paragraph={{ rows: 8 }} />
          ) : data && data.content.length > 0 ? (
            <div style={{ opacity: isFetching ? 0.6 : 1, transition: 'opacity .2s' }}>
              <ProductGrid items={data.content} />
              <div style={{ display: 'flex', justifyContent: 'center', marginTop: 28 }}>
                <Pagination current={query.page + 1} pageSize={PAGE_SIZE} total={data.totalElements}
                  showSizeChanger={false} hideOnSinglePage
                  onChange={(page) => {
                    const next = new URLSearchParams(params);
                    next.set('page', String(page));
                    setParams(next);
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                  }} />
              </div>
            </div>
          ) : (
            <Empty description="Không tìm thấy sản phẩm phù hợp" style={{ margin: '60px 0' }} />
          )}
        </section>
      </div>
      <Drawer open={drawer} onClose={() => setDrawer(false)} title="Bộ lọc" placement="right" width={300}>{filters}</Drawer>
    </>
  );
}
