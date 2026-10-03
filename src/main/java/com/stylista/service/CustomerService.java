package com.stylista.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylista.model.Customer;
import com.stylista.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private final CustomerRepository customerRepo;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public CustomerService(CustomerRepository customerRepo) {
        this.customerRepo = customerRepo;
    }

    /**
     * Add or update keyed on (normalized-name + mobile).
     * Normalization = trim + collapse internal whitespace + title-case first letter of each word.
     * This prevents duplicate-insert 500s when the same name is typed with slight
     * whitespace/case variation (e.g. "ABC New " vs "abc new").
     *
     * A genuinely NEW name on an existing mobile creates a SEPARATE customer row.
     */
    public Customer addOrUpdate(String name, String mobile, String measurements) {
        String normalizedName = normalizeName(name);
        String normalizedMobile = mobile == null ? "" : mobile.trim();

        Optional<Customer> existing = customerRepo.findByNameIgnoreCaseAndMobile(
                normalizedName, normalizedMobile);

        Customer c = existing.orElseGet(() -> {
            Customer fresh = new Customer();
            fresh.setMobile(normalizedMobile);
            return fresh;
        });

        // Always store the normalized name so DB stays clean
        c.setName(normalizedName);

        if (measurements != null) {
            if (existing.isPresent()) {
                c.setMeasurements(mergeMeasurements(c.getMeasurements(), measurements));
            } else {
                c.setMeasurements(measurements);
            }
        }
        return customerRepo.save(c);
    }

    /** Trim, collapse internal spaces, capitalize each word. */
    public static String normalizeName(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) return trimmed;
        StringBuilder sb = new StringBuilder();
        for (String word : trimmed.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private String mergeMeasurements(String oldJson, String newJson) {
        try {
            Map<String, Object> oldMap = (oldJson == null || oldJson.isBlank())
                    ? new LinkedHashMap<>() : MAPPER.readValue(oldJson, LinkedHashMap.class);
            Map<String, Object> newMap = (newJson == null || newJson.isBlank())
                    ? new LinkedHashMap<>() : MAPPER.readValue(newJson, LinkedHashMap.class);

            Object oldNotes = oldMap.get("notes");
            boolean oldHasNote = oldNotes != null && !oldNotes.toString().isBlank();

            Map<String, Object> merged = new LinkedHashMap<>(oldMap);
            for (Map.Entry<String, Object> e : newMap.entrySet()) {
                String key = e.getKey(); Object v = e.getValue();
                boolean blank = (v == null || v.toString().isBlank());
                if ("notes".equals(key)) { if (!oldHasNote && !blank) merged.put("notes", v); continue; }
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
    public List<Customer> findAllByMobile(String mobile) { return customerRepo.findByMobile(mobile); }

    /**
     * PERF: batch lookup by id -- used by the Orders / Cashbacks admin lists to
     * resolve customer_name/mobile for a whole PAGE of rows in ONE query instead
     * of one findById() call per row (the N+1 this whole change set exists to fix).
     */
    public List<Customer> findAllByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return customerRepo.findAllById(ids);
    }

    /** PERF: batch lookup by a set of mobiles -- see CashbackService.getLiveBalancesForCustomerPage. */
    public List<Customer> findAllByMobiles(Collection<String> mobiles) {
        if (mobiles == null || mobiles.isEmpty()) return List.of();
        return customerRepo.findByMobileIn(mobiles);
    }

    /**
     * PERF + SEARCH: paginated customer list for GET /api/admin/customers, with
     * an optional search term matched against name/mobile -- applied in SQL
     * before the page is sliced (not after, client-side, against only the
     * current page's rows -- that was the bug where a match on page 2 showed
     * nothing while viewing page 1).
     * Sorted by id so paging is stable (no duplicate/skipped rows across pages
     * as new customers get added between page fetches).
     * q may be null/blank for "no search" (normal unfiltered browsing).
     *
     * NOTE: this is the "default" sort. For sort-by-balance, AdminController uses
     * {@link #findAllFiltered} instead, since balance is a derived/pooled value
     * (not a DB column) and has to be computed before it can be sorted on.
     */
    public Page<Customer> listPaged(String q, int page, int size) {
        String qLike = (q == null || q.isBlank()) ? null : "%" + q.trim().toLowerCase() + "%";
        return customerRepo.searchPage(qLike, PageRequest.of(page, size, Sort.by("id").ascending()));
    }

    /**
     * SORT-BY-BALANCE support: full (unpaginated) customer list, optionally
     * filtered by q (name/mobile contains, case-insensitive). Cashback balance
     * is pooled by mobile number and summed across cashback_assignments at read
     * time -- it isn't a persisted column on customers, so it can't be sorted
     * with a SQL ORDER BY without denormalizing the schema. Instead,
     * AdminController pulls the full (filtered) list via this method, computes
     * every balance in 2 bulk queries total (not per-row -- see
     * CashbackService.getLiveBalancesForCustomerPage), sorts by balance in Java,
     * and paginates the already-sorted list. Perfectly fine at boutique scale
     * (hundreds to low thousands of customers); would need revisiting only if
     * this ever grew to tens of thousands of rows.
     */
    public List<Customer> findAllFiltered(String q) {
        List<Customer> all = customerRepo.findAll();
        if (q == null || q.isBlank()) return all;
        String needle = q.trim().toLowerCase();
        return all.stream()
                .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(needle))
                          || (c.getMobile() != null && c.getMobile().contains(needle)))
                .collect(Collectors.toList());
    }

    public Optional<Customer> updateMeasurements(Long id, String measurements) {
        return customerRepo.findById(id).map(c -> {
            c.setMeasurements(measurements); return customerRepo.save(c);
        });
    }

    public long totalCustomers() { return customerRepo.count(); }
}
