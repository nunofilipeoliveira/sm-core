package sm.core.helper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import sm.core.Start_SMCore;

public class DBUtils {

    String url = "";
    String username = "";
    String password = "";

    private final Properties configProp = new Properties();

    public Connection getConnection() {
        System.out.println("DBUtils | Obter conexão");
        getConfigurations();

        try {
            // Já não precisas do Class.forName()
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            System.out.println("DBUtils | Erro a criar conexão");
            e.printStackTrace();
            return null;
        }
    }

    private void getConfigurations() {
        if (this.url.equals("")) {
            try (InputStream in = this.getClass().getClassLoader().getResourceAsStream("application.properties")) {
                System.out.println("Reading all properties from the file");
                configProp.load(in);

                String amb = Start_SMCore.configProp.getProperty("sm.core.amb");

                if (amb == null || amb.equals("PROD")) {
                    this.url = configProp.getProperty("sm.core.db.url.prod");
                    this.username = configProp.getProperty("sm.core.db.user.prod");
                    this.password = configProp.getProperty("sm.core.db.pwd.prod");
                } else if (amb.equals("DEV")) {
                    this.url = configProp.getProperty("sm.core.db.url.dev");
                    this.username = configProp.getProperty("sm.core.db.user.dev");
                    this.password = configProp.getProperty("sm.core.db.pwd.dev");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void closeConnection(Connection con) {
        if (con != null) {
            try {
                System.out.println("DBUtils | Fechar conexão");
                con.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

