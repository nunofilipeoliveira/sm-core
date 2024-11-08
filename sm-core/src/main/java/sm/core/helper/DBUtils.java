package sm.core.helper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import sm.core.Start_SMCore;

public class DBUtils {

//	static String url = "jdbc:mysql://localhost:3306/sm_hcmaia";
//	static String username = "root";
//	static String password = "admin123";

//	static String url = "jdbc:mysql://94.46.180.24:3306/sm_hcmaia";
//	static String username = "nfoliveira";
//	static String password = "oliveiraDB2024#";

	String url = "";
	String username = "";
	String password = "";

	static Connection con = null;

	private final Properties configProp = new Properties();

	public Connection getConnection() {

		System.out.println("DBUtils | Obter conexão");

		getConfigurations();

		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			System.out.println("DBUtils | Erro a criar conexão (Driver)");
			e.printStackTrace();
		}

		try {
			con = DriverManager.getConnection(url, username, password);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("DBUtils | Erro a criar conexão (obter nova conexão)");
		}

		return con;

	}

	private void getConfigurations() {

		if (this.url.equals("")) {

			// Private constructor to restrict new instances
			InputStream in = this.getClass().getClassLoader().getResourceAsStream("application.properties");
			System.out.println("Reading all properties from the file");
			try {
				configProp.load(in);
			} catch (IOException e) {
				e.printStackTrace();
			}

			String amb = Start_SMCore.configProp.getProperty("sm.core.amb");
			if (amb == null) {

				this.url = configProp.getProperty("sm.core.db.url.prod");
				this.username = configProp.getProperty("sm.core.db.user.prod");
				this.password = configProp.getProperty("sm.core.db.pwd.prod");
				return;
			}
			if (amb.equals("PROD")) {

				this.url = configProp.getProperty("sm.core.db.url.prod");
				this.username = configProp.getProperty("sm.core.db.user.prod");
				this.password = configProp.getProperty("sm.core.db.pwd.prod");
				return;
			}

			if (amb.equals("DEV")) {

				this.url = configProp.getProperty("sm.core.db.url.dev");
				this.username = configProp.getProperty("sm.core.db.user.dev");
				this.password = configProp.getProperty("sm.core.db.pwd.dev");
				return;
			}
		}
	}

	public void closeConnection() {
		try {
			System.out.println("DBUtils | Fechar conexão");
			con.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
