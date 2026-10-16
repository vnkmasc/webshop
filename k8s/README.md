# Chạy trên Kubernetes

Manifest để chạy toàn bộ cửa hàng trên một cụm Kubernetes: 4 thành phần của ứng dụng cùng PostgreSQL và Kafka chạy trong cụm. Phù hợp cho môi trường dev/test: mỗi thành phần một bản (frontend 2 bản), dữ liệu trên PersistentVolumeClaim của StorageClass mặc định.

```
Trình duyệt ──NodePort 30080──> frontend (nginx, 2 pod) ──/api──> backend ──> postgres (shop)
                                                                     ├──> risk
                                                                     ├──> payment ──> postgres (payments)
                                                                     └──> kafka (topic orders)
```

| File | Nội dung |
|---|---|
| `config.yaml` | ConfigMap `shop-config`: cấu hình không bí mật (địa chỉ các dịch vụ, bật dữ liệu mẫu và mô phỏng sự cố) |
| `postgres.yaml` | PostgreSQL 16 (StatefulSet, PVC 5 Gi); lần đầu tạo database `shop` và `payments` |
| `kafka.yaml` | Kafka 4.1 một node KRaft (StatefulSet, PVC 5 Gi) cho hàng đợi đơn hàng |
| `payment.yaml`, `risk.yaml`, `backend.yaml`, `frontend.yaml` | Deployment + Service của từng thành phần; frontend mở NodePort 30080 |
| `deploy.sh` | Tạo namespace, Secret, áp manifest với image từ registry, chờ mọi thứ chạy |

## 1. Build image

Cần JDK 21, Node 22 và podman (hoặc docker) trên máy build.

| Image | Containerfile | Context | Ghi chú |
|---|---|---|---|
| `shop-backend` | `backend/Containerfile` | `backend/target` | Build jar trước |
| `shop-payment` | `payment/Containerfile` | `payment/target` | Build jar trước |
| `shop-risk` | `risk/Containerfile` | `risk/` | Chạy unit test lúc build |
| `shop-frontend` | `frontend/Containerfile` | `frontend/` | Build `dist/` trước; nginx chuyển `/api/` tới `BACKEND_UPSTREAM` (mặc định `backend:8080`) |

`scripts/build-images.sh` làm hết các bước (build jar/dist, build image, đẩy lên registry), tag theo commit hiện tại (thêm `-dirty` nếu còn thay đổi chưa commit) và `latest`:

```bash
podman login registry.example.com                       # một lần
scripts/build-images.sh -p registry.example.com/shop    # cả 4 image
scripts/build-images.sh -p registry.example.com/shop backend frontend   # chỉ vài image
```

Registry dùng chứng chỉ tự ký: đặt CA vào `/etc/containers/certs.d/<registry>:<port>/ca.crt` (podman) hoặc `/etc/docker/certs.d/...` (docker). Các node phải kéo được image: dùng project public, hoặc thêm `imagePullSecrets` vào các Deployment.

## 2. Triển khai

```bash
k8s/deploy.sh registry.example.com/shop <tag> [namespace]     # namespace mặc định: webshop
```

Lần đầu `deploy.sh` tạo Secret `shop-secrets` với giá trị ngẫu nhiên (mật khẩu database, khóa API giữa các dịch vụ, `JWT_SECRET`, mật khẩu admin); các lần sau giữ nguyên. Chạy lại với tag mới để cập nhật phiên bản.

Sau khi chạy xong:

- Cửa hàng: `http://<IP node bất kỳ>:30080`, quản trị: `/admin`.
- Tài khoản admin: `admin@saibuoc.vn`, mật khẩu:
  ```bash
  kubectl -n webshop get secret shop-secrets -o jsonpath='{.data.APP_ADMIN_PASSWORD}' | base64 -d; echo
  ```
- Khách hàng mẫu: như phần "Tài khoản và dữ liệu mẫu" của [README](../README.md).

## 3. Vận hành

```bash
kubectl -n webshop get pods -o wide
kubectl -n webshop logs -f deploy/backend                 # log JSON (ECS) ra stdout
kubectl -n webshop rollout restart deploy/backend         # khởi động lại (cũng tắt mọi kịch bản sự cố đang bật)
kubectl -n webshop scale deploy/risk --replicas=0         # tắt một dịch vụ (đặt lại 1 để bật)
kubectl -n webshop port-forward svc/backend 8081:8081     # actuator: http://localhost:8081/actuator/health
kubectl delete ns webshop                                  # gỡ toàn bộ, kể cả dữ liệu (PVC)
```

Lưu ý:

- **Readiness của backend** theo `/actuator/health/readiness`. Khi nó DOWN (mất database, kịch bản `READINESS_FAIL`), Kubernetes ngừng chuyển request tới pod và cửa hàng trả lỗi 502 cho tới khi readiness UP lại.
- Kịch bản sự cố (`APP_SIMULATION_ENABLED`) chạy như trên mọi môi trường. `HIGH_CPU`, `MEMORY_LEAK` bị giới hạn bởi `resources.limits` của pod: rò bộ nhớ quá limit thì pod bị OOMKilled và khởi động lại.
- Các pod tắt `enableServiceLinks`: nếu bật, Kubernetes thêm biến `PAYMENT_PORT=tcp://...`, `BACKEND_PORT`, `RISK_PORT`... trùng tên với cấu hình của ứng dụng và làm dịch vụ không khởi động được.
- Bỏ hàng đợi: đặt `APP_KAFKA_BOOTSTRAP: ""` trong `config.yaml` và không áp `kafka.yaml`.
- Dùng PostgreSQL/Kafka có sẵn bên ngoài cụm: sửa `DB_HOST`, `APP_KAFKA_BOOTSTRAP` trong `config.yaml`, tạo sẵn hai database và user, đặt đúng mật khẩu trong `shop-secrets`, và không áp `postgres.yaml`/`kafka.yaml`.
