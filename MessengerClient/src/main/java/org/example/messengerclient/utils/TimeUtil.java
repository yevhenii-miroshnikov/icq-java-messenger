package org.example.messengerclient.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TimeUtil {
    private static final Logger logger = Logger.getLogger(TimeUtil.class.getName());
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    public static LocalDateTime parse(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return null;
        try {
            return LocalDateTime.parse(timestamp);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Date parsing error: " + timestamp);
            return null;
        }
    }

    // Formats relative time metadata for networking activity tracking and read receipts
    public static String formatRelativeTime(String timestamp) {
        LocalDateTime dt = parse(timestamp);
        if (dt == null) return "";

        LocalDate today = LocalDate.now();
        LocalDate date = dt.toLocalDate();
        String timeStr = dt.format(DateTimeFormatter.ofPattern("HH:mm"));

        if (date.equals(today)) {
            return "today at " + timeStr;
        } else if (date.equals(today.minusDays(1))) {
            return "yesterday at " + timeStr;
        } else if (date.getYear() == today.getYear()) {
            return dt.format(DateTimeFormatter.ofPattern("d MMMM 'at' HH:mm", DEFAULT_LOCALE));
        } else {
            return dt.format(DateTimeFormatter.ofPattern("d MMMM yyyy 'at' HH:mm", DEFAULT_LOCALE));
        }
    }

    // Formats plain message delivery timestamp inside chat bubbles
    public static String formatMessageTime(String timestamp) {
        LocalDateTime dt = parse(timestamp);
        if (dt == null) return "";
        return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    // Formats text descriptor for chronological calendar capsule separators
    public static String formatDateDivider(String timestamp) {
        LocalDateTime dt = parse(timestamp);
        if (dt == null) return "";

        LocalDate today = LocalDate.now();
        LocalDate date = dt.toLocalDate();

        if (date.equals(today)) {
            return "Today";
        } else if (date.equals(today.minusDays(1))) {
            return "Yesterday";
        } else if (date.getYear() == today.getYear()) {
            return dt.format(DateTimeFormatter.ofPattern("d MMMM", DEFAULT_LOCALE));
        } else {
            return dt.format(DateTimeFormatter.ofPattern("d MMMM yyyy", DEFAULT_LOCALE));
        }
    }

    // Utility evaluating inter-day boundary crossings for UI capsule rendering
    public static boolean isDifferentDay(String ts1, String ts2) {
        LocalDateTime dt1 = parse(ts1);
        LocalDateTime dt2 = parse(ts2);
        if (dt1 == null || dt2 == null) return true;
        return !dt1.toLocalDate().equals(dt2.toLocalDate());
    }
}