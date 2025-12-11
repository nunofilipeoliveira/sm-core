package sm.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Start_SMCore {

    // se ainda usas este Properties noutros pontos legacy, mantemos; ideal é migrar tudo para Spring @Value/@ConfigurationProperties
    public static final Properties configProp = new Properties();

    public static void main(String[] args) {
        // Determina o ambiente a partir do primeiro argumento (ex: "DEV") ou usa PROD por defeito
        String env = "PROD";
        if (args != null && args.length > 0 && args[0] != null && !args[0].isBlank()) {
            env = args[0].trim().equalsIgnoreCase("DEV") ? "DEV" : "PROD";
        }

        System.out.println("Ambiente: " + env);
        // Define profile Spring antes de executar a aplicação
        SpringApplication app = new SpringApplication(Start_SMCore.class);
        if ("DEV".equalsIgnoreCase(env)) {
            app.setAdditionalProfiles("dev");
            // define também uma propriedade custom por compatibilidade com código legacy que lê Start_SMCore.configProp
            System.setProperty("sm.core.amb", "DEV");
        } else {
            app.setAdditionalProfiles("prod");
            System.setProperty("sm.core.amb", "PROD");
        }

        // Se ainda dependes de ler application.properties manualmente para algo legacy, podes carregar aqui (opcional)
        try (InputStream in = Start_SMCore.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                configProp.load(in);
            }
        } catch (IOException e) {
            // log apropriado
            e.printStackTrace();
        }

        // Arranca a aplicação com os args originais (ou sem args se preferires ocultar o arg "DEV")
        app.run(args);
    }
}