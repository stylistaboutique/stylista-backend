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
 * Cashback pool is shared by mobile number across all names.
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
                    "found",       false,
                    "message",     "No account found for this number. Visit us to register!",
                    "cashbacks",   List.of(),
                    "live_balance", 0
            ));
        }

        // Pool: all cashbacks across every name on this number
        List<CashbackAssignment> history = cashbackService.getCashbacksForMobile(clean);
        int liveBalance = cashbackService.getLiveBalanceForMobile(clean);

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

        String displayName = customers.get(0).getName();
        if (customers.size() > 1)
            displayName = displayName + " +" + (customers.size() - 1) + " more";

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("found",         true);
        res.put("customer_name", displayName);
        res.put("live_balance",  liveBalance);
        res.put("cashbacks",     cashbacks);
        return ResponseEntity.ok(res);
    }
}
