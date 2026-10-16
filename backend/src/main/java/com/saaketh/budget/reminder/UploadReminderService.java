package com.saaketh.budget.reminder;

import com.saaketh.budget.email.EmailSender;
import com.saaketh.budget.transaction.TransactionRepository;
import com.saaketh.budget.user.User;
import com.saaketh.budget.user.UserRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The opt-in monthly email: "Time to upload your September statement". There's no bank connection,
 * so the app is only as fresh as the last upload; this is the nudge.
 *
 * Runs early each month, once statements for the month before are out. Only people who turned it
 * on and confirmed their email get it, at most once a month, and not if they've already uploaded
 * last month's statement.
 */
@Service
public class UploadReminderService {

    private static final Logger log = LoggerFactory.getLogger(UploadReminderService.class);
    /** Most students using this are in the US East; the exact hour doesn't matter much. */
    static final ZoneId ZONE = ZoneId.of("America/New_York");
    /** Data reaching this close to the end of last month counts as "already uploaded". */
    private static final int END_OF_MONTH_SLACK_DAYS = 4;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMMM d", Locale.US);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final EmailSender emailSender;
    private final String baseUrl;

    public UploadReminderService(UserRepository userRepository, TransactionRepository transactionRepository,
            EmailSender emailSender, @Value("${app.base-url:http://localhost:5173}") String baseUrl) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.emailSender = emailSender;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** 3 p.m. Eastern on the 3rd of every month. */
    @Scheduled(cron = "${app.reminders.cron:0 0 15 3 * *}", zone = "America/New_York")
    public void sendMonthly() {
        int sent = sendDue(LocalDate.now(ZONE));
        log.info("Sent {} upload reminder(s)", sent);
    }

    /**
     * Sends every reminder that's due on {@code today}. Safe to run more than once: each person
     * gets at most one a month.
     *
     * @return how many were sent
     */
    @Transactional
    public int sendDue(LocalDate today) {
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth lastMonth = thisMonth.minusMonths(1);
        String lastMonthName = lastMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.US);
        int sent = 0;
        for (User user : userRepository.findByUploadRemindersTrueAndEmailVerifiedAtIsNotNull()) {
            if (thisMonth.toString().equals(user.getLastReminderMonth())) {
                continue; // already reminded this month
            }
            LocalDate latest = transactionRepository.findLatestDate(user.getId()).orElse(null);
            if (latest != null && !latest.isBefore(lastMonth.atEndOfMonth().minusDays(END_OF_MONTH_SLACK_DAYS))) {
                continue; // last month is already uploaded
            }
            try {
                emailSender.send(user.getEmail(), "Time to upload your " + lastMonthName + " statement",
                        body(lastMonthName, latest));
            } catch (RuntimeException e) {
                log.error("Could not send the upload reminder to {}: {}", user.getEmail(), e.getMessage());
                continue; // not marked as sent, so the next run tries again
            }
            user.setLastReminderMonth(thisMonth.toString());
            sent++;
        }
        return sent;
    }

    private String body(String lastMonthName, LocalDate latest) {
        String status = latest == null
                ? "You haven't uploaded a statement yet."
                : "Your Spendr transactions currently end on " + DAY.format(latest) + ".";
        return """
                Hi!

                Your %s statement is probably ready at your bank. %s

                Download it as a CSV from your bank's website and upload it here:

                %s/import

                Not sure where the CSV is? The upload page has steps for most banks.

                You're getting this because you turned on monthly reminders. You can turn them off on your
                Account page: %s/account
                """.formatted(lastMonthName, status, baseUrl, baseUrl);
    }
}
