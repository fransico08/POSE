package com.pose.backend.ai;

import org.springframework.stereotype.Component;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class Redactor {
    private static final Pattern EMAIL=Pattern.compile("(?i)(?<![\\w.+-])[\\w.+-]+@[a-z0-9.-]+\\.[a-z]{2,}(?![\\w.-])");
    private static final Pattern PHONE=Pattern.compile("(?<![\\d+])(?:\\+84[ .-]*|0)[1-9](?:[ .-]*\\d){8}(?![\\d])");
    private static final Pattern IDENTITY=Pattern.compile("(?<![0-9a-fA-F]{4}-)(?<!\\d)(?:\\d{12}|\\d{9})(?!\\d)");
    public record Result(String text,int count) {}
    public Result redact(String input) {
        int[] count={0}; String text=replace(EMAIL,input,"EMAIL",count);
        text=replace(PHONE,text,"PHONE",count); text=replace(IDENTITY,text,"IDENTITY",count);
        return new Result(text,count[0]);
    }
    private String replace(Pattern pattern,String input,String label,int[] count) {
        Matcher m=pattern.matcher(input); StringBuffer result=new StringBuffer(); int index=0;
        while(m.find()) {index++;count[0]++;m.appendReplacement(result,Matcher.quoteReplacement("["+label+"_"+index+"]"));}
        m.appendTail(result);return result.toString();
    }
}
