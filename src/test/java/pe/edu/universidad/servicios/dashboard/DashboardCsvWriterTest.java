package pe.edu.universidad.servicios.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import pe.edu.universidad.servicios.dashboard.report.DashboardCsvWriter;

class DashboardCsvWriterTest {

    @Test
    void neutralizaFormulasYEscapaSeparadores() {
        String csv = new DashboardCsvWriter()
                .row("a", null, "=SUMA(A1)", "x,y", "di \"hola\"", "@cmd", "-1")
                .build();
        assertThat(csv).isEqualTo("\uFEFFa,,'=SUMA(A1),\"x,y\",\"di \"\"hola\"\"\",'@cmd,'-1\r\n");
    }

    @Test
    void empiezaConBomUtf8ParaExcel() {
        assertThat(new DashboardCsvWriter().row("Soporte informático").build()).startsWith("\uFEFF");
    }
}
