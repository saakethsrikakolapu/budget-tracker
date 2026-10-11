package com.saaketh.budget.recurring;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HiddenMerchantRepository extends JpaRepository<HiddenMerchant, Long> {

    List<HiddenMerchant> findByUserId(Long userId);

    boolean existsByUserIdAndMerchantKey(Long userId, String merchantKey);

    long deleteByUserIdAndMerchantKey(Long userId, String merchantKey);
}
