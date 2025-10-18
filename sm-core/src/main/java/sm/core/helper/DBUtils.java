package sm.core.helper;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

@Component
public class DBUtils {

    private static final Logger log = LoggerFactory.getLogger(DBUtils.class);

    private final DataSource dataSource;
    private final Properties configProp = new Properties();

    public DBUtils(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Connection getConnection() throws SQLException {
        // Se for Hikari, obtém o MXBean e regista estado antes de pedir a connection
        if (dataSource instanceof HikariDataSource) {
            HikariPoolMXBean mx = ((HikariDataSource) dataSource).getHikariPoolMXBean();
            if (mx != null) {
                log.info("Hikari BEFORE getConnection - total={}, active={}, idle={}, waiting={}",
                        mx.getTotalConnections(), mx.getActiveConnections(),
                        mx.getIdleConnections(), mx.getThreadsAwaitingConnection());
            }
        } else {
            log.debug("DataSource is not Hikari, cannot log pool metrics.");
        }

        Connection con = dataSource.getConnection();

        // Regista estado logo após obter a connection
        if (dataSource instanceof HikariDataSource) {
            HikariPoolMXBean mx = ((HikariDataSource) dataSource).getHikariPoolMXBean();
            if (mx != null) {
                log.info("Hikari AFTER getConnection - total={}, active={}, idle={}, waiting={}",
                        mx.getTotalConnections(), mx.getActiveConnections(),
                        mx.getIdleConnections(), mx.getThreadsAwaitingConnection());
            }
        }

        return con;
    }

    public void closeConnection(Connection con) {
        if (con != null) {
            try {
                con.close(); // devolve a connection ao pool
            } catch (SQLException e) {
                log.error("Erro ao fechar connection", e);
            }
        }

        // Log do estado do pool após fechar
        if (dataSource instanceof HikariDataSource) {
            HikariPoolMXBean mx = ((HikariDataSource) dataSource).getHikariPoolMXBean();
            if (mx != null) {
                log.info("Hikari AFTER closeConnection - total={}, active={}, idle={}, waiting={}",
                        mx.getTotalConnections(), mx.getActiveConnections(),
                        mx.getIdleConnections(), mx.getThreadsAwaitingConnection());
            }
        }
    }
}