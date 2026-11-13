package pe.edu.universidad.servicios.attachments.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.shared.config.AppProperties.Storage.Supabase;

/**
 * Evidencias en un bucket privado de Supabase Storage (ADR-0004, punto 10) mediante su API REST con la clave de
 * servicio. La clave nunca aparece en mensajes de error ni en logs. Se registra en {@link SupabaseStorageConfiguration}.
 */
public class SupabaseAttachmentStorage implements AttachmentStorage {

    private final RestClient http;
    private final String baseUrl;
    private final String bucket;

    public SupabaseAttachmentStorage(RestClient.Builder builder, AppProperties properties) {
        Supabase config = properties.storage().supabase();
        if (config == null || isBlank(config.url())) {
            throw new IllegalStateException("APP_STORAGE_SUPABASE_URL es obligatorio con APP_STORAGE_PROVIDER=supabase.");
        }
        if (isBlank(config.serviceKey())) {
            throw new IllegalStateException("APP_STORAGE_SUPABASE_SERVICE_KEY es obligatorio con APP_STORAGE_PROVIDER=supabase.");
        }
        this.baseUrl = config.url().replaceAll("/+$", "");
        this.bucket = isBlank(config.bucket()) ? "evidencias-solicitudes" : config.bucket().trim();
        String key = config.serviceKey().trim();
        this.http = builder
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                .defaultHeader("apikey", key)
                .build();
    }

    @Override
    public String provider() {
        return "SUPABASE";
    }

    @Override
    public void put(String key, byte[] content, String mime) {
        call("subir", () -> http.post().uri(objectUri(key))
                .contentType(MediaType.parseMediaType(mime))
                .header("x-upsert", "false")
                .body(content)
                .retrieve().toBodilessEntity());
    }

    @Override
    public void delete(String key) {
        call("borrar", () -> http.method(HttpMethod.DELETE).uri(URI.create(baseUrl + "/storage/v1/object/" + bucket))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("prefixes", List.of(key)))
                .retrieve().toBodilessEntity());
    }

    @Override
    public Optional<URI> signedDownloadUrl(String key, Duration ttl) {
        Map<?, ?> body = call("firmar", () -> http.post()
                .uri(URI.create(baseUrl + "/storage/v1/object/sign/" + bucket + "/" + key))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", ttl.toSeconds()))
                .retrieve().body(Map.class));
        Object signed = body == null ? null : body.get("signedURL");
        if (!(signed instanceof String path) || path.isBlank()) {
            throw new IllegalStateException("Supabase Storage no devolvió una URL firmada.");
        }
        return Optional.of(URI.create(baseUrl + "/storage/v1" + path));
    }

    /** La descarga siempre usa la URL firmada; el backend no transmite archivos de Supabase. */
    @Override
    public InputStream open(String key) {
        throw new UnsupportedOperationException("Las evidencias de Supabase se descargan con URL firmada.");
    }

    private URI objectUri(String key) {
        return URI.create(baseUrl + "/storage/v1/object/" + bucket + "/" + key);
    }

    private static <T> T call(String operation, java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            // Solo el código de estado: el cuerpo o las cabeceras podrían reflejar datos de la petición.
            throw new IllegalStateException("Supabase Storage respondió " + ex.getStatusCode().value() + " al " + operation
                    + " una evidencia.");
        } catch (RestClientException ex) {
            throw new IllegalStateException("No se pudo contactar a Supabase Storage al " + operation + " una evidencia.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
