package co.edu.corhuila.opti.sales.adapter.in.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ResponseEntity;

/** Small text PDF renderer. Page streams and xref offsets use bytes, including accented text. */
final class PdfDocument {
    private static final Charset ENCODING = Charset.forName("windows-1252");
    private PdfDocument() { }
    static ResponseEntity<byte[]> download(String name, List<String> rows) {
        return ResponseEntity.ok().header("Content-Type", "application/pdf")
            .header("Content-Disposition", "attachment; filename=\"" + name + "\"")
            .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
            .body(render(rows));
    }
    static byte[] render(List<String> rows) {
        List<String> lines = new ArrayList<>();
        for (String row : rows) {
            String clean = row.replaceAll("[\\p{Cntrl}]", " ");
            do { lines.add(clean.substring(0, Math.min(88, clean.length())));
                clean = clean.substring(Math.min(88, clean.length())); } while (!clean.isEmpty());
        }
        int pages = Math.max(1, (lines.size() + 47) / 48);
        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < pages; p++) kids.append(4 + p * 2).append(" 0 R ");
        objects.add("<< /Type /Pages /Count " + pages + " /Kids [" + kids + "] >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        for (int p = 0; p < pages; p++) {
            StringBuilder stream = new StringBuilder("BT /F1 11 Tf 48 790 Td 15 TL\n");
            for (String line : lines.subList(p * 48, Math.min(lines.size(), (p + 1) * 48))) {
                stream.append('(').append(line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"))
                    .append(") Tj T*\n");
            }
            stream.append("(OptiView - Página ").append(p + 1).append(" de ").append(pages).append(") Tj ET\n");
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> /Contents " + (5 + p * 2) + " 0 R >>");
            objects.add("<< /Length " + stream.toString().getBytes(ENCODING).length + " >>\nstream\n" + stream + "endstream");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("%PDF-1.4\n".getBytes(ENCODING));
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.writeBytes(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(ENCODING));
        }
        int xref = out.size();
        StringBuilder trailer = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) trailer.append(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offset));
        trailer.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        out.writeBytes(trailer.toString().getBytes(ENCODING));
        return out.toByteArray();
    }
}
