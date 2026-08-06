# 🪙 FinanceCare Backend (Capstone Project)

แอปพลิเคชันระบบหลังบ้านของ **FinanceCare** — แพลตฟอร์มช่วยวางแผนการเงิน จัดการหนี้สิน และติดตามธุรกรรมอัจฉริยะ (Debt and Finance Management Platform) ที่ช่วยผู้ใช้งานวิเคราะห์และปรับปรุงสุขภาพทางการเงิน รวมถึงการคำนวณและเลือกกลยุทธ์ปลดหนี้ที่เหมาะสมกับแต่ละบุคคล

---

## 🚀 ฟีเจอร์หลัก (Key Features)

* **🔑 Authentication & Security (JWT)**
  * ระบบลงทะเบียนและเข้าสู่ระบบด้วยสิทธิ์การใช้งานผ่าน JSON Web Token (JWT)
  * มีระบบยืนยันตัวตนและรหัส OTP ผ่านทางอีเมล (Email Verification)
* **📊 Transaction & Budget Management**
  * บันทึกรายการรายรับ-รายจ่ายประจำวัน แบ่งตามหมวดหมู่ (Categories) อย่างเป็นระเบียบ
  * ตั้งค่าและติดตามงบประมาณ (Budget) เพื่อคุมค่าใช้จ่ายให้อยู่ในกรอบที่กำหนด
* **🛡️ Debt & Repayment Planning (Snowball & Avalanche)**
  * คำนวณแผนการชำระหนี้ด้วยกลยุทธ์ที่มีประสิทธิภาพ:
    * **Debt Snowball**: มุ่งเน้นชำระหนี้ก้อนเล็กที่สุดก่อน เพื่อสร้างขวัญกำลังใจ
    * **Debt Avalanche**: มุ่งเน้นชำระหนี้ที่อัตราดอกเบี้ยสูงที่สุดก่อน เพื่อประหยัดเงินจากดอกเบี้ยสะสม
* **📑 Slip Verification & OCR**
  * ระบบสแกนและตรวจสอบความถูกต้องของสลิปโอนเงินผ่าน **EasyOCR Service**
  * อัปโหลดสลิปและเก็บไฟล์ภาพลงระบบ Cloud Object Storage
* **📂 Cloud Object Storage (MinIO)**
  * ใช้ **MinIO** ในการจัดการและเก็บเอกสาร/สลิปโอนเงินอย่างปลอดภัย
* **📧 Notification Service**
  * แจ้งเตือนผู้ใช้ล่วงหน้าเมื่อถึงกำหนดชำระหนี้ หรือการเข้าสู่ระบบ/ทำธุรกรรมผ่านระบบอีเมล
* **💼 Job Recommendation Integration**
  * เชื่อมต่อกับ **Jooble API** เพื่อให้ผู้ใช้สามารถค้นหาช่องทางและตำแหน่งงานเสริม เพื่อนำรายได้มาช่วยในการแบ่งเบาภาระหนี้สิน
* **📖 REST API Documentation (Swagger)**
  * แสดงคู่มือการทดสอบและเรียกใช้งาน API ด้วย **Swagger UI (Springdoc OpenAPI)**

---

## 🛠️ Stack เทคโนโลยี (Technology Stack)

* **Programming Language:** Java 21 (พร้อมรองรับ Kotlin stdlib / reflect)
* **Framework:** Spring Boot 3.4.1 (Spring Security, Spring Data JPA, Spring Mail)
* **Database:** PostgreSQL (Production) และ H2 Database (สำหรับ Automated Test)
* **Caching & Session:** Redis 7.2
* **Storage Engine:** MinIO Object Storage
* **Integrations:**
  * **EasyOCR API**: สำหรับประมวลผล OCR ตรวจสอบสลิป
  * **Jooble API**: บริการค้นหาข้อมูลงานภายนอก
  * **Firebase Admin**: รองรับการขยายผลแจ้งเตือนและสิทธิ์บนแพลตฟอร์ม
