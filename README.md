# Sải Bước - cửa hàng giày trực tuyến

Ứng dụng bán giày gồm trang mua sắm cho khách và trang quản trị cho cửa hàng. Hệ thống chạy trên ba máy chủ RHEL 9, mỗi máy một tầng:

| Máy | Thành phần | Cổng |
|---|---|---|
| Web | nginx: giao diện React, reverse proxy `/api` | 80 |
| Ứng dụng | `shop-backend` (Spring Boot, Java 21) và `shop-payment` (cổng thanh toán thẻ giả lập) | 8080, 8081 (health), 8090 (chỉ nội bộ) |
| CSDL | PostgreSQL 16: database `shop` và `payments` | 5432 |

```
Trình duyệt ──HTTP──> nginx (web) ──/api──> shop-backend ──JDBC──> PostgreSQL: shop
                                                 │
                                                 └──HTTP──> shop-payment ──JDBC──> PostgreSQL: payments
```

## Tính năng

**Trang khách hàng** (`/`)
- Trang chủ, danh mục, tìm kiếm không cần gõ dấu, lọc theo size, giá, thương hiệu, đối tượng.
- Trang sản phẩm có chọn size và báo tồn kho theo từng size.
- Giỏ hàng, mã giảm giá, phí giao hàng (miễn phí từ 1.000.000₫).
- Đặt hàng không cần tài khoản; thanh toán khi nhận hàng hoặc bằng thẻ.
- Tài khoản: lịch sử đơn, hủy đơn đang chờ, tra cứu đơn bằng mã và số điện thoại.

**Trang quản trị** (`/admin`)
- Tổng quan: doanh thu, số đơn, tỷ lệ thanh toán thẻ thất bại, biểu đồ theo ngày, sản phẩm bán chạy, sắp hết hàng.
- Đơn hàng: lọc theo trạng thái, xác nhận, giao hàng, hoàn tất, hủy (hoàn tiền thẻ tự động).
- Sản phẩm và tồn kho theo size, khách hàng, mã giảm giá.
- Cấu hình cổng thanh toán: độ trễ, tỷ lệ lỗi, tỷ lệ từ chối, ngừng hoạt động.
- Mô phỏng sự cố cho môi trường thử nghiệm (xem [docs/demo.md](docs/demo.md)).

## Tài khoản mẫu

Khi bật dữ liệu mẫu (`APP_SEED_ENABLED=true`):

| Email | Mật khẩu | Vai trò |
|---|---|---|
| `admin@saibuoc.vn` | giá trị `APP_ADMIN_PASSWORD` | Quản trị |
| `khach@saibuoc.vn` | `SaiBuoc@2026` | Khách hàng có sẵn lịch sử đơn |
| `load01..20@saibuoc.vn` | `SaiBuoc@2026` | Dành cho kiểm thử tải |

Thẻ thử: `4111 1111 1111 1111` thành công, `4000 0000 0000 0002` bị từ chối, `4000 0000 0000 9995` không đủ số dư (hạn bất kỳ trong tương lai, CVV 3 số).

## Cấu trúc thư mục

| Thư mục | Nội dung |
|---|---|
| `backend/` | API cửa hàng (Spring Boot 3.4, JPA + JDBC, Flyway) |
| `payment/` | Cổng thanh toán thẻ giả lập (Spring Boot, database riêng) |
| `frontend/` | React 18 + antd: trang khách hàng và trang quản trị |
| `nginx/` | Cấu hình nginx cho máy web |
| `deploy/` | Script cài đặt và triển khai cho từng máy RHEL 9 |
| `scripts/` | Smoke test, sinh ảnh sản phẩm, kịch bản tải k6 |
| `docs/` | Triển khai, vận hành (bật/tắt), kịch bản demo |

## Phát triển

```bash
# Backend (cần PostgreSQL với database shop và payments)
cd payment && DB_PASSWORD=... PAYMENT_DB_PASSWORD=... ./mvnw spring-boot:run
cd backend && DB_PASSWORD=... JWT_SECRET=$(openssl rand -base64 48) ./mvnw spring-boot:run
# Front end, proxy /api sang backend
cd frontend && VITE_DEV_PROXY_TARGET=http://localhost:8080 npm run dev
# Kiểm thử
(cd backend && ./mvnw test) && (cd payment && ./mvnw test) && (cd frontend && npm test)
```

Triển khai lên ba máy: [docs/deployment.md](docs/deployment.md). Danh sách những gì đang chạy và cách bật/tắt: [docs/operations.md](docs/operations.md).
