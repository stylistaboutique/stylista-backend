package com.stylista.repository;

import com.stylista.model.Customer;
import com.stylista.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // All orders (incl. deleted) sorted by due_date ascending (nulls last) - admin "Show deleted"
    @Query("SELECT o FROM Order o ORDER BY CASE WHEN o.dueDate IS NULL THEN 1 ELSE 0 END, o.dueDate ASC")
    List<Order> findAllSortedByDueDate();

    // Non-deleted only - default admin list + prioritization view
    @Query("SELECT o FROM Order o WHERE o.deleted = false ORDER BY CASE WHEN o.dueDate IS NULL THEN 1 ELSE 0 END, o.dueDate ASC")
    List<Order> findAllSortedByDueDateNotDeleted();

    List<Order> findByCustomerIdOrderByDueDateAsc(Long customerId);

    // Customer-facing: never expose deleted orders
    List<Order> findByCustomerIdAndDeletedFalseOrderByDueDateAsc(Long customerId);

    List<Order> findByTailorIdOrderByDueDateAsc(Long tailorId);

    /**
     * Flexible admin list query (unpaginated, kept for any remaining callers):
     *  - includeDeleted=false -> only non-deleted rows
     *  - status=null          -> no status filter
     *  - excludeDelivered=true -> drop DELIVERED rows (used as the default "hide delivered" view)
     */
    @Query("SELECT o FROM Order o " +
           "WHERE (:includeDeleted = true OR o.deleted = false) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:excludeDelivered = false OR o.status <> :deliveredStatus) " +
           "ORDER BY CASE WHEN o.dueDate IS NULL THEN 1 ELSE 0 END, o.dueDate ASC")
    List<Order> findFiltered(@org.springframework.data.repository.query.Param("includeDeleted") boolean includeDeleted,
                              @org.springframework.data.repository.query.Param("status") Order.Status status,
                              @org.springframework.data.repository.query.Param("excludeDelivered") boolean excludeDelivered,
                              @org.springframework.data.repository.query.Param("deliveredStatus") Order.Status deliveredStatus);

    /**
     * PERF + SEARCH: same filter/sort as findFiltered, PAGINATED, with an optional
     * search term (qLike). Spring Data auto-derives the COUNT query from this
     * @Query for Page.getTotalElements() -- the count also honors qLike, so
     * total_pages/total_elements reflect the FILTERED result set, not the whole
     * table. This is what GET /api/admin/orders uses.
     *
     * IMPORTANT: search is applied here, in SQL, BEFORE pagination. Searching
     * client-side against only the current page's 20 rows was the bug this
     * fixes -- a match on page 2 would never show while viewing page 1, and a
     * handful of matches could get split across pages instead of filling page 1.
     *
     * Order has no JPA @ManyToOne to Customer (customerId is a plain column), so
     * this uses an explicit ON-clause LEFT JOIN (supported since JPA 2.1 / Hibernate
     * 5.1+) rather than a dot-path join. LEFT JOIN (not INNER) so an order whose
     * customer row is somehow missing still shows up when qLike is null -- it
     * just won't match a name/mobile search, which is correct.
     *
     * qLike is null when there's no search text (controller passes null, not "").
     */
    @Query("SELECT o FROM Order o LEFT JOIN Customer c ON c.id = o.customerId " +
           "WHERE (:includeDeleted = true OR o.deleted = false) " +
           "AND (:status IS NULL OR o.status = :status) " +
           "AND (:excludeDelivered = false OR o.status <> :deliveredStatus) " +
           "AND (:qLike IS NULL " +
           "     OR LOWER(c.name) LIKE :qLike " +
           "     OR c.mobile LIKE :qLike " +
           "     OR LOWER(COALESCE(o.productDescription, '')) LIKE :qLike) " +
           "ORDER BY CASE WHEN o.dueDate IS NULL THEN 1 ELSE 0 END, o.dueDate ASC")
    Page<Order> findFilteredPage(@org.springframework.data.repository.query.Param("includeDeleted") boolean includeDeleted,
                                  @org.springframework.data.repository.query.Param("status") Order.Status status,
                                  @org.springframework.data.repository.query.Param("excludeDelivered") boolean excludeDelivered,
                                  @org.springframework.data.repository.query.Param("deliveredStatus") Order.Status deliveredStatus,
                                  @org.springframework.data.repository.query.Param("qLike") String qLike,
                                  Pageable pageable);
}
