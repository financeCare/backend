package com.example.capstone.service;

import com.example.capstone.dto.OccupationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OccupationDiscoveryService {
    private static final String PHYSICAL_FATIGUE = "เหนื่อยกาย";
    private static final String CACHE_KEY = "occupations:recommended";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
        occupations.add(InternalOccupation.builder()
                .title("พนักงานส่งอาหาร (Rider)")
                .description("งานอิสระยอดนิยม รับรายได้รายวันตามรอบที่วิ่ง")
                .averageIncome("500 - 1,500 บาท/วัน")
                .iconType("delivery")
                .pros(List.of("อิสระเรื่องเวลา", "รับรายได้รายวัน", "สมัครง่าย"))
                .cons(List.of("ความเสี่ยงบนท้องถนน", "สภาพอากาศ", "ค่าเสื่อมรถ"))
                .potentialKeywords("rider messenger grab lineman food delivery")
                .externalUrl("https://tdriver.grab.com/s/signup")
                .platformName("Grab Driver")
                .minMonthlyPotential(15000.0)
                .maxMonthlyPotential(45000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("ขับรถรับส่งผู้โดยสาร")
                .description("เปลี่ยนเวลาว่างและรถส่วนตัวให้เป็นรายได้")
                .averageIncome("800 - 1,800 บาท/วัน")
                .iconType("car")
                .pros(List.of("นั่งทำงานในแอร์", "เลือกช่วงเวลาเองได้", "ความต้องการสูง"))
                .cons(List.of("รถติด", "ค่าซ่อมบำรุงรถ", "เจอผู้โดยสารหลากหลาย"))
                .potentialKeywords("driver ขับรถ grabcar bolt แท็กซี่")
                .externalUrl("https://tdriver.grab.com/s/signup")
                .platformName("GrabCar")
                .minMonthlyPotential(24000.0)
                .maxMonthlyPotential(54000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("ล้างรถนอกสถานที่ (Mobile Car Wash)")
                .description("บริการล้างรถถึงบ้านลูกค้า ไม่ต้องเสียเวลาเดินทาง")
                .averageIncome("300 - 800 บาท/คัน")
                .iconType("car")
                .pros(List.of("ไม่ต้องมีหน้าร้าน", "รายได้ต่อคันสูง", "ลูกค้านิยมเพราะสะดวก"))
                .cons(List.of(PHYSICAL_FATIGUE, "ต้องเตรียมอุปกรณ์เอง", "ขึ้นอยู่กับฟ้าฝน"))
                .potentialKeywords("ล้างรถ คาร์แคร์ car wash เคลือบสี")
                .minMonthlyPotential(9000.0)
                .maxMonthlyPotential(24000.0)
                .build());

        // --- กลุ่มงานดิจิทัลและสร้างสรรค์ ---
        occupations.add(InternalOccupation.builder()
                .title("ฟรีแลนซ์ กราฟิก/คอนเทนต์")
                .description("เน้นงานไอเดียและดีไซน์ ทำงานจากที่บ้านได้ 100%")
                .averageIncome("1,500 - 5,000 บาท/งาน")
                .iconType("computer")
                .pros(List.of("WFH", "เป็นเจ้าของเวลา", "สร้างพอร์ตงานได้"))
                .cons(List.of("งานไม่สม่ำเสมอ", "การแก้รอบงาน", "การแข่งขันสูง"))
                .potentialKeywords("graphic design designer content creator ตัดต่อ ยิงโฆษณา")
                .externalUrl("https://fastwork.co/")
                .platformName("Fastwork")
                .minMonthlyPotential(15000.0)
                .maxMonthlyPotential(60000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("ตัดต่อวิดีโอสั้น (TikTok/Reels)")
                .description("รับตัดต่อคลิปวิดีโอขนาดสั้นให้แม่ค้าออนไลน์หรือครีเอเตอร์")
                .averageIncome("500 - 2,000 บาท/คลิป")
                .iconType("video")
                .pros(List.of("ตลาดกำลังโต", "ทำงานผ่านมือถือ/iPad ได้", "รายได้ต่อชิ้นดี"))
                .cons(List.of("ต้องตามเทรนด์ตลอด", "ใช้สายตาเยอะ", "คอมพิวเตอร์ต้องแรง"))
                .potentialKeywords("ตัดต่อวิดีโอ video editor tiktok reels capcut")
                .externalUrl("https://fastwork.co/video-editing")
                .platformName("Fastwork Video")
                .minMonthlyPotential(10000.0)
                .maxMonthlyPotential(40000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("นักแปล / นักเขียน")
                .description("งานใช้ภาษาและทักษะการเรียบเรียงบทความ")
                .averageIncome("300 - 1,500 บาท/หน้า")
                .iconType("writing")
                .pros(List.of("งานเงียบสงบ", "ฝึกทักษะภาษา", "งานมีความเป็นส่วนตัวสูง"))
                .cons(List.of("ใช้สมาธิสูง", "กำหนดส่งงานกดดัน", "สายตาล้า"))
                .potentialKeywords("แปลภาษา translator นักเขียน บทความ พิสูจน์อักษร")
                .minMonthlyPotential(6000.0)
                .maxMonthlyPotential(30000.0)
                .build());

        // --- กลุ่มงานขายและการตลาด ---
        occupations.add(InternalOccupation.builder()
                .title("นายหน้าออนไลน์ (Affiliate)")
                .description("ขายของไม่ต้องสต็อกสินค้า รับค่าคอมมิชชันจากการรีวิว")
                .averageIncome("2,000 - 15,000+ บาท/เดือน")
                .iconType("sales")
                .pros(List.of("ไม่ต้องลงทุนเงิน", "โอกาสรายได้ไร้ขีดจำกัด", "ทำได้ทุกที่"))
                .cons(List.of("ต้องสร้างตัวตน", "รายได้ไม่แน่นอนในช่วงแรก", "อัลกอริทึมเปลี่ยนบ่อย"))
                .potentialKeywords("affiliate นายหน้า นายหน้า tiktok ขายของออนไลน์ รีวิว")
                .externalUrl("https://affiliate.shopee.co.th/")
                .platformName("Shopee Affiliate")
                .minMonthlyPotential(2000.0)
                .maxMonthlyPotential(40000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("รับซื้อมาขายไป (Reseller)")
                .description("หาของมือสองสภาพดีมาอัปเกรดและขายต่อ")
                .averageIncome("2,000 - 10,000+ บาท/ชิ้น")
                .iconType("resell")
                .pros(List.of("กำไรต่อชิ้นสูง", "ได้ความรู้เรื่องสินค้า", "ตื่นเต้นท้าทาย"))
                .cons(List.of("ต้องมีงบลงทุน", "ความเสี่ยงของค้างสต็อก", "ต้องมีที่เก็บของ"))
                .potentialKeywords("ขายของมือสอง แบรนด์เนม second hand รับซื้อ ของเก่า")
                .minMonthlyPotential(5000.0)
                .maxMonthlyPotential(50000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("รับหิ้วของ (Personal Shopper)")
                .description("รับหิ้วสินค้าตามงานอีเวนต์หรือห้างสรรพสินค้า")
                .averageIncome("20 - 100 บาท/ชิ้น")
                .iconType("shopping")
                .pros(List.of("ไม่ต้องลงทุนมาก", "ชอบช้อปปิ้งอยู่แล้ว", "ได้ค่าหิ้วทันที"))
                .cons(List.of("ต้องเดินเยอะ", "แบกของหนัก", "เสี่ยงเจอออเดอร์ยกเลิก"))
                .potentialKeywords("รับหิ้ว personal shopper หิ้ว ช้อปปิ้ง")
                .externalUrl("https://shobshop.com/")
                .platformName("ShobShop")
                .minMonthlyPotential(3000.0)
                .maxMonthlyPotential(15000.0)
                .build());

        // --- กลุ่มงานแอดมินและสนับสนุน ---
        occupations.add(InternalOccupation.builder()
                .title("แอดมินตอบแชท (Remote Admin)")
                .description("ช่วยร้านค้าออนไลน์ดูแลลูกค้าและรับยอดจอง")
                .averageIncome("300 - 800 บาท/วัน")
                .iconType("admin")
                .pros(List.of("งานไม่ซับซ้อน", "ทำที่บ้านได้", "รายได้มั่นคง"))
                .cons(List.of("ต้องสแตนบายตลอด", "รับมือลูกค้ากดดัน", "งานรูทีน"))
                .potentialKeywords("แอดมิน customer service ตอบแชท admin แอดมินร้าน")
                .externalUrl("https://fastwork.co/admin")
                .platformName("Fastwork Admin")
                .minMonthlyPotential(9000.0)
                .maxMonthlyPotential(24000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("พนักงานแพ็คของ (Packing Staff)")
                .description("ช่วยร้านค้าออนไลน์แพ็คสินค้าลงกล่องในช่วงออเดอร์เยอะ")
                .averageIncome("300 - 500 บาท/วัน")
                .iconType("package")
                .pros(List.of("ไม่เครียดงาน", "งานเป็นรูปธรรม", "มีเวลาพักชัดเจน"))
                .cons(List.of(PHYSICAL_FATIGUE, "งานซ้ำซาก", "ต้องทำเวลาแข่งกับคิวส่ง"))
                .potentialKeywords("แพ็คของ คลังสินค้า พนักงานคลัง สต็อกสินค้า")
                .minMonthlyPotential(9000.0)
                .maxMonthlyPotential(15000.0)
                .build());

        // --- กลุ่มงานการศึกษาและวิชาชีพ ---
        occupations.add(InternalOccupation.builder()
                .title("ติวเตอร์สอนพิเศษออนไลน์")
                .description("สอนวิชาการหรือทักษะผ่านระบบออนไลน์")
                .averageIncome("250 - 600 บาท/ชั่วโมง")
                .iconType("teaching")
                .pros(List.of("รายได้ต่อชม.สูง", "ภูมิใจในงาน", "ไม่ต้องเดินทาง"))
                .cons(List.of("ต้องเตรียมการสอน", "เด็กนักเรียนลาบ่อย", "ต้องมีความรู้แน่น"))
                .potentialKeywords("ติวเตอร์ tutor สอนพิเศษ ภาษา สอนออนไลน์")
                .externalUrl("https://bestkru.com/")
                .platformName("BestKru")
                .minMonthlyPotential(10000.0)
                .maxMonthlyPotential(30000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("เทรนเนอร์ส่วนตัว (Personal Trainer)")
                .description("สอนออกกำลังกายและแนะนำโภชนาการแบบตัวต่อตัว")
                .averageIncome("500 - 1,200 บาท/ชม.")
                .iconType("fitness")
                .pros(List.of("รายได้ดีมาก", "ตัวเราสุขภาพดีด้วย", "มีคอนเนคชัน"))
                .cons(List.of(PHYSICAL_FATIGUE, "ต้องคุมอารมณ์ลูกค้า", "ต้องมีใบเซอร์/ความรู้แน่น"))
                .potentialKeywords("เทรนเนอร์ trainer ออกกำลังกาย ฟิตเนส สอนโยคะ")
                .externalUrl("https://www.fitii.co/")
                .platformName("Fitii Trainer")
                .minMonthlyPotential(20000.0)
                .maxMonthlyPotential(80000.0)
                .build());

        // --- กลุ่มงานบริการเฉพาะทาง ---
        occupations.add(InternalOccupation.builder()
                .title("ช่างบริการ/แม่บ้านรายชั่วโมง")
                .description("งานบริการทำความสะอาดหรือซ่อมบำรุงที่พัก")
                .averageIncome("500 - 2,500 บาท/งาน")
                .iconType("service")
                .pros(List.of("ความต้องการสูง", "รายได้ดีต่องาน", "งานจบไว"))
                .cons(List.of("ใช้แรงงาน", "ต้องมีอุปกรณ์เยอะ", "เลอะเทอะบ้าง"))
                .potentialKeywords("แม่บ้าน ล้างแอร์ ช่างซ่อม พนักงานทำความสะอาด")
                .externalUrl("https://seekster.co/th")
                .platformName("Seekster")
                .minMonthlyPotential(10000.0)
                .maxMonthlyPotential(50000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("รับดูแลสัตว์เลี้ยง (Pet Sitter)")
                .description("ดูแลสุนัข/แมว ขณะเจ้าของไมู่อยู่บ้าน")
                .averageIncome("200 - 600 บาท/วัน")
                .iconType("pet")
                .pros(List.of("ได้อยู่กับสัตว์", "งานไม่เครียด", "มีความสุข"))
                .cons(List.of("รับผิดชอบสูง", "อาจโดนกัด/ข่วน", "เลอะเทอะ"))
                .potentialKeywords("ดูแลสัตว์เลี้ยง dog walker รับฝากสัตว์ อาบน้ำสัตว์")
                .externalUrl("https://www.petbacker.com/th")
                .platformName("PetBacker")
                .minMonthlyPotential(6000.0)
                .maxMonthlyPotential(18000.0)
                .build());

        occupations.add(InternalOccupation.builder()
                .title("ดูแลผู้สูงอายุรายชั่วโมง")
                .description("อยู่เป็นเพื่อนหรือช่วยดูแลกิจวัตรประจำวันของผู้สูงอายุ")
                .averageIncome("500 - 1,000 บาท/วัน")
                .iconType("old")
                .pros(List.of("ได้ทำบุญช่วยเหลือ", "สังคมต้องการสูง", "งานสงบ"))
                .cons(List.of("ความกดดันสูง", "ต้องใจเย็นมาก", "ต้องมีพื้นฐานพยาบาลเบื้องต้น"))
                .potentialKeywords("ดูแลผู้สูงอายุ พยาบาล เฝ้าไข้ออนไลน์ ผู้ช่วยพยาบาล")
                .externalUrl("https://healthathome.in.th/")
                .platformName("Health at Home")
                .minMonthlyPotential(15000.0)
                .maxMonthlyPotential(30000.0)
                .build());

        List<OccupationResponse> result = occupations.stream().map(this::mapToResponseRaw).toList();
        try {
            String jsonData = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(CACHE_KEY, jsonData, 30, TimeUnit.DAYS);
        } catch (Exception e) {
            // Fallback
        }
        return result;
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

    @Builder
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
        @Builder.Default
        Boolean isBestMatch = false;
    }
}
