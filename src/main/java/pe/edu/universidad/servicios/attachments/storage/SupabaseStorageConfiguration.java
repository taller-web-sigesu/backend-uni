package pe.edu.universidad.servicios.attachments.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import pe.edu.universidad.servicios.shared.config.AppProperties;

/**
 * Activa Supabase Storage con {@code APP_STORAGE_PROVIDER=supabase}. Falla al arrancar si falta la URL o la clave.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")
public class SupabaseStorageConfiguration {

    @Bean
    AttachmentStorage supabaseAttachmentStorage(AppProperties properties) {
        return new SupabaseAttachmentStorage(RestClient.builder(), properties);
    }
}
