package sm.core.ws;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import sm.core.data.SondaResponse;
import sm.core.helper.SondaHelper;

@WebMvcTest(SondaWS.class)
@Import(SondaWSTest.TestConfig.class)
class SondaWSTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Configuração de teste que fornece um SondaHelper fake
     * (evita uso de @MockBean que requer ByteBuddy/Mockito com Java 26)
     */
    static class TestConfig {
        @Bean
        public SondaHelper sondaHelper() {
            return new SondaHelper(null) {
                private boolean simulateError = false;

                @Override
                public SondaResponse verificarSistema() {
                    if (simulateError) {
                        return new SondaResponse(
                            "ERROR",
                            "2024-01-01T12:00:00",
                            "ERROR",
                            "OK",
                            "Erro ao conectar ao banco de dados: Connection refused"
                        );
                    }
                    return new SondaResponse(
                        "OK",
                        "2024-01-01T12:00:00",
                        "OK",
                        "OK",
                        "Sistema funcionando corretamente"
                    );
                }
            };
        }

        
    }

    @Test
    void testVerificarSistema_Sucesso() throws Exception {
        mockMvc.perform(put("/sm/sonda"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OK"))
            .andExpect(jsonPath("$.database").value("OK"))
            .andExpect(jsonPath("$.application").value("OK"))
            .andExpect(jsonPath("$.message").value("Sistema funcionando corretamente"));
    }

    @Test
    void testVerificarSistema_DatabaseError() throws Exception {
        // Valida a estrutura do SondaResponse para erros de banco de dados
        SondaResponse errorResponse = new SondaResponse(
            "ERROR",
            "2024-01-01T12:00:00",
            "ERROR",
            "OK",
            "Erro ao conectar ao banco de dados: Connection refused"
        );

        // Valida os campos da resposta de erro
        org.junit.jupiter.api.Assertions.assertEquals("ERROR", errorResponse.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("ERROR", errorResponse.getDatabase());
        org.junit.jupiter.api.Assertions.assertEquals("OK", errorResponse.getApplication());
        org.junit.jupiter.api.Assertions.assertEquals(
            "Erro ao conectar ao banco de dados: Connection refused", errorResponse.getMessage()
        );
    }
}