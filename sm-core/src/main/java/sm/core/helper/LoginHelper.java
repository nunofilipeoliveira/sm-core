package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import sm.core.data.HistoricoLoginData;
import sm.core.data.LoginData;
import sm.core.data.UtilizadorParaAtivarData;
import sm.core.utils.TokenGenerator;
import sm.core.utils.TokenValidator;

public class LoginHelper {

	public LoginHelper() {

	}

	public boolean isAuthenticated(String token) {

		boolean isValid = TokenValidator.isTokenValid(token);
		if (isValid) {
			System.out.println("O token é válido.");
			return true;
		} else {
			System.out.println("O token é inválido.");
			return false;
		}

	}

	public LoginData Dologin(String parmUser, String parmPWD) {

		DBUtils dbUtils = new DBUtils();
		LoginData loginData = null;
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement(
					"select uti.user, uti.id, uti.password, uti.Nome nome, ee.id IDEscalao_Epoca, e2.nome DesctivoEscalao from UTILIZADORES Uti\r\n"
							+ "inner join utilizadores_escalao UE on ue.id_utilizador=Uti.id\r\n"
							+ "inner join escalao_epoca ee on ue.id_escalao_epoca=ee.id \r\n"
							+ "inner join epoca e on e.id =ee.id_epoca\r\n"
							+ "inner join escalao e2 on ee.id_escalao =e2.id \r\n" + "where e.Estado ='1'\r\n"
							+ "and uti.user=? and uti.password =?");

			preparedStatement.setString(1, parmUser);
			preparedStatement.setString(2, parmPWD);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				if (loginData == null) {
					loginData = new LoginData(rs.getInt("id"), rs.getString("nome"), rs.getString("user"),
							rs.getString("password"), TokenGenerator.generateToken(rs.getString("user")));

					// regista histórico de acesso

					preparedStatement = dbUtils.getConnection()
							.prepareStatement("insert into logins values (?, now(), ?)");

					preparedStatement.setInt(1, loginData.getId());
					preparedStatement.setString(2, parmUser);
					preparedStatement.executeUpdate();
				}

				loginData.adicionarEscalaoEpoca(rs.getInt("IDEscalao_Epoca"), rs.getString("DesctivoEscalao"));

			}

			dbUtils.closeConnection();
			return loginData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public UtilizadorParaAtivarData getUtilizadorParaAtivarByCode(String parmCode) {

		DBUtils dbUtils = new DBUtils();
		UtilizadorParaAtivarData utilizadorParaAtivarData = null;
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select *from utilizadores_ativar ua where ESTADO='0' and CODE=?");

			preparedStatement.setString(1, parmCode);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorParaAtivarData = new UtilizadorParaAtivarData(rs.getString("user"), rs.getString("estado"),
						rs.getString("code"), rs.getString("ids_escalao"), rs.getString("nome"));
			}

			dbUtils.closeConnection();
			return utilizadorParaAtivarData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean createUtilizador(LoginData parmLoginData) {

		DBUtils dbUtils = new DBUtils();
		boolean resultado = false;
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement(
					"insert into utilizadores(nome, user, password) values(?, ?, ?)",
					PreparedStatement.RETURN_GENERATED_KEYS);

			preparedStatement.setString(1, parmLoginData.getNome());
			preparedStatement.setString(2, parmLoginData.getUser());
			preparedStatement.setString(3, parmLoginData.getPassword());
			preparedStatement.executeUpdate();
			ResultSet rs = preparedStatement.getGeneratedKeys();

			if (rs.next()) {
				parmLoginData.setId(rs.getInt(1));
			}

			// registar os escaloes
			for (int i = 0; i < parmLoginData.getEscalaoEpoca().size(); i++) {
				preparedStatement = dbUtils.getConnection()
						.prepareStatement("insert into utilizadores_escalao  values(?, ?)");
				preparedStatement.setInt(1, parmLoginData.getId());
				preparedStatement.setInt(2, parmLoginData.getEscalaoEpoca().get(i).getId_escalao_epoca());

				preparedStatement.executeUpdate();
			}

			preparedStatement = dbUtils.getConnection()
					.prepareStatement("update utilizadores_ativar set estado='1' where user=?");

			preparedStatement.setString(1, parmLoginData.getUser());
			preparedStatement.executeUpdate();

			dbUtils.closeConnection();
			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public String getCodebyUser(String parmUser) {

		DBUtils dbUtils = new DBUtils();
		String code = null;
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select code from utilizadores_ativar ua where ESTADO='0' and user=?");

			preparedStatement.setString(1, parmUser);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				code = rs.getString("code");
			}

			dbUtils.closeConnection();
			return code;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<HistoricoLoginData> getHistoricoLogins() {

		DBUtils dbUtils = new DBUtils();
		HistoricoLoginData hosHistoricoLoginData = null;
		ArrayList<HistoricoLoginData> listaHistorico = new ArrayList<HistoricoLoginData>();
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select user, data from logins order by data desc LIMIT 500");

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				hosHistoricoLoginData = new HistoricoLoginData(rs.getString("user"), rs.getTimestamp("data"));
				listaHistorico.add(hosHistoricoLoginData);
			}

			dbUtils.closeConnection();
			return listaHistorico;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

}
