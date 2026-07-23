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

/**
 * Runs every day at 10:00 AM server time.
 * Finds active cashbacks expiring in {15, 7, 3, 1} days and fires reminders
 * (currently logged only, since notifications are disabled).
 */
@Component
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private static final int[] REMIND_ON_DAYS_LEFT = {15, 7, 3, 1};

    private final CashbackRepository cashbackRepo;
    private final CashbackService cashbackService;
    private final NotificationService notifications;

    public ReminderScheduler(CashbackRepository cashbackRepo,
                             CashbackService cashbackService,
                             NotificationService notifications) {
        this.cashbackRepo = cashbackRepo;
        this.cashbackService = cashbackService;
        this.notifications = notifications;
    }

    // Cron: second minute hour day month weekday  → 0 0 10 * * * = 10:00 AM daily
    @Scheduled(cron = "0 0 10 * * *")
    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<CashbackAssignment> all = cashbackRepo.findAll();
        int sent = 0;

        for (CashbackAssignment cb : all) {
            if (cb.isRedeemed() || cb.getExpiresAt() == null) continue;
            long daysLeft = ChronoUnit.DAYS.between(now.toLocalDate(), cb.getExpiresAt().toLocalDate());
            for (int d : REMIND_ON_DAYS_LEFT) {
                if (daysLeft == d) {
                    Customer c = cashbackService.findCustomer(cb.getCustomerId());
                    if (c != null) {
                        notifications.sendCashbackReminder(c.getName(), c.getMobile(),
                                cb.getCashbackAmount(), daysLeft);
                        sent++;
                    }
                }
            }
        }
        log.info("Reminder job finished. {} reminder(s) processed.", sent);
    }
}
