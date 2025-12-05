package server.format;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public final class DocxUtil {
    private DocxUtil() {}


    public static void writeNormalizedDocx(String title, String text, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (var out = Files.newOutputStream(target); var zip = new ZipOutputStream(out)) {
            // [Content_Types].xml
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // _rels/.rels
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write(RELS.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // word/_rels/.rels
            zip.putNextEntry(new ZipEntry("word/_rels/.rels"));
            zip.write(WORD_RELS.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // word/document.xml
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(buildDocumentXml(title, text).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // word/styles.xml
            zip.putNextEntry(new ZipEntry("word/styles.xml"));
            zip.write(STYLES.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    public static String extractPlainText(byte[] docxBytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(docxBytes); ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if ("word/document.xml".equalsIgnoreCase(e.getName())) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    zip.transferTo(bos);
                    String xml = bos.toString(StandardCharsets.UTF_8);
                    return xml.replaceAll("<[^>]+>", " ")
                            .replaceAll("\\s+", " ")
                            .trim();
                }
            }
        }
        return "";
    }

    private static String buildDocumentXml(String title, String body) {
        String safeTitle = escapeXml(title);
        String safeBody  = escapeXml(body).replace("\n", "</w:t></w:r></w:p><w:p><w:r><w:t>");
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:wpc="http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas" xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:m="http://schemas.openxmlformats.org/officeDocument/2006/math" xmlns:v="urn:schemas-microsoft-com:vml" xmlns:wp14="http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:w10="urn:schemas-microsoft-com:office:word" xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:w14="http://schemas.microsoft.com/office/2010/wordml" xmlns:wpg="http://schemas.microsoft.com/office/word/2010/wordprocessingGroup" xmlns:wpi="http://schemas.microsoft.com/office/word/2010/wordprocessingInk" xmlns:wne="http://schemas.microsoft.com/office/2006/wordml" xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape" mc:Ignorable="w14 wp14">
                  <w:body>
                    <w:p>
                      <w:pPr><w:pStyle w:val="Title"/></w:pPr>
                      <w:r><w:t>%s</w:t></w:r>
                    </w:p>
                    <w:p>
                      <w:r><w:t>%s</w:t></w:r>
                    </w:p>
                    <w:sectPr><w:pgSz w:w=11906 w:h=16838/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/></w:sectPr>
                  </w:body>
                </w:document>
                """.formatted(safeTitle, safeBody);
    }

    private static String escapeXml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
            </Types>
            """;

    private static final String RELS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
            """;

    private static final String WORD_RELS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            </Relationships>
            """;

    private static final String STYLES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:style w:type="paragraph" w:styleId="Title">
                <w:name w:val="Title"/>
                <w:qFormat/>
                <w:basedOn w:val="Normal"/>
                <w:pPr><w:jc w:val="center"/></w:pPr>
                <w:rPr><w:b/><w:sz w:val="32"/></w:rPr>
              </w:style>
              <w:style w:type="paragraph" w:styleId="Normal">
                <w:name w:val="Normal"/>
                <w:qFormat/>
                <w:rPr><w:sz w:val="24"/></w:rPr>
              </w:style>
            </w:styles>
            """;
}
