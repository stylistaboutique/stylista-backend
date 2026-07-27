package com.stylista.repository;

import com.stylista.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // All customers on a mobile (multiple names possible)
    List<Customer> findByMobile(String mobile);

    // Case-insensitive name + exact mobile — used by addOrUpdate to find existing customer
    // Spring Data generates: WHERE LOWER(name) = LOWER(:name) AND mobile = :mobile
    Optional<Customer> findByNameIgnoreCaseAndMobile(String name, String mobile);

    // Exact match (still kept for other callers)
    Optional<Customer> findByNameAndMobile(String name, String mobile);

    boolean existsByMobile(String mobile);
}
