package vn.saibuoc.shop.catalog;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    /** Row locks in id order so concurrent checkouts of the same items cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariant v join fetch v.product where v.id in ?1 order by v.id")
    List<ProductVariant> lockAll(Collection<Long> ids);

    @Query("select v from ProductVariant v join fetch v.product where v.id in ?1")
    List<ProductVariant> findAllWithProduct(Collection<Long> ids);
}
