package vn.saibuoc.shop.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCode(String code);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query(value = "select nextval('order_code_seq')", nativeQuery = true)
    long nextCode();
}
