package sm.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Start_SMCore {

	public final static Properties configProp = new Properties();
	
	

	public static void main(String[] args) {
		SpringApplication.run(Start_SMCore.class, args);

		if (args.length > 0) {
			InputStream in = Start_SMCore.class.getClassLoader().getResourceAsStream("application.properties");
			System.out.println("Reading all properties from the file.");
			try {
				configProp.load(in);
			} catch (IOException e) {
				e.printStackTrace();
			}

			configProp.setProperty("sm.core.amb", args[0]);
			
		}
	}

}