* **Build System:** Gradle (Wrapper 8.x)
* **Quality Assurance:**
  * **Jacoco**: ตรวจวัด Code Coverage
  * **SonarQube**: วิเคราะห์ความปลอดภัยและ Clean Code ของระบบ

---

## 🏗️ สถาปัตยกรรมระบบและ Services ใน Docker

ระบบแบ่งออกเป็น Services ย่อยๆ ที่สื่อสารกันผ่านเครือข่าย Docker `finance-net` ดังนี้:

* **spring-app (`FinanceCare Backend`):** รันบนพอร์ต `8080` (Context path: `/api`)
* **redis:** เก็บข้อมูล Session, OTP Cache และการทำงานแบบชั่วคราว
* **easyocr-service:** บริการ OCR สำหรับตรวจสแกนสลิปโอนเงิน รันบนพอร์ต `8000`
* **minio-service:** บริการเก็บ Object Storage บนพอร์ต `9000` และมีหน้า Admin Console อยู่ที่พอร์ต `9001`
* **postgres-db:** ฐานข้อมูลหลัก (ตามการตั้งค่า URL Connection)

---

## 📦 วิธีการติดตั้งและเริ่มต้นใช้งาน (Setup & Run)

### 📋 สิ่งที่จำเป็นในการรัน (Prerequisites)
* **Docker** และ **Docker Compose**
* **Java Development Kit (JDK) 21** (หากต้องการรัน/แก้ไขโค้ดจากเครื่องโลคอลโดยตรง)

### ⚙️ ขั้นตอนการรันระบบด้วย Docker Compose

1. **สร้าง Docker Network ภายนอก** (เนื่องจาก Compose อ้างอิง network นี้ภายนอก)
   ```bash
   docker network create finance-net
   ```

2. **สั่ง Build และรัน Services ทั้งหมดขึ้นมา**
   ```bash
   docker-compose up -d --build
   ```

3. **การเข้าใช้งานบริการต่างๆ:**
   * **Backend REST API:** `http://localhost:8080/api`
   * **Swagger UI (API Docs):** `http://localhost:8080/api/swagger-ui/index.html`
   * **MinIO Object Console:** `http://localhost:9001` (Username: `admin` / Password: `password123`)

---

## 🧪 การทดสอบระบบ (Testing)

คุณสามารถสั่งรันการทดสอบ Unit Tests และ Integration Tests ผ่าน Gradle Wrapper หรือไฟล์สคริปต์ `.bat` (สำหรับ Windows) ที่อยู่ใน Root Directory:

* **สั่งรัน Test ทั่วไปผ่าน Gradle:**
  ```bash
  ./gradlew test
  ```
* **ใช้ Script สำเร็จรูปบน Windows:**
  * `run_tests.bat` : สั่งรันชุดการทดสอบทั้งหมดของระบบ
  * `run_auth.bat` : สั่งรันการทดสอบเฉพาะระบบ Authentication (`AuthControllerTest`)
  * `test_repayment.bat` : สั่งรันการทดสอบการวางแผนและจัดการหนี้สิน (Repayment Plan)
  * `run_debug.bat` : รันชุดการทดสอบ Debugger และบันทึก Log การรันลงใน `debug_output.txt`

---

## 📊 การวัด Code Coverage และคุณภาพโค้ด

### 📈 Jacoco Report
หลังจากการรันการทดสอบ `./gradlew test` ระบบจะคำนวณ Code Coverage โดยอัตโนมัติ (ข้ามคลาสประเภท Config, Entity, DTO, และ Exception ตามที่เราคัดแยกไว้ใน `build.gradle`):
* ไฟล์รายงาน HTML จะบันทึกไว้ที่:
  `build/reports/jacoco/test/html/index.html`

### 🔍 SonarQube Integration
สามารถส่งผลลัพธ์ Coverage และสถิติโค้ดไปยังระบบ SonarQube Local Dashboard (รันที่พอร์ต `9005`):
```bash
./gradlew sonar
```

---

*พัฒนาขึ้นสำหรับโครงการ Capstone Project — เพื่อยกระดับและดูแลสุขภาพทางการเงินของผู้ใช้งานอย่างเป็นระบบ 🪙*
=======
# backend
