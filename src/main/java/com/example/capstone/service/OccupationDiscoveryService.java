package com.example.capstone.service;

import com.example.capstone.dto.OccupationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OccupationDiscoveryService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private static final String CACHE_KEY = "occupations:recommended";

    public List<OccupationResponse> getRecommendedOccupations() {
        try {
            String cachedData = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cachedData != null) {
                return List.of(objectMapper.readValue(cachedData, OccupationResponse[].class));
            }
        } catch (Exception e) {
            // Fallback and execute method body
        }

        List<InternalOccupation> occupations = new ArrayList<>();

        // --- กลุ่มงานขนส่งและเดินทาง ---
        occupations.add(createOccupation("พนักงานส่งอาหาร (Rider)",
                "งานอิสระยอดนิยม รับรายได้รายวันตามรอบที่วิ่ง", "500 - 1,500 บาท/วัน", "delivery",
                List.of("อิสระเรื่องเวลา", "รับรายได้รายวัน", "สมัครง่าย"),
                List.of("ความเสี่ยงบนท้องถนน", "สภาพอากาศ", "ค่าเสื่อมรถ"),
                "rider messenger grab lineman food delivery", "https://tdriver.grab.com/s/signup", "Grab Driver",
                15000.0, 45000.0));

        occupations.add(createOccupation("ขับรถรับส่งผู้โดยสาร",
                "เปลี่ยนเวลาว่างและรถส่วนตัวให้เป็นรายได้", "800 - 1,800 บาท/วัน", "car",
                List.of("นั่งทำงานในแอร์", "เลือกช่วงเวลาเองได้", "ความต้องการสูง"),
                List.of("รถติด", "ค่าซ่อมบำรุงรถ", "เจอผู้โดยสารหลากหลาย"),
                "driver ขับรถ grabcar bolt แท็กซี่", "https://tdriver.grab.com/s/signup", "GrabCar", 24000.0, 54000.0));

        occupations.add(createOccupation("ล้างรถนอกสถานที่ (Mobile Car Wash)",
                "บริการล้างรถถึงบ้านลูกค้า ไม่ต้องเสียเวลาเดินทาง", "300 - 800 บาท/คัน", "car",
                List.of("ไม่ต้องมีหน้าร้าน", "รายได้ต่อคันสูง", "ลูกค้านิยมเพราะสะดวก"),
                List.of("เหนื่อยกาย", "ต้องเตรียมอุปกรณ์เอง", "ขึ้นอยู่กับฟ้าฝน"),
                "ล้างรถ คาร์แคร์ car wash เคลือบสี", null, null, 9000.0, 24000.0));

        // --- กลุ่มงานดิจิทัลและสร้างสรรค์ ---
        occupations.add(createOccupation("ฟรีแลนซ์ กราฟิก/คอนเทนต์",
                "เน้นงานไอเดียและดีไซน์ ทำงานจากที่บ้านได้ 100%", "1,500 - 5,000 บาท/งาน", "computer",
                List.of("WFH", "เป็นเจ้าของเวลา", "สร้างพอร์ตงานได้"),
                List.of("งานไม่สม่ำเสมอ", "การแก้รอบงาน", "การแข่งขันสูง"),
                "graphic design designer content creator ตัดต่อ ยิงโฆษณา", "https://fastwork.co/", "Fastwork", 15000.0,
                60000.0));

        occupations.add(createOccupation("ตัดต่อวิดีโอสั้น (TikTok/Reels)",
                "รับตัดต่อคลิปวิดีโอขนาดสั้นให้แม่ค้าออนไลน์หรือครีเอเตอร์", "500 - 2,000 บาท/คลิป", "video",
                List.of("ตลาดกำลังโต", "ทำงานผ่านมือถือ/iPad ได้", "รายได้ต่อชิ้นดี"),
                List.of("ต้องตามเทรนด์ตลอด", "ใช้สายตาเยอะ", "คอมพิวเตอร์ต้องแรง"),
                "ตัดต่อวิดีโอ video editor tiktok reels capcut", "https://fastwork.co/video-editing", "Fastwork Video",
                10000.0, 40000.0));

        occupations.add(createOccupation("นักแปล / นักเขียน",
                "งานใช้ภาษาและทักษะการเรียบเรียงบทความ", "300 - 1,500 บาท/หน้า", "writing",
                List.of("งานเงียบสงบ", "ฝึกทักษะภาษา", "งานมีความเป็นส่วนตัวสูง"),
                List.of("ใช้สมาธิสูง", "กำหนดส่งงานกดดัน", "สายตาล้า"),
                "แปลภาษา translator นักเขียน บทความ พิสูจน์อักษร", null, null, 6000.0, 30000.0));

        // --- กลุ่มงานขายและการตลาด ---
        occupations.add(createOccupation("นายหน้าออนไลน์ (Affiliate)",
                "ขายของไม่ต้องสต็อกสินค้า รับค่าคอมมิชชันจากการรีวิว", "2,000 - 15,000+ บาท/เดือน", "sales",
                List.of("ไม่ต้องลงทุนเงิน", "โอกาสรายได้ไร้ขีดจำกัด", "ทำได้ทุกที่"),
                List.of("ต้องสร้างตัวตน", "รายได้ไม่แน่นอนในช่วงแรก", "อัลกอริทึมเปลี่ยนบ่อย"),
                "affiliate นายหน้า นายหน้า tiktok ขายของออนไลน์ รีวิว", "https://affiliate.shopee.co.th/",
                "Shopee Affiliate", 2000.0, 40000.0));

        occupations.add(createOccupation("รับซื้อมาขายไป (Reseller)",
                "หาของมือสองสภาพดีมาอัปเกรดและขายต่อ", "2,000 - 10,000+ บาท/ชิ้น", "resell",
                List.of("กำไรต่อชิ้นสูง", "ได้ความรู้เรื่องสินค้า", "ตื่นเต้นท้าทาย"),
                List.of("ต้องมีงบลงทุน", "ความเสี่ยงของค้างสต็อก", "ต้องมีที่เก็บของ"),
                "ขายของมือสอง แบรนด์เนม second hand รับซื้อ ของเก่า", null, null, 5000.0, 50000.0));

        occupations.add(createOccupation("รับหิ้วของ (Personal Shopper)",
                "รับหิ้วสินค้าตามงานอีเวนต์หรือห้างสรรพสินค้า", "20 - 100 บาท/ชิ้น", "shopping",
                List.of("ไม่ต้องลงทุนมาก", "ชอบช้อปปิ้งอยู่แล้ว", "ได้ค่าหิ้วทันที"),
                List.of("ต้องเดินเยอะ", "แบกของหนัก", "เสี่ยงเจอออเดอร์ยกเลิก"),
                "รับหิ้ว personal shopper หิ้ว ช้อปปิ้ง", "https://shobshop.com/", "ShobShop", 3000.0, 15000.0));

        // --- กลุ่มงานแอดมินและสนับสนุน ---
        occupations.add(createOccupation("แอดมินตอบแชท (Remote Admin)",
                "ช่วยร้านค้าออนไลน์ดูแลลูกค้าและรับยอดจอง", "300 - 800 บาท/วัน", "admin",
                List.of("งานไม่ซับซ้อน", "ทำที่บ้านได้", "รายได้มั่นคง"),
                List.of("ต้องสแตนบายตลอด", "รับมือลูกค้ากดดัน", "งานรูทีน"),
                "แอดมิน customer service ตอบแชท admin แอดมินร้าน", "https://fastwork.co/admin", "Fastwork Admin",
                9000.0, 24000.0));

        occupations.add(createOccupation("พนักงานแพ็คของ (Packing Staff)",
                "ช่วยร้านค้าออนไลน์แพ็คสินค้าลงกล่องในช่วงออเดอร์เยอะ", "300 - 500 บาท/วัน", "package",
                List.of("ไม่เครียดงาน", "งานเป็นรูปธรรม", "มีเวลาพักชัดเจน"),
                List.of("เหนื่อยกาย", "งานซ้ำซาก", "ต้องทำเวลาแข่งกับคิวส่ง"),
                "แพ็คของ คลังสินค้า พนักงานคลัง สต็อกสินค้า", null, null, 9000.0, 15000.0));

        // --- กลุ่มงานการศึกษาและวิชาชีพ ---
        occupations.add(createOccupation("ติวเตอร์สอนพิเศษออนไลน์",
                "สอนวิชาการหรือทักษะผ่านระบบออนไลน์", "250 - 600 บาท/ชั่วโมง", "teaching",
                List.of("รายได้ต่อชม.สูง", "ภูมิใจในงาน", "ไม่ต้องเดินทาง"),
                List.of("ต้องเตรียมการสอน", "เด็กนักเรียนลาบ่อย", "ต้องมีความรู้แน่น"),
                "ติวเตอร์ tutor สอนพิเศษ ภาษา สอนออนไลน์", "https://bestkru.com/", "BestKru", 10000.0, 30000.0));

        occupations.add(createOccupation("เทรนเนอร์ส่วนตัว (Personal Trainer)",
                "สอนออกกำลังกายและแนะนำโภชนาการแบบตัวต่อตัว", "500 - 1,200 บาท/ชม.", "fitness",
                List.of("รายได้ดีมาก", "ตัวเราสุขภาพดีด้วย", "มีคอนเนคชัน"),
                List.of("เหนื่อยกาย", "ต้องคุมอารมณ์ลูกค้า", "ต้องมีใบเซอร์/ความรู้แน่น"),
                "เทรนเนอร์ trainer ออกกำลังกาย ฟิตเนส สอนโยคะ", "https://www.fitii.co/", "Fitii Trainer", 20000.0,
                80000.0));

        // --- กลุ่มงานบริการเฉพาะทาง ---
        occupations.add(createOccupation("ช่างบริการ/แม่บ้านรายชั่วโมง",
                "งานบริการทำความสะอาดหรือซ่อมบำรุงที่พัก", "500 - 2,500 บาท/งาน", "service",
                List.of("ความต้องการสูง", "รายได้ดีต่องาน", "งานจบไว"),
                List.of("ใช้แรงงาน", "ต้องมีอุปกรณ์เยอะ", "เลอะเทอะบ้าง"),
                "แม่บ้าน ล้างแอร์ ช่างซ่อม พนักงานทำความสะอาด", "https://seekster.co/th", "Seekster", 10000.0,
                50000.0));

        occupations.add(createOccupation("รับดูแลสัตว์เลี้ยง (Pet Sitter)",
                "ดูแลสุนัข/แมว ขณะเจ้าของไมู่อยู่บ้าน", "200 - 600 บาท/วัน", "pet",
                List.of("ได้อยู่กับสัตว์", "งานไม่เครียด", "มีความสุข"),
                List.of("รับผิดชอบสูง", "อาจโดนกัด/ข่วน", "เลอะเทอะ"),
                "ดูแลสัตว์เลี้ยง dog walker รับฝากสัตว์ อาบน้ำสัตว์", "https://www.petbacker.com/th", "PetBacker",
                6000.0, 18000.0));

        occupations.add(createOccupation("ดูแลผู้สูงอายุรายชั่วโมง",
                "อยู่เป็นเพื่อนหรือช่วยดูแลกิจวัตรประจำวันของผู้สูงอายุ", "500 - 1,000 บาท/วัน", "old",
                List.of("ได้ทำบุญช่วยเหลือ", "สังคมต้องการสูง", "งานสงบ"),
                List.of("ความกดดันสูง", "ต้องใจเย็นมาก", "ต้องมีพื้นฐานพยาบาลเบื้องต้น"),
                "ดูแลผู้สูงอายุ พยาบาล เฝ้าไข้ออนไลน์ ผู้ช่วยพยาบาล", "https://healthathome.in.th/", "Health at Home",
                15000.0, 30000.0));

        List<OccupationResponse> result = occupations.stream().map(this::mapToResponseRaw).collect(Collectors.toList());
        try {
            String jsonData = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(CACHE_KEY, jsonData, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            // Fallback
        }
        return result;
    }

    private InternalOccupation createOccupation(String title, String desc, String income, String icon,
            List<String> pros, List<String> cons, String keywords, String url, String platform, Double min,
            Double max) {
        InternalOccupation o = new InternalOccupation();
        o.title = title;
        o.description = desc;
        o.averageIncome = income;
        o.iconType = icon;
        o.pros = pros;
        o.cons = cons;
        o.potentialKeywords = keywords;
        o.externalUrl = url;
        o.platformName = platform;
        o.minMonthlyPotential = min;
        o.maxMonthlyPotential = max;
        o.isBestMatch = false;
        return o;
    }

    private OccupationResponse mapToResponseRaw(InternalOccupation o) {
        return OccupationResponse.builder()
                .title(o.title)
                .description(o.description)
                .averageIncome(o.averageIncome)
                .iconType(o.iconType)
                .pros(o.pros)
                .cons(o.cons)
                .potentialKeywords(o.potentialKeywords)
                .externalUrl(o.externalUrl)
                .platformName(o.platformName)
                .isBestMatch(o.isBestMatch)
                .build();
    }

    // Class ภายในสำหรับช่วยคำนวณรายได้เปรียบเทียบ
    private static class InternalOccupation {
        String title;
        String description;
        String averageIncome;
        String iconType;
        List<String> pros;
        List<String> cons;
        String potentialKeywords;
        String externalUrl;
        String platformName;
        Double minMonthlyPotential;
        Double maxMonthlyPotential;
        Boolean isBestMatch;
    }
}
