package pe.edu.universidad.servicios.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import pe.edu.universidad.servicios.attachments.storage.SupabaseAttachmentStorage;
import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.shared.config.AppProperties.Storage;
import pe.edu.universidad.servicios.shared.config.AppProperties.Storage.Supabase;

class SupabaseAttachmentStorageTest {

    static final String URL = "https://proyecto-demo.supabase.co";
    static final String KEY = "clave-servicio-de-prueba-123";
    static final String KEY_PATH = "solicitudes/31/0b5f2a8e-1c3d-4e5f-8a9b-0c1d2e3f4a5b.jpg";

    private MockRestServiceServer server;
    private SupabaseAttachmentStorage storage;

    static AppProperties props(String url, String key) {
        return new AppProperties(null, null, null, new Storage("supabase", null, new Supabase(url, key, "evidencias-solicitudes")));
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        storage = new SupabaseAttachmentStorage(builder, props(URL, KEY));
    }

    @Test
    void putEnviaCabecerasYRuta() {
        server.expect(requestTo(URL + "/storage/v1/object/evidencias-solicitudes/" + KEY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(header("apikey", KEY))
                .andExpect(header("Content-Type", "image/jpeg"))
                .andExpect(header("x-upsert", "false"))
                .andRespond(withSuccess("{\"Key\":\"evidencias-solicitudes/" + KEY_PATH + "\"}", MediaType.APPLICATION_JSON));

        storage.put(KEY_PATH, new byte[] {1, 2, 3}, "image/jpeg");

        server.verify();
        assertThat(storage.provider()).isEqualTo("SUPABASE");
    }

    @Test
    void deleteEnviaPrefijos() {
        server.expect(requestTo(URL + "/storage/v1/object/evidencias-solicitudes"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer " + KEY))
                .andExpect(content().json("{\"prefixes\":[\"" + KEY_PATH + "\"]}", true))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        storage.delete(KEY_PATH);

        server.verify();
    }

    @Test
    void firmaDevuelveUrlAbsoluta() {
        server.expect(requestTo(URL + "/storage/v1/object/sign/evidencias-solicitudes/" + KEY_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"expiresIn\":300}", true))
                .andRespond(withSuccess("{\"signedURL\":\"/object/sign/evidencias-solicitudes/" + KEY_PATH + "?token=abc\"}",
                        MediaType.APPLICATION_JSON));

        URI uri = storage.signedDownloadUrl(KEY_PATH, Duration.ofMinutes(5)).orElseThrow();

        assertThat(uri).isEqualTo(URI.create(URL + "/storage/v1/object/sign/evidencias-solicitudes/" + KEY_PATH + "?token=abc"));
    }

    @Test
    void errorDelProveedorNoFiltraLaClave() {
        server.expect(requestTo(URL + "/storage/v1/object/evidencias-solicitudes/" + KEY_PATH))
                .andRespond(withServerError().body("fallo interno"));

        assertThatThrownBy(() -> storage.put(KEY_PATH, new byte[] {1}, "image/jpeg"))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> assertThat(String.valueOf(ex.getMessage()) + ex.getCause()).doesNotContain(KEY));
    }

    @Test
    void faltaConfiguracionFallaAlArrancar() {
        assertThatThrownBy(() -> new SupabaseAttachmentStorage(RestClient.builder(), props("", KEY)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_STORAGE_SUPABASE_URL");
        assertThatThrownBy(() -> new SupabaseAttachmentStorage(RestClient.builder(), props(URL, " ")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_STORAGE_SUPABASE_SERVICE_KEY")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(KEY));
    }
}
