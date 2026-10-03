package sm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import sm.core.utils.TokenGenerator;

/**
 * Validacao HTTP real com {@code sm.core.security.auth.mode=ENFORCE}: sem token
 * valido os endpoints nao publicos devolvem {@code 401} e com token valido o
 * pedido segue para a aplicacao.
 *
 * <p>Usa {@link HttpClient} (JDK) para controlar exatamente os cabecalhos e
 * evitar o tratamento de autenticacao do {@code HttpURLConnection}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "sm.core.security.auth.mode=ENFORCE")
class TokenAuthenticationIntegrationTest {

    private static final String ORIGEM = "https://sm.com.pt";

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> put(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + path))
                .method("PUT", HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void semToken_endpointPrivado_devolve401() throws Exception {
        HttpResponse<String> response = put("/sm/getAllJogadores/1", null);

        assertEquals(401, response.statusCode(),
                "Sem token o pedido tem de ser recusado antes de chegar ao controlador");
        assertTrue(response.body().contains("unauthorized"));
    }

    @Test
    void tokenInvalido_devolve401() throws Exception {
        assertEquals(401, put("/sm/getAllJogadores/1", "token.falso.aqui").statusCode());
    }

    @Test
    void tokenValido_naoE401() throws Exception {
        String token = TokenGenerator.generateToken("treinador");

        // caminho inexistente para nao tocar na base de dados: o que importa e que
        // a autenticacao tenha sido aceite (404 em vez de 401)
        HttpResponse<String> response = put("/sm/naoExiste", token);

        assertNotEquals(401, response.statusCode());
        assertEquals(404, response.statusCode());
    }

    @Test
    void tokenViaQueryString_naoE401() throws Exception {
        String token = TokenGenerator.generateToken("treinador");

        assertNotEquals(401, put("/sm/naoExiste?token=" + token, null).statusCode());
    }

    @Test
    void caminhosPublicos_continuamAcessiveis() throws Exception {
        HttpResponse<String> response = put("/sm/helloworld", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Hello World"));
    }

    /**
     * O preflight CORS (OPTIONS) e lancerado pelo navegador SEM qualquer cabecalho
     * de aplicacao — por especificacao nunca leva o {@code Authorization}. Como o
     * front-end corre noutra origem ({@code http://localhost} vs
     * {@code http://localhost:8080}), cada pedido com {@code Authorization} ou
     * {@code Content-Type: application/json} passa primeiro por aqui.
     *
     * <p>Se o filtro o tratar como "pedido sem token", devolve 401 e o navegador
     * bloqueia o pedido real — a aplicacao deixa de funcionar de todo.</p>
     */
    @Test
    void preflightCors_naoDevolve401() throws Exception {
        HttpRequest request = HttpRequest
                .newBuilder(URI.create("http://localhost:" + port + "/sm/getAllJogadores/1"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", ORIGEM)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertNotEquals(401, response.statusCode(),
                "O preflight CORS nao pode ser recusado pela autenticacao por token");
        assertEquals(200, response.statusCode(), "O preflight tem de ser respondido pelo CORS");
        assertTrue(response.headers().firstValue("Access-Control-Allow-Headers").isPresent(),
                "O preflight tem de indicar os cabecalhos permitidos");
    }
}
