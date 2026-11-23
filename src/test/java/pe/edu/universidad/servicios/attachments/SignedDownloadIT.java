package pe.edu.universidad.servicios.attachments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import pe.edu.universidad.servicios.attachments.storage.AttachmentStorage;
import pe.edu.universidad.servicios.support.Api.Response;
import pe.edu.universidad.servicios.support.IntegrationTest;

/**
 * HU-26 con un proveedor que firma URLs (Supabase): la descarga redirige a la URL firmada.
 */
class SignedDownloadIT extends IntegrationTest {

    @MockitoBean
    AttachmentStorage storage;

    @Test
    void descargaRedirigeConProveedorFirmado() {
        URI signed = URI.create("https://proyecto-demo.supabase.co/storage/v1/object/sign/evidencias-solicitudes/x.jpg?token=abc");
        when(storage.provider()).thenReturn("SUPABASE");
        when(storage.signedDownloadUrl(anyString(), any())).thenReturn(Optional.of(signed));
        long area = data.area();
        long category = data.category(area, 2);
        String token = api.token(data.user("ESTUDIANTE", null));
        String id = api.post("/api/v1/requests", token,
                Map.of("categoriaId", String.valueOf(category), "ubicacion", "Pabellón C", "descripcion", "Fuga.")).text("id");
        Response created = api.multipart("/api/v1/requests/" + id + "/attachments", token, "f.jpg", "image/jpeg",
                AttachmentIT.jpeg(100));
        assertThat(created.status()).as(created.toString()).isEqualTo(201);

        String contentPath = "/api/v1/requests/" + id + "/attachments/" + created.text("id") + "/content";
        String otherStudent = api.token(data.user("ESTUDIANTE", null));
        HttpResponse<byte[]> anonymous = api.download(contentPath, null);
        HttpResponse<byte[]> outsideScope = api.download(contentPath, otherStudent);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.headers().firstValue("Location")).isEmpty();
        assertThat(outsideScope.statusCode()).isEqualTo(404);
        assertThat(outsideScope.headers().firstValue("Location")).isEmpty();
        verify(storage, never()).signedDownloadUrl(anyString(), any());

        HttpResponse<byte[]> download = api.download(contentPath, token);

        assertThat(download.statusCode()).isEqualTo(302);
        assertThat(download.headers().firstValue("Location")).contains(signed.toString());
        String provider = data.jdbc().sql("SELECT proveedor_almacenamiento FROM adjuntos_solicitud WHERE id = :id")
                .param("id", Long.parseLong(created.text("id"))).query(String.class).single();
        assertThat(provider).isEqualTo("SUPABASE");
    }
}
