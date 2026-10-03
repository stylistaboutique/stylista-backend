package com.stylista.repository;

import com.stylista.model.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // All customers on a mobile (multiple names possible)
    List<Customer> findByMobile(String mobile);

    /**
     * PERF: bulk version of findByMobile for a SET of mobiles in one query.
     * Used to make the admin Customers list pagination-safe for shared-mobile
     * cashback pooling: resolves the FULL customer-id set for every mobile
     * present on the current page (not just the page's own ids), so two
     * customers sharing a mobile still get the correct pooled balance even if
     * they land on different pages.
     */
    List<Customer> findByMobileIn(Collection<String> mobiles);

    /**
     * SEARCH: paginated customer list with an optional search term, applied in
     * SQL BEFORE pagination (not after, client-side, against only the current
     * page -- that was the bug where a match on page 2 showed nothing on page 1).
     * qLike is null when there's no search text (controller passes null, not "").
     */
    @Query("SELECT c FROM Customer c " +
           "WHERE (:qLike IS NULL OR LOWER(c.name) LIKE :qLike OR c.mobile LIKE :qLike)")
    Page<Customer> searchPage(@org.springframework.data.repository.query.Param("qLike") String qLike, Pageable pageable);

    // Case-insensitive name + exact mobile — used by addOrUpdate to find existing customer
    // Spring Data generates: WHERE LOWER(name) = LOWER(:name) AND mobile = :mobile
    Optional<Customer> findByNameIgnoreCaseAndMobile(String name, String mobile);

    // Exact match (still kept for other callers)
    Optional<Customer> findByNameAndMobile(String name, String mobile);

    boolean existsByMobile(String mobile);
}
