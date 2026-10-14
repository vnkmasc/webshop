# risk - chấm điểm rủi ro đơn hàng

Dịch vụ Python 3.11 + FastAPI. Backend gọi khi khách đặt hàng (timeout 3 giây); dịch vụ không phản hồi thì đơn vẫn đặt được với điểm 0. Không có database: backend gửi kèm các tín hiệu cần thiết.

```
POST /api/risk/score   {"orderRef","amount","paymentMethod","phone","province","avgOrderValue","phoneOrders24h","previousProvinces"}
                       -> {"score": 73, "level": "HIGH", "reasons": ["COD_HIGH_VALUE", "REPEAT_PHONE_24H"]}
GET  /healthz
GET|PUT /api/admin/simulation/{RISK_SLOW|RISK_ERROR|RISK_TIMEOUT}   {"active", "params", "until"} - do backend điều khiển
```

| Lý do | Điều kiện | Điểm |
|---|---|---|
| `AMOUNT_UNUSUAL` | Giá trị ≥ 3 lần (≥ 5 lần) trung bình 30 ngày | 30 (40) |
| `REPEAT_PHONE_24H` | ≥ 3 (≥ 5) đơn cùng số điện thoại trong 24 giờ | 25 (35) |
| `COD_HIGH_VALUE` | COD và giá trị ≥ 5.000.000₫ | 30 |
| `NEW_PROVINCE` | Giao tới tỉnh khác các đơn trước của cùng số điện thoại | 15 |

Mức: `HIGH` ≥ 60 (đơn được đánh dấu cần kiểm tra thủ công), `MEDIUM` ≥ 30, còn lại `LOW`. Tối đa 100.

| Biến | Mặc định | |
|---|---|---|
| `RISK_PORT` | `8095` | |
| `RISK_API_KEY` | - | Nếu đặt, mọi `/api/**` cần `Authorization: Bearer <key>` |
| `APP_RELEASE_VERSION` | `dev` | Hiện ở `/healthz` |

```bash
docker build -t shop-risk:dev -f risk/Containerfile risk          # chạy unit test trong lúc build
docker build --build-arg EXTRA_PIP_PACKAGES="<gói>" ...           # gói bổ sung lúc build, ví dụ agent giám sát
cd risk && python -m unittest discover -s tests -t .              # test logic chấm điểm không cần cài gì
```
