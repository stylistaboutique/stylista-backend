package com.stylista.repository;

import com.stylista.model.CashbackAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CashbackRepository extends JpaRepository<CashbackAssignment, Long> {

    // All cashbacks for a customer, oldest first (important for expiry: oldest > 60 days expires first)
    List<CashbackAssignment> findByCustomerIdOrderByAssignedAtAsc(Long customerId);

    // Active (not redeemed, not expired) cashbacks
    @Query("SELECT c FROM CashbackAssignment c WHERE c.redeemed = false AND c.expiresAt > :now")
    List<CashbackAssignment> findActive(@Param("now") LocalDateTime now);

    // Expiring within 7 days (for stats)
    @Query("SELECT c FROM CashbackAssignment c WHERE c.redeemed = false AND c.expiresAt > :now AND c.expiresAt <= :in7")
    List<CashbackAssignment> findExpiringSoon(@Param("now") LocalDateTime now, @Param("in7") LocalDateTime in7);

    // For reminder scheduler
    @Query("SELECT c FROM CashbackAssignment c WHERE c.redeemed = false AND c.expiresAt > :now")
    List<CashbackAssignment> findAllActive(@Param("now") LocalDateTime now);

    long countByRedeemedFalseAndExpiresAtAfter(LocalDateTime now);
    long countByRedeemedTrue();
}
