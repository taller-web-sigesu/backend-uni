package pe.edu.universidad.servicios.attachments.controller;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import pe.edu.universidad.servicios.attachments.dto.AttachmentDtos.AttachmentResponse;
import pe.edu.universidad.servicios.attachments.service.AttachmentService;
import pe.edu.universidad.servicios.attachments.service.AttachmentService.Download;
import pe.edu.universidad.servicios.shared.security.AuthenticatedActor;
import pe.edu.universidad.servicios.shared.security.CurrentActor;

@RestController
@RequestMapping("/api/v1/requests/{id}/attachments")
@Tag(name = "Evidencias", description = "HU-26")
public class AttachmentController {

    private final AttachmentService attachments;

    public AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Adjunta una evidencia JPG, PNG o PDF de hasta 5 MB (máximo 5 vigentes)")
    public ResponseEntity<AttachmentResponse> upload(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @RequestPart(name = "archivo", required = false) MultipartFile archivo) {
        AttachmentResponse created = attachments.upload(actor, id, archivo);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/requests/" + id + "/attachments/" + created.id() + "/content"))
                .body(created);
    }

    @GetMapping
    @Operation(summary = "Lista las evidencias vigentes de la solicitud")
    public List<AttachmentResponse> list(@CurrentActor AuthenticatedActor actor, @PathVariable long id) {
        return attachments.list(actor, id);
    }

    @GetMapping("/{attachmentId}/content")
    @Operation(summary = "Descarga una evidencia (o redirige a una URL firmada de 5 minutos)")
    public ResponseEntity<Resource> download(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @PathVariable long attachmentId) {
        Download download = attachments.download(actor, id, attachmentId);
        if (download.redirect().isPresent()) {
            return ResponseEntity.status(HttpStatus.FOUND).location(download.redirect().get())
                    .cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.row().tipoMime()))
                .contentLength(download.row().tamanoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.row().nombreOriginal(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/{attachmentId}")
    @Operation(summary = "Elimina una evidencia propia mientras la solicitud está abierta")
    public ResponseEntity<Void> delete(@CurrentActor AuthenticatedActor actor, @PathVariable long id,
            @PathVariable long attachmentId) {
        attachments.delete(actor, id, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
