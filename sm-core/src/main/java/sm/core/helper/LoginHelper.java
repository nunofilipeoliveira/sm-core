package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.SecureRandom;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import sm.core.config.Auth;
import sm.core.config.SecurityProperties;
import sm.core.data.AcessoAtletaData;
import sm.core.data.ConviteAtletaData;
import sm.core.data.EscalaoData;
import sm.core.data.HistoricoLoginData;
import sm.core.data.LoginData;
import sm.core.data.UtilizadorData;
import sm.core.data.UtilizadorParaAtivarData;
import sm.core.security.AcessoNegadoException;
import sm.core.security.ConflitoException;
import sm.core.security.Identidade;
import sm.core.security.PedidoInvalidoException;
import sm.core.utils.EmailService;
import sm.core.utils.PasswordUtils;
import sm.core.utils.TenantProperties;
import sm.core.utils.TokenGenerator;
import sm.core.utils.TokenValidator;

@Component
public class LoginHelper {

	private static final Logger log = LoggerFactory.getLogger(LoginHelper.class);

	@Autowired
	private TenantProperties tenantProperties;


	private final DBUtils dbUtils;

	public LoginHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;

	}

	@Autowired
	private EmailService emailService;

	@Autowired
	private SecurityProperties securityProperties;

	public boolean isAuthenticated(String token) {

		boolean isValid = TokenValidator.isTokenValid(token);
		if (isValid) {
			log.info("O token e valido.");
			return true;
		} else {
			log.info("O token e invalido.");
			return false;
		}

	}

	/**
	 * Ponto unico (R23) que decide se o tenant aceita contas ATLETA (login, convite e
	 * ativacao). Para ja so com a autenticacao ativa e em ENFORCE e a autorizacao em
	 * ENFORCE (Q13; com auth.enabled=false o filtro de token nao corre, I1).
	 */
	public boolean aceitaAtletas(int parmTenantId) {
		Auth auth = securityProperties.getAuth();
		return auth.isEnabled() && auth.isEnforce() && auth.isAuthorizationEnforce();
	}

	private static boolean isPerfilAtleta(String parmPerfil) {
		return parmPerfil != null && Identidade.PERFIL_ATLETA.equalsIgnoreCase(parmPerfil.trim());
	}

	/** Jogador ligado a conta ATLETA: existe, esta ativo, e do tenant da conta e o tenant aceita atletas. */
	private boolean isJogadorAtletaValido(Integer parmIdJogador, String parmJogadorEstado, int parmJogadorTenant,
			int parmTenantId) {
		return parmIdJogador != null && "1".equals(parmJogadorEstado) && parmJogadorTenant == parmTenantId
				&& aceitaAtletas(parmTenantId);
	}

	private static Integer getInteger(ResultSet rs, String parmColuna) throws SQLException {
		int valor = rs.getInt(parmColuna);
		return rs.wasNull() ? null : valor;
	}

	public LoginData Dologin(String parmUser, String parmPWD, int parmTenant_id) {

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select uti.id, uti.user, uti.password, uti.nome, uti.perfil, uti.id_jogador,\r\n"
							+ "j.estado jogador_estado, j.tenant_id jogador_tenant_id from utilizadores uti\r\n"
							+ "left join jogador j on j.id=uti.id_jogador\r\n"
							+ "where uti.estado='1' and uti.user=? and uti.tenant_id=?");

			preparedStatement.setString(1, parmUser);
			preparedStatement.setInt(2, parmTenant_id);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null || !rs.next()) {
				return null;
			}

			String storedPassword = rs.getString("password");

			// A password deixou de ser comparada em SQL (estava em texto simples
			// na tabela): e validada aqui, aceitando BCrypt (novo formato) e
			// texto simples (formato legado).
			if (!PasswordUtils.matches(parmPWD, storedPassword)) {
				log.info("Dologin | Password invalida para o utilizador: " + parmUser);
				return null;
			}

			// A password nunca vai na resposta (R19)
			LoginData loginData = new LoginData(rs.getInt("id"), rs.getString("nome"), rs.getString("user"), null,
					null, rs.getString("perfil"));
			Integer idJogador = null;

			if (isPerfilAtleta(loginData.getPerfil())) {
				idJogador = getInteger(rs, "id_jogador");
				if (!isJogadorAtletaValido(idJogador, rs.getString("jogador_estado"), rs.getInt("jogador_tenant_id"),
						parmTenant_id)) {
					// mesma resposta que credenciais erradas (R8)
					log.info("Dologin | Atleta sem acesso (jogador inativo, de outro tenant ou tenant sem atletas): "
							+ parmUser);
					return null;
				}
				loginData.setIdJogador(idJogador);
			} else {
				PreparedStatement psEscaloes = conn.prepareStatement(
						"select ee.id idescalao_epoca, ee.nome desctivoescalao from utilizadores_escalao ue\r\n"
								+ "inner join escalao_epoca ee on ue.id_escalao_epoca=ee.id \r\n"
								+ "inner join epoca e on e.id =ee.id_epoca\r\n"
								+ "inner join escalao e2 on ee.id_escalao =e2.id \r\n"
								+ "where e.estado ='1' and ue.id_utilizador=?");
				psEscaloes.setInt(1, loginData.getId());
				ResultSet rsEscaloes = psEscaloes.executeQuery();
				while (rsEscaloes.next()) {
					loginData.adicionarEscalaoEpoca(rsEscaloes.getInt("idescalao_epoca"),
							rsEscaloes.getString("desctivoescalao"));
				}
				// sem escalao na epoca ativa: login recusado (regra atual)
				if (loginData.getEscalaoEpoca().isEmpty()) {
					return null;
				}
			}

			loginData.setToken(TokenGenerator.generateToken(new Identidade(loginData.getId(), loginData.getUser(),
					parmTenant_id, loginData.getPerfil(), idJogador)));

			// MIGRACAO LAZY: se a password ainda estava em texto simples fica
			// encriptada (BCrypt) a partir deste login, sem obrigar a reset.
			if (!PasswordUtils.isEncoded(storedPassword)) {
				updatePWD(loginData.getId(), parmPWD);
				log.info("Dologin | Password do utilizador " + parmUser + " migrada para BCrypt.");
			}

			// regista histórico de acesso

			PreparedStatement preparedStatement2 = conn
					.prepareStatement("insert into logins values (?, now(), ?,?)");

			preparedStatement2.setInt(1, loginData.getId());
			preparedStatement2.setString(2, parmUser);
			// extrai o tenant name
			preparedStatement2.setString(3,
					tenantProperties.getTenant_id().get(String.valueOf(parmTenant_id)).get("name"));
			preparedStatement2.executeUpdate();

			return loginData;

		} catch (SQLException e) {
			log.error("Erro em Dologin: " + e.getMessage(), e);
		}

		return null;
	}

	/**
	 * Recarrega da BD a identidade de um utilizador ativo (para o extendSession).
	 * Para ATLETA exige tambem o jogador ativo, do mesmo tenant, e o tenant a aceitar atletas.
	 *
	 * @return a identidade atualizada, ou {@code null} se a sessao nao puder ser renovada
	 */
	public Identidade carregaIdentidadeAtiva(int parmIdUtilizador) {

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select uti.id, uti.user, uti.tenant_id, uti.perfil, uti.id_jogador,\r\n"
							+ "j.estado jogador_estado, j.tenant_id jogador_tenant_id from utilizadores uti\r\n"
							+ "left join jogador j on j.id=uti.id_jogador\r\n"
							+ "where uti.estado='1' and uti.id=?");
			preparedStatement.setInt(1, parmIdUtilizador);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null || !rs.next()) {
				return null;
			}

			int tenantId = rs.getInt("tenant_id");
			String perfil = rs.getString("perfil");
			Integer idJogador = null;
			if (isPerfilAtleta(perfil)) {
				idJogador = getInteger(rs, "id_jogador");
				if (!isJogadorAtletaValido(idJogador, rs.getString("jogador_estado"), rs.getInt("jogador_tenant_id"),
						tenantId)) {
					return null;
				}
			}
			return new Identidade(rs.getInt("id"), rs.getString("user"), tenantId, perfil, idJogador);

		} catch (SQLException e) {
			log.error("Erro em carregaIdentidadeAtiva: " + e.getMessage(), e);
		}

		return null;
	}

	public UtilizadorParaAtivarData getUtilizadorParaAtivarByCode(String parmCode) {

		
		UtilizadorParaAtivarData utilizadorParaAtivarData = null;
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select *from utilizadores_ativar ua where estado='0' and code=?");

			preparedStatement.setString(1, parmCode);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorParaAtivarData = new UtilizadorParaAtivarData(rs.getString("user"), rs.getString("estado"),
						rs.getString("ids_escalao"), rs.getString("nome"), rs.getString("email"),
						rs.getString("perfil"), rs.getString("code"));
				utilizadorParaAtivarData.setTenant_id(rs.getInt("tenant_id"));
				utilizadorParaAtivarData.setId_jogador(getInteger(rs, "id_jogador"));
			}

			return utilizadorParaAtivarData;

		} catch (SQLException e) {
			log.error("Erro ao obter utilizador para ativação por código: " + parmCode, e);
		}

		return null;
	}

	/**
	 * Ativacao de conta por codigo (D6). Do corpo so se usam o {@code code} e a
	 * {@code password}: user, nome, email, perfil, escaloes e jogador vem da linha
	 * pendente (R5, R20).
	 *
	 * @return {@code true} se a conta ficou ativa
	 */
	public boolean createUtilizador(LoginData parmLoginData, int parmTenantID) {

		String password = parmLoginData.getPassword();
		if (parmLoginData.getCode() == null || parmLoginData.getCode().isBlank() || password == null
				|| password.isEmpty()) {
			log.info("createUtilizador | pedido sem codigo ou sem password");
			return false;
		}

		UtilizadorParaAtivarData pendente = getUtilizadorParaAtivarByCode(parmLoginData.getCode());
		if (pendente == null || pendente.getTenant_id() != parmTenantID) {
			log.info("createUtilizador | codigo invalido para o tenant " + parmTenantID);
			return false;
		}

		boolean atleta = isPerfilAtleta(pendente.getPerfil());
		Integer idJogador = atleta ? pendente.getId_jogador() : null;
		PreparedStatement preparedStatement = null;

		try (Connection conn = dbUtils.getConnection()) {

			// verifica se já existe e é apenas para ativar.
			preparedStatement = conn
					.prepareStatement("select id, id_jogador from utilizadores where user=? and tenant_id=?");
			preparedStatement.setString(1, pendente.getUser());
			preparedStatement.setInt(2, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();
			int idExistente = 0;
			Integer idJogadorExistente = null;
			if (rs.next()) {
				idExistente = rs.getInt("id");
				idJogadorExistente = getInteger(rs, "id_jogador");
			}

			if (atleta) {
				if (idJogador == null || !aceitaAtletas(parmTenantID)) {
					log.info("createUtilizador | convite ATLETA sem jogador ou tenant sem atletas");
					return false;
				}
				preparedStatement = conn.prepareStatement("select estado from jogador where id=? and tenant_id=?");
				preparedStatement.setInt(1, idJogador);
				preparedStatement.setInt(2, parmTenantID);
				rs = preparedStatement.executeQuery();
				if (!rs.next() || !"1".equals(rs.getString("estado"))) {
					log.info("createUtilizador | jogador inativo ou de outro tenant: " + idJogador);
					return false;
				}
				// no maximo uma conta por jogador (R3); a propria conta pode ser reativada (resetPWD)
				preparedStatement = conn.prepareStatement("select id from utilizadores where id_jogador=?");
				preparedStatement.setInt(1, idJogador);
				rs = preparedStatement.executeQuery();
				if (rs.next() && rs.getInt("id") != idExistente) {
					log.info("createUtilizador | jogador ja tem outra conta: " + idJogador);
					return false;
				}
			}
			// uma conta existente so e reativada se estiver ligada ao mesmo jogador (ou a nenhum)
			if (idExistente != 0 && !java.util.Objects.equals(idJogadorExistente, idJogador)) {
				log.info("createUtilizador | conta existente ligada a outro jogador: " + pendente.getUser());
				return false;
			}

			if (idExistente == 0) {

				ArrayList<Integer> escaloes = new ArrayList<>();
				if (!atleta && pendente.getIdsescalao() != null) {
					for (String id : pendente.getIdsescalao().split(";")) {
						if (!id.isBlank()) {
							escaloes.add(Integer.parseInt(id.trim()));
						}
					}
				}

				preparedStatement = conn.prepareStatement(
						"insert into utilizadores(nome, user, password, perfil, tenant_id, estado, email, id_jogador) values(?, ?, ?, ?, ?, 1, ?, ?)",
						PreparedStatement.RETURN_GENERATED_KEYS);

				preparedStatement.setString(1, pendente.getNome());
				preparedStatement.setString(2, pendente.getUser());
				preparedStatement.setString(3, PasswordUtils.encodeIfNeeded(password));
				preparedStatement.setString(4, pendente.getPerfil());
				preparedStatement.setInt(5, parmTenantID);
				preparedStatement.setString(6, pendente.getEmail());
				preparedStatement.setObject(7, idJogador, java.sql.Types.INTEGER);
				preparedStatement.executeUpdate();
				rs = preparedStatement.getGeneratedKeys();

				int idNovo = 0;
				if (rs.next()) {
					idNovo = rs.getInt(1);
				}

				// registar os escaloes
				for (Integer idEscalaoEpoca : escaloes) {
					preparedStatement = conn
							.prepareStatement("insert into utilizadores_escalao  values(?, ?)");
					preparedStatement.setInt(1, idNovo);
					preparedStatement.setInt(2, idEscalaoEpoca);

					preparedStatement.executeUpdate();
				}
			} else {
				updatePWD(idExistente, password);
				enableUser(idExistente);
			}

			preparedStatement = conn
					.prepareStatement("update utilizadores_ativar set estado='1' where user=? and tenant_id=? and estado='0'");

			preparedStatement.setString(1, pendente.getUser());
			preparedStatement.setInt(2, parmTenantID);
			preparedStatement.executeUpdate();

			return true;

		} catch (SQLException | NumberFormatException e) {
			log.error("Erro em createUtilizador: " + e.getMessage(), e);
		}

		return false;
	}

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private String generateRandomAlphanumericString() {
		String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		StringBuilder sb = new StringBuilder(30);

		for (int i = 0; i < 30; i++) {
			sb.append(characters.charAt(SECURE_RANDOM.nextInt(characters.length())));
		}

		return sb.toString();
	}

	/** Username ja usado no tenant, numa conta ou num pendente por ativar. */
	private boolean existeUsername(Connection conn, String parmUser, int parmTenantID) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement(
				"select 1 from utilizadores where user=? and tenant_id=? union all "
						+ "select 1 from utilizadores_ativar where estado='0' and user=? and tenant_id=? LIMIT 1");
		preparedStatement.setString(1, parmUser);
		preparedStatement.setInt(2, parmTenantID);
		preparedStatement.setString(3, parmUser);
		preparedStatement.setInt(4, parmTenantID);
		return preparedStatement.executeQuery().next();
	}

	/**
	 * Novo utilizador de staff por ativar (createUtilizador). O jogador nunca vem do
	 * pedido: as contas ATLETA so se criam por {@link #convidarAtleta}.
	 *
	 * @throws ConflitoException se o username ja existir no tenant
	 */
	public String createUtilizadorParaAtivar(UtilizadorParaAtivarData parmUtilizadorData, int parmTenantID) {

		try (Connection conn = dbUtils.getConnection()) {
			if (existeUsername(conn, parmUtilizadorData.getUser(), parmTenantID)) {
				throw new ConflitoException("O utilizador já existe.");
			}
		} catch (SQLException e) {
			log.error("Erro em createUtilizadorParaAtivar: " + e.getMessage(), e);
			return "";
		}

		return inserirUtilizadorParaAtivar(parmUtilizadorData.getUser(), parmUtilizadorData.getIdsescalao(),
				parmUtilizadorData.getNome(), parmUtilizadorData.getEmail(), parmUtilizadorData.getPerfil(),
				parmTenantID, null);
	}

	private String inserirUtilizadorParaAtivar(String parmUser, String parmIdsEscalao, String parmNome,
			String parmEmail, String parmPerfil, int parmTenantID, Integer parmIdJogador) {

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"insert into utilizadores_ativar(user, estado, code, ids_escalao, nome, email, perfil, tenant_id, id_jogador) values(?, ?, ?, ?, ?, ?, ?, ?, ?)");

			preparedStatement.setString(1, parmUser);
			preparedStatement.setString(2, "0");
			String code = generateRandomAlphanumericString();
			preparedStatement.setString(3, code);
			// ID_escalao
			preparedStatement.setString(4, parmIdsEscalao);

			preparedStatement.setString(5, parmNome);
			preparedStatement.setString(6, parmEmail);
			preparedStatement.setString(7, parmPerfil);
			preparedStatement.setInt(8, parmTenantID);
			preparedStatement.setObject(9, parmIdJogador, java.sql.Types.INTEGER);

			preparedStatement.executeUpdate();

			return code;

		} catch (SQLException e) {
			log.error("Erro em createUtilizador: " + e.getMessage(), e);
		}

		return "";
	}

	/**
	 * Convite de um atleta (so ADMIN), a partir da ficha do jogador. Cria o pendente
	 * ATLETA ligado ao jogador e envia o email de convite.
	 *
	 * @return o codigo de ativacao
	 * @throws PedidoInvalidoException user ou email vazios, ou email invalido
	 * @throws AcessoNegadoException jogador inexistente ou de outro tenant
	 * @throws ConflitoException jogador inativo, ja com conta ou convite pendente, username
	 *                           ja existente, ou tenant sem atletas
	 */
	public String convidarAtleta(ConviteAtletaData parmConvite, int parmTenantID) {

		String user = parmConvite.getUser() == null ? "" : parmConvite.getUser().trim();
		String email = parmConvite.getEmail() == null ? "" : parmConvite.getEmail().trim();
		if (user.isEmpty() || email.isEmpty()) {
			throw new PedidoInvalidoException("O utilizador e o email são obrigatórios.");
		}
		if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
			throw new PedidoInvalidoException("O email não é válido.");
		}

		String nomeJogador;
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select nome, estado from jogador where id=? and tenant_id=?");
			preparedStatement.setInt(1, parmConvite.getIdJogador());
			preparedStatement.setInt(2, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();
			if (!rs.next()) {
				throw new AcessoNegadoException();
			}
			nomeJogador = rs.getString("nome");
			if (!"1".equals(rs.getString("estado"))) {
				throw new ConflitoException("O jogador está inativo.");
			}
			if (!aceitaAtletas(parmTenantID)) {
				throw new ConflitoException("O clube ainda não aceita contas de atleta.");
			}

			preparedStatement = conn.prepareStatement("select 1 from utilizadores where id_jogador=?");
			preparedStatement.setInt(1, parmConvite.getIdJogador());
			if (preparedStatement.executeQuery().next()) {
				throw new ConflitoException("O jogador já tem conta.");
			}

			preparedStatement = conn
					.prepareStatement("select 1 from utilizadores_ativar where id_jogador=? and estado='0'");
			preparedStatement.setInt(1, parmConvite.getIdJogador());
			if (preparedStatement.executeQuery().next()) {
				throw new ConflitoException("O jogador já tem um convite pendente.");
			}

			if (existeUsername(conn, user, parmTenantID)) {
				throw new ConflitoException("O utilizador já existe.");
			}
		} catch (SQLException e) {
			log.error("Erro em convidarAtleta: " + e.getMessage(), e);
			return "";
		}

		String code = inserirUtilizadorParaAtivar(user, "", nomeJogador, email, Identidade.PERFIL_ATLETA, parmTenantID,
				parmConvite.getIdJogador());
		if (!code.isEmpty() && !enviarEmailConviteAtleta(email, nomeJogador, code, parmTenantID)) {
			log.error("convidarAtleta | Erro ao enviar email de convite para " + email);
		}
		return code;
	}

	/**
	 * Estado do acesso de um jogador: SEM_CONTA, PENDENTE (com o codigo), ATIVA,
	 * DESATIVADA ou AGUARDA_PASSWORD.
	 *
	 * @throws AcessoNegadoException jogador inexistente ou de outro tenant
	 */
	public AcessoAtletaData getAcessoAtleta(int parmIdJogador, int parmTenantID) {

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select 1 from jogador where id=? and tenant_id=?");
			preparedStatement.setInt(1, parmIdJogador);
			preparedStatement.setInt(2, parmTenantID);
			if (!preparedStatement.executeQuery().next()) {
				throw new AcessoNegadoException();
			}

			preparedStatement = conn.prepareStatement(
					"select id, user, email, estado from utilizadores where id_jogador=? and tenant_id=?");
			preparedStatement.setInt(1, parmIdJogador);
			preparedStatement.setInt(2, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();
			AcessoAtletaData acesso;
			if (rs.next()) {
				acesso = new AcessoAtletaData(estadoConta(rs.getString("estado")), rs.getInt("id"),
						rs.getString("user"), rs.getString("email"), null);
			} else {
				preparedStatement = conn.prepareStatement(
						"select user, email, code from utilizadores_ativar where id_jogador=? and tenant_id=? and estado='0'");
				preparedStatement.setInt(1, parmIdJogador);
				preparedStatement.setInt(2, parmTenantID);
				rs = preparedStatement.executeQuery();
				acesso = rs.next()
						? new AcessoAtletaData("PENDENTE", null, rs.getString("user"), rs.getString("email"),
								rs.getString("code"))
						: new AcessoAtletaData("SEM_CONTA", null, null, null, null);
			}
			acesso.setIdJogador(parmIdJogador);
			return acesso;

		} catch (SQLException e) {
			log.error("Erro em getAcessoAtleta: " + e.getMessage(), e);
		}

		return null;
	}

	/** Estado de acesso de uma conta a partir de utilizadores.estado. */
	private static String estadoConta(String parmEstadoUtilizador) {
		switch (String.valueOf(parmEstadoUtilizador)) {
		case "1":
			return "ATIVA";
		case "2":
			return "AGUARDA_PASSWORD";
		default:
			return "DESATIVADA";
		}
	}

	/**
	 * Estado do acesso de todos os jogadores ativos do tenant (mesma regra de
	 * {@link #getAcessoAtleta}), numa so query.
	 */
	public ArrayList<AcessoAtletaData> getAcessoAtletas(int parmTenantID) {

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select j.id id_jogador, u.id id_utilizador, u.user u_user, u.email u_email, u.estado u_estado,\r\n"
							+ "ua.user ua_user, ua.email ua_email, ua.code ua_code\r\n"
							+ "from jogador j\r\n"
							+ "left join utilizadores u on u.id_jogador=j.id and u.tenant_id=j.tenant_id\r\n"
							+ "left join utilizadores_ativar ua on ua.id_jogador=j.id and ua.tenant_id=j.tenant_id and ua.estado='0'\r\n"
							+ "where j.tenant_id=? and j.estado='1'");
			preparedStatement.setInt(1, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();

			// pendentes repetidos dao varias linhas por jogador: a primeira chega
			java.util.LinkedHashMap<Integer, AcessoAtletaData> porJogador = new java.util.LinkedHashMap<>();
			while (rs.next()) {
				int idJogador = rs.getInt("id_jogador");
				if (porJogador.containsKey(idJogador)) {
					continue;
				}
				AcessoAtletaData acesso;
				Integer idUtilizador = getInteger(rs, "id_utilizador");
				if (idUtilizador != null) {
					acesso = new AcessoAtletaData(estadoConta(rs.getString("u_estado")), idUtilizador,
							rs.getString("u_user"), rs.getString("u_email"), null);
				} else if (rs.getString("ua_user") != null) {
					acesso = new AcessoAtletaData("PENDENTE", null, rs.getString("ua_user"), rs.getString("ua_email"),
							rs.getString("ua_code"));
				} else {
					acesso = new AcessoAtletaData("SEM_CONTA", null, null, null, null);
				}
				acesso.setIdJogador(idJogador);
				porJogador.put(idJogador, acesso);
			}
			return new ArrayList<>(porJogador.values());

		} catch (SQLException e) {
			log.error("Erro em getAcessoAtletas: " + e.getMessage(), e);
		}

		return null;
	}

	/**
	 * Cancela os convites pendentes do user no tenant. utilizadores_ativar.estado:
	 * '0' pendente, '1' ativado, '2' cancelado (Q5: marca, sem DELETE).
	 *
	 * @throws PedidoInvalidoException user vazio
	 * @throws ConflitoException nenhum convite pendente
	 */
	public boolean cancelarConvite(String parmUser, int parmTenantID) {

		if (parmUser == null || parmUser.trim().isEmpty()) {
			throw new PedidoInvalidoException("O utilizador é obrigatório.");
		}

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"update utilizadores_ativar set estado='2' where user=? and tenant_id=? and estado='0'");
			preparedStatement.setString(1, parmUser);
			preparedStatement.setInt(2, parmTenantID);
			if (preparedStatement.executeUpdate() == 0) {
				throw new ConflitoException("O convite já não está pendente.");
			}
			return true;

		} catch (SQLException e) {
			log.error("Erro em cancelarConvite: " + e.getMessage(), e);
		}

		return false;
	}

	/** Codigo pendente do utilizador no tenant. */
	public String getCodebyUser(String parmUser, int parmTenantID) {

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select code from utilizadores_ativar ua where estado='0' and user=? and tenant_id=?");
			preparedStatement.setString(1, parmUser);
			preparedStatement.setInt(2, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();

			String code = null;
			while (rs.next()) {
				code = rs.getString("code");
			}
			return code;

		} catch (SQLException e) {
			log.error("Erro em getCodebyUser: " + e.getMessage(), e);
		}

		return null;
	}

	public ArrayList<UtilizadorData> getAllUser(int parmTenantID) {

		
		UtilizadorData utilizadorData = null;
		ArrayList<UtilizadorData> utilizadores = new ArrayList<>();
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select u.*, (select max(l.data) from logins l where l.id_utilizador=u.id) ultimo_acesso"
							+ " from utilizadores u where u.tenant_id=? and u.estado in ('0','1','2')");

			preparedStatement.setInt(1, parmTenantID);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorData = new UtilizadorData(rs.getInt("id"), rs.getString("nome"), rs.getString("user"),
						rs.getString("perfil"), rs.getString("email"), rs.getString("estado"));
				utilizadorData.setIdJogador(getInteger(rs, "id_jogador"));
				utilizadorData.setUltimoAcesso(rs.getTimestamp("ultimo_acesso"));
				utilizadores.add(utilizadorData);
			}

			return utilizadores;

		} catch (SQLException e) {
			log.error("Erro em getCodebyUser: " + e.getMessage(), e);
		}

		return null;
	}

	public ArrayList<UtilizadorParaAtivarData> getAllUserWait(int parmTenantID) {

		
		UtilizadorParaAtivarData utilizadorParaAtivarData = null;
		ArrayList<UtilizadorParaAtivarData> utilizadores = new ArrayList<>();
		try (Connection conn = dbUtils.getConnection()) {
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

			return utilizadores;

		} catch (SQLException e) {
			log.error("Erro em getAllUser: " + e.getMessage(), e);
		}

		return null;
	}

	public ArrayList<EscalaoData> getEscaloesByUser(int parmUserId) {

		
		EscalaoData escalao = null;
		ArrayList<EscalaoData> escaloes = new ArrayList<>();
		try (Connection conn = dbUtils.getConnection()) {
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

			return escaloes;

		} catch (SQLException e) {
			log.error("Erro em getAllUserWait: " + e.getMessage(), e);
		}

		return null;
	}

	public UtilizadorData getUser(int parmIdUser, int parmTenantID) {

		
		UtilizadorData utilizadorData = null;

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select * from utilizadores where tenant_id=? and id=?");

			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setInt(2, parmIdUser);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorData = new UtilizadorData(rs.getInt("id"), rs.getString("nome"), rs.getString("user"),
						rs.getString("perfil"), rs.getString("email"), rs.getString("estado"));

			}

			return utilizadorData;

		} catch (SQLException e) {
			log.error("Erro em getEscaloesByUser: " + e.getMessage(), e);
		}

		return null;
	}

	public UtilizadorData getUserByUserName(String parmUserName, int parmTenantID) {

		
		UtilizadorData utilizadorData = null;

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select * from utilizadores where tenant_id=? and user=?");

			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setString(2, parmUserName);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				utilizadorData = new UtilizadorData(rs.getInt("id"), rs.getString("nome"), rs.getString("user"),
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

					utilizadorData = new UtilizadorData(0, rs.getString("nome"), rs.getString("user"),
							rs.getString("perfil"), rs.getString("email"), "0");

				}

			}


			return utilizadorData;

		} catch (SQLException e) {
			log.error("Erro em getUser: " + e.getMessage(), e);
		}

		return null;
	}

	public ArrayList<HistoricoLoginData> getHistoricoLogins() {

		
		HistoricoLoginData hosHistoricoLoginData = null;
		ArrayList<HistoricoLoginData> listaHistorico = new ArrayList<HistoricoLoginData>();
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select * from logins order by data desc LIMIT 500");

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				hosHistoricoLoginData = new HistoricoLoginData(rs.getString("user"), rs.getTimestamp("data"), rs.getString("tenant_name"));
				listaHistorico.add(hosHistoricoLoginData);
			}

			return listaHistorico;

		} catch (SQLException e) {
			log.error("Erro em getUserByUserName: " + e.getMessage(), e);
		}

		return null;
	}

	/** Perfil atual do utilizador (null se nao existir ou nao tiver perfil). */
	private String getPerfilUtilizador(Connection conn, int parmUserId) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select perfil from utilizadores where id=?");
		preparedStatement.setInt(1, parmUserId);
		ResultSet rs = preparedStatement.executeQuery();
		return rs.next() ? rs.getString("perfil") : null;
	}

	/** @throws ConflitoException se a conta for ATLETA (nao tem escaloes) */
	public boolean updateUserWithEscaloes(ArrayList<Integer> parmEscaloes, int parmUserId) {



		try (Connection conn = dbUtils.getConnection()) {
			if (isPerfilAtleta(getPerfilUtilizador(conn, parmUserId))) {
				throw new ConflitoException("Uma conta de atleta não tem escalões.");
			}

			// PRIMEIRO APAGA OS ESCALOES DA EPOCA ATUAL
			PreparedStatement preparedStatement = conn
					.prepareStatement("delete from utilizadores_escalao  where  id_utilizador =?\r\n"
							+ "and exists (select *from escalao_epoca inner join epoca on id_epoca=epoca.id\r\n"
							+ "where estado='1' and escalao_epoca.id=id_escalao_epoca\r\n" + " )");

			preparedStatement.setInt(1, parmUserId);

			preparedStatement.executeUpdate();

			

			// insere os escaloes:

			for (int i = 0; i < parmEscaloes.size(); i++) {
				Integer valor = parmEscaloes.get(i);

				preparedStatement = conn
						.prepareStatement("INSERT INTO utilizadores_escalao VALUES (?,?) ");

				preparedStatement.setInt(1, parmUserId);
				preparedStatement.setInt(2, valor);

				preparedStatement.executeUpdate();

				

			}


			return true;

		} catch (SQLException e) {
			log.error("Erro no método: " + e.getMessage(), e);
			return false;
		}
	}

	/** @throws ConflitoException se o pedido mudar o perfil de ou para ATLETA */
	public boolean updateUser(int parmUserId, UtilizadorData parmUserData) {



		try (Connection conn = dbUtils.getConnection()) {
			// o perfil ATLETA so se cria por convite e nao se troca (R5, R20)
			if (isPerfilAtleta(getPerfilUtilizador(conn, parmUserId)) != isPerfilAtleta(parmUserData.getPerfil())) {
				throw new ConflitoException("Não é possível mudar o perfil de ou para ATLETA.");
			}

			PreparedStatement preparedStatement = conn
					.prepareStatement("update utilizadores SET nome=?, perfil=?, email=? where id=?");

			preparedStatement.setString(1, parmUserData.getNome());
			preparedStatement.setString(2, parmUserData.getPerfil());
			preparedStatement.setString(3, parmUserData.getEmail());
			preparedStatement.setInt(4, parmUserId);

			preparedStatement.executeUpdate();


			return true;

		} catch (SQLException e) {
			log.error("Erro no método: " + e.getMessage(), e);
			return false;
		}
	}

	public boolean updatePWD(int parmUserId, String parmPWD) {

		

		try (Connection conn = dbUtils.getConnection()) {
			// PRIMEIRO APAGA OS ESCALOES DA EPOCA ATUAL
			PreparedStatement preparedStatement = conn
					.prepareStatement("update utilizadores SET password=? where id=?");

			preparedStatement.setString(1, PasswordUtils.encodeIfNeeded(parmPWD));
			preparedStatement.setInt(2, parmUserId);

			preparedStatement.executeUpdate();


			return true;

		} catch (SQLException e) {
			log.error("Erro no método: " + e.getMessage(), e);
			return false;
		}
	}

	public boolean enableUser(int parmUserId) {

		

		try (Connection conn = dbUtils.getConnection()) {
			// PRIMEIRO APAGA OS ESCALOES DA EPOCA ATUAL
			PreparedStatement preparedStatement = conn
					.prepareStatement("update utilizadores SET estado='1' where id=?");

			preparedStatement.setInt(1, parmUserId);

			preparedStatement.executeUpdate();


			return true;

		} catch (SQLException e) {
			log.error("Erro no método: " + e.getMessage(), e);
			return false;
		}
	}

	public boolean resetPWD(int parmUserId) {

		

		try (Connection conn = dbUtils.getConnection()) {
			// COLOCA UTILIZADOR NUM ESTADO DE AGUARDA PWD
			PreparedStatement preparedStatement = conn
					.prepareStatement("update utilizadores SET estado='2' where id=?");

			preparedStatement.setInt(1, parmUserId);

			preparedStatement.executeUpdate();

			// APAGA POSSIVEIS REGISTOS EM ATIVAÇÃO

			preparedStatement = conn.prepareStatement(
					"SELECT u.user, u.nome, u.email, u.perfil, u.tenant_id, u.id_jogador, ue.id_escalao_epoca "
							+ "FROM utilizadores u LEFT JOIN utilizadores_escalao ue ON u.id = ue.id_utilizador WHERE u.id=?");

			preparedStatement.setInt(1, parmUserId);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return false;
			}

			String tmpIdsEscalao = "";
			String user = null;
			String nome = null;
			String email = null;
			String perfil = null;
			int tenantId = 0;
			Integer idJogador = null;

			while (rs.next()) {

				if (rs.getString("id_escalao_epoca") != null) {
					tmpIdsEscalao += rs.getString("id_escalao_epoca") + ";";
				}

				user = rs.getString("user");
				nome = rs.getString("nome");
				email = rs.getString("email");
				perfil = rs.getString("perfil");
				tenantId = rs.getInt("tenant_id");
				idJogador = getInteger(rs, "id_jogador");

			}

			if (user == null) {
				return false;
			}

			//retira o ultimo ; do tmpIdsEscalao
			if (tmpIdsEscalao.endsWith(";")) {
				tmpIdsEscalao = tmpIdsEscalao.substring(0, tmpIdsEscalao.length() - 1);
			}
			

			// o pendente mantem o jogador da conta (CA15); sem verificacao de username, que ja existe
			inserirUtilizadorParaAtivar(user, tmpIdsEscalao, nome, email, perfil, tenantId, idJogador);


			return true;

		} catch (SQLException e) {
			log.error("Erro no método: " + e.getMessage(), e);
			return false;
		}
	}

	public boolean disableUser(int parmUserId) {

		

		try (Connection conn = dbUtils.getConnection()) {
			// PRIMEIRO APAGA OS ESCALOES DA EPOCA ATUAL
			PreparedStatement preparedStatement = conn
					.prepareStatement("update utilizadores SET estado='0' where id=?");

			preparedStatement.setInt(1, parmUserId);

			preparedStatement.executeUpdate();


			return true;

		} catch (SQLException e) {
			log.error("Erro no método: " + e.getMessage(), e);
			return false;
		}
	}

	private String getLinkAtivacao(String parmCode, int parmTenantID) {
		return "https://sm.com.pt/" + tenantProperties.getTenant_id().get(String.valueOf(parmTenantID)).get("name")
				+ "/#/ativeuser?code=" + parmCode;
	}

	/** Email de convite de atleta (Q10), usado no convite e no reenvio. */
	private boolean enviarEmailConviteAtleta(String parmEmail, String parmNomeJogador, String parmCode,
			int parmTenantID) {
		String clube = tenantProperties.getTenant_id().get(String.valueOf(parmTenantID)).get("name");
		String html = "<!DOCTYPE html>" + "<html>"
				+ "<body style='font-family: Arial, sans-serif; background-color:#f4f4f4; padding:20px;'>"
				+ "  <div style='max-width:600px; margin:0 auto; background:#fff; padding:20px; border-radius:8px; box-shadow:0 2px 4px rgba(0,0,0,0.1);'>"
				+ "    <h2 style='color:#333;'>Olá,</h2>"
				+ "    <p style='color:#555;'>O clube <b>" + HtmlUtils.htmlEscape(clube)
				+ "</b> convidou-o a aceder à ficha de atleta de <b>"
				+ HtmlUtils.htmlEscape(parmNomeJogador == null ? "" : parmNomeJogador)
				+ "</b> na plataforma Sports Manager. Para ativar o acesso, clique no botão abaixo:</p>"
				+ "    <p style='text-align:center; margin:30px 0;'>" + "      <a href='" + getLinkAtivacao(parmCode, parmTenantID)
				+ "' style='background:#007bff; color:#fff; padding:12px 24px; text-decoration:none; border-radius:5px; font-size:16px;'>Ativar Acesso</a>"
				+ "    </p>"
				+ "    <p style='color:#777; font-size:14px;'>Se não esperava este convite, pode ignorar este email.</p>"
				+ "    <hr style='margin-top:30px;'/>"
				+ "    <p style='font-size:12px; color:#999;'>Este email foi enviado automaticamente, por favor não responda.</p>"
				+ "  </div>" + "</body>" + "</html>";

		return emailService.enviarEmailHtml(parmEmail, "Convite de acesso à ficha de atleta - Sports Manager | " + clube,
				html);
	}

	public String reenviarEmailAtivacao(UtilizadorParaAtivarData parmUtilizadorData, int parmTenantID) {
		String enderecoEmail = parmUtilizadorData.getEmail();

		return enviarEmailAtivacao(parmUtilizadorData.getUser(), enderecoEmail, parmUtilizadorData.getNome(),
				parmTenantID) ? "Email de ativação enviado com sucesso para " + enderecoEmail
						: "Erro ao enviar email de ativação para " + enderecoEmail;
	}

	/**
	 * Reenvio do convite pela Administracao (so ADMIN). Se o envio tiver sucesso, o
	 * email passa a ficar gravado no pendente (Q3).
	 *
	 * @throws PedidoInvalidoException user ou email vazios, email invalido ou com mais de 100 caracteres
	 * @throws ConflitoException sem pendente, ou falha no envio (nada e gravado)
	 */
	public String reenviarConvite(UtilizadorParaAtivarData parmDados, int parmTenantID) {

		String user = parmDados.getUser();
		String email = parmDados.getEmail() == null ? "" : parmDados.getEmail().trim();
		if (user == null || user.trim().isEmpty() || email.isEmpty()) {
			throw new PedidoInvalidoException("O utilizador e o email são obrigatórios.");
		}
		// utilizadores_ativar.email e varchar(100)
		if (email.length() > 100 || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
			throw new PedidoInvalidoException("O email não é válido.");
		}
		if (getCodebyUser(user, parmTenantID) == null) {
			throw new ConflitoException("O convite já não está pendente.");
		}
		if (!enviarEmailAtivacao(user, email, parmDados.getNome(), parmTenantID)) {
			throw new ConflitoException("Não foi possível enviar o email para " + email + ".");
		}

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"update utilizadores_ativar set email=? where user=? and tenant_id=? and estado='0'");
			preparedStatement.setString(1, email);
			preparedStatement.setString(2, user);
			preparedStatement.setInt(3, parmTenantID);
			preparedStatement.executeUpdate();
		} catch (SQLException e) {
			log.error("Erro em reenviarConvite: " + e.getMessage(), e);
		}

		return "Email de ativação enviado para " + email + ".";
	}

	/** Envia o email de ativacao do pendente do user no tenant. Falso se nao houver pendente ou o envio falhar. */
	private boolean enviarEmailAtivacao(String parmUser, String parmEmail, String parmNome, int parmTenantID) {
		String enderecoEmail = parmEmail;

		// pendente do user no tenant (E2): codigo e, se for ATLETA, o nome do jogador
		String codigoAtivacao = null;
		String perfil = null;
		String nomeJogador = null;
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select ua.code, ua.perfil, j.nome nome_jogador from utilizadores_ativar ua\r\n"
							+ "left join jogador j on j.id=ua.id_jogador\r\n"
							+ "where ua.estado='0' and ua.user=? and ua.tenant_id=?");
			preparedStatement.setString(1, parmUser);
			preparedStatement.setInt(2, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();
			while (rs.next()) {
				codigoAtivacao = rs.getString("code");
				perfil = rs.getString("perfil");
				nomeJogador = rs.getString("nome_jogador");
			}
		} catch (SQLException e) {
			log.error("Erro em reenviarEmailAtivacao: " + e.getMessage(), e);
		}

		if (codigoAtivacao == null) {
			return false;
		}

		if (isPerfilAtleta(perfil)) {
			return enviarEmailConviteAtleta(enderecoEmail, nomeJogador, codigoAtivacao, parmTenantID);
		}

		String linkAtivacao = "https://sm.com.pt/"
				+ tenantProperties.getTenant_id().get(String.valueOf(parmTenantID)).get("name") + "/#/ativeuser?code="
				+ codigoAtivacao;

		String html = "<!DOCTYPE html>" + "<html>"
				+ "<body style='font-family: Arial, sans-serif; background-color:#f4f4f4; padding:20px;'>"
				+ "  <div style='max-width:600px; margin:0 auto; background:#fff; padding:20px; border-radius:8px; box-shadow:0 2px 4px rgba(0,0,0,0.1);'>"
				+ "    <h2 style='color:#333;'>Olá " + HtmlUtils.htmlEscape(parmNome == null ? "" : parmNome) + ",</h2>"
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

		return emailService.enviarEmailHtml(enderecoEmail, "Ative a sua conta - Sports Manager | "
				+ tenantProperties.getTenant_id().get(String.valueOf(parmTenantID)).get("name"), html);
	}

}
