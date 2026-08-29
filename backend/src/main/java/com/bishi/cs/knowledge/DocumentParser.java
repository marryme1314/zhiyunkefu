package com.bishi.cs.knowledge;

import com.bishi.cs.common.ApiException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class DocumentParser {
    public String detectType(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".docx")) {
            return "docx";
        }
        if (lower.endsWith(".doc")) {
            return "doc";
        }
        if (lower.endsWith(".md")) {
            return "md";
        }
        if (lower.endsWith(".txt")) {
            return "txt";
        }
        throw new ApiException(400, "仅支持 .txt、.md、.pdf、.docx、.doc 格式");
    }

    public String extract(MultipartFile file, String type) {
        try {
            byte[] bytes = file.getBytes();
            return switch (type) {
                case "pdf" -> extractPdf(bytes);
                case "docx" -> extractDocx(bytes);
                case "doc" -> extractDoc(bytes);
                default -> decodeText(bytes);
            };
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(400, "文件解析失败: " + e.getMessage());
        }
    }

    private String extractPdf(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            String text = new PDFTextStripper().getText(document);
            return normalize(text);
        } catch (Exception e) {
            throw new ApiException(400, "PDF 解析失败: " + e.getMessage());
        }
    }

    private String extractDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return normalize(extractor.getText());
        } catch (Exception e) {
            throw new ApiException(400, "Word(.docx) 解析失败: " + e.getMessage());
        }
    }

    private String extractDoc(byte[] bytes) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(document)) {
            return normalize(extractor.getText());
        } catch (Exception e) {
            throw new ApiException(400, "Word(.doc) 解析失败: " + e.getMessage());
        }
    }

    private String decodeText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (!utf8.contains("\uFFFD")) {
            return utf8;
        }
        return new String(bytes, Charset.forName("GBK"));
    }

    private static String normalize(String text) {
        return text == null ? "" : text.trim();
    }
}
