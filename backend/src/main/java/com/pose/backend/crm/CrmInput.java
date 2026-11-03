package com.pose.backend.crm;
import com.pose.backend.api.ApiRequestException;
import com.pose.backend.api.JsonInput;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;
public final class CrmInput {
    private CrmInput() {}
    public static String text(JsonInput in,String field,int max,boolean required,boolean nullable) {
        if (required && !in.has(field)) throw ApiRequestException.invalidContact(field,"Vui lòng điền đầy đủ các trường bắt buộc");
        try { return in.text(field,max,required,nullable); }
        catch (ApiRequestException e) {
            if (required) throw ApiRequestException.invalidContact(field,"Vui lòng điền đầy đủ các trường bắt buộc");
            if (field.equals("email") || field.equals("phone")) throw ApiRequestException.invalidContact(field,"Hãy nhập thông tin liên hệ hợp lệ");
            throw e;
        }
    }
    public static UUID uuid(JsonInput in,String field) {
        if (in.missingOrBlank(field)) throw ApiRequestException.invalidContact(field,"Vui lòng điền đầy đủ các trường bắt buộc");
        return in.uuid(field);
    }
    public static Instant instant(JsonInput in,String field) {
        try { return Instant.parse(text(in,field,40,true,false)); }
        catch (DateTimeParseException e) { throw JsonInput.invalid(field,"Thời điểm phải ở định dạng ISO-8601 UTC."); }
    }
}
