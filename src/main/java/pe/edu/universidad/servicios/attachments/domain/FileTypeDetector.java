package pe.edu.universidad.servicios.attachments.domain;

import java.util.Optional;

/**
 * Detecta el tipo de una evidencia por sus primeros bytes (HU-26 CA-02), sin confiar en la extensión ni en el
 * {@code Content-Type} declarado por el cliente.
 */
public final class FileTypeDetector {

    public enum DetectedType {
        JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        PNG("image/png", "png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
        PDF("application/pdf", "pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D});

        private final String mime;
        private final String extension;
        private final byte[] signature;

        DetectedType(String mime, String extension, byte[] signature) {
            this.mime = mime;
            this.extension = extension;
            this.signature = signature;
        }

        public String mime() {
            return mime;
        }

        public String extension() {
            return extension;
        }

        boolean matches(byte[] head) {
            if (head.length < signature.length) {
                return false;
            }
            for (int i = 0; i < signature.length; i++) {
                if (head[i] != signature[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    private FileTypeDetector() {
    }

    public static Optional<DetectedType> detect(byte[] head) {
        for (DetectedType type : DetectedType.values()) {
            if (type.matches(head)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
