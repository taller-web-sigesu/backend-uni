package pe.edu.universidad.servicios.attachments.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import pe.edu.universidad.servicios.shared.config.AppProperties;

/**
 * Evidencias en una carpeta del servidor (volumen Docker en local). Las claves nunca salen de la carpeta base.
 */
@Component
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalAttachmentStorage implements AttachmentStorage {

    private final Path base;

    public LocalAttachmentStorage(AppProperties properties) {
        this.base = Path.of(properties.storage().localDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(base);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo crear la carpeta de evidencias " + base, ex);
        }
    }

    @Override
    public String provider() {
        return "LOCAL";
    }

    @Override
    public void put(String key, byte[] content, String mime) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo guardar la evidencia " + key, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo borrar la evidencia " + key, ex);
        }
    }

    @Override
    public Optional<URI> signedDownloadUrl(String key, Duration ttl) {
        return Optional.empty();
    }

    @Override
    public InputStream open(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer la evidencia " + key, ex);
        }
    }

    private Path resolve(String key) {
        Path target = base.resolve(key).normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            throw new IllegalArgumentException("Clave de evidencia fuera de la carpeta base.");
        }
        return target;
    }
}
