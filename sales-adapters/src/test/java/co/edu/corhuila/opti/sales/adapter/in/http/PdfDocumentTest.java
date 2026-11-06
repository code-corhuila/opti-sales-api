package co.edu.corhuila.opti.sales.adapter.in.http;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class PdfDocumentTest {
    @Test void paginatesAndEscapesTextWithAccurateByteOffsets() {
        var rows = new ArrayList<String>();
        rows.add("Fórmula óptica (prueba) \\ ÁÉÍÓÚ ñ");
        for (int i = 0; i < 100; i++) rows.add("Línea " + i);
        byte[] bytes = PdfDocument.render(rows);
        String pdf = new String(bytes, java.nio.charset.Charset.forName("windows-1252"));
        assertThat(pdf).startsWith("%PDF-1.4").contains("/Count 3", "Fórmula óptica \\(prueba\\)", "%%EOF");
        int offset = Integer.parseInt(pdf.substring(pdf.indexOf("startxref\n") + 10).trim().split("\n")[0]);
        assertThat(new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("xref");
    }
}
