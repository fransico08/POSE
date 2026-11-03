package com.pose.backend.crm;

import com.pose.backend.api.ApiRequestException;
import java.util.Locale;

public record ContactInput(String email, String normalizedEmail, String phone, String normalizedPhone) {
    private static final String REQUIRED = "Vui lòng điền đầy đủ các trường bắt buộc";
    private static final String INVALID = "Hãy nhập thông tin liên hệ hợp lệ";
    public static ContactInput of(String email, String phone) {
        if (email == null && phone == null) throw ApiRequestException.invalidContact("email", REQUIRED);
        String normalizedEmail = email == null ? null : email.toLowerCase(Locale.ROOT);
        if (normalizedEmail != null && !normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw ApiRequestException.invalidContact("email", INVALID);
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone != null) {
            if (!normalizedPhone.matches("^\\+[1-9]\\d{7,14}$")) throw ApiRequestException.invalidContact("phone", INVALID);
        }
        return new ContactInput(email, normalizedEmail, phone, normalizedPhone);
    }
    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String normalized = phone.replaceAll("[\\s.()\\-]", "");
        if (normalized.matches("0\\d{9}")) return "+84" + normalized.substring(1);
        if (normalized.startsWith("84")) return "+" + normalized;
        return normalized;
    }
}
