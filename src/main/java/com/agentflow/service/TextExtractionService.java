package com.agentflow.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本提取服务：支�?TXT、PDF、DOCX 格式的文本提取，基于文档标题结构进行分块，无标题时回退到固定大小分�?
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    /** 固定分块大小（字符数），无标题时回退使用 */
    private static final int CHUNK_SIZE = 1500;
    /** 固定分块重叠（字符数），无标题时回退使用 */
    private static final int CHUNK_OVERLAP = 100;
    /** 单块最大字符数，超过此限制需二次分片（Embedding 模型 token 上限 8192，中文约 1~2 token/字符，保守取 6000�?*/
    private static final int MAX_CHUNK_SIZE = 6000;

    /** 标题匹配正则：支�?Markdown(#)、中文章�?第一�?第一�?、数字编�?1./1.1)、中文数�?一�?（一�? */
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "(?m)^(" +
                    "#{1,6}\\s+.+" +                        // Markdown 标题: # Title
                    "|第[一二三四五六七八九十百千\\d]+[章节节部分篇].*" + // 中文章节: 第一�?第一�?
                    "|\\d+(?:\\.\\d+)*\\.\\s*.+" +           // 数字编号: 1. / 1.1. / 1.1.1.
                    "|[一二三四五六七八九十]+�?+" +           // 中文数字: 一、二�?
                    "|（[一二三四五六七八九十\\d]+�?+" +      // 带括�? （一）（1�?
                    ")$"
    );

    /**
     * 从上传文件中提取文本并依据标题结构分�?
     */
    public List<String> extractAndChunk(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为�?);
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        List<String> chunks = extractAndChunkByTitle(file, extension);

        log.info("文件 [{}] 按标题分块完成，�?{} 个块", fileName, chunks.size());
        return chunks;
    }

    /**
     * 根据文件扩展名提取文本并按标题分�?
     */
    private List<String> extractAndChunkByTitle(MultipartFile file, String extension) throws IOException {
        return switch (extension) {
            case "docx", "doc" -> extractFromDocxByHeading(file);
            case "txt" -> extractFromTxtByHeading(file);
            case "pdf" -> extractFromPdfByHeading(file);
            default -> throw new IllegalArgumentException("不支持的文件格式: " + extension);
        };
    }

    /**
     * �?TXT 文件提取文本，按标题模式分块
     */
    private List<String> extractFromTxtByHeading(MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        return splitByHeadingPattern(text);
    }

    /**
     * �?PDF 文件提取文本，按标题模式分块
     */
    private List<String> extractFromPdfByHeading(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return splitByHeadingPattern(text);
        }
    }

    /**
     * �?DOCX 文件提取文本，依据段落标题样式（Heading1~Heading6）分�?
     */
    private List<String> extractFromDocxByHeading(MultipartFile file) throws IOException {
        List<String> chunks = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {

            String currentTitle = null;
            StringBuilder currentContent = new StringBuilder();

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String style = paragraph.getStyle();
                boolean isHeading = style != null && style.toLowerCase().startsWith("heading");

                if (isHeading) {
                    // 保存前一个章�?
                    if (currentContent.length() > 0 || currentTitle != null) {
                        chunks.add(buildChunk(currentTitle, currentContent.toString()));
                    }
                    currentTitle = paragraph.getText().trim();
                    currentContent = new StringBuilder();
                } else {
                    String text = paragraph.getText().trim();
                    if (!text.isEmpty()) {
                        if (currentContent.length() > 0) {
                            currentContent.append("\n");
                        }
                        currentContent.append(text);
                    }
                }
            }

            // 保存最后一个章�?
            if (currentContent.length() > 0 || currentTitle != null) {
                chunks.add(buildChunk(currentTitle, currentContent.toString()));
            }

            // 未检测到标题样式，回退到固定大小分�?
            if (chunks.isEmpty()) {
                log.info("DOCX 未检测到标题样式，回退到固定大小分�?);
                String fullText = extractFullTextFromDocx(document);
                return chunkByFixedSize(fullText);
            }
        }

        log.info("DOCX 按标题样式分块，�?{} 个块", chunks.size());
        return splitOversizedChunks(chunks);
    }

    /**
     * �?DOCX 文档提取全部纯文本（回退时使用）
     */
    private String extractFullTextFromDocx(XWPFDocument document) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            String text = paragraph.getText().trim();
            if (!text.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(text);
            }
        }
        return sb.toString();
    }

    /**
     * 按正则标题模式将文本分块（适用�?TXT、PDF�?
     */
    private List<String> splitByHeadingPattern(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        Matcher matcher = HEADING_PATTERN.matcher(text);
        List<int[]> headingPositions = new ArrayList<>();

        while (matcher.find()) {
            headingPositions.add(new int[]{matcher.start(), matcher.end()});
        }

        if (headingPositions.isEmpty()) {
            // 未检测到标题，回退到固定大小分�?
            log.info("未检测到标题，回退到固定大小分�?);
            return chunkByFixedSize(text);
        }

        // 第一个标题之前的内容作为引言�?
        if (headingPositions.get(0)[0] > 0) {
            String preamble = text.substring(0, headingPositions.get(0)[0]).trim();
            if (!preamble.isEmpty()) {
                chunks.add(preamble);
            }
        }

        // 每个标题到下一个标题之间的内容作为一个块
        for (int i = 0; i < headingPositions.size(); i++) {
            int start = headingPositions.get(i)[0];
            int end = (i + 1 < headingPositions.size()) ? headingPositions.get(i + 1)[0] : text.length();
            String section = text.substring(start, end).trim();
            if (!section.isEmpty()) {
                chunks.add(section);
            }
        }

        log.info("按标题模式分块，检测到 {} 个标题，�?{} 个块", headingPositions.size(), chunks.size());
        return splitOversizedChunks(chunks);
    }

    /**
     * 对超长分块进行二次分片，确保每块不超�?MAX_CHUNK_SIZE
     */
    private List<String> splitOversizedChunks(List<String> chunks) {
        List<String> result = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk.length() > MAX_CHUNK_SIZE) {
                log.info("分块超长（{} 字符），进行二次分片", chunk.length());
                result.addAll(chunkByFixedSize(chunk));
            } else {
                result.add(chunk);
            }
        }
        return result;
    }

    /**
     * 将文本按固定大小分块，块之间有重叠（无标题时的回退策略�?
     */
    private List<String> chunkByFixedSize(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        text = text.replaceAll("\\s+", " ").trim();

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            start += CHUNK_SIZE - CHUNK_OVERLAP;
        }

        log.info("固定大小分块完成，共 {} 个块", chunks.size());
        return chunks;
    }

    /**
     * 构建分块内容：有标题时拼接标题与内容，无标题时仅返回内容
     */
    private String buildChunk(String title, String content) {
        if (title != null && !title.isEmpty()) {
            return title + "\n" + content.trim();
        }
        return content.trim();
    }
}
