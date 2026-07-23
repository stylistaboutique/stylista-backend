package com.stylista.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * NotificationService — the single place where messaging lives.
 *
 * Right now notifications are DISABLED (app.notifications.enabled=false).
 * When you're ready for WhatsApp or SMS later, you only edit THIS file —
 * no controllers, models, or frontend need to change.
 *
 * To enable later:
 *   1. Set NOTIFICATIONS_ENABLED=true in Render env vars.
 *   2. Replace the log lines below with real WhatsApp Cloud API / SMS calls.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Value("${app.notifications.enabled:false}")
    private boolean enabled;

    /** Sent when a customer is added with a cashback. */
    public void sendCashbackThankYou(String name, String mobile, Integer percent, Integer amount, String expiryDate) {
        if (!enabled) {
            log.info("[notifications disabled] Would send THANK-YOU to {} ({}): {}% / Rs {} valid till {}",
                    name, mobile, percent, amount, expiryDate);
            return;
        }
        // TODO: plug in WhatsApp / SMS here later.
    }

    /** Sent by the reminder scheduler before a cashback expires. */
    public void sendCashbackReminder(String name, String mobile, Integer amount, long daysLeft) {
        if (!enabled) {
            log.info("[notifications disabled] Would send REMINDER to {} ({}): Rs {} expires in {} days",
                    name, mobile, amount, daysLeft);
            return;
        }
        // TODO: plug in WhatsApp / SMS here later.
    }
}
