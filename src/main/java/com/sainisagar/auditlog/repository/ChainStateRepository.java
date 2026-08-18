package com.sainisagar.auditlog.repository;

import com.sainisagar.auditlog.entity.ChainState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChainStateRepository extends JpaRepository<ChainState, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from ChainState state where state.chainName = :chainName")
    Optional<ChainState> findByNameForUpdate(@Param("chainName") String chainName);
}
