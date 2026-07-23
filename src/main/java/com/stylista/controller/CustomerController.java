package com.stylista.controller;

import com.stylista.model.CashbackAssignment;
import com.stylista.service.CashbackService;
import com.stylista.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Public API — no auth required.
 * Customers enter their mobile number to view cashback balance + history.
 * No OTP verification — open by design per requirements.
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

    /**
     * GET /api/customer/cashback?mobile=9876543210
     *
     * Returns:
     * {
     *   found: true/false,
     *   customer_name: "Priya",
     *   live_balance: 400,       ← sum of active (non-expired, non-redeemed) cashback amounts
     *   cashbacks: [             ← full history oldest-first
     *     { id, cashback_percent, cashback_amount, assigned_at, expires_at,
     *       is_redeemed, is_expired, is_active }
     *   ]
     * }
     */
    @GetMapping("/cashback")
    public ResponseEntity<Map<String, Object>> myCashback(@RequestParam String mobile) {
        String clean = mobile.replaceAll("\\D", "");
        var customerOpt = customerService.findByMobile(clean);

        if (customerOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "found", false,
                    "message", "No account found for this number. Visit us to register!",
                    "cashbacks", List.of(),
                    "live_balance", 0
            ));
        }

        var customer = customerOpt.get();
        List<CashbackAssignment> history = cashbackService.getCashbacksForCustomer(customer.getId());

        int liveBalance = history.stream()
                .filter(CashbackAssignment::isActive)
                .mapToInt(cb -> cb.getCashbackAmount() != null ? cb.getCashbackAmount() : 0)
                .sum();

        List<Map<String, Object>> cashbacks = history.stream().map(cb -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",               cb.getId());
            m.put("cashback_percent", cb.getCashbackPercent());
            m.put("cashback_amount",  cb.getCashbackAmount());
            m.put("assigned_at",      cb.getAssignedAt().toString());
            m.put("expires_at",       cb.getExpiresAt().toString());
            m.put("is_redeemed",      cb.isRedeemed());
            m.put("is_expired",       cb.isExpired());
            m.put("is_active",        cb.isActive());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("found",         true);
        res.put("customer_name", customer.getName());
        res.put("live_balance",  liveBalance);
        res.put("cashbacks",     cashbacks);
        return ResponseEntity.ok(res);
    }
}
