package com.stylista.service;

import com.stylista.model.CashbackAssignment;
import com.stylista.repository.CashbackRepository;
import com.stylista.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CashbackService {

    private final CashbackRepository cashbackRepo;
    private final CustomerRepository customerRepo;
    private final NotificationService notifications;

    @Value("${app.cashback.default-percent:20}")      private int defaultPercent;
    @Value("${app.cashback.default-expiry-days:60}")   private int defaultExpiryDays;

    public CashbackService(CashbackRepository cashbackRepo,
                           CustomerRepository customerRepo,
                           NotificationService notifications) {
        this.cashbackRepo = cashbackRepo;
        this.customerRepo = customerRepo;
        this.notifications = notifications;
    }

    // ─────────────────────────────────────────────────────────────
    //  Helper: resolve the set of customer IDs for a mobile number.
    //  All cashback pool operations go through this.
    // ─────────────────────────────────────────────────────────────
    private List<Long> customerIdsForMobile(String mobile) {
        return customerRepo.findByMobile(mobile).stream()
                .map(c -> c.getId())
                .collect(Collectors.toList());
    }

    private String mobileForCustomerId(Long customerId) {
        return customerRepo.findById(customerId)
                .map(c -> c.getMobile())
                .orElse(null);
    }

    // ─────────────────────────────────────────────────────────────
    //  Assign cashback (ad-hoc OR auto on delivery).
    //  Still stored under the specific customer_id that earned it,
    //  but the POOL is shared by number on read/spend.
    // ─────────────────────────────────────────────────────────────
    public CashbackAssignment assignCashback(Long customerId, Long orderId,
                                             Integer percent, Integer amount,
                                             Integer expiryDays, String notes) {
        int pct  = (percent  != null && percent  > 0) ? percent  : defaultPercent;
        int days = (expiryDays != null && expiryDays > 0) ? expiryDays : defaultExpiryDays;

        CashbackAssignment cb = new CashbackAssignment();
        cb.setCustomerId(customerId);
        cb.setOrderId(orderId);
        cb.setCashbackPercent(pct);
        cb.setCashbackAmount(amount);
        cb.setRemainingAmount(amount);
        cb.setAssignedAt(LocalDateTime.now());
        cb.setExpiresAt(LocalDateTime.now().plusDays(days));
        cb.setNotes(notes);

        CashbackAssignment saved = cashbackRepo.save(cb);
        customerRepo.findById(customerId).ifPresent(c ->
            notifications.sendCashbackThankYou(c.getName(), c.getMobile(),
                    pct, amount, saved.getExpiresAt().toLocalDate().toString()));
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    //  Read: history for a specific customer_id (for per-customer
    //  detail views in admin). Still per-name.
    // ─────────────────────────────────────────────────────────────
    public List<CashbackAssignment> getCashbacksForCustomer(Long customerId) {
        return cashbackRepo.findByCustomerIdOrderByAssignedAtAsc(customerId);
    }

    // ─────────────────────────────────────────────────────────────
    //  Pool: full history across all names on a mobile (for the
    //  public my-cashback page and the balance box in New Order).
    // ─────────────────────────────────────────────────────────────
    public List<CashbackAssignment> getCashbacksForMobile(String mobile) {
        List<Long> ids = customerIdsForMobile(mobile);
        if (ids.isEmpty()) return List.of();
        return cashbackRepo.findByCustomerIdInOrderByAssignedAtAsc(ids);
    }

    // ─────────────────────────────────────────────────────────────
    //  Live balance: shared by number.
    //  Pass customerId; we resolve mobile and pool across all names.
    // ─────────────────────────────────────────────────────────────
    public int getLiveBalance(Long customerId) {
        String mobile = mobileForCustomerId(customerId);
        if (mobile == null) return 0;
        return getLiveBalanceForMobile(mobile);
    }

    public int getLiveBalanceForMobile(String mobile) {
        List<Long> ids = customerIdsForMobile(mobile);
        if (ids.isEmpty()) return 0;
        return cashbackRepo.findByCustomerIdInOrderByExpiresAtAsc(ids).stream()
                .filter(CashbackAssignment::isActive)
                .mapToInt(cb -> cb.getRemainingAmount() != null
                        ? cb.getRemainingAmount()
                        : (cb.getCashbackAmount() != null ? cb.getCashbackAmount() : 0))
                .sum();
    }

    // ─────────────────────────────────────────────────────────────
    //  Spend: FIFO across the whole number's pool.
    //  Uses customerId to resolve the mobile, then drains oldest-
    //  expiring-first regardless of which name holds each row.
    // ─────────────────────────────────────────────────────────────
    public int applyBalance(Long customerId, int amount) {
        if (amount <= 0) return 0;
        String mobile = mobileForCustomerId(customerId);
        if (mobile == null) return 0;
        return applyBalanceForMobile(mobile, amount);
    }

    public int applyBalanceForMobile(String mobile, int amount) {
        if (amount <= 0) return 0;
        List<Long> ids = customerIdsForMobile(mobile);
        if (ids.isEmpty()) return 0;

        int toApply = amount, applied = 0;
        List<CashbackAssignment> pool =
                cashbackRepo.findByCustomerIdInOrderByExpiresAtAsc(ids);

        for (CashbackAssignment cb : pool) {
            if (toApply <= 0) break;
            if (!cb.isActive()) continue;
            int rem = cb.getRemainingAmount() != null ? cb.getRemainingAmount()
                    : (cb.getCashbackAmount() != null ? cb.getCashbackAmount() : 0);
            if (rem <= 0) continue;
            int take = Math.min(rem, toApply);
            cb.setRemainingAmount(rem - take);
            if (cb.getRemainingAmount() <= 0) cb.setRedeemed(true);
            cashbackRepo.save(cb);
            applied += take;
            toApply -= take;
        }
        return applied;
    }

    // ─────────────────────────────────────────────────────────────
    //  Refund: put balance back (on order delete/edit).
    //  Also operates across the whole number's pool.
    // ─────────────────────────────────────────────────────────────
    public void refundBalance(Long customerId, int amount) {
        if (amount <= 0) return;
        String mobile = mobileForCustomerId(customerId);
        if (mobile == null) return;

        List<Long> ids = customerIdsForMobile(mobile);
        if (ids.isEmpty()) return;

        int toRefund = amount;
        List<CashbackAssignment> pool =
                cashbackRepo.findByCustomerIdInOrderByExpiresAtAsc(ids);

        for (CashbackAssignment cb : pool) {
            if (toRefund <= 0) break;
            if (cb.isExpired()) continue;
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
