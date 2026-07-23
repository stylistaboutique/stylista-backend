package com.stylista.service;

import com.stylista.model.CashbackAssignment;
import com.stylista.model.Customer;
import com.stylista.repository.CashbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Runs every day at 10:00 AM server time.
 * Finds active (not redeemed, not expired) cashbacks expiring in {15, 7, 3, 1}
 * days and fires reminders (currently logged only — notifications disabled).
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final int[] REMIND_ON_DAYS_LEFT = {15, 7, 3, 1};

    private final CashbackRepository cashbackRepo;
    private final CustomerService customerService;
    private final NotificationService notifications;

    public ReminderScheduler(CashbackRepository cashbackRepo,
                             CustomerService customerService,
                             NotificationService notifications) {
        this.cashbackRepo = cashbackRepo;
        this.customerService = customerService;
        this.notifications = notifications;
    }

    // Cron: second minute hour day month weekday → 0 0 10 * * * = 10:00 AM daily
    @Scheduled(cron = "0 0 10 * * *")
    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<CashbackAssignment> active = cashbackRepo.findAllActive(now);
        int sent = 0;

        for (CashbackAssignment cb : active) {
            if (cb.getExpiresAt() == null) continue;
            long daysLeft = ChronoUnit.DAYS.between(now.toLocalDate(), cb.getExpiresAt().toLocalDate());
            for (int d : REMIND_ON_DAYS_LEFT) {
                if (daysLeft == d) {
                    Optional<Customer> c = customerService.findById(cb.getCustomerId());
                    if (c.isPresent()) {
                        notifications.sendCashbackReminder(
                                c.get().getName(), c.get().getMobile(),
                                cb.getCashbackAmount(), daysLeft);
                        sent++;
                    }
                }
            }
        }
        log.info("Reminder job finished. {} reminder(s) processed.", sent);
    }
}