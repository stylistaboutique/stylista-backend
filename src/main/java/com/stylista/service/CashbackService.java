package com.stylista.service;

import com.stylista.model.CashbackAssignment;
import com.stylista.model.Customer;
import com.stylista.repository.CashbackRepository;
import com.stylista.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CashbackService {

    private final CashbackRepository cashbackRepo;
    private final CustomerRepository customerRepo;
    private final NotificationService notifications;

    @Value("${app.cashback.default-percent:20}")
    private int defaultPercent;

    @Value("${app.cashback.default-expiry-days:60}")
    private int defaultExpiryDays;

    public CashbackService(CashbackRepository cashbackRepo,
                           CustomerRepository customerRepo,
                           NotificationService notifications) {
        this.cashbackRepo = cashbackRepo;
        this.customerRepo = customerRepo;
        this.notifications = notifications;
    }

    /**
     * Assign cashback to a customer.
     * Expiry rule: each cashback expires independently at assignedAt + expiryDays.
     * Only cashbacks whose expiresAt < NOW are considered expired.
     * Multiple cashbacks are allowed — each has its own expiry.
     */
    public CashbackAssignment assignCashback(Long customerId, Long orderId,
                                              Integer percent, Integer amount,
                                              Integer expiryDays, String notes) {
        int pct  = (percent != null && percent > 0)    ? percent    : defaultPercent;
        int days = (expiryDays != null && expiryDays > 0) ? expiryDays : defaultExpiryDays;

        CashbackAssignment cb = new CashbackAssignment();
        cb.setCustomerId(customerId);
        cb.setOrderId(orderId);
        cb.setCashbackPercent(pct);
        cb.setCashbackAmount(amount);
        cb.setAssignedAt(LocalDateTime.now());
        cb.setExpiresAt(LocalDateTime.now().plusDays(days));
        cb.setNotes(notes);

        CashbackAssignment saved = cashbackRepo.save(cb);

        customerRepo.findById(customerId).ifPresent(c ->
            notifications.sendCashbackThankYou(c.getName(), c.getMobile(),
                    pct, amount, saved.getExpiresAt().toLocalDate().toString()));

        return saved;
    }

    /**
     * All cashbacks for a customer, oldest first.
     * The frontend shows history + computes live balance from is_redeemed + expires_at.
     *
     * Expiry logic (per requirement):
     * - Each cashback expires on its own expiresAt date (independent).
     * - "Only oldest ones > 60 days should be expired" means:
     *   if a cashback's expiresAt is in the past, it is expired — the model
     *   already encodes this because expiresAt = assignedAt + expiryDays.
     *   So a cashback assigned 70 days ago (with 60-day expiry) is expired.
     *   A cashback assigned 30 days ago (with 60-day expiry) is still active.
     * - Live balance = sum of cashbackAmount where isActive() == true.
     */
    public List<CashbackAssignment> getCashbacksForCustomer(Long customerId) {
        return cashbackRepo.findByCustomerIdOrderByAssignedAtAsc(customerId);
    }

    public List<CashbackAssignment> getCashbacksForMobile(String mobile) {
        return customerRepo.findByMobile(mobile)
                .map(c -> getCashbacksForCustomer(c.getId()))
                .orElseGet(List::of);
    }

    /** Live balance = sum of all active (not redeemed + not expired) cashback amounts */
    public int getLiveBalance(Long customerId) {
        return getCashbacksForCustomer(customerId).stream()
                .filter(CashbackAssignment::isActive)
                .mapToInt(cb -> cb.getCashbackAmount() != null ? cb.getCashbackAmount() : 0)
                .sum();
    }

    public boolean markRedeemed(Long cashbackId) {
        return cashbackRepo.findById(cashbackId).map(cb -> {
            cb.setRedeemed(true);
            cashbackRepo.save(cb);
            return true;
        }).orElse(false);
    }

    public List<CashbackAssignment> getAllCashbacks() {
        return cashbackRepo.findAll();
    }

    // ===== Stats =====
    public long countActive()  { return cashbackRepo.countByRedeemedFalseAndExpiresAtAfter(LocalDateTime.now()); }
    public long countRedeemed(){ return cashbackRepo.countByRedeemedTrue(); }
    public long countExpiringSoon() {
        LocalDateTime now = LocalDateTime.now();
        return cashbackRepo.findExpiringSoon(now, now.plusDays(7)).size();
    }
}
