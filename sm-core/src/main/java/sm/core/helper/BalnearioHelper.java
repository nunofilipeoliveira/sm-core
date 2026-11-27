package sm.core.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import sm.core.data.BalnearioAtribuidoData;
import sm.core.data.BalnearioData;
import sm.core.data.CalculoBalneariosData;
import sm.core.data.ConfigBalneariosData;
import sm.core.data.FisicoStaffData;
import sm.core.data.JogoSemanaData;
import sm.core.data.MicroCicloData;
import sm.core.data.PedidoBalneariosData;
import sm.core.data.SemanaMicroCicloData;
import sm.core.security.AcessoNegadoException;
import sm.core.security.ConflitoException;
import sm.core.security.Identidade;
import sm.core.security.PedidoInvalidoException;

/**
 * Balnearios (V17, desenho .melhorias/balnearios/02-desenho.md). Configuracao, atribuicao manual, calculo automatico
 * (CalculoBalnearios) e leituras. O MicroCicloHelper so chama os metodos static daqui (B12: sem ciclo de dependencias).
 * Todas as leituras de atribuicoes fazem join balneario ... b.tenant_id = ? (R31). Logs so com ids e contagens.
 */
@Component
public class BalnearioHelper {

	private static final Logger log = LoggerFactory.getLogger(BalnearioHelper.class);

	// ==================== decisoes do dono (§0), num so sitio ====================

	/** P4: tempos por omissao (sem linha em balneario_config). */
	static final int ANTECEDENCIA_OMISSAO = 30;
	static final int PERMANENCIA_OMISSAO = 30;
	static final int LIBERTACAO_OMISSAO = 0;
	static final int MAXIMO_MINUTOS = 240;
	/** P18. */
	static final int MAXIMO_BALNEARIOS = 30;
	static final int MAXIMO_NOME = 30;

	static final String MANUAL = "MANUAL";
	static final String AUTOMATICA = "AUTOMATICA";
	static final String EQUIPA = CalculoBalnearios.EQUIPA;
	static final String ADVERSARIO = CalculoBalnearios.ADVERSARIO;

	private static final String MSG_ALTERADOS = "Os balneários deste evento foram alterados entretanto. Recarregue.";
	private static final String MSG_SO_LEITURA = "Esta semana é só de leitura.";
	private static final String MSG_MC_CANCELADO = "O micro ciclo está cancelado.";
	private static final String MSG_SEM_ATIVOS = "Configure pelo menos um balneário ativo na Administração.";
	private static final String MSG_SEMANA_MUDOU = "A semana mudou desde o cálculo. Calcule de novo.";

	private static final String SQL_TREINO_BALNEARIO = "select tb.id_treino, b.id, b.nome, b.estado, tb.origem "
			+ "from treino_balneario tb inner join balneario b on b.id = tb.id_balneario and b.tenant_id = ? "
			+ "where tb.id_treino in (%s) order by b.ordem, b.id";
	/** A colacao de jogo e _ci: 'C' apanha tambem 'c' (como o equalsIgnoreCase de jogosSemana). */
	private static final String SQL_JOGO_BALNEARIO = "select jb.id_jogo, jb.lado, b.id, b.nome, b.estado, jb.origem "
			+ "from jogo_balneario jb inner join balneario b on b.id = jb.id_balneario and b.tenant_id = ? "
			+ "inner join jogo j on j.id = jb.id_jogo and j.tipo_local = 'C' where jb.id_jogo in (%s) order by b.ordem, b.id";

	@Autowired
	private MicroCicloHelper microCicloHelper;

	record Config(int antecedencia, int permanencia, int libertacao) {
	}

	// ==================== utilitarios ====================

	private static String marcas(int parmN) {
		return String.join(", ", Collections.nCopies(parmN, "?"));
	}

	static String hhmm(int parmMinutos) {
		int m = Math.max(0, Math.min(1439, parmMinutos));
		return String.format("%02d:%02d", m / 60, m % 60);
	}

