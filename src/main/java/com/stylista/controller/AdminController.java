package com.stylista.controller;

import com.stylista.model.*;
import com.stylista.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CustomerService customerService;
    private final OrderService orderService;
    private final CashbackService cashbackService;
    private final TailorService tailorService;

    @Value("${app.admin.username}")
    private String adminUsername;
    @Value("${app.admin.password}")
    private String adminPassword;
    @Value("${app.admin.token-secret}")
    private String tokenSecret;

    public AdminController(CustomerService customerService,
                           OrderService orderService,
                           CashbackService cashbackService,
                           TailorService tailorService) {
        this.customerService = customerService;
        this.orderService = orderService;
        this.cashbackService = cashbackService;
        this.tailorService = tailorService;
    }

    // ===== Auth helpers =====
    private boolean isAuthorized(String authHeader) {
        return authHeader != null && authHeader.equals("Bearer " + tokenSecret);
    }
    private ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("message", "Unauthorized."));
    }
    private ResponseEntity<Map<String, Object>> notFound(String msg) {
        return ResponseEntity.status(404).body(Map.of("message", msg));
    }

    // ==================== AUTH ====================

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String u = body.getOrDefault("username", "").trim();
        String p = body.getOrDefault("password", "");
        if (adminUsername.equals(u) && adminPassword.equals(p)) {
            return ResponseEntity.ok(Map.of("token", tokenSecret));
        }
        return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials."));
    }

    // ==================== STATS ====================

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAuthorized(auth)) return unauthorized();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalCustomers",  customerService.totalCustomers());
        s.put("totalOrders",     orderService.totalOrders());
        s.put("activeOrders",    orderService.activeOrders());
        s.put("activeCashbacks", cashbackService.countActive());
        s.put("expiringSoon",    cashbackService.countExpiringSoon());
        s.put("redeemed",        cashbackService.countRedeemed());
        return ResponseEntity.ok(s);
    }

    // ==================== CUSTOMERS ====================

    @GetMapping("/customers")
    public ResponseEntity<Map<String, Object>> listCustomers(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAuthorized(auth)) return unauthorized();
        List<Map<String, Object>> list = customerService.allCustomers().stream()
                .map(this::customerMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("customers", list));
    }

    @PostMapping("/customers")
    public ResponseEntity<Map<String, Object>> addCustomer(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        if (!isAuthorized(auth)) return unauthorized();

        String name   = str(body.get("name"));
        String mobile = str(body.get("mobile"));
        if (name.isBlank() || mobile.length() != 10)
            return ResponseEntity.badRequest().body(Map.of("message", "Name and 10-digit mobile required."));

        String measurements = str(body.get("measurements")); // JSON string or empty
        Customer c = customerService.addOrUpdate(name, mobile, measurements.isEmpty() ? null : measurements);
        return ResponseEntity.ok(Map.of("message", "Customer saved.", "customer", customerMap(c)));
    }

    @PutMapping("/customers/{id}/measurements")
    public ResponseEntity<Map<String, Object>> updateMeasurements(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (!isAuthorized(auth)) return unauthorized();
        String measurements = str(body.get("measurements"));
        return customerService.updateMeasurements(id, measurements)
                .map(c -> ResponseEntity.ok(Map.of("message", "Measurements updated.", "customer", customerMap(c))))
                .orElseGet(() -> notFound("Customer not found."));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<Map<String, Object>> getCustomer(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        if (!isAuthorized(auth)) return unauthorized();
        return customerService.findById(id)
                .map(c -> {
                    Map<String, Object> res = new LinkedHashMap<>();
                    res.putAll(customerMap(c));
                    res.put("orders", orderService.ordersForCustomer(id).stream()
                            .map(this::orderMap).collect(Collectors.toList()));
                    res.put("cashbacks", cashbackService.getCashbacksForCustomer(id).stream()
                            .map(this::cashbackMap).collect(Collectors.toList()));
                    res.put("live_balance", cashbackService.getLiveBalance(id));
                    return ResponseEntity.ok(res);
                })
                .orElseGet(() -> notFound("Customer not found."));
    }

    // ==================== ORDERS ====================

    /**
     * GET /api/admin/orders
     * Returns all orders sorted by due_date ASC (overdue first, no-date last).
     * Each order includes customer name + mobile for display.
     */
    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAuthorized(auth)) return unauthorized();
        List<Map<String, Object>> list = orderService.allOrdersByPriority().stream()
                .map(this::orderMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("orders", list));
    }

    /**
     * POST /api/admin/orders
     * Body: { customer_id, tailor_id?, product_type, product_description,
     *          due_date (yyyy-MM-dd), expected_price, advance_paid,
     *          cashback_percent (default 20), cashback_amount,
     *          expiry_days (default 60), notes,
     *          assign_cashback: true/false }
     */
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        if (!isAuthorized(auth)) return unauthorized();

        Long customerId = longOrNull(body.get("customer_id"));
        if (customerId == null)
            return ResponseEntity.badRequest().body(Map.of("message", "customer_id is required."));
        if (customerService.findById(customerId).isEmpty())
            return notFound("Customer not found.");

        Order order = new Order();
        order.setCustomerId(customerId);
        order.setTailorId(longOrNull(body.get("tailor_id")));

        String pt = str(body.get("product_type")).toUpperCase();
        try { order.setProductType(Order.ProductType.valueOf(pt)); }
        catch (Exception e) { order.setProductType(Order.ProductType.OTHER); }

        order.setProductDescription(str(body.get("product_description")));
        String dueDateStr = str(body.get("due_date"));
        if (!dueDateStr.isBlank()) order.setDueDate(LocalDate.parse(dueDateStr));
        order.setExpectedPrice(intOrNull(body.get("expected_price")));
        order.setAdvancePaid(intOrDefault(body.get("advance_paid"), 0));
        order.setStatus(Order.Status.ORDER_RECEIVED);
        order.setNotes(str(body.get("notes")));

        Order saved = orderService.createOrder(order);

        // Optionally assign cashback at order creation
        boolean assignCashback = Boolean.parseBoolean(str(body.get("assign_cashback")));
        CashbackAssignment cb = null;
        if (assignCashback) {
            Integer cbAmount = intOrNull(body.get("cashback_amount"));
            if (cbAmount != null && cbAmount > 0) {
                cb = cashbackService.assignCashback(
                        customerId, saved.getId(),
                        intOrNull(body.get("cashback_percent")),
                        cbAmount,
                        intOrNull(body.get("expiry_days")),
                        "Order #" + saved.getId());
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("message", "Order created.");
        res.put("order", orderMap(saved));
        if (cb != null) res.put("cashback", cashbackMap(cb));
        return ResponseEntity.ok(res);
    }

    /**
     * PATCH /api/admin/orders/{id}
     * Use to update status, price, advance, notes, tailor, due_date.
     * Admin uses this to manually move status: ORDER_RECEIVED → DELIVERED.
     */
    @PatchMapping("/orders/{id}")
    public ResponseEntity<Map<String, Object>> updateOrder(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (!isAuthorized(auth)) return unauthorized();

        Order patch = new Order();
        String statusStr = str(body.get("status")).toUpperCase();
        if (!statusStr.isBlank()) {
            try { patch.setStatus(Order.Status.valueOf(statusStr)); }
            catch (Exception e) { /* ignore invalid status */ }
        }
        String pt = str(body.get("product_type")).toUpperCase();
        if (!pt.isBlank()) {
            try { patch.setProductType(Order.ProductType.valueOf(pt)); }
            catch (Exception e) { /* ignore */ }
        }
        String pd = str(body.get("product_description"));
        if (!pd.isBlank()) patch.setProductDescription(pd);
        String dd = str(body.get("due_date"));
        if (!dd.isBlank()) patch.setDueDate(LocalDate.parse(dd));
        patch.setExpectedPrice(intOrNull(body.get("expected_price")));
        patch.setAdvancePaid(intOrNull(body.get("advance_paid")));
        String notes = str(body.get("notes"));
        if (!notes.isBlank()) patch.setNotes(notes);
        patch.setTailorId(longOrNull(body.get("tailor_id")));

        return orderService.updateOrder(id, patch)
                .map(o -> ResponseEntity.ok(Map.of("message", "Order updated.", "order", orderMap(o))))
                .orElseGet(() -> notFound("Order not found."));
    }

    // ==================== CASHBACKS ====================

    @GetMapping("/cashbacks")
    public ResponseEntity<Map<String, Object>> listCashbacks(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAuthorized(auth)) return unauthorized();
        List<Map<String, Object>> list = cashbackService.getAllCashbacks().stream()
                .map(this::cashbackMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("cashbacks", list));
    }

    @PostMapping("/cashback")
    public ResponseEntity<Map<String, Object>> assignCashback(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        if (!isAuthorized(auth)) return unauthorized();

        Long customerId = longOrNull(body.get("customer_id"));
        Integer amount  = intOrNull(body.get("cashback_amount"));
        if (customerId == null || amount == null || amount <= 0)
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "customer_id and cashback_amount required."));

        CashbackAssignment cb = cashbackService.assignCashback(
                customerId,
                longOrNull(body.get("order_id")),
                intOrNull(body.get("cashback_percent")),
                amount,
                intOrNull(body.get("expiry_days")),
                str(body.get("notes")));
        return ResponseEntity.ok(Map.of("message", "Cashback assigned.", "cashback", cashbackMap(cb)));
    }

    @PostMapping("/cashback/{id}/redeem")
    public ResponseEntity<Map<String, Object>> redeem(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        if (!isAuthorized(auth)) return unauthorized();
        boolean ok = cashbackService.markRedeemed(id);
        if (ok) return ResponseEntity.ok(Map.of("message", "Cashback marked as redeemed."));
        return notFound("Cashback not found.");
    }

    // ==================== TAILORS ====================

    @GetMapping("/tailors")
    public ResponseEntity<Map<String, Object>> listTailors(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (!isAuthorized(auth)) return unauthorized();
        List<Map<String, Object>> list = tailorService.allTailors().stream()
                .map(this::tailorMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("tailors", list));
    }

    @PostMapping("/tailors")
    public ResponseEntity<Map<String, Object>> addTailor(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody Map<String, Object> body) {
        if (!isAuthorized(auth)) return unauthorized();

        String name   = str(body.get("name"));
        String mobile = str(body.get("mobile"));
        if (name.isBlank() || mobile.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Name and mobile required."));

        Tailor t = new Tailor();
        t.setName(name);
        t.setMobile(mobile);
        t.setBoutiqueName(str(body.get("boutique_name")));
        t.setCity(str(body.get("city")));
        t.setSpecialization(str(body.get("specialization")));
        t.setActive(true);

        Tailor saved = tailorService.addTailor(t);
        return ResponseEntity.ok(Map.of("message", "Tailor added.", "tailor", tailorMap(saved)));
    }

    @PatchMapping("/tailors/{id}")
    public ResponseEntity<Map<String, Object>> updateTailor(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        if (!isAuthorized(auth)) return unauthorized();
        Tailor patch = new Tailor();
        patch.setName(nullIfBlank(str(body.get("name"))));
        patch.setMobile(nullIfBlank(str(body.get("mobile"))));
        patch.setBoutiqueName(nullIfBlank(str(body.get("boutique_name"))));
        patch.setCity(nullIfBlank(str(body.get("city"))));
        patch.setSpecialization(nullIfBlank(str(body.get("specialization"))));
        Object activeObj = body.get("active");
        patch.setActive(activeObj == null || Boolean.parseBoolean(activeObj.toString()));

        return tailorService.updateTailor(id, patch)
                .map(t -> ResponseEntity.ok(Map.of("message", "Tailor updated.", "tailor", tailorMap(t))))
                .orElseGet(() -> notFound("Tailor not found."));
    }

    @DeleteMapping("/tailors/{id}")
    public ResponseEntity<Map<String, Object>> deactivateTailor(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {
        if (!isAuthorized(auth)) return unauthorized();
        boolean ok = tailorService.deactivateTailor(id);
        if (ok) return ResponseEntity.ok(Map.of("message", "Tailor deactivated."));
        return notFound("Tailor not found.");
    }

    // ==================== Serialization helpers ====================

    private Map<String, Object> customerMap(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           c.getId());
        m.put("name",         c.getName());
        m.put("mobile",       c.getMobile());
        m.put("measurements", c.getMeasurements());
        m.put("created_at",   c.getCreatedAt().toString());
        return m;
    }

    private Map<String, Object> orderMap(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",                  o.getId());
        m.put("customer_id",         o.getCustomerId());
        m.put("tailor_id",           o.getTailorId());
        m.put("product_type",        o.getProductType().name());
        m.put("product_description", o.getProductDescription());
        m.put("due_date",            o.getDueDate() != null ? o.getDueDate().toString() : null);
        m.put("expected_price",      o.getExpectedPrice());
        m.put("advance_paid",        o.getAdvancePaid());
        m.put("remaining",           o.getRemaining());
        m.put("status",              o.getStatus().name());
        m.put("notes",               o.getNotes());
        m.put("created_at",          o.getCreatedAt().toString());
        m.put("updated_at",          o.getUpdatedAt() != null ? o.getUpdatedAt().toString() : null);

        // Attach customer name + mobile for list display
        customerService.findById(o.getCustomerId()).ifPresent(c -> {
            m.put("customer_name",   c.getName());
            m.put("customer_mobile", c.getMobile());
        });
        // Is the order overdue?
        boolean overdue = o.getDueDate() != null
                && o.getDueDate().isBefore(LocalDate.now())
                && o.getStatus() != Order.Status.DELIVERED;
        m.put("overdue", overdue);
        return m;
    }

    private Map<String, Object> cashbackMap(CashbackAssignment cb) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",               cb.getId());
        m.put("customer_id",      cb.getCustomerId());
        m.put("order_id",         cb.getOrderId());
        m.put("cashback_percent", cb.getCashbackPercent());
        m.put("cashback_amount",  cb.getCashbackAmount());
        m.put("assigned_at",      cb.getAssignedAt().toString());
        m.put("expires_at",       cb.getExpiresAt().toString());
        m.put("is_redeemed",      cb.isRedeemed());
        m.put("is_expired",       cb.isExpired());
        m.put("is_active",        cb.isActive());
        m.put("notes",            cb.getNotes());

        // Attach customer name + mobile
        customerService.findById(cb.getCustomerId()).ifPresent(c -> {
            m.put("customer_name",   c.getName());
            m.put("customer_mobile", c.getMobile());
        });
        return m;
    }

    private Map<String, Object> tailorMap(Tailor t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",             t.getId());
        m.put("name",           t.getName());
        m.put("mobile",         t.getMobile());
        m.put("boutique_name",  t.getBoutiqueName());
        m.put("city",           t.getCity());
        m.put("specialization", t.getSpecialization());
        m.put("active",         t.isActive());
        m.put("created_at",     t.getCreatedAt().toString());
        return m;
    }

    // ==================== Parsing helpers ====================

    private static String str(Object o) { return o == null ? "" : o.toString().trim(); }
    private static String nullIfBlank(String s) { return (s == null || s.isBlank()) ? null : s; }

    private static Integer intOrNull(Object o) {
        if (o == null) return null;
        try { String s = o.toString().trim(); return s.isEmpty() ? null : (int) Double.parseDouble(s); }
        catch (Exception e) { return null; }
    }
    private static int intOrDefault(Object o, int def) {
        Integer v = intOrNull(o); return v != null ? v : def;
    }
    private static Long longOrNull(Object o) {
        if (o == null) return null;
        try { String s = o.toString().trim(); return s.isEmpty() ? null : Long.parseLong(s.replace(".0","")); }
        catch (Exception e) { return null; }
    }
}
