package com.sainisagar.auditlog.repository;

import com.sainisagar.auditlog.entity.RedactionReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RedactionReceiptRepository extends JpaRepository<RedactionReceipt, Long> {
    List<RedactionReceipt> findAllByOrderByRedactedAtAsc();
}
