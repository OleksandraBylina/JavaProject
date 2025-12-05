package server.format;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxUtil {
    private XlsxUtil() {}

    public record Row(String storyId, String title, String author) {}

    public static byte[] buildAssignmentsSheet(List<Row> rows) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bos)) {
            // [Content_Types].xml
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write(CONTENT_TYPES.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // _rels/.rels
            zip.putNextEntry(new ZipEntry("_rels/.rels"));
            zip.write(RELS.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // xl/_rels/workbook.xml.rels
            zip.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
            zip.write(WORKBOOK_RELS.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // xl/workbook.xml
            zip.putNextEntry(new ZipEntry("xl/workbook.xml"));
            zip.write(WORKBOOK.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // sheet data
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zip.write(buildSheetXml(rows).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bos.toByteArray();
    }

    private static String buildSheetXml(List<Row> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ");
        sb.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">\n");
        sb.append("  <sheetData>\n");
        // header
        sb.append(row(1, new String[]{"Story ID", "Title", "Author"}));
        int idx = 2;
        for (Row r : rows) {
            sb.append(row(idx++, new String[]{r.storyId(), r.title(), r.author()}));
        }
        sb.append("  </sheetData>\n</worksheet>\n");
        return sb.toString();
    }

    private static String row(int rowIndex, String[] cells) {
        StringBuilder sb = new StringBuilder();
        sb.append("    <row r=\"").append(rowIndex).append("\">\n");
        for (int i = 0; i < cells.length; i++) {
            char col = (char) ('A' + i);
            sb.append("      <c r=\"").append(col).append(rowIndex).append("\" t=\"inlineStr\"><is><t>")
                    .append(escape(cells[i])).append("</t></is></c>\n");
        }
        sb.append("    </row>\n");
        return sb.toString();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static final String CONTENT_TYPES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
            """;

    private static final String RELS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
            """;

    private static final String WORKBOOK_RELS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            </Relationships>
            """;

    private static final String WORKBOOK = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Assignments" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
            """;
}
