package pe.edu.universidad.servicios.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import pe.edu.universidad.servicios.attachments.repository.AttachmentRepository;
import pe.edu.universidad.servicios.shared.config.AppProperties;
import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;

/**
 * HU-26 / DISENIO-BD 7.10: si el INSERT falla después de subir el objeto, el objeto se borra.
 */
class AttachmentFailureIT extends IntegrationTest {

    @MockitoSpyBean
    AttachmentRepository attachments;

    @Autowired
    AppProperties properties;

    @Test
    void insertFallidoBorraElObjeto() {
        long area = data.area();
        long category = data.category(area, 2);
        String token = api.token(data.user("ESTUDIANTE", null));
        Response created = api.post("/api/v1/requests", token,
                Map.of("categoriaId", String.valueOf(category), "ubicacion", "Pabellón C", "descripcion", "Fuga."));
        String id = created.text("id");
        doThrow(new IllegalStateException("fallo simulado del INSERT")).when(attachments)
                .insert(anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString(), any());

        Response r = api.multipart("/api/v1/requests/" + id + "/attachments", token, "f.jpg", "image/jpeg",
                AttachmentIT.jpeg(100));

        assertThat(r.status()).isEqualTo(500);
        Path dir = Path.of(properties.storage().localDir()).resolve("solicitudes").resolve(id);
        assertThat(dir.toFile().list() == null || dir.toFile().list().length == 0).isTrue();
    }
}
