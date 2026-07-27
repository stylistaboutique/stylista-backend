package com.stylista.controller;

import com.stylista.model.CashbackAssignment;
import com.stylista.model.Customer;
import com.stylista.service.CashbackService;
import com.stylista.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Public API - no auth required.
 * Customers enter their mobile number to view cashback balance + history.
 * A mobile can now belong to several people (name+mobile uniqueness); this
 * endpoint aggregates every person on that number.
 */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final CashbackService cashbackService;
    private final CustomerService customerService;

    public CustomerController(CashbackService cashbackService, CustomerService customerService) {
        this.cashbackService = cashbackService;
        this.customerService = customerService;
    }

    @GetMapping("/cashback")
    public ResponseEntity<Map<String, Object>> myCashback(@RequestParam String mobile) {
        String clean = mobile.replaceAll("\\D", "");
        List<Customer> customers = customerService.findAllByMobile(clean);

        if (customers.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "No account found for this number. Visit us to register!",
                    "cashbacks", List.of(),
                    "live_balance", 0
            ));
        }

        // Aggregate cashback across every person on this mobile.
        List<CashbackAssignment> history = new ArrayList<>();
        int liveBalance = 0;
        for (Customer c : customers) {
            history.addAll(cashbackService.getCashbacksForCustomer(c.getId()));
            liveBalance += cashbackService.getLiveBalance(c.getId());
        }
        // Oldest first for display
        history.sort(Comparator.comparing(CashbackAssignment::getAssignedAt));

        List<Map<String, Object>> cashbacks = history.stream().map(cb -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",               cb.getId());
            m.put("cashback_percent", cb.getCashbackPercent());
            m.put("cashback_amount",  cb.getCashbackAmount());
            m.put("remaining_amount", cb.getRemainingAmount());
            m.put("assigned_at",      cb.getAssignedAt().toString());
            m.put("expires_at",       cb.getExpiresAt().toString());
            m.put("is_redeemed",      cb.isRedeemed());
            m.put("is_expired",       cb.isExpired());
            m.put("is_active",        cb.isActive());
            return m;
        }).collect(Collectors.toList());

        // Show the first name; if several share the number, indicate that.
        String displayName = customers.get(0).getName();
        if (customers.size() > 1) displayName = displayName + " +" + (customers.size() - 1) + " more";

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("found",         true);
        res.put("customer_name", displayName);
        res.put("live_balance",  liveBalance);
        res.put("cashbacks",     cashbacks);
        return ResponseEntity.ok(res);
    }
}
