package co.edu.corhuila.opti.sales.adapter.in.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;

/** Lightweight A4 layout with wrapped cells and repeated table headers. */
final class PdfDocument {
    private static final Charset ENCODING = Charset.forName("windows-1252");
    private static final String INK = "0.08 0.15 0.24", MUTED = "0.28 0.36 0.45",
            NAVY = "0.06 0.14 0.24", TEAL = "0.00 0.45 0.45", WHITE = "1 1 1",
            PALE = "0.94 0.97 0.98", LINE = "0.84 0.89 0.92";
    private static final int LEFT = 42, WIDTH = 511, BOTTOM = 68;
    private final String title, subtitle;
    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder page;
    private int y;

    PdfDocument(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
        nextPage();
    }

    private void nextPage() {
        page = new StringBuilder();
        pages.add(page);
        rectangle(0, 728, 595, 114, NAVY);
        rectangle(0, 725, 595, 3, TEAL);
        text(LEFT, 804, 13, true, "0.45 0.93 0.87", "OptiView");
        text(LEFT, 765, 27, true, WHITE, title);
        text(LEFT, 743, 11, false, "0.83 0.89 0.94", subtitle);
        y = 697;
    }

    void section(String title) {
        space(35);
        text(LEFT, y, 13, true, TEAL, title);
        y -= 19;
    }

    void fields(String... pairs) {
        final int columnWidth = WIDTH / 2;
        for (int i = 0; i < pairs.length; i += 4) {
            var first = wrap(pairs[i + 1], columnWidth - 34, 11);
            var second = i + 3 < pairs.length ? wrap(pairs[i + 3], columnWidth - 34, 11) : List.<String>of();
            int height = 29 + 14 * Math.max(first.size(), second.size());
            space(height + 7);
            rectangle(LEFT, y - height, WIDTH, height, PALE);
            field(LEFT + 13, pairs[i], first);
            if (!second.isEmpty()) field(LEFT + columnWidth + 10, pairs[i + 2], second);
            y -= height + 7;
        }
        y -= 10;
    }

    private void field(int x, String label, List<String> lines) {
        text(x, y - 15, 9, true, MUTED, label);
        for (int i = 0; i < lines.size(); i++) text(x, y - 32 - 14 * i, 11, false, INK, lines.get(i));
    }

    void table(String[] headers, int[] widths, List<List<String>> rows) {
        space(60);
        tableHeader(headers, widths);
        int index = 0;
        for (var row : rows) {
            var cells = new ArrayList<List<String>>();
            int lineCount = 1;
            for (int i = 0; i < row.size(); i++) {
                var lines = wrap(row.get(i), widths[i] - 18, 10);
                cells.add(lines);
                lineCount = Math.max(lineCount, lines.size());
            }
            int height = Math.max(31, lineCount * 14 + 16);
            if (y - height < BOTTOM) {
                nextPage();
                tableHeader(headers, widths);
            }
            rectangle(LEFT, y - height, WIDTH, height, index++ % 2 == 0 ? PALE : WHITE);
            rectangle(LEFT, y - height, WIDTH, 1, LINE);
            int x = LEFT;
            for (int i = 0; i < cells.size(); i++) {
                var lines = cells.get(i);
                for (int j = 0; j < lines.size(); j++) {
                    text(x + 9, y - 17 - 14 * j, 10, i == 0, INK, lines.get(j));
                }
                x += widths[i];
            }
            y -= height;
        }
        y -= 26;
    }

    private void tableHeader(String[] headers, int[] widths) {
        rectangle(LEFT, y - 26, WIDTH, 26, NAVY);
        int x = LEFT;
        for (int i = 0; i < headers.length; i++) {
            text(x + 9, y - 17, 9, true, WHITE, headers[i]);
            x += widths[i];
        }
        y -= 26;
    }

