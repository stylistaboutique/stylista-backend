package com.stylista.repository;

import com.stylista.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByMobile(String mobile);
    boolean existsByMobile(String mobile);
}