	static Config config(Connection conn, int parmTenantId) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("select antecedencia_min, permanencia_min, libertacao_min "
				+ "from balneario_config where tenant_id = ?")) {
			ps.setInt(1, parmTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? new Config(rs.getInt(1), rs.getInt(2), rs.getInt(3))
						: new Config(ANTECEDENCIA_OMISSAO, PERMANENCIA_OMISSAO, LIBERTACAO_OMISSAO);
			}
		}
	}

	/** Todos os balnearios do tenant (ativos e inativos), pela ordem do clube, com emUso e a regra de uso (V18). */
	static List<BalnearioData> lista(Connection conn, int parmTenantId) throws SQLException {
		List<BalnearioData> lista = new ArrayList<>();
		boolean comRegras = false;
		try (PreparedStatement ps = conn.prepareStatement("select b.id, b.nome, b.ordem, b.libertacao_min, b.estado, b.uso, "
				+ "(exists (select 1 from treino_balneario tb where tb.id_balneario = b.id) "
				+ "or exists (select 1 from jogo_balneario jb where jb.id_balneario = b.id)) as em_uso "
				+ "from balneario b where b.tenant_id = ? order by b.ordem, b.id")) {
			ps.setInt(1, parmTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					BalnearioData b = new BalnearioData();
					b.setId(rs.getInt("id"));
					b.setNome(rs.getString("nome"));
					b.setOrdem(rs.getInt("ordem"));
					int libertacao = rs.getInt("libertacao_min");
					b.setLibertacao(rs.wasNull() ? null : libertacao);
					b.setAtivo("1".equals(rs.getString("estado")));
					b.setEmUso(rs.getBoolean("em_uso"));
					b.setUso(rs.getString("uso"));
					b.setUsoEscaloes(new ArrayList<>());
					b.setUsoAdversarios(false);
					comRegras |= !CalculoBalnearios.TODOS.equals(b.getUso());
					lista.add(b);
				}
			}
		}
		if (!comRegras) {
			return lista;
		}
		Map<Integer, BalnearioData> porId = new HashMap<>();
		lista.forEach(b -> porId.put(b.getId(), b));
		Map<Integer, List<String>> nomes = new HashMap<>();
		try (PreparedStatement ps = conn.prepareStatement("select r.id_balneario, r.tipo, r.id_escalao, es.nome "
				+ "from balneario_restricao r inner join balneario b on b.id = r.id_balneario and b.tenant_id = ? "
				+ "left join escalao es on es.id = r.id_escalao order by r.id_balneario, es.nome, r.id_escalao")) {
			ps.setInt(1, parmTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					BalnearioData b = porId.get(rs.getInt(1));
					if (b == null) {
						continue;
					}
					if (ADVERSARIO.equals(rs.getString(2))) {
						b.setUsoAdversarios(true);
					} else {
						b.getUsoEscaloes().add(rs.getInt(3));
						nomes.computeIfAbsent(b.getId(), k -> new ArrayList<>()).add(rs.getString(4));
					}
				}
			}
		}
		for (BalnearioData b : lista) {
			if (restricao(b) != null) {
				List<String> n = new ArrayList<>(nomes.getOrDefault(b.getId(), List.of()));
				if (Boolean.TRUE.equals(b.getUsoAdversarios())) {
					n.add("Adversários");
				}
				b.setNotaUso((CalculoBalnearios.DEDICADO.equals(b.getUso()) ? NOTA_DEDICADO : NOTA_EXCLUSAO)
						+ String.join(", ", n));
			}
		}
		return lista;
	}

	private static final String NOTA_DEDICADO = "só ";
	private static final String NOTA_EXCLUSAO = "exclui ";

	/** A regra de uso para o calculo; null em TODOS (ou sem regra). */
	static CalculoBalnearios.Restricao restricao(BalnearioData b) {
		if (b.getUso() == null || CalculoBalnearios.TODOS.equals(b.getUso())) {
			return null;
		}
		return new CalculoBalnearios.Restricao(b.getUso(),
				new java.util.TreeSet<>(b.getUsoEscaloes() == null ? List.of() : b.getUsoEscaloes()),
				Boolean.TRUE.equals(b.getUsoAdversarios()));
	}

	/** R46/C4: usados por uma equipa do tenant (qualquer epoca) ou ja com regras gravadas (preferencia, prioridade, uso). */
	private static Set<Integer> escaloesValidos(Connection conn, int parmTenantId) throws SQLException {
		Set<Integer> escaloesValidos = new HashSet<>();
		try (PreparedStatement ps = conn.prepareStatement("select distinct ee.id_escalao from escalao_epoca ee "
				+ "inner join epoca e on e.id = ee.id_epoca where e.tenant_id = ? and ee.id_escalao is not null "
				+ "union select p.id_escalao from balneario_preferencia p inner join balneario b on b.id = p.id_balneario "
				+ "where b.tenant_id = ? and p.id_escalao is not null "
				+ "union select id_escalao from balneario_prioridade where tenant_id = ? "
				+ "union select r.id_escalao from balneario_restricao r inner join balneario b on b.id = r.id_balneario "
				+ "where b.tenant_id = ? and r.id_escalao is not null")) {
			ps.setInt(1, parmTenantId);
			ps.setInt(2, parmTenantId);
			ps.setInt(3, parmTenantId);
			ps.setInt(4, parmTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					escaloesValidos.add(rs.getInt(1));
				}
			}
		}
		return escaloesValidos;
	}

	/** P6: a libertacao propria, ou a global. */
	static int libertacao(BalnearioData b, Config c) {
		return b.getLibertacao() != null ? b.getLibertacao() : c.libertacao();
	}

	/** P5: [inicio da sessao - antecedencia, fim da sessao + permanencia]; sem fim, 60 min (minutosFim). */
	static int[] janelaTreino(MicroCicloHelper.Treino t, Config c) {
		int inicio = MicroCicloHelper.minutos(t.d.getHoraInicio());
		int fim = MicroCicloHelper.minutosFim(t.d);
		if (t.d.getFisicos() != null) {
			for (FisicoStaffData f : t.d.getFisicos()) {
				int fi = MicroCicloHelper.minutos(f.getHoraInicio());
				inicio = Math.min(inicio, fi);
				fim = Math.max(fim, f.getHoraFim() != null ? MicroCicloHelper.minutos(f.getHoraFim()) : fi + 60);
			}
		}
		return new int[] { Math.max(0, inicio - c.antecedencia()), Math.min(1439, fim + c.permanencia()) };
	}

	/** P5: [concentracao (ou hora - 60), hora + 90 + permanencia]; null sem hora valida. */
	static int[] janelaJogo(JogoSemanaData j, Config c) {
		if (j.getHora() == null || !MicroCicloHelper.HORA.matcher(j.getHora()).matches()) {
			return null;
		}
		int[] intervalo = MicroCicloHelper.intervaloJogo(j);
		return new int[] { Math.max(0, intervalo[0]), Math.min(1439, intervalo[1] + c.permanencia()) };
	}

	/** R36: um treino cancelado ou rejeitado nao ocupa; um fisico ligado nunca tem linhas (B14). */
	static boolean ocupa(MicroCicloHelper.Treino t) {
		return !t.ligado() && !MicroCicloHelper.CANCELADO.equals(t.estadoBD)
				&& !MicroCicloHelper.REJEITADO.equals(t.estadoBD);
	}

	static boolean casa(JogoSemanaData j) {
		return "CASA".equals(j.getCasaFora());
	}

	static boolean ocupa(JogoSemanaData j, Config c) {
		return casa(j) && !"CONCLUIDO".equals(j.getEstado()) && janelaJogo(j, c) != null;
	}

	/** P10: so as sessoes ESCALAO de equipa, por comecar. */
	static boolean entraNoCalculo(MicroCicloHelper.Treino t) {
		String estado = t.estadoBD;
		return "ESCALAO".equals(t.d.getTipo()) && !t.evento() && !t.ligado()
				&& (MicroCicloHelper.RASCUNHO.equals(estado) || MicroCicloHelper.PENDENTE.equals(estado)
						|| MicroCicloHelper.APROVADO.equals(estado))
				&& !t.realizado() && !MicroCicloHelper.inicioPassou(t.d.getData(), t.inicioSessao());
	}

	/** P10: jogos em casa por comecar (1 + 1 pedidos). */
	static boolean entraNoCalculo(JogoSemanaData j, Config c) {
		return ocupa(j, c) && !MicroCicloHelper.inicioPassou(j.getData(), j.getHora());
	}

	private static String descricao(MicroCicloHelper.Treino t) {
		String nome = t.d.getNomeEquipa() != null ? t.d.getNomeEquipa()
				: t.d.getNomeGrupo() != null ? t.d.getNomeGrupo() : "Treino";
		return nome + " " + MicroCicloHelper.dia(t.d.getData()) + " " + t.d.getHoraInicio();
	}

	private static String descricao(JogoSemanaData j, String parmLado) {
		String base = "Jogo " + j.getNomeEquipa();
		if (ADVERSARIO.equals(parmLado)) {
			return "Adv. " + (j.getNomeAdversario() == null ? "" : j.getNomeAdversario() + " ") + "(" + base + ")";
		}
		return base + (j.getHora() == null ? "" : " " + j.getHora());
	}

	// ==================== leituras de atribuicoes ====================

	static Map<Integer, List<BalnearioAtribuidoData>> atribuicoesTreinos(Connection conn, int parmTenantId,
			Collection<Integer> parmIds) throws SQLException {
		Map<Integer, List<BalnearioAtribuidoData>> mapa = new HashMap<>();
		if (parmIds.isEmpty()) {
			return mapa;
		}
		try (PreparedStatement ps = conn.prepareStatement(String.format(SQL_TREINO_BALNEARIO, marcas(parmIds.size())))) {
			int i = 1;
			ps.setInt(i++, parmTenantId);
			for (Integer id : parmIds) {
				ps.setInt(i++, id);
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					mapa.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(new BalnearioAtribuidoData(rs.getInt(2),
							rs.getString(3), "1".equals(rs.getString(4)), rs.getString(5)));
				}
			}
		}
		return mapa;
	}

	/** Chave id:lado (so jogos em casa). */
	static Map<String, List<BalnearioAtribuidoData>> atribuicoesJogos(Connection conn, int parmTenantId,
			Collection<Integer> parmIds) throws SQLException {
		Map<String, List<BalnearioAtribuidoData>> mapa = new HashMap<>();
		if (parmIds.isEmpty()) {
			return mapa;
		}
		try (PreparedStatement ps = conn.prepareStatement(String.format(SQL_JOGO_BALNEARIO, marcas(parmIds.size())))) {
			int i = 1;
			ps.setInt(i++, parmTenantId);
			for (Integer id : parmIds) {
				ps.setInt(i++, id);
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					mapa.computeIfAbsent(rs.getInt(1) + ":" + rs.getString(2), k -> new ArrayList<>()).add(
							new BalnearioAtribuidoData(rs.getInt(3), rs.getString(4), "1".equals(rs.getString(5)),
									rs.getString(6)));
				}
			}
		}
		return mapa;
	}

	/** Atleta (P15): so os nomes, por treino (sem entrada = nenhum). */
	static Map<Integer, List<String>> nomesTreinos(Connection conn, int parmTenantId, Collection<Integer> parmIds)
			throws SQLException {
		Map<Integer, List<String>> mapa = new HashMap<>();
		atribuicoesTreinos(conn, parmTenantId, parmIds)
				.forEach((id, lista) -> lista.forEach(b -> mapa.computeIfAbsent(id, k -> new ArrayList<>()).add(b.getNome())));
		return mapa;
	}

	/** Atleta (P15): so o lado da equipa, so id e nome. */
	static Map<Integer, List<BalnearioAtribuidoData>> nomesJogosEquipa(Connection conn, int parmTenantId,
			Collection<Integer> parmIds) throws SQLException {
		Map<Integer, List<BalnearioAtribuidoData>> mapa = new HashMap<>();
		for (Integer id : parmIds) {
			List<BalnearioAtribuidoData> lista = atribuicoesJogos(conn, parmTenantId, List.of(id)).get(id + ":" + EQUIPA);
			if (lista != null) {
				List<BalnearioAtribuidoData> so = new ArrayList<>();
				lista.forEach(b -> so.add(new BalnearioAtribuidoData(b.getId(), b.getNome(), null, null)));
				mapa.put(id, so);
			}
		}
		return mapa;
	}

	/** Um evento ocupado (para os avisos): onde escrever e como se descreve. */
	private record Ocupante(MicroCicloHelper.Treino treino, JogoSemanaData jogo, String descricao, String nome) {
	}

	/**
	 * B11: balnearios nos treinos (nao ligados) e nos jogos em casa. Sem balnearios no tenant, 1 consulta e sai (os
	 * campos ficam null). Usa sempre os tempos e as regras atuais (R42, R50). C7 (V18): os avisos de balneario so vao
	 * para avisosBalnearios e so para o ADMIN, tal como balneariosNaoPermitidos; nunca para TreinoData.avisos.
	 */
	static void preenche(Connection conn, int parmTenantId, boolean parmAdmin, List<MicroCicloHelper.Treino> parmTreinos,
			List<JogoSemanaData> parmJogos) throws SQLException {
		List<BalnearioData> lista = lista(conn, parmTenantId);
		if (lista.isEmpty()) {
			return;
		}
		Config config = config(conn, parmTenantId);
		Map<Integer, Integer> libertacoes = new HashMap<>();
		lista.forEach(b -> libertacoes.put(b.getId(), libertacao(b, config)));
		List<Integer> idsTreinos = new ArrayList<>();
		for (MicroCicloHelper.Treino t : parmTreinos) {
			if (!t.ligado()) {
				idsTreinos.add(t.d.getId());
			}
		}
		List<JogoSemanaData> jogos = parmJogos == null ? List.of() : parmJogos;
		List<Integer> idsJogos = new ArrayList<>();
		for (JogoSemanaData j : jogos) {
			if (casa(j)) {
				idsJogos.add(j.getId());
			}
		}
		Map<Integer, List<BalnearioAtribuidoData>> porTreino = atribuicoesTreinos(conn, parmTenantId, idsTreinos);
		Map<String, List<BalnearioAtribuidoData>> porJogo = atribuicoesJogos(conn, parmTenantId, idsJogos);
		if (!parmAdmin) {
			for (MicroCicloHelper.Treino t : parmTreinos) {
				if (!t.ligado()) {
					t.d.setBalnearios(porTreino.getOrDefault(t.d.getId(), new ArrayList<>()));
				}
			}
			for (JogoSemanaData j : jogos) {
				if (casa(j)) {
					j.setBalnearios(porJogo.getOrDefault(j.getId() + ":" + EQUIPA, new ArrayList<>()));
					j.setBalneariosAdversario(porJogo.getOrDefault(j.getId() + ":" + ADVERSARIO, new ArrayList<>()));
				}
			}
			return;
		}

		List<CalculoBalnearios.Ocupacao> ocupacoes = new ArrayList<>();
		List<Ocupante> ocupantes = new ArrayList<>();
		for (MicroCicloHelper.Treino t : parmTreinos) {
			if (t.ligado()) {
				continue;
			}
			List<BalnearioAtribuidoData> atribuidos = porTreino.getOrDefault(t.d.getId(), new ArrayList<>());
			t.d.setBalnearios(atribuidos);
			if (ocupa(t)) {
				int[] janela = janelaTreino(t, config);
				for (BalnearioAtribuidoData b : atribuidos) {
					ocupacoes.add(new CalculoBalnearios.Ocupacao("TREINO:" + t.d.getId(), b.getId(), t.d.getData(), janela[0],
							janela[1]));
					String nome = t.d.getNomeEquipa() != null ? t.d.getNomeEquipa() : t.d.getNomeGrupo();
					ocupantes.add(new Ocupante(t, null, nome == null ? "Treino" : nome, b.getNome()));
				}
			}
		}
		for (JogoSemanaData j : jogos) {
			if (!casa(j)) {
				continue;
			}
			List<BalnearioAtribuidoData> equipa = porJogo.getOrDefault(j.getId() + ":" + EQUIPA, new ArrayList<>());
			List<BalnearioAtribuidoData> adversario = porJogo.getOrDefault(j.getId() + ":" + ADVERSARIO, new ArrayList<>());
			j.setBalnearios(equipa);
			j.setBalneariosAdversario(adversario);
			int[] janela = janelaJogo(j, config);
			if (janela == null) {
				if (!equipa.isEmpty() || !adversario.isEmpty()) {
					avisoJogo(j, "Jogo sem hora: não é possível verificar conflitos"); // R13, CA11
				}
				continue;
			}
			if (!ocupa(j, config)) {
				continue;
			}
			for (BalnearioAtribuidoData b : equipa) {
				ocupacoes.add(new CalculoBalnearios.Ocupacao("JOGO:" + j.getId() + ":" + EQUIPA, b.getId(), j.getData(),
						janela[0], janela[1]));
				ocupantes.add(new Ocupante(null, j, "Jogo " + j.getNomeEquipa(), b.getNome()));
			}
			for (BalnearioAtribuidoData b : adversario) {
				ocupacoes.add(new CalculoBalnearios.Ocupacao("JOGO:" + j.getId() + ":" + ADVERSARIO, b.getId(), j.getData(),
						janela[0], janela[1]));
				ocupantes.add(new Ocupante(null, j,
						"Adv. " + (j.getNomeAdversario() == null ? "" : j.getNomeAdversario()), b.getNome()));
			}
		}
		for (int[] par : CalculoBalnearios.conflitos(ocupacoes, libertacoes)) {
			avisa(ocupantes.get(par[0]), ocupacoes.get(par[1]), ocupantes.get(par[1]), libertacoes);
			avisa(ocupantes.get(par[1]), ocupacoes.get(par[0]), ocupantes.get(par[0]), libertacoes);
		}
		regrasDeUso(conn, parmTenantId, lista, parmTreinos, jogos);
	}

	/** C9/C10 (so ADMIN): balneariosNaoPermitidos e os avisos de regra violada. Sem regras no tenant, nao faz nada. */
	private static void regrasDeUso(Connection conn, int parmTenantId, List<BalnearioData> parmLista,
			List<MicroCicloHelper.Treino> parmTreinos, List<JogoSemanaData> parmJogos) throws SQLException {
		Map<Integer, BalnearioData> porId = new HashMap<>();
		boolean comRegras = false;
		for (BalnearioData b : parmLista) {
			porId.put(b.getId(), b);
			comRegras |= restricao(b) != null;
		}
		if (!comRegras) {
			return;
		}
		Set<Integer> equipas = new HashSet<>();
		for (MicroCicloHelper.Treino t : parmTreinos) {
			if (!t.ligado() && !t.evento()) {
				equipas.add(t.d.getIdEquipa());
			}
		}
		for (JogoSemanaData j : parmJogos) {
			if (casa(j)) {
				equipas.add(j.getIdEquipa());
			}
		}
		Map<Integer, Integer> escalao = new HashMap<>();
		Map<Integer, String> nomeEscalao = new HashMap<>();
		if (!equipas.isEmpty()) {
			try (PreparedStatement ps = conn.prepareStatement("select ee.id, ee.id_escalao, es.nome from escalao_epoca ee "
					+ "inner join epoca e on e.id = ee.id_epoca left join escalao es on es.id = ee.id_escalao "
					+ "where e.tenant_id = ? and ee.id in (" + marcas(equipas.size()) + ")")) {
				int i = 1;
				ps.setInt(i++, parmTenantId);
				for (Integer id : equipas) {
					ps.setInt(i++, id);
				}
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						escalao.put(rs.getInt(1), rs.getInt(2)); // 0 = equipa sem escalao
						nomeEscalao.put(rs.getInt(2), rs.getString(3));
					}
				}
			}
		}
		for (MicroCicloHelper.Treino t : parmTreinos) {
			if (t.ligado()) {
				continue;
			}
			Integer idEscalao = t.evento() ? null : escalao.getOrDefault(t.d.getIdEquipa(), 0); // P25: sem equipa = null
			t.d.setBalneariosNaoPermitidos(naoPermitidos(parmLista, null, idEscalao));
			if (ocupa(t) && t.d.getBalnearios() != null) {
				for (BalnearioAtribuidoData a : t.d.getBalnearios()) {
					String texto = avisoRegra(porId.get(a.getId()), null, idEscalao, nomeEscalao.get(idEscalao));
					if (texto != null) {
						if (t.d.getAvisosBalnearios() == null) {
							t.d.setAvisosBalnearios(new ArrayList<>());
						}
						t.d.getAvisosBalnearios().add(texto);
					}
				}
			}
		}
		for (JogoSemanaData j : parmJogos) {
			if (!casa(j)) {
				continue;
			}
			Integer idEscalao = escalao.getOrDefault(j.getIdEquipa(), 0);
			j.setBalneariosNaoPermitidos(naoPermitidos(parmLista, EQUIPA, idEscalao));
			j.setBalneariosNaoPermitidosAdversario(naoPermitidos(parmLista, ADVERSARIO, idEscalao));
			if ("CONCLUIDO".equals(j.getEstado())) {
				continue;
			}
			// tambem nos jogos sem hora (C10)
			for (String lado : List.of(EQUIPA, ADVERSARIO)) {
				List<BalnearioAtribuidoData> atribuidos = EQUIPA.equals(lado) ? j.getBalnearios() : j.getBalneariosAdversario();
				for (BalnearioAtribuidoData a : atribuidos == null ? List.<BalnearioAtribuidoData>of() : atribuidos) {
					String texto = avisoRegra(porId.get(a.getId()), lado, idEscalao, nomeEscalao.get(idEscalao));
					if (texto != null) {
						avisoJogo(j, texto);
					}
				}
			}
		}
	}

	/** Os ids de todos os balnearios do tenant (ativos e inativos) que o destinatario nao pode usar. */
	private static List<Integer> naoPermitidos(List<BalnearioData> parmLista, String parmLado, Integer parmIdEscalao) {
		List<Integer> ids = new ArrayList<>();
		for (BalnearioData b : parmLista) {
			if (!CalculoBalnearios.permite(restricao(b), parmLado, parmIdEscalao)) {
				ids.add(b.getId());
			}
		}
		return ids;
	}

	/** §5.2: o texto do aviso de regra violada, ou null se a regra permite (R56, P28: nao bloqueia). */
	private static String avisoRegra(BalnearioData b, String parmLado, Integer parmIdEscalao, String parmNomeEscalao) {
		if (b == null || CalculoBalnearios.permite(restricao(b), parmLado, parmIdEscalao)) {
			return null;
		}
		if (CalculoBalnearios.DEDICADO.equals(b.getUso())) {
			return "Balneário " + b.getNome() + ": só para " + b.getNotaUso().substring(NOTA_DEDICADO.length());
		}
		return ADVERSARIO.equals(parmLado) ? "Balneário " + b.getNome() + ": os adversários estão excluídos"
				: "Balneário " + b.getNome() + ": o " + parmNomeEscalao + " está excluído";
	}

	/** "Balneário {nome} ocupado {ini}–{fim} ({descricao do outro}); livre às {fim + L}" no evento a. */
	private static void avisa(Ocupante a, CalculoBalnearios.Ocupacao parmOutra, Ocupante parmOutro,
			Map<Integer, Integer> parmLibertacoes) {
		String texto = "Balneário " + a.nome() + " ocupado " + hhmm(parmOutra.inicio()) + "–" + hhmm(parmOutra.fim()) + " ("
				+ parmOutro.descricao() + "); livre às "
				+ hhmm(parmOutra.fim() + parmLibertacoes.getOrDefault(parmOutra.idBalneario(), 0));
		if (a.treino() != null) {
			if (a.treino().d.getAvisosBalnearios() == null) {
				a.treino().d.setAvisosBalnearios(new ArrayList<>());
			}
			a.treino().d.getAvisosBalnearios().add(texto);
		} else {
			avisoJogo(a.jogo(), texto);
		}
	}

	private static void avisoJogo(JogoSemanaData j, String parmTexto) {
		if (j.getAvisosBalnearios() == null) {
			j.setAvisosBalnearios(new ArrayList<>());
		}
		j.getAvisosBalnearios().add(parmTexto);
	}

	// ==================== atribuicao manual (B4, B16, §5.3) ====================

	/** "1, 3" pela ordem do clube, ou "—". */
	private static String nomes(List<BalnearioData> parmLista, Collection<Integer> parmIds) {
		List<String> nomes = new ArrayList<>();
		for (BalnearioData b : parmLista) {
			if (parmIds.contains(b.getId())) {
				nomes.add(b.getNome());
			}
		}
		return nomes.isEmpty() ? "—" : String.join(", ", nomes);
	}

	private static Set<Integer> conjunto(List<Integer> parmIds) {
		Set<Integer> ids = new LinkedHashSet<>();
		if (parmIds != null) {
			for (Integer id : parmIds) {
				if (id == null || !ids.add(id)) {
					throw new PedidoInvalidoException("Balneário inválido ou repetido.");
				}
			}
		}
		return ids;
	}

	/** B16: do tenant; os novos (que nao estavam) tem de estar ativos. */
	private static void validaIds(List<BalnearioData> parmLista, Set<Integer> parmNovos, Set<Integer> parmAtuais) {
		Map<Integer, BalnearioData> porId = new HashMap<>();
		parmLista.forEach(b -> porId.put(b.getId(), b));
		for (Integer id : parmNovos) {
			BalnearioData b = porId.get(id);
			if (b == null) {
				throw new PedidoInvalidoException("Balneário inválido.");
			}
			if (!b.isAtivo() && !parmAtuais.contains(id)) {
				throw new PedidoInvalidoException("O balneário " + b.getNome() + " está inativo.");
			}
		}
	}

	private static Set<Integer> idsComLock(Connection conn, String parmSql, int parmId, String parmLado)
			throws SQLException {
		Set<Integer> ids = new HashSet<>();
		try (PreparedStatement ps = conn.prepareStatement(parmSql)) {
			ps.setInt(1, parmId);
			if (parmLado != null) {
				ps.setString(2, parmLado);
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getInt(1));
				}
			}
		}
		return ids;
	}

	/** P14/§5.3: micro ciclo nao cancelado e semana editavel. */
	static void exigeSemanaEditavel(Connection conn, int parmTenantId, String parmEstadoMc, int parmDataInicio,
			int parmIdEpoca) throws SQLException {
		if (MicroCicloHelper.CANCELADO.equals(parmEstadoMc)) {
			throw new ConflitoException(MSG_MC_CANCELADO);
		}
		Integer epoca = MicroCicloHelper.epocaAtiva(conn, parmTenantId);
		if (MicroCicloHelper.semanaTerminou(parmDataInicio) || epoca == null || epoca != parmIdEpoca) {
			throw new ConflitoException(MSG_SO_LEITURA);
		}
	}

	/**
	 * Gancho de gravarTreino e rota gravarBalnearios: substitui os balnearios do treino por MANUAL (B4, B5). Sem
	 * alteracao (a lista nova igual a vista), nao faz nada nem valida o estado. Nao mexe em versao, estado nem
	 * notificacoes (B6, P12).
	 */
	static void gravaTreino(Connection conn, Identidade parmIdentidade, MicroCicloHelper.Treino t, List<Integer> parmIds,
			List<Integer> parmIdsAntes) throws SQLException {
		Set<Integer> novos = conjunto(parmIds);
		Set<Integer> vistos = conjunto(parmIdsAntes);
		if (novos.equals(vistos)) {
			return;
		}
		if (t.ligado()) {
			throw new PedidoInvalidoException(
					"Este físico faz parte de um treino principal: altere os balneários no principal.");
		}
		if (MicroCicloHelper.CANCELADO.equals(t.estadoBD) || MicroCicloHelper.REJEITADO.equals(t.estadoBD)) {
			throw new ConflitoException("Um treino cancelado ou rejeitado não pode ter balneários alterados.");
		}
		if (t.realizado()) {
			throw new ConflitoException("Um treino realizado não pode ter balneários alterados.");
		}
		if (MicroCicloHelper.inicioPassou(t.d.getData(), t.inicioSessao())) {
			throw new ConflitoException(MicroCicloHelper.MSG_JA_COMECOU);
		}
		int tenant = parmIdentidade.getTenantId();
		exigeSemanaEditavel(conn, tenant, t.mcEstado, t.dataInicio, t.idEpoca);
		// shortcut: o lock e so em treino_balneario (o treino ja e do tenant, carregaTreino); sem join para nao bloquear balneario
		Set<Integer> atuais = idsComLock(conn, "select id_balneario from treino_balneario where id_treino = ? for update",
				t.d.getId(), null);
		if (!atuais.equals(vistos)) {
			throw new ConflitoException(MSG_ALTERADOS);
		}
		List<BalnearioData> lista = lista(conn, tenant);
		validaIds(lista, novos, atuais);
		try (PreparedStatement ps = conn.prepareStatement("delete from treino_balneario where id_treino = ?")) {
			ps.setInt(1, t.d.getId());
			ps.executeUpdate();
		}
		insereTreino(conn, t.d.getId(), novos, MANUAL, parmIdentidade.getIdUtilizador());
		MicroCicloHelper.historico(conn, t.d.getIdMicroCiclo(), t.d.getId(), parmIdentidade.getIdUtilizador(),
				"Balneários | " + nomes(lista, atuais) + " => " + nomes(lista, novos));
		log.info("BalnearioHelper | gravaTreino | tenant:{} treino:{} antes:{} depois:{}", tenant, t.d.getId(),
				atuais.size(), novos.size());
	}

	private static void insereTreino(Connection conn, int parmIdTreino, Collection<Integer> parmIds, String parmOrigem,
			int parmUtilizador) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("insert into treino_balneario(id_treino, id_balneario, origem, "
				+ "data_alteracao, id_utilizador) values (?, ?, ?, now(), ?)")) {
			for (Integer id : parmIds) {
				ps.setInt(1, parmIdTreino);
				ps.setInt(2, id);
				ps.setString(3, parmOrigem);
				ps.setInt(4, parmUtilizador);
				ps.executeUpdate();
			}
		}
	}

	private static void insereJogo(Connection conn, int parmIdJogo, String parmLado, Collection<Integer> parmIds,
			String parmOrigem, int parmUtilizador) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("insert into jogo_balneario(id_jogo, lado, id_balneario, origem, "
				+ "data_alteracao, id_utilizador) values (?, ?, ?, ?, now(), ?)")) {
			for (Integer id : parmIds) {
				ps.setInt(1, parmIdJogo);
				ps.setString(2, parmLado);
				ps.setInt(3, id);
				ps.setString(4, parmOrigem);
				ps.setInt(5, parmUtilizador);
				ps.executeUpdate();
			}
		}
	}

	/** B14: ao ligar um fisico avulso, as suas linhas saem (o balneario e da sessao). */
	static void apagaDoTreino(Connection conn, MicroCicloHelper.Treino parmAvulso, int parmUtilizador) throws SQLException {
		int id = parmAvulso.d.getId();
		List<String> nomes = new ArrayList<>();
		try (PreparedStatement ps = conn.prepareStatement("select b.nome from treino_balneario tb inner join balneario b "
				+ "on b.id = tb.id_balneario inner join micro_ciclo m on m.tenant_id = b.tenant_id and m.id = ? "
				+ "where tb.id_treino = ? order by b.ordem, b.id")) {
			ps.setInt(1, parmAvulso.d.getIdMicroCiclo());
			ps.setInt(2, id);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					nomes.add(rs.getString(1));
				}
			}
		}
		if (nomes.isEmpty()) {
			return;
		}
		try (PreparedStatement ps = conn.prepareStatement("delete from treino_balneario where id_treino = ?")) {
			ps.setInt(1, id);
			ps.executeUpdate();
		}
		MicroCicloHelper.historico(conn, parmAvulso.d.getIdMicroCiclo(), id, parmUtilizador,
				"Balneários | " + String.join(", ", nomes) + " => — (ligado ao treino principal)");
	}

	private static void bloqueiaMicroCiclo(Connection conn, int parmIdMicroCiclo, int parmTenantId) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("select id from micro_ciclo where id = ? and tenant_id = ? for update")) {
			ps.setInt(1, parmIdMicroCiclo);
			ps.setInt(2, parmTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new AcessoNegadoException();
				}
			}
		}
	}

	/** B10: um evento (treino ou jogo); devolve a semana do evento. */
	public SemanaMicroCicloData gravaAtribuicao(Identidade parmIdentidade, PedidoBalneariosData p) {
		if (p == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		boolean treino = p.getIdTreino() != null && p.getIdTreino() > 0;
		boolean jogo = p.getIdJogo() != null && p.getIdJogo() > 0;
		if (treino == jogo) {
			throw new PedidoInvalidoException("Indique o treino ou o jogo.");
		}
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		int segunda = microCicloHelper.emTransacao("gravarBalnearios", conn -> {
			if (treino) {
				MicroCicloHelper.Treino t = microCicloHelper.carregaTreino(conn, p.getIdTreino(), tenant);
				if (p.getIds() == null) {
					throw new PedidoInvalidoException("Indique os balneários.");
				}
				bloqueiaMicroCiclo(conn, t.d.getIdMicroCiclo(), tenant);
				t = microCicloHelper.carregaTreino(conn, p.getIdTreino(), tenant); // ja com o lock (serializa com o calculo)
				gravaTreino(conn, parmIdentidade, t, p.getIds(), p.getIdsAntes());
				return t.dataInicio;
			}
			int idJogo = p.getIdJogo();
			java.sql.Date dataJogo;
			String hora;
			String tipoLocal;
			String estado;
			try (PreparedStatement ps = conn.prepareStatement("select j.`data`, j.hora, j.tipo_local, j.estado from jogo j "
					+ "inner join escalao_epoca ee on ee.id = j.equipa_id inner join epoca e on e.id = ee.id_epoca "
					+ "where j.id = ? and e.tenant_id = ?")) {
				ps.setInt(1, idJogo);
				ps.setInt(2, tenant);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) {
						throw new AcessoNegadoException();
					}
					dataJogo = rs.getDate(1);
					hora = rs.getString(2);
					tipoLocal = rs.getString(3);
					estado = rs.getString(4);
				}
			}
			if (!"C".equalsIgnoreCase(tipoLocal)) {
				throw new ConflitoException("Num jogo fora não se atribuem balneários.");
			}
			if ("CONCLUIDO".equalsIgnoreCase(estado)) {
				throw new ConflitoException("O jogo já terminou.");
			}
			if (dataJogo == null) {
				throw new ConflitoException("Não há micro ciclo nesta semana.");
			}
			int data = MicroCicloHelper.paraInt(dataJogo.toLocalDate());
			if (IndisponibilidadeHelper.jogoComecou(data, hora)) { // V7: como as indisponibilidades (Z7)
				throw new ConflitoException("O jogo já começou.");
			}
			MicroCicloData mc = MicroCicloHelper.microCicloDaSemana(conn, tenant, MicroCicloHelper.segundaDe(data));
			if (mc == null) {
				throw new ConflitoException("Não há micro ciclo nesta semana."); // P14
			}
			bloqueiaMicroCiclo(conn, mc.getId(), tenant);
			exigeSemanaEditavel(conn, tenant, mc.getEstado(), mc.getDataInicio(), mc.getIdEpoca());
			List<BalnearioData> lista = lista(conn, tenant);
			gravaLado(conn, lista, idJogo, EQUIPA, p.getIdsEquipa(), p.getIdsEquipaAntes(), utilizador);
			gravaLado(conn, lista, idJogo, ADVERSARIO, p.getIdsAdversario(), p.getIdsAdversarioAntes(), utilizador);
			return mc.getDataInicio();
		});
		log.info("BalnearioHelper | gravaAtribuicao | tenant:{} treino:{} jogo:{}", tenant, p.getIdTreino(), p.getIdJogo());
		return microCicloHelper.getSemana(parmIdentidade, segunda);
	}

	/** Um lado do jogo; lista nova null = nao mexer. Os jogos nao tem historico (R12). */
	private static void gravaLado(Connection conn, List<BalnearioData> parmLista, int parmIdJogo, String parmLado,
			List<Integer> parmIds, List<Integer> parmIdsAntes, int parmUtilizador) throws SQLException {
		if (parmIds == null) {
			return;
		}
		Set<Integer> novos = conjunto(parmIds);
		Set<Integer> vistos = conjunto(parmIdsAntes);
		if (novos.equals(vistos)) {
			return;
		}
		Set<Integer> atuais = idsComLock(conn,
				"select id_balneario from jogo_balneario where id_jogo = ? and lado = ? for update", parmIdJogo, parmLado);
		if (!atuais.equals(vistos)) {
			throw new ConflitoException(MSG_ALTERADOS);
		}
		validaIds(parmLista, novos, atuais);
		try (PreparedStatement ps = conn.prepareStatement("delete from jogo_balneario where id_jogo = ? and lado = ?")) {
			ps.setInt(1, parmIdJogo);
			ps.setString(2, parmLado);
			ps.executeUpdate();
		}
		insereJogo(conn, parmIdJogo, parmLado, novos, MANUAL, parmUtilizador);
	}

	// ==================== configuracao (§5.2) ====================

	public ConfigBalneariosData getConfig(Identidade parmIdentidade) {
		return microCicloHelper.leitura("getConfigBalnearios", conn -> carregaConfig(conn, parmIdentidade.getTenantId()));
	}

	private static ConfigBalneariosData carregaConfig(Connection conn, int parmTenantId) throws SQLException {
		ConfigBalneariosData c = new ConfigBalneariosData();
		Config config = config(conn, parmTenantId);
		c.setAntecedencia(config.antecedencia());
		c.setPermanencia(config.permanencia());
		c.setLibertacao(config.libertacao());
		c.setBalnearios(lista(conn, parmTenantId));
		List<Integer> ordem = new ArrayList<>();
		c.getBalnearios().forEach(b -> ordem.add(b.getId()));
		c.setOrdem(ordem);

		Map<Integer, List<Integer>> preferencias = new HashMap<>();
		List<Integer> adversarios = new ArrayList<>();
		try (PreparedStatement ps = conn.prepareStatement("select p.tipo, p.id_escalao, p.id_balneario "
				+ "from balneario_preferencia p inner join balneario b on b.id = p.id_balneario and b.tenant_id = ? "
				+ "order by p.tipo, p.id_escalao, p.ordem, p.id")) {
			ps.setInt(1, parmTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					if (ADVERSARIO.equals(rs.getString(1))) {
						adversarios.add(rs.getInt(3));
					} else {
						preferencias.computeIfAbsent(rs.getInt(2), k -> new ArrayList<>()).add(rs.getInt(3));
					}
				}
			}
		}
		c.setPreferenciaAdversarios(adversarios);
		List<Integer> prioridade = new ArrayList<>();
		try (PreparedStatement ps = conn.prepareStatement(
				"select id_escalao from balneario_prioridade where tenant_id = ? order by ordem, id_escalao")) {
			ps.setInt(1, parmTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					prioridade.add(rs.getInt(1));
				}
			}
		}
		c.setPrioridade(prioridade);

		// escaloes das equipas da epoca ativa; depois (V1) os que so tem regras gravadas
		List<ConfigBalneariosData.Escalao> escaloes = new ArrayList<>();
		Set<Integer> vistos = new HashSet<>();
		try (PreparedStatement ps = conn.prepareStatement("select distinct es.id, es.nome from escalao_epoca ee "
				+ "inner join epoca e on e.id = ee.id_epoca inner join escalao es on es.id = ee.id_escalao "
				+ "where e.tenant_id = ? and e.estado = '1' order by es.nome, es.id")) {
			ps.setInt(1, parmTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					escaloes.add(escalao(rs.getInt(1), rs.getString(2), true, preferencias));
					vistos.add(rs.getInt(1));
				}
			}
		}
		Set<Integer> comRegras = new HashSet<>(preferencias.keySet());
		comRegras.addAll(prioridade);
		c.getBalnearios().forEach(b -> comRegras.addAll(b.getUsoEscaloes())); // R45: tambem os que so estao em regras de uso
		comRegras.removeAll(vistos);
		if (!comRegras.isEmpty()) {
			try (PreparedStatement ps = conn.prepareStatement("select id, nome from escalao where id in ("
					+ marcas(comRegras.size()) + ") order by nome, id")) {
				int i = 1;
				for (Integer id : comRegras) {
					ps.setInt(i++, id);
				}
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						escaloes.add(escalao(rs.getInt(1), rs.getString(2), false, preferencias));
					}
				}
			}
		}
		c.setEscaloes(escaloes);
		return c;
	}

	private static ConfigBalneariosData.Escalao escalao(int parmId, String parmNome, boolean parmComEquipas,
			Map<Integer, List<Integer>> parmPreferencias) {
		ConfigBalneariosData.Escalao e = new ConfigBalneariosData.Escalao();
		e.setIdEscalao(parmId);
		e.setNome(parmNome);
		e.setComEquipas(parmComEquipas);
		e.setPreferencias(parmPreferencias.getOrDefault(parmId, new ArrayList<>()));
		return e;
	}

	private static int minutosValidos(Integer parmValor, String parmCampo) {
		if (parmValor == null || parmValor < 0 || parmValor > MAXIMO_MINUTOS) {
			throw new PedidoInvalidoException(parmCampo + " tem de estar entre 0 e " + MAXIMO_MINUTOS + " minutos.");
		}
		return parmValor;
	}

	public ConfigBalneariosData gravaBalneario(Identidade parmIdentidade, BalnearioData parmBalneario) {
		if (parmBalneario == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		String nome = MicroCicloHelper.limpa(parmBalneario.getNome(), MAXIMO_NOME, "O nome");
		if (nome == null) {
			throw new PedidoInvalidoException("Indique o nome do balneário.");
		}
		Integer libertacao = parmBalneario.getLibertacao() == null ? null
				: minutosValidos(parmBalneario.getLibertacao(), "A libertação");
		// V18 (C2): uso null = nao mexer na regra (R47)
		String uso = parmBalneario.getUso();
		boolean comLista = CalculoBalnearios.DEDICADO.equals(uso) || CalculoBalnearios.EXCLUSAO.equals(uso);
		if (uso != null && !comLista && !CalculoBalnearios.TODOS.equals(uso)) {
			throw new PedidoInvalidoException("Regra de uso inválida.");
		}
		boolean usoAdversarios = comLista && Boolean.TRUE.equals(parmBalneario.getUsoAdversarios());
		if (comLista && !usoAdversarios
				&& (parmBalneario.getUsoEscaloes() == null || parmBalneario.getUsoEscaloes().isEmpty())) {
			throw new PedidoInvalidoException("Escolha pelo menos um escalão.");
		}
		int tenant = parmIdentidade.getTenantId();
		ConfigBalneariosData resultado = microCicloHelper.emTransacao("gravarBalneario", conn -> {
			int idBalneario = parmBalneario.getId();
			try {
				if (parmBalneario.getId() <= 0) {
					int total = 0;
					int ordem = 0;
					try (PreparedStatement ps = conn.prepareStatement(
							"select count(*), coalesce(max(ordem), 0) from balneario where tenant_id = ? for update")) {
						ps.setInt(1, tenant);
						try (ResultSet rs = ps.executeQuery()) {
							if (rs.next()) {
								total = rs.getInt(1);
								ordem = rs.getInt(2);
							}
						}
					}
					if (total >= MAXIMO_BALNEARIOS) {
						throw new ConflitoException("No máximo " + MAXIMO_BALNEARIOS + " balneários.");
					}
					try (PreparedStatement ps = conn.prepareStatement("insert into balneario(tenant_id, nome, ordem, "
							+ "libertacao_min, estado, datacriacao, id_utilizador_criacao) values (?, ?, ?, ?, ?, now(), ?)",
							Statement.RETURN_GENERATED_KEYS)) {
						ps.setInt(1, tenant);
						ps.setString(2, nome);
						ps.setInt(3, ordem + 1);
						if (libertacao == null) {
							ps.setNull(4, java.sql.Types.INTEGER);
						} else {
							ps.setInt(4, libertacao);
						}
						ps.setString(5, parmBalneario.isAtivo() ? "1" : "0");
						ps.setInt(6, parmIdentidade.getIdUtilizador());
						ps.executeUpdate();
						try (ResultSet rs = ps.getGeneratedKeys()) {
							if (rs.next()) {
								idBalneario = rs.getInt(1);
							}
						}
					}
				} else {
					try (PreparedStatement ps = conn.prepareStatement("update balneario set nome = ?, libertacao_min = ?, "
							+ "estado = ? where id = ? and tenant_id = ?")) {
						ps.setString(1, nome);
						if (libertacao == null) {
							ps.setNull(2, java.sql.Types.INTEGER);
						} else {
							ps.setInt(2, libertacao);
						}
						ps.setString(3, parmBalneario.isAtivo() ? "1" : "0");
						ps.setInt(4, parmBalneario.getId());
						ps.setInt(5, tenant);
						if (ps.executeUpdate() == 0) {
							throw new AcessoNegadoException(); // inexistente ou de outro tenant
						}
					}
				}
			} catch (SQLIntegrityConstraintViolationException e) {
				throw new ConflitoException("Já existe o balneário " + nome + ".");
			}
			if (uso != null) {
				gravaUso(conn, tenant, idBalneario, uso,
						comLista ? listaValida(parmBalneario.getUsoEscaloes(), escaloesValidos(conn, tenant),
								"Escalão inválido ou repetido na regra de uso.") : List.of(),
						usoAdversarios);
			}
			return carregaConfig(conn, tenant);
		});
		log.info("BalnearioHelper | gravaBalneario | tenant:{} id:{} uso:{}", tenant, parmBalneario.getId(), uso);
		return resultado;
	}

	/** C2/C13: substitui a regra de uso; so depois do insert/update do balneario (ja do tenant). Nao mexe em atribuicoes. */
	private static void gravaUso(Connection conn, int parmTenantId, int parmIdBalneario, String parmUso,
			List<Integer> parmEscaloes, boolean parmAdversarios) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("update balneario set uso = ? where id = ? and tenant_id = ?")) {
			ps.setString(1, parmUso);
			ps.setInt(2, parmIdBalneario);
			ps.setInt(3, parmTenantId);
			if (ps.executeUpdate() == 0) {
				throw new AcessoNegadoException();
			}
		}
		try (PreparedStatement ps = conn.prepareStatement("delete from balneario_restricao where id_balneario = ?")) {
			ps.setInt(1, parmIdBalneario);
			ps.executeUpdate();
		}
		try (PreparedStatement ps = conn.prepareStatement(
				"insert into balneario_restricao(id_balneario, tipo, id_escalao) values (?, ?, ?)")) {
			for (Integer id : parmEscaloes) {
				ps.setInt(1, parmIdBalneario);
				ps.setString(2, "ESCALAO");
				ps.setInt(3, id);
				ps.executeUpdate();
			}
			if (parmAdversarios) {
				ps.setInt(1, parmIdBalneario);
				ps.setString(2, ADVERSARIO);
				ps.setNull(3, java.sql.Types.INTEGER);
				ps.executeUpdate();
			}
		}
	}

	public ConfigBalneariosData apagaBalneario(Identidade parmIdentidade, int parmIdBalneario) {
		int tenant = parmIdentidade.getTenantId();
		String emUso = "O balneário tem atribuições: desative-o.";
		ConfigBalneariosData resultado = microCicloHelper.emTransacao("apagarBalneario", conn -> {
			int apagados;
			try (PreparedStatement ps = conn.prepareStatement("delete from balneario where id = ? and tenant_id = ? "
					+ "and not exists (select 1 from treino_balneario tb where tb.id_balneario = ?) "
					+ "and not exists (select 1 from jogo_balneario jb where jb.id_balneario = ?)")) {
				ps.setInt(1, parmIdBalneario);
				ps.setInt(2, tenant);
				ps.setInt(3, parmIdBalneario);
				ps.setInt(4, parmIdBalneario);
				apagados = ps.executeUpdate();
			} catch (SQLIntegrityConstraintViolationException e) {
				throw new ConflitoException(emUso);
			}
			if (apagados == 0) {
				try (PreparedStatement ps = conn.prepareStatement("select 1 from balneario where id = ? and tenant_id = ?")) {
					ps.setInt(1, parmIdBalneario);
					ps.setInt(2, tenant);
					try (ResultSet rs = ps.executeQuery()) {
						if (!rs.next()) {
							throw new AcessoNegadoException();
						}
					}
				}
				throw new ConflitoException(emUso);
			}
			return carregaConfig(conn, tenant);
		});
		log.info("BalnearioHelper | apagaBalneario | tenant:{} id:{}", tenant, parmIdBalneario);
		return resultado;
	}

	/** Lista sem repetidos, com todos os ids no conjunto valido; null = vazia. */
	private static List<Integer> listaValida(List<Integer> parmIds, Set<Integer> parmValidos, String parmErro) {
		List<Integer> lista = new ArrayList<>();
		if (parmIds == null) {
			return lista;
		}
		Set<Integer> vistos = new HashSet<>();
		for (Integer id : parmIds) {
			if (id == null || !parmValidos.contains(id) || !vistos.add(id)) {
				throw new PedidoInvalidoException(parmErro);
			}
			lista.add(id);
		}
		return lista;
	}

	/** §5.2: substitui as regras (tempos, ordem, preferencias, prioridade); nao toca em atribuicoes (R42). */
	public ConfigBalneariosData gravaRegras(Identidade parmIdentidade, ConfigBalneariosData c) {
		if (c == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		int antecedencia = minutosValidos(c.getAntecedencia(), "A antecedência");
		int permanencia = minutosValidos(c.getPermanencia(), "A permanência");
		int libertacao = minutosValidos(c.getLibertacao(), "A libertação");
		if (c.getEscaloes() == null || c.getPreferenciaAdversarios() == null || c.getPrioridade() == null) {
			throw new PedidoInvalidoException("Faltam as preferências ou a prioridade.");
		}
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		ConfigBalneariosData resultado = microCicloHelper.emTransacao("gravarRegrasBalnearios", conn -> {
			Set<Integer> balnearios = new LinkedHashSet<>();
			try (PreparedStatement ps = conn.prepareStatement(
					"select id from balneario where tenant_id = ? order by ordem, id for update")) {
				ps.setInt(1, tenant);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						balnearios.add(rs.getInt(1));
					}
				}
			}
			if (c.getOrdem() != null && (c.getOrdem().size() != balnearios.size()
					|| !new HashSet<>(c.getOrdem()).equals(balnearios))) {
				throw new ConflitoException("A lista de balneários mudou entretanto. Recarregue.");
			}
			Set<Integer> escaloesValidos = escaloesValidos(conn, tenant);
			Map<Integer, List<Integer>> preferencias = new LinkedHashMap<>();
			for (ConfigBalneariosData.Escalao e : c.getEscaloes()) {
				if (e == null || !escaloesValidos.contains(e.getIdEscalao())) {
					throw new PedidoInvalidoException("Escalão inválido.");
				}
				if (preferencias.put(e.getIdEscalao(), listaValida(e.getPreferencias(), balnearios,
						"Balneário inválido ou repetido nas preferências.")) != null) {
					throw new PedidoInvalidoException("Escalão repetido.");
				}
			}
			List<Integer> adversarios = listaValida(c.getPreferenciaAdversarios(), balnearios,
					"Balneário inválido ou repetido nas preferências dos adversários.");
			List<Integer> prioridade = listaValida(c.getPrioridade(), escaloesValidos,
					"Escalão inválido ou repetido na prioridade.");

			try (PreparedStatement ps = conn.prepareStatement("insert into balneario_config(tenant_id, antecedencia_min, "
					+ "permanencia_min, libertacao_min, data_alteracao, id_utilizador_alteracao) values (?, ?, ?, ?, now(), ?) "
					+ "on duplicate key update antecedencia_min = values(antecedencia_min), "
					+ "permanencia_min = values(permanencia_min), libertacao_min = values(libertacao_min), "
					+ "data_alteracao = now(), id_utilizador_alteracao = values(id_utilizador_alteracao)")) {
				ps.setInt(1, tenant);
				ps.setInt(2, antecedencia);
				ps.setInt(3, permanencia);
				ps.setInt(4, libertacao);
				ps.setInt(5, utilizador);
				ps.executeUpdate();
			}
			if (c.getOrdem() != null) {
				try (PreparedStatement ps = conn.prepareStatement("update balneario set ordem = ? where id = ? and tenant_id = ?")) {
					int posicao = 1;
					for (Integer id : c.getOrdem()) {
						ps.setInt(1, posicao++);
						ps.setInt(2, id);
						ps.setInt(3, tenant);
						ps.executeUpdate();
					}
				}
			}
			try (PreparedStatement ps = conn.prepareStatement("delete p from balneario_preferencia p inner join balneario b "
					+ "on b.id = p.id_balneario where b.tenant_id = ?")) {
				ps.setInt(1, tenant);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = conn.prepareStatement("insert into balneario_preferencia(id_balneario, tipo, "
					+ "id_escalao, ordem) values (?, ?, ?, ?)")) {
				for (Map.Entry<Integer, List<Integer>> e : preferencias.entrySet()) {
					int posicao = 1;
					for (Integer id : e.getValue()) {
						ps.setInt(1, id);
						ps.setString(2, "ESCALAO");
						ps.setInt(3, e.getKey());
						ps.setInt(4, posicao++);
						ps.executeUpdate();
					}
				}
				int posicao = 1;
				for (Integer id : adversarios) {
					ps.setInt(1, id);
					ps.setString(2, ADVERSARIO);
					ps.setNull(3, java.sql.Types.INTEGER);
					ps.setInt(4, posicao++);
					ps.executeUpdate();
				}
			}
			try (PreparedStatement ps = conn.prepareStatement("delete from balneario_prioridade where tenant_id = ?")) {
				ps.setInt(1, tenant);
				ps.executeUpdate();
			}
			try (PreparedStatement ps = conn.prepareStatement(
					"insert into balneario_prioridade(tenant_id, id_escalao, ordem) values (?, ?, ?)")) {
				int posicao = 1;
				for (Integer id : prioridade) {
					ps.setInt(1, tenant);
					ps.setInt(2, id);
					ps.setInt(3, posicao++);
					ps.executeUpdate();
				}
			}
			return carregaConfig(conn, tenant);
		});
		log.info("BalnearioHelper | gravaRegras | tenant:{} escaloes:{} prioridade:{}", tenant, c.getEscaloes().size(),
				c.getPrioridade().size());
		return resultado;
	}

	// ==================== calculo automatico (B8, B9) ====================

	/** A entrada do calculo, as automaticas atuais dos pedidos (chave -> ids) e as descricoes. */
	private record Montagem(CalculoBalnearios.Entrada entrada, TreeMap<String, List<Integer>> atuais,
			Map<String, String> descricoes, Map<Integer, String> nomes) {
	}

	private static boolean temManual(List<BalnearioAtribuidoData> parmLista) {
		for (BalnearioAtribuidoData b : parmLista) {
			if (MANUAL.equals(b.getOrigem())) {
				return true;
			}
		}
		return false;
	}

	private static List<Integer> automaticas(List<BalnearioAtribuidoData> parmLista) {
		List<Integer> ids = new ArrayList<>();
		for (BalnearioAtribuidoData b : parmLista) {
			if (AUTOMATICA.equals(b.getOrigem())) {
				ids.add(b.getId());
			}
		}
		return ids;
	}

	/** P11: pedidos = os eventos em calculo sem manual (no lado); ocupacoes = o resto, menos as automaticas dos pedidos. */
	private Montagem montaEntrada(Connection conn, Identidade parmIdentidade, MicroCicloData mc) throws SQLException {
		int tenant = parmIdentidade.getTenantId();
		List<JogoSemanaData> jogos = MicroCicloHelper.jogosSemana(conn, tenant, mc.getDataInicio(), null);
		List<MicroCicloHelper.Treino> treinos = microCicloHelper.listaTreinos(conn, parmIdentidade, mc.getId(), jogos);
		Config config = config(conn, tenant);
		List<BalnearioData> lista = lista(conn, tenant);
		Map<Integer, String> nomes = new HashMap<>();
		List<CalculoBalnearios.Balneario> ativos = new ArrayList<>();
		TreeMap<Integer, CalculoBalnearios.Restricao> restricoes = new TreeMap<>(); // C6: so dos ativos (entra na assinatura)
		for (BalnearioData b : lista) {
			nomes.put(b.getId(), b.getNome());
			if (b.isAtivo()) {
				ativos.add(new CalculoBalnearios.Balneario(b.getId(), b.getOrdem(), libertacao(b, config)));
				if (restricao(b) != null) {
					restricoes.put(b.getId(), restricao(b));
				}
			}
		}
		Map<Integer, Integer> escalaoDaEquipa = new HashMap<>();
		Map<Integer, String> nomeEscalao = new HashMap<>();
		try (PreparedStatement ps = conn.prepareStatement("select ee.id, ee.id_escalao, es.nome from escalao_epoca ee "
				+ "inner join epoca e on e.id = ee.id_epoca left join escalao es on es.id = ee.id_escalao "
				+ "where e.tenant_id = ? and ee.id_epoca = ?")) {
			ps.setInt(1, tenant);
			ps.setInt(2, mc.getIdEpoca());
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					escalaoDaEquipa.put(rs.getInt(1), rs.getInt(2));
					nomeEscalao.put(rs.getInt(2), rs.getString(3));
				}
			}
		}

		List<CalculoBalnearios.Pedido> pedidos = new ArrayList<>();
		List<CalculoBalnearios.Ocupacao> ocupadas = new ArrayList<>();
		TreeMap<String, List<Integer>> atuais = new TreeMap<>();
		Map<String, String> descricoes = new HashMap<>();
		TreeMap<Integer, String> nomesUsados = new TreeMap<>();
		for (MicroCicloHelper.Treino t : treinos) {
			if (!ocupa(t) || t.d.getBalnearios() == null) {
				continue;
			}
			String chave = "TREINO:" + t.d.getId();
			int[] janela = janelaTreino(t, config);
			boolean pedido = entraNoCalculo(t) && !temManual(t.d.getBalnearios());
			if (pedido) {
				int idEscalao = escalaoDaEquipa.getOrDefault(t.d.getIdEquipa(), 0);
				nomesUsados.put(idEscalao, nomeEscalao.getOrDefault(idEscalao, ""));
				pedidos.add(new CalculoBalnearios.Pedido(CalculoBalnearios.TREINO, t.d.getId(), null, t.d.getData(), janela[0],
						janela[1], idEscalao));
				atuais.put(chave, automaticas(t.d.getBalnearios()));
				descricoes.put(chave, descricao(t));
			}
			for (BalnearioAtribuidoData b : t.d.getBalnearios()) {
				if (!(pedido && AUTOMATICA.equals(b.getOrigem()))) {
					ocupadas.add(new CalculoBalnearios.Ocupacao(chave, b.getId(), t.d.getData(), janela[0], janela[1]));
				}
			}
		}
		for (JogoSemanaData j : jogos) {
			if (!ocupa(j, config)) {
				continue;
			}
			int[] janela = janelaJogo(j, config);
			boolean emCalculo = entraNoCalculo(j, config);
			int idEscalao = escalaoDaEquipa.getOrDefault(j.getIdEquipa(), 0);
			for (String lado : List.of(EQUIPA, ADVERSARIO)) {
				List<BalnearioAtribuidoData> atribuidos = EQUIPA.equals(lado) ? j.getBalnearios() : j.getBalneariosAdversario();
				if (atribuidos == null) {
					atribuidos = new ArrayList<>();
				}
				String chave = "JOGO:" + j.getId() + ":" + lado;
				boolean pedido = emCalculo && !temManual(atribuidos);
				if (pedido) {
					nomesUsados.put(idEscalao, nomeEscalao.getOrDefault(idEscalao, ""));
					pedidos.add(new CalculoBalnearios.Pedido(CalculoBalnearios.JOGO, j.getId(), lado, j.getData(), janela[0],
							janela[1], idEscalao));
					atuais.put(chave, automaticas(atribuidos));
					descricoes.put(chave, descricao(j, lado));
				}
				for (BalnearioAtribuidoData b : atribuidos) {
					if (!(pedido && AUTOMATICA.equals(b.getOrigem()))) {
						ocupadas.add(new CalculoBalnearios.Ocupacao(chave, b.getId(), j.getData(), janela[0], janela[1]));
					}
				}
			}
		}
		// ordem estavel para o toString (assinatura)
		pedidos.sort(java.util.Comparator.comparing(CalculoBalnearios.Pedido::chave));
		ocupadas.sort(java.util.Comparator.comparing(CalculoBalnearios.Ocupacao::chave)
				.thenComparingInt(CalculoBalnearios.Ocupacao::idBalneario));
		ativos.sort(java.util.Comparator.comparingInt(CalculoBalnearios.Balneario::id));

		TreeMap<Integer, List<Integer>> preferencias = new TreeMap<>();
		List<Integer> adversarios = new ArrayList<>();
		try (PreparedStatement ps = conn.prepareStatement("select p.tipo, p.id_escalao, p.id_balneario "
				+ "from balneario_preferencia p inner join balneario b on b.id = p.id_balneario and b.tenant_id = ? "
				+ "order by p.tipo, p.id_escalao, p.ordem, p.id")) {
			ps.setInt(1, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					if (ADVERSARIO.equals(rs.getString(1))) {
						adversarios.add(rs.getInt(3));
					} else {
						preferencias.computeIfAbsent(rs.getInt(2), k -> new ArrayList<>()).add(rs.getInt(3));
					}
				}
			}
		}
		List<Integer> prioridade = new ArrayList<>();
		try (PreparedStatement ps = conn.prepareStatement(
				"select id_escalao from balneario_prioridade where tenant_id = ? order by ordem, id_escalao")) {
			ps.setInt(1, tenant);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					prioridade.add(rs.getInt(1));
				}
			}
		}
		CalculoBalnearios.Regras regras = new CalculoBalnearios.Regras(preferencias, adversarios, prioridade, nomesUsados,
				restricoes);
		return new Montagem(new CalculoBalnearios.Entrada(pedidos, ocupadas, ativos, regras), atuais, descricoes, nomes);
	}

	/** B8: SHA-256 hexadecimal da entrada e das automaticas atuais. */
	static String assinatura(Montagem m, int parmIdMicroCiclo) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest((parmIdMicroCiclo + "|" + m.entrada() + "|" + m.atuais()).getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static String mudanca(List<Integer> parmAtuais, Integer parmProposto) {
		if (parmAtuais.isEmpty()) {
			return parmProposto == null ? "SEM_LIVRE" : "NOVO";
		}
		if (parmProposto == null) {
			return "RETIRADO";
		}
		return parmAtuais.size() == 1 && parmAtuais.get(0).equals(parmProposto) ? "IGUAL" : "ALTERADO";
	}

	private static boolean altera(String parmMudanca) {
		return "NOVO".equals(parmMudanca) || "ALTERADO".equals(parmMudanca) || "RETIRADO".equals(parmMudanca);
	}

	private static void exigeAtivos(Montagem m) {
		if (m.entrada().balnearios().isEmpty()) {
			throw new ConflitoException(MSG_SEM_ATIVOS);
		}
	}

	/** Pre-visualizacao: nao escreve nada (R21). */
	public CalculoBalneariosData calcula(Identidade parmIdentidade, int parmIdMicroCiclo) {
		int tenant = parmIdentidade.getTenantId();
		CalculoBalneariosData resultado = microCicloHelper.leitura("calcularBalnearios", conn -> {
			MicroCicloData mc = MicroCicloHelper.carregaMicroCiclo(conn, parmIdMicroCiclo, tenant);
			exigeSemanaEditavel(conn, tenant, mc.getEstado(), mc.getDataInicio(), mc.getIdEpoca());
			Montagem m = montaEntrada(conn, parmIdentidade, mc);
			exigeAtivos(m);
			CalculoBalneariosData d = new CalculoBalneariosData();
			d.setIdMicroCiclo(parmIdMicroCiclo);
			d.setAssinatura(assinatura(m, parmIdMicroCiclo));
			for (CalculoBalnearios.Atribuicao a : CalculoBalnearios.calcula(m.entrada())) {
				CalculoBalnearios.Pedido p = a.pedido();
				List<Integer> atuais = m.atuais().getOrDefault(p.chave(), List.of());
				CalculoBalneariosData.Item item = new CalculoBalneariosData.Item();
				item.setTipo(p.tipo());
				item.setId(p.id());
				item.setLado(p.lado());
				item.setData(p.data());
				item.setInicio(hhmm(p.inicio()));
				item.setFim(hhmm(p.fim()));
				item.setDescricao(m.descricoes().get(p.chave()));
				atuais.forEach(id -> item.getAtuais().add(m.nomes().get(id)));
				item.setIdProposto(a.idBalneario());
				item.setNomeProposto(a.idBalneario() == null ? null : m.nomes().get(a.idBalneario()));
				item.setOrigem(a.origem());
				item.setMudanca(mudanca(atuais, a.idBalneario()));
				if (altera(item.getMudanca())) {
					d.setAlteracoes(d.getAlteracoes() + 1);
				}
				if (a.idBalneario() == null) {
					boolean semPermitido = CalculoBalnearios.permitidos(m.entrada().balnearios(), m.entrada().regras(), p)
							.isEmpty();
					item.setMotivo(semPermitido ? CalculoBalnearios.SEM_PERMITIDO : CalculoBalnearios.SEM_LIVRE);
					d.getSemBalneario().add((semPermitido ? "Sem balneário permitido: " : "Sem balneário livre: ")
							+ item.getDescricao());
				}
				d.getItens().add(item);
			}
			return d;
		});
		log.info("BalnearioHelper | calcula | tenant:{} microCiclo:{} itens:{} alteracoes:{}", tenant, parmIdMicroCiclo,
				resultado.getItens().size(), resultado.getAlteracoes());
		return resultado;
	}

	private static List<Integer> idsComLock(Connection conn, String parmSql, Collection<Integer> parmIds)
			throws SQLException {
		List<Integer> ids = new ArrayList<>();
		if (parmIds != null && parmIds.isEmpty()) {
			return ids;
		}
		try (PreparedStatement ps = conn.prepareStatement(parmIds == null ? parmSql
				: String.format(parmSql, marcas(parmIds.size())))) {
			int i = 1;
			if (parmIds != null) {
				for (Integer id : parmIds) {
					ps.setInt(i++, id);
				}
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getInt(1));
				}
			}
		}
		return ids;
	}

	/** B9: tudo ou nada, com locks; assinatura diferente -> 409. */
	public SemanaMicroCicloData gravaCalculo(Identidade parmIdentidade, int parmIdMicroCiclo, String parmAssinatura) {
		if (parmAssinatura == null || parmAssinatura.trim().isEmpty()) {
			throw new PedidoInvalidoException("Pedido sem assinatura.");
		}
		int tenant = parmIdentidade.getTenantId();
		int utilizador = parmIdentidade.getIdUtilizador();
		int[] gravados = new int[1];
		int segunda = microCicloHelper.emTransacao("gravarCalculoBalnearios", conn -> {
			// (1) micro ciclo; (2) treinos; (3) jogos (so a tabela jogo); (4) atribuicoes
			bloqueiaMicroCiclo(conn, parmIdMicroCiclo, tenant);
			MicroCicloData mc = MicroCicloHelper.carregaMicroCiclo(conn, parmIdMicroCiclo, tenant);
			List<Integer> idsTreinos = new ArrayList<>();
			try (PreparedStatement ps = conn.prepareStatement("select id from treino where id_micro_ciclo = ? for update")) {
				ps.setInt(1, parmIdMicroCiclo);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						idsTreinos.add(rs.getInt(1));
					}
				}
			}
			List<Integer> idsJogos = new ArrayList<>();
			for (JogoSemanaData j : MicroCicloHelper.jogosSemana(conn, tenant, mc.getDataInicio(), null)) {
				idsJogos.add(j.getId());
			}
			idsComLock(conn, "select id from jogo where id in (%s) for update", idsJogos);
			idsComLock(conn, "select id_treino from treino_balneario where id_treino in (%s) for update", idsTreinos);
			idsComLock(conn, "select id_jogo from jogo_balneario where id_jogo in (%s) for update", idsJogos);

			// (5) recalcula com os dados ja bloqueados
			exigeSemanaEditavel(conn, tenant, mc.getEstado(), mc.getDataInicio(), mc.getIdEpoca());
			Montagem m = montaEntrada(conn, parmIdentidade, mc);
			exigeAtivos(m);
			if (!assinatura(m, parmIdMicroCiclo).equals(parmAssinatura.trim())) {
				throw new ConflitoException(MSG_SEMANA_MUDOU);
			}
			// (6) aplica: apaga as AUTOMATICA dos lados alterados e insere as novas; historico nos treinos
			for (CalculoBalnearios.Atribuicao a : CalculoBalnearios.calcula(m.entrada())) {
				CalculoBalnearios.Pedido p = a.pedido();
				List<Integer> atuais = m.atuais().getOrDefault(p.chave(), List.of());
				if (!altera(mudanca(atuais, a.idBalneario()))) {
					continue;
				}
				List<Integer> novos = a.idBalneario() == null ? List.of() : List.of(a.idBalneario());
				if (CalculoBalnearios.TREINO.equals(p.tipo())) {
					try (PreparedStatement ps = conn.prepareStatement(
							"delete from treino_balneario where id_treino = ? and origem = 'AUTOMATICA'")) {
						ps.setInt(1, p.id());
						ps.executeUpdate();
					}
					insereTreino(conn, p.id(), novos, AUTOMATICA, utilizador);
					MicroCicloHelper.historico(conn, parmIdMicroCiclo, p.id(), utilizador, "Balneários | "
							+ nomesIds(m.nomes(), atuais) + " => " + nomesIds(m.nomes(), novos) + " (cálculo automático)");
				} else {
					try (PreparedStatement ps = conn.prepareStatement(
							"delete from jogo_balneario where id_jogo = ? and lado = ? and origem = 'AUTOMATICA'")) {
						ps.setInt(1, p.id());
						ps.setString(2, p.lado());
						ps.executeUpdate();
					}
					insereJogo(conn, p.id(), p.lado(), novos, AUTOMATICA, utilizador);
				}
				gravados[0]++;
			}
			return mc.getDataInicio();
		});
		log.info("BalnearioHelper | gravaCalculo | tenant:{} microCiclo:{} alteracoes:{}", tenant, parmIdMicroCiclo,
				gravados[0]);
		return microCicloHelper.getSemana(parmIdentidade, segunda);
	}

	private static String nomesIds(Map<Integer, String> parmNomes, List<Integer> parmIds) {
		List<String> nomes = new ArrayList<>();
		parmIds.forEach(id -> nomes.add(parmNomes.get(id)));
		return nomes.isEmpty() ? "—" : String.join(", ", nomes);
	}
}
