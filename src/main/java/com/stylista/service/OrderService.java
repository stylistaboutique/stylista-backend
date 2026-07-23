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

    public OrderService(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    public Order createOrder(Order order) {
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepo.save(order);
    }

    /**
     * Returns ALL orders sorted by due_date ascending.
     * Overdue orders (dueDate < today) bubble to the very top.
     * Orders with no due_date go to the bottom.
     */
    public List<Order> allOrdersByPriority() {
        return orderRepo.findAllSortedByDueDate();
    }

    public List<Order> ordersForCustomer(Long customerId) {
        return orderRepo.findByCustomerIdOrderByDueDateAsc(customerId);
    }

    public List<Order> ordersForTailor(Long tailorId) {
        return orderRepo.findByTailorIdOrderByDueDateAsc(tailorId);
    }

    public Optional<Order> findById(Long id) {
        return orderRepo.findById(id);
    }

    /**
     * Update only the fields provided (status update, price update, etc.)
     */
    public Optional<Order> updateOrder(Long id, Order patch) {
        return orderRepo.findById(id).map(existing -> {
            if (patch.getStatus() != null)            existing.setStatus(patch.getStatus());
            if (patch.getProductType() != null)       existing.setProductType(patch.getProductType());
            if (patch.getProductDescription() != null) existing.setProductDescription(patch.getProductDescription());
            if (patch.getDueDate() != null)           existing.setDueDate(patch.getDueDate());
            if (patch.getExpectedPrice() != null)     existing.setExpectedPrice(patch.getExpectedPrice());
            if (patch.getAdvancePaid() != null)       existing.setAdvancePaid(patch.getAdvancePaid());
            if (patch.getNotes() != null)             existing.setNotes(patch.getNotes());
            if (patch.getTailorId() != null)          existing.setTailorId(patch.getTailorId());
            existing.setUpdatedAt(LocalDateTime.now());
            return orderRepo.save(existing);
        });
    }

    public boolean deleteOrder(Long id) {
        if (!orderRepo.existsById(id)) return false;
        orderRepo.deleteById(id);
        return true;
    }

    // ===== Stats =====
    public long totalOrders() { return orderRepo.count(); }

    public long activeOrders() {
        return orderRepo.findAll().stream()
                .filter(o -> o.getStatus() != Order.Status.DELIVERED)
                .count();
    }
}
