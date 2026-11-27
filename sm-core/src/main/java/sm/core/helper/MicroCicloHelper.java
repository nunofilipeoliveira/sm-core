package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import sm.core.data.FisicoAtletaData;
import sm.core.data.FisicoData;
import sm.core.data.FisicoStaffData;
import sm.core.data.SemanaAtletasData;
import sm.core.data.JogoSemanaData;
import sm.core.data.MicroCicloData;
import sm.core.data.PedidoAusenciaData;
import sm.core.data.PedidoConvocatoriaData;
import sm.core.data.PedidoCopiaTreinoData;
import sm.core.data.PedidoEstadoTreinoData;
import sm.core.data.PedidoMicroCicloData;
import sm.core.data.PresencaData;
import sm.core.data.PresencaJogadorData;
import sm.core.data.PresencaStaffData;
import sm.core.data.ResultadoCopiaTreinoData;
import sm.core.data.SemanaMicroCicloData;
import sm.core.data.TreinoAtletaData;
import sm.core.data.TreinoAusenciaData;
import sm.core.data.TreinoConvocadoData;
import sm.core.data.TreinoData;
import sm.core.data.TreinoDetalheData;
import sm.core.security.AcessoNegadoException;
import sm.core.security.ConflitoException;
import sm.core.security.Identidade;
import sm.core.security.PedidoInvalidoException;

/**
 * Micro Ciclo: plano semanal de treinos, aprovacao e convocatoria (desenho §5.4).
 * Todas as consultas filtram pelo tenant da identidade (D11); todas as escritas
 * correm numa transacao. Horas de Lisboa calculadas em Java (D13).
 */
@Component
public class MicroCicloHelper {

	private static final Logger log = LoggerFactory.getLogger(MicroCicloHelper.class);

	public static final String RASCUNHO = "RASCUNHO";
	public static final String EM_APROVACAO = "EM_APROVACAO";
	public static final String PUBLICADO = "PUBLICADO";
	public static final String PENDENTE = "PENDENTE";
	public static final String APROVADO = "APROVADO";
	public static final String REJEITADO = "REJEITADO";
	public static final String CANCELADO = "CANCELADO";
	public static final String REALIZADO = "REALIZADO";

	private static final Set<String> TIPOS = Set.of("ESCALAO", "FISICO", "OUTRO");

	static final ZoneId LISBOA = ZoneId.of("Europe/Lisbon");
	private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
	private static final DateTimeFormatter DDMM = DateTimeFormatter.ofPattern("dd/MM");
	static final Pattern HORA = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
	private static final String[] DIAS = { "seg", "ter", "qua", "qui", "sex", "sáb", "dom" };

	static final String MSG_VERSAO = "O treino foi alterado entretanto. Recarregue.";
	private static final String MSG_TRANSICAO = "Transição não permitida.";
	static final String MSG_JA_COMECOU = "O treino já começou.";
	private static final String MSG_PRESENCA_PRINCIPAL = "A presença marca-se no treino principal.";

	private static final String SQL_TREINO = "select t.id, t.id_micro_ciclo, t.id_equipa, t.tipo, t.tipo_descricao, "
			+ "t.sub_grupo, t.`data`, t.hora_inicio, t.hora_fim, t.local_treino, t.observacoes, t.estado, "
			+ "t.motivo_estado, t.versao, t.id_presenca, t.data_aprovacao, ua.nome as aprovado_por, "
			+ "t.origem_cancelamento, ee.id_epoca as equipa_epoca, "
			+ "t.id_grupo, g.nome as nome_grupo, t.aprovar_ao_publicar, "
			+ "m.estado as mc_estado, m.data_inicio, m.id_epoca, coalesce(nullif(ee.nome, ''), es.nome) as nome_equipa, "
			// v6 (V11): fisico ligado ao principal; realizado e inicio da sessao derivados do principal
			+ "t.id_treino_principal, tp.id_presenca as presenca_principal, "
			+ "(select min(f.hora_inicio) from treino f where f.id_treino_principal = t.id) as inicio_fisicos "
			+ "from treino t inner join micro_ciclo m on m.id = t.id_micro_ciclo "
			+ "left join treino tp on tp.id = t.id_treino_principal "
			// v5 (W1): left join, para os eventos sem equipa (id_equipa NULL, id_grupo preenchido)
			+ "left join escalao_epoca ee on ee.id = t.id_equipa "
			+ "left join grupo_evento g on g.id = t.id_grupo and g.tenant_id = m.tenant_id "
			+ "left join escalao es on es.id = ee.id_escalao "
			+ "left join utilizadores ua on ua.id = t.id_utilizador_aprovacao "
			+ "where m.tenant_id = ? ";

	private static final String SQL_INSERE_OMISSAO = "insert into treino_convocado(id_treino, id_jogador, origem, "
			+ "estado, data_alteracao, id_utilizador_alteracao) select ?, j.id, 'OMISSAO', '1', now(), ? from jogador j "
			+ "where j.estado = '1' and j.id in (select eej.id_jogador from escalao_epoca_jogador eej "
			+ "where eej.id_escalao_epoca = ?) "
			+ "and not exists (select 1 from treino_convocado tc where tc.id_treino = ? and tc.id_jogador = j.id)";

	/** Indisponibilidades (V16): ausencias ativas de um treino, com a origem; lida por lerAusencia (colunas por nome). */
	private static final String SQL_AUSENCIA = "select ta.id_jogador, j.nome, ta.categoria, ta.motivo, ta.data_indicacao, "
			+ "ta.estado, ta.pendente_confirmacao, ta.id_indisponibilidade, i.data_inicio as indisp_inicio, "
			+ "i.data_fim as indisp_fim from treino_ausencia ta inner join jogador j on j.id = ta.id_jogador "
			+ "left join indisponibilidade i on i.id = ta.id_indisponibilidade where ta.estado = 'ATIVA' and ta.id_treino = ?";

	private final DBUtils dbUtils;

	@Autowired
	private AutorizacaoHelper autorizacaoHelper;

	@Autowired
	private NotificacaoHelper notificacaoHelper;

	@Autowired
	private PresencaHelper presencaHelper;

