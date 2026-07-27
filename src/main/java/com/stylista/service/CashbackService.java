package com.stylista.service;

import com.stylista.model.CashbackAssignment;
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

    @Value("${app.cashback.default-percent:20}")     private int defaultPercent;
    @Value("${app.cashback.default-expiry-days:60}")  private int defaultExpiryDays;

    public CashbackService(CashbackRepository cashbackRepo,
                           CustomerRepository customerRepo,
                           NotificationService notifications) {
        this.cashbackRepo = cashbackRepo;
        this.customerRepo = customerRepo;
        this.notifications = notifications;
    }

    /** Ad-hoc assign (Cashbacks tab) AND the delivery-time grant both go through here. */
    public CashbackAssignment assignCashback(Long customerId, Long orderId,
                                             Integer percent, Integer amount,
                                             Integer expiryDays, String notes) {
        int pct  = (percent != null && percent > 0) ? percent : defaultPercent;
        int days = (expiryDays != null && expiryDays > 0) ? expiryDays : defaultExpiryDays;

        CashbackAssignment cb = new CashbackAssignment();
        cb.setCustomerId(customerId);
        cb.setOrderId(orderId);
        cb.setCashbackPercent(pct);
        cb.setCashbackAmount(amount);
        cb.setRemainingAmount(amount);   // #4 fully spendable at creation
        cb.setAssignedAt(LocalDateTime.now());
        cb.setExpiresAt(LocalDateTime.now().plusDays(days));
        cb.setNotes(notes);

        CashbackAssignment saved = cashbackRepo.save(cb);
        customerRepo.findById(customerId).ifPresent(c ->
            notifications.sendCashbackThankYou(c.getName(), c.getMobile(),
                    pct, amount, saved.getExpiresAt().toLocalDate().toString()));
        return saved;
    }

    public List<CashbackAssignment> getCashbacksForCustomer(Long customerId) {
        return cashbackRepo.findByCustomerIdOrderByAssignedAtAsc(customerId);
    }

    /**
     * #4 Live balance = sum of remaining_amount over ACTIVE cashbacks.
     * Expired / redeemed / fully-used drop out automatically.
     */
    public int getLiveBalance(Long customerId) {
        return getCashbacksForCustomer(customerId).stream()
                .filter(CashbackAssignment::isActive)
                .mapToInt(cb -> cb.getRemainingAmount() != null
                        ? cb.getRemainingAmount()
                        : (cb.getCashbackAmount() != null ? cb.getCashbackAmount() : 0))
                .sum();
    }

    /**
     * #4 Spend up to `amount` from a customer's balance, oldest-expiring first (FIFO).
     * Partially-used entries keep the leftover; fully-used entries are marked redeemed.
     * Returns how much was actually applied.
     */
    public int applyBalance(Long customerId, int amount) {
        if (amount <= 0) return 0;
        int toApply = amount, applied = 0;
        List<CashbackAssignment> pool = cashbackRepo.findByCustomerIdOrderByExpiresAtAsc(customerId);
        for (CashbackAssignment cb : pool) {
            if (toApply <= 0) break;
            if (!cb.isActive()) continue;
            int rem = cb.getRemainingAmount() != null ? cb.getRemainingAmount()
                    : (cb.getCashbackAmount() != null ? cb.getCashbackAmount() : 0);
            if (rem <= 0) continue;
            int take = Math.min(rem, toApply);
            cb.setRemainingAmount(rem - take);
            if (cb.getRemainingAmount() <= 0) cb.setRedeemed(true); // fully used
            cashbackRepo.save(cb);
            applied += take; toApply -= take;
        }
        return applied;
    }

    /** Reverse an applied amount back onto the customer's balance (used on order edit/delete). */
    public void refundBalance(Long customerId, int amount) {
        if (amount <= 0) return;
        int toRefund = amount;
        List<CashbackAssignment> pool = cashbackRepo.findByCustomerIdOrderByExpiresAtAsc(customerId);
        for (CashbackAssignment cb : pool) {
            if (toRefund <= 0) break;
            if (cb.isExpired()) continue; // don't revive expired credit
            int rem  = cb.getRemainingAmount() != null ? cb.getRemainingAmount() : 0;
            int full = cb.getCashbackAmount()  != null ? cb.getCashbackAmount()  : 0;
            int room = full - rem;
            if (room <= 0) continue;
            int give = Math.min(room, toRefund);
            cb.setRemainingAmount(rem + give);
            if (cb.getRemainingAmount() > 0) cb.setRedeemed(false);
            cashbackRepo.save(cb);
            toRefund -= give;
        }
    }

    /** Remove all cashback generated by an order (used on soft-delete / recalc). */
    public void removeCashbackForOrder(Long orderId) {
        List<CashbackAssignment> list = cashbackRepo.findByOrderId(orderId);
        if (!list.isEmpty()) cashbackRepo.deleteAll(list);
    }

    public List<CashbackAssignment> getCashbacksByOrder(Long orderId) {
        return cashbackRepo.findByOrderId(orderId);
    }

    public boolean markRedeemed(Long cashbackId) {
        return cashbackRepo.findById(cashbackId).map(cb -> {
            cb.setRedeemed(true);
            cb.setRemainingAmount(0);
            cashbackRepo.save(cb);
            return true;
        }).orElse(false);
    }

    public List<CashbackAssignment> getAllCashbacks() { return cashbackRepo.findAll(); }

    public long countActive()  { return cashbackRepo.countByRedeemedFalseAndExpiresAtAfter(LocalDateTime.now()); }
    public long countRedeemed(){ return cashbackRepo.countByRedeemedTrue(); }
    public long countExpiringSoon() {
        LocalDateTime now = LocalDateTime.now();
        return cashbackRepo.findExpiringSoon(now, now.plusDays(7)).size();
    }
}
