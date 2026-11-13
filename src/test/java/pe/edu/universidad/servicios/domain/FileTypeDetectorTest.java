package pe.edu.universidad.servicios.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import pe.edu.universidad.servicios.attachments.domain.FileTypeDetector;
import pe.edu.universidad.servicios.attachments.domain.FileTypeDetector.DetectedType;

class FileTypeDetectorTest {

    static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    @Test
    void detectaJpegPngYPdfPorContenido() {
        assertThat(FileTypeDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00))).contains(DetectedType.JPEG);
        assertThat(FileTypeDetector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00))).contains(DetectedType.PNG);
        assertThat(FileTypeDetector.detect("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))).contains(DetectedType.PDF);
        assertThat(DetectedType.JPEG.mime()).isEqualTo("image/jpeg");
        assertThat(DetectedType.PNG.extension()).isEqualTo("png");
        assertThat(DetectedType.PDF.mime()).isEqualTo("application/pdf");
    }

    @Test
    void rechazaEjecutablesGifVacioYTruncado() {
        assertThat(FileTypeDetector.detect("MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[0])).isEmpty();
        assertThat(FileTypeDetector.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(FileTypeDetector.detect(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(FileTypeDetector.detect(bytes(0x89, 0x50, 0x4E, 0x47))).isEmpty();
    }
}
