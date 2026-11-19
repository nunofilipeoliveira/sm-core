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
	private static final Pattern HORA = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
	private static final String[] DIAS = { "seg", "ter", "qua", "qui", "sex", "sáb", "dom" };

	static final String MSG_VERSAO = "O treino foi alterado entretanto. Recarregue.";
	private static final String MSG_TRANSICAO = "Transição não permitida.";
	private static final String MSG_JA_COMECOU = "O treino já começou.";

	private static final String SQL_TREINO = "select t.id, t.id_micro_ciclo, t.id_equipa, t.tipo, t.tipo_descricao, "
			+ "t.sub_grupo, t.`data`, t.hora_inicio, t.hora_fim, t.local_treino, t.observacoes, t.estado, "
			+ "t.motivo_estado, t.versao, t.id_presenca, t.data_aprovacao, ua.nome as aprovado_por, "
			+ "t.origem_cancelamento, ee.id_epoca as equipa_epoca, "
			+ "t.id_grupo, g.nome as nome_grupo, t.aprovar_ao_publicar, "
			+ "m.estado as mc_estado, m.data_inicio, m.id_epoca, coalesce(nullif(ee.nome, ''), es.nome) as nome_equipa "
			+ "from treino t inner join micro_ciclo m on m.id = t.id_micro_ciclo "
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

		boolean realizado() {
			return APROVADO.equals(estadoBD) && d.getIdPresenca() != null;
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

	private static void historico(Connection conn, int parmIdMicroCiclo, Integer parmIdTreino, int parmIdUtilizador,
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

	private static Integer epocaAtiva(Connection conn, int parmTenantId) throws SQLException {
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
		return mc;
	}

	/** @throws AcessoNegadoException micro ciclo inexistente ou de outro tenant */
	private static MicroCicloData carregaMicroCiclo(Connection conn, int parmId, int parmTenantId) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select id, data_inicio, estado, "
				+ "motivo_cancelamento, id_epoca from micro_ciclo where id = ? and tenant_id = ?");
		preparedStatement.setInt(1, parmId);
		preparedStatement.setInt(2, parmTenantId);
		ResultSet rs = preparedStatement.executeQuery();
		if (!rs.next()) {
			throw new AcessoNegadoException();
		}
		return lerMicroCiclo(rs);
	}

	private static MicroCicloData microCicloDaSemana(Connection conn, int parmTenantId, int parmSegunda)
			throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select id, data_inicio, estado, "
				+ "motivo_cancelamento, id_epoca from micro_ciclo where tenant_id = ? and data_inicio = ?");
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
		d.setEstado(t.realizado() ? REALIZADO : t.estadoBD);
		return t;
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
	private static Set<Integer> equipasDoUtilizador(Connection conn, int parmIdUtilizador) throws SQLException {
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

	/** D10: ADMIN, ou TREINADOR com a equipa (sempre, em REPORT e ENFORCE). */
	private static boolean podeAgirNaEquipa(Identidade parmIdentidade, int parmIdEquipa, Set<Integer> parmEquipas) {
		return parmIdentidade.isAdmin() || (parmIdentidade.isTreinador() && parmEquipas.contains(parmIdEquipa));
	}

	private static void preencheDerivados(Treino t, Identidade parmIdentidade, Set<Integer> parmEquipas) {
		boolean passou = inicioPassou(t.d.getData(), t.d.getHoraInicio());
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

	private static Map<Integer, Integer> ausenciasAtivasPorTreino(Connection conn, int parmIdMicroCiclo)
			throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select ta.id_treino, count(*) from treino_ausencia ta "
				+ "inner join treino t on t.id = ta.id_treino where t.id_micro_ciclo = ? and ta.estado = 'ATIVA' "
				+ "group by ta.id_treino");
		preparedStatement.setInt(1, parmIdMicroCiclo);
		ResultSet rs = preparedStatement.executeQuery();
		Map<Integer, Integer> contagens = new HashMap<>();
		while (rs.next()) {
			contagens.put(rs.getInt(1), rs.getInt(2));
		}
		return contagens;
	}

	/** Treinos do micro ciclo, com contagens, derivados e avisos (R12, R17). */
	List<Treino> listaTreinos(Connection conn, Identidade parmIdentidade, int parmIdMicroCiclo) throws SQLException {
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
		Map<Integer, Integer> ausencias = ausenciasAtivasPorTreino(conn, parmIdMicroCiclo);
		Map<Integer, List<TreinoConvocadoData>> convocatorias = new HashMap<>();
		Map<Integer, String> cores = coresEquipas(conn, parmIdentidade.getTenantId());
		for (Treino t : treinos) {
			t.d.setCorEquipa(cores.get(t.d.getIdEquipa()));
			List<TreinoConvocadoData> conv = convocatoriaEfetiva(conn, t);
			convocatorias.put(t.d.getId(), conv);
			t.d.setNumConvocados(conv.size());
			t.d.setNumAusencias(ausencias.getOrDefault(t.d.getId(), 0));
			preencheDerivados(t, parmIdentidade, equipas);
		}
		List<JogoSemanaData> jogos = treinos.isEmpty() ? new ArrayList<>()
				: jogosSemana(conn, parmIdentidade.getTenantId(), treinos.get(0).dataInicio, null);
		avisos(treinos, convocatorias, jogos);
		return treinos;
	}

	/** Q54: o jogo dura 90 min; o conflito conta desde a concentracao (ou hora - 60). */
	static final int DURACAO_JOGO_MIN = 90;

	/** R12, R17, R97: so avisam, nunca bloqueiam. Cancelados nao contam. */
	private static void avisos(List<Treino> parmTreinos, Map<Integer, List<TreinoConvocadoData>> parmConvocatorias,
			List<JogoSemanaData> parmJogos) {
		for (Treino a : parmTreinos) {
			if (CANCELADO.equals(a.estadoBD)) {
				continue;
			}
			// v4 (W4): treino da mesma equipa sobreposto a [concentracao, hora + 90]
			for (JogoSemanaData j : parmJogos) {
				if (j.getIdEquipa() != a.d.getIdEquipa() || j.getData() != a.d.getData() || j.getHora() == null
						|| !HORA.matcher(j.getHora()).matches()) {
					continue;
				}
				boolean temConcentracao = j.getHoraConcentracao() != null && HORA.matcher(j.getHoraConcentracao()).matches();
				int inicioJogo = temConcentracao ? minutos(j.getHoraConcentracao()) : minutos(j.getHora()) - 60;
				int fimJogo = minutos(j.getHora()) + DURACAO_JOGO_MIN;
				if (minutos(a.d.getHoraInicio()) < fimJogo && inicioJogo < minutosFim(a.d)) {
					a.d.getAvisos().add("Coincide com o jogo da equipa ("
							+ (temConcentracao ? "concentração " + j.getHoraConcentracao() + ", " : "") + "jogo "
							+ j.getHora() + ")");
				}
			}
			for (Treino b : parmTreinos) {
				if (a == b || CANCELADO.equals(b.estadoBD) || !sobrepostos(a.d, b.d)) {
					continue;
				}
				if (a.evento() || b.evento()) {
					// v5 (R130): os eventos so entram no aviso de local
				} else if (a.d.getIdEquipa() == b.d.getIdEquipa()) {
					a.d.getAvisos().add(b.d.getNomeEquipa() + " já tem treino " + intervaloTexto(b.d) + " neste dia");
				} else {
					Set<Integer> idsB = new HashSet<>();
					for (TreinoConvocadoData c : parmConvocatorias.get(b.d.getId())) {
						idsB.add(c.getIdJogador());
					}
					for (TreinoConvocadoData c : parmConvocatorias.get(a.d.getId())) {
						if (idsB.contains(c.getIdJogador())) {
							a.d.getAvisos().add(c.getNome() + " também está convocado para " + b.d.getNomeEquipa()
									+ " (" + intervalo(b.d) + ")");
						}
					}
				}
				if (a.d.getLocal() != null && b.d.getLocal() != null && a.d.getLocal().equalsIgnoreCase(b.d.getLocal())) {
					a.d.getAvisos().add(a.d.getLocal() + " já está ocupado " + intervaloTexto(b.d) + " ("
							+ b.d.getNomeEquipa() + ")");
				}
			}
		}
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
				Set<Integer> equipasComTreino = new HashSet<>();
				for (Treino t : listaTreinos(conn, parmIdentidade, mc.getId())) {
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
				for (Treino t : treinos) {
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
					t.d.setData(novaData);
					int idTreino = insereTreino(conn, idMicroCiclo, t.d, RASCUNHO, utilizador);
					insereConvocatoriaOmissao(conn, idTreino, t.d.getIdEquipa(), utilizador);
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

	public SemanaMicroCicloData publica(Identidade parmIdentidade, int parmIdMicroCiclo) {
		int tenant = parmIdentidade.getTenantId();
		int segunda = emTransacao("publica", conn -> {
			MicroCicloData mc = carregaMicroCiclo(conn, parmIdMicroCiclo, tenant);
			PreparedStatement psMc = conn.prepareStatement("update micro_ciclo set estado = 'PUBLICADO' "
					+ "where id = ? and tenant_id = ? and estado = 'RASCUNHO'");
			psMc.setInt(1, parmIdMicroCiclo);
			psMc.setInt(2, tenant);
			if (psMc.executeUpdate() == 0) {
				throw new ConflitoException("O micro ciclo não está em rascunho.");
			}
			// v5 (W15): os eventos "Ja aprovado" passam logo a Aprovado
			List<Integer> eventosAprovados = new ArrayList<>();
			try (PreparedStatement psEventos = conn.prepareStatement("select id from treino where id_micro_ciclo = ? "
					+ "and estado = 'RASCUNHO' and id_grupo is not null and aprovar_ao_publicar = '1'")) {
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
					+ "where id_micro_ciclo = ? and estado = 'RASCUNHO'");
			psTreinos.setInt(1, parmIdMicroCiclo);
			int n = psTreinos.executeUpdate() + eventosAprovados.size();
			historico(conn, parmIdMicroCiclo, null, parmIdentidade.getIdUtilizador(),
					"Micro ciclo publicado (" + n + " treinos)");

			// Q31 (BK-12): uma notificacao por destinatario, com o numero de treinos das suas equipas
			PreparedStatement psPendentes = conn.prepareStatement(
					"select id, id_equipa from treino where id_micro_ciclo = ? and estado = 'PENDENTE' and id_grupo is null "
					+ "order by `data`, hora_inicio");
			psPendentes.setInt(1, parmIdMicroCiclo);
			ResultSet rsPendentes = psPendentes.executeQuery();
			Map<Integer, List<Integer>> treinosPorEquipa = new LinkedHashMap<>();
			while (rsPendentes.next()) {
				treinosPorEquipa.computeIfAbsent(rsPendentes.getInt(2), k -> new ArrayList<>()).add(rsPendentes.getInt(1));
			}
			notificaPendentes(conn, parmIdentidade, treinosPorEquipa,
					"Micro ciclo de " + ddmm(mc.getDataInicio()) + " publicado: ");
			return mc.getDataInicio();
		});
		return getSemana(parmIdentidade, segunda);
	}

	/** TREINOS_PENDENTES agregada: uma por destinatario, com o numero de treinos das suas equipas (sem o autor). */
	private void notificaPendentes(Connection conn, Identidade parmIdentidade,
			Map<Integer, List<Integer>> parmTreinosPorEquipa, String parmPrefixo) throws SQLException {
		Map<Integer, List<Integer>> treinosPorDestinatario = new LinkedHashMap<>();
		for (Map.Entry<Integer, List<Integer>> equipa : parmTreinosPorEquipa.entrySet()) {
			for (Integer destinatario : notificacaoHelper.destinatariosEquipa(conn, equipa.getKey(),
					parmIdentidade.getTenantId())) {
				treinosPorDestinatario.computeIfAbsent(destinatario, k -> new ArrayList<>()).addAll(equipa.getValue());
			}
		}
		for (Map.Entry<Integer, List<Integer>> destinatario : treinosPorDestinatario.entrySet()) {
			// o primeiro treino da o link para a semana e o filtro de visibilidade
			notifica(conn, parmIdentidade, List.of(destinatario.getKey()), "TREINOS_PENDENTES",
					destinatario.getValue().get(0), null, parmPrefixo + destinatario.getValue().size()
							+ " treinos das suas equipas aguardam aprovação");
		}
	}

	private static String estadoTexto(String parmEstado) {
		switch (parmEstado) {
		case RASCUNHO:
			return "em rascunho";
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
				+ "order by t.`data`, t.hora_inicio, t.id")) {
			preparedStatement.setInt(1, parmTenantId);
			preparedStatement.setInt(2, parmMc.getId());
			preparedStatement.setString(3, parmMc.getMotivoCancelamento());
			preparedStatement.setInt(4, parmEpocaAtiva);
			try (ResultSet rs = preparedStatement.executeQuery()) {
				while (rs.next()) {
					Treino t = lerTreino(rs);
					if (!inicioPassou(t.d.getData(), t.d.getHoraInicio())) {
						treinos.add(t);
					}
				}
			}
		}
		return treinos;
	}

	private static int contaCancelados(Connection conn, int parmIdMicroCiclo) throws SQLException {
		try (PreparedStatement preparedStatement = conn
				.prepareStatement("select count(*) from treino where id_micro_ciclo = ? and estado = 'CANCELADO'")) {
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
			try (PreparedStatement psMc = conn.prepareStatement("update micro_ciclo set estado = 'PUBLICADO', "
					+ "motivo_cancelamento = null where id = ? and tenant_id = ? and estado = 'CANCELADO'")) {
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
					if (!t.evento()) { // v5 (R128): eventos sem notificacoes
						treinosPorEquipa.computeIfAbsent(t.d.getIdEquipa(), k -> new ArrayList<>()).add(t.d.getId());
					}
				}
			}
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
				+ "data_alteracao = now() where id_treino = ? and estado = 'ATIVA'")) {
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
			if (!PUBLICADO.equals(mc.getEstado())) {
				throw new ConflitoException("Só se pode cancelar um micro ciclo publicado.");
			}
			if (semanaTerminou(mc.getDataInicio())) {
				throw new ConflitoException("Esta semana já terminou.");
			}
			PreparedStatement psMc = conn.prepareStatement("update micro_ciclo set estado = 'CANCELADO', "
					+ "motivo_cancelamento = ? where id = ? and tenant_id = ? and estado = 'PUBLICADO'");
			psMc.setString(1, motivo);
			psMc.setInt(2, parmIdMicroCiclo);
			psMc.setInt(3, tenant);
			if (psMc.executeUpdate() == 0) {
				throw new ConflitoException("Só se pode cancelar um micro ciclo publicado.");
			}
			// v4 (W1): origem MICRO_CICLO, para a reativacao saber quais voltam
			PreparedStatement psTreinos = conn.prepareStatement("update treino set estado = 'CANCELADO', motivo_estado = ?, "
					+ "origem_cancelamento = 'MICRO_CICLO', versao = versao + 1 where id_micro_ciclo = ? and estado <> 'CANCELADO' "
					+ "and not (estado = 'APROVADO' and id_presenca is not null)");
			psTreinos.setString(1, motivo);
			psTreinos.setInt(2, parmIdMicroCiclo);
			psTreinos.executeUpdate();
			historico(conn, parmIdMicroCiclo, null, parmIdentidade.getIdUtilizador(), "Micro ciclo cancelado | " + motivo);
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

	public TreinoData gravaTreino(Identidade parmIdentidade, TreinoData v) {
		validaTreino(v);
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		boolean evento = v.getIdGrupo() > 0;
		int idTreino = emTransacao("gravaTreino", conn -> {
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
				// Q8: num micro ciclo publicado o treino fica logo Pendente
				String estado = RASCUNHO.equals(mc.getEstado()) ? RASCUNHO : PENDENTE;
				int id = insereTreino(conn, mc.getId(), v, estado, utilizador);
				insereConvocatoriaOmissao(conn, id, v.getIdEquipa(), utilizador);
				String nome = nomeEquipa(conn, v.getIdEquipa());
				historico(conn, mc.getId(), id, utilizador, "Treino criado | " + nome + " " + dia(v.getData()) + " " + intervalo(v));
				if (PENDENTE.equals(estado)) {
					// v4 (R76, W7): treino novo num micro ciclo publicado
					notifica(conn, parmIdentidade, notificacaoHelper.destinatariosEquipa(conn, v.getIdEquipa(), tenant),
							"TREINO_NOVO", id, null, "Novo treino " + nome + " de " + dia(v.getData()) + " às "
									+ v.getHoraInicio() + " aguarda aprovação");
				}
				return id;
			}

			Treino t = carregaTreino(conn, v.getId(), tenant);
			if (v.getVersao() != t.d.getVersao()) {
				throw new ConflitoException(MSG_VERSAO);
			}
			if (CANCELADO.equals(t.estadoBD) || t.realizado()) {
				throw new ConflitoException("Um treino cancelado ou realizado não pode ser alterado.");
			}
			if (inicioPassou(t.d.getData(), t.d.getHoraInicio())) {
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
			if (diferencas.isEmpty()) {
				return t.d.getId();
			}

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
				insereConvocatoriaOmissao(conn, t.d.getId(), v.getIdEquipa(), utilizador);
				PreparedStatement psDescarta = conn.prepareStatement("update treino_ausencia set estado = 'DESCARTADA', "
						+ "data_alteracao = now() where id_treino = ? and estado = 'ATIVA'");
				psDescarta.setInt(1, t.d.getId());
				psDescarta.executeUpdate();
			} else if (voltaPendente || (corrigido && essencial)) {
				// R40: a ausencia mantem-se, assinalada, ate o atleta confirmar
				PreparedStatement psPendente = conn.prepareStatement("update treino_ausencia set pendente_confirmacao = '1', "
						+ "data_alteracao = now() where id_treino = ? and estado = 'ATIVA'");
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
			return t.d.getId();
		});
		return getTreinoData(parmIdentidade, idTreino);
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
			throw new ConflitoException("Num rascunho, publique primeiro.");
		}
		if (!PUBLICADO.equals(mc.getEstado())) {
			throw new ConflitoException("O micro ciclo não está publicado.");
		}

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
		if (!PENDENTE.equals(t.estadoBD)) {
			throw new ConflitoException("já não está pendente (" + t.d.getEstado().toLowerCase() + ")");
		}
		if (inicioPassou(t.d.getData(), t.d.getHoraInicio())) {
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
		historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdentidade.getIdUtilizador(),
				"Aprovado (" + textoQualidade(qualidade(parmIdentidade, t.d.getIdEquipa(), equipas)) + ")");
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
				try (PreparedStatement preparedStatement = conn.prepareStatement("select t.id, t.`data`, t.hora_inicio, "
						+ "m.data_inicio from treino t inner join micro_ciclo m on m.id = t.id_micro_ciclo "
						+ "where m.tenant_id = ? and m.estado = 'PUBLICADO' and t.estado = 'PENDENTE' "
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
			PreparedStatement preparedStatement = conn
					.prepareStatement("delete from treino where id = ? and estado = 'RASCUNHO'");
			preparedStatement.setInt(1, parmIdTreino);
			if (preparedStatement.executeUpdate() == 0) {
				throw new ConflitoException("Só se pode apagar um treino em rascunho.");
			}
			historico(conn, t.d.getIdMicroCiclo(), null, parmIdentidade.getIdUtilizador(), "Treino apagado | "
					+ t.d.getNomeEquipa() + " " + dia(t.d.getData()) + " " + t.d.getHoraInicio());
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
			if (parmPedido.getVersao() != t.d.getVersao()) {
				throw new ConflitoException(MSG_VERSAO);
			}
			Set<Integer> equipas = equipasDoUtilizador(conn, utilizador);
			boolean passou = inicioPassou(t.d.getData(), t.d.getHoraInicio());
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
				if (!CANCELADO.equals(t.estadoBD) || !PUBLICADO.equals(t.mcEstado)) {
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
			historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), utilizador, texto);
			if ("REJEITAR".equals(acao) && !t.evento()) { // v5 (R128): eventos sem notificacoes
				aposTreinoRejeitado(conn, parmIdentidade, t);
			}
			if ("REATIVAR".equals(acao) && !t.evento()) {
				try (PreparedStatement psAusencias = conn.prepareStatement("update treino_ausencia set "
						+ "pendente_confirmacao = '1', data_alteracao = now() where id_treino = ? and estado = 'ATIVA'")) {
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
			if (!podeAgirNaEquipa(parmIdentidade, t.d.getIdEquipa(), equipasDoUtilizador(conn, utilizador))) {
				throw new AcessoNegadoException();
			}
			if (t.evento()) {
				throw new ConflitoException("Um evento não tem convocatória."); // v5 (R128)
			}
			if (CANCELADO.equals(t.estadoBD) || t.realizado()) {
				throw new ConflitoException("Não se pode ajustar a convocatória de um treino cancelado ou realizado.");
			}
			if (inicioPassou(t.d.getData(), t.d.getHoraInicio())) {
				throw new ConflitoException(MSG_JA_COMECOU);
			}
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
			if (APROVADO.equals(t.estadoBD) && !t.realizado()) {
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
			}
			return true;
		});
		return getDetalhe(parmIdentidade, parmIdTreino);
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

			PreparedStatement psAusencias = conn.prepareStatement("select ta.id_jogador, j.nome, ta.categoria, ta.motivo, "
					+ "ta.data_indicacao, ta.estado, ta.pendente_confirmacao from treino_ausencia ta "
					+ "inner join jogador j on j.id = ta.id_jogador where ta.id_treino = ? and ta.estado = 'ATIVA' "
					+ "order by j.nome");
			psAusencias.setInt(1, t.d.getId());
			ResultSet rsAusencias = psAusencias.executeQuery();
			Set<Integer> avisaram = new HashSet<>();
			while (rsAusencias.next()) {
				TreinoAusenciaData a = lerAusencia(rsAusencias);
				detalhe.getAusencias().add(a);
				avisaram.add(a.getIdJogador());
			}

			PreparedStatement psOrigem = conn.prepareStatement("select coalesce(nullif(ee.nome, ''), es.nome) "
					+ "from escalao_epoca_jogador eej inner join escalao_epoca ee on ee.id = eej.id_escalao_epoca "
					+ "inner join epoca e on e.id = ee.id_epoca left join escalao es on es.id = ee.id_escalao "
					+ "where eej.id_jogador = ? and e.estado = '1' and e.tenant_id = ? and ee.id <> ? LIMIT 1");
			for (TreinoConvocadoData c : convocatoriaEfetiva(conn, t)) {
				c.setAvisou(avisaram.contains(c.getIdJogador()));
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

			boolean passou = inicioPassou(t.d.getData(), t.d.getHoraInicio());
			boolean fechado = CANCELADO.equals(t.estadoBD) || t.realizado();
			boolean agir = podeAgirNaEquipa(parmIdentidade, t.d.getIdEquipa(), equipas);
			detalhe.setPodeAprovar(t.d.isPodeAprovar());
			detalhe.setPodeAjustar(!t.evento() && agir && !fechado && !passou);
			detalhe.setPodeMarcarPresenca(!t.evento() && APROVADO.equals(t.estadoBD) && !t.realizado() && passou
					&& podeMarcarPresenca(parmIdentidade, t.d.getIdEquipa(), equipas));
			detalhe.setPodeEditar(parmIdentidade.isAdmin() && !fechado && !passou && !CANCELADO.equals(t.mcEstado)
					&& !semanaTerminou(t.dataInicio));

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
		return leitura("getJogosAtleta",
				conn -> jogosSemana(conn, parmIdentidade.getTenantId(), segunda, parmIdJogador));
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
		Treino origem = emTransacao("copiaTreino", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, tenant);
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
					historico(conn, mc.getId(), id, utilizador, "Treino criado (cópia de " + descricao + ")");
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
		for (Treino t : treinos) {
			t.d.setCorEquipa(cores.get(t.d.getIdEquipa()));
			t.d.setNumConvocados(convocatoriaEfetiva(conn, t).size());
			t.d.setNumAusencias(ausenciasAtivas(conn, t.d.getId()));
			preencheDerivados(t, parmIdentidade, parmEquipas);
			lista.add(t.d);
		}
		return lista;
	}

	/** R51, R52: treinos do dia (aprovados e pendentes) e aprovados por marcar (Q38). */
	public Map<String, List<TreinoData>> getTreinosEquipaDia(Identidade parmIdentidade, int parmIdEquipa, int parmData) {
		paraData(parmData);
		int tenant = parmIdentidade.getTenantId();
		if (!autorizacaoHelper.pertenceAoTenant(sm.core.security.TipoRecurso.EQUIPA, parmIdEquipa, tenant)) {
			throw new AcessoNegadoException();
		}
		return leitura("getTreinosEquipaDia", conn -> {
			Set<Integer> equipas = equipasDoUtilizador(conn, parmIdentidade.getIdUtilizador());
			Map<String, List<TreinoData>> resultado = new LinkedHashMap<>();
			PreparedStatement psDia = conn.prepareStatement(SQL_TREINO + "and t.id_equipa = ? and t.`data` = ? "
					+ "and t.estado in ('APROVADO', 'PENDENTE') and m.estado = 'PUBLICADO' order by t.hora_inicio, t.id");
			psDia.setInt(1, tenant);
			psDia.setInt(2, parmIdEquipa);
			psDia.setInt(3, parmData);
			resultado.put("dia", treinosComContagens(conn, parmIdentidade, psDia, equipas));
			int hoje = hoje();
			PreparedStatement psPorMarcar = conn.prepareStatement(SQL_TREINO + "and t.id_equipa = ? "
					+ "and t.estado = 'APROVADO' and t.id_presenca is null and t.`data` between ? and ? "
					+ "order by t.`data`, t.hora_inicio, t.id");
			psPorMarcar.setInt(1, tenant);
			psPorMarcar.setInt(2, parmIdEquipa);
			psPorMarcar.setInt(3, somaDias(segundaDe(hoje), -7));
			psPorMarcar.setInt(4, somaDias(hoje, -1));
			resultado.put("porMarcar", treinosComContagens(conn, parmIdentidade, psPorMarcar, equipas));
			return resultado;
		});
	}

	/** Q23, Q40: permissoes e estado para marcar a presenca do treino. */
	private static void verificaMarcacao(Connection conn, Identidade parmIdentidade, Treino t) throws SQLException {
		if (t.evento()) {
			throw new ConflitoException("Um evento não tem presença."); // v5 (R128)
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
		if (!inicioPassou(t.d.getData(), t.d.getHoraInicio())) {
			throw new ConflitoException("A presença só pode ser marcada a partir da hora de início ("
					+ t.d.getHoraInicio() + ").");
		}
	}

	/** R18, R42, R56.4: presenca pre-preenchida a partir da convocatoria e das ausencias. */
	public Map<String, Object> getPresencaTreino(Identidade parmIdentidade, int parmIdTreino) {
		return leitura("getPresencaTreino", conn -> {
			Treino t = carregaTreino(conn, parmIdTreino, parmIdentidade.getTenantId());
			verificaMarcacao(conn, parmIdentidade, t);
			PresencaData presenca = new PresencaData(0, t.d.getData(), t.d.getHoraInicio(), t.d.getIdEquipa(),
					t.d.getNomeEquipa(), null, parmIdentidade.getIdUtilizador(), parmIdentidade.getUser());

			PreparedStatement psAusencias = conn.prepareStatement("select ta.id_jogador, j.nome, ta.categoria, ta.motivo, "
					+ "ta.data_indicacao, ta.estado, ta.pendente_confirmacao from treino_ausencia ta "
					+ "inner join jogador j on j.id = ta.id_jogador where ta.id_treino = ? and ta.estado = 'ATIVA'");
			psAusencias.setInt(1, t.d.getId());
			ResultSet rsAusencias = psAusencias.executeQuery();
			Map<Integer, TreinoAusenciaData> ausencias = new HashMap<>();
			while (rsAusencias.next()) {
				TreinoAusenciaData a = lerAusencia(rsAusencias);
				ausencias.put(a.getIdJogador(), a);
			}

			Set<Integer> convocados = new HashSet<>();
			for (TreinoConvocadoData c : convocatoriaEfetiva(conn, t)) {
				convocados.add(c.getIdJogador());
				TreinoAusenciaData a = ausencias.get(c.getIdJogador());
				if (a == null) {
					presenca.getJogadoresPresenca().add(new PresencaJogadorData(c.getIdJogador(), c.getNome(), "", ""));
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

	// ==================== 16-17. atleta (Entrega D) ====================

	private static final Set<String> CATEGORIAS = Set.of("DOENCA", "LESAO", "ESCOLA", "FAMILIA", "OUTRO");
	private static final String MSG_PRAZO = "O prazo para avisar terminou. Fala com o teu treinador.";

	/** D24: treinos da semana em que o jogador e convocado efetivo, ja aprovados alguma vez. */
	private List<TreinoAtletaData> treinosAtleta(Connection conn, int parmTenantId, int parmIdJogador, int parmSegunda,
			int parmIdUtilizador)
			throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement(SQL_TREINO + "and t.`data` between ? and ? "
				+ "and t.estado in ('APROVADO', 'CANCELADO') and t.data_aprovacao is not null and exists (select 1 "
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

		PreparedStatement psAusencia = conn.prepareStatement("select ta.id_jogador, j.nome, ta.categoria, ta.motivo, "
				+ "ta.data_indicacao, ta.estado, ta.pendente_confirmacao from treino_ausencia ta "
				+ "inner join jogador j on j.id = ta.id_jogador where ta.id_treino = ? and ta.id_jogador = ? "
				+ "and ta.estado = 'ATIVA'");
		List<TreinoAtletaData> lista = new ArrayList<>();
		Map<Integer, String> cores = coresEquipas(conn, parmTenantId);
		// v5 (Q79): CONVOCATORIA_ENTROU por ler, desta conta, por treino
		Set<Integer> convocatoriaAlterada = new HashSet<>();
		try (PreparedStatement psAlterada = conn.prepareStatement("select distinct n.id_treino from notificacao n "
				+ "where n.id_utilizador = ? and n.tipo = 'CONVOCATORIA_ENTROU' and n.data_leitura is null "
				+ "and n.id_treino is not null")) {
			psAlterada.setInt(1, parmIdUtilizador);
			try (ResultSet rsAlterada = psAlterada.executeQuery()) {
				while (rsAlterada.next()) {
					convocatoriaAlterada.add(rsAlterada.getInt(1));
				}
			}
		}
		for (Treino t : treinos) {
			TreinoAtletaData a = new TreinoAtletaData();
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
			a.setPrazoTerminado(inicioPassou(t.d.getData(), t.d.getHoraInicio()));
			for (Treino outro : treinos) {
				if (outro != t && !CANCELADO.equals(t.estadoBD) && !CANCELADO.equals(outro.estadoBD)
						&& sobrepostos(t.d, outro.d)) {
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
			lista.add(a);
		}
		return lista;
	}

	private static void exigeAtleta(Identidade parmIdentidade, Integer parmIdJogador) {
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
			if (!APROVADO.equals(t.estadoBD)) {
				throw new ConflitoException("O treino não está aprovado.");
			}
			if (t.realizado() || inicioPassou(t.d.getData(), t.d.getHoraInicio())) {
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
						+ "id_utilizador = values(id_utilizador)");
				psIndica.setInt(1, t.d.getId());
				psIndica.setInt(2, jid);
				psIndica.setString(3, categoria);
				psIndica.setString(4, motivoFinal);
				psIndica.setInt(5, parmIdentidade.getIdUtilizador());
				psIndica.executeUpdate();
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
}
