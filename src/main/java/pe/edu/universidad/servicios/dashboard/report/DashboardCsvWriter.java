package pe.edu.universidad.servicios.dashboard.report;

import java.util.Arrays;
import java.util.List;

/**
 * CSV RFC 4180 con neutralización de valores que una hoja de cálculo interpretaría como fórmula.
 */
public final class DashboardCsvWriter {

    private final StringBuilder out = new StringBuilder();

    public DashboardCsvWriter row(List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(values.get(i)));
        }
        out.append("\r\n");
        return this;
    }

    public DashboardCsvWriter row(String... values) {
        return row(Arrays.asList(values));
    }

    public String build() {
        return out.toString();
    }

    static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw;
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            value = '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
