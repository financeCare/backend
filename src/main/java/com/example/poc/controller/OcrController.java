package com.example.poc.controller;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.poc.service.OcrService.extractAmounts;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {
    @PostMapping("/extract")
    public String extractText(@RequestParam("file") MultipartFile file) throws Exception {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");
        tesseract.setLanguage("tha+eng");

        File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + file.getOriginalFilename());
        file.transferTo(convFile);

        String text = tesseract.doOCR(convFile).replaceAll(" ","");
        System.out.println(text);

        Pattern pattern = Pattern.compile("\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(0);
        }
        return "พี่หาไม่เจออะน้องง";
    }

}
