package com.prodigal.travel.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.prodigal.travel.constants.TravelConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description PDF生成工具
 * @since 2026/4/3
 */
public class PDFGenerationTool {
    public static final String FILE_DIR = TravelConstant.FILE_SAVE_PATH +"/"+TravelConstant.PDF_DIR;
    @Tool(description = "Generate a PDF file with given content")
    public String generatePDF(@ToolParam(description = "content to be included in the PDF") String content,
                              @ToolParam(description = "Name of the file to save the generated PDF") String fileName) {
        String filePath = FILE_DIR + "/" + fileName;
        try {
            FileUtil.mkdir(FILE_DIR);//创建目录
            try (
                    //创建pdfwriter 和 pdfDocument 对象
                    PdfWriter writer = new PdfWriter(filePath);
                    PdfDocument pdfDocument = new PdfDocument(writer);
                    Document document = new Document(pdfDocument)) {
                //使用内置中文字体
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light","UniGB-UCS2-H");

                //自定义字体（需下载字体到本地
//              String pdfFont = Paths.get("src/main/resources/fonts/STSongStd-Light.ttf").toAbsolutePath().toString();
//              PdfFont font = PdfFontFactory.createFont(pdfFont, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                document.setFont(font);
                //创建段落
                Paragraph paragraph = new Paragraph(content).setTextAlignment(TextAlignment.CENTER);;
                //将段落添加到文档中
                document.add(paragraph);
                document.close();
            }
            return "PDF generate to " + filePath;
        } catch (Exception e) {
            return "PDF generate failed：" + e.getMessage();
        }
    }
}
