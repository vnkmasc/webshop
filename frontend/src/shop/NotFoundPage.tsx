import { Link } from 'react-router-dom';
import { Button, Result } from 'antd';

export default function NotFoundPage() {
  return <Result status="404" title="Không tìm thấy trang" subTitle="Trang bạn tìm không tồn tại hoặc đã được di chuyển."
    extra={<Link to="/"><Button type="primary">Về trang chủ</Button></Link>} />;
}
