package com.stylista.repository;

import com.stylista.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    // A mobile can now map to MANY customers (name+mobile is the unique key)
    List<Customer> findByMobile(String mobile);
    Optional<Customer> findByNameAndMobile(String name, String mobile);
    boolean existsByMobile(String mobile);
}
