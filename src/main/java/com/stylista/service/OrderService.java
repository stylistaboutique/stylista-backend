package com.stylista.service;

import com.stylista.model.Order;
import com.stylista.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final CashbackService cashbackService;

    public OrderService(OrderRepository orderRepo, CashbackService cashbackService) {
        this.orderRepo = orderRepo;
        this.cashbackService = cashbackService;
    }

    // Persist a modified order entity directly (used by updateOrder wallet recalc)
    public Order saveOrder(Order order) {
        return orderRepo.save(order);
    }

    public Order createOrder(Order order) {
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        Order saved = orderRepo.save(order);
        grantCashbackIfDelivered(saved);   // in case it's created already delivered
        return saved;
    }

    // Admin default list = NOT deleted
    public List<Order> allOrdersByPriority() {
        return orderRepo.findAllSortedByDueDateNotDeleted();
    }

    /**
     * Flexible listing used by GET /orders. status=null means no status filter;
     * excludeDelivered=true drops DELIVERED rows regardless of status filter
     * (only matters when status is also null, since a specific status already narrows it).
     */
    public List<Order> listFiltered(boolean includeDeleted, Order.Status status, boolean excludeDelivered) {
        return orderRepo.findFiltered(includeDeleted, status, excludeDelivered, Order.Status.DELIVERED);
    }
    // Admin "Show deleted" = everything
    public List<Order> allOrdersIncludingDeleted() {
        return orderRepo.findAllSortedByDueDate();
    }
    // Customer-facing / customer detail = NOT deleted
    public List<Order> ordersForCustomer(Long customerId) {
        return orderRepo.findByCustomerIdAndDeletedFalseOrderByDueDateAsc(customerId);
    }
    public List<Order> ordersForTailor(Long tailorId) {
        return orderRepo.findByTailorIdOrderByDueDateAsc(tailorId);
    }
    public Optional<Order> findById(Long id) { return orderRepo.findById(id); }

    public Optional<Order> updateOrder(Long id, Order patch) {
        return orderRepo.findById(id).map(existing -> {
            if (patch.getStatus() != null)             existing.setStatus(patch.getStatus());
            if (patch.getProductType() != null)        existing.setProductType(patch.getProductType());
            if (patch.getProductDescription() != null) existing.setProductDescription(patch.getProductDescription());
            if (patch.getDueDate() != null)            existing.setDueDate(patch.getDueDate());
            if (patch.getExpectedPrice() != null)      existing.setExpectedPrice(patch.getExpectedPrice());
            if (patch.getAdvancePaid() != null)        existing.setAdvancePaid(patch.getAdvancePaid());
            if (patch.getNotes() != null)              existing.setNotes(patch.getNotes());
            if (patch.getTailorId() != null)           existing.setTailorId(patch.getTailorId());
            existing.setUpdatedAt(LocalDateTime.now());
            Order saved = orderRepo.save(existing);
            grantCashbackIfDelivered(saved);   // grant on the DELIVERED transition
            return saved;
        });
    }

    /** Grants pending cashback the first time an order is DELIVERED. Idempotent. */
    public void grantCashbackIfDelivered(Order order) {
        if (order.getStatus() != Order.Status.DELIVERED) return;
        if (order.isCashbackGranted()) return;

        Integer pct  = order.getPendingCashbackPercent();
        Integer amt  = order.getPendingCashbackAmount();
        Integer days = order.getPendingExpiryDays();
        boolean hasReward = (amt != null && amt > 0) || (pct != null && pct > 0);
        if (hasReward) {
            cashbackService.assignCashback(order.getCustomerId(), order.getId(),
                    pct, amt, days, "Auto-granted on delivery of order #" + order.getId());
        }
        order.setCashbackGranted(true);
        orderRepo.save(order);
    }

    /** #3 Soft delete: hide from customer, refund any applied balance, drop earned cashback. */
    public boolean softDelete(Long id) {
        return orderRepo.findById(id).map(o -> {
            int applied = o.getAppliedCashbackBalance() != null ? o.getAppliedCashbackBalance() : 0;
            if (applied > 0) cashbackService.refundBalance(o.getCustomerId(), applied);
            cashbackService.removeCashbackForOrder(o.getId());
            o.setDeleted(true);
            o.setCashbackGranted(false);
            orderRepo.save(o);
            return true;
        }).orElse(false);
    }

    public boolean restore(Long id) {
        return orderRepo.findById(id).map(o -> { o.setDeleted(false); orderRepo.save(o); return true; }).orElse(false);
    }

    public long totalOrders() { return orderRepo.count(); }
    public long activeOrders() {
        return orderRepo.findAll().stream()
                .filter(o -> !o.isDeleted() && o.getStatus() != Order.Status.DELIVERED)
                .count();
    }
}
