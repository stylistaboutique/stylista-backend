package com.stylista.service;

import com.stylista.model.Customer;
import com.stylista.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepo;

    public CustomerService(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    public Customer addOrUpdate(String name, String mobile, String measurements) {
        Customer c = customerRepo.findByMobile(mobile).orElseGet(() -> {
            Customer fresh = new Customer();
            fresh.setMobile(mobile);
            return fresh;
        });
        if (name != null && !name.isBlank()) c.setName(name);
        if (measurements != null)            c.setMeasurements(measurements);
        return customerRepo.save(c);
    }

    public List<Customer> allCustomers() {
        return customerRepo.findAll();
    }

    public Optional<Customer> findById(Long id) {
        return customerRepo.findById(id);
    }

    public Optional<Customer> findByMobile(String mobile) {
        return customerRepo.findByMobile(mobile);
    }

    /** Update measurements only (patch) */
    public Optional<Customer> updateMeasurements(Long id, String measurements) {
        return customerRepo.findById(id).map(c -> {
            c.setMeasurements(measurements);
            return customerRepo.save(c);
        });
    }

    public long totalCustomers() { return customerRepo.count(); }
}
