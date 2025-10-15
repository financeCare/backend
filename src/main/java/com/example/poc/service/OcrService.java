package com.example.poc.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?:฿|THB|บาท\\s*)?\\s*([0-9OIlS]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{2})?)",
            Pattern.CASE_INSENSITIVE
    );

    // 2) ทำ normalization เบื้องต้น เพื่อแก้ข้อผิดพลาดจาก OCR
    private static String normalizeOcrNoise(String s) {
        if (s == null) return "";
        String t = s.trim();

        // ตัวอย่างการแก้ common OCR mistakes
        t = t.replaceAll("[O]", "0");   // O -> 0
        t = t.replaceAll("[Il|\\|]", "1"); // I, l, | -> 1
        t = t.replaceAll("S", "5");     // S -> 5 (ระวังถ้าคำจริง)
        t = t.replaceAll("，", ",");    // fullwidth comma
        t = t.replaceAll("。", ".");    // fullwidth dot
        t = t.replaceAll("[^0-9.,]", ""); // เอาเฉพาะตัวเลขและ . ,
        return t;
    }

    // 3) ปรับ logic สำหรับ comma / dot: ถ้ามีทั้งสองอย่าง คิดว่า dot เป็นทศนิยม, comma เป็นหลักพัน
    private static BigDecimal normalizeAndParseNumber(String raw) {
        String s = normalizeOcrNoise(raw);

        // ถ้า s มีทั้ง '.' และ ',' -> สมมติว่า ',' เป็น thousands separator และ '.' เป็น decimal separator
        if (s.contains(".") && s.contains(",")) {
            s = s.replaceAll(",", ""); // ลบ comma
        } else if (s.contains(",")) {
            // ถ้ามีแค่ comma อาจหมายถึง . (decimal) หรือ comma เป็น thousands
            // วิธีหนึ่ง: ถ้า comma อยู่หลัง 3 หลักจากขวา ให้ถือเป็น thousands separator
            int lastComma = s.lastIndexOf(',');
            if (s.length() - lastComma - 1 == 2) {
                // เช่น "1000,25" -> เป็นทศนิยม
                s = s.replace(',', '.');
            } else {
                // เช่น "1,000" -> เอา comma ออก
                s = s.replace(",", "");
            }
        }
        if (s.isEmpty()) return null;
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // 4) ฟังก์ชันหลัก: คืน list ของ BigDecimal
    public static List<BigDecimal> extractAmounts(String ocrText) {
        List<BigDecimal> results = new ArrayList<>();
        if (ocrText == null || ocrText.isEmpty()) return results;

        Matcher m = AMOUNT_PATTERN.matcher(ocrText);
        while (m.find()) {
            String raw = m.group(1);
            BigDecimal value = normalizeAndParseNumber(raw);
            if (value != null) {
                results.add(value);
            }
        }
        return results;
    }
}