    void totals(String total, String paid, String balance) {
        space(98);
        int x = 279;
        rectangle(x, y - 86, 274, 86, PALE);
        text(x + 15, y - 19, 10, true, INK, "Total");
        text(x + 142, y - 19, 11, true, INK, total);
        text(x + 15, y - 38, 10, false, MUTED, "Abonado");
        text(x + 142, y - 38, 11, false, INK, paid);
        rectangle(x, y - 86, 274, 33, TEAL);
        text(x + 15, y - 74, 11, true, WHITE, "Saldo pendiente");
        text(x + 142, y - 74, 12, true, WHITE, balance);
        y -= 105;
    }

    void note(String value) {
        for (String line : wrap(value, WIDTH, 9)) {
            space(16);
            text(LEFT, y, 9, false, MUTED, line);
            y -= 14;
        }
    }

    private void space(int height) {
        if (y - height < BOTTOM) nextPage();
    }

    private void rectangle(int x, int yy, int w, int h, String color) {
        page.append(color).append(" rg ").append(x).append(' ').append(yy).append(' ')
            .append(w).append(' ').append(h).append(" re f\n");
    }

    private void text(int x, int yy, int size, boolean bold, String color, String value) {
        page.append("BT ").append(color).append(" rg /").append(bold ? "F2" : "F1").append(' ')
            .append(size).append(" Tf 1 0 0 1 ").append(x).append(' ').append(yy).append(" Tm (")
            .append(clean(value).replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"))
            .append(") Tj ET\n");
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? "-" : value.replaceAll("[\\p{Cntrl}]", " ").trim();
    }

    private static List<String> wrap(String value, int width, int size) {
        String rest = clean(value);
        var lines = new ArrayList<String>();
        while (!rest.isEmpty()) {
            int max = 0;
            double used = 0;
            while (max < rest.length() && used + size * glyphWidth(rest.charAt(max)) <= width) {
                used += size * glyphWidth(rest.charAt(max++));
            }
            if (max == rest.length()) { lines.add(rest); break; }
            max = Math.max(1, max);
            int at = rest.lastIndexOf(' ', max);
            if (at < max / 2 || at == 0) at = max;
            lines.add(rest.substring(0, at));
            rest = rest.substring(at).stripLeading();
        }
        return lines;
    }

    private static double glyphWidth(char value) {
        if (" .,;:!|ijlI'".indexOf(value) >= 0) return 0.35;
        if ("MWmw@%".indexOf(value) >= 0) return 0.96;
        if (Character.isUpperCase(value)) return 0.73;
        if (Character.isDigit(value)) return 0.60;
        return 0.62;
    }

    ResponseEntity<byte[]> download(String filename) {
        return ResponseEntity.ok().header("Content-Type", "application/pdf")
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
            .body(render());
    }

    byte[] render() {
        var objects = new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int p = 0; p < pages.size(); p++) kids.append(5 + p * 2).append(" 0 R ");
        objects.add("<< /Type /Pages /Count " + pages.size() + " /Kids [" + kids + "] >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int p = 0; p < pages.size(); p++) {
            page = new StringBuilder(pages.get(p));
            rectangle(LEFT, 47, WIDTH, 1, LINE);
            text(LEFT, 32, 9, false, MUTED, "OptiView  /  " + title);
            text(468, 32, 9, false, MUTED, "Página " + (p + 1) + " de " + pages.size());
            var stream = page.toString();
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
                + "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (6 + p * 2) + " 0 R >>");
            objects.add("<< /Length " + stream.getBytes(ENCODING).length + " >>\nstream\n" + stream + "endstream");
        }
        var out = new ByteArrayOutputStream();
        out.writeBytes("%PDF-1.4\n".getBytes(ENCODING));
        var offsets = new ArrayList<Integer>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.writeBytes(((i + 1) + " 0 obj\n" + objects.get(i) + "\nendobj\n").getBytes(ENCODING));
        }
        int xref = out.size();
        var trailer = new StringBuilder("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) trailer.append(String.format(Locale.ROOT, "%010d 00000 n \n", offset));
        trailer.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
            .append(xref).append("\n%%EOF\n");
        out.writeBytes(trailer.toString().getBytes(ENCODING));
        return out.toByteArray();
    }
}
