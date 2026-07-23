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
     *
     * Measurement rule (per requirement):
     * - For an EXISTING customer, the incoming measurements are MERGED onto the
     *   saved measurements: any measurement field present (non-blank) in the new
     *   payload overwrites the old value, so future measurement changes update.
     * - EXCEPTION: the "notes" field (the "Other notes" measurement) is PRESERVED
     *   from the existing record — the new payload does NOT overwrite it. This keeps
     *   long-lived notes intact across repeat orders.
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

    /**
     * Merge new measurement JSON onto old.
     * - New non-blank values overwrite old values.
     * - The "notes" (Other notes) field is always kept from the OLD record.
     * If either side is not valid JSON, we fall back gracefully.
     */
    @SuppressWarnings("unchecked")
    private String mergeMeasurements(String oldJson, String newJson) {
        try {
            Map<String, Object> oldMap = (oldJson == null || oldJson.isBlank())
                    ? new LinkedHashMap<>()
                    : MAPPER.readValue(oldJson, LinkedHashMap.class);
            Map<String, Object> newMap = (newJson == null || newJson.isBlank())
                    ? new LinkedHashMap<>()
                    : MAPPER.readValue(newJson, LinkedHashMap.class);

            Map<String, Object> merged = new LinkedHashMap<>(oldMap);
            for (Map.Entry<String, Object> e : newMap.entrySet()) {
                if ("notes".equals(e.getKey())) continue; // preserve Other notes from old
                Object v = e.getValue();
                if (v != null && !v.toString().isBlank()) {
                    merged.put(e.getKey(), v);
                }
            }
            // Ensure old notes survive even if old JSON lacked the key layout
            if (oldMap.containsKey("notes")) merged.put("notes", oldMap.get("notes"));

            return MAPPER.writeValueAsString(merged);
        } catch (Exception ex) {
            // If parsing fails, keep the old measurements to avoid data loss
            return (oldJson != null && !oldJson.isBlank()) ? oldJson : newJson;
        }
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

    /** Update measurements only (patch) — full overwrite from the customer detail page. */
    public Optional<Customer> updateMeasurements(Long id, String measurements) {
        return customerRepo.findById(id).map(c -> {
            c.setMeasurements(measurements);
            return customerRepo.save(c);
        });
    }

    public long totalCustomers() { return customerRepo.count(); }
}
