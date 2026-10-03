package sm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Validacao HTTP real (servidor em execucao) das protecoes de fronteira: CORS
 * global (apos a remocao das anotacoes {@code @CrossOrigin}), preflight com o
 * cabecalho {@code Authorization} e comportamento em modo REPORT.
 *
 * <p>Usa {@link HttpClient} (JDK) de proposito: o {@code HttpURLConnection}
 * usado pelo {@code TestRestTemplate} elimina cabecalhos como {@code Origin} e
 * {@code Access-Control-Request-Method} (sao "restricted headers" do JDK), o que
 * tornaria estes testes falsos.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityHttpIntegrationTest {

    private static final String ORIGEM = "https://sm.com.pt";

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> enviar(String method, String path, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Optional<String> primeiro(HttpResponse<String> response, String header) {
        return response.headers().firstValue(header);
    }

    @Test
    void cors_respondeAOrigemPermitidaMesmoSemAnotacoes() throws Exception {
        HttpResponse<String> response = enviar("PUT", "/sm/helloworld", "Origin", ORIGEM);

        assertEquals(200, response.statusCode());
        String allowOrigin = primeiro(response, "Access-Control-Allow-Origin").orElse(null);
        assertTrue("*".equals(allowOrigin) || ORIGEM.equals(allowOrigin),
                "O CORS deve responder apos remover as anotacoes @CrossOrigin (recebido: " + allowOrigin + ")");
    }

    @Test
    void preflight_permiteOCabecalhoAuthorization() throws Exception {
        HttpResponse<String> response = enviar("OPTIONS", "/sm/login",
                "Origin", ORIGEM,
                "Access-Control-Request-Method", "PUT",
                "Access-Control-Request-Headers", "authorization,content-type");

        assertEquals(200, response.statusCode());
        List<String> allowedHeaders = response.headers().allValues("Access-Control-Allow-Headers");
        assertTrue(allowedHeaders.stream().anyMatch(h -> h.toLowerCase().contains("authorization")),
                "O preflight tem de permitir o cabecalho Authorization: " + allowedHeaders);
        assertTrue(primeiro(response, "Access-Control-Allow-Methods").isPresent(),
                "O preflight tem de indicar os metodos permitidos");
    }

    @Test
    void modoReport_naoBloqueiaPedidoSemToken() throws Exception {
        HttpResponse<String> response = enviar("PUT", "/sm/naoExiste");

        assertNotEquals(401, response.statusCode(),
                "Em modo REPORT a autenticacao nao pode bloquear pedidos");
    }
}
