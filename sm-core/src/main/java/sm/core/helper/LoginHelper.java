package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import sm.core.data.EscalaoData;
import sm.core.data.HistoricoLoginData;
import sm.core.data.LoginData;
import sm.core.data.UtilizadorData;
import sm.core.data.UtilizadorParaAtivarData;
import sm.core.utils.EmailService;
import sm.core.utils.TenantProperties;
import sm.core.utils.TokenGenerator;
import sm.core.utils.TokenValidator;

@Component
public class LoginHelper {

	@Autowired
	private TenantProperties tenantProperties;


	private final DBUtils dbUtils;

	public LoginHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;

	}

	@Autowired
	private EmailService emailService;

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

	public LoginData Dologin(String parmUser, String parmPWD, int parmTenant_id) {

	
		LoginData loginData = null;
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select uti.user, uti.id, uti.password, uti.Nome nome, uti.perfil, ee.id IDEscalao_Epoca, ee.nome  DesctivoEscalao from UTILIZADORES Uti\r\n"
							+ "inner join utilizadores_escalao UE on ue.id_utilizador=Uti.id\r\n"
							+ "inner join escalao_epoca ee on ue.id_escalao_epoca=ee.id \r\n"
							+ "inner join epoca e on e.id =ee.id_epoca\r\n"
							+ "inner join escalao e2 on ee.id_escalao =e2.id \r\n" + "where e.Estado ='1'\r\n"
							+ "and uti.user=? and uti.password =? and uti.tenant_id=?");

			preparedStatement.setString(1, parmUser);
			preparedStatement.setString(2, parmPWD);
			preparedStatement.setInt(3, parmTenant_id);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				if (loginData == null) {
					loginData = new LoginData(rs.getInt("id"), rs.getString("nome"), rs.getString("user"),
							rs.getString("password"), TokenGenerator.generateToken(rs.getString("user")), rs.getString("perfil"));

					// regista histórico de acesso

					PreparedStatement preparedStatement2 = conn
							.prepareStatement("insert into logins values (?, now(), ?)");

					preparedStatement2.setInt(1, loginData.getId());
					preparedStatement2.setString(2, parmUser);
					preparedStatement2.executeUpdate();
				
				}

				loginData.adicionarEscalaoEpoca(rs.getInt("IDEscalao_Epoca"), rs.getString("DesctivoEscalao"));

			}

			dbUtils.closeConnection(conn);

			return loginData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public UtilizadorParaAtivarData getUtilizadorParaAtivarByCode(String parmCode) {

		
		UtilizadorParaAtivarData utilizadorParaAtivarData = null;
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select *from utilizadores_ativar ua where ESTADO='0' and CODE=?");

			preparedStatement.setString(1, parmCode);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorParaAtivarData = new UtilizadorParaAtivarData(rs.getString("user"), rs.getString("estado"),
						rs.getString("ids_escalao"), rs.getString("nome"), rs.getString("email"),
						rs.getString("perfil"), rs.getString("code"));
			}

			dbUtils.closeConnection(conn);
			return utilizadorParaAtivarData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean createUtilizador(LoginData parmLoginData, int parmTenantID) {

		
		boolean resultado = false;
		PreparedStatement preparedStatement = null;
		// verifica se já existe e é apenas para ativar.

		UtilizadorData tmpUtiliazdor = getUserByUserName(parmLoginData.getUser(), parmTenantID);

		try {
	Connection conn = dbUtils.getConnection();
			if (tmpUtiliazdor.getId() == 0) {

			
				preparedStatement = conn.prepareStatement(
						"insert into utilizadores(nome, user, password, perfil, Tenant_id, estado) values(?, ?, ?, ?, ?, 1)",
						PreparedStatement.RETURN_GENERATED_KEYS);

				preparedStatement.setString(1, parmLoginData.getNome());
				preparedStatement.setString(2, parmLoginData.getUser());
				preparedStatement.setString(3, parmLoginData.getPassword());
				preparedStatement.setString(4, parmLoginData.getPerfil());
				preparedStatement.setInt(5, parmTenantID);
				preparedStatement.executeUpdate();
				ResultSet rs = preparedStatement.getGeneratedKeys();

				if (rs.next()) {
					parmLoginData.setId(rs.getInt(1));
				}

				// registar os escaloes
				for (int i = 0; i < parmLoginData.getEscalaoEpoca().size(); i++) {
					preparedStatement = conn
							.prepareStatement("insert into utilizadores_escalao  values(?, ?)");
					preparedStatement.setInt(1, parmLoginData.getId());
					preparedStatement.setInt(2, parmLoginData.getEscalaoEpoca().get(i).getId_escalao_epoca());

					preparedStatement.executeUpdate();
				}
			} else {
				updatePWD(tmpUtiliazdor.getId(), parmLoginData.getPassword());
				enableUser(tmpUtiliazdor.getId());
			}

			preparedStatement = conn
					.prepareStatement("update utilizadores_ativar set estado='1' where user=?");

			preparedStatement.setString(1, parmLoginData.getUser());
			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);
			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	private String generateRandomAlphanumericString() {
		String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		Random random = new Random();
		StringBuilder sb = new StringBuilder(30);

		for (int i = 0; i < 30; i++) {
			sb.append(characters.charAt(random.nextInt(characters.length())));
		}

		return sb.toString();
	}

	public String createUtilizadorParaAtivar(UtilizadorParaAtivarData parmUtilizadorData, int parmTenantID) {

		

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement(
					"insert into utilizadores_ativar(user, estado, code, ids_escalao, nome, email, perfil, tenant_id) values(?, ?, ?, ?, ?, ?, ?, ?)");

			preparedStatement.setString(1, parmUtilizadorData.getUser());
			preparedStatement.setString(2, "0");
			String code = generateRandomAlphanumericString();
			preparedStatement.setString(3, code);
			// ID_escalao
			preparedStatement.setString(4, parmUtilizadorData.getIdsescalao());

			preparedStatement.setString(5, parmUtilizadorData.getNome());
			preparedStatement.setString(6, parmUtilizadorData.getEmail());
			preparedStatement.setString(7, parmUtilizadorData.getPerfil());
			preparedStatement.setInt(8, parmTenantID);

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);
			return code;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return "";
	}

	public String getCodebyUser(String parmUser) {

		
		String code = null;
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select code from utilizadores_ativar ua where ESTADO='0' and user=?");

			preparedStatement.setString(1, parmUser);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				code = rs.getString("code");
			}

			dbUtils.closeConnection(conn);
			return code;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<UtilizadorData> getAllUser(int parmTenantID) {

		
		UtilizadorData utilizadorData = null;
		ArrayList<UtilizadorData> utilizadores = new ArrayList<>();
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select * from utilizadores where tenant_id=? and estado in (0, 1) ");

			preparedStatement.setInt(1, parmTenantID);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorData = new UtilizadorData(rs.getInt("id"), rs.getString("Nome"), rs.getString("user"),
						rs.getString("perfil"), rs.getString("email"), rs.getString("estado"));
				utilizadores.add(utilizadorData);
			}

			dbUtils.closeConnection(conn);
			return utilizadores;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<UtilizadorParaAtivarData> getAllUserWait(int parmTenantID) {

		
		UtilizadorParaAtivarData utilizadorParaAtivarData = null;
		ArrayList<UtilizadorParaAtivarData> utilizadores = new ArrayList<>();
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select * from utilizadores_ativar where estado=0 and tenant_id=?");

			preparedStatement.setInt(1, parmTenantID);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorParaAtivarData = new UtilizadorParaAtivarData(rs.getString("user"), rs.getString("estado"),
						rs.getString("ids_escalao"), rs.getString("Nome"), rs.getString("email"),
						rs.getString("perfil"), rs.getString("code"));
				utilizadores.add(utilizadorParaAtivarData);
			}

			dbUtils.closeConnection(conn);
			return utilizadores;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<EscalaoData> getEscaloesByUser(int parmUserId) {

		
		EscalaoData escalao = null;
		ArrayList<EscalaoData> escaloes = new ArrayList<>();
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select * from utilizadores_escalao inner join escalao_epoca on escalao_epoca.id=utilizadores_escalao.id_escalao_epoca where id_utilizador=?");

			preparedStatement.setInt(1, parmUserId);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				escalao = new EscalaoData(rs.getInt("id_escalao_epoca"), rs.getString("nome"));
				escaloes.add(escalao);
			}

			dbUtils.closeConnection(conn);
			return escaloes;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public UtilizadorData getUser(int parmIdUser, int parmTenantID) {

		
		UtilizadorData utilizadorData = null;

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select * from utilizadores where tenant_id=? and id=?");

			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setInt(2, parmIdUser);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorData = new UtilizadorData(rs.getInt("id"), rs.getString("Nome"), rs.getString("user"),
						rs.getString("perfil"), rs.getString("email"), rs.getString("estado"));

			}

			dbUtils.closeConnection(conn);
			return utilizadorData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public UtilizadorData getUserByUserName(String parmUserName, int parmTenantID) {

		
		UtilizadorData utilizadorData = null;

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select * from utilizadores where tenant_id=? and user=?");

			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setString(2, parmUserName);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorData = new UtilizadorData(rs.getInt("id"), rs.getString("Nome"), rs.getString("user"),
						rs.getString("perfil"), rs.getString("email"), rs.getString("estado"));

			}

			

			if (utilizadorData == null) {

				preparedStatement = conn
						.prepareStatement("select * from utilizadores_ativar where tenant_id=? and user=?");

				preparedStatement.setInt(1, parmTenantID);
				preparedStatement.setString(2, parmUserName);

				rs = preparedStatement.executeQuery();

				if (rs == null) {
					return null;
				}

				while (rs.next()) {

					utilizadorData = new UtilizadorData(0, rs.getString("Nome"), rs.getString("user"),
							rs.getString("perfil"), rs.getString("email"), "0");

				}

			}

			dbUtils.closeConnection(conn);

			return utilizadorData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<HistoricoLoginData> getHistoricoLogins() {

		
		HistoricoLoginData hosHistoricoLoginData = null;
		ArrayList<HistoricoLoginData> listaHistorico = new ArrayList<HistoricoLoginData>();
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select user, data from logins order by data desc LIMIT 500");

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				hosHistoricoLoginData = new HistoricoLoginData(rs.getString("user"), rs.getTimestamp("data"));
				listaHistorico.add(hosHistoricoLoginData);
			}

			dbUtils.closeConnection(conn);
			return listaHistorico;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean updateUserWithEscaloes(ArrayList<Integer> parmEscaloes, int parmUserId) {

		

		try {
			// PRIMEIRO APAGA OS ESCALOES DA EPOCA ATUAL
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("delete from utilizadores_escalao  where  id_utilizador =?\r\n"
							+ "and exists (select *from escalao_epoca inner join EPOCA on ID_EPOCA=EPOCA.ID\r\n"
							+ "where ESTADO='1' and escalao_epoca.id=ID_ESCALAO_EPOCA\r\n" + " )");

			preparedStatement.setInt(1, parmUserId);

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(preparedStatement.getConnection());

			// insere os escaloes:

			for (int i = 0; i < parmEscaloes.size(); i++) {
				Integer valor = parmEscaloes.get(i);

				preparedStatement = conn
						.prepareStatement("INSERT INTO utilizadores_escalao VALUES (?,?) ");

				preparedStatement.setInt(1, parmUserId);
				preparedStatement.setInt(2, valor);

				preparedStatement.executeUpdate();

				

			}

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean updateUser(int parmUserId, UtilizadorData parmUserData) {

		

		try {
			// PRIMEIRO APAGA OS ESCALOES DA EPOCA ATUAL
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("update UTILIZADORES SET NOME=?, PERFIL=?, EMAIL=? where ID=?");

			preparedStatement.setString(1, parmUserData.getNome());
			preparedStatement.setString(2, parmUserData.getPerfil());
			preparedStatement.setString(3, parmUserData.getEmail());
			preparedStatement.setInt(4, parmUserId);

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean updatePWD(int parmUserId, String parmPWD) {

		

		try {
			// PRIMEIRO APAGA OS ESCALOES DA EPOCA ATUAL
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("update UTILIZADORES SET password=? where ID=?");

			preparedStatement.setString(1, parmPWD);
			preparedStatement.setInt(2, parmUserId);

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean enableUser(int parmUserId) {

		

		try {
			// PRIMEIRO APAGA OS ESCALOES DA EPOCA ATUAL
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("update UTILIZADORES SET estado='1' where ID=?");

			preparedStatement.setInt(1, parmUserId);

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean resetPWD(int parmUserId) {

		
		UtilizadorParaAtivarData tmpUtilizadorParaAtivarData = null;

		try {
			// COLOCA UTILIZADOR NUM ESTADO DE AGUARDA PWD
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("update UTILIZADORES SET estado='2' where ID=?");

			preparedStatement.setInt(1, parmUserId);

			preparedStatement.executeUpdate();

			// APAGA POSSIVEIS REGISTOS EM ATIVAÇÃO

			preparedStatement = dbUtils.getConnection().prepareStatement("SELECT * FROM UTILIZADORES WHERE ID=?");

			preparedStatement.setInt(1, parmUserId);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return false;
			}

			while (rs.next()) {

				tmpUtilizadorParaAtivarData = new UtilizadorParaAtivarData(rs.getString("user"), "0", "",
						rs.getString("Nome"), rs.getString("email"), rs.getString("perfil"), "");
				createUtilizadorParaAtivar(tmpUtilizadorParaAtivarData, rs.getInt("Tenant_id"));

			}

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean disableUser(int parmUserId) {

		

		try {
			// PRIMEIRO APAGA OS ESCALOES DA EPOCA ATUAL
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("update UTILIZADORES SET estado='0' where ID=?");

			preparedStatement.setInt(1, parmUserId);

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public String reenviarEmailAtivacao(UtilizadorParaAtivarData parmUtilizadorData, int parmTenantID) {
		String enderecoEmail = parmUtilizadorData.getEmail();
		String codigoAtivacao = getCodebyUser(parmUtilizadorData.getUser());
		String linkAtivacao = "https://sm.com.pt/"
				+ tenantProperties.getTenant_id().get(String.valueOf(parmTenantID)).get("name") + "/#/ativeuser?code="
				+ codigoAtivacao;

		String html = "<!DOCTYPE html>" + "<html>"
				+ "<body style='font-family: Arial, sans-serif; background-color:#f4f4f4; padding:20px;'>"
				+ "  <div style='max-width:600px; margin:0 auto; background:#fff; padding:20px; border-radius:8px; box-shadow:0 2px 4px rgba(0,0,0,0.1);'>"
				+ "    <h2 style='color:#333;'>Olá " + parmUtilizadorData.getNome() + ",</h2>"
				+ "    <p style='color:#555;'>Obrigado por se registar na plataforma <b>Sports Manager - "
				+ tenantProperties.getTenant_id().get(String.valueOf(parmTenantID)).get("name")
				+ "</b>. Para concluir o seu registo, clique no botão abaixo:</p>"
				+ "    <p style='text-align:center; margin:30px 0;'>" + "      <a href='" + linkAtivacao
				+ "' style='background:#007bff; color:#fff; padding:12px 24px; text-decoration:none; border-radius:5px; font-size:16px;'>Ativar Conta</a>"
				+ "    </p>"
				+ "    <p style='color:#777; font-size:14px;'>Se não se registou, pode ignorar este email.</p>"
				+ "    <hr style='margin-top:30px;'/>"
				+ "    <p style='font-size:12px; color:#999;'>Este email foi enviado automaticamente, por favor não responda.</p>"
				+ "  </div>" + "</body>" + "</html>";

		boolean enviado = emailService.enviarEmailHtml(enderecoEmail, "Ative a sua conta - Sports Manager | "
				+ tenantProperties.getTenant_id().get(String.valueOf(parmTenantID)).get("name"), html);

		return enviado ? "Email de ativação enviado com sucesso para " + enderecoEmail
				: "Erro ao enviar email de ativação para " + enderecoEmail;
	}

}
