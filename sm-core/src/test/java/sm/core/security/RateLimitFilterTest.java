package sm.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import sm.core.config.RateLimit;
import sm.core.config.Rule;
import sm.core.config.SecurityProperties;

/**
 * Testes do filtro de protecao contra abuso/DoS.
 *
 * Sem Mockito (nao compativel com o JDK usado no build): usam-se os objetos
 * MockHttpServletRequest/MockHttpServletResponse do spring-test.
 */
class RateLimitFilterTest {

    private SecurityProperties properties;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();

        RateLimit config = new RateLimit();
        config.setTrustClientIpHeader(false);
        config.setMaxTrackedClients(100);
        config.setMaxRequestBodyBytes(1024L);
        config.setMaxConcurrentRequests(10);
        // refill a 0 para os testes serem deterministicos (sem reposicao de tokens)
        config.setGlobal(Rule.of("global", 100, 0d, 0));
        config.setLogin(Rule.of("login", 2, 0d, 0, "/sm/login"));
        config.setSession(Rule.of("session", 100, 0d, 0));
        config.setEmail(Rule.of("email", 100, 0d, 0));
        config.setUpload(Rule.of("upload", 5, 0d, 0, "/sm/uploadfoto"));
        config.setProbe(Rule.of("probe", 100, 0d, 0));
        config.setActuator(Rule.of("actuator", 100, 0d, 0, "/actuator"));
        properties.setRateLimit(config);

