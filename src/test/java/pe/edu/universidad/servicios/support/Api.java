package pe.edu.universidad.servicios.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP mínimo para recorrer la API real con JWT, cookies y cabeceras explícitas.
 */
public class Api {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;
    private final JsonMapper json;

    public Api(String base, JsonMapper json) {
        this.base = base;
        this.json = json;
    }

    public record Response(int status, HttpHeaders headers, String body, JsonMapper mapper) {

        public JsonNode json() {
            return body == null || body.isBlank() ? null : mapper.readTree(body);
        }

        public String text(String field) {
            JsonNode node = json().get(field);
            return node == null || node.isNull() ? null : node.asString();
        }

        public Optional<String> header(String name) {
            return headers.firstValue(name);
        }

        /** Valor de la cookie refresh_token en Set-Cookie. */
        public String refreshCookie() {
            return headers.allValues("Set-Cookie").stream()
                    .filter(c -> c.startsWith("refresh_token="))
                    .map(c -> c.substring("refresh_token=".length(), c.indexOf(';')))
                    .findFirst().orElse(null);
        }

        public String codigo() {
            return text("codigo");
        }

        @Override
        public String toString() {
            return status + " " + body;
        }
    }

    public Response get(String path, String token) {
        return send("GET", path, token, null, Map.of());
    }

    public Response post(String path, String token, Object body) {
        return send("POST", path, token, body, Map.of());
    }

    public Response patch(String path, String token, Object body) {
        return send("PATCH", path, token, body, Map.of());
    }

    public Response put(String path, String token, Object body) {
        return send("PUT", path, token, body, Map.of());
    }

    public Response send(String method, String path, String token, Object body, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30));
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            headers.forEach(builder::header);
            if (body != null) {
                builder.header("Content-Type", "application/json");
                String payload = body instanceof String s ? s : json.writeValueAsString(body);
                builder.method(method, HttpRequest.BodyPublishers.ofString(payload));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers(), response.body(), json);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    /** POST multipart/form-data con un único archivo en el campo {@code archivo}. */
    public Response multipart(String path, String token, String filename, String contentType, byte[] content) {
        String boundary = "----gestal" + System.nanoTime();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"" + filename
                + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeBytes(head);
        out.writeBytes(content);
        out.writeBytes(tail);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers(), response.body(), json);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    /** GET que devuelve el cuerpo binario sin seguir redirecciones. */
    public HttpResponse<byte[]> download(String path, String token) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30)).GET();
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    public Response login(String correo, String password) {
        return post("/api/v1/auth/login", null, Map.of("correo", correo, "contrasena", password));
    }

    /** Inicia sesión y devuelve el JWT; falla si la respuesta no es 200. */
    public String token(TestData.Account account) {
        Response response = login(account.correo(), account.password());
        if (response.status() != 200) {
            throw new AssertionError("Login falló: " + response);
        }
        return response.text("tokenAcceso");
    }
}
