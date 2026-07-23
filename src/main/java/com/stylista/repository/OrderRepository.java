package com.stylista.repository;

import com.stylista.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // All orders sorted by due_date ascending (nulls last) — drives prioritization view
    @Query("SELECT o FROM Order o ORDER BY CASE WHEN o.dueDate IS NULL THEN 1 ELSE 0 END, o.dueDate ASC")
    List<Order> findAllSortedByDueDate();

    List<Order> findByCustomerIdOrderByDueDateAsc(Long customerId);

    List<Order> findByTailorIdOrderByDueDateAsc(Long tailorId);
}
