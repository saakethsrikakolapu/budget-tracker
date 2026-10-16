-- Opt-in monthly email: "Time to upload September's statement" (UploadReminderService).
-- Off by default. last_reminder_month ("2026-10") makes sure nobody gets two in one month, even if
-- the server restarts or the job runs twice.
ALTER TABLE users
    ADD COLUMN upload_reminders    BOOLEAN    NOT NULL DEFAULT FALSE,
    ADD COLUMN last_reminder_month VARCHAR(7);
