package com.stylista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylista.model.Customer;
import com.stylista.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepo;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public CustomerService(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    /**
     * Add a new customer or update an existing one (matched by mobile).
     * Measurements MERGE on repeat customers; the "notes" (Other notes) field
     * is sticky: preserved once set, but allowed through the first time.
     */
    public Customer addOrUpdate(String name, String mobile, String measurements) {
        Optional<Customer> existing = customerRepo.findByMobile(mobile);

        Customer c = existing.orElseGet(() -> {
            Customer fresh = new Customer();
            fresh.setMobile(mobile);
            return fresh;
        });

        if (name != null && !name.isBlank()) c.setName(name);

        if (measurements != null) {
            if (existing.isPresent()) {
                c.setMeasurements(mergeMeasurements(c.getMeasurements(), measurements));
            } else {
                c.setMeasurements(measurements);
            }
        }
        return customerRepo.save(c);
    }

    @SuppressWarnings("unchecked")
    private String mergeMeasurements(String oldJson, String newJson) {
        try {
            Map<String, Object> oldMap = (oldJson == null || oldJson.isBlank())
                    ? new LinkedHashMap<>()
                    : MAPPER.readValue(oldJson, LinkedHashMap.class);
            Map<String, Object> newMap = (newJson == null || newJson.isBlank())
                    ? new LinkedHashMap<>()
                    : MAPPER.readValue(newJson, LinkedHashMap.class);

            Object oldNotes = oldMap.get("notes");
            boolean oldHasNote = oldNotes != null && !oldNotes.toString().isBlank();

            Map<String, Object> merged = new LinkedHashMap<>(oldMap);
            for (Map.Entry<String, Object> e : newMap.entrySet()) {
                String key = e.getKey();
                Object v = e.getValue();
                boolean blank = (v == null || v.toString().isBlank());

                if ("notes".equals(key)) {
                    if (!oldHasNote && !blank) merged.put("notes", v);
                    continue;
                }
                if (!blank) merged.put(key, v);
            }
            if (oldHasNote) merged.put("notes", oldNotes);

            return MAPPER.writeValueAsString(merged);
        } catch (Exception ex) {
            return (oldJson != null && !oldJson.isBlank()) ? oldJson : newJson;
        }
    }

    public List<Customer> allCustomers() { return customerRepo.findAll(); }

    public Optional<Customer> findById(Long id) { return customerRepo.findById(id); }

    public Optional<Customer> findByMobile(String mobile) { return customerRepo.findByMobile(mobile); }

    public Optional<Customer> updateMeasurements(Long id, String measurements) {
        return customerRepo.findById(id).map(c -> {
            c.setMeasurements(measurements);
            return customerRepo.save(c);
        });
    }

    public long totalCustomers() { return customerRepo.count(); }
}