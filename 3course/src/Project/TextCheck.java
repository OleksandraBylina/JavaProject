package Project;

import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.*;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;



public class TextCheck {

    // --- 1) чтение текста из .txt или .docx ---
    public static String readText(Path path) throws Exception {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".txt")) {
            String raw = Files.readString(path);
            return normalize(raw);
        } else if (name.endsWith(".docx")) {
            try (InputStream in = Files.newInputStream(path);
                 XWPFDocument doc = new XWPFDocument(OPCPackage.open(in));
                 XWPFWordExtractor ext = new XWPFWordExtractor(doc)) {
                return normalize(ext.getText());
            }
        }
        throw new IllegalArgumentException("Поддерживаются только .txt и .docx");
    }

    // --- 2) нормализация текста ---
    public static String normalize(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') s = s.substring(1); // BOM
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        s = Normalizer.normalize(s, Normalizer.Form.NFC);

        // схлопываем многократные пустые строки
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }

    // --- 3) генерация стандартизованного DOCX ---
    public static void writeStandardDocx(String titleOrNull, String text, Path out) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            // Поля страницы ~ 25 мм
            CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                    ? doc.getDocument().getBody().getSectPr()
                    : doc.getDocument().getBody().addNewSectPr();
            CTPageMar mar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
            mar.setTop(1418); mar.setBottom(1418); mar.setLeft(1418); mar.setRight(1418); // 1418 twips ≈ 25 мм

            // Нумерация страниц внизу по центру
            XWPFParagraph footerP = doc.createParagraph();
            footerP.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun fr = footerP.createRun();
            fr.getCTR().addNewFldChar().setFldCharType(STFldCharType.BEGIN);
            fr = footerP.createRun(); fr.setText(" PAGE ");
            fr.getCTR().addNewFldChar().setFldCharType(STFldCharType.SEPARATE);
            fr = footerP.createRun(); fr.setText("1");
            fr.getCTR().addNewFldChar().setFldCharType(STFldCharType.END);
            doc.createFooter(HeaderFooterType.DEFAULT, new XWPFParagraph[]{footerP});

            // Заголовок (необязателен)
            if (titleOrNull != null && !titleOrNull.isBlank()) {
                XWPFParagraph h = doc.createParagraph();
                h.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun r = h.createRun();
                r.setBold(true);
                r.setFontFamily("Times New Roman");
                r.setFontSize(14);
                r.setText(titleOrNull.trim());
                addEmptyLine(doc);
            }

            // Основной текст: 12 pt, TNR, 1.5 интервал, отступ 1.25 см, выравнивание по ширине
            for (String para : splitIntoParagraphs(text)) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.BOTH);
                p.setSpacingBetween(1.5);
                p.setIndentationFirstLine(709); // ~1.25 см (в twips)
                XWPFRun r = p.createRun();
                r.setFontFamily("Times New Roman");
                r.setFontSize(12);
                r.setText(para);
            }

            try (OutputStream os = Files.newOutputStream(out)) {
                doc.write(os);
            }
        }
    }

    private static void addEmptyLine(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun r = p.createRun();
        r.setText("");
    }

    private static List<String> splitIntoParagraphs(String text) {
        List<String> res = new ArrayList<>();
        for (String block : text.split("\\n\\n")) {
            res.add(block.strip());
        }
        return res;
    }

    // --- демо ---
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java NormalizeAndMakeDocx <input.(txt|docx)> <output.docx> [title]");
            return;
        }
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        String title = args.length >= 3 ? args[2] : null;

        String text = readText(in);
        writeStandardDocx(title, text, out);
        System.out.println("Готово: " + out.toAbsolutePath());
    }
}