	public MicroCicloHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;
	}

	/** Linha de treino com os dados do micro ciclo, para as verificacoes. */
	static final class Treino {
		TreinoData d;
		String estadoBD;
		String mcEstado;
		int dataInicio;
		int idEpoca;
		/** v4 (V9): MICRO_CICLO, TREINO ou null (nao cancelado ou anterior a V9). */
		String origemCancelamento;
		/** Epoca da equipa (escalao_epoca.id_epoca). */
		int equipaEpoca;
		/** v6: principal de um fisico ligado (null = treino autonomo). */
		Integer idPrincipal;
		/** v6: hora de inicio mais cedo dos fisicos ligados a este treino (null = sem fisicos). */
		String inicioFisicos;
		/** v6: o principal deste fisico ligado ja tem presenca. */
		boolean presencaPrincipal;

		boolean realizado() {
			return APROVADO.equals(estadoBD) && (d.getIdPresenca() != null || presencaPrincipal);
		}

		/** v6 (X1): fisico ligado a um treino principal. */
		boolean ligado() {
			return idPrincipal != null;
		}

		/** v6 (X7, R176): o inicio mais cedo da sessao (HH:MM compara-se como texto). */
		String inicioSessao() {
			return inicioFisicos != null && inicioFisicos.compareTo(d.getHoraInicio()) < 0 ? inicioFisicos
					: d.getHoraInicio();
		}

		/** v5 (W1): evento do clube (grupo, sem equipa nem convocatoria). */
		boolean evento() {
			return d.getIdGrupo() > 0;
		}

		/** D4: depois de aprovado, a convocatoria e a "fotografia" gravada. */
		boolean fotografado() {
			return APROVADO.equals(estadoBD) || (CANCELADO.equals(estadoBD) && d.getDataAprovacao() != null);
		}
	}

	@FunctionalInterface
	interface Trabalho<T> {
		T executa(Connection conn) throws SQLException;
	}

	// ==================== utilitarios ====================

	<T> T emTransacao(String parmMetodo, Trabalho<T> parmTrabalho) {
		Connection conn = null;
		try {
			conn = dbUtils.getConnection();
			conn.setAutoCommit(false);
			T resultado = parmTrabalho.executa(conn);
			conn.commit();
			return resultado;
		} catch (SQLException e) {
			rollback(conn);
			log.error("MicroCicloHelper | " + parmMetodo + " | Erro: " + e.getMessage(), e);
			throw new IllegalStateException("Erro de base de dados.", e);
		} catch (RuntimeException e) {
			rollback(conn);
			throw e;
		} finally {
			if (conn != null) {
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					// ignore
				}
				dbUtils.closeConnection(conn);
			}
		}
	}

	/** Indisponibilidades (Z11): o mesmo que emTransacao, mas acaba sempre em rollback (pre-visualizacao exata). */
	<T> T emSimulacao(String parmMetodo, Trabalho<T> parmTrabalho) {
		Connection conn = null;
		try {
			conn = dbUtils.getConnection();
			conn.setAutoCommit(false);
			return parmTrabalho.executa(conn);
		} catch (SQLException e) {
			log.error("MicroCicloHelper | " + parmMetodo + " | Erro: " + e.getMessage(), e);
			throw new IllegalStateException("Erro de base de dados.", e);
		} finally {
			if (conn != null) {
				rollback(conn);
				try {
					conn.setAutoCommit(true);
				} catch (SQLException e) {
					// ignore
				}
				dbUtils.closeConnection(conn);
			}
		}
	}

	<T> T leitura(String parmMetodo, Trabalho<T> parmTrabalho) {
		try (Connection conn = dbUtils.getConnection()) {
			return parmTrabalho.executa(conn);
		} catch (SQLException e) {
			log.error("MicroCicloHelper | " + parmMetodo + " | Erro: " + e.getMessage(), e);
			throw new IllegalStateException("Erro de base de dados.", e);
		}
	}

	private static void rollback(Connection conn) {
		if (conn != null) {
			try {
				conn.rollback();
			} catch (SQLException e) {
				// ignore
			}
		}
	}

	static LocalDateTime agora() {
		return LocalDateTime.now(LISBOA);
	}

	static int paraInt(LocalDate parmData) {
		return Integer.parseInt(parmData.format(YMD));
	}

	static LocalDate paraData(int parmData) {
		try {
			return LocalDate.parse(String.valueOf(parmData), YMD);
		} catch (DateTimeParseException e) {
			throw new PedidoInvalidoException("Data inválida: " + parmData);
		}
	}

	static int hoje() {
		return paraInt(agora().toLocalDate());
	}

	static int segundaDe(int parmData) {
		return paraInt(paraData(parmData).with(DayOfWeek.MONDAY));
	}

	static int somaDias(int parmData, int parmDias) {
		return paraInt(paraData(parmData).plusDays(parmDias));
	}

	static boolean inicioPassou(int parmData, String parmHora) {
		return !agora().isBefore(LocalDateTime.of(paraData(parmData), LocalTime.parse(parmHora)));
	}

	static boolean semanaTerminou(int parmSegunda) {
		return hoje() > somaDias(parmSegunda, 6);
	}

	static int minutos(String parmHora) {
		return Integer.parseInt(parmHora.substring(0, 2)) * 60 + Integer.parseInt(parmHora.substring(3, 5));
	}

	/** D23: sem hora de fim conta como 60 minutos (so para avisos). */
	static int minutosFim(TreinoData parmTreino) {
		return parmTreino.getHoraFim() != null ? minutos(parmTreino.getHoraFim()) : minutos(parmTreino.getHoraInicio()) + 60;
	}

	static boolean sobrepostos(TreinoData a, TreinoData b) {
		return a.getData() == b.getData() && minutos(a.getHoraInicio()) < minutosFim(b)
				&& minutos(b.getHoraInicio()) < minutosFim(a);
	}

	static String ddmm(int parmData) {
		return paraData(parmData).format(DDMM);
	}

	static String dia(int parmData) {
		return DIAS[paraData(parmData).getDayOfWeek().getValue() - 1] + " " + ddmm(parmData);
	}

	static String intervalo(TreinoData parmTreino) {
		return parmTreino.getHoraFim() != null ? parmTreino.getHoraInicio() + "–" + parmTreino.getHoraFim()
				: "a partir das " + parmTreino.getHoraInicio();
	}

	static String limpa(String parmValor, int parmMax, String parmCampo) {
		if (parmValor == null || parmValor.trim().isEmpty()) {
			return null;
		}
		String valor = parmValor.trim();
		if (valor.length() > parmMax) {
			throw new PedidoInvalidoException(parmCampo + " tem no máximo " + parmMax + " caracteres.");
		}
		return valor;
	}

	private static Integer getInteger(ResultSet rs, String parmColuna) throws SQLException {
		int valor = rs.getInt(parmColuna);
		return rs.wasNull() ? null : valor;
	}

	static void historico(Connection conn, int parmIdMicroCiclo, Integer parmIdTreino, int parmIdUtilizador,
			String parmTexto) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("insert into micro_ciclo_historico(id_micro_ciclo, "
				+ "id_treino, `data`, id_utilizador, alteracao) values (?, ?, now(), ?, ?)");
		preparedStatement.setInt(1, parmIdMicroCiclo);
		if (parmIdTreino == null) {
			preparedStatement.setNull(2, java.sql.Types.INTEGER);
		} else {
			preparedStatement.setInt(2, parmIdTreino);
		}
		preparedStatement.setInt(3, parmIdUtilizador);
		preparedStatement.setString(4, parmTexto.length() > 500 ? parmTexto.substring(0, 500) : parmTexto);
		preparedStatement.executeUpdate();
	}

	static Integer epocaAtiva(Connection conn, int parmTenantId) throws SQLException {
		PreparedStatement preparedStatement = conn
				.prepareStatement("select id from epoca where estado = '1' and tenant_id = ? LIMIT 1");
		preparedStatement.setInt(1, parmTenantId);
		ResultSet rs = preparedStatement.executeQuery();
		return rs.next() ? rs.getInt(1) : null;
	}

	private static MicroCicloData lerMicroCiclo(ResultSet rs) throws SQLException {
		MicroCicloData mc = new MicroCicloData();
		mc.setId(rs.getInt("id"));
		mc.setDataInicio(rs.getInt("data_inicio"));
		mc.setEstado(rs.getString("estado"));
		mc.setMotivoCancelamento(rs.getString("motivo_cancelamento"));
		mc.setIdEpoca(rs.getInt("id_epoca"));
		mc.setEstadoAntesCancelamento(rs.getString("estado_antes_cancelamento")); // v9 (V15)
		return mc;
	}

	/** @throws AcessoNegadoException micro ciclo inexistente ou de outro tenant */
	static MicroCicloData carregaMicroCiclo(Connection conn, int parmId, int parmTenantId) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select id, data_inicio, estado, "
				+ "motivo_cancelamento, id_epoca, estado_antes_cancelamento from micro_ciclo where id = ? and tenant_id = ?");
		preparedStatement.setInt(1, parmId);
		preparedStatement.setInt(2, parmTenantId);
		ResultSet rs = preparedStatement.executeQuery();
		if (!rs.next()) {
			throw new AcessoNegadoException();
		}
		return lerMicroCiclo(rs);
	}

	static MicroCicloData microCicloDaSemana(Connection conn, int parmTenantId, int parmSegunda)
			throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select id, data_inicio, estado, "
				+ "motivo_cancelamento, id_epoca, estado_antes_cancelamento from micro_ciclo where tenant_id = ? and data_inicio = ?");
		preparedStatement.setInt(1, parmTenantId);
		preparedStatement.setInt(2, parmSegunda);
		ResultSet rs = preparedStatement.executeQuery();
		return rs.next() ? lerMicroCiclo(rs) : null;
	}

	private static Treino lerTreino(ResultSet rs) throws SQLException {
		Treino t = new Treino();
		TreinoData d = new TreinoData();
		d.setId(rs.getInt("id"));
		d.setIdMicroCiclo(rs.getInt("id_micro_ciclo"));
		d.setIdEquipa(rs.getInt("id_equipa"));
		d.setNomeEquipa(rs.getString("nome_equipa"));
		d.setTipo(rs.getString("tipo"));
		d.setTipoDescricao(rs.getString("tipo_descricao"));
		d.setSubGrupo(rs.getString("sub_grupo"));
		d.setData(rs.getInt("data"));
		d.setHoraInicio(rs.getString("hora_inicio"));
		d.setHoraFim(rs.getString("hora_fim"));
		d.setLocal(rs.getString("local_treino"));
		d.setObservacoes(rs.getString("observacoes"));
		d.setMotivoEstado(rs.getString("motivo_estado"));
		d.setVersao(rs.getInt("versao"));
		d.setIdPresenca(getInteger(rs, "id_presenca"));
		d.setDataAprovacao(rs.getTimestamp("data_aprovacao"));
		d.setAprovadoPor(rs.getString("aprovado_por"));
		t.d = d;
		t.estadoBD = rs.getString("estado");
		t.mcEstado = rs.getString("mc_estado");
		t.dataInicio = rs.getInt("data_inicio");
		t.idEpoca = rs.getInt("id_epoca");
		t.origemCancelamento = rs.getString("origem_cancelamento");
		t.equipaEpoca = rs.getInt("equipa_epoca");
		d.setIdGrupo(rs.getInt("id_grupo")); // 0 nos treinos; id_equipa NULL da 0 nos eventos
		d.setNomeGrupo(rs.getString("nome_grupo"));
		d.setAprovarAoPublicar("1".equals(rs.getString("aprovar_ao_publicar")));
		t.idPrincipal = getInteger(rs, "id_treino_principal");
		t.presencaPrincipal = getInteger(rs, "presenca_principal") != null;
		t.inicioFisicos = rs.getString("inicio_fisicos");
		d.setIdTreinoPrincipal(t.idPrincipal);
		d.setInicioSessao(t.inicioSessao());
		if ("ESCALAO".equals(d.getTipo()) && !t.evento()) {
			d.setFisicos(new ArrayList<>()); // v6: so os ESCALAO de equipa tem fisicos (null nos outros)
		}
		d.setEstado(t.realizado() ? REALIZADO : t.estadoBD);
		return t;
	}

	/** v6 (X6): ANTES, DURANTE (sem fim conta se comecar antes do fim do principal) ou DEPOIS. */
	static String posicaoFisico(TreinoData parmPrincipal, String parmInicio, String parmFim) {
		int inicio = minutos(parmInicio);
		int fimPrincipal = minutosFim(parmPrincipal);
		if (inicio < minutos(parmPrincipal.getHoraInicio())) {
			return "ANTES";
		}
		if (inicio < fimPrincipal && (parmFim == null || minutos(parmFim) <= fimPrincipal)) {
			return "DURANTE";
		}
		return "DEPOIS";
	}

	static String intervalo(FisicoData parmFisico) {
		return parmFisico.getHoraFim() != null ? parmFisico.getHoraInicio() + "–" + parmFisico.getHoraFim()
				: "a partir das " + parmFisico.getHoraInicio();
	}

	/** v6: " (inclui físico 17:00–18:00, ...)" ou "" (texto do historico). */
	static String incluiFisicos(List<? extends FisicoData> parmFisicos) {
		if (parmFisicos == null || parmFisicos.isEmpty()) {
			return "";
		}
		List<String> intervalos = new ArrayList<>();
		for (FisicoData f : parmFisicos) {
			intervalos.add(intervalo(f));
		}
		return " (inclui físico " + String.join(", ", intervalos) + ")";
	}

	/** v6: fisicos ligados aos principais pedidos (do tenant), por hora, com a posicao. */
	static Map<Integer, List<FisicoStaffData>> fisicosDe(Connection conn, int parmTenantId,
			java.util.Collection<Integer> parmIdsPrincipais) throws SQLException {
		Map<Integer, List<FisicoStaffData>> mapa = new HashMap<>();
		if (parmIdsPrincipais == null || parmIdsPrincipais.isEmpty()) {
			return mapa;
		}
		String marcas = String.join(", ", java.util.Collections.nCopies(parmIdsPrincipais.size(), "?"));
		try (PreparedStatement preparedStatement = conn.prepareStatement("select f.id, f.id_treino_principal, "
				+ "f.hora_inicio, f.hora_fim, f.local_treino, f.sub_grupo, p.hora_inicio as p_inicio, p.hora_fim as p_fim "
				+ "from treino f inner join treino p on p.id = f.id_treino_principal "
				+ "inner join micro_ciclo m on m.id = p.id_micro_ciclo where m.tenant_id = ? "
				+ "and f.id_treino_principal in (" + marcas + ") order by f.hora_inicio, f.id")) {
			int i = 1;
			preparedStatement.setInt(i++, parmTenantId);
			for (Integer id : parmIdsPrincipais) {
				preparedStatement.setInt(i++, id);
			}
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					TreinoData principal = new TreinoData();
					principal.setHoraInicio(rs.getString("p_inicio"));
					principal.setHoraFim(rs.getString("p_fim"));
					FisicoStaffData f = new FisicoStaffData();
					f.setId(rs.getInt("id"));
					f.setHoraInicio(rs.getString("hora_inicio"));
					f.setHoraFim(rs.getString("hora_fim"));
					f.setLocal(rs.getString("local_treino"));
					f.setSubGrupo(rs.getString("sub_grupo"));
					f.setPosicao(posicaoFisico(principal, f.getHoraInicio(), f.getHoraFim()));
					mapa.computeIfAbsent(rs.getInt("id_treino_principal"), k -> new ArrayList<>()).add(f);
				}
			}
		}
		return mapa;
	}

	/** v6: fisicos de um so principal (lista vazia se nao tiver). */
	static List<FisicoStaffData> fisicosDe(Connection conn, int parmTenantId, int parmIdPrincipal) throws SQLException {
		return fisicosDe(conn, parmTenantId, List.of(parmIdPrincipal)).getOrDefault(parmIdPrincipal, new ArrayList<>());
	}

	/**
	 * v6 (X2): copia do principal para os fisicos ligados o estado, a aprovacao, a equipa e a data. Por um
	 * principal (parmIdPrincipal) ou por todos os de um micro ciclo (parmIdMicroCiclo); o outro a null.
	 */
	static void propagaSessao(Connection conn, int parmTenantId, Integer parmIdPrincipal, Integer parmIdMicroCiclo)
			throws SQLException {
		try (PreparedStatement preparedStatement = conn.prepareStatement("update treino f "
				+ "inner join treino p on p.id = f.id_treino_principal inner join micro_ciclo m on m.id = p.id_micro_ciclo "
				+ "set f.estado = p.estado, f.motivo_estado = p.motivo_estado, f.origem_cancelamento = p.origem_cancelamento, "
				+ "f.data_aprovacao = p.data_aprovacao, f.id_utilizador_aprovacao = p.id_utilizador_aprovacao, "
				+ "f.id_equipa = p.id_equipa, f.`data` = p.`data`, f.versao = f.versao + 1 "
				+ "where m.tenant_id = ? and (p.id = ? or p.id_micro_ciclo = ?)")) {
			preparedStatement.setInt(1, parmTenantId);
			if (parmIdPrincipal == null) {
				preparedStatement.setNull(2, java.sql.Types.INTEGER);
			} else {
				preparedStatement.setInt(2, parmIdPrincipal);
			}
			if (parmIdMicroCiclo == null) {
				preparedStatement.setNull(3, java.sql.Types.INTEGER);
			} else {
				preparedStatement.setInt(3, parmIdMicroCiclo);
			}
			preparedStatement.executeUpdate();
		}
	}

	/** v6: insere um fisico ligado (sem convocatoria propria); o estado e acertado por propagaSessao. */
	private static int insereFisico(Connection conn, int parmIdMicroCiclo, int parmIdPrincipal, int parmIdEquipa,
			int parmData, FisicoData parmFisico, String parmEstado, int parmIdUtilizador) throws SQLException {
		TreinoData f = new TreinoData();
		f.setIdEquipa(parmIdEquipa);
		f.setTipo("FISICO");
		f.setSubGrupo(parmFisico.getSubGrupo());
		f.setData(parmData);
		f.setHoraInicio(parmFisico.getHoraInicio());
		f.setHoraFim(parmFisico.getHoraFim());
		f.setLocal(parmFisico.getLocal());
		int id = insereTreino(conn, parmIdMicroCiclo, f, parmEstado, parmIdUtilizador);
		try (PreparedStatement preparedStatement = conn
				.prepareStatement("update treino set id_treino_principal = ? where id = ?")) {
			preparedStatement.setInt(1, parmIdPrincipal);
			preparedStatement.setInt(2, id);
			preparedStatement.executeUpdate();
		}
		return id;
	}

	/** @throws AcessoNegadoException treino inexistente ou de outro tenant */
	Treino carregaTreino(Connection conn, int parmIdTreino, int parmTenantId) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement(SQL_TREINO + "and t.id = ?");
		preparedStatement.setInt(1, parmTenantId);
		preparedStatement.setInt(2, parmIdTreino);
		ResultSet rs = preparedStatement.executeQuery();
		if (!rs.next()) {
			throw new AcessoNegadoException();
		}
		return lerTreino(rs);
	}

	private static String nomeEquipa(Connection conn, int parmIdEquipa) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select coalesce(nullif(ee.nome, ''), es.nome) "
				+ "from escalao_epoca ee left join escalao es on es.id = ee.id_escalao where ee.id = ?");
		preparedStatement.setInt(1, parmIdEquipa);
		ResultSet rs = preparedStatement.executeQuery();
		return rs.next() ? rs.getString(1) : "";
	}

	/** Equipas da conta na epoca ativa (utilizadores_escalao), para as listas. */
	static Set<Integer> equipasDoUtilizador(Connection conn, int parmIdUtilizador) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select ue.id_escalao_epoca "
				+ "from utilizadores_escalao ue inner join escalao_epoca ee on ee.id = ue.id_escalao_epoca "
				+ "inner join epoca e on e.id = ee.id_epoca where e.estado = '1' and ue.id_utilizador = ?");
		preparedStatement.setInt(1, parmIdUtilizador);
		ResultSet rs = preparedStatement.executeQuery();
		Set<Integer> equipas = new HashSet<>();
		while (rs.next()) {
			equipas.add(rs.getInt(1));
		}
		return equipas;
	}

	/** Indisponibilidades (Q13): equipas do atleta na epoca ativa do tenant (escalao_epoca_jogador). */
	static Set<Integer> equipasDoAtleta(Connection conn, int parmTenantId, int parmIdJogador) throws SQLException {
		Set<Integer> equipas = new HashSet<>();
		try (PreparedStatement preparedStatement = conn.prepareStatement("select eej.id_escalao_epoca "
				+ "from escalao_epoca_jogador eej inner join escalao_epoca ee on ee.id = eej.id_escalao_epoca "
				+ "inner join epoca e on e.id = ee.id_epoca where e.estado = '1' and e.tenant_id = ? and eej.id_jogador = ?")) {
			preparedStatement.setInt(1, parmTenantId);
			preparedStatement.setInt(2, parmIdJogador);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					equipas.add(rs.getInt(1));
				}
			}
		}
		return equipas;
	}

	/** D10: ADMIN, ou TREINADOR com a equipa (sempre, em REPORT e ENFORCE). */
	private static boolean podeAgirNaEquipa(Identidade parmIdentidade, int parmIdEquipa, Set<Integer> parmEquipas) {
		return parmIdentidade.isAdmin() || (parmIdentidade.isTreinador() && parmEquipas.contains(parmIdEquipa));
	}

	private static void preencheDerivados(Treino t, Identidade parmIdentidade, Set<Integer> parmEquipas) {
		boolean passou = inicioPassou(t.d.getData(), t.inicioSessao());
		boolean pendente = PENDENTE.equals(t.estadoBD);
		t.d.setNaoAprovadoATempo(pendente && passou);
		t.d.setPodeAprovar(pendente && !passou && podeAgirNaEquipa(parmIdentidade, t.d.getIdEquipa(), parmEquipas));
		// v3 (V5): com que qualidade o utilizador aprovaria agora
		t.d.setQualidadeSeAprovar(t.d.isPodeAprovar() ? qualidade(parmIdentidade, t.d.getIdEquipa(), parmEquipas) : null);
	}

	static final String QUALIDADE_TREINADOR = "TREINADOR";
	static final String QUALIDADE_ADMINISTRADOR = "ADMINISTRADOR";
	private static final String TEXTO_TREINADOR = "como treinador da equipa";
	private static final String TEXTO_ADMINISTRADOR = "como administrador";
	/** v3 (V1): a qualidade fica so no texto do historico; linhas antigas nao tem o marcador. */
	private static final Pattern DECISAO = Pattern
			.compile("^(Aprovado|Rejeitado) \\((como treinador da equipa|como administrador)\\)");

	/** v3 (V3): TREINADOR se a conta tem a equipa (TREINADOR ou ADMIN), ADMINISTRADOR se e ADMIN sem a equipa. */
	private static String qualidade(Identidade parmIdentidade, int parmIdEquipa, Set<Integer> parmEquipas) {
		if (parmEquipas.contains(parmIdEquipa)) {
			return QUALIDADE_TREINADOR;
		}
		return parmIdentidade.isAdmin() ? QUALIDADE_ADMINISTRADOR : null;
	}

	private static String textoQualidade(String parmQualidade) {
		return QUALIDADE_TREINADOR.equals(parmQualidade) ? TEXTO_TREINADOR : TEXTO_ADMINISTRADOR;
	}

	/** Q23: ADMIN; TREINADOR so da equipa; UTILIZADOR (e perfil nulo) sim. */
	static boolean podeMarcarPresenca(Identidade parmIdentidade, int parmIdEquipa, Set<Integer> parmEquipas) {
		if (parmIdentidade.isAtleta()) {
			return false;
		}
		return !parmIdentidade.isTreinador() || parmEquipas.contains(parmIdEquipa);
	}

	/** D4: convocados efetivos do treino (sem os retirados nem os inativos). */
	List<TreinoConvocadoData> convocatoriaEfetiva(Connection conn, Treino t) throws SQLException {
		if (t.evento()) {
			return new ArrayList<>(); // v5 (R128): eventos sem convocatoria
		}
		String sql = "select j.id, j.nome, tc.origem from treino_convocado tc inner join jogador j on j.id = tc.id_jogador "
				+ "where tc.id_treino = ? and tc.estado = '1' and j.estado = '1' and (tc.origem = 'ACRESCENTADO' "
				+ "or exists (select 1 from escalao_epoca_jogador eej where eej.id_escalao_epoca = ? "
				+ "and eej.id_jogador = tc.id_jogador))";
		if (!t.fotografado()) {
			sql += " union select j.id, j.nome, 'OMISSAO' from jogador j where j.estado = '1' and j.id in "
					+ "(select eej.id_jogador from escalao_epoca_jogador eej where eej.id_escalao_epoca = ?) "
					+ "and not exists (select 1 from treino_convocado tc where tc.id_treino = ? and tc.id_jogador = j.id)";
		}
		PreparedStatement preparedStatement = conn.prepareStatement("select * from (" + sql + ") x order by 2");
		preparedStatement.setInt(1, t.d.getId());
		preparedStatement.setInt(2, t.d.getIdEquipa());
		if (!t.fotografado()) {
			preparedStatement.setInt(3, t.d.getIdEquipa());
			preparedStatement.setInt(4, t.d.getId());
		}
		ResultSet rs = preparedStatement.executeQuery();
		List<TreinoConvocadoData> lista = new ArrayList<>();
		while (rs.next()) {
			TreinoConvocadoData c = new TreinoConvocadoData();
			c.setIdJogador(rs.getInt(1));
			c.setNome(rs.getString(2));
			c.setOrigem(rs.getString(3));
			c.setConvocado(true);
			lista.add(c);
		}
		return lista;
	}

	private static void insereConvocatoriaOmissao(Connection conn, int parmIdTreino, int parmIdEquipa,
			int parmIdUtilizador) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement(SQL_INSERE_OMISSAO);
		preparedStatement.setInt(1, parmIdTreino);
		preparedStatement.setInt(2, parmIdUtilizador);
		preparedStatement.setInt(3, parmIdEquipa);
		preparedStatement.setInt(4, parmIdTreino);
		preparedStatement.executeUpdate();
	}

	/**
	 * v7: jogadores com ausencia ativa, por treino do micro ciclo (a contagem e o tamanho do conjunto).
	 * Indisponibilidades: os que vem de uma indisponibilidade (id_indisponibilidade) vao tambem para parmIndisponiveis.
	 */
	private static Map<Integer, Set<Integer>> ausenciasAtivasPorTreino(Connection conn, int parmIdMicroCiclo,
			Map<Integer, Set<Integer>> parmIndisponiveis) throws SQLException {
		Map<Integer, Set<Integer>> ausencias = new HashMap<>();
		try (PreparedStatement preparedStatement = conn.prepareStatement("select ta.id_treino, ta.id_jogador, "
				+ "ta.id_indisponibilidade from treino_ausencia ta inner join treino t on t.id = ta.id_treino "
				+ "where t.id_micro_ciclo = ? and ta.estado = 'ATIVA'")) {
			preparedStatement.setInt(1, parmIdMicroCiclo);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					ausencias.computeIfAbsent(rs.getInt(1), k -> new HashSet<>()).add(rs.getInt(2));
					if (getInteger(rs, "id_indisponibilidade") != null) {
						parmIndisponiveis.computeIfAbsent(rs.getInt(1), k -> new HashSet<>()).add(rs.getInt(2));
					}
				}
			}
		}
		return ausencias;
	}

	/** Indisponibilidades: jogadores com ausencia ativa vinda de uma indisponibilidade, num treino. */
	private static Set<Integer> indisponiveisNoTreino(Connection conn, int parmIdTreino) throws SQLException {
		Set<Integer> ids = new HashSet<>();
		try (PreparedStatement preparedStatement = conn.prepareStatement("select id_jogador from treino_ausencia "
				+ "where id_treino = ? and estado = 'ATIVA' and id_indisponibilidade is not null")) {
			preparedStatement.setInt(1, parmIdTreino);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getInt(1));
				}
			}
		}
		return ids;
	}

	/** v7 (X1): quem nao faz cada fisico (treino_fisico_excluido), dos fisicos pedidos do tenant. */
	static Map<Integer, Set<Integer>> excluidosDe(Connection conn, int parmTenantId,
			java.util.Collection<Integer> parmIdsFisicos) throws SQLException {
		Map<Integer, Set<Integer>> mapa = new HashMap<>();
		if (parmIdsFisicos == null || parmIdsFisicos.isEmpty()) {
			return mapa;
		}
		String marcas = String.join(", ", java.util.Collections.nCopies(parmIdsFisicos.size(), "?"));
		try (PreparedStatement preparedStatement = conn.prepareStatement("select x.id_treino, x.id_jogador "
				+ "from treino_fisico_excluido x inner join treino f on f.id = x.id_treino "
				+ "inner join micro_ciclo m on m.id = f.id_micro_ciclo where m.tenant_id = ? "
				+ "and x.id_treino in (" + marcas + ")")) {
			int i = 1;
			preparedStatement.setInt(i++, parmTenantId);
			for (Integer id : parmIdsFisicos) {
				preparedStatement.setInt(i++, id);
			}
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					mapa.computeIfAbsent(rs.getInt(1), k -> new HashSet<>()).add(rs.getInt(2));
				}
			}
		}
		return mapa;
	}

	// ==================== v8: confirmacao "Vou" (Y1, Y2) ====================

	static final String VOU = "VOU";
	static final String NAO_VOU = "NAO_VOU";
	static final String SEM_RESPOSTA = "SEM_RESPOSTA";
	static final String CADUCADA = "CADUCADA";
	static final String SEM_CONTA = "SEM_CONTA";

	/** Linha de treino_confirmacao. */
	static final class Confirmacao {
		String sessao;
		String avisada;
		Timestamp data;
	}

	private static String parteSessao(String parmInicio, String parmFim, String parmLocal) {
		return parmInicio + "|" + (parmFim == null ? "" : parmFim) + "|"
				+ (parmLocal == null ? "" : parmLocal.trim().toLowerCase());
	}

	/**
	 * Y2: texto canonico da sessao do atleta: "data|inicio|fim|local" do principal e, por inicio, ";inicio|fim|local"
	 * de cada fisico que faz. Ex.: "20261118|18:00|19:30|pavilhão;17:00|18:00|ginásio".
	 */
	static String sessaoDoAtleta(TreinoData parmPrincipal, List<? extends FisicoData> parmFisicosDoAtleta) {
		StringBuilder sessao = new StringBuilder(parmPrincipal.getData() + "|"
				+ parteSessao(parmPrincipal.getHoraInicio(), parmPrincipal.getHoraFim(), parmPrincipal.getLocal()));
		List<FisicoData> fisicos = new ArrayList<>(parmFisicosDoAtleta);
		fisicos.sort(java.util.Comparator.comparing(FisicoData::getHoraInicio));
		for (FisicoData f : fisicos) {
			sessao.append(';').append(parteSessao(f.getHoraInicio(), f.getHoraFim(), f.getLocal()));
		}
		return sessao.toString();
	}

	/**
	 * Y2, R227: caducou se a parte do principal mudou ou se um fisico que o atleta faz agora nao estava na confirmada.
	 * Sair de um fisico nao faz caducar. Ex.: caducou("d|18:00|19:30|p;17:00|18:00|g", "d|18:00|19:30|p") = false.
	 */
	static boolean caducou(String parmConfirmada, String parmAtual) {
		String[] confirmada = parmConfirmada.split(";", -1);
		String[] atual = parmAtual.split(";", -1);
		if (!confirmada[0].equals(atual[0])) {
			return true;
		}
		Set<String> fisicosConfirmados = new HashSet<>(java.util.Arrays.asList(confirmada).subList(1, confirmada.length));
		for (int i = 1; i < atual.length; i++) {
			if (!fisicosConfirmados.contains(atual[i])) {
				return true;
			}
		}
		return false;
	}

	/** Confirmacoes das sessoes pedidas (do tenant): sessao -> jogador -> confirmacao. */
	static Map<Integer, Map<Integer, Confirmacao>> confirmacoesDe(Connection conn, int parmTenantId,
			java.util.Collection<Integer> parmIdsSessoes) throws SQLException {
		Map<Integer, Map<Integer, Confirmacao>> mapa = new HashMap<>();
		if (parmIdsSessoes == null || parmIdsSessoes.isEmpty()) {
			return mapa;
		}
		String marcas = String.join(", ", java.util.Collections.nCopies(parmIdsSessoes.size(), "?"));
		try (PreparedStatement preparedStatement = conn.prepareStatement("select c.id_treino, c.id_jogador, "
				+ "c.sessao_confirmada, c.sessao_avisada, c.data_confirmacao from treino_confirmacao c "
				+ "inner join treino t on t.id = c.id_treino inner join micro_ciclo m on m.id = t.id_micro_ciclo "
				+ "where m.tenant_id = ? and c.id_treino in (" + marcas + ")")) {
			int i = 1;
			preparedStatement.setInt(i++, parmTenantId);
			for (Integer id : parmIdsSessoes) {
				preparedStatement.setInt(i++, id);
			}
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					Confirmacao c = new Confirmacao();
					c.sessao = rs.getString(3);
					c.avisada = rs.getString(4);
					c.data = rs.getTimestamp(5);
					mapa.computeIfAbsent(rs.getInt(1), k -> new HashMap<>()).put(rs.getInt(2), c);
				}
			}
		}
		return mapa;
	}

	/** §1.1: jogadores com conta ATLETA ativa no tenant (a regra de notificaAtleta). */
	static Set<Integer> comConta(Connection conn, int parmTenantId, java.util.Collection<Integer> parmIdsJogadores)
			throws SQLException {
		return new HashSet<>(contasAtleta(conn, parmTenantId, parmIdsJogadores).keySet());
	}

	/** v9: as contas ATLETA ativas de cada jogador (a mesma regra), numa consulta: jogador -> ids de utilizador. */
	static Map<Integer, List<Integer>> contasAtleta(Connection conn, int parmTenantId,
			java.util.Collection<Integer> parmIdsJogadores) throws SQLException {
		Map<Integer, List<Integer>> contas = new HashMap<>();
		if (parmIdsJogadores == null || parmIdsJogadores.isEmpty()) {
			return contas;
		}
		String marcas = String.join(", ", java.util.Collections.nCopies(parmIdsJogadores.size(), "?"));
		try (PreparedStatement preparedStatement = conn.prepareStatement("select u.id_jogador, u.id from utilizadores u "
				+ "where u.tenant_id = ? and u.estado = '1' and upper(u.perfil) = 'ATLETA' and u.id_jogador in (" + marcas
				+ ")")) {
			int i = 1;
			preparedStatement.setInt(i++, parmTenantId);
			for (Integer id : parmIdsJogadores) {
				preparedStatement.setInt(i++, id);
			}
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					contas.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(rs.getInt(2));
				}
			}
		}
		return contas;
	}

	/** Ordem: ausencia ativa -> NAO_VOU; sem conta -> SEM_CONTA; caducada -> CADUCADA; confirmacao -> VOU; SEM_RESPOSTA. */
	static String estadoResposta(boolean parmAusente, boolean parmComConta, Confirmacao parmConfirmacao,
			String parmSessaoAtual) {
		if (parmAusente) {
			return NAO_VOU;
		}
		if (!parmComConta) {
			return SEM_CONTA;
		}
		if (parmConfirmacao != null) {
			return caducou(parmConfirmacao.sessao, parmSessaoAtual) ? CADUCADA : VOU;
		}
		return SEM_RESPOSTA;
	}

	/** v8 (Y5): apaga as confirmacoes de uma sessao (todas, ou so a do jogador). */
	static void apagaConfirmacoes(Connection conn, int parmIdTreino, Integer parmIdJogador) throws SQLException {
		try (PreparedStatement preparedStatement = conn.prepareStatement("delete from treino_confirmacao where id_treino = ?"
				+ (parmIdJogador != null ? " and id_jogador = ?" : ""))) {
			preparedStatement.setInt(1, parmIdTreino);
			if (parmIdJogador != null) {
				preparedStatement.setInt(2, parmIdJogador);
			}
			preparedStatement.executeUpdate();
		}
	}

	/** v8: grava (ou renova) o "Vou" com a sessao que o atleta viu; limpa a sessao_avisada. */
	private static void gravaConfirmacao(Connection conn, int parmIdTreino, int parmIdJogador, String parmSessao,
			int parmIdUtilizador) throws SQLException {
		try (PreparedStatement preparedStatement = conn.prepareStatement("insert into treino_confirmacao(id_treino, "
				+ "id_jogador, sessao_confirmada, sessao_avisada, data_confirmacao, id_utilizador) values (?, ?, ?, null, now(), ?) "
				+ "on duplicate key update sessao_confirmada = values(sessao_confirmada), sessao_avisada = null, "
				+ "data_confirmacao = now(), id_utilizador = values(id_utilizador)")) {
			preparedStatement.setInt(1, parmIdTreino);
			preparedStatement.setInt(2, parmIdJogador);
			preparedStatement.setString(3, parmSessao);
			preparedStatement.setInt(4, parmIdUtilizador);
			preparedStatement.executeUpdate();
		}
	}

	/** v8: a sessao atual do jogador: o principal e os fisicos dele de que nao esta excluido. */
	static String sessaoDoJogador(TreinoData parmPrincipal, List<? extends FisicoData> parmFisicos,
			Map<Integer, Set<Integer>> parmExcluidos, int parmIdJogador) {
		List<FisicoData> meus = new ArrayList<>();
		if (parmFisicos != null) {
			for (FisicoData f : parmFisicos) {
				if (!parmExcluidos.getOrDefault(f.getId(), Set.of()).contains(parmIdJogador)) {
					meus.add(f);
				}
			}
		}
		return sessaoDoAtleta(parmPrincipal, meus);
	}

	/** v8: um fisico ligado (linha de treino) como FisicoData, para a sessao do atleta. */
	private static FisicoData comoFisico(TreinoData parmFisico) {
		FisicoData f = new FisicoData();
		f.setId(parmFisico.getId());
		f.setHoraInicio(parmFisico.getHoraInicio());
		f.setHoraFim(parmFisico.getHoraFim());
		f.setLocal(parmFisico.getLocal());
		return f;
	}

	/** v7: a convocatoria sem os excluidos (os participantes do fisico). */
	private static List<TreinoConvocadoData> participantes(List<TreinoConvocadoData> parmConvocatoria,
			Set<Integer> parmExcluidos) {
		List<TreinoConvocadoData> lista = new ArrayList<>();
		for (TreinoConvocadoData c : parmConvocatoria) {
			if (parmExcluidos == null || !parmExcluidos.contains(c.getIdJogador())) {
				lista.add(c);
			}
		}
		return lista;
	}

	/** Treinos do micro ciclo, com contagens, derivados e avisos (R12, R17). */
	List<Treino> listaTreinos(Connection conn, Identidade parmIdentidade, int parmIdMicroCiclo) throws SQLException {
		return listaTreinos(conn, parmIdentidade, parmIdMicroCiclo, null);
	}

	/** Balnearios (B11): com parmJogos (os da resposta), os seus balnearios e avisos ficam tambem preenchidos. */
	List<Treino> listaTreinos(Connection conn, Identidade parmIdentidade, int parmIdMicroCiclo,
			List<JogoSemanaData> parmJogos) throws SQLException {
		PreparedStatement preparedStatement = conn
				.prepareStatement(SQL_TREINO + "and t.id_micro_ciclo = ? order by t.`data`, t.hora_inicio, t.id");
		preparedStatement.setInt(1, parmIdentidade.getTenantId());
		preparedStatement.setInt(2, parmIdMicroCiclo);
		ResultSet rs = preparedStatement.executeQuery();
		List<Treino> treinos = new ArrayList<>();
		while (rs.next()) {
			treinos.add(lerTreino(rs));
		}
		Set<Integer> equipas = equipasDoUtilizador(conn, parmIdentidade.getIdUtilizador());
		Map<Integer, Set<Integer>> indisponiveis = new HashMap<>();
		Map<Integer, Set<Integer>> ausencias = ausenciasAtivasPorTreino(conn, parmIdMicroCiclo, indisponiveis);
		Map<Integer, List<TreinoConvocadoData>> convocatorias = new HashMap<>();
		Map<Integer, List<TreinoConvocadoData>> convocatoriasTodas = new HashMap<>(); // com os indisponiveis
		Map<Integer, String> cores = coresEquipas(conn, parmIdentidade.getTenantId());
		Map<Integer, Treino> porId = new HashMap<>();
		List<Integer> idsLigados = new ArrayList<>();
		for (Treino t : treinos) {
			porId.put(t.d.getId(), t);
			if (t.ligado()) {
				idsLigados.add(t.d.getId());
			}
		}
		Map<Integer, Set<Integer>> excluidos = excluidosDe(conn, parmIdentidade.getTenantId(), idsLigados);
		Map<Integer, FisicoStaffData> resumos = new HashMap<>();
		for (Treino t : treinos) {
			t.d.setCorEquipa(cores.get(t.d.getIdEquipa()));
			Treino principal = t.ligado() ? porId.get(t.idPrincipal) : null;
			if (t.ligado() && principal != null) {
				// v6 (X3): o fisico ligado entra no resumo do principal; a convocatoria e a do principal
				FisicoStaffData f = new FisicoStaffData();
				f.setId(t.d.getId());
				f.setHoraInicio(t.d.getHoraInicio());
				f.setHoraFim(t.d.getHoraFim());
				f.setLocal(t.d.getLocal());
				f.setSubGrupo(t.d.getSubGrupo());
				f.setPosicao(posicaoFisico(principal.d, f.getHoraInicio(), f.getHoraFim()));
				if (principal.d.getFisicos() != null) {
					principal.d.getFisicos().add(f);
				}
				resumos.put(t.d.getId(), f);
			} else {
				List<TreinoConvocadoData> todos = convocatoriaEfetiva(conn, t);
				convocatoriasTodas.put(t.d.getId(), todos);
				// Indisponibilidades (D-1): o indisponivel sai da convocatoria mostrada e contada; a ausencia fica
				List<TreinoConvocadoData> conv = participantes(todos, indisponiveis.get(t.d.getId()));
				convocatorias.put(t.d.getId(), conv);
				t.d.setNumConvocados(conv.size());
				t.d.setNumAusencias(ausencias.getOrDefault(t.d.getId(), Set.of()).size());
			}
			preencheDerivados(t, parmIdentidade, equipas);
		}
		for (Treino t : treinos) {
			Treino principal = t.ligado() ? porId.get(t.idPrincipal) : null;
			if (principal != null) {
				// v7 (R201): os avisos de jogadores do fisico so contam os participantes
				List<TreinoConvocadoData> part = participantes(
						convocatorias.getOrDefault(principal.d.getId(), new ArrayList<>()), excluidos.get(t.d.getId()));
				convocatorias.put(t.d.getId(), part);
				t.d.setNumConvocados(principal.d.getNumConvocados()); // compativel com os builds v6
				t.d.setNumAusencias(principal.d.getNumAusencias());
				Set<Integer> ausentes = ausencias.getOrDefault(principal.d.getId(), Set.of());
				int numAusencias = 0;
				// numAusencias mantem-se: conta tambem os participantes indisponiveis
				for (TreinoConvocadoData c : participantes(
						convocatoriasTodas.getOrDefault(principal.d.getId(), new ArrayList<>()), excluidos.get(t.d.getId()))) {
					if (ausentes.contains(c.getIdJogador())) {
						numAusencias++;
					}
				}
				FisicoStaffData resumo = resumos.get(t.d.getId());
				resumo.setNumParticipantes(part.size());
				resumo.setNumAusencias(numAusencias);
			}
		}
		// v8 (Y9): respostas das sessoes Aprovadas (e Realizadas), com 2 consultas por micro ciclo
		List<Integer> idsSessoes = new ArrayList<>();
		Set<Integer> idsConvocados = new HashSet<>();
		for (Treino t : treinos) {
			if (!t.ligado() && !t.evento() && APROVADO.equals(t.estadoBD) && PUBLICADO.equals(t.mcEstado)) { // v9 (R246)
				idsSessoes.add(t.d.getId());
				for (TreinoConvocadoData c : convocatorias.getOrDefault(t.d.getId(), new ArrayList<>())) {
					idsConvocados.add(c.getIdJogador());
				}
			}
		}
		Map<Integer, Map<Integer, Confirmacao>> confirmacoes = confirmacoesDe(conn, parmIdentidade.getTenantId(),
				idsSessoes);
		Set<Integer> contas = comConta(conn, parmIdentidade.getTenantId(), idsConvocados);
		for (Integer idSessao : idsSessoes) {
			Treino t = porId.get(idSessao);
			sm.core.data.ContagemRespostasData r = new sm.core.data.ContagemRespostasData();
			for (TreinoConvocadoData c : convocatorias.getOrDefault(idSessao, new ArrayList<>())) {
				String estado = estadoResposta(ausencias.getOrDefault(idSessao, Set.of()).contains(c.getIdJogador()),
						contas.contains(c.getIdJogador()), confirmacoes.getOrDefault(idSessao, Map.of()).get(c.getIdJogador()),
						sessaoDoJogador(t.d, t.d.getFisicos(), excluidos, c.getIdJogador()));
				if (VOU.equals(estado)) {
					r.setVao(r.getVao() + 1);
				} else if (NAO_VOU.equals(estado)) {
					r.setNaoVao(r.getNaoVao() + 1);
				} else {
					r.setSemResposta(r.getSemResposta() + 1);
					if (SEM_CONTA.equals(estado)) {
						r.setSemConta(r.getSemConta() + 1);
					}
				}
			}
			t.d.setRespostas(r);
		}
		List<JogoSemanaData> jogos = parmJogos != null ? parmJogos : treinos.isEmpty() ? new ArrayList<>()
				: jogosSemana(conn, parmIdentidade.getTenantId(), treinos.get(0).dataInicio, null);
		avisos(treinos, convocatorias, jogos);
		BalnearioHelper.preenche(conn, parmIdentidade.getTenantId(), parmIdentidade.isAdmin(), treinos, jogos);
		return treinos;
	}

	/** Q54: o jogo dura 90 min; o conflito conta desde a concentracao (ou hora - 60). */
	static final int DURACAO_JOGO_MIN = 90;

	/** R12, R17, R97: so avisam, nunca bloqueiam. Cancelados nao contam. */
	private static void avisos(List<Treino> parmTreinos, Map<Integer, List<TreinoConvocadoData>> parmConvocatorias,
			List<JogoSemanaData> parmJogos) {
		Map<Integer, Treino> porId = new HashMap<>();
		for (Treino t : parmTreinos) {
			porId.put(t.d.getId(), t);
		}
		for (Treino a : parmTreinos) {
			if (CANCELADO.equals(a.estadoBD)) {
				continue;
			}
			// v6 (R173): os avisos de um fisico ligado vao para o principal, com o prefixo do fisico
			Treino principalA = a.ligado() ? porId.get(a.idPrincipal) : null;
			List<String> destino = principalA != null ? principalA.d.getAvisos() : a.d.getAvisos();
			String prefixo = principalA != null ? "Físico " + intervalo(a.d) + ": " : "";
			// v4 (W4): treino da mesma equipa sobreposto a [concentracao, hora + 90]
			for (JogoSemanaData j : parmJogos) {
				if (j.getIdEquipa() != a.d.getIdEquipa() || j.getData() != a.d.getData() || j.getHora() == null
						|| !HORA.matcher(j.getHora()).matches()) {
					continue;
				}
				boolean temConcentracao = j.getHoraConcentracao() != null && HORA.matcher(j.getHoraConcentracao()).matches();
				int[] intervaloJogo = intervaloJogo(j);
				if (minutos(a.d.getHoraInicio()) < intervaloJogo[1] && intervaloJogo[0] < minutosFim(a.d)) {
					destino.add(prefixo + "Coincide com o jogo da equipa ("
							+ (temConcentracao ? "concentração " + j.getHoraConcentracao() + ", " : "") + "jogo "
							+ j.getHora() + ")");
				}
			}
			for (Treino b : parmTreinos) {
				// v6 (R173): sem avisos dentro da mesma sessao (principal e os seus fisicos)
				if (sessao(a) == sessao(b) || CANCELADO.equals(b.estadoBD) || !sobrepostos(a.d, b.d)) {
					continue;
				}
				if (a.evento() || b.evento()) {
					// v5 (R130): os eventos so entram no aviso de local
				} else if (a.d.getIdEquipa() == b.d.getIdEquipa()) {
					destino.add(prefixo + b.d.getNomeEquipa() + " já tem treino " + intervaloTexto(b.d) + " neste dia");
				} else {
					Set<Integer> idsB = new HashSet<>();
					for (TreinoConvocadoData c : parmConvocatorias.get(b.d.getId())) {
						idsB.add(c.getIdJogador());
					}
					for (TreinoConvocadoData c : parmConvocatorias.get(a.d.getId())) {
						if (idsB.contains(c.getIdJogador())) {
							destino.add(prefixo + c.getNome() + " também está convocado para " + b.d.getNomeEquipa()
									+ " (" + intervalo(b.d) + ")");
						}
					}
				}
				if (a.d.getLocal() != null && b.d.getLocal() != null && a.d.getLocal().equalsIgnoreCase(b.d.getLocal())) {
					destino.add(prefixo + a.d.getLocal() + " já está ocupado " + intervaloTexto(b.d) + " ("
							+ b.d.getNomeEquipa() + ")");
				}
			}
		}
	}

	/** Q54: o jogo ocupa [concentracao (ou hora - 60), hora + 90], em minutos. Exige hora HH:MM valida. */
	static int[] intervaloJogo(JogoSemanaData j) {
		boolean temConcentracao = j.getHoraConcentracao() != null && HORA.matcher(j.getHoraConcentracao()).matches();
		int inicio = temConcentracao ? minutos(j.getHoraConcentracao()) : minutos(j.getHora()) - 60;
		return new int[] { inicio, minutos(j.getHora()) + DURACAO_JOGO_MIN };
	}

	/** v6: a sessao de um treino (o principal, para um fisico ligado). */
	private static int sessao(Treino t) {
		return t.idPrincipal != null ? t.idPrincipal : t.d.getId();
	}

	private static String intervaloTexto(TreinoData parmTreino) {
		return parmTreino.getHoraFim() != null
				? "das " + parmTreino.getHoraInicio() + " às " + parmTreino.getHoraFim()
				: "a partir das " + parmTreino.getHoraInicio();
	}

	/** Treino com contagens e avisos da semana. */
	public TreinoData getTreinoData(Identidade parmIdentidade, int parmIdTreino) {
		return leitura("getTreinoData", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, parmIdentidade.getTenantId());
			for (Treino s : listaTreinos(conn, parmIdentidade, t.d.getIdMicroCiclo())) {
				if (s.d.getId() == parmIdTreino) {
					return s.d;
				}
			}
			return t.d;
		});
	}

	// ==================== 1. semana ====================

	public SemanaMicroCicloData getSemana(Identidade parmIdentidade, int parmData) {
		int segunda = segundaDe(parmData);
		int tenant = parmIdentidade.getTenantId();
		return leitura("getSemana", conn -> {
			SemanaMicroCicloData semana = new SemanaMicroCicloData();
			semana.setDataInicio(segunda);
			Integer epoca = epocaAtiva(conn, tenant);
			MicroCicloData mc = microCicloDaSemana(conn, tenant, segunda);
			// cores: todas as equipas da epoca do micro ciclo (ou da ativa, sem micro ciclo)
			Integer epocaCores = mc != null ? Integer.valueOf(mc.getIdEpoca()) : epoca;
			if (epocaCores != null) {
				Map<Integer, String> cores = coresEquipas(conn, tenant);
				PreparedStatement psEquipas = conn.prepareStatement("select ee.id, "
						+ "coalesce(nullif(ee.nome, ''), es.nome) as nome_equipa from escalao_epoca ee "
						+ "inner join epoca e on e.id = ee.id_epoca left join escalao es on es.id = ee.id_escalao "
						+ "where ee.id_epoca = ? and e.tenant_id = ? order by nome_equipa, ee.id");
				psEquipas.setInt(1, epocaCores);
				psEquipas.setInt(2, tenant);
				ResultSet rsEquipas = psEquipas.executeQuery();
				while (rsEquipas.next()) {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("idEquipa", rsEquipas.getInt(1));
					item.put("nome", rsEquipas.getString(2));
					item.put("cor", cores.get(rsEquipas.getInt(1)));
					semana.getCoresEquipas().add(item);
				}
			}
			// CA7: a quem nao e ADMIN, um rascunho e "sem plano"
			if (mc != null && RASCUNHO.equals(mc.getEstado()) && !parmIdentidade.isAdmin()) {
				mc = null;
			}
			semana.setSomenteLeitura(semanaTerminou(segunda) || (mc != null && (epoca == null || mc.getIdEpoca() != epoca)));
			// v4 (W2): jogos da semana, sempre (tambem sem micro ciclo)
			semana.setJogos(jogosSemana(conn, tenant, segunda, null));
			semana.setGrupos(gruposEvento(conn, tenant)); // v5 (W2)
			// v4 (W6): previsao da reativacao, so ao ADMIN
			if (mc != null && parmIdentidade.isAdmin() && reativavel(mc, epoca)) {
				int voltam = treinosAReativar(conn, tenant, mc, epoca).size();
				Map<String, Integer> reativacao = new LinkedHashMap<>();
				reativacao.put("voltam", voltam);
				reativacao.put("ficam", contaCancelados(conn, mc.getId()) - voltam);
				semana.setReativacao(reativacao);
			}
			if (mc != null) {
				semana.setMicroCiclo(mc);
				if (EM_APROVACAO.equals(mc.getEstado())) {
					semana.setProgresso(progresso(conn, tenant, mc.getId())); // v9 (Z11)
				}
				Set<Integer> equipasComTreino = new HashSet<>();
				// balnearios (B11, B15): os jogos da resposta ficam preenchidos so com micro ciclo visivel
				for (Treino t : listaTreinos(conn, parmIdentidade, mc.getId(), semana.getJogos())) {
					semana.getTreinos().add(t.d);
					if (!t.evento()) {
						equipasComTreino.add(t.d.getIdEquipa());
					}
				}
				for (Integer idEquipa : equipasComTreino) {
					if (notificacaoHelper.destinatariosEquipa(conn, idEquipa, tenant).isEmpty()) {
						semana.getEquipasSemTreinador().add(idEquipa);
					}
				}
				if (parmIdentidade.isAdmin()) {
					semana.setBalnearios(BalnearioHelper.lista(conn, tenant));
				}
				int atual = segundaDe(hoje());
				if (parmIdentidade.isAdmin() && (segunda == atual || segunda == somaDias(atual, 7))) {
					semana.setFisicosLigaveis(fisicosLigaveis(conn, tenant, mc.getId()));
				}
			}

			PreparedStatement psFora = conn.prepareStatement("select p.id, p.id_equipa, "
					+ "coalesce(nullif(ee.nome, ''), es.nome) as nome_equipa, p.`data`, p.hora from presencas p "
					+ "inner join escalao_epoca ee on ee.id = p.id_equipa left join escalao es on es.id = ee.id_escalao "
					+ "inner join epoca e on e.id = ee.id_epoca where e.tenant_id = ? and p.`data` between ? and ? "
					+ "and not exists (select 1 from treino t where t.id_presenca = p.id) order by p.`data`, p.hora");
			psFora.setInt(1, tenant);
			psFora.setInt(2, segunda);
			psFora.setInt(3, somaDias(segunda, 6));
			ResultSet rsFora = psFora.executeQuery();
			while (rsFora.next()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("idPresenca", rsFora.getInt("id"));
				item.put("idEquipa", rsFora.getInt("id_equipa"));
				item.put("nomeEquipa", rsFora.getString("nome_equipa"));
				item.put("data", rsFora.getInt("data"));
				item.put("hora", rsFora.getString("hora"));
				semana.getForaDoPlano().add(item);
			}

			PreparedStatement psLocais = conn.prepareStatement("select distinct t.local_treino from treino t "
					+ "inner join micro_ciclo m on m.id = t.id_micro_ciclo where m.tenant_id = ? "
					+ "and t.local_treino is not null and t.local_treino <> '' order by t.local_treino");
			psLocais.setInt(1, tenant);
			ResultSet rsLocais = psLocais.executeQuery();
			while (rsLocais.next()) {
				semana.getLocais().add(rsLocais.getString(1));
			}
			return semana;
		});
	}

	/**
	 * v6 (X12, R186): fisicos avulsos (nao cancelados, nao realizados, por comecar) com exatamente um ESCALAO
	 * candidato da mesma equipa e dia no micro ciclo (nao cancelado, nao realizado, sessao por comecar).
	 */
	private static List<Map<String, Integer>> fisicosLigaveis(Connection conn, int parmTenantId, int parmIdMicroCiclo)
			throws SQLException {
		Map<Integer, List<Integer>> candidatos = new LinkedHashMap<>();
		try (PreparedStatement preparedStatement = conn.prepareStatement("select f.id, f.`data`, f.hora_inicio, "
				+ "p.id as id_p, p.hora_inicio as p_inicio, "
				+ "(select min(x.hora_inicio) from treino x where x.id_treino_principal = p.id) as p_fisicos "
				+ "from treino f inner join micro_ciclo m on m.id = f.id_micro_ciclo "
				+ "inner join treino p on p.id_micro_ciclo = f.id_micro_ciclo and p.id_equipa = f.id_equipa "
				+ "and p.`data` = f.`data` and p.tipo = 'ESCALAO' and p.id_grupo is null and p.id_treino_principal is null "
				+ "and p.estado <> 'CANCELADO' and p.id_presenca is null "
				+ "where m.tenant_id = ? and m.id = ? and f.tipo = 'FISICO' and f.id_treino_principal is null "
				+ "and f.id_grupo is null and f.estado <> 'CANCELADO' and f.id_presenca is null order by f.id, p.id")) {
			preparedStatement.setInt(1, parmTenantId);
			preparedStatement.setInt(2, parmIdMicroCiclo);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					int data = rs.getInt("data");
					String inicioP = rs.getString("p_inicio");
					String fisicosP = rs.getString("p_fisicos");
					String sessaoP = fisicosP != null && fisicosP.compareTo(inicioP) < 0 ? fisicosP : inicioP;
					if (inicioPassou(data, rs.getString("hora_inicio")) || inicioPassou(data, sessaoP)) {
						continue;
					}
					candidatos.computeIfAbsent(rs.getInt("id"), k -> new ArrayList<>()).add(rs.getInt("id_p"));
				}
			}
		}
		List<Map<String, Integer>> lista = new ArrayList<>();
		candidatos.forEach((idFisico, principais) -> {
			if (principais.size() == 1) {
				Map<String, Integer> item = new LinkedHashMap<>();
				item.put("idFisico", idFisico);
				item.put("idPrincipal", principais.get(0));
				lista.add(item);
			}
		});
		return lista;
	}

	// ==================== 2. criar / copiar ====================

	public SemanaMicroCicloData criaMicroCiclo(Identidade parmIdentidade, PedidoMicroCicloData parmPedido) {
		int dataInicio = parmPedido.getDataInicio();
		if (paraData(dataInicio).getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new PedidoInvalidoException("A data de início tem de ser uma segunda-feira.");
		}
		Integer copiarDe = parmPedido.getCopiarDe();
		if (copiarDe != null && (paraData(copiarDe).getDayOfWeek() != DayOfWeek.MONDAY || copiarDe == dataInicio)) {
			throw new PedidoInvalidoException("A semana a copiar tem de ser outra segunda-feira.");
		}
		if (semanaTerminou(dataInicio)) {
			throw new ConflitoException("Esta semana já terminou.");
		}
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		List<String> avisos = emTransacao("criaMicroCiclo", conn -> {
			Integer epoca = epocaAtiva(conn, tenant);
			if (epoca == null) {
				throw new ConflitoException("Não há época ativa.");
			}
			int idMicroCiclo;
			try {
				PreparedStatement psInsere = conn.prepareStatement("insert into micro_ciclo(tenant_id, id_epoca, "
						+ "data_inicio, estado, datacriacao, id_utilizador_criacao) values (?, ?, ?, 'RASCUNHO', now(), ?)",
						Statement.RETURN_GENERATED_KEYS);
				psInsere.setInt(1, tenant);
				psInsere.setInt(2, epoca);
				psInsere.setInt(3, dataInicio);
				psInsere.setInt(4, utilizador);
				psInsere.executeUpdate();
				ResultSet rsId = psInsere.getGeneratedKeys();
				rsId.next();
				idMicroCiclo = rsId.getInt(1);
			} catch (SQLIntegrityConstraintViolationException e) {
				// v4 (Q49): diz o estado do que existe; um cancelado reativa-se
				MicroCicloData existente = microCicloDaSemana(conn, tenant, dataInicio);
				if (existente != null && CANCELADO.equals(existente.getEstado())) {
					throw new ConflitoException("Esta semana tem um micro ciclo cancelado: reative-o.");
				}
				throw new ConflitoException("Já existe um micro ciclo para esta semana"
						+ (existente != null ? " (" + estadoTexto(existente.getEstado()) + ")." : "."));
			}

			List<String> lista = new ArrayList<>();
			if (copiarDe != null) {
				MicroCicloData origem = microCicloDaSemana(conn, tenant, copiarDe);
				if (origem == null) {
					throw new ConflitoException("Não há micro ciclo na semana a copiar.");
				}
				PreparedStatement psOrigem = conn.prepareStatement(SQL_TREINO
						+ "and t.id_micro_ciclo = ? and t.estado <> 'CANCELADO' order by t.`data`, t.hora_inicio");
				psOrigem.setInt(1, tenant);
				psOrigem.setInt(2, origem.getId());
				ResultSet rsOrigem = psOrigem.executeQuery();
				List<Treino> treinos = new ArrayList<>();
				while (rsOrigem.next()) {
					treinos.add(lerTreino(rsOrigem));
				}
				PreparedStatement psEquipa = conn
						.prepareStatement("select 1 from escalao_epoca where id = ? and id_epoca = ?");
				// v6 (R184): primeiro os treinos autonomos (idAntigo -> idNovo), depois os fisicos ligados
				Map<Integer, Integer> copiados = new HashMap<>();
				for (Treino t : treinos) {
					if (t.ligado()) {
						continue;
					}
					String descricao = (t.evento() ? t.d.getNomeGrupo() : t.d.getNomeEquipa()) + " " + dia(t.d.getData()) + " "
							+ t.d.getHoraInicio();
					psEquipa.setInt(1, t.d.getIdEquipa());
					psEquipa.setInt(2, epoca);
					// v5: os eventos nao tem equipa (copiam-se com o grupo, sem convocatoria)
					if (!t.evento() && !psEquipa.executeQuery().next()) {
						lista.add(descricao + ": a equipa não é da época ativa, treino não copiado");
						continue;
					}
					long dias = ChronoUnit.DAYS.between(paraData(copiarDe), paraData(t.d.getData()));
					int novaData = somaDias(dataInicio, (int) dias);
					if (inicioPassou(novaData, t.d.getHoraInicio())) {
						lista.add(descricao + ": o dia já passou, treino não copiado");
						continue;
					}
					int idAntigo = t.d.getId();
					t.d.setData(novaData);
					int idTreino = insereTreino(conn, idMicroCiclo, t.d, RASCUNHO, utilizador);
					insereConvocatoriaOmissao(conn, idTreino, t.d.getIdEquipa(), utilizador);
					copiados.put(idAntigo, idTreino);
				}
				for (Treino t : treinos) {
					if (!t.ligado()) {
						continue;
					}
					String descricao = "Físico " + t.d.getNomeEquipa() + " " + dia(t.d.getData()) + " " + t.d.getHoraInicio();
					Integer idPrincipalNovo = copiados.get(t.idPrincipal);
					if (idPrincipalNovo == null) {
						lista.add(descricao + ": treino principal não copiado, físico não copiado");
						continue;
					}
					long dias = ChronoUnit.DAYS.between(paraData(copiarDe), paraData(t.d.getData()));
					int novaData = somaDias(dataInicio, (int) dias);
					if (inicioPassou(novaData, t.d.getHoraInicio())) {
						lista.add(descricao + ": o dia já passou, treino não copiado");
						continue;
					}
					FisicoStaffData f = new FisicoStaffData();
					f.setHoraInicio(t.d.getHoraInicio());
					f.setHoraFim(t.d.getHoraFim());
					f.setLocal(t.d.getLocal());
					f.setSubGrupo(t.d.getSubGrupo());
					insereFisico(conn, idMicroCiclo, idPrincipalNovo, t.d.getIdEquipa(), novaData, f, RASCUNHO, utilizador);
				}
				for (Integer idCopia : copiados.values()) {
					sincronizaTreino(conn, tenant, idCopia, null); // Indisponibilidades (Z3)
				}
			}
			historico(conn, idMicroCiclo, null, utilizador,
					copiarDe != null ? "Micro ciclo criado (cópia de " + ddmm(copiarDe) + ")" : "Micro ciclo criado");
			return lista;
		});
		log.info("MicroCicloHelper | criaMicroCiclo | tenant:{} semana:{} copiarDe:{}", tenant, dataInicio, copiarDe);
		SemanaMicroCicloData semana = getSemana(parmIdentidade, dataInicio);
		semana.setAvisos(avisos);
		return semana;
	}

	private static int insereTreino(Connection conn, int parmIdMicroCiclo, TreinoData parmTreino, String parmEstado,
			int parmIdUtilizador) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("insert into treino(id_micro_ciclo, id_equipa, tipo, "
				+ "tipo_descricao, sub_grupo, `data`, hora_inicio, hora_fim, local_treino, observacoes, estado, versao, "
				+ "datacriacao, id_utilizador_criacao, id_grupo, aprovar_ao_publicar) "
				+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now(), ?, ?, ?)",
				Statement.RETURN_GENERATED_KEYS);
		preparedStatement.setInt(1, parmIdMicroCiclo);
		// v5 (W1): evento com id_equipa NULL e id_grupo; treino com equipa e id_grupo NULL
		if (parmTreino.getIdGrupo() > 0) {
			preparedStatement.setNull(2, java.sql.Types.INTEGER);
			preparedStatement.setInt(13, parmTreino.getIdGrupo());
		} else {
			preparedStatement.setInt(2, parmTreino.getIdEquipa());
			preparedStatement.setNull(13, java.sql.Types.INTEGER);
		}
		preparedStatement.setString(14, parmTreino.isAprovarAoPublicar() ? "1" : "0");
		preparedStatement.setString(3, parmTreino.getTipo());
		preparedStatement.setString(4, parmTreino.getTipoDescricao());
		preparedStatement.setString(5, parmTreino.getSubGrupo());
		preparedStatement.setInt(6, parmTreino.getData());
		preparedStatement.setString(7, parmTreino.getHoraInicio());
		preparedStatement.setString(8, parmTreino.getHoraFim());
		preparedStatement.setString(9, parmTreino.getLocal());
		preparedStatement.setString(10, parmTreino.getObservacoes());
		preparedStatement.setString(11, parmEstado);
		preparedStatement.setInt(12, parmIdUtilizador);
		preparedStatement.executeUpdate();
		ResultSet rs = preparedStatement.getGeneratedKeys();
		rs.next();
		return rs.getInt(1);
	}

	// ==================== 3-5. publicar, cancelar, eliminar ====================

	/** v9 (§1.3, Z3): o atleta so ve micro ciclos publicados, ou cancelados depois de publicados. Alias m = micro_ciclo. */
	static final String MC_VISIVEL_ATLETA = "(m.estado = 'PUBLICADO' or (m.estado = 'CANCELADO' "
			+ "and coalesce(m.estado_antes_cancelamento, 'PUBLICADO') = 'PUBLICADO'))";

	private static final String MSG_SUBMETA = "Submeta primeiro o micro ciclo para aprovação.";

	/** v9: as sessoes do micro ciclo (sem os fisicos ligados), por data e hora. */
	private List<Treino> sessoesDe(Connection conn, int parmTenantId, int parmIdMicroCiclo) throws SQLException {
		List<Treino> sessoes = new ArrayList<>();
		try (PreparedStatement preparedStatement = conn.prepareStatement(SQL_TREINO + "and t.id_micro_ciclo = ? "
				+ "and t.id_treino_principal is null order by t.`data`, t.hora_inicio, t.id")) {
			preparedStatement.setInt(1, parmTenantId);
			preparedStatement.setInt(2, parmIdMicroCiclo);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					sessoes.add(lerTreino(rs));
				}
			}
		}
		return sessoes;
	}

	/** v9 (Q131): ha sessoes nao canceladas e estao todas APROVADO. */
	private static boolean pronto(Connection conn, int parmIdMicroCiclo) throws SQLException {
		try (PreparedStatement preparedStatement = conn.prepareStatement("select count(*), "
				+ "coalesce(sum(case when estado <> 'APROVADO' then 1 else 0 end), 0) from treino "
				+ "where id_micro_ciclo = ? and id_treino_principal is null and estado <> 'CANCELADO'")) {
			preparedStatement.setInt(1, parmIdMicroCiclo);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				return rs.next() && rs.getInt(1) > 0 && rs.getInt(2) == 0;
			}
		}
	}

	/** v9 (Z11): contagens por sessao (os eventos contam; os cancelados nao). */
	private Map<String, Object> progresso(Connection conn, int parmTenantId, int parmIdMicroCiclo) throws SQLException {
		int total = 0;
		int aprovados = 0;
		int pendentes = 0;
		int rejeitados = 0;
		int naoATempo = 0;
		for (Treino t : sessoesDe(conn, parmTenantId, parmIdMicroCiclo)) {
			if (CANCELADO.equals(t.estadoBD)) {
				continue;
			}
			total++;
			if (APROVADO.equals(t.estadoBD)) {
				aprovados++;
			} else if (PENDENTE.equals(t.estadoBD)) {
				pendentes++;
				if (inicioPassou(t.d.getData(), t.inicioSessao())) {
					naoATempo++;
				}
			} else if (REJEITADO.equals(t.estadoBD)) {
				rejeitados++;
			}
		}
		Map<String, Object> progresso = new LinkedHashMap<>();
		progresso.put("total", total);
		progresso.put("aprovados", aprovados);
		progresso.put("pendentes", pendentes);
		progresso.put("rejeitados", rejeitados);
		progresso.put("naoAprovadosATempo", naoATempo);
		progresso.put("pronto", total > 0 && aprovados == total);
		return progresso;
	}

	/**
	 * v9 (R248, Q141): PRONTO_A_PUBLICAR aos ADMIN (sem o autor) quando a transacao deixa pronto um micro ciclo
	 * EM_APROVACAO que nao estava pronto no inicio dela.
	 */
	private void talvezPronto(Connection conn, Identidade parmIdentidade, int parmIdMicroCiclo, boolean parmProntoAntes)
			throws SQLException {
		if (parmProntoAntes) {
			return;
		}
		MicroCicloData mc = carregaMicroCiclo(conn, parmIdMicroCiclo, parmIdentidade.getTenantId());
		if (!EM_APROVACAO.equals(mc.getEstado()) || !pronto(conn, parmIdMicroCiclo)) {
			return;
		}
		Integer primeiro = null;
		for (Treino t : sessoesDe(conn, parmIdentidade.getTenantId(), parmIdMicroCiclo)) {
			if (!CANCELADO.equals(t.estadoBD)) {
				primeiro = t.d.getId();
				break;
			}
		}
		notifica(conn, parmIdentidade, notificacaoHelper.destinatariosAdmin(conn, parmIdentidade.getTenantId()),
				"PRONTO_A_PUBLICAR", primeiro, null,
				"Micro ciclo de " + ddmm(mc.getDataInicio()) + " pronto a publicar: todos os treinos estão aprovados");
	}

	/** v9 (Z1): Rascunho -> Em aprovacao (o publica da v8): treinos Pendentes, eventos "Ja aprovado", TREINOS_PENDENTES. */
	public SemanaMicroCicloData submete(Identidade parmIdentidade, int parmIdMicroCiclo) {
		int tenant = parmIdentidade.getTenantId();
		int segunda = emTransacao("submete", conn -> {
			MicroCicloData mc = carregaMicroCiclo(conn, parmIdMicroCiclo, tenant);
			PreparedStatement psMc = conn.prepareStatement("update micro_ciclo set estado = 'EM_APROVACAO' "
					+ "where id = ? and tenant_id = ? and estado = 'RASCUNHO'");
			psMc.setInt(1, parmIdMicroCiclo);
			psMc.setInt(2, tenant);
			if (psMc.executeUpdate() == 0) {
				throw new ConflitoException("O micro ciclo não está em rascunho.");
			}
			if (sessoesDe(conn, tenant, parmIdMicroCiclo).isEmpty()) {
				throw new ConflitoException("Acrescente treinos antes de submeter.");
			}
			// v5 (W15): os eventos "Ja aprovado" passam logo a Aprovado
			List<Integer> eventosAprovados = new ArrayList<>();
			try (PreparedStatement psEventos = conn.prepareStatement("select id from treino where id_micro_ciclo = ? "
					+ "and estado = 'RASCUNHO' and id_grupo is not null and aprovar_ao_publicar = '1' "
					+ "and id_treino_principal is null")) {
				psEventos.setInt(1, parmIdMicroCiclo);
				try (ResultSet rsEventos = psEventos.executeQuery()) {
					while (rsEventos.next()) {
						eventosAprovados.add(rsEventos.getInt(1));
					}
				}
			}
			for (Integer idEvento : eventosAprovados) {
				marcaAprovadoAdmin(conn, idEvento, parmIdMicroCiclo, parmIdentidade.getIdUtilizador());
				try (PreparedStatement psVersao = conn.prepareStatement("update treino set versao = versao + 1 where id = ?")) {
					psVersao.setInt(1, idEvento);
					psVersao.executeUpdate();
				}
			}
			PreparedStatement psTreinos = conn.prepareStatement("update treino set estado = 'PENDENTE', versao = versao + 1 "
					+ "where id_micro_ciclo = ? and estado = 'RASCUNHO' and id_treino_principal is null");
			psTreinos.setInt(1, parmIdMicroCiclo);
			int n = psTreinos.executeUpdate() + eventosAprovados.size(); // v6 (R175): conta sessoes
			propagaSessao(conn, tenant, null, parmIdMicroCiclo);
			historico(conn, parmIdMicroCiclo, null, parmIdentidade.getIdUtilizador(),
					"Micro ciclo submetido para aprovação (" + n + " treinos)");

			// Q31 (BK-12): uma notificacao por destinatario, com o numero de treinos das suas equipas
			PreparedStatement psPendentes = conn.prepareStatement(
					"select id, id_equipa from treino where id_micro_ciclo = ? and estado = 'PENDENTE' and id_grupo is null "
					+ "and id_treino_principal is null order by `data`, hora_inicio");
			psPendentes.setInt(1, parmIdMicroCiclo);
			ResultSet rsPendentes = psPendentes.executeQuery();
			Map<Integer, List<Integer>> treinosPorEquipa = new LinkedHashMap<>();
			while (rsPendentes.next()) {
				treinosPorEquipa.computeIfAbsent(rsPendentes.getInt(2), k -> new ArrayList<>()).add(rsPendentes.getInt(1));
			}
			notificaAgregada(conn, parmIdentidade, treinosPorEquipa, "TREINOS_PENDENTES",
					k -> "Micro ciclo de " + ddmm(mc.getDataInicio()) + ": " + k + " treinos à espera da tua aprovação");
			talvezPronto(conn, parmIdentidade, parmIdMicroCiclo, false); // so com eventos "Ja aprovado"
			return mc.getDataInicio();
		});
		log.info("MicroCicloHelper | submete | tenant:{} microCiclo:{}", tenant, parmIdMicroCiclo);
		return getSemana(parmIdentidade, segunda);
	}

	/**
	 * v9 (Z1, Z2): Em aprovacao -> Publicado, numa so transacao (tudo ou nada). Cancela os Pendentes cuja sessao ja
	 * comecou; com aprovarPendentes aprova os itens pedidos (iguais aos Pendentes restantes); publica e notifica os
	 * atletas (R251). Num Rascunho: 409 "Submeta primeiro..." (R278).
	 */
	public SemanaMicroCicloData publica(Identidade parmIdentidade, int parmIdMicroCiclo,
			sm.core.data.PedidoPublicarData parmPedido) {
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		int segunda = emTransacao("publica", conn -> {
			MicroCicloData mc = carregaMicroCiclo(conn, parmIdMicroCiclo, tenant);
			if (RASCUNHO.equals(mc.getEstado())) {
				throw new ConflitoException(MSG_SUBMETA);
			}
			if (!EM_APROVACAO.equals(mc.getEstado())) {
				throw new ConflitoException("O micro ciclo não está em aprovação.");
			}
			List<Treino> sessoes = sessoesDe(conn, tenant, parmIdMicroCiclo);
			int rejeitados = 0;
			for (Treino t : sessoes) {
				if (REJEITADO.equals(t.estadoBD)) {
					rejeitados++;
				}
			}
			if (rejeitados > 0) {
				throw new ConflitoException("Há " + rejeitados + " treinos rejeitados: corrija-os ou cancele-os.");
			}
			Map<Integer, Treino> porAprovar = new LinkedHashMap<>();
			for (Treino t : sessoes) {
				if (!PENDENTE.equals(t.estadoBD)) {
					continue;
				}
				if (!inicioPassou(t.d.getData(), t.inicioSessao())) {
					porAprovar.put(t.d.getId(), t);
					continue;
				}
				// nao aprovado a tempo: cancelado na publicacao
				try (PreparedStatement psCancela = conn.prepareStatement("update treino set estado = 'CANCELADO', "
						+ "motivo_estado = 'não aprovado a tempo', origem_cancelamento = 'TREINO', versao = versao + 1 "
						+ "where id = ? and estado = 'PENDENTE'")) {
					psCancela.setInt(1, t.d.getId());
					psCancela.executeUpdate();
				}
				historico(conn, parmIdMicroCiclo, t.d.getId(), utilizador, "Cancelado | não aprovado a tempo");
				apagaConfirmacoes(conn, t.d.getId(), null);
				propagaSessao(conn, tenant, t.d.getId(), null);
			}
			if (!porAprovar.isEmpty()) {
				if (parmPedido == null || !parmPedido.isAprovarPendentes()) {
					throw new ConflitoException("Há " + porAprovar.size() + " treinos por aprovar.");
				}
				Map<Integer, Integer> versoes = new LinkedHashMap<>();
				if (parmPedido.getItens() != null) {
					for (sm.core.data.PedidoAprovacaoLoteData.Item item : parmPedido.getItens()) {
						if (item == null || item.getId() == null || item.getVersao() == null
								|| versoes.put(item.getId(), item.getVersao()) != null) {
							throw new PedidoInvalidoException("Itens inválidos (id e versão obrigatórios, sem repetidos).");
						}
					}
				}
				if (!versoes.keySet().equals(porAprovar.keySet())) {
					throw new ConflitoException("A lista de treinos por aprovar mudou. Recarregue.");
				}
				for (Treino t : porAprovar.values()) {
					try {
						aprovaUm(conn, parmIdentidade, parmIdMicroCiclo, t.d.getId(), versoes.get(t.d.getId()));
					} catch (ConflitoException e) {
						throw new ConflitoException("Não publicado: " + (t.evento() ? t.d.getNomeGrupo() : t.d.getNomeEquipa())
								+ " " + dia(t.d.getData()) + " " + t.d.getHoraInicio() + " " + e.getMessage() + ".");
					}
				}
			}
			try (PreparedStatement psMc = conn.prepareStatement("update micro_ciclo set estado = 'PUBLICADO' "
					+ "where id = ? and tenant_id = ? and estado = 'EM_APROVACAO'")) {
				psMc.setInt(1, parmIdMicroCiclo);
				psMc.setInt(2, tenant);
				if (psMc.executeUpdate() == 0) {
					throw new ConflitoException("O micro ciclo foi alterado entretanto. Recarregue.");
				}
			}
			propagaSessao(conn, tenant, null, parmIdMicroCiclo);
			List<Treino> aprovadas = new ArrayList<>();
			int n = 0;
			for (Treino t : sessoesDe(conn, tenant, parmIdMicroCiclo)) {
				if (APROVADO.equals(t.estadoBD)) {
					n++;
					if (!t.evento()) {
						aprovadas.add(t);
					}
				}
			}
			historico(conn, parmIdMicroCiclo, null, utilizador, "Micro ciclo publicado (" + n + " treinos)");
			// R251, Q136: uma por atleta com conta e com sessoes; os treinadores nao recebem nada
			notificaAtletasDaSemana(conn, parmIdentidade, aprovadas, "MICRO_CICLO_PUBLICADO",
					k -> "Micro ciclo da semana de " + ddmm(mc.getDataInicio()) + " publicado: tens " + k + " treinos");
			return mc.getDataInicio();
		});
		log.info("MicroCicloHelper | publica | tenant:{} microCiclo:{}", tenant, parmIdMicroCiclo);
		return getSemana(parmIdentidade, segunda);
	}

	/**
	 * v9 (R251, R257): uma notificacao por conta ATLETA ativa dos convocados efetivos das sessoes, com o numero de
	 * sessoes do jogador; id_treino = a primeira sessao dele (link e visibilidade). Consultas em lote (sem N+1).
	 */
	private void notificaAtletasDaSemana(Connection conn, Identidade parmIdentidade, List<Treino> parmSessoes,
			String parmTipo, java.util.function.IntFunction<String> parmTexto) throws SQLException {
		Map<Integer, Set<Integer>> convocados = convocadosEmLote(conn, parmSessoes, null);
		Map<Integer, List<Integer>> sessoesPorJogador = new LinkedHashMap<>();
		for (Treino t : parmSessoes) { // ja por data e hora
			for (Integer idJogador : convocados.getOrDefault(t.d.getId(), Set.of())) {
				sessoesPorJogador.computeIfAbsent(idJogador, k -> new ArrayList<>()).add(t.d.getId());
			}
		}
		Map<Integer, List<Integer>> contas = contasAtleta(conn, parmIdentidade.getTenantId(), sessoesPorJogador.keySet());
		for (Map.Entry<Integer, List<Integer>> j : sessoesPorJogador.entrySet()) {
			List<Integer> contasJogador = contas.get(j.getKey());
			if (contasJogador != null) {
				notifica(conn, parmIdentidade, contasJogador, parmTipo, j.getValue().get(0), null,
						parmTexto.apply(j.getValue().size()));
			}
		}
	}

	/**
	 * v7/v9: convocatoria efetiva em lote das sessoes (nao ligadas), opcionalmente so dos jogadores pedidos. Mesma regra
	 * que convocatoriaEfetiva (D4); mudar as duas.
	 */
	static Map<Integer, Set<Integer>> convocadosEmLote(Connection conn, List<Treino> parmSessoes,
			java.util.Collection<Integer> parmSoJogadores) throws SQLException {
		Map<Integer, Set<Integer>> convocados = new HashMap<>(); // treino -> jogadores
		List<Integer> ids = new ArrayList<>();
		for (Treino t : parmSessoes) {
			if (!t.ligado()) {
				ids.add(t.d.getId());
			}
		}
		if (ids.isEmpty() || (parmSoJogadores != null && parmSoJogadores.isEmpty())) {
			return convocados;
		}
		String inT = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
		String inP = parmSoJogadores == null ? null
				: String.join(", ", java.util.Collections.nCopies(parmSoJogadores.size(), "?"));
		try (PreparedStatement psConv = conn.prepareStatement("select tc.id_treino, tc.id_jogador "
				+ "from treino_convocado tc inner join jogador j on j.id = tc.id_jogador and j.estado = '1' "
				+ "inner join treino t on t.id = tc.id_treino where tc.id_treino in (" + inT + ") "
				+ (inP != null ? "and tc.id_jogador in (" + inP + ") " : "") + "and tc.estado = '1' and (tc.origem = 'ACRESCENTADO' "
				+ "or exists (select 1 from escalao_epoca_jogador eej where eej.id_escalao_epoca = t.id_equipa "
				+ "and eej.id_jogador = tc.id_jogador)) "
				+ "union select t.id, eej.id_jogador from treino t "
				+ "inner join escalao_epoca_jogador eej on eej.id_escalao_epoca = t.id_equipa "
				+ "inner join jogador j on j.id = eej.id_jogador and j.estado = '1' where t.id in (" + inT + ") "
				+ (inP != null ? "and eej.id_jogador in (" + inP + ") " : "")
				+ "and not (t.estado = 'APROVADO' or (t.estado = 'CANCELADO' and t.data_aprovacao is not null)) "
				+ "and not exists (select 1 from treino_convocado tc where tc.id_treino = t.id "
				+ "and tc.id_jogador = eej.id_jogador)")) {
			int i = 1;
			for (int volta = 0; volta < 2; volta++) {
				for (Integer id : ids) {
					psConv.setInt(i++, id);
				}
				if (parmSoJogadores != null) {
					for (Integer id : parmSoJogadores) {
						psConv.setInt(i++, id);
					}
				}
			}
			try (ResultSet rs = psConv.executeQuery()) {
				while (rs.next()) {
					convocados.computeIfAbsent(rs.getInt(1), k -> new HashSet<>()).add(rs.getInt(2));
				}
			}
		}
		return convocados;
	}

	/** v9: TREINOS_PENDENTES (ou outra agregada aos treinadores): uma por destinatario, com o numero de treinos. */
	private void notificaAgregada(Connection conn, Identidade parmIdentidade,
			Map<Integer, List<Integer>> parmTreinosPorEquipa, String parmTipo, java.util.function.IntFunction<String> parmTexto)
			throws SQLException {
		Map<Integer, List<Integer>> treinosPorDestinatario = new LinkedHashMap<>();
		for (Map.Entry<Integer, List<Integer>> equipa : parmTreinosPorEquipa.entrySet()) {
			for (Integer destinatario : notificacaoHelper.destinatariosEquipa(conn, equipa.getKey(),
					parmIdentidade.getTenantId())) {
				treinosPorDestinatario.computeIfAbsent(destinatario, k -> new ArrayList<>()).addAll(equipa.getValue());
			}
		}
		for (Map.Entry<Integer, List<Integer>> destinatario : treinosPorDestinatario.entrySet()) {
			// o primeiro treino da o link para a semana e o filtro de visibilidade
			notifica(conn, parmIdentidade, List.of(destinatario.getKey()), parmTipo, destinatario.getValue().get(0), null,
					parmTexto.apply(destinatario.getValue().size()));
		}
	}

	/** TREINOS_PENDENTES agregada: uma por destinatario, com o numero de treinos das suas equipas (sem o autor). */
	private void notificaPendentes(Connection conn, Identidade parmIdentidade,
			Map<Integer, List<Integer>> parmTreinosPorEquipa, String parmPrefixo) throws SQLException {
		notificaAgregada(conn, parmIdentidade, parmTreinosPorEquipa, "TREINOS_PENDENTES",
				k -> parmPrefixo + k + " treinos das suas equipas aguardam aprovação");
	}

	private static String estadoTexto(String parmEstado) {
		switch (parmEstado) {
		case RASCUNHO:
			return "em rascunho";
		case EM_APROVACAO:
			return "em aprovação";
		case PUBLICADO:
			return "publicado";
		default:
			return "cancelado";
		}
	}

	// ==================== v4: reativar micro ciclo (BK-v4-2) ====================

	/**
	 * R77-R81, Q51: treinos que voltam ao reativar. Cancelados com o micro ciclo (V9) ou, antes
	 * da V9, com o mesmo motivo do micro ciclo; inicio por passar; equipa da epoca ativa.
	 */
	private List<Treino> treinosAReativar(Connection conn, int parmTenantId, MicroCicloData parmMc, int parmEpocaAtiva)
			throws SQLException {
		List<Treino> treinos = new ArrayList<>();
		try (PreparedStatement preparedStatement = conn.prepareStatement(SQL_TREINO + "and t.id_micro_ciclo = ? "
				+ "and t.estado = 'CANCELADO' and (t.origem_cancelamento = 'MICRO_CICLO' "
				+ "or (t.origem_cancelamento is null and t.motivo_estado = ?)) and (t.id_grupo is not null or ee.id_epoca = ?) "
				+ "and t.id_treino_principal is null order by t.`data`, t.hora_inicio, t.id")) {
			preparedStatement.setInt(1, parmTenantId);
			preparedStatement.setInt(2, parmMc.getId());
			preparedStatement.setString(3, parmMc.getMotivoCancelamento());
			preparedStatement.setInt(4, parmEpocaAtiva);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					Treino t = lerTreino(rs);
					if (!inicioPassou(t.d.getData(), t.inicioSessao())) {
						treinos.add(t);
					}
				}
			}
		}
		return treinos;
	}

	private static int contaCancelados(Connection conn, int parmIdMicroCiclo) throws SQLException {
		try (PreparedStatement preparedStatement = conn
				.prepareStatement("select count(*) from treino where id_micro_ciclo = ? and estado = 'CANCELADO' "
						+ "and id_treino_principal is null")) {
			preparedStatement.setInt(1, parmIdMicroCiclo);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	/** Micro ciclo cancelado, semana por terminar e da epoca ativa. */
	private static boolean reativavel(MicroCicloData parmMc, Integer parmEpocaAtiva) {
		return CANCELADO.equals(parmMc.getEstado()) && !semanaTerminou(parmMc.getDataInicio()) && parmEpocaAtiva != null
				&& parmMc.getIdEpoca() == parmEpocaAtiva;
	}

	public SemanaMicroCicloData reativaMicroCiclo(Identidade parmIdentidade, int parmIdMicroCiclo) {
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		int segunda = emTransacao("reativaMicroCiclo", conn -> {
			MicroCicloData mc = carregaMicroCiclo(conn, parmIdMicroCiclo, tenant);
			Integer epoca = epocaAtiva(conn, tenant);
			if (!CANCELADO.equals(mc.getEstado())) {
				throw new ConflitoException("O micro ciclo não está cancelado.");
			}
			if (semanaTerminou(mc.getDataInicio())) {
				throw new ConflitoException("Esta semana já terminou.");
			}
			if (!reativavel(mc, epoca)) {
				throw new ConflitoException("O micro ciclo é de outra época.");
			}
			List<Treino> voltam = treinosAReativar(conn, tenant, mc, epoca);
			int ficam = contaCancelados(conn, parmIdMicroCiclo) - voltam.size();
			// v9 (Q140, R258): reativar leva a Em aprovacao; o atleta so volta a ver depois de publicar de novo
			try (PreparedStatement psMc = conn.prepareStatement("update micro_ciclo set estado = 'EM_APROVACAO', "
					+ "motivo_cancelamento = null, estado_antes_cancelamento = null where id = ? and tenant_id = ? and estado = 'CANCELADO'")) {
				psMc.setInt(1, parmIdMicroCiclo);
				psMc.setInt(2, tenant);
				if (psMc.executeUpdate() == 0) {
					throw new ConflitoException("O micro ciclo foi alterado entretanto. Recarregue.");
				}
			}
			Map<Integer, List<Integer>> treinosPorEquipa = new LinkedHashMap<>();
			int n = 0;
			for (Treino t : voltam) {
				if (reativaTreino(conn, t, utilizador, "Estado | CANCELADO => PENDENTE (reativação)")) {
					n++;
					sincronizaTreino(conn, tenant, t.d.getId(), null); // Indisponibilidades (Z3)
					if (!t.evento()) { // v5 (R128): eventos sem notificacoes
						treinosPorEquipa.computeIfAbsent(t.d.getIdEquipa(), k -> new ArrayList<>()).add(t.d.getId());
					}
				}
			}
			propagaSessao(conn, tenant, null, parmIdMicroCiclo);
			historico(conn, parmIdMicroCiclo, null, utilizador, "Micro ciclo reativado (" + n
					+ " treinos de volta a Pendente, " + (ficam + voltam.size() - n) + " ficam cancelados)");
			notificaPendentes(conn, parmIdentidade, treinosPorEquipa,
					"Micro ciclo de " + ddmm(mc.getDataInicio()) + " reativado: ");
			return mc.getDataInicio();
		});
		log.info("MicroCicloHelper | reativaMicroCiclo | tenant:{} microCiclo:{}", tenant, parmIdMicroCiclo);
		return getSemana(parmIdentidade, segunda);
	}

	/**
	 * Cancelado -> Pendente: sem motivo nem origem, versao+1, ausencias ativas a confirmar (R40),
	 * historico. A convocatoria nao muda (D4 trata de quem saiu da equipa).
	 *
	 * @return false se o treino ja nao estava cancelado (concorrencia)
	 */
	private static boolean reativaTreino(Connection conn, Treino t, int parmIdUtilizador, String parmHistorico)
			throws SQLException {
		try (PreparedStatement psTreino = conn.prepareStatement("update treino set estado = 'PENDENTE', "
				+ "motivo_estado = null, origem_cancelamento = null, versao = versao + 1 "
				+ "where id = ? and estado = 'CANCELADO'")) {
			psTreino.setInt(1, t.d.getId());
			if (psTreino.executeUpdate() == 0) {
				return false;
			}
		}
		try (PreparedStatement psAusencias = conn.prepareStatement("update treino_ausencia set pendente_confirmacao = '1', "
				+ "data_alteracao = now() where id_treino = ? and estado = 'ATIVA' and id_indisponibilidade is null")) {
			psAusencias.setInt(1, t.d.getId());
			psAusencias.executeUpdate();
		}
		historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdUtilizador, parmHistorico);
		return true;
	}

	public SemanaMicroCicloData cancelaMicroCiclo(Identidade parmIdentidade, int parmIdMicroCiclo, String parmMotivo) {
		String motivo = limpa(parmMotivo, 255, "O motivo");
		if (motivo == null) {
			throw new PedidoInvalidoException("Indique o motivo.");
		}
		int tenant = parmIdentidade.getTenantId();
		int segunda = emTransacao("cancelaMicroCiclo", conn -> {
			MicroCicloData mc = carregaMicroCiclo(conn, parmIdMicroCiclo, tenant);
			// v9 (R257): tambem em aprovacao; o estado anterior fica guardado (o atleta so ve os cancelados de um publicado)
			String estadoAntes = mc.getEstado();
			if (!PUBLICADO.equals(estadoAntes) && !EM_APROVACAO.equals(estadoAntes)) {
				throw new ConflitoException("Só se pode cancelar um micro ciclo em aprovação ou publicado.");
			}
			if (semanaTerminou(mc.getDataInicio())) {
				throw new ConflitoException("Esta semana já terminou.");
			}
			// as sessoes antes do cancelamento: as visiveis ao atleta (publicado) ou as dos treinadores (em aprovacao)
			List<Treino> sessoesAntes = sessoesDe(conn, tenant, parmIdMicroCiclo);
			PreparedStatement psMc = conn.prepareStatement("update micro_ciclo set estado = 'CANCELADO', "
					+ "motivo_cancelamento = ?, estado_antes_cancelamento = ? where id = ? and tenant_id = ? and estado = ?");
			psMc.setString(1, motivo);
			psMc.setString(2, estadoAntes);
			psMc.setInt(3, parmIdMicroCiclo);
			psMc.setInt(4, tenant);
			psMc.setString(5, estadoAntes);
			if (psMc.executeUpdate() == 0) {
				throw new ConflitoException("Só se pode cancelar um micro ciclo em aprovação ou publicado.");
			}
			// v4 (W1): origem MICRO_CICLO, para a reativacao saber quais voltam
			PreparedStatement psTreinos = conn.prepareStatement("update treino set estado = 'CANCELADO', motivo_estado = ?, "
					+ "origem_cancelamento = 'MICRO_CICLO', versao = versao + 1 where id_micro_ciclo = ? and estado <> 'CANCELADO' "
					+ "and not (estado = 'APROVADO' and id_presenca is not null) and id_treino_principal is null");
			psTreinos.setString(1, motivo);
			psTreinos.setInt(2, parmIdMicroCiclo);
			psTreinos.executeUpdate();
			// v6 (§1.3): os fisicos ligados seguem o principal (um realizado protege o seu fisico)
			propagaSessao(conn, tenant, null, parmIdMicroCiclo);
			// v8 (Y5, Q129): sem confirmacoes nos cancelados
			try (PreparedStatement psConfirmacoes = conn.prepareStatement("delete c from treino_confirmacao c "
					+ "inner join treino t on t.id = c.id_treino inner join micro_ciclo m on m.id = t.id_micro_ciclo "
					+ "where m.id = ? and m.tenant_id = ? and t.estado = 'CANCELADO'")) {
				psConfirmacoes.setInt(1, parmIdMicroCiclo);
				psConfirmacoes.setInt(2, tenant);
				psConfirmacoes.executeUpdate();
			}
			historico(conn, parmIdMicroCiclo, null, parmIdentidade.getIdUtilizador(), "Micro ciclo cancelado | " + motivo);
			if (PUBLICADO.equals(estadoAntes)) {
				// R257, Q139: uma por atleta com sessoes visiveis (as aprovadas antes do cancelamento)
				List<Treino> visiveis = new ArrayList<>();
				for (Treino t : sessoesAntes) {
					if (APROVADO.equals(t.estadoBD) && !t.evento()) {
						visiveis.add(t);
					}
				}
				notificaAtletasDaSemana(conn, parmIdentidade, visiveis, "MICRO_CICLO_CANCELADO",
						k -> "O micro ciclo da semana de " + ddmm(mc.getDataInicio()) + " foi cancelado");
			} else {
				// R257: em aprovacao, so os treinadores (agregada por destinatario)
				Map<Integer, List<Integer>> treinosPorEquipa = new LinkedHashMap<>();
				for (Treino t : sessoesAntes) {
					if (!t.evento() && !CANCELADO.equals(t.estadoBD)) {
						treinosPorEquipa.computeIfAbsent(t.d.getIdEquipa(), k -> new ArrayList<>()).add(t.d.getId());
					}
				}
				notificaAgregada(conn, parmIdentidade, treinosPorEquipa, "MICRO_CICLO_CANCELADO",
						k -> "Micro ciclo de " + ddmm(mc.getDataInicio()) + " cancelado");
			}
			return mc.getDataInicio();
		});
		return getSemana(parmIdentidade, segunda);
	}

	public boolean eliminaMicroCiclo(Identidade parmIdentidade, int parmIdMicroCiclo) {
		int tenant = parmIdentidade.getTenantId();
		return emTransacao("eliminaMicroCiclo", conn -> {
			MicroCicloData mc = carregaMicroCiclo(conn, parmIdMicroCiclo, tenant);
			if (!RASCUNHO.equals(mc.getEstado())) {
				throw new ConflitoException("Só se pode eliminar um micro ciclo em rascunho.");
			}
			// revisao I3: o historico sai primeiro, para nao depender da cascata dupla (micro_ciclo e treino)
			PreparedStatement psHistorico = conn
					.prepareStatement("delete from micro_ciclo_historico where id_micro_ciclo = ?");
			psHistorico.setInt(1, parmIdMicroCiclo);
			psHistorico.executeUpdate();
			PreparedStatement preparedStatement = conn.prepareStatement(
					"delete from micro_ciclo where id = ? and tenant_id = ? and estado = 'RASCUNHO'");
			preparedStatement.setInt(1, parmIdMicroCiclo);
			preparedStatement.setInt(2, tenant);
			if (preparedStatement.executeUpdate() == 0) {
				throw new ConflitoException("Só se pode eliminar um micro ciclo em rascunho.");
			}
			return true;
		});
	}

	// ==================== 6-7. gravar / eliminar treino ====================

	/** Validacao (400) e normalizacao dos campos do treino (R2, R3). */
	private static void validaTreino(TreinoData v) {
		boolean evento = v.getIdGrupo() > 0;
		String tipo = evento ? "OUTRO" : v.getTipo() == null ? null : v.getTipo().trim().toUpperCase();
		if (tipo == null || !TIPOS.contains(tipo)) {
			throw new PedidoInvalidoException("Tipo de treino inválido.");
		}
		v.setTipo(tipo);
		if (evento) {
			// v5 (W1): evento = OUTRO sem descricao do tipo; o descritivo vai em sub_grupo; hora de fim opcional
			if (v.getIdEquipa() != 0) {
				throw new PedidoInvalidoException("Um evento não tem equipa.");
			}
			v.setTipoDescricao(null);
		} else {
			v.setAprovarAoPublicar(false);
			v.setTipoDescricao("OUTRO".equals(tipo) ? limpa(v.getTipoDescricao(), 50, "A descrição do tipo") : null);
			if ("OUTRO".equals(tipo) && v.getTipoDescricao() == null) {
				throw new PedidoInvalidoException("Indique a descrição do tipo.");
			}
		}
		String inicio = v.getHoraInicio() == null ? "" : v.getHoraInicio().trim();
		if (!HORA.matcher(inicio).matches()) {
			throw new PedidoInvalidoException("Hora de início inválida (HH:MM).");
		}
		v.setHoraInicio(inicio);
		String fim = limpa(v.getHoraFim(), 5, "A hora de fim");
		if (fim != null && !HORA.matcher(fim).matches()) {
			throw new PedidoInvalidoException("Hora de fim inválida (HH:MM).");
		}
		if (fim == null && "ESCALAO".equals(tipo)) {
			throw new PedidoInvalidoException("O treino de escalão tem de ter hora de fim.");
		}
		if (fim != null && minutos(fim) <= minutos(inicio)) {
			throw new PedidoInvalidoException("A hora de fim tem de ser depois da hora de início.");
		}
		v.setHoraFim(fim);
		v.setSubGrupo(limpa(v.getSubGrupo(), 50, "O sub-grupo"));
		v.setLocal(limpa(v.getLocal(), 100, "O local"));
		v.setObservacoes(limpa(v.getObservacoes(), 500, "As observações"));
		paraData(v.getData());
		if (!evento && v.getIdEquipa() <= 0) {
			throw new PedidoInvalidoException("Equipa inválida.");
		}
	}

	/** Micro ciclo nao cancelado nem so de leitura; data dentro da semana; inicio no futuro. */
	private static void validaPlaneamento(Connection conn, int parmTenantId, int parmDataInicio, String parmEstadoMc,
			int parmIdEpoca, TreinoData v) throws SQLException {
		if (CANCELADO.equals(parmEstadoMc)) {
			throw new ConflitoException("O micro ciclo está cancelado.");
		}
		Integer epoca = epocaAtiva(conn, parmTenantId);
		if (semanaTerminou(parmDataInicio) || epoca == null || epoca != parmIdEpoca) {
			throw new ConflitoException("Esta semana é só de leitura.");
		}
		if (v.getData() < parmDataInicio || v.getData() > somaDias(parmDataInicio, 6)) {
			throw new PedidoInvalidoException("A data tem de estar na semana do micro ciclo.");
		}
		if (inicioPassou(v.getData(), v.getHoraInicio())) {
			throw new ConflitoException("Não se pode planear um treino para uma hora que já passou.");
		}
	}

	/** v6 (X13): maximo de fisicos ligados por treino principal. */
	private static final int MAXIMO_FISICOS = 4;

	private static boolean temPrincipal(TreinoData v) {
		return v.getIdTreinoPrincipal() != null && v.getIdTreinoPrincipal() > 0;
	}

	/** v6 (R161, X13): fisicos so num ESCALAO de equipa; idTreinoPrincipal so num FISICO. Depois de validaTreino. */
	private static void validaFisicos(TreinoData v) {
		boolean escalaoEquipa = "ESCALAO".equals(v.getTipo()) && v.getIdGrupo() <= 0;
		if (v.getFisicos() != null && !v.getFisicos().isEmpty() && !escalaoEquipa) {
			throw new PedidoInvalidoException("Só um treino de escalão de uma equipa pode ter treino físico.");
		}
		if (temPrincipal(v) && !"FISICO".equals(v.getTipo())) {
			throw new PedidoInvalidoException("Só um treino físico pode ser ligado a um treino principal.");
		}
		if (v.getFisicos() == null) {
			return;
		}
		if (v.getFisicos().size() > MAXIMO_FISICOS) {
			throw new PedidoInvalidoException("No máximo " + MAXIMO_FISICOS + " treinos físicos por treino.");
		}
		Set<Integer> ids = new HashSet<>();
		for (FisicoStaffData f : v.getFisicos()) {
			if (f == null || (f.getId() > 0 && !ids.add(f.getId()))) {
				throw new PedidoInvalidoException("Treino físico inválido.");
			}
			String inicio = f.getHoraInicio() == null ? "" : f.getHoraInicio().trim();
			if (!HORA.matcher(inicio).matches()) {
				throw new PedidoInvalidoException("Hora de início do físico inválida (HH:MM).");
			}
			f.setHoraInicio(inicio);
			String fim = limpa(f.getHoraFim(), 5, "A hora de fim do físico");
			if (fim != null && !HORA.matcher(fim).matches()) {
				throw new PedidoInvalidoException("Hora de fim do físico inválida (HH:MM).");
			}
			if (fim != null && minutos(fim) <= minutos(inicio)) {
				throw new PedidoInvalidoException("A hora de fim do físico tem de ser depois da hora de início.");
			}
			f.setHoraFim(fim);
			f.setLocal(limpa(f.getLocal(), 100, "O local do físico"));
			f.setSubGrupo(limpa(f.getSubGrupo(), 50, "O sub-grupo do físico"));
			f.setPosicao(null); // so resposta
		}
	}

	/** v6 (R10, R182): o fisico nao pode ficar numa hora que ja passou (na data do principal). */
	private static void exigeHoraFisico(int parmData, FisicoData f) {
		if (inicioPassou(parmData, f.getHoraInicio())) {
			throw new ConflitoException("O físico das " + f.getHoraInicio() + " ficaria numa hora que já passou.");
		}
	}

	public TreinoData gravaTreino(Identidade parmIdentidade, TreinoData v) {
		validaTreino(v);
		validaFisicos(v);
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		boolean evento = v.getIdGrupo() > 0;
		Trabalho<Integer> grava = conn -> {
			String nomeGrupo = null;
			if (evento) {
				nomeGrupo = validaGrupo(conn, tenant, v.getIdGrupo(), v.getId() <= 0);
			} else {
				PreparedStatement psEquipa = conn.prepareStatement("select 1 from escalao_epoca ee inner join epoca e "
						+ "on e.id = ee.id_epoca where ee.id = ? and e.tenant_id = ? and e.estado = '1'");
				psEquipa.setInt(1, v.getIdEquipa());
				psEquipa.setInt(2, tenant);
				if (!psEquipa.executeQuery().next()) {
					throw new PedidoInvalidoException("Equipa inválida.");
				}
			}

			if (temPrincipal(v)) {
				return ligaFisico(conn, parmIdentidade, v); // v6 (R163): a resposta e o principal
			}

			if (v.getId() <= 0) {
				MicroCicloData mc = v.getIdMicroCiclo() > 0 ? carregaMicroCiclo(conn, v.getIdMicroCiclo(), tenant)
						: microCicloDaSemana(conn, tenant, segundaDe(v.getData()));
				if (mc == null) {
					throw new ConflitoException("Não há micro ciclo nesta semana.");
				}
				validaPlaneamento(conn, tenant, mc.getDataInicio(), mc.getEstado(), mc.getIdEpoca(), v);
				if (evento) {
					return criaEvento(conn, mc, v, nomeGrupo, utilizador);
				}
				// v6: os fisicos criados com o principal (novos: id 0)
				List<FisicoStaffData> fisicos = v.getFisicos() == null ? new ArrayList<>() : v.getFisicos();
				for (FisicoStaffData f : fisicos) {
					if (f.getId() > 0) {
						throw new PedidoInvalidoException("Treino físico inválido.");
					}
					exigeHoraFisico(v.getData(), f);
				}
				// Q8: num micro ciclo publicado o treino fica logo Pendente
				String estado = RASCUNHO.equals(mc.getEstado()) ? RASCUNHO : PENDENTE;
				int id = insereTreino(conn, mc.getId(), v, estado, utilizador);
				insereConvocatoriaOmissao(conn, id, v.getIdEquipa(), utilizador);
				for (FisicoStaffData f : fisicos) {
					insereFisico(conn, mc.getId(), id, v.getIdEquipa(), v.getData(), f, estado, utilizador);
				}
				if (!fisicos.isEmpty()) {
					propagaSessao(conn, tenant, id, null);
				}
				String nome = nomeEquipa(conn, v.getIdEquipa());
				historico(conn, mc.getId(), id, utilizador, "Treino criado | " + nome + " " + dia(v.getData()) + " " + intervalo(v)
						+ incluiFisicos(fisicos));
				if (PENDENTE.equals(estado)) {
					// v4 (R76, W7): treino novo num micro ciclo publicado
					notifica(conn, parmIdentidade, notificacaoHelper.destinatariosEquipa(conn, v.getIdEquipa(), tenant),
							"TREINO_NOVO", id, null, "Novo treino " + nome + " de " + dia(v.getData()) + " às "
									+ v.getHoraInicio() + " aguarda aprovação");
				}
				return id;
			}

			Treino t = carregaTreino(conn, v.getId(), tenant);
			if (t.ligado()) {
				throw new PedidoInvalidoException(msgFisicoLigado(conn, t, tenant));
			}
			return alteraTreino(conn, parmIdentidade, t, v, nomeGrupo);
		};
		// v8 (Y3): na mesma transacao, avisa as confirmacoes caducadas da sessao gravada (principal ou avulso)
		int idTreino = emTransacao("gravaTreino", conn -> {
			int id = grava.executa(conn);
			// Balnearios (B3, R15): so quando o pedido traz idsBalnearios; nao mexe em versao, estado nem notificacoes
			if (v.getIdsBalnearios() != null && !temPrincipal(v)) {
				BalnearioHelper.gravaTreino(conn, parmIdentidade, carregaTreino(conn, id, tenant), v.getIdsBalnearios(),
						v.getIdsBalneariosAntes());
			}
			sincronizaTreino(conn, tenant, id, null); // Indisponibilidades (Z3)
			avisaAtletas(conn, parmIdentidade, id, MotivoAviso.LOCAL, Set.of());
			return id;
		});
		return getTreinoData(parmIdentidade, idTreino);
	}

	/** v6 (X5): "Este físico faz parte do treino {equipa} {hora}: altere-o no treino principal." */
	private String msgFisicoLigado(Connection conn, Treino t, int parmTenantId) throws SQLException {
		Treino p = carregaTreino(conn, t.idPrincipal, parmTenantId);
		return "Este físico faz parte do treino " + p.d.getNomeEquipa() + " " + p.d.getHoraInicio()
				+ ": altere-o no treino principal.";
	}

	/** Edicao de um treino ou evento existente (nao ligado). v6: sincroniza os fisicos quando v.fisicos != null. */
	private int alteraTreino(Connection conn, Identidade parmIdentidade, Treino t, TreinoData v, String nomeGrupo)
			throws SQLException {
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		boolean evento = v.getIdGrupo() > 0;
		if (v.getVersao() != t.d.getVersao()) {
			throw new ConflitoException(MSG_VERSAO);
		}
		if (CANCELADO.equals(t.estadoBD) || t.realizado()) {
			throw new ConflitoException("Um treino cancelado ou realizado não pode ser alterado.");
		}
		if (inicioPassou(t.d.getData(), t.inicioSessao())) {
			throw new ConflitoException(MSG_JA_COMECOU);
		}
		if (t.evento() != evento) {
			throw new PedidoInvalidoException("Não se pode converter um treino num evento, nem um evento num treino.");
		}
		if (evento && v.getIdGrupo() != t.d.getIdGrupo()) {
			validaGrupo(conn, tenant, v.getIdGrupo(), true); // mudar para outro grupo: so ativos
		}
		validaPlaneamento(conn, tenant, t.dataInicio, t.mcEstado, t.idEpoca, v);
		if (evento) {
			return alteraEvento(conn, t, v, nomeGrupo, utilizador);
		}

		// v6: fisicos atuais; um ESCALAO com fisicos nao muda de tipo
		List<FisicoStaffData> existentes = fisicosDe(conn, tenant, t.d.getId());
		if (!existentes.isEmpty() && !"ESCALAO".equals(v.getTipo())) {
			throw new PedidoInvalidoException("Um treino com físico tem de ser de escalão.");
		}
		// R10, R182: cada fisico (os do pedido ou, sem a lista, os atuais) na data nova
		for (FisicoStaffData f : v.getFisicos() != null ? v.getFisicos() : existentes) {
			exigeHoraFisico(v.getData(), f);
		}

		List<String> diferencas = new ArrayList<>();
		boolean essencial = false;
		boolean mudouEquipa = t.d.getIdEquipa() != v.getIdEquipa();
		if (t.d.getData() != v.getData()) {
			diferencas.add("Data | " + ddmm(t.d.getData()) + " => " + ddmm(v.getData()));
			essencial = true;
		}
		essencial |= diferenca(diferencas, "Hora de início", t.d.getHoraInicio(), v.getHoraInicio());
		essencial |= diferenca(diferencas, "Hora de fim", t.d.getHoraFim(), v.getHoraFim());
		if (mudouEquipa) {
			diferencas.add("Equipa | " + t.d.getNomeEquipa() + " => " + nomeEquipa(conn, v.getIdEquipa()));
			essencial = true;
		}
		essencial |= diferenca(diferencas, "Tipo", t.d.getTipo(), v.getTipo());
		essencial |= diferenca(diferencas, "Descrição do tipo", t.d.getTipoDescricao(), v.getTipoDescricao());
		diferenca(diferencas, "Sub-grupo", t.d.getSubGrupo(), v.getSubGrupo());
		diferenca(diferencas, "Local", t.d.getLocal(), v.getLocal());
		diferenca(diferencas, "Observações", t.d.getObservacoes(), v.getObservacoes());
		// v6 (R180, Q91): acrescentar, remover e mudar as horas dos fisicos sao essenciais; local e sub-grupo nao
		List<FisicoStaffData> novos = new ArrayList<>();
		List<FisicoStaffData> alterados = new ArrayList<>();
		List<FisicoStaffData> removidos = new ArrayList<>();
		if (v.getFisicos() != null) {
			essencial |= diferencasFisicos(existentes, v.getFisicos(), diferencas, novos, alterados, removidos);
		}
		if (diferencas.isEmpty()) {
			return t.d.getId();
		}
		aposAlteracaoPrincipal(conn, parmIdentidade, t, v, diferencas, essencial);
		sincronizaFisicos(conn, t, v, novos, alterados, removidos, utilizador);
		propagaSessao(conn, tenant, t.d.getId(), null); // equipa, data e estado seguem o principal
		return t.d.getId();
	}

	/** v6: diferencas dos fisicos (por id) para o historico; devolve se ha alteracao essencial. */
	private static boolean diferencasFisicos(List<FisicoStaffData> parmExistentes, List<FisicoStaffData> parmPedidos,
			List<String> parmDiferencas, List<FisicoStaffData> parmNovos, List<FisicoStaffData> parmAlterados,
			List<FisicoStaffData> parmRemovidos) {
		Map<Integer, FisicoStaffData> atuais = new LinkedHashMap<>();
		for (FisicoStaffData a : parmExistentes) {
			atuais.put(a.getId(), a);
		}
		boolean essencial = false;
		for (FisicoStaffData f : parmPedidos) {
			if (f.getId() <= 0) {
				parmNovos.add(f);
				parmDiferencas.add("Físico | + " + intervalo(f));
				essencial = true;
				continue;
			}
			FisicoStaffData a = atuais.remove(f.getId());
			if (a == null) {
				throw new PedidoInvalidoException("Treino físico inválido: " + f.getId());
			}
			boolean mudou = false;
			if (!Objects.equals(a.getHoraInicio(), f.getHoraInicio()) || !Objects.equals(a.getHoraFim(), f.getHoraFim())) {
				parmDiferencas.add("Físico | " + intervalo(a) + " => " + intervalo(f));
				essencial = true;
				mudou = true;
			}
			mudou |= diferenca(parmDiferencas, "Físico " + intervalo(a) + " | Local", a.getLocal(), f.getLocal());
			mudou |= diferenca(parmDiferencas, "Físico " + intervalo(a) + " | Sub-grupo", a.getSubGrupo(), f.getSubGrupo());
			if (mudou) {
				parmAlterados.add(f);
			}
		}
		for (FisicoStaffData a : atuais.values()) {
			parmRemovidos.add(a);
			parmDiferencas.add("Físico | − " + intervalo(a));
			essencial = true;
		}
		return essencial;
	}

	/** v6: grava os fisicos novos, alterados e removidos do principal (sem convocatoria propria). */
	private static void sincronizaFisicos(Connection conn, Treino t, TreinoData v, List<FisicoStaffData> parmNovos,
			List<FisicoStaffData> parmAlterados, List<FisicoStaffData> parmRemovidos, int parmIdUtilizador) throws SQLException {
		for (FisicoStaffData f : parmNovos) {
			insereFisico(conn, t.d.getIdMicroCiclo(), t.d.getId(), v.getIdEquipa(), v.getData(), f, t.estadoBD,
					parmIdUtilizador);
		}
		try (PreparedStatement psAltera = conn.prepareStatement("update treino set hora_inicio = ?, hora_fim = ?, "
				+ "local_treino = ?, sub_grupo = ?, versao = versao + 1 where id = ? and id_treino_principal = ?")) {
			for (FisicoStaffData f : parmAlterados) {
				psAltera.setString(1, f.getHoraInicio());
				psAltera.setString(2, f.getHoraFim());
				psAltera.setString(3, f.getLocal());
				psAltera.setString(4, f.getSubGrupo());
				psAltera.setInt(5, f.getId());
				psAltera.setInt(6, t.d.getId());
				psAltera.executeUpdate();
			}
		}
		try (PreparedStatement psApaga = conn
				.prepareStatement("delete from treino where id = ? and id_treino_principal = ?")) {
			for (FisicoStaffData f : parmRemovidos) {
				psApaga.setInt(1, f.getId());
				psApaga.setInt(2, t.d.getId());
				psApaga.executeUpdate();
			}
		}
	}

	/**
	 * Depois das diferencas de um treino (nao evento): update com o estado novo (Q10, R73), convocatoria e
	 * ausencias, historico e notificacoes (W7). v6: tambem na ligacao de um fisico avulso.
	 */
	private void aposAlteracaoPrincipal(Connection conn, Identidade parmIdentidade, Treino t, TreinoData v,
			List<String> diferencas, boolean essencial) throws SQLException {
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		boolean mudouEquipa = t.d.getIdEquipa() != v.getIdEquipa();
		// Q10, R8.4: campo essencial num Aprovado volta a Pendente
		boolean voltaPendente = essencial && APROVADO.equals(t.estadoBD);
		// v4 (R73, Q48): um Rejeitado alterado volta logo a Pendente, sem o motivo da rejeicao
		boolean corrigido = REJEITADO.equals(t.estadoBD);
		String novoEstado = voltaPendente || corrigido ? PENDENTE : t.estadoBD;
		PreparedStatement psUpdate = conn.prepareStatement("update treino set id_equipa = ?, tipo = ?, "
				+ "tipo_descricao = ?, sub_grupo = ?, `data` = ?, hora_inicio = ?, hora_fim = ?, local_treino = ?, "
				+ "observacoes = ?, estado = ?, motivo_estado = " + (corrigido ? "null" : "motivo_estado")
				+ ", versao = versao + 1 where id = ? and versao = ?");
		psUpdate.setInt(1, v.getIdEquipa());
		psUpdate.setString(2, v.getTipo());
		psUpdate.setString(3, v.getTipoDescricao());
		psUpdate.setString(4, v.getSubGrupo());
		psUpdate.setInt(5, v.getData());
		psUpdate.setString(6, v.getHoraInicio());
		psUpdate.setString(7, v.getHoraFim());
		psUpdate.setString(8, v.getLocal());
		psUpdate.setString(9, v.getObservacoes());
		psUpdate.setString(10, novoEstado);
		psUpdate.setInt(11, t.d.getId());
		psUpdate.setInt(12, t.d.getVersao());
		if (psUpdate.executeUpdate() == 0) {
			throw new ConflitoException(MSG_VERSAO);
		}

		if (mudouEquipa) {
			PreparedStatement psApaga = conn.prepareStatement("delete from treino_convocado where id_treino = ?");
			psApaga.setInt(1, t.d.getId());
			psApaga.executeUpdate();
			apagaExcluidosDoPrincipal(conn, t.d.getId(), null); // v7 (X2): nova equipa, todos fazem os fisicos
			apagaConfirmacoes(conn, t.d.getId(), null); // v8 (Y5)
			insereConvocatoriaOmissao(conn, t.d.getId(), v.getIdEquipa(), utilizador);
			PreparedStatement psDescarta = conn.prepareStatement("update treino_ausencia set estado = 'DESCARTADA', "
					+ "data_alteracao = now() where id_treino = ? and estado = 'ATIVA'");
			psDescarta.setInt(1, t.d.getId());
			psDescarta.executeUpdate();
		} else if (voltaPendente || (corrigido && essencial)) {
			// R40: a ausencia mantem-se, assinalada, ate o atleta confirmar
			PreparedStatement psPendente = conn.prepareStatement("update treino_ausencia set pendente_confirmacao = '1', "
					+ "data_alteracao = now() where id_treino = ? and estado = 'ATIVA' and id_indisponibilidade is null");
			psPendente.setInt(1, t.d.getId());
			psPendente.executeUpdate();
		}
		for (String diferenca : diferencas) {
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), utilizador, diferenca);
		}
		// W7: no maximo uma notificacao por destinatario e gravacao (ALTERADO > CORRIGIDO > INFO)
		String nomeNovo = nomeEquipa(conn, v.getIdEquipa());
		String treinoTexto = "Treino " + nomeNovo + " de " + dia(v.getData()) + " às " + v.getHoraInicio();
		if (voltaPendente) {
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), utilizador, "Estado | APROVADO => PENDENTE");
			aposTreinoAlterado(conn, parmIdentidade, t, v);
		} else if (corrigido) {
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), utilizador, "Estado | REJEITADO => PENDENTE (corrigido)");
			notifica(conn, parmIdentidade, notificacaoHelper.destinatariosEquipa(conn, v.getIdEquipa(), tenant),
					"TREINO_CORRIGIDO", t.d.getId(), null, treinoTexto + " foi corrigido e aguarda aprovação");
		} else if (APROVADO.equals(t.estadoBD)) {
			// Q47 (a): alteracao so informativa; so o nome dos campos, sem valores
			List<String> campos = new ArrayList<>();
			for (String diferenca : diferencas) {
				campos.add(diferenca.substring(0, diferenca.indexOf(" | ")).toLowerCase());
			}
			notifica(conn, parmIdentidade, notificacaoHelper.destinatariosEquipa(conn, v.getIdEquipa(), tenant),
					"TREINO_INFO", t.d.getId(), null,
					treinoTexto + " alterado (sem nova aprovação): " + String.join(", ", campos));
		}
		if (mudouEquipa && !PENDENTE.equals(t.estadoBD) && !RASCUNHO.equals(t.estadoBD)) {
			// [O] equipa anterior, sem quem tambem e da nova (esses ja foram notificados acima)
			List<Integer> anteriores = new ArrayList<>(
					notificacaoHelper.destinatariosEquipa(conn, t.d.getIdEquipa(), tenant));
			anteriores.removeAll(notificacaoHelper.destinatariosEquipa(conn, v.getIdEquipa(), tenant));
			notifica(conn, parmIdentidade, anteriores, "TREINO_MUDOU_EQUIPA", t.d.getId(), null, "Treino "
					+ t.d.getNomeEquipa() + " de " + dia(t.d.getData()) + " às " + t.d.getHoraInicio()
					+ " passou para " + nomeNovo);
		}
	}

	/** v6: o principal tal como esta gravado, para as alteracoes que so mexem nos fisicos. */
	private static TreinoData copiaPrincipal(TreinoData d) {
		TreinoData v = new TreinoData();
		v.setId(d.getId());
		v.setIdMicroCiclo(d.getIdMicroCiclo());
		v.setIdEquipa(d.getIdEquipa());
		v.setTipo(d.getTipo());
		v.setTipoDescricao(d.getTipoDescricao());
		v.setSubGrupo(d.getSubGrupo());
		v.setData(d.getData());
		v.setHoraInicio(d.getHoraInicio());
		v.setHoraFim(d.getHoraFim());
		v.setLocal(d.getLocal());
		v.setObservacoes(d.getObservacoes());
		v.setVersao(d.getVersao());
		return v;
	}

	/**
	 * v6 (R163, X4 b, X11): FISICO com idTreinoPrincipal. id 0 cria um fisico ligado (como acrescentar no
	 * editor do principal); id > 0 liga um avulso (convocatoria propria apagada; ausencias ativas descartadas
	 * so com descartarAusencias). Devolve o id do principal.
	 */
	private int ligaFisico(Connection conn, Identidade parmIdentidade, TreinoData v) throws SQLException {
		int tenant = parmIdentidade.getTenantId();
		Treino p = carregaTreino(conn, v.getIdTreinoPrincipal(), tenant); // 403 de outro tenant (CA129)
		Treino avulso = v.getId() > 0 ? carregaTreino(conn, v.getId(), tenant) : null;
		int idMicroCiclo;
		if (avulso != null) {
			idMicroCiclo = avulso.d.getIdMicroCiclo();
		} else {
			MicroCicloData mc = v.getIdMicroCiclo() > 0 ? carregaMicroCiclo(conn, v.getIdMicroCiclo(), tenant)
					: microCicloDaSemana(conn, tenant, segundaDe(v.getData()));
			if (mc == null) {
				throw new ConflitoException("Não há micro ciclo nesta semana.");
			}
			idMicroCiclo = mc.getId();
		}
		if (!"ESCALAO".equals(p.d.getTipo()) || p.evento() || p.ligado()) {
			throw new PedidoInvalidoException("O treino principal tem de ser um treino de escalão de uma equipa.");
		}
		if (p.d.getIdEquipa() != v.getIdEquipa()) {
			throw new PedidoInvalidoException("O treino principal tem de ser da mesma equipa.");
		}
		if (p.d.getData() != v.getData()) {
			throw new PedidoInvalidoException("O treino principal tem de ser do mesmo dia.");
		}
		if (p.d.getIdMicroCiclo() != idMicroCiclo) {
			throw new PedidoInvalidoException("O treino principal tem de ser do mesmo micro ciclo.");
		}
		if (CANCELADO.equals(p.estadoBD) || p.realizado()) {
			throw new PedidoInvalidoException("O treino principal está cancelado ou realizado.");
		}
		if (inicioPassou(p.d.getData(), p.inicioSessao())) {
			throw new PedidoInvalidoException("O treino principal já começou.");
		}
		FisicoStaffData f = new FisicoStaffData();
		f.setHoraInicio(v.getHoraInicio());
		f.setHoraFim(v.getHoraFim());
		f.setLocal(v.getLocal());
		f.setSubGrupo(v.getSubGrupo());
		List<FisicoStaffData> existentes = fisicosDe(conn, tenant, p.d.getId());
		if (existentes.size() >= MAXIMO_FISICOS) {
			throw new PedidoInvalidoException("No máximo " + MAXIMO_FISICOS + " treinos físicos por treino.");
		}
		TreinoData vp = copiaPrincipal(p.d);
		if (avulso == null) {
			// id 0: o mesmo que acrescentar um fisico ao principal
			List<FisicoStaffData> lista = new ArrayList<>(existentes);
			lista.add(f);
			vp.setFisicos(lista);
			return alteraTreino(conn, parmIdentidade, p, vp, null);
		}

		// id > 0: ligar um avulso existente
		if (avulso.ligado()) {
			throw new PedidoInvalidoException(msgFisicoLigado(conn, avulso, tenant));
		}
		if (!"FISICO".equals(avulso.d.getTipo()) || avulso.evento()) {
			throw new PedidoInvalidoException("Só um treino físico pode ser ligado a um treino principal.");
		}
		if (v.getVersao() != avulso.d.getVersao()) {
			throw new ConflitoException(MSG_VERSAO);
		}
		if (CANCELADO.equals(avulso.estadoBD) || avulso.realizado()) {
			throw new ConflitoException("Um físico cancelado ou realizado não pode ser ligado.");
		}
		int ausencias = ausenciasAtivas(conn, avulso.d.getId());
		if (ausencias > 0 && !Boolean.TRUE.equals(v.getDescartarAusencias())) {
			throw new ConflitoException(ausencias == 1
					? "1 ausência deste físico é descartada; vale o 'Não vou' do treino principal."
					: ausencias + " ausências deste físico são descartadas; vale o 'Não vou' do treino principal.");
		}
		validaPlaneamento(conn, tenant, p.dataInicio, p.mcEstado, p.idEpoca, vp);
		exigeHoraFisico(p.d.getData(), f);
		try (PreparedStatement psConvocados = conn.prepareStatement("delete from treino_convocado where id_treino = ?")) {
			psConvocados.setInt(1, avulso.d.getId());
			psConvocados.executeUpdate();
		}
		try (PreparedStatement psDescarta = conn.prepareStatement("update treino_ausencia set estado = 'DESCARTADA', "
				+ "data_alteracao = now() where id_treino = ? and estado = 'ATIVA'")) {
			psDescarta.setInt(1, avulso.d.getId());
			psDescarta.executeUpdate();
		}
		try (PreparedStatement psLiga = conn.prepareStatement("update treino set id_treino_principal = ?, "
				+ "hora_inicio = ?, hora_fim = ?, local_treino = ?, sub_grupo = ?, versao = versao + 1 "
				+ "where id = ? and versao = ? and id_treino_principal is null")) {
			psLiga.setInt(1, p.d.getId());
			psLiga.setString(2, f.getHoraInicio());
			psLiga.setString(3, f.getHoraFim());
			psLiga.setString(4, f.getLocal());
			psLiga.setString(5, f.getSubGrupo());
			psLiga.setInt(6, avulso.d.getId());
			psLiga.setInt(7, avulso.d.getVersao());
			if (psLiga.executeUpdate() == 0) {
				throw new ConflitoException(MSG_VERSAO);
			}
		}
		BalnearioHelper.apagaDoTreino(conn, avulso, parmIdentidade.getIdUtilizador()); // Balnearios (B14)
		List<String> diferencas = new ArrayList<>();
		diferencas.add("Físico | + " + intervalo(f) + " (era avulso)");
		aposAlteracaoPrincipal(conn, parmIdentidade, p, vp, diferencas, true);
		propagaSessao(conn, tenant, p.d.getId(), null);
		return p.d.getId();
	}

	/** Q31 (BK-12): treino aprovado que volta a Pendente, aos destinatarios da equipa (nova). */
	private void aposTreinoAlterado(Connection conn, Identidade parmIdentidade, Treino t, TreinoData v)
			throws SQLException {
		notifica(conn, parmIdentidade, notificacaoHelper.destinatariosEquipa(conn, v.getIdEquipa(),
				parmIdentidade.getTenantId()), "TREINO_ALTERADO", t.d.getId(), null, "Treino " + nomeEquipa(conn,
						v.getIdEquipa()) + " de " + dia(v.getData()) + " às " + v.getHoraInicio()
						+ " foi alterado e aguarda nova aprovação");
	}

	// ==================== v5: eventos do clube (BK-v5-1) ====================

	/**
	 * Grupo do tenant (400 se nao existir). Com parmExigeAtivo, recusa um grupo inativo
	 * (criacao ou troca de grupo); na edicao aceita-se o grupo atual mesmo inativo.
	 *
	 * @return o nome do grupo
	 */
	private static String validaGrupo(Connection conn, int parmTenantId, int parmIdGrupo, boolean parmExigeAtivo)
			throws SQLException {
		try (PreparedStatement preparedStatement = conn
				.prepareStatement("select nome, estado from grupo_evento where id = ? and tenant_id = ?")) {
			preparedStatement.setInt(1, parmIdGrupo);
			preparedStatement.setInt(2, parmTenantId);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				if (!rs.next()) {
					throw new PedidoInvalidoException("Grupo inválido.");
				}
				if (parmExigeAtivo && !"1".equals(rs.getString("estado"))) {
					throw new PedidoInvalidoException("O grupo está inativo.");
				}
				return rs.getString("nome");
			}
		}
	}

	/** W15: aprovacao do evento pelo ADMIN (ao criar num publicado, ao copiar ou ao publicar). */
	private static void marcaAprovadoAdmin(Connection conn, int parmIdTreino, int parmIdMicroCiclo, int parmIdUtilizador)
			throws SQLException {
		try (PreparedStatement preparedStatement = conn.prepareStatement("update treino set estado = 'APROVADO', "
				+ "motivo_estado = null, data_aprovacao = now(), id_utilizador_aprovacao = ? where id = ?")) {
			preparedStatement.setInt(1, parmIdUtilizador);
			preparedStatement.setInt(2, parmIdTreino);
			preparedStatement.executeUpdate();
		}
		historico(conn, parmIdMicroCiclo, parmIdTreino, parmIdUtilizador, "Aprovado (" + TEXTO_ADMINISTRADOR + ")");
	}

	/** Estado de um evento novo: rascunho, ou num publicado Aprovado ("Ja aprovado") ou Pendente. */
	private static String estadoEventoNovo(MicroCicloData parmMc, TreinoData v) {
		return RASCUNHO.equals(parmMc.getEstado()) ? RASCUNHO : v.isAprovarAoPublicar() ? APROVADO : PENDENTE;
	}

	/** Evento novo: sem convocatoria e sem notificacoes (R128). */
	private static int criaEvento(Connection conn, MicroCicloData parmMc, TreinoData v, String parmNomeGrupo,
			int parmIdUtilizador) throws SQLException {
		String estado = estadoEventoNovo(parmMc, v);
		int id = insereTreino(conn, parmMc.getId(), v, APROVADO.equals(estado) ? PENDENTE : estado, parmIdUtilizador);
		historico(conn, parmMc.getId(), id, parmIdUtilizador,
				"Evento criado | " + parmNomeGrupo + " " + dia(v.getData()) + " " + intervalo(v));
		if (APROVADO.equals(estado)) {
			marcaAprovadoAdmin(conn, id, parmMc.getId(), parmIdUtilizador);
		}
		return id;
	}

	/**
	 * Evento alterado (R126): um Aprovado continua Aprovado mesmo numa alteracao essencial; um
	 * Rejeitado volta a Pendente. Sem convocatoria, ausencias nem notificacoes.
	 */
	private static int alteraEvento(Connection conn, Treino t, TreinoData v, String parmNomeGrupo, int parmIdUtilizador)
			throws SQLException {
		List<String> diferencas = new ArrayList<>();
		if (t.d.getData() != v.getData()) {
			diferencas.add("Data | " + ddmm(t.d.getData()) + " => " + ddmm(v.getData()));
		}
		diferenca(diferencas, "Hora de início", t.d.getHoraInicio(), v.getHoraInicio());
		diferenca(diferencas, "Hora de fim", t.d.getHoraFim(), v.getHoraFim());
		if (t.d.getIdGrupo() != v.getIdGrupo()) {
			diferencas.add("Grupo | " + t.d.getNomeGrupo() + " => " + nomeGrupoAtual(conn, v.getIdGrupo()));
		}
		diferenca(diferencas, "Descritivo", t.d.getSubGrupo(), v.getSubGrupo());
		diferenca(diferencas, "Local", t.d.getLocal(), v.getLocal());
		diferenca(diferencas, "Observações", t.d.getObservacoes(), v.getObservacoes());
		diferenca(diferencas, "Já aprovado", t.d.isAprovarAoPublicar() ? "sim" : "não", v.isAprovarAoPublicar() ? "sim" : "não");
		if (diferencas.isEmpty()) {
			return t.d.getId();
		}
		boolean corrigido = REJEITADO.equals(t.estadoBD);
		try (PreparedStatement psUpdate = conn.prepareStatement("update treino set id_grupo = ?, sub_grupo = ?, "
				+ "`data` = ?, hora_inicio = ?, hora_fim = ?, local_treino = ?, observacoes = ?, aprovar_ao_publicar = ?, "
				+ "estado = ?, motivo_estado = " + (corrigido ? "null" : "motivo_estado")
				+ ", versao = versao + 1 where id = ? and versao = ?")) {
			psUpdate.setInt(1, v.getIdGrupo());
			psUpdate.setString(2, v.getSubGrupo());
			psUpdate.setInt(3, v.getData());
			psUpdate.setString(4, v.getHoraInicio());
			psUpdate.setString(5, v.getHoraFim());
			psUpdate.setString(6, v.getLocal());
			psUpdate.setString(7, v.getObservacoes());
			psUpdate.setString(8, v.isAprovarAoPublicar() ? "1" : "0");
			psUpdate.setString(9, corrigido ? PENDENTE : t.estadoBD);
			psUpdate.setInt(10, t.d.getId());
			psUpdate.setInt(11, t.d.getVersao());
			if (psUpdate.executeUpdate() == 0) {
				throw new ConflitoException(MSG_VERSAO);
			}
		}
		for (String diferenca : diferencas) {
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdUtilizador, diferenca);
		}
		if (corrigido) {
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdUtilizador, "Estado | REJEITADO => PENDENTE (corrigido)");
		}
		return t.d.getId();
	}

	private static String nomeGrupoAtual(Connection conn, int parmIdGrupo) throws SQLException {
		try (PreparedStatement preparedStatement = conn.prepareStatement("select nome from grupo_evento where id = ?")) {
			preparedStatement.setInt(1, parmIdGrupo);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				return rs.next() ? rs.getString(1) : "";
			}
		}
	}

	// ==================== v5: aprovacao em lote (BK-v5-3) ====================

	private static final int MAXIMO_LOTE = 200;
	private static final Pattern AMBITO = Pattern.compile("^(SEMANA|MEUS|EQUIPA:\\d{1,10})$");

	/**
	 * W5: aprova os itens pedidos (a lista que o utilizador viu), cada um na sua transacao. Nao
	 * alarga a lista; recusa por item o que nao pode aprovar. Sem notificacoes a atletas (R122).
	 */
	public sm.core.data.ResultadoAprovacaoLoteData aprovaLote(Identidade parmIdentidade, int parmIdMicroCiclo,
			sm.core.data.PedidoAprovacaoLoteData parmPedido) {
		if (!parmIdentidade.isAdmin() && !parmIdentidade.isTreinador()) {
			throw new AcessoNegadoException();
		}
		if (parmPedido == null || parmPedido.getItens() == null || parmPedido.getItens().isEmpty()) {
			throw new PedidoInvalidoException("Indique os treinos a aprovar.");
		}
		if (parmPedido.getItens().size() > MAXIMO_LOTE) {
			throw new PedidoInvalidoException("No máximo " + MAXIMO_LOTE + " treinos por lote.");
		}
		Set<Integer> ids = new HashSet<>();
		for (sm.core.data.PedidoAprovacaoLoteData.Item item : parmPedido.getItens()) {
			if (item == null || item.getId() == null || item.getVersao() == null || !ids.add(item.getId())) {
				throw new PedidoInvalidoException("Itens inválidos (id e versão obrigatórios, sem repetidos).");
			}
		}
		String ambito = parmPedido.getAmbito() == null ? "SEMANA" : parmPedido.getAmbito().trim().toUpperCase();
		if (!AMBITO.matcher(ambito).matches()) {
			throw new PedidoInvalidoException("Âmbito inválido.");
		}
		int tenant = parmIdentidade.getTenantId();
		MicroCicloData mc = leitura("aprovaLote", conn -> carregaMicroCiclo(conn, parmIdMicroCiclo, tenant));
		if (RASCUNHO.equals(mc.getEstado())) {
			throw new ConflitoException(MSG_SUBMETA);
		}
		// v9: aprova-se em aprovacao e depois de publicado
		if (!PUBLICADO.equals(mc.getEstado()) && !EM_APROVACAO.equals(mc.getEstado())) {
			throw new ConflitoException("O micro ciclo não está em aprovação nem publicado.");
		}
		boolean prontoAntes = leitura("aprovaLote",
				conn -> EM_APROVACAO.equals(mc.getEstado()) && pronto(conn, parmIdMicroCiclo)); // v9 (R248)

		sm.core.data.ResultadoAprovacaoLoteData resultado = new sm.core.data.ResultadoAprovacaoLoteData();
		for (sm.core.data.PedidoAprovacaoLoteData.Item item : parmPedido.getItens()) {
			try {
				emTransacao("aprovaLote", conn -> aprovaUm(conn, parmIdentidade, parmIdMicroCiclo, item.getId(),
						item.getVersao()));
				resultado.getAprovados().add(item.getId());
			} catch (ConflitoException e) {
				resultado.getFalhas().add(falhaLote(item.getId(), e.getMessage()));
			} catch (IllegalStateException e) {
				resultado.getFalhas().add(falhaLote(item.getId(), "erro ao gravar"));
			}
		}
		int n = resultado.getAprovados().size();
		if (n > 0) {
			emTransacao("aprovaLote", conn -> {
				historico(conn, parmIdMicroCiclo, null, parmIdentidade.getIdUtilizador(),
						"Aprovação em lote: " + n + " treinos (" + ambito + ")");
				talvezPronto(conn, parmIdentidade, parmIdMicroCiclo, prontoAntes); // v9 (R248): uma vez no fim
				return true;
			});
		}
		log.info("MicroCicloHelper | aprovaLote | microCiclo:{} aprovados:{} falhas:{}", parmIdMicroCiclo, n,
				resultado.getFalhas().size());
		resultado.setSemana(getSemana(parmIdentidade, mc.getDataInicio()));
		return resultado;
	}

	private static Map<String, Object> falhaLote(int parmId, String parmMotivo) {
		Map<String, Object> falha = new LinkedHashMap<>();
		falha.put("id", parmId);
		falha.put("motivo", parmMotivo);
		return falha;
	}

	/**
	 * Um treino do lote: as regras de mudaEstado APROVAR (estado, inicio, permissao, versao,
	 * convocatoria e historico com a qualidade). Qualquer falha lanca ConflitoException com o
	 * motivo curto, para a transacao do item fazer rollback.
	 */
	private Boolean aprovaUm(Connection conn, Identidade parmIdentidade, int parmIdMicroCiclo, int parmIdTreino,
			int parmVersao) throws SQLException {
		Treino t;
		try {
			t = carregaTreino(conn, parmIdTreino, parmIdentidade.getTenantId());
		} catch (AcessoNegadoException e) {
			throw new ConflitoException("não pertence a esta semana");
		}
		if (t.d.getIdMicroCiclo() != parmIdMicroCiclo) {
			throw new ConflitoException("não pertence a esta semana");
		}
		if (t.ligado()) {
			throw new ConflitoException("faz parte de outro treino"); // v6 (X5)
		}
		if (!PENDENTE.equals(t.estadoBD)) {
			throw new ConflitoException("já não está pendente (" + t.d.getEstado().toLowerCase() + ")");
		}
		if (inicioPassou(t.d.getData(), t.inicioSessao())) {
			throw new ConflitoException("não aprovado a tempo");
		}
		Set<Integer> equipas = equipasDoUtilizador(conn, parmIdentidade.getIdUtilizador());
		if ((t.evento() && !parmIdentidade.isAdmin()) || !podeAgirNaEquipa(parmIdentidade, t.d.getIdEquipa(), equipas)) {
			throw new ConflitoException("sem permissão");
		}
		if (parmVersao != t.d.getVersao()) {
			throw new ConflitoException("alterado entretanto");
		}
		// D4: a convocatoria efetiva fica gravada ("fotografia")
		insereConvocatoriaOmissao(conn, t.d.getId(), t.d.getIdEquipa(), parmIdentidade.getIdUtilizador());
		try (PreparedStatement preparedStatement = conn.prepareStatement("update treino set estado = 'APROVADO', "
				+ "motivo_estado = null, data_aprovacao = now(), id_utilizador_aprovacao = ?, versao = versao + 1 "
				+ "where id = ? and versao = ? and estado = 'PENDENTE'")) {
			preparedStatement.setInt(1, parmIdentidade.getIdUtilizador());
			preparedStatement.setInt(2, t.d.getId());
			preparedStatement.setInt(3, t.d.getVersao());
			if (preparedStatement.executeUpdate() == 0) {
				throw new ConflitoException("alterado entretanto");
			}
		}
		sincronizaTreino(conn, parmIdentidade.getTenantId(), t.d.getId(), null); // Indisponibilidades (Z3)
		historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdentidade.getIdUtilizador(),
				"Aprovado (" + textoQualidade(qualidade(parmIdentidade, t.d.getIdEquipa(), equipas)) + ")"
						+ incluiFisicos(fisicosDe(conn, parmIdentidade.getTenantId(), t.d.getId())));
		propagaSessao(conn, parmIdentidade.getTenantId(), t.d.getId(), null);
		// v9 (Z5): "era visivel" = publicado e ja aprovado antes; so atua num micro ciclo publicado (tambem no aprovaLote)
		boolean eraVisivel = PUBLICADO.equals(t.mcEstado) && t.d.getDataAprovacao() != null;
		avisaAtletas(conn, parmIdentidade, t.d.getId(),
				eraVisivel ? MotivoAviso.APROVADO_DE_NOVO : MotivoAviso.APROVADO_NOVO, Set.of());
		return Boolean.TRUE;
	}

	// ==================== v5: lembrete de pendentes (BK-v5-7) ====================

	/**
	 * W12: pendentes das equipas da conta (utilizadores_escalao, epoca ativa), de micro ciclos
	 * publicados da semana atual e da seguinte, ainda por comecar. UTILIZADOR (e outros) recebem 0.
	 */
	public Map<String, Object> pendentesMeus(Identidade parmIdentidade) {
		Map<String, Object> resultado = new LinkedHashMap<>();
		List<Integer> ids = new ArrayList<>();
		Integer semanaMin = null;
		if (parmIdentidade.isAdmin() || parmIdentidade.isTreinador()) {
			int atual = segundaDe(hoje());
			List<int[]> linhas = leitura("pendentesMeus", conn -> {
				List<int[]> lista = new ArrayList<>();
				// v6 (R175, R176): por sessao (sem os fisicos ligados) e pelo inicio da sessao
				try (PreparedStatement preparedStatement = conn.prepareStatement("select t.id, t.`data`, "
						+ "least(t.hora_inicio, coalesce((select min(f.hora_inicio) from treino f "
						+ "where f.id_treino_principal = t.id), t.hora_inicio)) as hora_inicio, "
						+ "m.data_inicio from treino t inner join micro_ciclo m on m.id = t.id_micro_ciclo "
						+ "where m.tenant_id = ? and m.estado in ('EM_APROVACAO', 'PUBLICADO') and t.estado = 'PENDENTE' "
						+ "and t.id_treino_principal is null "
						+ "and m.data_inicio in (?, ?) and exists (select 1 from utilizadores_escalao ue "
						+ "inner join escalao_epoca ee on ee.id = ue.id_escalao_epoca inner join epoca e on e.id = ee.id_epoca "
						+ "where e.estado = '1' and ue.id_utilizador = ? and ue.id_escalao_epoca = t.id_equipa) "
						+ "order by t.`data`, t.hora_inicio, t.id")) {
					preparedStatement.setInt(1, parmIdentidade.getTenantId());
					preparedStatement.setInt(2, atual);
					preparedStatement.setInt(3, somaDias(atual, 7));
					preparedStatement.setInt(4, parmIdentidade.getIdUtilizador());
					try (ResultSet rs = preparedStatement.executeQuery()) {
						while (rs.next()) {
							if (!inicioPassou(rs.getInt("data"), rs.getString("hora_inicio"))) {
								lista.add(new int[] { rs.getInt("id"), rs.getInt("data_inicio") });
							}
						}
					}
				}
				return lista;
			});
			for (int[] linha : linhas) {
				ids.add(linha[0]);
				semanaMin = semanaMin == null ? linha[1] : Math.min(semanaMin, linha[1]);
			}
		}
		resultado.put("total", ids.size());
		resultado.put("ids", ids);
		resultado.put("semana", semanaMin);
		// v9 (Z10, R259): so ao ADMIN, os micro ciclos em aprovacao prontos a publicar, da semana atual em diante
		List<Integer> prontos = new ArrayList<>();
		if (parmIdentidade.isAdmin()) {
			int atual = segundaDe(hoje());
			prontos = leitura("pendentesMeus", conn -> {
				List<Integer> lista = new ArrayList<>();
				try (PreparedStatement preparedStatement = conn.prepareStatement("select m.data_inicio from micro_ciclo m "
						+ "where m.tenant_id = ? and m.estado = 'EM_APROVACAO' and m.data_inicio >= ? "
						+ "and exists (select 1 from treino t where t.id_micro_ciclo = m.id and t.id_treino_principal is null "
						+ "and t.estado <> 'CANCELADO') and not exists (select 1 from treino t where t.id_micro_ciclo = m.id "
						+ "and t.id_treino_principal is null and t.estado not in ('CANCELADO', 'APROVADO')) "
						+ "order by m.data_inicio")) {
					preparedStatement.setInt(1, parmIdentidade.getTenantId());
					preparedStatement.setInt(2, atual);
					try (ResultSet rs = preparedStatement.executeQuery()) {
						while (rs.next()) {
							lista.add(rs.getInt(1));
						}
					}
				}
				return lista;
			});
		}
		resultado.put("prontos", prontos);
		return resultado;
	}

	// ==================== v5: grupos de eventos (BK-v5-2) ====================

	/** Todos os grupos do tenant (W2), ativos e inativos, por nome. */
	static List<sm.core.data.GrupoEventoData> gruposEvento(Connection conn, int parmTenantId) throws SQLException {
		List<sm.core.data.GrupoEventoData> grupos = new ArrayList<>();
		try (PreparedStatement preparedStatement = conn.prepareStatement(
				"select id, nome, cor, estado from grupo_evento where tenant_id = ? order by nome, id")) {
			preparedStatement.setInt(1, parmTenantId);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					sm.core.data.GrupoEventoData g = new sm.core.data.GrupoEventoData();
					g.setIdGrupo(rs.getInt("id"));
					g.setNome(rs.getString("nome"));
					g.setCor(rs.getString("cor"));
					g.setAtivo("1".equals(rs.getString("estado")));
					grupos.add(g);
				}
			}
		}
		return grupos;
	}

	/** Cria (idGrupo 0) ou altera um grupo do tenant (ADMIN). Nunca se apaga: inativa-se (caso limite 9). */
	public sm.core.data.GrupoEventoData gravaGrupoEvento(Identidade parmIdentidade, sm.core.data.GrupoEventoData parmGrupo) {
		if (parmGrupo == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		String nome = limpa(parmGrupo.getNome(), 60, "O nome");
		if (nome == null) {
			throw new PedidoInvalidoException("Indique o nome do grupo.");
		}
		String cor = parmGrupo.getCor() == null || parmGrupo.getCor().trim().isEmpty() ? null : parmGrupo.getCor().trim();
		if (cor != null && !COR.matcher(cor).matches()) {
			throw new PedidoInvalidoException("Cor inválida: use o formato #RRGGBB.");
		}
		String corFinal = cor == null ? null : cor.toUpperCase();
		int tenant = parmIdentidade.getTenantId();
		int id = emTransacao("gravaGrupoEvento", conn -> {
			try {
				if (parmGrupo.getIdGrupo() <= 0) {
					try (PreparedStatement psInsere = conn.prepareStatement("insert into grupo_evento(tenant_id, nome, cor, "
							+ "estado, datacriacao, id_utilizador_criacao) values (?, ?, ?, ?, now(), ?)",
							Statement.RETURN_GENERATED_KEYS)) {
						psInsere.setInt(1, tenant);
						psInsere.setString(2, nome);
						psInsere.setString(3, corFinal);
						psInsere.setString(4, parmGrupo.isAtivo() ? "1" : "0");
						psInsere.setInt(5, parmIdentidade.getIdUtilizador());
						psInsere.executeUpdate();
						try (ResultSet rsId = psInsere.getGeneratedKeys()) {
							rsId.next();
							return rsId.getInt(1);
						}
					}
				}
				try (PreparedStatement psAltera = conn.prepareStatement(
						"update grupo_evento set nome = ?, cor = ?, estado = ? where id = ? and tenant_id = ?")) {
					psAltera.setString(1, nome);
					psAltera.setString(2, corFinal);
					psAltera.setString(3, parmGrupo.isAtivo() ? "1" : "0");
					psAltera.setInt(4, parmGrupo.getIdGrupo());
					psAltera.setInt(5, tenant);
					if (psAltera.executeUpdate() == 0) {
						// 0 linhas: grupo inexistente ou de outro tenant (o driver conta as linhas encontradas)
						throw new AcessoNegadoException();
					}
					return parmGrupo.getIdGrupo();
				}
			} catch (SQLIntegrityConstraintViolationException e) {
				throw new ConflitoException("Já existe o grupo " + nome + ".");
			}
		});
		log.info("MicroCicloHelper | gravaGrupoEvento | tenant:{} grupo:{} utilizador:{}", tenant, id,
				parmIdentidade.getIdUtilizador());
		sm.core.data.GrupoEventoData resultado = new sm.core.data.GrupoEventoData();
		resultado.setIdGrupo(id);
		resultado.setNome(nome);
		resultado.setCor(corFinal);
		resultado.setAtivo(parmGrupo.isAtivo());
		return resultado;
	}

	/** Cria as notificacoes, sem o proprio autor do evento. */
	private void notifica(Connection conn, Identidade parmIdentidade, List<Integer> parmDestinatarios, String parmTipo,
			Integer parmIdTreino, Integer parmIdJogador, String parmTexto) throws SQLException {
		// v3 (R68): sem duplicados, e sem o autor
		List<Integer> destinatarios = new ArrayList<>(new java.util.LinkedHashSet<>(parmDestinatarios));
		destinatarios.remove(Integer.valueOf(parmIdentidade.getIdUtilizador()));
		notificacaoHelper.cria(conn, destinatarios, FuncionalidadeHelper.MICRO_CICLO, parmTipo, parmIdTreino,
				parmIdJogador, parmTexto);
	}

	private static boolean diferenca(List<String> parmDiferencas, String parmCampo, String parmAntigo, String parmNovo) {
		if (Objects.equals(parmAntigo, parmNovo)) {
			return false;
		}
		parmDiferencas.add(parmCampo + " | " + (parmAntigo == null ? "" : parmAntigo) + " => "
				+ (parmNovo == null ? "" : parmNovo));
		return true;
	}

	public boolean eliminaTreino(Identidade parmIdentidade, int parmIdTreino) {
		return emTransacao("eliminaTreino", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, parmIdentidade.getTenantId());
			if (t.ligado()) {
				throw new PedidoInvalidoException("Remova o físico no editor do treino principal."); // v6 (X5)
			}
			// v6 (R183): os fisicos saem por cascata (fk_treino_principal); lidos antes, para o historico
			String fisicos = incluiFisicos(fisicosDe(conn, parmIdentidade.getTenantId(), t.d.getId()));
			PreparedStatement preparedStatement = conn
					.prepareStatement("delete from treino where id = ? and estado = 'RASCUNHO'");
			preparedStatement.setInt(1, parmIdTreino);
			if (preparedStatement.executeUpdate() == 0) {
				throw new ConflitoException("Só se pode apagar um treino em rascunho.");
			}
			historico(conn, t.d.getIdMicroCiclo(), null, parmIdentidade.getIdUtilizador(), "Treino apagado | "
					+ t.d.getNomeEquipa() + " " + dia(t.d.getData()) + " " + t.d.getHoraInicio() + fisicos);
			return true;
		});
	}

	// ==================== 8. transicoes ====================

	public TreinoData mudaEstado(Identidade parmIdentidade, int parmIdTreino, PedidoEstadoTreinoData parmPedido) {
		String acao = parmPedido.getAcao() == null ? "" : parmPedido.getAcao().trim().toUpperCase();
		if (!Set.of("APROVAR", "REJEITAR", "SUBMETER", "CANCELAR", "REATIVAR").contains(acao)) {
			throw new PedidoInvalidoException("Ação inválida.");
		}
		if (parmPedido.getVersao() == null) {
			throw new PedidoInvalidoException("Versão em falta.");
		}
		String motivo = limpa(parmPedido.getMotivo(), 255, "O motivo");
		int utilizador = parmIdentidade.getIdUtilizador();
		emTransacao("mudaEstado", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, parmIdentidade.getTenantId());
			if (!parmIdentidade.isAdmin() && (RASCUNHO.equals(t.estadoBD) || RASCUNHO.equals(t.mcEstado))) {
				throw new AcessoNegadoException();
			}
			if (t.ligado()) {
				throw new ConflitoException("Este físico faz parte de outro treino: mude o estado no treino principal.");
			}
			if (parmPedido.getVersao() != t.d.getVersao()) {
				throw new ConflitoException(MSG_VERSAO);
			}
			Set<Integer> equipas = equipasDoUtilizador(conn, utilizador);
			boolean passou = inicioPassou(t.d.getData(), t.inicioSessao());
			boolean prontoAntes = EM_APROVACAO.equals(t.mcEstado) && pronto(conn, t.d.getIdMicroCiclo()); // v9 (R248)
			if (t.evento() && !parmIdentidade.isAdmin()) {
				throw new AcessoNegadoException(); // v5: so o ADMIN decide sobre eventos
			}
			String sql;
			String texto;
			switch (acao) {
			case "APROVAR":
				if (!PENDENTE.equals(t.estadoBD)) {
					throw new ConflitoException(MSG_TRANSICAO);
				}
				if (!podeAgirNaEquipa(parmIdentidade, t.d.getIdEquipa(), equipas)) {
					throw new AcessoNegadoException();
				}
				if (passou) {
					throw new ConflitoException("O treino já começou: já não pode ser aprovado.");
				}
				// D4: a convocatoria efetiva fica gravada ("fotografia")
				insereConvocatoriaOmissao(conn, t.d.getId(), t.d.getIdEquipa(), utilizador);
				sql = "update treino set estado = 'APROVADO', motivo_estado = null, data_aprovacao = now(), "
						+ "id_utilizador_aprovacao = ?, versao = versao + 1 where id = ? and versao = ?";
				texto = "Aprovado (" + textoQualidade(qualidade(parmIdentidade, t.d.getIdEquipa(), equipas)) + ")";
				break;
			case "REJEITAR":
				if (!PENDENTE.equals(t.estadoBD)) {
					throw new ConflitoException(MSG_TRANSICAO);
				}
				if (!podeAgirNaEquipa(parmIdentidade, t.d.getIdEquipa(), equipas)) {
					throw new AcessoNegadoException();
				}
				exigeMotivo(motivo);
				if (passou) {
					throw new ConflitoException(MSG_JA_COMECOU);
				}
				sql = "update treino set estado = 'REJEITADO', motivo_estado = ?, versao = versao + 1 "
						+ "where id = ? and versao = ?";
				texto = "Rejeitado (" + textoQualidade(qualidade(parmIdentidade, t.d.getIdEquipa(), equipas)) + ") | "
						+ motivo;
				break;
			case "SUBMETER":
				if (!parmIdentidade.isAdmin()) {
					throw new AcessoNegadoException();
				}
				if (!REJEITADO.equals(t.estadoBD)) {
					throw new ConflitoException(MSG_TRANSICAO);
				}
				sql = "update treino set estado = 'PENDENTE', motivo_estado = null, versao = versao + 1 "
						+ "where id = ? and versao = ?";
				texto = "Submetido de novo";
				break;
			case "REATIVAR": // v4 (Q50, BK-v4-7, opcional)
				if (!parmIdentidade.isAdmin()) {
					throw new AcessoNegadoException();
				}
				if (!CANCELADO.equals(t.estadoBD) || !(PUBLICADO.equals(t.mcEstado) || EM_APROVACAO.equals(t.mcEstado))) {
					throw new ConflitoException(MSG_TRANSICAO);
				}
				if (passou || semanaTerminou(t.dataInicio)) {
					throw new ConflitoException(MSG_JA_COMECOU);
				}
				Integer epocaAtual = epocaAtiva(conn, parmIdentidade.getTenantId());
				if (!t.evento() && (epocaAtual == null || t.equipaEpoca != epocaAtual)) {
					throw new ConflitoException("A equipa não é da época ativa.");
				}
				sql = "update treino set estado = 'PENDENTE', motivo_estado = null, origem_cancelamento = null, "
						+ "versao = versao + 1 where id = ? and versao = ?";
				texto = "Estado | CANCELADO => PENDENTE (reativado)";
				break;
			default: // CANCELAR
				if (!parmIdentidade.isAdmin()) {
					throw new AcessoNegadoException();
				}
				if (CANCELADO.equals(t.estadoBD) || t.realizado()) {
					throw new ConflitoException(MSG_TRANSICAO);
				}
				exigeMotivo(motivo);
				if (semanaTerminou(t.dataInicio)) {
					throw new ConflitoException("Esta semana já terminou.");
				}
				// v4 (W1): cancelado um a um
				sql = "update treino set estado = 'CANCELADO', motivo_estado = ?, origem_cancelamento = 'TREINO', "
						+ "versao = versao + 1 "
						+ "where id = ? and versao = ?";
				texto = "Cancelado | " + motivo;
				break;
			}
			PreparedStatement preparedStatement = conn.prepareStatement(sql);
			int i = 1;
			if (sql.contains("motivo_estado = ?")) {
				preparedStatement.setString(i++, motivo);
			}
			if (sql.contains("id_utilizador_aprovacao = ?")) {
				preparedStatement.setInt(i++, utilizador);
			}
			preparedStatement.setInt(i++, t.d.getId());
			preparedStatement.setInt(i, t.d.getVersao());
			if (preparedStatement.executeUpdate() == 0) {
				throw new ConflitoException(MSG_VERSAO);
			}
			// v6 (CA122): uma so linha por acao, com os fisicos; os fisicos seguem o estado do principal
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), utilizador,
					texto + incluiFisicos(fisicosDe(conn, parmIdentidade.getTenantId(), t.d.getId())));
			if ("APROVAR".equals(acao) || "REATIVAR".equals(acao)) {
				sincronizaTreino(conn, parmIdentidade.getTenantId(), t.d.getId(), null); // Indisponibilidades (Z3)
			}
			propagaSessao(conn, parmIdentidade.getTenantId(), t.d.getId(), null);
			// v9 (Z5): "era visivel" = publicado e ja aprovado antes desta acao
			boolean eraVisivel = PUBLICADO.equals(t.mcEstado) && t.d.getDataAprovacao() != null;
			if ("APROVAR".equals(acao)) {
				avisaAtletas(conn, parmIdentidade, t.d.getId(),
						eraVisivel ? MotivoAviso.APROVADO_DE_NOVO : MotivoAviso.APROVADO_NOVO, Set.of());
			}
			if ("CANCELAR".equals(acao)) {
				if (eraVisivel) {
					avisaAtletas(conn, parmIdentidade, t.d.getId(), MotivoAviso.CANCELADO, Set.of()); // R256, Q139
				}
				apagaConfirmacoes(conn, t.d.getId(), null); // v8 (Y5, Q129): ao reativar, todos sem resposta
			}
			if ("APROVAR".equals(acao) || "CANCELAR".equals(acao)) {
				talvezPronto(conn, parmIdentidade, t.d.getIdMicroCiclo(), prontoAntes); // v9 (R248)
			}
			if ("REJEITAR".equals(acao) && !t.evento()) { // v5 (R128): eventos sem notificacoes
				aposTreinoRejeitado(conn, parmIdentidade, t);
			}
			if ("REATIVAR".equals(acao) && !t.evento()) {
				try (PreparedStatement psAusencias = conn.prepareStatement("update treino_ausencia set "
						+ "pendente_confirmacao = '1', data_alteracao = now() where id_treino = ? and estado = 'ATIVA' "
						+ "and id_indisponibilidade is null")) {
					psAusencias.setInt(1, t.d.getId());
					psAusencias.executeUpdate();
				}
				notifica(conn, parmIdentidade, notificacaoHelper.destinatariosEquipa(conn, t.d.getIdEquipa(),
						parmIdentidade.getTenantId()), "TREINO_NOVO", t.d.getId(), null, "Treino " + t.d.getNomeEquipa()
								+ " de " + dia(t.d.getData()) + " às " + t.d.getHoraInicio() + " reativado: aguarda aprovação");
			}
			return true;
		});
		log.info("MicroCicloHelper | mudaEstado | treino:{} acao:{} utilizador:{}", parmIdTreino, acao, utilizador);
		return getTreinoData(parmIdentidade, parmIdTreino);
	}

	/** Q31 (BK-12): rejeicao, as contas ADMIN ativas do tenant. */
	private void aposTreinoRejeitado(Connection conn, Identidade parmIdentidade, Treino t) throws SQLException {
		notifica(conn, parmIdentidade, notificacaoHelper.destinatariosAdmin(conn, parmIdentidade.getTenantId()),
				"TREINO_REJEITADO", t.d.getId(), null, "Treino " + t.d.getNomeEquipa() + " de " + dia(t.d.getData())
						+ " às " + t.d.getHoraInicio() + " foi rejeitado");
	}

	private static void exigeMotivo(String parmMotivo) {
		if (parmMotivo == null) {
			throw new PedidoInvalidoException("Indique o motivo.");
		}
	}

	// ==================== 9. convocatoria ====================

	public TreinoDetalheData ajustaConvocatoria(Identidade parmIdentidade, int parmIdTreino,
			PedidoConvocatoriaData parmPedido) {
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		emTransacao("ajustaConvocatoria", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, tenant);
			if (!parmIdentidade.isAdmin() && (RASCUNHO.equals(t.estadoBD) || RASCUNHO.equals(t.mcEstado))) {
				throw new AcessoNegadoException();
			}
			if (t.ligado()) {
				throw new ConflitoException("A convocatória é a do treino principal."); // v6 (X5)
			}
			if (!podeAgirNaEquipa(parmIdentidade, t.d.getIdEquipa(), equipasDoUtilizador(conn, utilizador))) {
				throw new AcessoNegadoException();
			}
			if (t.evento()) {
				throw new ConflitoException("Um evento não tem convocatória."); // v5 (R128)
			}
			if (CANCELADO.equals(t.estadoBD) || t.realizado()) {
				throw new ConflitoException("Não se pode ajustar a convocatória de um treino cancelado ou realizado.");
			}
			// v7 (X4): a convocatoria fecha no inicio da sessao; os participantes de cada fisico no inicio desse fisico
			boolean mexeConvocatoria = parmPedido.isRepor()
					|| (parmPedido.getAdicionar() != null && !parmPedido.getAdicionar().isEmpty())
					|| (parmPedido.getRetirar() != null && !parmPedido.getRetirar().isEmpty());
			if (inicioPassou(t.d.getData(), t.inicioSessao()) && (mexeConvocatoria || parmPedido.getFisicos() == null)) {
				throw new ConflitoException(MSG_JA_COMECOU);
			}
			// v7 (X3): fisicos do pedido, so deste principal e sem repetidos
			Map<Integer, FisicoStaffData> fisicosPrincipal = new LinkedHashMap<>();
			for (FisicoStaffData f : fisicosDe(conn, tenant, t.d.getId())) {
				fisicosPrincipal.put(f.getId(), f);
			}
			Map<Integer, Set<Integer>> pedidosFisicos = new LinkedHashMap<>();
			if (parmPedido.getFisicos() != null) {
				for (PedidoConvocatoriaData.Fisico pf : parmPedido.getFisicos()) {
					if (pf == null || pf.getIdFisico() == null || !fisicosPrincipal.containsKey(pf.getIdFisico())
							|| pedidosFisicos.containsKey(pf.getIdFisico())) {
						throw new PedidoInvalidoException("Treino físico inválido.");
					}
					Set<Integer> excluidosPedido = new HashSet<>();
					if (pf.getExcluidos() != null) {
						for (Integer idJogador : pf.getExcluidos()) {
							if (idJogador == null) {
								throw new PedidoInvalidoException("Jogador inválido: null");
							}
							excluidosPedido.add(idJogador);
						}
					}
					pedidosFisicos.put(pf.getIdFisico(), excluidosPedido);
				}
			}
			Map<Integer, Set<Integer>> excluidosAntes = excluidosDe(conn, tenant, fisicosPrincipal.keySet());
			Map<Integer, String> antes = nomes(convocatoriaEfetiva(conn, t));

			if (parmPedido.isRepor()) {
				PreparedStatement psAcrescentados = conn.prepareStatement(
						"delete from treino_convocado where id_treino = ? and origem = 'ACRESCENTADO'");
				psAcrescentados.setInt(1, t.d.getId());
				psAcrescentados.executeUpdate();
				PreparedStatement psOmissao = conn.prepareStatement("update treino_convocado set estado = '1', "
						+ "data_alteracao = now(), id_utilizador_alteracao = ? where id_treino = ? and origem = 'OMISSAO'");
				psOmissao.setInt(1, utilizador);
				psOmissao.setInt(2, t.d.getId());
				psOmissao.executeUpdate();
				insereConvocatoriaOmissao(conn, t.d.getId(), t.d.getIdEquipa(), utilizador);
			}

			PreparedStatement psJogador = conn
					.prepareStatement("select 1 from jogador where id = ? and tenant_id = ? and estado = '1'");
			Set<Integer> adicionados = new HashSet<>();
			if (parmPedido.getAdicionar() != null) {
				PreparedStatement psAdiciona = conn.prepareStatement("insert into treino_convocado(id_treino, id_jogador, "
						+ "origem, estado, data_alteracao, id_utilizador_alteracao) values (?, ?, 'ACRESCENTADO', '1', now(), ?) "
						+ "on duplicate key update estado = '1', data_alteracao = now(), "
						+ "id_utilizador_alteracao = values(id_utilizador_alteracao)");
				for (Integer idJogador : parmPedido.getAdicionar()) {
					psJogador.setInt(1, idJogador == null ? 0 : idJogador);
					psJogador.setInt(2, tenant);
					if (idJogador == null || !psJogador.executeQuery().next()) {
						throw new PedidoInvalidoException("Jogador inválido: " + idJogador);
					}
					psAdiciona.setInt(1, t.d.getId());
					psAdiciona.setInt(2, idJogador);
					psAdiciona.setInt(3, utilizador);
					psAdiciona.executeUpdate();
					adicionados.add(idJogador);
				}
			}
			if (parmPedido.getRetirar() != null) {
				Set<Integer> atuais = nomes(convocatoriaEfetiva(conn, t)).keySet();
				PreparedStatement psRetira = conn.prepareStatement("insert into treino_convocado(id_treino, id_jogador, "
						+ "origem, estado, data_alteracao, id_utilizador_alteracao) values (?, ?, 'OMISSAO', '0', now(), ?) "
						+ "on duplicate key update estado = '0', data_alteracao = now(), "
						+ "id_utilizador_alteracao = values(id_utilizador_alteracao)");
				for (Integer idJogador : parmPedido.getRetirar()) {
					if (idJogador == null || !(atuais.contains(idJogador) || adicionados.contains(idJogador))) {
						throw new PedidoInvalidoException("Jogador inválido: " + idJogador);
					}
					psRetira.setInt(1, t.d.getId());
					psRetira.setInt(2, idJogador);
					psRetira.setInt(3, utilizador);
					psRetira.executeUpdate();
				}
			}

			Map<Integer, String> depois = nomes(convocatoriaEfetiva(conn, t));
			// ausencias de quem deixou de estar convocado deixam de ter efeito
			PreparedStatement psAusencias = conn.prepareStatement(
					"select id_jogador from treino_ausencia where id_treino = ? and estado = 'ATIVA'");
			psAusencias.setInt(1, t.d.getId());
			ResultSet rsAusencias = psAusencias.executeQuery();
			List<Integer> descartar = new ArrayList<>();
			while (rsAusencias.next()) {
				if (!depois.containsKey(rsAusencias.getInt(1))) {
					descartar.add(rsAusencias.getInt(1));
				}
			}
			PreparedStatement psDescarta = conn.prepareStatement("update treino_ausencia set estado = 'DESCARTADA', "
					+ "data_alteracao = now() where id_treino = ? and id_jogador = ? and estado = 'ATIVA'");
			for (Integer idJogador : descartar) {
				psDescarta.setInt(1, t.d.getId());
				psDescarta.setInt(2, idJogador);
				psDescarta.executeUpdate();
			}

			// v7 (X2, R189): repor apaga todas as exclusoes; quem saiu do principal perde as suas
			if (parmPedido.isRepor()) {
				apagaExcluidosDoPrincipal(conn, t.d.getId(), null);
			}
			for (Integer idJogador : antes.keySet()) {
				if (!depois.containsKey(idJogador)) {
					apagaExcluidosDoPrincipal(conn, t.d.getId(), idJogador);
					apagaConfirmacoes(conn, t.d.getId(), idJogador); // v8 (Y5): quem volta comeca sem resposta
				}
			}
			// v7 (X3): cada entrada substitui os excluidos desse fisico
			Map<Integer, Set<Integer>> excluidosAtuais = excluidosDe(conn, tenant, fisicosPrincipal.keySet());
			for (Map.Entry<Integer, Set<Integer>> pedido : pedidosFisicos.entrySet()) {
				FisicoStaffData f = fisicosPrincipal.get(pedido.getKey());
				Set<Integer> atuais = new HashSet<>(excluidosAtuais.getOrDefault(f.getId(), Set.of()));
				atuais.retainAll(depois.keySet()); // as exclusoes orfas (ver apagaExcluidosDoPrincipal) nao contam

				Set<Integer> novos = pedido.getValue();
				for (Integer idJogador : novos) {
					if (!depois.containsKey(idJogador)) {
						throw new PedidoInvalidoException("Jogador inválido: " + idJogador);
					}
				}
				if (atuais.equals(novos)) {
					continue;
				}
				if (inicioPassou(t.d.getData(), f.getHoraInicio())) {
					throw new ConflitoException("O físico das " + f.getHoraInicio() + " já começou.");
				}
				List<String> menos = new ArrayList<>();
				List<String> mais = new ArrayList<>();
				try (PreparedStatement psInsere = conn.prepareStatement("insert into treino_fisico_excluido(id_treino, "
						+ "id_jogador, data_alteracao, id_utilizador_alteracao) values (?, ?, now(), ?)");
						PreparedStatement psApaga = conn.prepareStatement(
								"delete from treino_fisico_excluido where id_treino = ? and id_jogador = ?")) {
					for (Integer idJogador : novos) {
						if (!atuais.contains(idJogador)) {
							psInsere.setInt(1, f.getId());
							psInsere.setInt(2, idJogador);
							psInsere.setInt(3, utilizador);
							psInsere.executeUpdate();
							menos.add(depois.get(idJogador));
						}
					}
					for (Integer idJogador : atuais) {
						if (!novos.contains(idJogador)) {
							psApaga.setInt(1, f.getId());
							psApaga.setInt(2, idJogador);
							psApaga.executeUpdate();
							mais.add(depois.get(idJogador));
						}
					}
				}
				// R191: historico no principal, sem mudar o estado nem notificar o treinador
				historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), utilizador, "Físico " + intervalo(f)
						+ (menos.isEmpty() ? "" : " | − " + String.join(", ", menos))
						+ (mais.isEmpty() ? "" : " | + " + String.join(", ", mais)));
			}
			Map<Integer, Set<Integer>> excluidosDepois = excluidosDe(conn, tenant, fisicosPrincipal.keySet());

			List<String> entrou = new ArrayList<>();
			List<String> saiu = new ArrayList<>();
			depois.forEach((id, nome) -> {
				if (!antes.containsKey(id)) {
					entrou.add(nome);
				}
			});
			antes.forEach((id, nome) -> {
				if (!depois.containsKey(id)) {
					saiu.add(nome);
				}
			});
			if (!entrou.isEmpty() || !saiu.isEmpty()) {
				historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), utilizador, "Convocatória | entrou: "
						+ String.join(", ", entrou) + " | saiu: " + String.join(", ", saiu));
			}
			// v5 (W8, R149-R153): so num Aprovado; diferenca entre antes e depois (entrar e sair anulam-se)
			// v9 (R252): so com o micro ciclo publicado (em aprovacao o atleta ainda nao ve o treino)
			if (APROVADO.equals(t.estadoBD) && !t.realizado() && PUBLICADO.equals(t.mcEstado)) {
				String treino = "treino " + t.d.getNomeEquipa() + " de " + dia(t.d.getData()) + " às " + t.d.getHoraInicio();
				for (Integer idJogador : depois.keySet()) {
					if (!antes.containsKey(idJogador)) {
						notificaAtleta(conn, parmIdentidade, idJogador, t.d.getId(), "CONVOCATORIA_ENTROU",
								"Foste convocado para o " + treino);
					}
				}
				for (Integer idJogador : antes.keySet()) {
					if (!depois.containsKey(idJogador)) {
						notificaAtleta(conn, parmIdentidade, idJogador, t.d.getId(), "CONVOCATORIA_SAIU",
								"Deixaste de estar convocado para o " + treino);
					}
				}
				// v7 (X6): quem ficou e mudou nalgum fisico; vale o primeiro fisico alterado (por hora)
				Map<Integer, String[]> notificacoesFisico = new LinkedHashMap<>(); // jogador -> {tipo, texto}
				for (Integer idJogador : depois.keySet()) {
					if (!antes.containsKey(idJogador)) {
						continue;
					}
					for (FisicoStaffData f : fisicosPrincipal.values()) {
						boolean fazia = !excluidosAntes.getOrDefault(f.getId(), Set.of()).contains(idJogador);
						boolean faz = !excluidosDepois.getOrDefault(f.getId(), Set.of()).contains(idJogador);
						if (fazia != faz) {
							String fisico = "físico do " + t.d.getNomeEquipa() + " de " + dia(t.d.getData()) + " às "
									+ f.getHoraInicio();
							notificacoesFisico.put(idJogador, new String[] { faz ? "FISICO_ENTROU" : "FISICO_SAIU",
									(faz ? "Passaste a fazer o " : "Deixaste de fazer o ") + fisico });
							break;
						}
					}
				}
				// v8 (Y4): quem entra num fisico e tinha confirmado recebe uma so notificacao (FISICO_ENTROU + "Confirma de novo.")
				Set<Integer> juntar = new HashSet<>();
				notificacoesFisico.forEach((idJogador, n) -> {
					if ("FISICO_ENTROU".equals(n[0])) {
						juntar.add(idJogador);
					}
				});
				Set<Integer> caducadas = avisaAtletas(conn, parmIdentidade, t.d.getId(), MotivoAviso.LOCAL, juntar);
				for (Map.Entry<Integer, String[]> n : notificacoesFisico.entrySet()) {
					boolean confirmaDeNovo = juntar.contains(n.getKey()) && caducadas.contains(n.getKey());
					notificaAtleta(conn, parmIdentidade, n.getKey(), t.d.getId(), n.getValue()[0],
							n.getValue()[1] + (confirmaDeNovo ? ". Confirma de novo." : ""));
				}
			}
			sincronizaTreino(conn, tenant, t.d.getId(), null); // Indisponibilidades (Z3)
			return true;
		});
		return getDetalhe(parmIdentidade, parmIdTreino);
	}

	/**
	 * v7 (X2): apaga as exclusoes dos fisicos do principal (todas, ou so as do jogador). shortcut: quem sai da
	 * convocatoria por inatividade ou por sair do plantel (sem gravacao) mantem a exclusao; limpar na leitura se for preciso.
	 */
	private static void apagaExcluidosDoPrincipal(Connection conn, int parmIdPrincipal, Integer parmIdJogador)
			throws SQLException {
		try (PreparedStatement preparedStatement = conn.prepareStatement("delete x from treino_fisico_excluido x "
				+ "inner join treino f on f.id = x.id_treino where f.id_treino_principal = ?"
				+ (parmIdJogador != null ? " and x.id_jogador = ?" : ""))) {
			preparedStatement.setInt(1, parmIdPrincipal);
			if (parmIdJogador != null) {
				preparedStatement.setInt(2, parmIdJogador);
			}
			preparedStatement.executeUpdate();
		}
	}

	/** v9 (Z5): motivos de avisaAtletas. */
	enum MotivoAviso {
		/** alteracao sem nova aprovacao (local) ou ajuste da convocatoria: so os confirmados caducados */
		LOCAL,
		/** aprovado de novo um treino ja visivel: caducados -> CONFIRMACAO_CADUCOU; os outros -> TREINO_MUDOU */
		APROVADO_DE_NOVO,
		/** aprovado um treino que o atleta ainda nao via: TREINO_NOVO_ATLETA */
		APROVADO_NOVO,
		/** cancelado um treino visivel: TREINO_CANCELADO (chamar antes de apagar as confirmacoes) */
		CANCELADO
	}

	/**
	 * v9 (Z5; substitui a avisaCaducadas da v8, Y3/Y4): uma notificacao por atleta e por acao, so num micro ciclo
	 * PUBLICADO (R252). Idempotente para os confirmados (sessao_avisada). Os de parmJuntarEm nao recebem
	 * CONFIRMACAO_CADUCOU (vao receber outra notificacao com "Confirma de novo."), mas a sessao_avisada grava-se.
	 *
	 * @return os jogadores com a confirmacao caducada nesta chamada
	 */
	private Set<Integer> avisaAtletas(Connection conn, Identidade parmIdentidade, int parmIdSessao, MotivoAviso parmMotivo,
			Set<Integer> parmJuntarEm) throws SQLException {
		Set<Integer> avisados = new HashSet<>();
		int tenant = parmIdentidade.getTenantId();
		Treino t = carregaTreino(conn, parmIdSessao, tenant);
		if (t.ligado() || t.evento() || !PUBLICADO.equals(t.mcEstado)) {
			return avisados;
		}
		Set<Integer> convocados = nomes(convocatoriaEfetiva(conn, t)).keySet();
		String treinoTexto = "treino " + t.d.getNomeEquipa() + " de " + dia(t.d.getData());
		if (parmMotivo == MotivoAviso.CANCELADO) {
			for (Integer idJogador : convocados) {
				notificaAtleta(conn, parmIdentidade, idJogador, t.d.getId(), "TREINO_CANCELADO",
						"O " + treinoTexto + " às " + t.d.getHoraInicio() + " foi cancelado");
			}
			return avisados;
		}
		if (!APROVADO.equals(t.estadoBD) || t.realizado()) {
			return avisados;
		}
		Map<Integer, Confirmacao> confirmacoes = confirmacoesDe(conn, tenant, List.of(t.d.getId()))
				.getOrDefault(t.d.getId(), Map.of());
		if (parmMotivo == MotivoAviso.LOCAL && confirmacoes.isEmpty()) {
			return avisados;
		}
		List<FisicoStaffData> fisicos = fisicosDe(conn, tenant, t.d.getId());
		List<Integer> idsFisicos = new ArrayList<>();
		for (FisicoStaffData f : fisicos) {
			idsFisicos.add(f.getId());
		}
		Map<Integer, Set<Integer>> excluidos = excluidosDe(conn, tenant, idsFisicos);
		try (PreparedStatement psAvisada = conn.prepareStatement(
				"update treino_confirmacao set sessao_avisada = ? where id_treino = ? and id_jogador = ?")) {
			for (Integer idJogador : convocados) { // as confirmacoes orfas (saiu sem gravacao) nao se avisam
				List<FisicoData> meus = new ArrayList<>();
				for (FisicoStaffData f : fisicos) {
					if (!excluidos.getOrDefault(f.getId(), Set.of()).contains(idJogador)) {
						meus.add(f);
					}
				}
				String inicio = inicioDoAtleta(t.d.getHoraInicio(), meus);
				String horas = (t.d.getHoraFim() != null ? inicio + "–" + t.d.getHoraFim() : "a partir das " + inicio)
						+ (t.d.getLocal() != null ? " · " + t.d.getLocal() : "");
				if (parmMotivo == MotivoAviso.APROVADO_NOVO) {
					notificaAtleta(conn, parmIdentidade, idJogador, t.d.getId(), "TREINO_NOVO_ATLETA",
							"Novo " + treinoTexto + " (" + horas + ")");
					continue;
				}
				Confirmacao c = confirmacoes.get(idJogador);
				String atual = sessaoDoAtleta(t.d, meus);
				if (c != null && caducou(c.sessao, atual)) {
					if (atual.equals(c.avisada)) {
						continue; // ja avisado desta mudanca
					}
					if (!parmJuntarEm.contains(idJogador)) {
						notificaAtleta(conn, parmIdentidade, idJogador, t.d.getId(), "CONFIRMACAO_CADUCOU",
								"O " + treinoTexto + " mudou (" + horas + "). Confirma de novo.");
					}
					psAvisada.setString(1, atual);
					psAvisada.setInt(2, t.d.getId());
					psAvisada.setInt(3, idJogador);
					psAvisada.executeUpdate();
					avisados.add(idJogador);
				} else if (parmMotivo == MotivoAviso.APROVADO_DE_NOVO) {
					notificaAtleta(conn, parmIdentidade, idJogador, t.d.getId(), "TREINO_MUDOU",
							"O " + treinoTexto + " foi alterado (" + horas + ")");
				}
			}
		}
		return avisados;
	}

	/** v7 (R202): copia as exclusoes da origem para o fisico copiado, so de quem e convocado efetivo da copia. */
	private void copiaExcluidos(Connection conn, int parmIdCopia, int parmIdEquipa, String parmEstado, int parmIdFisico,
			Set<Integer> parmExcluidos, int parmIdUtilizador) throws SQLException {
		Treino copia = new Treino();
		copia.d = new TreinoData();
		copia.d.setId(parmIdCopia);
		copia.d.setIdEquipa(parmIdEquipa);
		copia.estadoBD = parmEstado; // RASCUNHO ou PENDENTE: nao fotografado
		Set<Integer> convocados = nomes(convocatoriaEfetiva(conn, copia)).keySet();
		try (PreparedStatement preparedStatement = conn.prepareStatement("insert into treino_fisico_excluido(id_treino, "
				+ "id_jogador, data_alteracao, id_utilizador_alteracao) values (?, ?, now(), ?)")) {
			for (Integer idJogador : parmExcluidos) {
				if (convocados.contains(idJogador)) {
					preparedStatement.setInt(1, parmIdFisico);
					preparedStatement.setInt(2, idJogador);
					preparedStatement.setInt(3, parmIdUtilizador);
					preparedStatement.executeUpdate();
				}
			}
		}
	}

	/**
	 * W7: notificacao a conta ATLETA ativa do jogador (se tiver), com id_jogador NULL para nunca lhe
	 * juntar um motivo; sem nomes de outros, motivos nem autor (R152).
	 */
	private void notificaAtleta(Connection conn, Identidade parmIdentidade, int parmIdJogador, int parmIdTreino,
			String parmTipo, String parmTexto) throws SQLException {
		List<Integer> contas = new ArrayList<>();
		try (PreparedStatement preparedStatement = conn.prepareStatement("select u.id from utilizadores u "
				+ "where u.id_jogador = ? and u.tenant_id = ? and u.estado = '1' and upper(u.perfil) = 'ATLETA'")) {
			preparedStatement.setInt(1, parmIdJogador);
			preparedStatement.setInt(2, parmIdentidade.getTenantId());
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					contas.add(rs.getInt(1));
				}
			}
		}
		notifica(conn, parmIdentidade, contas, parmTipo, parmIdTreino, null, parmTexto);
	}

	private static Map<Integer, String> nomes(List<TreinoConvocadoData> parmLista) {
		Map<Integer, String> mapa = new LinkedHashMap<>();
		for (TreinoConvocadoData c : parmLista) {
			mapa.put(c.getIdJogador(), c.getNome());
		}
		return mapa;
	}

	// ==================== 11. detalhe ====================

	public TreinoDetalheData getDetalhe(Identidade parmIdentidade, int parmIdTreino) {
		int tenant = parmIdentidade.getTenantId();
		return leitura("getDetalhe", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, tenant);
			if (!parmIdentidade.isAdmin() && (RASCUNHO.equals(t.estadoBD) || RASCUNHO.equals(t.mcEstado))) {
				throw new AcessoNegadoException();
			}
			for (Treino s : listaTreinos(conn, parmIdentidade, t.d.getIdMicroCiclo())) {
				if (s.d.getId() == parmIdTreino) {
					t = s;
				}
			}
			Set<Integer> equipas = equipasDoUtilizador(conn, parmIdentidade.getIdUtilizador());
			TreinoDetalheData detalhe = new TreinoDetalheData();
			detalhe.setTreino(t.d);
			if (t.ligado()) {
				// v6 (X5): o detalhe e o do principal (treino.idTreinoPrincipal); sem convocatoria, ausencias nem acoes
				detalhe.setPodeAprovar(false);
				detalhe.setPodeAjustar(false);
				detalhe.setPodeMarcarPresenca(false);
				detalhe.setPodeEditar(false);
				return detalhe;
			}

			PreparedStatement psAusencias = conn.prepareStatement(SQL_AUSENCIA + " order by j.nome");
			psAusencias.setInt(1, t.d.getId());
			ResultSet rsAusencias = psAusencias.executeQuery();
			Set<Integer> avisaram = new HashSet<>();
			Set<Integer> indisponiveis = new HashSet<>();
			while (rsAusencias.next()) {
				TreinoAusenciaData a = lerAusencia(rsAusencias);
				detalhe.getAusencias().add(a);
				avisaram.add(a.getIdJogador());
				if (a.getIdIndisponibilidade() != null) {
					indisponiveis.add(a.getIdJogador());
				}
			}
			// Indisponibilidades (Q13): categoria e motivo so para quem pode ver
			for (TreinoAusenciaData a : detalhe.getAusencias()) {
				if (a.getIdIndisponibilidade() != null) {
					IndisponibilidadeHelper.mascara(a, IndisponibilidadeHelper.podeVerMotivo(parmIdentidade, equipas,
							t.d.getIdEquipa(), equipasDoAtleta(conn, tenant, a.getIdJogador())));
				}
			}

			PreparedStatement psOrigem = conn.prepareStatement("select coalesce(nullif(ee.nome, ''), es.nome) "
					+ "from escalao_epoca_jogador eej inner join escalao_epoca ee on ee.id = eej.id_escalao_epoca "
					+ "inner join epoca e on e.id = ee.id_epoca left join escalao es on es.id = ee.id_escalao "
					+ "where eej.id_jogador = ? and e.estado = '1' and e.tenant_id = ? and ee.id <> ? LIMIT 1");
			// Indisponibilidades (D-1): como em listaTreinos, o indisponivel so fica em ausencias
			List<TreinoConvocadoData> convocatoria = participantes(convocatoriaEfetiva(conn, t), indisponiveis);
			// v8 (R236, Y10): resposta de cada convocado, so nas sessoes Aprovadas; a data nao vai ao treinador de outra equipa
			boolean comRespostas = !t.evento() && APROVADO.equals(t.estadoBD) && PUBLICADO.equals(t.mcEstado); // v9 (R246)
			boolean comDatas = !(parmIdentidade.isTreinador() && !parmIdentidade.isAdmin()
					&& !equipas.contains(t.d.getIdEquipa()));
			Map<Integer, Confirmacao> confirmacoes = new HashMap<>();
			Set<Integer> contas = new HashSet<>();
			Map<Integer, Set<Integer>> excluidosSessao = new HashMap<>();
			Map<Integer, Timestamp> datasAusencia = new HashMap<>();
			if (comRespostas) {
				List<Integer> idsConvocados = new ArrayList<>();
				for (TreinoConvocadoData c : convocatoria) {
					idsConvocados.add(c.getIdJogador());
				}
				confirmacoes = confirmacoesDe(conn, tenant, List.of(t.d.getId())).getOrDefault(t.d.getId(), new HashMap<>());
				contas = comConta(conn, tenant, idsConvocados);
				List<Integer> idsFisicos = new ArrayList<>();
				if (t.d.getFisicos() != null) {
					for (FisicoStaffData f : t.d.getFisicos()) {
						idsFisicos.add(f.getId());
					}
				}
				excluidosSessao = excluidosDe(conn, tenant, idsFisicos);
				for (TreinoAusenciaData a : detalhe.getAusencias()) {
					datasAusencia.put(a.getIdJogador(), a.getDataIndicacao());
				}
			}
			for (TreinoConvocadoData c : convocatoria) {
				c.setAvisou(avisaram.contains(c.getIdJogador()));
				if (comRespostas) {
					Confirmacao conf = confirmacoes.get(c.getIdJogador());
					String estado = estadoResposta(avisaram.contains(c.getIdJogador()), contas.contains(c.getIdJogador()),
							conf, sessaoDoJogador(t.d, t.d.getFisicos(), excluidosSessao, c.getIdJogador()));
					c.setResposta(NAO_VOU.equals(estado) ? NAO_VOU : VOU.equals(estado) || CADUCADA.equals(estado) ? VOU : null);
					c.setCaducada(CADUCADA.equals(estado));
					c.setSemConta(SEM_CONTA.equals(estado));
					if (comDatas) {
						c.setDataResposta(NAO_VOU.equals(estado) ? datasAusencia.get(c.getIdJogador())
								: conf != null && !SEM_CONTA.equals(estado) ? conf.data : null);
					}
				}
				if ("ACRESCENTADO".equals(c.getOrigem())) {
					psOrigem.setInt(1, c.getIdJogador());
					psOrigem.setInt(2, tenant);
					psOrigem.setInt(3, t.d.getIdEquipa());
					ResultSet rsOrigem = psOrigem.executeQuery();
					c.setEquipaOrigem(rsOrigem.next() ? rsOrigem.getString(1) : null);
				}
				detalhe.getConvocatoria().add(c);
			}
			PreparedStatement psRetirados = conn.prepareStatement("select j.id, j.nome, tc.origem from treino_convocado tc "
					+ "inner join jogador j on j.id = tc.id_jogador where tc.id_treino = ? and tc.estado = '0' "
					+ "and j.estado = '1' order by j.nome");
			psRetirados.setInt(1, t.d.getId());
			ResultSet rsRetirados = psRetirados.executeQuery();
			while (rsRetirados.next()) {
				TreinoConvocadoData c = new TreinoConvocadoData();
				c.setIdJogador(rsRetirados.getInt(1));
				c.setNome(rsRetirados.getString(2));
				c.setOrigem(rsRetirados.getString(3));
				detalhe.getConvocatoria().add(c);
			}
			if (t.fotografado()) {
				// R15, Q15: jogador que entrou na equipa depois da aprovacao
				PreparedStatement psNovos = conn.prepareStatement("select j.id, j.nome from jogador j where j.estado = '1' "
						+ "and j.id in (select eej.id_jogador from escalao_epoca_jogador eej where eej.id_escalao_epoca = ?) "
						+ "and not exists (select 1 from treino_convocado tc where tc.id_treino = ? and tc.id_jogador = j.id) "
						+ "order by j.nome");
				psNovos.setInt(1, t.d.getIdEquipa());
				psNovos.setInt(2, t.d.getId());
				ResultSet rsNovos = psNovos.executeQuery();
				while (rsNovos.next()) {
					TreinoConvocadoData c = new TreinoConvocadoData();
					c.setIdJogador(rsNovos.getInt(1));
					c.setNome(rsNovos.getString(2));
					c.setOrigem("OMISSAO");
					c.setNovoNaEquipa(true);
					detalhe.getConvocatoria().add(c);
				}
			}

			if (parmIdentidade.isAdmin() || parmIdentidade.isTreinador()) {
				// revisao I2: juntado em Java (u.nome e h.alteracao tem colacoes diferentes)
				PreparedStatement psHistorico = conn.prepareStatement("select h.`data`, u.nome, h.alteracao "
						+ "from micro_ciclo_historico h inner join utilizadores u on u.id = h.id_utilizador "
						+ "where h.id_treino = ? order by h.`data`, h.id");
				psHistorico.setInt(1, t.d.getId());
				ResultSet rsHistorico = psHistorico.executeQuery();
				while (rsHistorico.next()) {
					// mesmo formato de loadHistoricoByID: "data  nome   alteracao"
					detalhe.getHistorico().add(rsHistorico.getString(1) + "  " + rsHistorico.getString(2) + "   "
							+ rsHistorico.getString(3));
				}
			}

			boolean passou = inicioPassou(t.d.getData(), t.inicioSessao());
			boolean fechado = CANCELADO.equals(t.estadoBD) || t.realizado();
			boolean agir = podeAgirNaEquipa(parmIdentidade, t.d.getIdEquipa(), equipas);
			detalhe.setPodeAprovar(t.d.isPodeAprovar());
			detalhe.setPodeAjustar(!t.evento() && agir && !fechado && !passou);
			detalhe.setPodeMarcarPresenca(!t.evento() && APROVADO.equals(t.estadoBD) && !t.realizado() && passou
					&& podeMarcarPresenca(parmIdentidade, t.d.getIdEquipa(), equipas));
			detalhe.setPodeEditar(parmIdentidade.isAdmin() && !fechado && !passou && !CANCELADO.equals(t.mcEstado)
					&& !semanaTerminou(t.dataInicio));
			// v7 (R192): os excluidos de cada fisico (so convocados efetivos) e se ainda se podem mudar
			if (t.d.getFisicos() != null && !t.d.getFisicos().isEmpty()) {
				List<Integer> idsFisicos = new ArrayList<>();
				for (FisicoStaffData f : t.d.getFisicos()) {
					idsFisicos.add(f.getId());
				}
				Set<Integer> convocados = nomes(convocatoriaEfetiva(conn, t)).keySet();
				Map<Integer, Set<Integer>> excluidos = excluidosDe(conn, tenant, idsFisicos);
				for (FisicoStaffData f : t.d.getFisicos()) {
					List<Integer> lista = new ArrayList<>();
					for (Integer idJogador : excluidos.getOrDefault(f.getId(), Set.of())) {
						if (convocados.contains(idJogador)) {
							lista.add(idJogador);
						}
					}
					java.util.Collections.sort(lista);
					f.setExcluidos(lista);
					f.setPodeAjustar(agir && !fechado && !inicioPassou(t.d.getData(), f.getHoraInicio()));
				}
			}

			// v3 (R66): ultima decisao (aprovacao ou rejeicao), para todo o staff, como o "Aprovado por"
			if (APROVADO.equals(t.estadoBD) || REJEITADO.equals(t.estadoBD)) {
				PreparedStatement psDecisao = conn.prepareStatement("select h.`data`, u.nome, h.alteracao "
						+ "from micro_ciclo_historico h inner join utilizadores u on u.id = h.id_utilizador "
						+ "where h.id_treino = ? and (h.alteracao like 'Aprovado%' or h.alteracao like 'Rejeitado%') "
						+ "order by h.id desc LIMIT 1");
				psDecisao.setInt(1, t.d.getId());
				ResultSet rsDecisao = psDecisao.executeQuery();
				if (rsDecisao.next()) {
					String alteracao = rsDecisao.getString("alteracao");
					detalhe.setDecisao(alteracao.startsWith("Aprovado") ? APROVADO : REJEITADO);
					detalhe.setDecididoPor(rsDecisao.getString("nome"));
					detalhe.setDataDecisao(rsDecisao.getTimestamp("data"));
					java.util.regex.Matcher m = DECISAO.matcher(alteracao);
					detalhe.setQualidadeDecisao(!m.find() ? null
							: TEXTO_TREINADOR.equals(m.group(2)) ? QUALIDADE_TREINADOR : QUALIDADE_ADMINISTRADOR);
				}
			}
			return detalhe;
		});
	}

	static TreinoAusenciaData lerAusencia(ResultSet rs) throws SQLException {
		TreinoAusenciaData a = new TreinoAusenciaData();
		a.setIdJogador(rs.getInt("id_jogador"));
		a.setNome(rs.getString("nome"));
		a.setCategoria(rs.getString("categoria"));
		a.setMotivo(rs.getString("motivo"));
		Timestamp indicacao = rs.getTimestamp("data_indicacao");
		a.setDataIndicacao(indicacao);
		a.setEstado(rs.getString("estado"));
		a.setPendenteConfirmacao("1".equals(rs.getString("pendente_confirmacao")));
		a.setIdIndisponibilidade(getInteger(rs, "id_indisponibilidade"));
		a.setIndisponivelDe(getInteger(rs, "indisp_inicio"));
		a.setIndisponivelAte(getInteger(rs, "indisp_fim"));
		return a;
	}

	// ==================== 12-15. presencas a partir do plano (Entrega C) ====================

	static String categoriaTexto(String parmCategoria) {
		switch (parmCategoria) {
		case "DOENCA":
			return "Doença";
		case "LESAO":
			return "Lesão";
		case "ESCOLA":
			return "Escola";
		case "FAMILIA":
			return "Família";
		default:
			return "Outro";
		}
	}

	private static int ausenciasAtivas(Connection conn, int parmIdTreino) throws SQLException {
		PreparedStatement preparedStatement = conn
				.prepareStatement("select count(*) from treino_ausencia where id_treino = ? and estado = 'ATIVA'");
		preparedStatement.setInt(1, parmIdTreino);
		ResultSet rs = preparedStatement.executeQuery();
		return rs.next() ? rs.getInt(1) : 0;
	}

	// ==================== cor da equipa (escalao_epoca.cor, V8) ====================

	private static final Pattern COR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

	/**
	 * Cores das equipas do tenant. A coluna vem da V8: sem ela, o erro fica isolado aqui
	 * (sem cor) e a leitura do Micro Ciclo continua a funcionar.
	 */
	private static Map<Integer, String> coresEquipas(Connection conn, int parmTenantId) {
		Map<Integer, String> cores = new HashMap<>();
		// revisao: statement e result set fechados (try-with-resources)
		try (PreparedStatement preparedStatement = conn.prepareStatement("select ee.id, ee.cor from escalao_epoca ee "
				+ "inner join epoca e on e.id = ee.id_epoca where e.tenant_id = ? and ee.cor is not null")) {
			preparedStatement.setInt(1, parmTenantId);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					cores.put(rs.getInt(1), rs.getString(2));
				}
			}
		} catch (SQLException e) {
			log.warn("MicroCicloHelper | coresEquipas | Sem cores (V8 em falta?): {}", e.getMessage());
		}
		return cores;
	}

	// ==================== v4: jogos da semana (BK-v4-4, BK-v4-5) ====================

	/**
	 * Jogos do tenant na semana (so leitura). left join clube: as ligacoes do jogo nao tem FK.
	 * Com parmIdJogador (v5, R109-R111): os jogos em que consta e nao esta INDISPONIVEL (convocado)
	 * e os jogos das equipas em que esta (ativo) nesta epoca; convocado so preenchido neste caso.
	 */
	static List<JogoSemanaData> jogosSemana(Connection conn, int parmTenantId, int parmSegunda, Integer parmIdJogador)
			throws SQLException {
		String convocado = "exists (select 1 from jogo_jogador jj where jj.id_jogo = j.id "
				+ "and jj.id_jogador = ? and jj.estado <> 'INDISPONÍVEL')";
		String sql = "select j.id, j.equipa_id, coalesce(nullif(ee.nome, ''), es.nome) as nome_equipa, j.tipoequipa, "
				+ "j.`data`, j.hora, j.hora_concentracao, j.tipo_local, j.equipa_adv_id, c.nome as nome_adv, "
				+ "j.competicao_descritivo, j.estado, j.golos_equipa, j.golos_equipa_adv"
				+ (parmIdJogador != null ? ", " + convocado + " as convocado" : "") + " from jogo j "
				+ "inner join escalao_epoca ee on ee.id = j.equipa_id left join escalao es on es.id = ee.id_escalao "
				+ "inner join epoca e on e.id = ee.id_epoca left join clube c on c.id = j.equipa_adv_id "
				+ "where e.tenant_id = ? and j.`data` between ? and ? "
				+ (parmIdJogador != null ? "and (" + convocado + " or exists (select 1 from escalao_epoca_jogador eej "
						+ "inner join jogador jg on jg.id = eej.id_jogador and jg.estado = '1' "
						+ "where eej.id_escalao_epoca = j.equipa_id and eej.id_jogador = ?)) " : "")
				+ "order by j.`data`, j.hora, j.id";
		List<JogoSemanaData> jogos = new ArrayList<>();
		try (PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
			int i = 1;
			if (parmIdJogador != null) {
				preparedStatement.setInt(i++, parmIdJogador);
			}
			preparedStatement.setInt(i++, parmTenantId);
			preparedStatement.setDate(i++, java.sql.Date.valueOf(paraData(parmSegunda)));
			preparedStatement.setDate(i++, java.sql.Date.valueOf(paraData(somaDias(parmSegunda, 6))));
			if (parmIdJogador != null) {
				preparedStatement.setInt(i++, parmIdJogador);
				preparedStatement.setInt(i, parmIdJogador);
			}
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					JogoSemanaData j = new JogoSemanaData();
					j.setId(rs.getInt("id"));
					j.setIdEquipa(rs.getInt("equipa_id"));
					j.setNomeEquipa(rs.getString("nome_equipa"));
					j.setTipoEquipa(rs.getString("tipoequipa"));
					j.setData(paraInt(rs.getDate("data").toLocalDate()));
					j.setHora(vazioANull(rs.getString("hora")));
					j.setHoraConcentracao(vazioANull(rs.getString("hora_concentracao")));
					String local = rs.getString("tipo_local");
					j.setCasaFora("C".equalsIgnoreCase(local) ? "CASA" : "F".equalsIgnoreCase(local) ? "FORA" : null);
					j.setIdAdversario(getInteger(rs, "equipa_adv_id"));
					j.setNomeAdversario(rs.getString("nome_adv"));
					j.setCompeticao(rs.getString("competicao_descritivo"));
					j.setEstado(rs.getString("estado"));
					if ("CONCLUIDO".equals(j.getEstado())) {
						j.setGolosEquipa(getInteger(rs, "golos_equipa"));
						j.setGolosAdversario(getInteger(rs, "golos_equipa_adv"));
					}
					if (parmIdJogador != null) {
						j.setConvocado(rs.getBoolean("convocado"));
					}
					jogos.add(j);
				}
			}
		}
		return jogos;
	}

	private static String vazioANull(String parmValor) {
		return parmValor == null || parmValor.trim().isEmpty() ? null : parmValor.trim();
	}

	// ==================== v5: calendario do clube para o atleta (BK-v5-5, R138/R141) ====================

	/**
	 * W6, RGPD: leitura propria e fechada (SemanaClubeAtletaData). So itens APROVADOS de micro ciclos
	 * PUBLICADOS; observacoes so nos itens do proprio atleta; nada de convocatorias, contagens,
	 * ausencias, motivos, avisos ou quem aprovou. So ATLETA, o proprio jogador, semana atual ou seguinte.
	 */
	public sm.core.data.SemanaClubeAtletaData getSemanaClubeAtleta(Identidade parmIdentidade, int parmIdJogador,
			int parmData) {
		exigeAtleta(parmIdentidade, parmIdJogador);
		int segunda = segundaDe(parmData);
		int atual = segundaDe(hoje());
		if (segunda != atual && segunda != somaDias(atual, 7)) {
			throw new PedidoInvalidoException("Só se podem ver a semana atual e a seguinte.");
		}
		int tenant = parmIdentidade.getTenantId();
		return leitura("getSemanaClubeAtleta", conn -> {
			sm.core.data.SemanaClubeAtletaData semana = new sm.core.data.SemanaClubeAtletaData();
			semana.setDataInicio(segunda);
			// v9 (R250): sem micro ciclo publicado, sem itens (a consulta dos itens ja exige PUBLICADO); os jogos como antes
			MicroCicloData mcSemana = microCicloDaSemana(conn, tenant, segunda);
			semana.setPublicado(mcSemana != null && PUBLICADO.equals(mcSemana.getEstado()));
			Map<Integer, String> nomesEquipas = new LinkedHashMap<>();
			Map<Integer, String> nomesGrupos = new LinkedHashMap<>();

			// meu = convocado efetivo (a regra de treinoDoAtleta); os eventos nao tem convocados
			try (PreparedStatement psItens = conn.prepareStatement("select t.id, t.id_equipa, "
					+ "coalesce(nullif(ee.nome, ''), es.nome) as nome_equipa, t.id_grupo, g.nome as nome_grupo, t.tipo, "
					+ "t.tipo_descricao, t.sub_grupo, t.`data`, t.hora_inicio, t.hora_fim, t.local_treino, t.observacoes, "
					+ "exists (select 1 from treino_convocado tc inner join jogador jg on jg.id = tc.id_jogador "
					+ "and jg.estado = '1' where tc.id_treino = t.id and tc.id_jogador = ? and tc.estado = '1' "
					+ "and (tc.origem = 'ACRESCENTADO' or exists (select 1 from escalao_epoca_jogador eej "
					+ "where eej.id_escalao_epoca = t.id_equipa and eej.id_jogador = tc.id_jogador))) as meu "
					+ "from treino t inner join micro_ciclo m on m.id = t.id_micro_ciclo "
					+ "left join escalao_epoca ee on ee.id = t.id_equipa left join escalao es on es.id = ee.id_escalao "
					+ "left join grupo_evento g on g.id = t.id_grupo and g.tenant_id = m.tenant_id "
					+ "where m.tenant_id = ? and m.estado = 'PUBLICADO' and t.estado = 'APROVADO' "
					+ "and t.id_treino_principal is null " // v6 (CA124): o fisico ligado so em fisicos do principal
					+ "and t.`data` between ? and ? order by t.`data`, t.hora_inicio, t.id")) {
				psItens.setInt(1, parmIdJogador);
				psItens.setInt(2, tenant);
				psItens.setInt(3, segunda);
				psItens.setInt(4, somaDias(segunda, 6));
				try (ResultSet rs = psItens.executeQuery()) {
					while (rs.next()) {
						sm.core.data.SemanaClubeAtletaData.Item item = new sm.core.data.SemanaClubeAtletaData.Item();
						item.setId(rs.getInt("id"));
						item.setIdEquipa(rs.getInt("id_equipa"));
						item.setNomeEquipa(rs.getString("nome_equipa"));
						item.setIdGrupo(rs.getInt("id_grupo"));
						item.setNomeGrupo(rs.getString("nome_grupo"));
						item.setTipo(rs.getString("tipo"));
						item.setTipoDescricao(rs.getString("tipo_descricao"));
						item.setSubGrupo(rs.getString("sub_grupo"));
						item.setData(rs.getInt("data"));
						item.setHoraInicio(rs.getString("hora_inicio"));
						item.setHoraFim(rs.getString("hora_fim"));
						item.setLocal(rs.getString("local_treino"));
						item.setMeu(rs.getBoolean("meu"));
						// Q76: observacoes so nos treinos do proprio atleta
						item.setObservacoes(item.isMeu() ? rs.getString("observacoes") : null);
						semana.getItens().add(item);
						if (item.getIdGrupo() > 0) {
							nomesGrupos.put(item.getIdGrupo(), item.getNomeGrupo());
						} else {
							nomesEquipas.put(item.getIdEquipa(), item.getNomeEquipa());
						}
					}
				}
			}
			List<Integer> idsItens = new ArrayList<>();
			for (sm.core.data.SemanaClubeAtletaData.Item item : semana.getItens()) {
				idsItens.add(item.getId());
			}
			Map<Integer, List<FisicoStaffData>> fisicosItens = fisicosDe(conn, tenant, idsItens);
			List<Integer> idsFisicosItens = new ArrayList<>();
			fisicosItens.values().forEach(lista -> lista.forEach(f -> idsFisicosItens.add(f.getId())));
			Map<Integer, Set<Integer>> excluidosItens = excluidosDe(conn, tenant, idsFisicosItens);
			for (sm.core.data.SemanaClubeAtletaData.Item item : semana.getItens()) {
				// v7 (R195, CA137): todos os fisicos do item, com meu = participa; copia campo a campo
				List<FisicoAtletaData> lista = new ArrayList<>();
				for (FisicoStaffData f : fisicosItens.getOrDefault(item.getId(), new ArrayList<>())) {
					lista.add(FisicoAtletaData.de(f, item.isMeu()
							&& !excluidosItens.getOrDefault(f.getId(), Set.of()).contains(parmIdJogador)));
				}
				item.setFisicos(lista);
			}

			// equipas do jogador nesta epoca (ativo), para o "meu" dos jogos
			Set<Integer> equipasJogador = new HashSet<>();
			try (PreparedStatement psEquipas = conn.prepareStatement("select eej.id_escalao_epoca from escalao_epoca_jogador eej "
					+ "inner join jogador jg on jg.id = eej.id_jogador and jg.estado = '1' "
					+ "inner join escalao_epoca ee on ee.id = eej.id_escalao_epoca inner join epoca e on e.id = ee.id_epoca "
					+ "where eej.id_jogador = ? and e.tenant_id = ?")) {
				psEquipas.setInt(1, parmIdJogador);
				psEquipas.setInt(2, tenant);
				try (ResultSet rs = psEquipas.executeQuery()) {
					while (rs.next()) {
						equipasJogador.add(rs.getInt(1));
					}
				}
			}
			Set<Integer> convocados = new HashSet<>();
			try (PreparedStatement psConvocado = conn.prepareStatement("select jj.id_jogo from jogo_jogador jj "
					+ "inner join jogo j on j.id = jj.id_jogo where jj.id_jogador = ? and jj.estado <> 'INDISPONÍVEL' "
					+ "and j.`data` between ? and ?")) {
				psConvocado.setInt(1, parmIdJogador);
				psConvocado.setDate(2, java.sql.Date.valueOf(paraData(segunda)));
				psConvocado.setDate(3, java.sql.Date.valueOf(paraData(somaDias(segunda, 6))));
				try (ResultSet rs = psConvocado.executeQuery()) {
					while (rs.next()) {
						convocados.add(rs.getInt(1));
					}
				}
			}
			for (JogoSemanaData j : jogosSemana(conn, tenant, segunda, null)) {
				// copia campo a campo: sem concentracao, golos nem estado (contrato fechado)
				sm.core.data.SemanaClubeAtletaData.Jogo jogo = new sm.core.data.SemanaClubeAtletaData.Jogo();
				jogo.setId(j.getId());
				jogo.setIdEquipa(j.getIdEquipa());
				jogo.setNomeEquipa(j.getNomeEquipa());
				jogo.setTipoEquipa(j.getTipoEquipa());
				jogo.setData(j.getData());
				jogo.setHora(j.getHora());
				jogo.setCasaFora(j.getCasaFora());
				jogo.setIdAdversario(j.getIdAdversario());
				jogo.setNomeAdversario(j.getNomeAdversario());
				jogo.setCompeticao(j.getCompeticao());
				jogo.setConvocado(convocados.contains(j.getId()));
				jogo.setMeu(jogo.isConvocado() || equipasJogador.contains(j.getIdEquipa()));
				jogo.setPodeAbrir(jogo.isConvocado()); // R111: a pagina do jogo so para convocados
				semana.getJogos().add(jogo);
				nomesEquipas.put(j.getIdEquipa(), j.getNomeEquipa());
			}

			Map<Integer, String> cores = coresEquipas(conn, tenant);
			nomesEquipas.forEach((id, nome) -> semana.getCores()
					.add(new sm.core.data.SemanaClubeAtletaData.Cor(id, nome, cores.get(id))));
			for (sm.core.data.GrupoEventoData g : gruposEvento(conn, tenant)) {
				if (nomesGrupos.containsKey(g.getIdGrupo())) {
					semana.getGrupos().add(new sm.core.data.SemanaClubeAtletaData.Grupo(g.getIdGrupo(), g.getNome(), g.getCor()));
				}
			}
			return semana;
		});
	}

	/** R96, Q55: so ATLETA, o proprio jogador, semana atual ou seguinte; sem obs nem colegas. */
	public List<JogoSemanaData> getJogosAtleta(Identidade parmIdentidade, int parmIdJogador, int parmData) {
		exigeAtleta(parmIdentidade, parmIdJogador);
		int segunda = segundaDe(parmData);
		int atual = segundaDe(hoje());
		if (segunda != atual && segunda != somaDias(atual, 7)) {
			throw new PedidoInvalidoException("Só se podem ver os jogos desta semana e da seguinte.");
		}
		return leitura("getJogosAtleta", conn -> {
			List<JogoSemanaData> jogos = jogosSemana(conn, parmIdentidade.getTenantId(), segunda, parmIdJogador);
			// Balnearios (P15, B15): so o lado da equipa, em casa, com o micro ciclo da semana PUBLICADO
			MicroCicloData mc = microCicloDaSemana(conn, parmIdentidade.getTenantId(), segunda);
			if (mc != null && PUBLICADO.equals(mc.getEstado())) {
				List<Integer> ids = new ArrayList<>();
				for (JogoSemanaData j : jogos) {
					if ("CASA".equals(j.getCasaFora())) {
						ids.add(j.getId());
					}
				}
				Map<Integer, List<sm.core.data.BalnearioAtribuidoData>> balnearios = BalnearioHelper
						.nomesJogosEquipa(conn, parmIdentidade.getTenantId(), ids);
				for (JogoSemanaData j : jogos) {
					j.setBalnearios(balnearios.get(j.getId()));
				}
			}
			return jogos;
		});
	}

	// ==================== v4: copiar treino (BK-v4-6) ====================

	private static final int MAXIMO_COPIAS = 28;

	public ResultadoCopiaTreinoData copiaTreino(Identidade parmIdentidade, int parmIdTreino,
			PedidoCopiaTreinoData parmPedido) {
		if (parmPedido == null || parmPedido.getDatas() == null || parmPedido.getDatas().isEmpty()) {
			throw new PedidoInvalidoException("Indique pelo menos uma data.");
		}
		Set<Integer> datas = new java.util.LinkedHashSet<>();
		for (Integer data : parmPedido.getDatas()) {
			if (data == null) {
				throw new PedidoInvalidoException("Data inválida.");
			}
			paraData(data);
			datas.add(data);
		}
		if (datas.size() > MAXIMO_COPIAS) {
			throw new PedidoInvalidoException("No máximo " + MAXIMO_COPIAS + " datas.");
		}
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		ResultadoCopiaTreinoData resultado = new ResultadoCopiaTreinoData();
		Map<Integer, Integer> criados = new LinkedHashMap<>(); // id -> data
		Map<Integer, List<String>> avisosCopia = new HashMap<>(); // v6: fisicos nao copiados, por copia
		Treino origem = emTransacao("copiaTreino", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, tenant);
			if (t.ligado()) {
				throw new PedidoInvalidoException("Copie o treino principal."); // v6 (X5)
			}
			List<FisicoStaffData> fisicosOrigem = fisicosDe(conn, tenant, t.d.getId());
			List<Integer> idsFisicosOrigem = new ArrayList<>();
			for (FisicoStaffData f : fisicosOrigem) {
				idsFisicosOrigem.add(f.getId());
			}
			Map<Integer, Set<Integer>> excluidosOrigem = excluidosDe(conn, tenant, idsFisicosOrigem); // v7 (R202)
			String descricao = (t.evento() ? t.d.getNomeGrupo() : t.d.getNomeEquipa()) + " " + dia(t.d.getData()) + " "
					+ t.d.getHoraInicio();
			List<Integer> pendentes = new ArrayList<>();
			for (Integer data : datas) {
				try {
					MicroCicloData mc = microCicloDaSemana(conn, tenant, segundaDe(data));
					if (mc == null || CANCELADO.equals(mc.getEstado())) {
						throw new ConflitoException("Não há micro ciclo em rascunho ou publicado nesta semana.");
					}
					TreinoData v = new TreinoData();
					v.setIdEquipa(t.d.getIdEquipa());
					v.setTipo(t.d.getTipo());
					v.setTipoDescricao(t.d.getTipoDescricao());
					v.setSubGrupo(t.d.getSubGrupo());
					v.setData(data);
					v.setHoraInicio(t.d.getHoraInicio());
					v.setHoraFim(t.d.getHoraFim());
					v.setLocal(t.d.getLocal());
					v.setObservacoes(t.d.getObservacoes());
					v.setIdGrupo(t.d.getIdGrupo());
					v.setAprovarAoPublicar(t.d.isAprovarAoPublicar());
					validaTreino(v);
					if (!t.evento() && t.equipaEpoca != mc.getIdEpoca()) {
						throw new ConflitoException("A equipa não é da época desse micro ciclo.");
					}
					validaPlaneamento(conn, tenant, mc.getDataInicio(), mc.getEstado(), mc.getIdEpoca(), v);
					if (t.evento()) {
						// v5: copia de um evento, com o grupo e a marca "Ja aprovado"; sem notificacoes
						int idEvento = criaEvento(conn, mc, v, t.d.getNomeGrupo(), utilizador);
						historico(conn, mc.getId(), idEvento, utilizador, "Evento criado (cópia de " + descricao + ")");
						criados.put(idEvento, data);
						continue;
					}
					String estado = RASCUNHO.equals(mc.getEstado()) ? RASCUNHO : PENDENTE;
					int id = insereTreino(conn, mc.getId(), v, estado, utilizador);
					insereConvocatoriaOmissao(conn, id, v.getIdEquipa(), utilizador);
					if (parmPedido.isManterAjustes()) {
						// retirados e acrescentados da origem (Q53 b)
						try (PreparedStatement psAjustes = conn.prepareStatement("insert into treino_convocado(id_treino, "
								+ "id_jogador, origem, estado, data_alteracao, id_utilizador_alteracao) select ?, tc.id_jogador, "
								+ "tc.origem, tc.estado, now(), ? from treino_convocado tc where tc.id_treino = ? "
								+ "and (tc.estado = '0' or tc.origem = 'ACRESCENTADO') on duplicate key update "
								+ "estado = values(estado), origem = values(origem), data_alteracao = now(), "
								+ "id_utilizador_alteracao = values(id_utilizador_alteracao)")) {
							psAjustes.setInt(1, id);
							psAjustes.setInt(2, utilizador);
							psAjustes.setInt(3, t.d.getId());
							psAjustes.executeUpdate();
						}
					}
					// v6 (R184): os fisicos da origem, ligados a copia; os que nao passam ficam em aviso
					List<FisicoStaffData> copiados = new ArrayList<>();
					for (FisicoStaffData f : fisicosOrigem) {
						try {
							TreinoData vf = new TreinoData();
							vf.setIdEquipa(v.getIdEquipa());
							vf.setTipo("FISICO");
							vf.setSubGrupo(f.getSubGrupo());
							vf.setData(data);
							vf.setHoraInicio(f.getHoraInicio());
							vf.setHoraFim(f.getHoraFim());
							vf.setLocal(f.getLocal());
							validaTreino(vf);
							validaPlaneamento(conn, tenant, mc.getDataInicio(), mc.getEstado(), mc.getIdEpoca(), vf);
							int idFisico = insereFisico(conn, mc.getId(), id, v.getIdEquipa(), data, f, estado, utilizador);
							copiados.add(f);
							if (parmPedido.isManterAjustes() && excluidosOrigem.containsKey(f.getId())) {
								copiaExcluidos(conn, id, v.getIdEquipa(), estado, idFisico, excluidosOrigem.get(f.getId()),
										utilizador);
							}
						} catch (PedidoInvalidoException | ConflitoException e) {
							avisosCopia.computeIfAbsent(id, k -> new ArrayList<>())
									.add("Físico " + intervalo(f) + " não copiado: " + e.getMessage());
						}
					}
					historico(conn, mc.getId(), id, utilizador, "Treino criado (cópia de " + descricao + ")"
							+ incluiFisicos(copiados));
					sincronizaTreino(conn, tenant, id, null); // Indisponibilidades (Z3)
					criados.put(id, data);
					if (PENDENTE.equals(estado)) {
						pendentes.add(id);
					}
				} catch (PedidoInvalidoException | ConflitoException e) {
					Map<String, Object> falha = new LinkedHashMap<>();
					falha.put("data", data);
					falha.put("motivo", e.getMessage());
					resultado.getFalhas().add(falha);
				}
			}
			if (!pendentes.isEmpty()) {
				// W5: uma notificacao agregada por destinatario
				notifica(conn, parmIdentidade, notificacaoHelper.destinatariosEquipa(conn, t.d.getIdEquipa(), tenant),
						"TREINO_NOVO", pendentes.get(0), null, pendentes.size() == 1
								? "Novo treino " + t.d.getNomeEquipa() + " aguarda aprovação"
								: pendentes.size() + " novos treinos de " + t.d.getNomeEquipa() + " aguardam aprovação");
			}
			return t;
		});
		log.info("MicroCicloHelper | copiaTreino | treino:{} criados:{} falhas:{}", parmIdTreino, criados.size(),
				resultado.getFalhas().size());
		for (Map.Entry<Integer, Integer> criado : criados.entrySet()) {
			TreinoData d = getTreinoData(parmIdentidade, criado.getKey());
			if (criado.getValue() == origem.d.getData()) {
				d.getAvisos().add("Mesmo dia do treino original");
			}
			d.getAvisos().addAll(avisosCopia.getOrDefault(criado.getKey(), new ArrayList<>()));
			resultado.getCriados().add(d);
		}
		return resultado;
	}

	/**
	 * Grava a cor da equipa (por epoca). Sem linha em micro_ciclo_historico: o historico
	 * exige um micro ciclo (id_micro_ciclo NOT NULL) e a cor e da equipa, nao de uma semana.
	 * A alteracao fica no log.
	 */
	public Map<String, Object> setCorEquipa(Identidade parmIdentidade, Integer parmIdEquipa, String parmCor) {
		if (parmIdEquipa == null || parmIdEquipa <= 0) {
			throw new PedidoInvalidoException("Equipa inválida.");
		}
		String cor = parmCor == null || parmCor.trim().isEmpty() ? null : parmCor.trim();
		if (cor != null && !COR.matcher(cor).matches()) {
			throw new PedidoInvalidoException("Cor inválida: use o formato #RRGGBB.");
		}
		String corFinal = cor == null ? null : cor.toUpperCase();
		int tenant = parmIdentidade.getTenantId();
		if (!autorizacaoHelper.pertenceAoTenant(sm.core.security.TipoRecurso.EQUIPA, parmIdEquipa, tenant)) {
			throw new AcessoNegadoException();
		}
		emTransacao("setCorEquipa", conn -> {
			PreparedStatement preparedStatement = conn.prepareStatement("update escalao_epoca ee inner join epoca e "
					+ "on e.id = ee.id_epoca set ee.cor = ? where ee.id = ? and e.tenant_id = ?");
			preparedStatement.setString(1, corFinal);
			preparedStatement.setInt(2, parmIdEquipa);
			preparedStatement.setInt(3, tenant);
			preparedStatement.executeUpdate();
			return true;
		});
		log.info("MicroCicloHelper | setCorEquipa | tenant:{} equipa:{} cor:{} utilizador:{}", tenant, parmIdEquipa,
				corFinal, parmIdentidade.getIdUtilizador());
		Map<String, Object> resultado = new LinkedHashMap<>();
		resultado.put("idEquipa", parmIdEquipa);
		resultado.put("cor", corFinal);
		return resultado;
	}

	private List<TreinoData> treinosComContagens(Connection conn, Identidade parmIdentidade, PreparedStatement parmPs,
			Set<Integer> parmEquipas) throws SQLException {
		ResultSet rs = parmPs.executeQuery();
		List<Treino> treinos = new ArrayList<>();
		while (rs.next()) {
			treinos.add(lerTreino(rs));
		}
		List<TreinoData> lista = new ArrayList<>();
		Map<Integer, String> cores = coresEquipas(conn, parmIdentidade.getTenantId());
		List<Integer> ids = new ArrayList<>();
		for (Treino t : treinos) {
			ids.add(t.d.getId());
		}
		Map<Integer, List<FisicoStaffData>> fisicos = fisicosDe(conn, parmIdentidade.getTenantId(), ids); // v6 (CA119)
		List<Integer> idsFisicos = new ArrayList<>();
		fisicos.values().forEach(l -> l.forEach(f -> idsFisicos.add(f.getId())));
		Map<Integer, Set<Integer>> excluidos = excluidosDe(conn, parmIdentidade.getTenantId(), idsFisicos);
		for (Treino t : treinos) {
			if (t.d.getFisicos() != null) {
				t.d.setFisicos(fisicos.getOrDefault(t.d.getId(), new ArrayList<>()));
			}
			t.d.setCorEquipa(cores.get(t.d.getIdEquipa()));
			// Indisponibilidades (D-1): o indisponivel nao conta nos convocados nem nos participantes dos fisicos
			List<TreinoConvocadoData> conv = participantes(convocatoriaEfetiva(conn, t),
					indisponiveisNoTreino(conn, t.d.getId()));
			t.d.setNumConvocados(conv.size());
			if (t.d.getFisicos() != null) {
				for (FisicoStaffData f : t.d.getFisicos()) {
					f.setNumParticipantes(participantes(conv, excluidos.get(f.getId())).size()); // v7 (sem numAusencias)
				}
			}
			t.d.setNumAusencias(ausenciasAtivas(conn, t.d.getId()));
			preencheDerivados(t, parmIdentidade, parmEquipas);
			lista.add(t.d);
		}
		return lista;
	}

	/** R51, R52: treinos do dia (aprovados e pendentes) e aprovados por marcar (Q38). */
	public Map<String, Object> getTreinosEquipaDia(Identidade parmIdentidade, int parmIdEquipa, int parmData) {
		paraData(parmData);
		int tenant = parmIdentidade.getTenantId();
		if (!autorizacaoHelper.pertenceAoTenant(sm.core.security.TipoRecurso.EQUIPA, parmIdEquipa, tenant)) {
			throw new AcessoNegadoException();
		}
		return leitura("getTreinosEquipaDia", conn -> {
			Set<Integer> equipas = equipasDoUtilizador(conn, parmIdentidade.getIdUtilizador());
			Map<String, Object> resultado = new LinkedHashMap<>();
			PreparedStatement psDia = conn.prepareStatement(SQL_TREINO + "and t.id_equipa = ? and t.`data` = ? "
					+ "and t.estado in ('APROVADO', 'PENDENTE') and m.estado = 'PUBLICADO' and t.id_treino_principal is null "
					+ "order by t.hora_inicio, t.id");
			psDia.setInt(1, tenant);
			psDia.setInt(2, parmIdEquipa);
			psDia.setInt(3, parmData);
			resultado.put("dia", treinosComContagens(conn, parmIdentidade, psDia, equipas));
			int hoje = hoje();
			PreparedStatement psPorMarcar = conn.prepareStatement(SQL_TREINO + "and t.id_equipa = ? "
					+ "and t.estado = 'APROVADO' and t.id_presenca is null and t.`data` between ? and ? "
					+ "and t.id_treino_principal is null order by t.`data`, t.hora_inicio, t.id");
			psPorMarcar.setInt(1, tenant);
			psPorMarcar.setInt(2, parmIdEquipa);
			psPorMarcar.setInt(3, somaDias(segundaDe(hoje), -7));
			psPorMarcar.setInt(4, somaDias(hoje, -1));
			resultado.put("porMarcar", treinosComContagens(conn, parmIdentidade, psPorMarcar, equipas));
			// Indisponibilidades (Z15, Q13): jogadores da equipa indisponiveis nesse dia, para a presenca manual
			List<Map<String, Object>> indisponiveis = new ArrayList<>();
			try (PreparedStatement psIndisp = conn.prepareStatement("select i.id_jogador, i.categoria, i.motivo "
					+ "from indisponibilidade i inner join jogador j on j.id = i.id_jogador where j.estado = '1' "
					+ "and j.tenant_id = ? and i.estado = 'ATIVA' and i.data_inicio <= ? and i.data_fim >= ? "
					+ "and i.id_jogador in (select eej.id_jogador from escalao_epoca_jogador eej where eej.id_escalao_epoca = ?) "
					+ "order by i.id_jogador")) {
				psIndisp.setInt(1, tenant);
				psIndisp.setInt(2, parmData);
				psIndisp.setInt(3, parmData);
				psIndisp.setInt(4, parmIdEquipa);
				try (ResultSet rs = psIndisp.executeQuery()) {
					while (rs.next()) {
						int idJogador = rs.getInt(1);
						String categoria = categoriaTexto(rs.getString(2));
						String motivo = rs.getString(3);
						boolean pode = IndisponibilidadeHelper.podeVerMotivo(parmIdentidade, equipas, parmIdEquipa,
								equipasDoAtleta(conn, tenant, idJogador));
						Map<String, Object> item = new LinkedHashMap<>();
						item.put("idJogador", idJogador);
						item.put("motivo", !pode ? IndisponibilidadeHelper.TEXTO_MASCARA
								: motivo == null ? categoria : categoria + ": " + motivo);
						indisponiveis.add(item);
					}
				}
			}
			resultado.put("indisponiveis", indisponiveis);
			return resultado;
		});
	}

	/** Q23, Q40: permissoes e estado para marcar a presenca do treino. */
	private static void verificaMarcacao(Connection conn, Identidade parmIdentidade, Treino t) throws SQLException {
		if (t.evento()) {
			throw new ConflitoException("Um evento não tem presença."); // v5 (R128)
		}
		if (t.ligado()) {
			throw new ConflitoException(MSG_PRESENCA_PRINCIPAL); // v6 (R179)
		}
		if (!podeMarcarPresenca(parmIdentidade, t.d.getIdEquipa(),
				equipasDoUtilizador(conn, parmIdentidade.getIdUtilizador()))) {
			throw new AcessoNegadoException();
		}
		if (!APROVADO.equals(t.estadoBD)) {
			throw new ConflitoException("O treino não está aprovado.");
		}
		if (t.realizado()) {
			throw new ConflitoException("O treino já tem presença marcada.");
		}
		// v6 (X8): a partir do inicio da sessao (o fisico "antes", se houver)
		if (!inicioPassou(t.d.getData(), t.inicioSessao())) {
			throw new ConflitoException("A presença só pode ser marcada a partir das " + t.inicioSessao() + ".");
		}
	}

	/** R18, R42, R56.4: presenca pre-preenchida a partir da convocatoria e das ausencias. */
	public Map<String, Object> getPresencaTreino(Identidade parmIdentidade, int parmIdTreino) {
		return leitura("getPresencaTreino", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, parmIdentidade.getTenantId());
			verificaMarcacao(conn, parmIdentidade, t);
			PresencaData presenca = new PresencaData(0, t.d.getData(), t.d.getHoraInicio(), t.d.getIdEquipa(),
					t.d.getNomeEquipa(), null, parmIdentidade.getIdUtilizador(), parmIdentidade.getUser());

			PreparedStatement psAusencias = conn.prepareStatement(SQL_AUSENCIA);
			psAusencias.setInt(1, t.d.getId());
			ResultSet rsAusencias = psAusencias.executeQuery();
			Map<Integer, TreinoAusenciaData> ausencias = new HashMap<>();
			while (rsAusencias.next()) {
				TreinoAusenciaData a = lerAusencia(rsAusencias);
				ausencias.put(a.getIdJogador(), a);
			}

			Set<Integer> equipasConta = equipasDoUtilizador(conn, parmIdentidade.getIdUtilizador());
			Set<Integer> convocados = new HashSet<>();
			List<Integer> indisponiveis = new ArrayList<>();
			for (TreinoConvocadoData c : convocatoriaEfetiva(conn, t)) {
				convocados.add(c.getIdJogador());
				TreinoAusenciaData a = ausencias.get(c.getIdJogador());
				if (a == null) {
					presenca.getJogadoresPresenca().add(new PresencaJogadorData(c.getIdJogador(), c.getNome(), "", ""));
				} else if (a.getIdIndisponibilidade() != null) {
					// Indisponibilidades (Z16, Q3, Q13): estado da indisponibilidade; motivo so para quem pode ver
					indisponiveis.add(c.getIdJogador());
					boolean pode = IndisponibilidadeHelper.podeVerMotivo(parmIdentidade, equipasConta, t.d.getIdEquipa(),
							equipasDoAtleta(conn, parmIdentidade.getTenantId(), c.getIdJogador()));
					String categoria = categoriaTexto(a.getCategoria());
					presenca.getJogadoresPresenca().add(new PresencaJogadorData(c.getIdJogador(), c.getNome(),
							IndisponibilidadeHelper.estadoPresenca(a.getCategoria()), !pode ? IndisponibilidadeHelper.TEXTO_MASCARA
									: a.getMotivo() == null ? categoria : categoria + ": " + a.getMotivo()));
				} else {
					String categoria = categoriaTexto(a.getCategoria());
					presenca.getJogadoresPresenca().add(new PresencaJogadorData(c.getIdJogador(), c.getNome(),
							"Ausente (Avisou)", a.getMotivo() == null ? categoria : categoria + ": " + a.getMotivo()));
				}
			}
			PreparedStatement psEquipa = conn.prepareStatement("select j.id, j.nome from jogador j where j.estado = '1' "
					+ "and j.id in (select eej.id_jogador from escalao_epoca_jogador eej where eej.id_escalao_epoca = ?) "
					+ "order by j.nome");
			psEquipa.setInt(1, t.d.getIdEquipa());
			ResultSet rsEquipa = psEquipa.executeQuery();
			while (rsEquipa.next()) {
				if (!convocados.contains(rsEquipa.getInt(1))) {
					presenca.getJogadoresPresenca()
							.add(new PresencaJogadorData(rsEquipa.getInt(1), rsEquipa.getString(2), "Não convocado", ""));
				}
			}

			// D5: staff da equipa no momento da marcacao (mesmo criterio de getEquipaIDLight)
			PreparedStatement psStaff = conn.prepareStatement("select s.id, s.nome from escalao_epoca_staff ees "
					+ "inner join staff s on s.id = ees.id_staff where ees.id_escalao_epoca = ? and s.id_jogador = 0 "
					+ "union select s.id, j.nome from escalao_epoca_staff ees inner join staff s on s.id = ees.id_staff "
					+ "inner join jogador j on j.id = s.id_jogador where ees.id_escalao_epoca = ? and s.id_jogador <> 0");
			psStaff.setInt(1, t.d.getIdEquipa());
			psStaff.setInt(2, t.d.getIdEquipa());
			ResultSet rsStaff = psStaff.executeQuery();
			while (rsStaff.next()) {
				presenca.getStaffPresenca().add(new PresencaStaffData(rsStaff.getInt(1), rsStaff.getString(2), "", ""));
			}

			PreparedStatement psManual = conn.prepareStatement("select p.id from presencas p where p.id_equipa = ? "
					+ "and p.`data` = ? and p.hora = ? and not exists (select 1 from treino t where t.id_presenca = p.id) "
					+ "order by p.id LIMIT 1");
			psManual.setInt(1, t.d.getIdEquipa());
			psManual.setInt(2, t.d.getData());
			psManual.setString(3, t.d.getHoraInicio());
			ResultSet rsManual = psManual.executeQuery();

			Map<String, Object> resultado = new LinkedHashMap<>();
			resultado.put("presenca", presenca);
			resultado.put("idPresencaManual", rsManual.next() ? rsManual.getInt(1) : null);
			resultado.put("indisponiveis", indisponiveis);
			// v7 (R199): participantes de cada fisico, so informativo (a presenca e uma por sessao)
			List<FisicoStaffData> fisicos = fisicosDe(conn, parmIdentidade.getTenantId(), t.d.getId());
			List<Integer> idsFisicos = new ArrayList<>();
			for (FisicoStaffData f : fisicos) {
				idsFisicos.add(f.getId());
			}
			Map<Integer, Set<Integer>> excluidos = excluidosDe(conn, parmIdentidade.getTenantId(), idsFisicos);
			List<Map<String, Object>> listaFisicos = new ArrayList<>();
			for (FisicoStaffData f : fisicos) {
				List<Integer> part = new ArrayList<>();
				for (Integer idJogador : convocados) {
					if (!excluidos.getOrDefault(f.getId(), Set.of()).contains(idJogador)) {
						part.add(idJogador);
					}
				}
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("idFisico", f.getId());
				item.put("horas", intervalo(f));
				item.put("posicao", f.getPosicao());
				item.put("participantes", part);
				listaFisicos.add(item);
			}
			resultado.put("fisicos", listaFisicos);
			return resultado;
		});
	}

	/** Liga a presenca ao treino (R18). 0 linhas: o treino ja tem presenca (409). */
	private static void ligaTreino(Connection conn, int parmIdTreino, int parmIdPresenca) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("update treino set id_presenca = ?, "
				+ "versao = versao + 1 where id = ? and estado = 'APROVADO' and id_presenca is null");
		preparedStatement.setInt(1, parmIdPresenca);
		preparedStatement.setInt(2, parmIdTreino);
		int linhas;
		try {
			linhas = preparedStatement.executeUpdate();
		} catch (SQLIntegrityConstraintViolationException e) {
			linhas = 0; // uq_treino_presenca: a presenca ja esta ligada a outro treino
		}
		if (linhas == 0) {
			throw new ConflitoException("O treino ou a presença já estão ligados.");
		}
	}

	/** Estados de marcar_presenca (o frontend nao grava com estado vazio). */
	private static final Set<String> ESTADOS_PRESENCA = Set.of("Presente", "Ausente (Avisou)", "Ausente (Não Avisou)",
			"Lesão", "Não convocado");

	/**
	 * Revisao B1: jogadores e staff do corpo tem de ser do tenant do token, com estado
	 * conhecido e motivo ate 100 (presenca_jogador.motivo). Os nomes passam a ser os da BD
	 * (vao para o email). Qualquer item invalido: 400, sem gravar nada.
	 */
	private static void validaCorpoPresenca(Connection conn, PresencaData parmPresenca, int parmTenantId)
			throws SQLException {
		PreparedStatement psJogador = conn.prepareStatement("select nome from jogador where id = ? and tenant_id = ?");
		for (PresencaJogadorData jogador : parmPresenca.getJogadoresPresenca()) {
			if (jogador == null) {
				throw new PedidoInvalidoException("Jogador inválido.");
			}
			psJogador.setInt(1, jogador.getId_jogador());
			psJogador.setInt(2, parmTenantId);
			ResultSet rs = psJogador.executeQuery();
			if (!rs.next()) {
				throw new PedidoInvalidoException("Jogador inválido: " + jogador.getId_jogador());
			}
			jogador.setNome_jogador(rs.getString(1));
			validaEstadoMotivo(jogador.getEstado(), jogador.getMotivo());
		}
		PreparedStatement psStaff = conn.prepareStatement("select coalesce(j.nome, s.nome) from staff s "
				+ "left join jogador j on j.id = s.id_jogador where s.id = ? and s.tenant_id = ?");
		for (PresencaStaffData staff : parmPresenca.getStaffPresenca()) {
			if (staff == null) {
				throw new PedidoInvalidoException("Staff inválido.");
			}
			psStaff.setInt(1, staff.getid_staff());
			psStaff.setInt(2, parmTenantId);
			ResultSet rs = psStaff.executeQuery();
			if (!rs.next()) {
				throw new PedidoInvalidoException("Staff inválido: " + staff.getid_staff());
			}
			staff.setnome_staff(rs.getString(1));
			validaEstadoMotivo(staff.getEstado(), staff.getMotivo());
		}
	}

	private static void validaEstadoMotivo(String parmEstado, String parmMotivo) {
		if (parmEstado == null || !ESTADOS_PRESENCA.contains(parmEstado)) {
			throw new PedidoInvalidoException("Estado de presença inválido: " + parmEstado);
		}
		if (parmMotivo != null && parmMotivo.length() > 100) {
			throw new PedidoInvalidoException("O motivo tem no máximo 100 caracteres.");
		}
	}

	/** D19: insere a presenca e liga-a ao treino numa transacao; email sem motivos (decisao do dono). */
	public Map<String, Object> marcaPresencaTreino(Identidade parmIdentidade, int parmIdTreino, PresencaData parmPresenca) {
		if (parmPresenca == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		int tenant = parmIdentidade.getTenantId();
		int idPresenca = emTransacao("marcaPresencaTreino", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, tenant);
			verificaMarcacao(conn, parmIdentidade, t);
			// equipa, data e hora vem do treino, nao do corpo
			parmPresenca.setId(0);
			parmPresenca.setId_escalao(t.d.getIdEquipa());
			parmPresenca.setData(t.d.getData());
			parmPresenca.setHora(t.d.getHoraInicio());
			parmPresenca.setEscalao_descricao(t.d.getNomeEquipa());
			parmPresenca.setId_utilizador_criacao(parmIdentidade.getIdUtilizador());
			if (parmPresenca.getUser_criacao() == null) {
				parmPresenca.setUser_criacao(parmIdentidade.getUser());
			}
			if (parmPresenca.getJogadoresPresenca() == null) {
				parmPresenca.setJogadoresPresenca(new ArrayList<>());
			}
			if (parmPresenca.getStaffPresenca() == null) {
				parmPresenca.setStaffPresenca(new ArrayList<>());
			}
			validaCorpoPresenca(conn, parmPresenca, tenant);
			int id = presencaHelper.inserePresenca(conn, parmPresenca);
			ligaTreino(conn, t.d.getId(), id);
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdentidade.getIdUtilizador(),
					"Presença marcada (#" + id + ")");
			return id;
		});
		log.info("MicroCicloHelper | marcaPresencaTreino | treino:{} presenca:{}", parmIdTreino, idPresenca);
		try {
			presencaHelper.sendEmailPresencaRegisto(parmPresenca, String.valueOf(tenant), true);
		} catch (RuntimeException e) {
			// a presenca ja esta gravada: uma falha no email nao a pode dar como perdida
			log.error("MicroCicloHelper | marcaPresencaTreino | Erro no email: " + e.getMessage(), e);
		}
		Map<String, Object> resultado = new LinkedHashMap<>();
		resultado.put("idPresenca", idPresenca);
		return resultado;
	}

	/** R56.4: liga uma presenca manual ja existente (mesma equipa, data e hora) ao treino. */
	public Map<String, Object> ligaPresenca(Identidade parmIdentidade, int parmIdTreino, int parmIdPresenca) {
		int tenant = parmIdentidade.getTenantId();
		if (!autorizacaoHelper.pertenceAoTenant(sm.core.security.TipoRecurso.PRESENCA, parmIdPresenca, tenant)) {
			throw new AcessoNegadoException();
		}
		emTransacao("ligaPresenca", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, tenant);
			if (!podeMarcarPresenca(parmIdentidade, t.d.getIdEquipa(),
					equipasDoUtilizador(conn, parmIdentidade.getIdUtilizador()))) {
				throw new AcessoNegadoException();
			}
			if (t.evento()) {
				throw new ConflitoException("Um evento não tem presença."); // v5 (R128)
			}
			if (t.ligado()) {
				throw new ConflitoException(MSG_PRESENCA_PRINCIPAL); // v6 (R179)
			}
			if (!APROVADO.equals(t.estadoBD) || t.realizado()) {
				throw new ConflitoException("O treino não está aprovado ou já tem presença.");
			}
			PreparedStatement psPresenca = conn.prepareStatement("select 1 from presencas p where p.id = ? "
					+ "and p.id_equipa = ? and p.`data` = ? and p.hora = ? "
					+ "and not exists (select 1 from treino t where t.id_presenca = p.id)");
			psPresenca.setInt(1, parmIdPresenca);
			psPresenca.setInt(2, t.d.getIdEquipa());
			psPresenca.setInt(3, t.d.getData());
			psPresenca.setString(4, t.d.getHoraInicio());
			if (!psPresenca.executeQuery().next()) {
				throw new ConflitoException("A presença não corresponde ao treino ou já está ligada a outro treino.");
			}
			ligaTreino(conn, t.d.getId(), parmIdPresenca);
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdentidade.getIdUtilizador(),
					"Ligado à presença existente (#" + parmIdPresenca + ")");
			return true;
		});
		Map<String, Object> resultado = new LinkedHashMap<>();
		resultado.put("idPresenca", parmIdPresenca);
		return resultado;
	}

	// ==================== indisponibilidades (V16, desenho Z2/Z4) ====================

	/**
	 * Z2: aplica e retira, num treino, as ausencias vindas de indisponibilidades (idempotente). Nao faz nada em eventos,
	 * fisicos ligados, cancelados, com presenca ou ja comecados (R16, R18). Nao mexe na versao do treino.
	 *
	 * @param parmSoJogador so este jogador (null = todos os convocados efetivos)
	 * @return {aplicadas, retiradas}
	 */
	int[] sincronizaTreino(Connection conn, int parmTenantId, int parmIdTreino, Integer parmSoJogador)
			throws SQLException {
		int[] contagem = { 0, 0 };
		Treino t = carregaTreino(conn, parmIdTreino, parmTenantId);
		if (t.evento() || t.ligado() || CANCELADO.equals(t.estadoBD) || t.d.getIdPresenca() != null
				|| t.presencaPrincipal || inicioPassou(t.d.getData(), t.inicioSessao())) {
			return contagem;
		}
		Map<Integer, String> convocados = nomes(convocatoriaEfetiva(conn, t));
		if (parmSoJogador != null) {
			convocados.keySet().retainAll(Set.of(parmSoJogador));
		}
		int data = t.d.getData();

		// (a) retira: ja nao convocado, ou a indisponibilidade ja nao cobre a data ou ja nao esta ATIVA
		List<Object[]> retirar = new ArrayList<>(); // {idJogador, nome, inicio, fim, idUtilizador}
		try (PreparedStatement ps = conn.prepareStatement("select ta.id_jogador, j.nome, i.estado, i.data_inicio, "
				+ "i.data_fim, i.id_utilizador from treino_ausencia ta inner join indisponibilidade i on i.id = ta.id_indisponibilidade "
				+ "inner join jogador j on j.id = ta.id_jogador where ta.id_treino = ? and ta.estado = 'ATIVA'"
				+ (parmSoJogador != null ? " and ta.id_jogador = ?" : ""))) {
			ps.setInt(1, t.d.getId());
			if (parmSoJogador != null) {
				ps.setInt(2, parmSoJogador);
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					int inicio = rs.getInt("data_inicio");
					int fim = rs.getInt("data_fim");
					if (!convocados.containsKey(rs.getInt("id_jogador")) || !"ATIVA".equals(rs.getString("estado"))
							|| data < inicio || data > fim) {
						retirar.add(new Object[] { rs.getInt("id_jogador"), rs.getString("nome"), inicio, fim,
								rs.getInt("id_utilizador") });
					}
				}
			}
		}
		if (!retirar.isEmpty()) {
			try (PreparedStatement psDescarta = conn.prepareStatement("update treino_ausencia set estado = 'DESCARTADA', "
					+ "data_alteracao = now() where id_treino = ? and id_jogador = ? and estado = 'ATIVA' "
					+ "and id_indisponibilidade is not null")) {
				for (Object[] r : retirar) {
					psDescarta.setInt(1, t.d.getId());
					psDescarta.setInt(2, (Integer) r[0]);
					if (psDescarta.executeUpdate() > 0) {
						contagem[1]++;
						historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), (Integer) r[4], String.format(
								IndisponibilidadeHelper.TEXTO_HISTORICO_RETIRADA, ddmm((Integer) r[2]), ddmm((Integer) r[3]), r[1]));
					}
				}
			}
		}
		if (convocados.isEmpty()) {
			return contagem;
		}

		// (b) aplica: a cada convocado efetivo com uma indisponibilidade ATIVA que cubra a data
		List<Object[]> aplicar = new ArrayList<>(); // {id, idJogador, inicio, fim, categoria, motivo, idUtilizador}
		String marcas = String.join(", ", java.util.Collections.nCopies(convocados.size(), "?"));
		try (PreparedStatement ps = conn.prepareStatement("select id, id_jogador, data_inicio, data_fim, categoria, motivo, "
				+ "id_utilizador from indisponibilidade where estado = 'ATIVA' and data_inicio <= ? and data_fim >= ? "
				+ "and id_jogador in (" + marcas + ")")) {
			int i = 1;
			ps.setInt(i++, data);
			ps.setInt(i++, data);
			for (Integer idJogador : convocados.keySet()) {
				ps.setInt(i++, idJogador);
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					aplicar.add(new Object[] { rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getString(5),
							rs.getString(6), rs.getInt(7) });
				}
			}
		}
		for (Object[] ind : aplicar) {
			int idIndisp = (Integer) ind[0];
			int idJogador = (Integer) ind[1];
			String categoria = (String) ind[4];
			String motivo = (String) ind[5];
			try (PreparedStatement psAtual = conn.prepareStatement("select id_indisponibilidade, categoria, motivo "
					+ "from treino_ausencia where id_treino = ? and id_jogador = ? and estado = 'ATIVA'")) {
				psAtual.setInt(1, t.d.getId());
				psAtual.setInt(2, idJogador);
				try (ResultSet rs = psAtual.executeQuery()) {
					if (rs.next()) {
						Integer atual = getInteger(rs, "id_indisponibilidade");
						if (atual == null) {
							continue; // R11: a ausencia manual fica
						}
						if (atual == idIndisp) {
							if (!Objects.equals(categoria, rs.getString("categoria"))
									|| !Objects.equals(motivo, rs.getString("motivo"))) {
								// R6: editar a indisponibilidade atualiza a categoria e o motivo
								try (PreparedStatement psAtualiza = conn.prepareStatement("update treino_ausencia set "
										+ "categoria = ?, motivo = ?, data_alteracao = now() where id_treino = ? "
										+ "and id_jogador = ? and estado = 'ATIVA' and id_indisponibilidade = ?")) {
									psAtualiza.setString(1, categoria);
									psAtualiza.setString(2, motivo);
									psAtualiza.setInt(3, t.d.getId());
									psAtualiza.setInt(4, idJogador);
									psAtualiza.setInt(5, idIndisp);
									psAtualiza.executeUpdate();
								}
							}
							continue;
						}
					}
				}
			}
			try (PreparedStatement psIndica = conn.prepareStatement("insert into treino_ausencia(id_treino, id_jogador, "
					+ "categoria, motivo, estado, pendente_confirmacao, data_indicacao, data_alteracao, id_utilizador, "
					+ "id_indisponibilidade) values (?, ?, ?, ?, 'ATIVA', '0', now(), now(), ?, ?) on duplicate key update "
					+ "categoria = values(categoria), motivo = values(motivo), estado = 'ATIVA', pendente_confirmacao = '0', "
					+ "data_indicacao = now(), data_alteracao = now(), id_utilizador = values(id_utilizador), "
					+ "id_indisponibilidade = values(id_indisponibilidade)")) {
				psIndica.setInt(1, t.d.getId());
				psIndica.setInt(2, idJogador);
				psIndica.setString(3, categoria);
				psIndica.setString(4, motivo);
				psIndica.setInt(5, (Integer) ind[6]);
				psIndica.setInt(6, idIndisp);
				psIndica.executeUpdate();
			}
			apagaConfirmacoes(conn, t.d.getId(), idJogador); // R12: "Vou" e "Nao vou" exclusivos
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), (Integer) ind[6], String.format(
					IndisponibilidadeHelper.TEXTO_HISTORICO, ddmm((Integer) ind[2]), ddmm((Integer) ind[3]),
					convocados.get(idJogador)));
			contagem[0]++;
		}
		return contagem;
	}

	/** Z4 (R17): o atleta nao muda a resposta de um treino coberto por uma indisponibilidade (409). */
	static void exigeSemIndisponibilidade(Connection conn, int parmIdTreino, int parmIdJogador) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("select i.data_inicio, i.data_fim from treino_ausencia ta "
				+ "inner join indisponibilidade i on i.id = ta.id_indisponibilidade where ta.id_treino = ? "
				+ "and ta.id_jogador = ? and ta.estado = 'ATIVA'")) {
			ps.setInt(1, parmIdTreino);
			ps.setInt(2, parmIdJogador);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					throw new ConflitoException("Este treino está coberto pela tua indisponibilidade de " + ddmm(rs.getInt(1))
							+ " a " + ddmm(rs.getInt(2)) + ". Para mudar, edita ou cancela a indisponibilidade.");
				}
			}
		}
	}

	// ==================== 16-17. atleta (Entrega D) ====================

	static final Set<String> CATEGORIAS = Set.of("DOENCA", "LESAO", "ESCOLA", "FAMILIA", "OUTRO");
	private static final String MSG_PRAZO = "O prazo para avisar terminou. Fala com o teu treinador.";

	/** D24: treinos da semana em que o jogador e convocado efetivo, ja aprovados alguma vez. */
	private List<TreinoAtletaData> treinosAtleta(Connection conn, int parmTenantId, int parmIdJogador, int parmSegunda,
			int parmIdUtilizador)
			throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement(SQL_TREINO + "and t.`data` between ? and ? "
				// v9 (Z3, Z4): so micro ciclos visiveis ao atleta; Pendente/Rejeitado ja aprovado antes = "em revisao"
				+ "and " + MC_VISIVEL_ATLETA + " and t.data_aprovacao is not null "
				+ "and (t.estado in ('APROVADO', 'CANCELADO') or (t.estado in ('PENDENTE', 'REJEITADO') "
				+ "and m.estado = 'PUBLICADO')) and exists (select 1 "
				+ "from treino_convocado tc inner join jogador j on j.id = tc.id_jogador where tc.id_treino = t.id "
				+ "and tc.id_jogador = ? and tc.estado = '1' and j.estado = '1' and (tc.origem = 'ACRESCENTADO' "
				+ "or exists (select 1 from escalao_epoca_jogador eej where eej.id_escalao_epoca = t.id_equipa "
				+ "and eej.id_jogador = tc.id_jogador))) order by t.`data`, t.hora_inicio, t.id");
		preparedStatement.setInt(1, parmTenantId);
		preparedStatement.setInt(2, parmSegunda);
		preparedStatement.setInt(3, somaDias(parmSegunda, 6));
		preparedStatement.setInt(4, parmIdJogador);
		ResultSet rs = preparedStatement.executeQuery();
		List<Treino> treinos = new ArrayList<>();
		while (rs.next()) {
			treinos.add(lerTreino(rs));
		}

		PreparedStatement psAusencia = conn.prepareStatement(SQL_AUSENCIA + " and ta.id_jogador = ?");
		List<TreinoAtletaData> lista = new ArrayList<>();
		Map<Integer, String> cores = coresEquipas(conn, parmTenantId);
		// v5 (Q79): CONVOCATORIA_ENTROU por ler, desta conta, por treino
		Set<Integer> convocatoriaAlterada = new HashSet<>();
		try (PreparedStatement psAlterada = conn.prepareStatement("select distinct n.id_treino from notificacao n "
				+ "where n.id_utilizador = ? and n.tipo in ('CONVOCATORIA_ENTROU', 'FISICO_ENTROU') and n.data_leitura is null "
				+ "and n.id_treino is not null")) {
			psAlterada.setInt(1, parmIdUtilizador);
			try (ResultSet rsAlterada = psAlterada.executeQuery()) {
				while (rsAlterada.next()) {
					convocatoriaAlterada.add(rsAlterada.getInt(1));
				}
			}
		}
		// v6 (CA124): os fisicos ligados vem so no treino principal (sem convocatoria propria, nao entram na lista)
		List<Integer> ids = new ArrayList<>();
		for (Treino t : treinos) {
			ids.add(t.d.getId());
		}
		Map<Integer, List<FisicoAtletaData>> fisicos = fisicosDoAtleta(conn, parmTenantId, parmIdJogador, ids);
		Map<Integer, Map<Integer, Confirmacao>> confirmacoes = confirmacoesDe(conn, parmTenantId, ids); // v8
		Map<Integer, List<String>> balnearios = BalnearioHelper.nomesTreinos(conn, parmTenantId, ids); // P15
		for (Treino t : treinos) {
			TreinoAtletaData a = new TreinoAtletaData();
			if (!emRevisao(t) && !CANCELADO.equals(t.estadoBD)) {
				a.setBalnearios(balnearios.get(t.d.getId()));
			}
			a.setFisicos(fisicos.getOrDefault(t.d.getId(), new ArrayList<>()));
			a.setCorEquipa(cores.get(t.d.getIdEquipa()));
			a.setConvocatoriaAlterada(convocatoriaAlterada.contains(t.d.getId()));
			a.setId(t.d.getId());
			a.setTipo(t.d.getTipo());
			a.setTipoDescricao(t.d.getTipoDescricao());
			a.setNomeEquipa(t.d.getNomeEquipa());
			a.setSubGrupo(t.d.getSubGrupo());
			a.setData(t.d.getData());
			a.setHoraInicio(t.d.getHoraInicio());
			a.setHoraFim(t.d.getHoraFim());
			a.setLocal(t.d.getLocal());
			a.setObservacoes(t.d.getObservacoes());
			a.setEstado(t.d.getEstado());
			a.setVersao(t.d.getVersao());
			a.setPrazoTerminado(inicioPassou(t.d.getData(), inicioDoAtleta(t.d.getHoraInicio(), a.getFisicos())));
			for (Treino outro : treinos) {
				if (outro != t && !CANCELADO.equals(t.estadoBD) && !CANCELADO.equals(outro.estadoBD)
						&& !emRevisao(t) && !emRevisao(outro) && sobrepostos(t.d, outro.d)) {
					a.setSobreposto(true);
				}
			}
			psAusencia.setInt(1, t.d.getId());
			psAusencia.setInt(2, parmIdJogador);
			ResultSet rsAusencia = psAusencia.executeQuery();
			if (rsAusencia.next()) {
				TreinoAusenciaData ausencia = lerAusencia(rsAusencia);
				ausencia.setNome(null);
				a.setAusencia(ausencia);
			}
			// v8 (R220-R223): so a resposta e a sessao do proprio atleta (calculadas na leitura, sem escrever)
			a.setSessao(sessaoDoAtleta(t.d, a.getFisicos()));
			Confirmacao conf = confirmacoes.getOrDefault(t.d.getId(), Map.of()).get(parmIdJogador);
			if (a.getAusencia() != null) {
				a.setResposta(NAO_VOU);
			} else if (conf != null) {
				a.setResposta(VOU);
				a.setCaducada(caducou(conf.sessao, a.getSessao()));
				a.setDataConfirmacao(conf.data);
			}
			a.setPodeResponder(APROVADO.equals(t.estadoBD) && !t.realizado() && PUBLICADO.equals(t.mcEstado)
					&& !a.isPrazoTerminado());
			if (emRevisao(t)) {
				// v9 (Z4, R254): nunca saem horas, local, observacoes nem fisicos nao aprovados; "--:--" para os builds antigos
				a.setEmRevisao(true);
				a.setHoraInicio("--:--");
				a.setHoraFim(null);
				a.setLocal(null);
				a.setObservacoes(null);
				a.setFisicos(new ArrayList<>());
				a.setSessao(null);
				a.setCaducada(false);
				a.setPodeResponder(false);
				a.setPrazoTerminado(true);
				a.setSobreposto(false);
			}
			lista.add(a);
		}
		return lista;
	}

	/** v9 (Z4): Pendente ou Rejeitado ja aprovado antes, num micro ciclo publicado (o filtro de treinosAtleta). */
	private static boolean emRevisao(Treino t) {
		return PENDENTE.equals(t.estadoBD) || REJEITADO.equals(t.estadoBD);
	}

	/** v7 (R195, X5): por principal, so os fisicos que o jogador faz, copiados para FisicoAtletaData (meu = true). */
	private static Map<Integer, List<FisicoAtletaData>> fisicosDoAtleta(Connection conn, int parmTenantId,
			int parmIdJogador, List<Integer> parmIdsPrincipais) throws SQLException {
		Map<Integer, List<FisicoStaffData>> fisicos = fisicosDe(conn, parmTenantId, parmIdsPrincipais);
		List<Integer> idsFisicos = new ArrayList<>();
		fisicos.values().forEach(lista -> lista.forEach(f -> idsFisicos.add(f.getId())));
		Map<Integer, Set<Integer>> excluidos = excluidosDe(conn, parmTenantId, idsFisicos);
		Map<Integer, List<FisicoAtletaData>> mapa = new HashMap<>();
		fisicos.forEach((idPrincipal, lista) -> {
			List<FisicoAtletaData> meus = new ArrayList<>();
			for (FisicoStaffData f : lista) {
				if (!excluidos.getOrDefault(f.getId(), Set.of()).contains(parmIdJogador)) {
					meus.add(FisicoAtletaData.de(f, true));
				}
			}
			mapa.put(idPrincipal, meus);
		});
		return mapa;
	}

	/** v7 (R196): o inicio da primeira parte que o atleta faz (o principal ou um fisico dele mais cedo). */
	private static String inicioDoAtleta(String parmHoraPrincipal, List<? extends FisicoData> parmFisicos) {
		String inicio = parmHoraPrincipal;
		for (FisicoData f : parmFisicos) {
			if (f.getHoraInicio().compareTo(inicio) < 0) {
				inicio = f.getHoraInicio();
			}
		}
		return inicio;
	}

	static void exigeAtleta(Identidade parmIdentidade, Integer parmIdJogador) {
		if (!parmIdentidade.isAtleta() || parmIdentidade.getIdJogador() == null
				|| (parmIdJogador != null && !parmIdJogador.equals(parmIdentidade.getIdJogador()))) {
			throw new AcessoNegadoException();
		}
	}

	/** Q20: so a semana atual ou a seguinte. */
	public List<TreinoAtletaData> getTreinosAtleta(Identidade parmIdentidade, int parmIdJogador, int parmData) {
		exigeAtleta(parmIdentidade, parmIdJogador);
		int segunda = segundaDe(parmData);
		int atual = segundaDe(hoje());
		if (segunda != atual && segunda != somaDias(atual, 7)) {
			throw new PedidoInvalidoException("Só se podem ver os treinos desta semana e da seguinte.");
		}
		return leitura("getTreinosAtleta",
				conn -> treinosAtleta(conn, parmIdentidade.getTenantId(), parmIdJogador, segunda,
						parmIdentidade.getIdUtilizador()));
	}

	/** R37-R44: indicar, anular ou confirmar a ausencia; notifica os treinadores da equipa (R45, R46). */
	public TreinoAtletaData ausencia(Identidade parmIdentidade, int parmIdTreino, PedidoAusenciaData parmPedido) {
		exigeAtleta(parmIdentidade, null);
		int jid = parmIdentidade.getIdJogador();
		int tenant = parmIdentidade.getTenantId();
		String acao = parmPedido.getAcao() == null ? "" : parmPedido.getAcao().trim().toUpperCase();
		if (!Set.of("INDICAR", "ANULAR", "CONFIRMAR").contains(acao)) {
			throw new PedidoInvalidoException("Ação inválida.");
		}
		if (parmPedido.getVersao() == null) {
			throw new PedidoInvalidoException("Versão em falta.");
		}
		String categoria = parmPedido.getCategoria() == null ? null : parmPedido.getCategoria().trim().toUpperCase();
		String motivo = null;
		if ("INDICAR".equals(acao)) {
			if (categoria == null || !CATEGORIAS.contains(categoria)) {
				throw new PedidoInvalidoException("Escolha o motivo.");
			}
			motivo = limpa(parmPedido.getMotivo(), 90, "O motivo");
			if ("OUTRO".equals(categoria) && motivo == null) {
				throw new PedidoInvalidoException("Escreva o motivo.");
			}
		}
		if (!autorizacaoHelper.treinoDoAtleta(parmIdTreino, jid, tenant)) {
			throw new AcessoNegadoException();
		}
		String motivoFinal = motivo;
		int segunda = emTransacao("ausencia", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, tenant);
			exigeSemIndisponibilidade(conn, t.d.getId(), jid); // Indisponibilidades (Z4)
			if (!PUBLICADO.equals(t.mcEstado)) {
				throw new ConflitoException("O micro ciclo ainda não foi publicado."); // v9: defesa extra (treinoDoAtleta da 403)
			}
			if (!APROVADO.equals(t.estadoBD)) {
				throw new ConflitoException("O treino não está aprovado.");
			}
			// v7 (R196): o prazo e o inicio da primeira parte que o atleta faz
			List<FisicoAtletaData> meusFisicos = fisicosDoAtleta(conn, tenant, jid, List.of(t.d.getId()))
					.getOrDefault(t.d.getId(), new ArrayList<>());
			if (t.realizado() || inicioPassou(t.d.getData(), inicioDoAtleta(t.d.getHoraInicio(), meusFisicos))) {
				throw new ConflitoException(MSG_PRAZO);
			}
			if (parmPedido.getVersao() != t.d.getVersao()) {
				throw new ConflitoException(MSG_VERSAO);
			}
			PreparedStatement psNome = conn.prepareStatement("select nome from jogador where id = ?");
			psNome.setInt(1, jid);
			ResultSet rsNome = psNome.executeQuery();
			String nome = rsNome.next() ? rsNome.getString(1) : "";
			String treino = "treino " + t.d.getNomeEquipa() + " de " + dia(t.d.getData()) + " às " + t.d.getHoraInicio();

			String tipo;
			String historicoTexto;
			String texto;
			if ("INDICAR".equals(acao)) {
				PreparedStatement psIndica = conn.prepareStatement("insert into treino_ausencia(id_treino, id_jogador, "
						+ "categoria, motivo, estado, pendente_confirmacao, data_indicacao, data_alteracao, id_utilizador) "
						+ "values (?, ?, ?, ?, 'ATIVA', '0', now(), now(), ?) on duplicate key update "
						+ "categoria = values(categoria), motivo = values(motivo), estado = 'ATIVA', "
						+ "pendente_confirmacao = '0', data_indicacao = now(), data_alteracao = now(), "
						+ "id_utilizador = values(id_utilizador), id_indisponibilidade = null"); // o "Nao vou" manual deixa de vir da indisponibilidade
				psIndica.setInt(1, t.d.getId());
				psIndica.setInt(2, jid);
				psIndica.setString(3, categoria);
				psIndica.setString(4, motivoFinal);
				psIndica.setInt(5, parmIdentidade.getIdUtilizador());
				psIndica.executeUpdate();
				apagaConfirmacoes(conn, t.d.getId(), jid); // v8 (Y1): "Vou" e "Não vou" exclusivos
				tipo = "AUSENCIA_INDICADA";
				historicoTexto = "Ausência indicada | " + nome + " | " + categoriaTexto(categoria);
				texto = nome + " avisou que não vai ao " + treino;
			} else {
				boolean anular = "ANULAR".equals(acao);
				PreparedStatement psAltera = conn.prepareStatement(anular
						? "update treino_ausencia set estado = 'ANULADA', pendente_confirmacao = '0', data_alteracao = now() "
								+ "where id_treino = ? and id_jogador = ? and estado = 'ATIVA'"
						: "update treino_ausencia set pendente_confirmacao = '0', data_alteracao = now() "
								+ "where id_treino = ? and id_jogador = ? and estado = 'ATIVA' and pendente_confirmacao = '1'");
				psAltera.setInt(1, t.d.getId());
				psAltera.setInt(2, jid);
				if (psAltera.executeUpdate() == 0) {
					throw new ConflitoException(anular ? "Não tens nenhum aviso de falta neste treino."
							: "Não há nenhum aviso para confirmar neste treino.");
				}
				if (anular) {
					// v8 (Y7, R222): "Afinal vou" passa a "Vou", com a sessao atual (o pedido antigo nao traz a sessao)
					gravaConfirmacao(conn, t.d.getId(), jid, sessaoDoAtleta(t.d, meusFisicos), parmIdentidade.getIdUtilizador());
				}
				tipo = anular ? "AUSENCIA_ANULADA" : "AUSENCIA_CONFIRMADA";
				historicoTexto = (anular ? "Ausência anulada | " : "Ausência confirmada depois da alteração | ") + nome;
				texto = anular ? nome + " afinal vai ao " + treino
						: nome + " confirmou que não vai ao " + treino + " (alterado)";
			}
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdentidade.getIdUtilizador(), historicoTexto);
			notifica(conn, parmIdentidade, notificacaoHelper.destinatariosEquipa(conn, t.d.getIdEquipa(), tenant), tipo,
					t.d.getId(), jid, texto);
			return segundaDe(t.d.getData());
		});
		log.info("MicroCicloHelper | ausencia | treino:{} jogador:{} acao:{}", parmIdTreino, jid, acao);
		for (TreinoAtletaData a : leitura("ausencia", conn -> treinosAtleta(conn, tenant, jid, segunda, parmIdentidade.getIdUtilizador()))) {
			if (a.getId() == parmIdTreino) {
				return a;
			}
		}
		return null;
	}

	// ==================== v8: respostas do atleta (BK-v8-3, Y6-Y8) ====================

	private static final int MAXIMO_CONFIRMAR = 30;
	private static final String MSG_MUDOU = "O treino mudou entretanto.";

	/** Q116: null = o atleta pode responder; senao o motivo curto (o do lote). */
	private static String motivoSemResposta(Treino t, String parmInicioAtleta) {
		if (t.ligado() || t.evento()) {
			return "sem acesso";
		}
		if (!APROVADO.equals(t.estadoBD) || t.realizado() || !PUBLICADO.equals(t.mcEstado)) {
			return "não aprovado";
		}
		if (inicioPassou(t.d.getData(), parmInicioAtleta)) {
			return "prazo terminado";
		}
		return null;
	}

	private static boolean temAusenciaAtiva(Connection conn, int parmIdTreino, int parmIdJogador) throws SQLException {
		try (PreparedStatement preparedStatement = conn.prepareStatement("select 1 from treino_ausencia "
				+ "where id_treino = ? and id_jogador = ? and estado = 'ATIVA'")) {
			preparedStatement.setInt(1, parmIdTreino);
			preparedStatement.setInt(2, parmIdJogador);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				return rs.next();
			}
		}
	}

	/** O ANULAR de ausencia (historico e AUSENCIA_ANULADA aos treinadores), quando o "Vou" substitui um "Nao vou". */
	private void anulaAusenciaAtiva(Connection conn, Identidade parmIdentidade, Treino t, int parmIdJogador)
			throws SQLException {
		try (PreparedStatement psAnula = conn.prepareStatement("update treino_ausencia set estado = 'ANULADA', "
				+ "pendente_confirmacao = '0', data_alteracao = now() where id_treino = ? and id_jogador = ? and estado = 'ATIVA'")) {
			psAnula.setInt(1, t.d.getId());
			psAnula.setInt(2, parmIdJogador);
			if (psAnula.executeUpdate() == 0) {
				return;
			}
		}
		String nome = "";
		try (PreparedStatement psNome = conn.prepareStatement("select nome from jogador where id = ?")) {
			psNome.setInt(1, parmIdJogador);
			try (ResultSet rs = psNome.executeQuery()) {
				nome = rs.next() ? rs.getString(1) : "";
			}
		}
		historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdentidade.getIdUtilizador(), "Ausência anulada | " + nome);
		notifica(conn, parmIdentidade, notificacaoHelper.destinatariosEquipa(conn, t.d.getIdEquipa(),
				parmIdentidade.getTenantId()), "AUSENCIA_ANULADA", t.d.getId(), parmIdJogador, nome + " afinal vai ao treino "
						+ t.d.getNomeEquipa() + " de " + dia(t.d.getData()) + " às " + t.d.getHoraInicio());
	}

	/**
	 * R220-R224: "Vou" (com a sessao que o atleta viu) ou anular a confirmacao. So o ATLETA, numa sessao em que e
	 * convocado (o fisico ligado nao e uma sessao: 403). Sem historico nem notificacao do "Vou" (R238, R242).
	 */
	public TreinoAtletaData responde(Identidade parmIdentidade, int parmIdTreino,
			sm.core.data.PedidoRespostaData parmPedido) {
		exigeAtleta(parmIdentidade, null);
		if (parmPedido == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		String acao = parmPedido.getAcao() == null ? "" : parmPedido.getAcao().trim().toUpperCase();
		if (!Set.of(VOU, "ANULAR").contains(acao)) {
			throw new PedidoInvalidoException("Ação inválida.");
		}
		String sessao = parmPedido.getSessao() == null ? null : parmPedido.getSessao().trim();
		if (VOU.equals(acao) && (sessao == null || sessao.isEmpty())) {
			throw new PedidoInvalidoException("Sessão em falta.");
		}
		int jid = parmIdentidade.getIdJogador();
		int tenant = parmIdentidade.getTenantId();
		if (!autorizacaoHelper.treinoDoAtleta(parmIdTreino, jid, tenant)) {
			throw new AcessoNegadoException();
		}
		int segunda = emTransacao("responde", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, tenant);
			if (t.ligado() || t.evento()) {
				throw new AcessoNegadoException();
			}
			List<FisicoAtletaData> meus = fisicosDoAtleta(conn, tenant, jid, List.of(t.d.getId()))
					.getOrDefault(t.d.getId(), new ArrayList<>());
			String motivo = motivoSemResposta(t, inicioDoAtleta(t.d.getHoraInicio(), meus));
			if (motivo != null) {
				throw new ConflitoException("prazo terminado".equals(motivo) ? MSG_PRAZO : "O treino não está aprovado.");
			}
			if (VOU.equals(acao)) {
				if (!sessaoDoAtleta(t.d, meus).equals(sessao)) {
					throw new ConflitoException(MSG_MUDOU); // Y6
				}
				exigeSemIndisponibilidade(conn, t.d.getId(), jid); // Indisponibilidades (Z4)
				anulaAusenciaAtiva(conn, parmIdentidade, t, jid); // Y1: exclusivos na mesma transacao
				gravaConfirmacao(conn, t.d.getId(), jid, sessao, parmIdentidade.getIdUtilizador());
			} else {
				apagaConfirmacoes(conn, t.d.getId(), jid);
			}
			return segundaDe(t.d.getData());
		});
		log.info("MicroCicloHelper | responde | treino:{} jogador:{} acao:{}", parmIdTreino, jid, acao);
		for (TreinoAtletaData a : leitura("responde",
				conn -> treinosAtleta(conn, tenant, jid, segunda, parmIdentidade.getIdUtilizador()))) {
			if (a.getId() == parmIdTreino) {
				return a;
			}
		}
		return null;
	}

	/** Y8: "Confirmar todos": cada item com as regras do "Vou", sem lancar; nao mexe nos "Nao vou" (R223). */
	public sm.core.data.ResultadoConfirmarTodosData confirmaTodos(Identidade parmIdentidade, int parmIdJogador,
			sm.core.data.PedidoConfirmarTodosData parmPedido) {
		exigeAtleta(parmIdentidade, parmIdJogador);
		if (parmPedido == null || parmPedido.getItens() == null || parmPedido.getItens().isEmpty()) {
			throw new PedidoInvalidoException("Indique os treinos a confirmar.");
		}
		if (parmPedido.getItens().size() > MAXIMO_CONFIRMAR) {
			throw new PedidoInvalidoException("No máximo " + MAXIMO_CONFIRMAR + " treinos.");
		}
		Set<Integer> ids = new HashSet<>();
		for (sm.core.data.PedidoConfirmarTodosData.Item item : parmPedido.getItens()) {
			if (item == null || item.getId() == null || item.getSessao() == null || item.getSessao().trim().isEmpty()
					|| !ids.add(item.getId())) {
				throw new PedidoInvalidoException("Itens inválidos (id e sessão obrigatórios, sem repetidos).");
			}
		}
		int jid = parmIdentidade.getIdJogador();
		int tenant = parmIdentidade.getTenantId();
		sm.core.data.ResultadoConfirmarTodosData resultado = new sm.core.data.ResultadoConfirmarTodosData();
		Set<Integer> semanas = new java.util.TreeSet<>();
		Map<Integer, Boolean> acesso = new HashMap<>();
		for (Integer id : ids) {
			acesso.put(id, autorizacaoHelper.treinoDoAtleta(id, jid, tenant));
		}
		emTransacao("confirmaTodos", conn -> {
			for (sm.core.data.PedidoConfirmarTodosData.Item item : parmPedido.getItens()) {
				Treino t = null;
				if (acesso.get(item.getId())) {
					try {
						t = carregaTreino(conn, item.getId(), tenant);
					} catch (AcessoNegadoException e) {
						t = null;
					}
				}
				if (t == null) {
					resultado.getFalhas().add(falhaLote(item.getId(), "sem acesso"));
					continue;
				}
				semanas.add(segundaDe(t.d.getData()));
				List<FisicoAtletaData> meus = fisicosDoAtleta(conn, tenant, jid, List.of(t.d.getId()))
						.getOrDefault(t.d.getId(), new ArrayList<>());
				String motivo = motivoSemResposta(t, inicioDoAtleta(t.d.getHoraInicio(), meus));
				if (motivo == null && temAusenciaAtiva(conn, t.d.getId(), jid)) {
					motivo = "já respondeste";
				}
				if (motivo == null && !sessaoDoAtleta(t.d, meus).equals(item.getSessao().trim())) {
					motivo = "mudou entretanto";
				}
				if (motivo != null) {
					resultado.getFalhas().add(falhaLote(item.getId(), motivo));
					continue;
				}
				gravaConfirmacao(conn, t.d.getId(), jid, item.getSessao().trim(), parmIdentidade.getIdUtilizador());
				resultado.getConfirmados().add(item.getId());
			}
			return true;
		});
		log.info("MicroCicloHelper | confirmaTodos | jogador:{} confirmados:{} falhas:{}", jid,
				resultado.getConfirmados().size(), resultado.getFalhas().size());
		for (Integer segunda : semanas) {
			resultado.getTreinos().addAll(leitura("confirmaTodos",
					conn -> treinosAtleta(conn, tenant, jid, segunda, parmIdentidade.getIdUtilizador())));
		}
		return resultado;
	}

	/** R225: sessoes por responder ou caducadas, por comecar, da semana atual e da seguinte (so leitura). */
	public Map<String, Object> porConfirmarAtleta(Identidade parmIdentidade, int parmIdJogador) {
		exigeAtleta(parmIdentidade, parmIdJogador);
		int atual = segundaDe(hoje());
		int tenant = parmIdentidade.getTenantId();
		List<Map<String, Object>> itens = new ArrayList<>();
		Integer semana = null;
		for (int segunda : new int[] { atual, somaDias(atual, 7) }) {
			for (TreinoAtletaData a : leitura("porConfirmarAtleta",
					conn -> treinosAtleta(conn, tenant, parmIdJogador, segunda, parmIdentidade.getIdUtilizador()))) {
				if (a.isPodeResponder() && (a.getResposta() == null || a.isCaducada())) {
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("id", a.getId());
					item.put("data", a.getData());
					item.put("horaInicio", inicioDoAtleta(a.getHoraInicio(), a.getFisicos()));
					item.put("nomeEquipa", a.getNomeEquipa());
					item.put("sessao", a.getSessao());
					itens.add(item);
					semana = semana == null ? segunda : semana;
				}
			}
		}
		Map<String, Object> resultado = new LinkedHashMap<>();
		resultado.put("total", itens.size());
		resultado.put("semana", semana);
		resultado.put("itens", itens);
		return resultado;
	}

	// ==================== v7: "Os meus atletas" (BK-v7-6, X8, X9) ====================

	private static final int MAXIMO_EQUIPAS_ATLETAS = 20;

	private static String marcas(int parmN) {
		return String.join(", ", java.util.Collections.nCopies(parmN, "?"));
	}

	/** Item com os dados internos para os conflitos (nao saem no JSON). */
	private static final class ItemAtleta {
		SemanaAtletasData.Item item;
		int inicio;
		int fim;
		/** Sessao (principal) dos treinos; null nos jogos. */
		Integer sessao;
		String descricao;
		boolean ausente;
	}

	/**
	 * R205-R215: semana dos atletas das equipas (as pedidas ou as do utilizador). So ADMIN e TREINADOR; o TREINADOR so
	 * as suas equipas; 1 a 20 equipas do tenant. So leitura, com consultas fixas por pedido (sem N+1).
	 */
	public SemanaAtletasData getMeusAtletas(Identidade parmIdentidade, int parmData, List<Integer> parmEquipas) {
		if (!parmIdentidade.isAdmin() && !parmIdentidade.isTreinador()) {
			throw new AcessoNegadoException();
		}
		int segunda = segundaDe(parmData);
		int tenant = parmIdentidade.getTenantId();
		boolean admin = parmIdentidade.isAdmin();
		return leitura("getMeusAtletas", conn -> {
			Set<Integer> minhas = equipasDoUtilizador(conn, parmIdentidade.getIdUtilizador());
			Set<Integer> equipas = new java.util.LinkedHashSet<>();
			if (parmEquipas != null) {
				for (Integer id : parmEquipas) {
					if (id == null || id <= 0) {
						throw new PedidoInvalidoException("Equipa inválida.");
					}
					equipas.add(id);
				}
			}
			if (equipas.isEmpty()) {
				if (minhas.isEmpty()) {
					throw new PedidoInvalidoException("Escolha uma equipa."); // Q104
				}
				equipas.addAll(minhas);
			}
			if (equipas.size() > MAXIMO_EQUIPAS_ATLETAS) {
				throw new PedidoInvalidoException("No máximo " + MAXIMO_EQUIPAS_ATLETAS + " equipas.");
			}
			if (!admin && !minhas.containsAll(equipas)) {
				throw new AcessoNegadoException(); // CA150
			}

			SemanaAtletasData semana = new SemanaAtletasData();
			semana.setDataInicio(segunda);
			// equipas do tenant (nome e cor); uma que nao seja do tenant -> 403
			Map<Integer, String> cores = coresEquipas(conn, tenant);
			try (PreparedStatement psEquipas = conn.prepareStatement("select ee.id, coalesce(nullif(ee.nome, ''), es.nome) "
					+ "as nome_equipa from escalao_epoca ee inner join epoca e on e.id = ee.id_epoca "
					+ "left join escalao es on es.id = ee.id_escalao where e.tenant_id = ? and ee.id in ("
					+ marcas(equipas.size()) + ") order by nome_equipa, ee.id")) {
				int i = 1;
				psEquipas.setInt(i++, tenant);
				for (Integer id : equipas) {
					psEquipas.setInt(i++, id);
				}
				try (ResultSet rs = psEquipas.executeQuery()) {
					while (rs.next()) {
						SemanaAtletasData.Equipa e = new SemanaAtletasData.Equipa();
						e.setIdEquipa(rs.getInt(1));
						e.setNome(rs.getString(2));
						e.setCor(cores.get(e.getIdEquipa()));
						e.setMinha(admin || minhas.contains(e.getIdEquipa()));
						semana.getEquipas().add(e);
					}
				}
			}
			if (semana.getEquipas().size() != equipas.size()) {
				throw new AcessoNegadoException();
			}

			// 1. plantel (R206: cada atleta uma vez)
			Map<Integer, SemanaAtletasData.Atleta> atletas = new LinkedHashMap<>();
			try (PreparedStatement psPlantel = conn.prepareStatement("select eej.id_escalao_epoca, j.id, j.nome "
					+ "from escalao_epoca_jogador eej inner join jogador j on j.id = eej.id_jogador and j.estado = '1' "
					+ "inner join escalao_epoca ee on ee.id = eej.id_escalao_epoca inner join epoca e on e.id = ee.id_epoca "
					+ "where e.tenant_id = ? and eej.id_escalao_epoca in (" + marcas(equipas.size()) + ") order by j.nome, j.id")) {
				int i = 1;
				psPlantel.setInt(i++, tenant);
				for (Integer id : equipas) {
					psPlantel.setInt(i++, id);
				}
				try (ResultSet rs = psPlantel.executeQuery()) {
					while (rs.next()) {
						SemanaAtletasData.Atleta a = atletas.computeIfAbsent(rs.getInt(2), k -> new SemanaAtletasData.Atleta());
						a.setIdJogador(rs.getInt(2));
						a.setNome(rs.getString(3));
						if (!a.getEquipas().contains(rs.getInt(1))) {
							a.getEquipas().add(rs.getInt(1));
						}
					}
				}
			}

			// 2. treinos da semana (R209): Aprovado/Realizado e Pendente; Rascunho so ao ADMIN; sem eventos
			MicroCicloData mc = microCicloDaSemana(conn, tenant, segunda);
			if (mc != null && (CANCELADO.equals(mc.getEstado()) || (RASCUNHO.equals(mc.getEstado()) && !admin))) {
				mc = null;
			}
			semana.setSemPlano(mc == null);
			List<Treino> treinos = new ArrayList<>();
			if (mc != null && !atletas.isEmpty()) {
				try (PreparedStatement psTreinos = conn.prepareStatement(SQL_TREINO + "and t.id_micro_ciclo = ? "
						+ "and t.id_grupo is null and t.estado in ('APROVADO', 'PENDENTE'" + (admin ? ", 'RASCUNHO'" : "")
						+ ") order by t.`data`, t.hora_inicio, t.id")) {
					psTreinos.setInt(1, tenant);
					psTreinos.setInt(2, mc.getId());
					try (ResultSet rs = psTreinos.executeQuery()) {
						while (rs.next()) {
							treinos.add(lerTreino(rs));
						}
					}
				}
			}
			Map<Integer, Treino> porId = new HashMap<>();
			List<Integer> idsAutonomos = new ArrayList<>();
			List<Integer> idsLigados = new ArrayList<>();
			for (Treino t : treinos) {
				porId.put(t.d.getId(), t);
				(t.ligado() ? idsLigados : idsAutonomos).add(t.d.getId());
			}
			List<Integer> idsAtletas = new ArrayList<>(atletas.keySet());

			// 3. convocatoria efetiva em lote (v9: extraida para convocadosEmLote, tambem usada na publicacao)
			Map<Integer, Set<Integer>> convocados = convocadosEmLote(conn, treinos, idsAtletas);

			// 4. exclusoes dos fisicos ligados
			Map<Integer, Set<Integer>> excluidos = excluidosDe(conn, tenant, idsLigados);

			// 5. ausencias ativas (das sessoes) dos atletas
			Map<String, String[]> ausencias = new HashMap<>(); // "treino:jogador" -> {categoria, motivo}
			Map<String, Timestamp> datasAusencia = new HashMap<>(); // v8
			if (!idsAutonomos.isEmpty()) {
				try (PreparedStatement psAus = conn.prepareStatement("select ta.id_treino, ta.id_jogador, ta.categoria, "
						+ "ta.motivo, ta.data_indicacao from treino_ausencia ta where ta.estado = 'ATIVA' and ta.id_treino in ("
						+ marcas(idsAutonomos.size()) + ") and ta.id_jogador in (" + marcas(idsAtletas.size()) + ")")) {
					int i = 1;
					for (Integer id : idsAutonomos) {
						psAus.setInt(i++, id);
					}
					for (Integer id : idsAtletas) {
						psAus.setInt(i++, id);
					}
					try (ResultSet rs = psAus.executeQuery()) {
						while (rs.next()) {
							ausencias.put(rs.getInt(1) + ":" + rs.getInt(2),
									new String[] { rs.getString(3), rs.getString(4) });
							datasAusencia.put(rs.getInt(1) + ":" + rs.getInt(2), rs.getTimestamp(5));
						}
					}
				}
			}

			// 6. jogos da semana e em que o atleta consta sem ser INDISPONIVEL (Q106)
			Map<Integer, JogoSemanaData> jogos = new LinkedHashMap<>();
			for (JogoSemanaData j : jogosSemana(conn, tenant, segunda, null)) {
				jogos.put(j.getId(), j);
			}
			List<int[]> jogosAtletas = new ArrayList<>();
			if (!jogos.isEmpty() && !idsAtletas.isEmpty()) {
				try (PreparedStatement psJogos = conn.prepareStatement("select jj.id_jogo, jj.id_jogador from jogo_jogador jj "
						+ "inner join jogo j on j.id = jj.id_jogo inner join escalao_epoca ee on ee.id = j.equipa_id "
						+ "inner join epoca e on e.id = ee.id_epoca where e.tenant_id = ? and j.`data` between ? and ? "
						+ "and jj.estado <> 'INDISPONÍVEL' and jj.id_jogador in (" + marcas(idsAtletas.size()) + ")")) {
					int i = 1;
					psJogos.setInt(i++, tenant);
					psJogos.setDate(i++, java.sql.Date.valueOf(paraData(segunda)));
					psJogos.setDate(i++, java.sql.Date.valueOf(paraData(somaDias(segunda, 6))));
					for (Integer id : idsAtletas) {
						psJogos.setInt(i++, id);
					}
					try (ResultSet rs = psJogos.executeQuery()) {
						while (rs.next()) {
							jogosAtletas.add(new int[] { rs.getInt(1), rs.getInt(2) });
						}
					}
				}
			}

			// 7. v8: confirmacoes das sessoes e contas dos atletas (2 consultas fixas)
			Map<Integer, Map<Integer, Confirmacao>> confirmacoes = confirmacoesDe(conn, tenant, idsAutonomos);
			Set<Integer> contas = comConta(conn, tenant, idsAtletas);
			Map<Integer, List<FisicoData>> fisicosPorPrincipal = new HashMap<>();
			for (Treino t : treinos) {
				if (t.ligado()) {
					fisicosPorPrincipal.computeIfAbsent(t.idPrincipal, k -> new ArrayList<>()).add(comoFisico(t.d));
				}
			}
			Set<Integer> porConfirmar = new HashSet<>();

			// montagem em Java
			Map<Integer, List<ItemAtleta>> itens = new HashMap<>();
			for (Treino t : treinos) {
				Treino principal = t.ligado() ? porId.get(t.idPrincipal) : t;
				if (principal == null) {
					continue;
				}
				for (Integer idJogador : convocados.getOrDefault(principal.d.getId(), Set.of())) {
					if (t.ligado() && excluidos.getOrDefault(t.d.getId(), Set.of()).contains(idJogador)) {
						continue; // nao faz este fisico
					}
					boolean minha = admin || minhas.contains(t.d.getIdEquipa());
					String chave = principal.d.getId() + ":" + idJogador;
					ItemAtleta x = itemTreino(t, principal, ausencias.get(chave), minha);
					if (APROVADO.equals(principal.estadoBD) && PUBLICADO.equals(principal.mcEstado)) { // v9 (R246)
						// v8 (R233, R234): estado da sessao; o FISICO repete-o sem data; a data so nos itens minha
						List<FisicoData> meus = new ArrayList<>();
						for (FisicoData f : fisicosPorPrincipal.getOrDefault(principal.d.getId(), new ArrayList<>())) {
							if (!excluidos.getOrDefault(f.getId(), Set.of()).contains(idJogador)) {
								meus.add(f);
							}
						}
						Confirmacao conf = confirmacoes.getOrDefault(principal.d.getId(), Map.of()).get(idJogador);
						String estado = estadoResposta(ausencias.containsKey(chave), contas.contains(idJogador), conf,
								sessaoDoAtleta(principal.d, meus));
						sm.core.data.RespostaItemData resposta = new sm.core.data.RespostaItemData();
						resposta.setEstado(estado);
						if (minha && !t.ligado()) {
							resposta.setData(NAO_VOU.equals(estado) ? datasAusencia.get(chave)
									: conf != null && !SEM_CONTA.equals(estado) ? conf.data : null);
						}
						x.item.setResposta(resposta);
						if (!t.ligado() && !principal.realizado() && (SEM_RESPOSTA.equals(estado) || CADUCADA.equals(estado))
								&& !inicioPassou(principal.d.getData(), inicioDoAtleta(principal.d.getHoraInicio(), meus))) {
							porConfirmar.add(idJogador);
						}
					}
					itens.computeIfAbsent(idJogador, k -> new ArrayList<>()).add(x);
				}
			}
			for (int[] par : jogosAtletas) {
				JogoSemanaData j = jogos.get(par[0]);
				if (j != null) {
					itens.computeIfAbsent(par[1], k -> new ArrayList<>())
							.add(itemJogo(j, admin || minhas.contains(j.getIdEquipa())));
				}
			}

			int comConflitos = 0;
			for (SemanaAtletasData.Atleta a : atletas.values()) {
				List<ItemAtleta> lista = itens.getOrDefault(a.getIdJogador(), new ArrayList<>());
				lista.sort(java.util.Comparator.comparingInt((ItemAtleta x) -> x.item.getData())
						.thenComparingInt(x -> x.inicio));
				conflitos(a, lista);
				for (ItemAtleta x : lista) {
					a.getItens().add(x.item);
				}
				if (a.isComConflitos()) {
					comConflitos++;
				}
				a.setPorConfirmar(porConfirmar.contains(a.getIdJogador()));
				semana.getAtletas().add(a);
			}
			semana.setNumAtletasComConflitos(comConflitos);
			semana.setNumAtletasPorConfirmar(porConfirmar.size());
			return semana;
		});
	}

	/** X9: item de treino ou de fisico ligado; de outra equipa sem idTreino e com a ausencia so como "avisou". */
	private static ItemAtleta itemTreino(Treino t, Treino parmPrincipal, String[] parmAusencia, boolean parmMinha) {
		SemanaAtletasData.Item item = new SemanaAtletasData.Item();
		item.setChave("t" + t.d.getId());
		item.setTipo(t.ligado() ? "FISICO" : "TREINO");
		item.setIdTreino(parmMinha ? t.d.getId() : null);
		item.setIdPrincipal(t.ligado() ? t.idPrincipal : null);
		item.setIdEquipa(t.d.getIdEquipa());
		item.setNomeEquipa(t.d.getNomeEquipa());
		item.setTipoTreino(t.d.getTipo());
		item.setTipoDescricao(t.d.getTipoDescricao());
		item.setSubGrupo(t.d.getSubGrupo());
		item.setData(t.d.getData());
		item.setHoraInicio(t.d.getHoraInicio());
		item.setHoraFim(t.d.getHoraFim());
		item.setLocal(t.d.getLocal());
		item.setEstado(t.d.getEstado());
		item.setMinha(parmMinha);
		if (parmAusencia != null) {
			Map<String, Object> ausencia = new LinkedHashMap<>();
			if (parmMinha) {
				ausencia.put("categoria", parmAusencia[0]);
				ausencia.put("motivo", parmAusencia[1]);
			} else {
				ausencia.put("avisou", true); // Q108: sem categoria nem motivo de outras equipas
			}
			item.setAusencia(ausencia);
		}
		ItemAtleta x = new ItemAtleta();
		x.item = item;
		x.inicio = minutos(t.d.getHoraInicio());
		x.fim = minutosFim(t.d);
		x.sessao = parmPrincipal.d.getId();
		x.descricao = (t.ligado() ? "Físico " : "") + t.d.getNomeEquipa() + " " + intervalo(t.d);
		x.ausente = parmAusencia != null;
		return x;
	}

	/** Item de jogo (sem concentracao, golos nem estado). */
	private static ItemAtleta itemJogo(JogoSemanaData j, boolean parmMinha) {
		SemanaAtletasData.Item item = new SemanaAtletasData.Item();
		item.setChave("j" + j.getId());
		item.setTipo("JOGO");
		item.setIdJogo(j.getId());
		item.setIdEquipa(j.getIdEquipa());
		item.setNomeEquipa(j.getNomeEquipa());
		item.setTipoEquipa(j.getTipoEquipa());
		item.setData(j.getData());
		item.setHoraInicio(j.getHora());
		item.setCasaFora(j.getCasaFora());
		item.setNomeAdversario(j.getNomeAdversario());
		item.setMinha(parmMinha);
		ItemAtleta x = new ItemAtleta();
		x.item = item;
		boolean comHora = j.getHora() != null && HORA.matcher(j.getHora()).matches();
		int[] intervalo = comHora ? intervaloJogo(j) : new int[] { 24 * 60, 24 * 60 }; // sem hora: sem conflito
		x.inicio = intervalo[0];
		x.fim = intervalo[1];
		x.descricao = "Jogo " + j.getNomeEquipa() + (comHora ? " " + j.getHora() : "");
		x.ausente = !comHora;
		return x;
	}

	/** R212, Q109: sobreposicoes por dia, sem os itens com ausencia ativa nem os pares da mesma sessao. */
	private static void conflitos(SemanaAtletasData.Atleta parmAtleta, List<ItemAtleta> parmItens) {
		for (int i = 0; i < parmItens.size(); i++) {
			ItemAtleta a = parmItens.get(i);
			for (int k = i + 1; k < parmItens.size(); k++) {
				ItemAtleta b = parmItens.get(k);
				if (a.ausente || b.ausente || a.item.getData() != b.item.getData()
						|| (a.sessao != null && a.sessao.equals(b.sessao)) || !(a.inicio < b.fim && b.inicio < a.fim)) {
					continue;
				}
				a.item.setConflito(true);
				b.item.setConflito(true);
				parmAtleta.setComConflitos(true);
				parmAtleta.getConflitos().add(new SemanaAtletasData.Conflito(a.item.getData(),
						"Sobreposição: " + a.descricao + " e " + b.descricao));
			}
		}
	}
}