        filter = new RateLimitFilter(properties);
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, FilterChain chain)
            throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void pedidosDentroDoLimite_passamParaACadeia() throws Exception {
        boolean[] chamado = { false };
        FilterChain chain = (req, res) -> chamado[0] = true;

        MockHttpServletResponse primeira = run(request("PUT", "/sm/login", "10.0.0.1"), chain);
        MockHttpServletResponse segunda = run(request("PUT", "/sm/login", "10.0.0.1"), chain);

        assertEquals(200, primeira.getStatus());
        assertEquals(200, segunda.getStatus());
        assertTrue(chamado[0], "A cadeia deve ser executada dentro do limite");
    }

    @Test
    void acimaDoLimite_devolve429ComRetryAfterEJson() throws Exception {
        FilterChain chain = (req, res) -> { };

        run(request("PUT", "/sm/login", "10.0.0.2"), chain);
        run(request("PUT", "/sm/login", "10.0.0.2"), chain);
        MockHttpServletResponse terceira = run(request("PUT", "/sm/login", "10.0.0.2"), chain);

        assertEquals(429, terceira.getStatus());
        assertNotNull(terceira.getHeader("Retry-After"), "Deve indicar quando tentar novamente");
        assertTrue(terceira.getContentAsString().contains("too_many_requests"));
    }

    @Test
    void caminhosDiferentes_temOrcamentosIndepedentes() throws Exception {
        FilterChain chain = (req, res) -> { };

        run(request("PUT", "/sm/login", "10.0.0.3"), chain);
        run(request("PUT", "/sm/login", "10.0.0.3"), chain);
        // esgotou os 2 tokens da regra "login"...
        assertEquals(429, run(request("PUT", "/sm/login", "10.0.0.3"), chain).getStatus());
        // ...mas a regra geral continua com tokens disponiveis
        assertEquals(200, run(request("PUT", "/sm/getAllJogadores/1", "10.0.0.3"), chain).getStatus());
    }

    @Test
    void clientesDiferentes_naoPartilhamORcamento() throws Exception {
        FilterChain chain = (req, res) -> { };

        assertEquals(200, run(request("PUT", "/sm/login", "10.0.0.4"), chain).getStatus());
        assertEquals(200, run(request("PUT", "/sm/login", "10.0.0.4"), chain).getStatus());
        assertEquals(429, run(request("PUT", "/sm/login", "10.0.0.4"), chain).getStatus());
        assertEquals(200, run(request("PUT", "/sm/login", "10.0.0.5"), chain).getStatus());
    }

    @Test
    void corpoMaiorQueOLimite_devolve413SemExecutarACadeia() throws Exception {
        boolean[] chamado = { false };
        FilterChain chain = (req, res) -> chamado[0] = true;

        MockHttpServletRequest request = request("PUT", "/sm/getPresenca", "10.0.0.6");
        request.setContentType("application/json");
        request.setContent(new byte[4096]); // maior que o limite de 1024 dos testes

        MockHttpServletResponse response = run(request, chain);

        assertEquals(413, response.getStatus());
        assertFalse(chamado[0], "O pedido nao deve chegar a aplicacao");
        assertTrue(response.getContentAsString().contains("payload_too_large"));
    }

    @Test
    void multipart_naoEValidadoPeloFiltro() throws Exception {
        boolean[] chamado = { false };
        FilterChain chain = (req, res) -> chamado[0] = true;

        MockHttpServletRequest request = request("POST", "/sm/uploadfoto/1/1", "10.0.0.7");
        request.setContentType("multipart/form-data; boundary=xyz");
        request.setContent(new byte[2048]); // acima do limite do filtro, mas multipart

        MockHttpServletResponse response = run(request, chain);

        assertEquals(200, response.getStatus());
        assertTrue(chamado[0], "Os limites de multipart sao aplicados pelo Spring (multipart config)");
    }

    @Test
    void limiteDeConcorrenciaPorCliente_devolve503() throws Exception {
        // regra com 1 pedido simultaneo por cliente
        properties.getRateLimit().setGlobal(Rule.of("global", 100, 0d, 1));
        filter = new RateLimitFilter(properties);

        // dentro da cadeia do 1.o pedido simula-se um 2.o pedido do mesmo cliente
        MockHttpServletResponse[] segundo = new MockHttpServletResponse[1];
        FilterChain chain = (req, res) -> segundo[0] =
                run(request("PUT", "/sm/getPresenca", "10.0.0.8"), (r, s) -> { });

        MockHttpServletResponse primeiro = run(request("PUT", "/sm/getPresenca", "10.0.0.8"), chain);

        assertEquals(200, primeiro.getStatus());
        assertNotNull(segundo[0], "O pedido aninhado deve ter sido processado pelo filtro");
        assertEquals(503, segundo[0].getStatus(), "O 2.o pedido simultaneo deve ser recusado");
    }

    @Test
    void xForwardedFor_ignoradoQuandoProxyNaoEFiavel() throws Exception {
        FilterChain chain = (req, res) -> { };

        // mesmo IP de socket, cabecalhos diferentes: contam como o MESMO cliente
        run(withForwardedFor(request("PUT", "/sm/login", "10.0.0.9"), "1.1.1.1"), chain);
        run(withForwardedFor(request("PUT", "/sm/login", "10.0.0.9"), "2.2.2.2"), chain);
        MockHttpServletResponse terceira =
                run(withForwardedFor(request("PUT", "/sm/login", "10.0.0.9"), "3.3.3.3"), chain);

        assertEquals(429, terceira.getStatus(), "O cabecalho nao pode ser usado para contornar limites");
    }

    @Test
    void xForwardedFor_usadoQuandoProxyEFiavel() throws Exception {
        properties.getRateLimit().setTrustClientIpHeader(true);
        filter = new RateLimitFilter(properties);
        FilterChain chain = (req, res) -> { };

        assertEquals(200, run(withForwardedFor(request("PUT", "/sm/login", "172.18.0.1"), "1.1.1.1"), chain)
                .getStatus());
        assertEquals(200, run(withForwardedFor(request("PUT", "/sm/login", "172.18.0.1"), "2.2.2.2"), chain)
                .getStatus());
        assertEquals(200, run(withForwardedFor(request("PUT", "/sm/login", "172.18.0.1"), "3.3.3.3"), chain)
                .getStatus(), "Cada IP real tem o seu proprio orcamento");
    }

    @Test
    void actuator_bloqueiaClientesForaDaAllowlist() throws Exception {
        properties.getRateLimit().setActuatorAllowedClients(java.util.List.of("127.0.0.1", "172.16.0.0/12"));
        filter = new RateLimitFilter(properties);
        FilterChain chain = (req, res) -> { };

        assertEquals(403, run(request("GET", "/actuator/prometheus", "8.8.8.8"), chain).getStatus());
        assertEquals(200, run(request("GET", "/actuator/prometheus", "127.0.0.1"), chain).getStatus());
        assertEquals(200, run(request("GET", "/actuator/prometheus", "172.20.5.4"), chain).getStatus());
        // endpoints da aplicacao nao sao afetados pela allowlist
        assertEquals(200, run(request("PUT", "/sm/sonda", "8.8.8.8"), chain).getStatus());
    }

    @Test
    void filtroDesativado_deixaPassarTudo() throws Exception {
        properties.getRateLimit().setEnabled(false);
        filter = new RateLimitFilter(properties);
        FilterChain chain = (req, res) -> { };

        for (int i = 0; i < 10; i++) {
            assertEquals(200, run(request("PUT", "/sm/login", "10.0.0.10"), chain).getStatus());
        }
    }

    private MockHttpServletRequest withForwardedFor(MockHttpServletRequest request, String ip) {
        request.addHeader("X-Forwarded-For", ip);
        return request;
    }
}
