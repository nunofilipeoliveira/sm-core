package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import sm.core.data.SondaResponse;

@Component
public class SondaHelper {

    private static final Logger log = LoggerFactory.getLogger(SondaHelper.class);

    private final DBUtils dbUtils;

    @Autowired
    public SondaHelper(DBUtils dbUtils) {
        this.dbUtils = dbUtils;
    }

    /**
     * Verifica se o sistema está funcionando corretamente
     * Inclui verificação de conexão com banco de dados
     */
    public SondaResponse verificarSistema() {
        log.info("SondaHelper | verificarSistema | Start");

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String databaseStatus = "OK";
        String applicationStatus = "OK";
        String message = "Sistema funcionando corretamente";

        // Testar conexão com banco de dados
        Connection conn = null;
        try {
            conn = dbUtils.getConnection();
            
            // Executar uma query simples para verificar se o banco está respondendo
            PreparedStatement stmt = conn.prepareStatement("SELECT 1");
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                databaseStatus = "OK";
                log.info("SondaHelper | verificarSistema | Database connection OK");
            } else {
                databaseStatus = "ERROR";
                message = "Banco de dados não respondeu corretamente";
                log.error("SondaHelper | verificarSistema | Database connection FAILED");
            }
            
            rs.close();
            stmt.close();
            dbUtils.closeConnection(conn);

        } catch (SQLException e) {
            databaseStatus = "ERROR";
            message = "Erro ao conectar ao banco de dados: " + e.getMessage();
            log.error("SondaHelper | verificarSistema | Database ERROR: " + e.getMessage(), e);
            
            if (conn != null) {
                try {
                    dbUtils.closeConnection(conn);
                } catch (Exception ex) {
                    log.error("SondaHelper | verificarSistema | Error closing connection", ex);
                }
            }
        }

        // Determinar status geral
        String statusGeral = databaseStatus.equals("OK") ? "OK" : "ERROR";

        SondaResponse response = new SondaResponse(
            statusGeral,
            timestamp,
            databaseStatus,
            applicationStatus,
            message
        );

        log.info("SondaHelper | verificarSistema | End - Status: " + statusGeral);
        return response;
    }
}