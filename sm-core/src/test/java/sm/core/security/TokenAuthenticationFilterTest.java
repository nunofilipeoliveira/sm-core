package sm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import sm.core.config.Auth;
import sm.core.config.SecurityProperties;
import sm.core.utils.TokenGenerator;

/**
 * Testes da autenticacao por token JWT nos endpoints.
 */
class TokenAuthenticationFilterTest {

    private SecurityProperties properties;
    private TokenAuthenticationFilter filter;
    private final String validToken = TokenGenerator.generateToken("treinador");

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
        filter = new TokenAuthenticationFilter(properties);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, FilterChain chain)
            throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private Auth auth() {
        return properties.getAuth();
    }

    @Test
    void modoReport_naoBloqueiaPedidosSemToken() throws Exception {
        boolean[] chamado = { false };
        FilterChain chain = (req, res) -> chamado[0] = true;

        MockHttpServletResponse response = run(request("PUT", "/sm/getAllJogadores/1"), chain);

        assertEquals(200, response.getStatus());
        assertTrue(chamado[0], "Em modo REPORT o pedido segue para a aplicacao (apenas e registado em log)");
    }

    @Test
    void modoEnforce_semToken_devolve401() throws Exception {
        auth().setMode("ENFORCE");
        boolean[] chamado = { false };
        FilterChain chain = (req, res) -> chamado[0] = true;

        MockHttpServletResponse response = run(request("PUT", "/sm/getAllJogadores/1"), chain);

        assertEquals(401, response.getStatus());
        assertFalse(chamado[0], "O pedido nao pode chegar a aplicacao");
        assertNotNull(response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentAsString().contains("unauthorized"));
    }

    @Test
    void modoEnforce_tokenValido_deixaPassar() throws Exception {
        auth().setMode("ENFORCE");
        boolean[] chamado = { false };
        FilterChain chain = (req, res) -> chamado[0] = true;

        MockHttpServletRequest request = request("PUT", "/sm/getAllJogadores/1");
        request.addHeader("Authorization", "Bearer " + validToken);

        MockHttpServletResponse response = run(request, chain);

        assertEquals(200, response.getStatus());
        assertTrue(chamado[0]);
        assertEquals("treinador", request.getAttribute(AuthTokens.USER_ATTRIBUTE),
                "O utilizador autenticado fica disponivel no pedido");
    }

    @Test
    void modoEnforce_tokenInvalido_devolve401() throws Exception {
        auth().setMode("ENFORCE");
        FilterChain chain = (req, res) -> { };

        MockHttpServletRequest request = request("PUT", "/sm/getAllJogadores/1");
        request.addHeader("Authorization", "Bearer token.falso.aqui");

        assertEquals(401, run(request, chain).getStatus());
    }

    @Test
    void caminhosPublicos_naoExigemToken() throws Exception {
        auth().setMode("ENFORCE");
        FilterChain chain = (req, res) -> { };

        List<String> publicos = List.of("/sm/login", "/sm/sonda", "/sm/isAuthenticated",
                "/sm/extendSession", "/sm/createUtilizador/1", "/sm/reenviarEmailAtivacao/1",
                "/sm/activateuser/ABC", "/sm/getcode/ABC", "/sm/helloworld", "/actuator/health");
        for (String path : publicos) {
            assertEquals(200, run(request("PUT", path), chain).getStatus(), "Deveria ser publico: " + path);
        }
    }

    @Test
    void tokenAssinadoComOutroSegredo_devolve401() throws Exception {
        auth().setMode("ENFORCE");
        FilterChain chain = (req, res) -> { };

        // token bem formado, mas emitido por outro sistema
        String outroToken = io.jsonwebtoken.Jwts.builder()
                .setSubject("intruso")
                .signWith(io.jsonwebtoken.SignatureAlgorithm.HS512, "outro_segredo")
                .compact();

        MockHttpServletRequest request = request("PUT", "/sm/getAllJogadores/1");
        request.addHeader("Authorization", "Bearer " + outroToken);

        assertEquals(401, run(request, chain).getStatus());
    }

    @Test
    void cabecalhosAlternativos_eBearersaoAceites() throws Exception {
        auth().setMode("ENFORCE");
        auth().setTokenHeaders(List.of("Authorization", "X-Auth-Token", "Token"));
        FilterChain chain = (req, res) -> { };

        MockHttpServletRequest viaXAuth = request("PUT", "/sm/getAllJogadores/1");
        viaXAuth.addHeader("X-Auth-Token", validToken);
        assertEquals(200, run(viaXAuth, chain).getStatus());

        MockHttpServletRequest viaToken = request("PUT", "/sm/getAllJogadores/1");
        viaToken.addHeader("Token", validToken);
        assertEquals(200, run(viaToken, chain).getStatus());

        // Authorization sem o prefixo "Bearer "
        MockHttpServletRequest semBearer = request("PUT", "/sm/getAllJogadores/1");
        semBearer.addHeader("Authorization", validToken);
        assertEquals(200, run(semBearer, chain).getStatus());
    }

    @Test
    void tokenViaQueryString_eAceite() throws Exception {
        auth().setMode("ENFORCE");
        FilterChain chain = (req, res) -> { };

        MockHttpServletRequest request = request("PUT", "/sm/getAllJogadores/1");
        request.setQueryString("tenant=1&token=" + validToken);

        assertEquals(200, run(request, chain).getStatus());
    }

    @Test
    void queryStringComTokenVazio_devolve401() throws Exception {
        auth().setMode("ENFORCE");
        FilterChain chain = (req, res) -> { };

        MockHttpServletRequest request = request("PUT", "/sm/getAllJogadores/1");
        request.setQueryString("token=");

        assertEquals(401, run(request, chain).getStatus());
    }

    @Test
    void autenticacaoDesativada_deixaPassarTudo() throws Exception {
        auth().setMode("ENFORCE");
        auth().setEnabled(false);
        FilterChain chain = (req, res) -> { };

        assertEquals(200, run(request("PUT", "/sm/getAllJogadores/1"), chain).getStatus());
    }

    @Test
    void preflightCors_naoExigeTokenNemContaComoFalha() throws Exception {
        auth().setMode("ENFORCE");
        boolean[] chamado = { false };
        FilterChain chain = (req, res) -> chamado[0] = true;

        MockHttpServletRequest request = request("OPTIONS", "/sm/getAllJogadores/1");
        request.addHeader("Origin", "https://sm.com.pt");
        request.addHeader("Access-Control-Request-Method", "PUT");
        request.addHeader("Access-Control-Request-Headers", "authorization,content-type");

        assertEquals(200, run(request, chain).getStatus());
        assertTrue(chamado[0], "O preflight tem de chegar ao CORS do Spring");
    }

    @Test
    void pedidoOptionsQueNaoEPreflightContinuaExigindoToken() throws Exception {
        auth().setMode("ENFORCE");
        FilterChain chain = (req, res) -> { };

        // OPTIONS sem os cabecalhos de preflight nao e um preflight: continua sujeito
        // a autenticacao, para nao abrir uma via de bypass.
        assertEquals(401, run(request("OPTIONS", "/sm/getAllJogadores/1"), chain).getStatus());
    }

    @Test
    void pedidoMultipartComTokenValido_passaSemConsumirOCorpo() throws Exception {
        auth().setMode("ENFORCE");
        boolean[] chamado = { false };
        FilterChain chain = (req, res) -> chamado[0] = true;

        MockHttpServletRequest request = request("POST", "/sm/uploadfoto/1/1");
        request.setContentType("multipart/form-data; boundary=xyz");
        request.setContent(new byte[64]);
        request.addHeader("Authorization", "Bearer " + validToken);

        assertEquals(200, run(request, chain).getStatus());
        assertTrue(chamado[0], "O upload autenticado segue para a aplicacao");
    }
}
