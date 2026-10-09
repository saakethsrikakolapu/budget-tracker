package com.saaketh.budget.imports;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportFormatRepository extends JpaRepository<ImportFormat, Long> {

    Optional<ImportFormat> findByUserIdAndSignature(Long userId, String signature);
}
