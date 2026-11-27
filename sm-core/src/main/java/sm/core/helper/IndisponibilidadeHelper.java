package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import sm.core.data.IndisponibilidadeData;
import sm.core.data.JogadorConvocado;
import sm.core.data.PedidoIndisponibilidadeData;
import sm.core.data.TreinoAusenciaData;
import sm.core.security.AcessoNegadoException;
import sm.core.security.ConflitoException;
import sm.core.security.Identidade;
import sm.core.security.PedidoInvalidoException;

/**
 * Indisponibilidades do atleta (V16, desenho .melhorias/indisponibilidades/02-desenho.md). Os treinos sincronizam-se em
 * MicroCicloHelper.sincronizaTreino; os jogos aqui (sincronizaJogo), sem nunca escrever em jogo. Logs so com ids,
 * acao e contagens: nunca o motivo nem a categoria (RGPD).
 */
@Component
public class IndisponibilidadeHelper {

	private static final Logger log = LoggerFactory.getLogger(IndisponibilidadeHelper.class);

	// ==================== decisoes do dono (§0), num so sitio ====================

	/** Q1: so nos tenants com o Micro Ciclo ligado. */
	static final String FUNCIONALIDADE = FuncionalidadeHelper.MICRO_CICLO;
	/** Q5: duracao maxima, em dias (inclusiva). */
	static final int MAXIMO_DIAS = 90;
	/** Q15: a lista mostra as terminadas e canceladas ate 30 dias depois. */
	static final int DIAS_HISTORICO = 30;
	/** V1: o historico do treino nao leva a categoria (e visivel a todos os treinadores). */
	static final String TEXTO_HISTORICO = "Ausência indicada (indisponibilidade %s–%s) | %s";
	static final String TEXTO_HISTORICO_RETIRADA = "Ausência retirada (indisponibilidade %s–%s) | %s";
	/** Q13: o que ve quem nao pode ver o motivo. */
	static final String TEXTO_MASCARA = "Indisponível";

	static final String ATIVA = "ATIVA";
	static final String CANCELADA = "CANCELADA";
	static final String AUTOMATICA = "AUTOMATICA";
	static final String LIBERTADA = "LIBERTADA";
	static final String RETIRADA = "RETIRADA";
	static final String INDISPONIVEL = "INDISPONÍVEL";
	static final String CONVOCADO = "CONVOCADO";

	@Autowired
	private MicroCicloHelper microCicloHelper;

	@Autowired
	private NotificacaoHelper notificacaoHelper;

	@Autowired
	private FuncionalidadeHelper funcionalidadeHelper;

	/** Q3: estado da presenca pre-preenchida (hoje sempre "Ausente (Avisou)", tambem na Lesao). */
	static String estadoPresenca(String parmCategoria) {
		return "Ausente (Avisou)";
	}

	/** Q10, Z9: obs da linha automatica do jogo (sem categoria nem motivo). */
	static String obsJogo(String parmCategoria) {
		return "Indisponível (indicado pelo atleta)";
	}

	/** Q13: ADMIN, ou TREINADOR com a equipa do contexto ou com uma equipa do atleta. */
	static boolean podeVerMotivo(Identidade parmIdentidade, Set<Integer> parmEquipasConta, int parmEquipaContexto,
			Set<Integer> parmEquipasAtleta) {
		if (parmIdentidade.isAdmin()) {
			return true;
		}
		if (!parmIdentidade.isTreinador()) {
			return false;
		}
		return parmEquipasConta.contains(parmEquipaContexto) || !Collections.disjoint(parmEquipasConta, parmEquipasAtleta);
	}

	/** Q13: so as linhas vindas de indisponibilidade (apagar a 1.a condicao estende as ausencias manuais). */
	static void mascara(TreinoAusenciaData a, boolean parmPode) {
		if (a.getIdIndisponibilidade() != null && !parmPode) {
			a.setCategoria(TEXTO_MASCARA);
			a.setMotivo(null);
		}
	}

	// ==================== leitura ====================

	/** As ATIVA com fim nos ultimos 30 dias (ou futuras) e as CANCELADA alteradas nos ultimos 30 dias. */
	public List<IndisponibilidadeData> lista(Identidade parmIdentidade, int parmIdJogador) {
		MicroCicloHelper.exigeAtleta(parmIdentidade, parmIdJogador);
		int tenant = parmIdentidade.getTenantId();
		return microCicloHelper.leitura("getIndisponibilidades", conn -> {
			List<Integer> ids = new ArrayList<>();
			try (PreparedStatement ps = conn.prepareStatement("select i.id from indisponibilidade i "
					+ "inner join jogador j on j.id = i.id_jogador where j.tenant_id = ? and i.id_jogador = ? "
					+ "and ((i.estado = 'ATIVA' and i.data_fim >= ?) or (i.estado = 'CANCELADA' "
					+ "and i.data_alteracao >= now() - interval " + DIAS_HISTORICO + " day)) order by i.data_inicio, i.id")) {
				ps.setInt(1, tenant);
				ps.setInt(2, parmIdJogador);
				ps.setInt(3, MicroCicloHelper.somaDias(MicroCicloHelper.hoje(), -DIAS_HISTORICO));
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						ids.add(rs.getInt(1));
					}
				}
			}
			List<IndisponibilidadeData> lista = new ArrayList<>();
			for (Integer id : ids) {
				lista.add(carrega(conn, tenant, id));
			}
			return lista;
		});
	}

	/** Le uma indisponibilidade (ja verificada como do atleta) com as contagens e as permissoes. */
	private static IndisponibilidadeData carrega(Connection conn, int parmTenantId, int parmId) throws SQLException {
		IndisponibilidadeData d = new IndisponibilidadeData();
		int idJogador;
		try (PreparedStatement ps = conn.prepareStatement("select id, id_jogador, data_inicio, data_fim, categoria, motivo, "
				+ "estado from indisponibilidade where id = ?")) {
			ps.setInt(1, parmId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new AcessoNegadoException();
				}
				d.setId(rs.getInt("id"));
				idJogador = rs.getInt("id_jogador");
				d.setDataInicio(rs.getInt("data_inicio"));
				d.setDataFim(rs.getInt("data_fim"));
				d.setCategoria(rs.getString("categoria"));
				d.setMotivo(rs.getString("motivo"));
				d.setEstado(rs.getString("estado"));
			}
		}
		int hoje = MicroCicloHelper.hoje();
		boolean ativa = ATIVA.equals(d.getEstado());
		d.setSituacao(!ativa ? CANCELADA
				: d.getDataFim() < hoje ? "TERMINADA" : d.getDataInicio() <= hoje ? "EM_CURSO" : "AGENDADA");
		d.setPodeEditar(ativa && d.getDataFim() >= hoje);
		d.setPodeMudarInicio(d.isPodeEditar() && d.getDataInicio() >= hoje);
		d.setPodeCancelar(d.isPodeEditar());
		d.setTreinos(conta(conn, "select count(*) from treino_ausencia where id_indisponibilidade = ? and estado = 'ATIVA'",
				parmId));
		d.setJogos(conta(conn, "select count(*) from indisponibilidade_jogo where id_indisponibilidade = ? "
				+ "and estado = 'AUTOMATICA'", parmId));
		if (ativa) {
			for (int[] jogo : convocadoEm(conn, parmTenantId, idJogador, Math.max(hoje, d.getDataInicio()), d.getDataFim())) {
				d.getConvocadoEm().add(jogo[1]);
			}
		}
		return d;
	}

	private static int conta(Connection conn, String parmSql, int parmId) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(parmSql)) {
			ps.setInt(1, parmId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next() ? rs.getInt(1) : 0;
			}
		}
	}

	/** R26: jogos por concluir no intervalo em que o atleta esta CONVOCADO: {idJogo, data yyyymmdd, idEquipa}. */
	private static List<int[]> convocadoEm(Connection conn, int parmTenantId, int parmIdJogador, int parmDe, int parmAte)
			throws SQLException {
		List<int[]> lista = new ArrayList<>();
		if (parmDe > parmAte) {
			return lista;
		}
		try (PreparedStatement ps = conn.prepareStatement("select j.id, j.`data`, j.equipa_id from jogo j "
				+ "inner join escalao_epoca ee on ee.id = j.equipa_id inner join epoca e on e.id = ee.id_epoca "
				+ "inner join jogo_jogador jj on jj.id_jogo = j.id and jj.id_jogador = ? and jj.estado = 'CONVOCADO' "
				+ "where e.tenant_id = ? and j.`data` between ? and ? and coalesce(j.estado, '') <> 'CONCLUIDO' "
				+ "order by j.`data`, j.hora, j.id")) {
			ps.setInt(1, parmIdJogador);
			ps.setInt(2, parmTenantId);
			ps.setDate(3, java.sql.Date.valueOf(MicroCicloHelper.paraData(parmDe)));
			ps.setDate(4, java.sql.Date.valueOf(MicroCicloHelper.paraData(parmAte)));
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					lista.add(new int[] { rs.getInt(1), MicroCicloHelper.paraInt(rs.getDate(2).toLocalDate()), rs.getInt(3) });
				}
			}
		}
		return lista;
	}

	// ==================== escrita (so o atleta, Q8) ====================

	/** R1-R6: criar (id 0) ou editar. Com simular, corre tudo e faz rollback (Z11). */
	public IndisponibilidadeData grava(Identidade parmIdentidade, int parmIdJogador, PedidoIndisponibilidadeData parmPedido) {
		MicroCicloHelper.exigeAtleta(parmIdentidade, parmIdJogador);
		if (parmPedido == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		if (parmPedido.getDataInicio() == null || parmPedido.getDataFim() == null) {
			throw new PedidoInvalidoException("Indique as datas de início e de fim.");
		}
		int inicio = parmPedido.getDataInicio();
		int fim = parmPedido.getDataFim();
		int id = parmPedido.getId() == null ? 0 : parmPedido.getId();
		validaDatas(inicio, fim, id <= 0);
		String categoria = parmPedido.getCategoria() == null ? null : parmPedido.getCategoria().trim().toUpperCase();
		if (categoria == null || !MicroCicloHelper.CATEGORIAS.contains(categoria)) {
			throw new PedidoInvalidoException("Escolha o motivo.");
		}
		String motivo = MicroCicloHelper.limpa(parmPedido.getMotivo(), 90, "O motivo");
		if ("OUTRO".equals(categoria) && motivo == null) {
			throw new PedidoInvalidoException("Escreva o motivo.");
		}
		boolean simular = Boolean.TRUE.equals(parmPedido.getSimular());
		int tenant = parmIdentidade.getTenantId();
		int jid = parmIdentidade.getIdJogador();
		MicroCicloHelper.Trabalho<IndisponibilidadeData> trabalho = conn -> {
			bloqueiaJogador(conn, tenant, jid);
			int hoje = MicroCicloHelper.hoje();
			int idFinal = id;
			int deAntigo = inicio;
			int ateAntigo = fim;
			if (id > 0) {
				int[] antiga = carregaDoAtleta(conn, id, jid);
				if (antiga[2] != 1 || antiga[1] < hoje) {
					throw new ConflitoException("Uma indisponibilidade terminada ou cancelada não se edita.");
				}
				deAntigo = antiga[0];
				ateAntigo = antiga[1];
				if (deAntigo < hoje && inicio != deAntigo) {
					throw new ConflitoException("Para mudar o início, cancela e cria outra.");
				}
				if (deAntigo >= hoje && inicio < hoje) {
					throw new PedidoInvalidoException("A data de início não pode ser no passado.");
				}
			}
			exigeSemSobreposicao(conn, jid, id, inicio, fim);
			if (id > 0) {
				try (PreparedStatement ps = conn.prepareStatement("update indisponibilidade set data_inicio = ?, data_fim = ?, "
						+ "categoria = ?, motivo = ?, data_alteracao = now() where id = ? and id_jogador = ?")) {
					ps.setInt(1, inicio);
					ps.setInt(2, fim);
					ps.setString(3, categoria);
					ps.setString(4, motivo);
					ps.setInt(5, id);
					ps.setInt(6, jid);
					ps.executeUpdate();
				}
			} else {
				try (PreparedStatement ps = conn.prepareStatement("insert into indisponibilidade(id_jogador, data_inicio, "
						+ "data_fim, categoria, motivo, estado, data_criacao, data_alteracao, id_utilizador) "
						+ "values (?, ?, ?, ?, ?, 'ATIVA', now(), now(), ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
					ps.setInt(1, jid);
					ps.setInt(2, inicio);
					ps.setInt(3, fim);
					ps.setString(4, categoria);
					ps.setString(5, motivo);
					ps.setInt(6, parmIdentidade.getIdUtilizador());
					ps.executeUpdate();
					try (ResultSet rs = ps.getGeneratedKeys()) {
						rs.next();
						idFinal = rs.getInt(1);
					}
				}
			}
			Efeitos efeitos = aplica(conn, tenant, jid, idFinal, Math.max(hoje, Math.min(deAntigo, inicio)),
					Math.max(ateAntigo, fim));
			boolean semTreinador = notifica(conn, parmIdentidade, jid, idFinal,
					id > 0 ? "INDISPONIBILIDADE_ALTERADA" : "INDISPONIBILIDADE_INDICADA", inicio, fim, efeitos);
			IndisponibilidadeData d = carrega(conn, tenant, idFinal);
			d.setSemTreinador(semTreinador);
			d.setSimulacao(simular);
			log.info("IndisponibilidadeHelper | grava | jogador:{} indisponibilidade:{} editar:{} simular:{} treinos:{} jogos:{}",
					jid, idFinal, id > 0, simular, d.getTreinos(), d.getJogos());
			return d;
		};
		return simular ? microCicloHelper.emSimulacao("gravarIndisponibilidade", trabalho)
				: microCicloHelper.emTransacao("gravarIndisponibilidade", trabalho);
	}

	/** R7: a que ainda nao comecou fica CANCELADA; a que esta em curso termina ontem. */
	public IndisponibilidadeData cancela(Identidade parmIdentidade, int parmIdJogador, int parmId, boolean parmSimular) {
		MicroCicloHelper.exigeAtleta(parmIdentidade, parmIdJogador);
		int tenant = parmIdentidade.getTenantId();
		int jid = parmIdentidade.getIdJogador();
		MicroCicloHelper.Trabalho<IndisponibilidadeData> trabalho = conn -> {
			bloqueiaJogador(conn, tenant, jid);
			int hoje = MicroCicloHelper.hoje();
			int[] antiga = carregaDoAtleta(conn, parmId, jid);
			if (antiga[2] != 1 || antiga[1] < hoje) {
				throw new ConflitoException("Esta indisponibilidade já terminou ou foi cancelada.");
			}
			boolean naoComecou = antiga[0] >= hoje;
			try (PreparedStatement ps = conn.prepareStatement(naoComecou
					? "update indisponibilidade set estado = 'CANCELADA', data_alteracao = now() where id = ? and id_jogador = ?"
					: "update indisponibilidade set data_fim = ?, data_alteracao = now() where id = ? and id_jogador = ?")) {
				int i = 1;
				if (!naoComecou) {
					ps.setInt(i++, MicroCicloHelper.somaDias(hoje, -1));
				}
				ps.setInt(i++, parmId);
				ps.setInt(i, jid);
				ps.executeUpdate();
			}
			Efeitos efeitos = aplica(conn, tenant, jid, parmId, Math.max(hoje, antiga[0]), antiga[1]);
			boolean semTreinador = notifica(conn, parmIdentidade, jid, parmId, "INDISPONIBILIDADE_CANCELADA", antiga[0],
					antiga[1], efeitos);
			IndisponibilidadeData d = carrega(conn, tenant, parmId);
			d.setSemTreinador(semTreinador);
			d.setSimulacao(parmSimular);
			log.info("IndisponibilidadeHelper | cancela | jogador:{} indisponibilidade:{} simular:{} retiradas:{}", jid,
					parmId, parmSimular, efeitos.retiradas);
			return d;
		};
		return parmSimular ? microCicloHelper.emSimulacao("cancelarIndisponibilidade", trabalho)
				: microCicloHelper.emTransacao("cancelarIndisponibilidade", trabalho);
	}

	/** Q4/Q5 (CA2): tudo antes da transacao. O inicio no passado so se valida aqui na criacao. */
	static void validaDatas(int parmInicio, int parmFim, boolean parmCriar) {
		MicroCicloHelper.paraData(parmInicio);
		MicroCicloHelper.paraData(parmFim);
		if (parmFim < parmInicio) {
			throw new PedidoInvalidoException("A data de fim tem de ser igual ou posterior à data de início.");
		}
		int hoje = MicroCicloHelper.hoje();
		if (parmFim < hoje || (parmCriar && parmInicio < hoje)) {
			throw new PedidoInvalidoException("A data de início não pode ser no passado.");
		}
		long dias = ChronoUnit.DAYS.between(MicroCicloHelper.paraData(parmInicio), MicroCicloHelper.paraData(parmFim)) + 1;
		if (dias > MAXIMO_DIAS) {
			throw new PedidoInvalidoException("Uma indisponibilidade tem no máximo " + MAXIMO_DIAS + " dias.");
		}
	}

	/** Z12: serializa as acoes do mesmo atleta (e confirma o tenant). */
	private static void bloqueiaJogador(Connection conn, int parmTenantId, int parmIdJogador) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"select id from jogador where id = ? and tenant_id = ? and estado = '1' for update")) {
			ps.setInt(1, parmIdJogador);
			ps.setInt(2, parmTenantId);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new AcessoNegadoException();
				}
			}
		}
	}

	/** R37: so do proprio atleta (senao 403). @return {inicio, fim, ativa ? 1 : 0} */
	private static int[] carregaDoAtleta(Connection conn, int parmId, int parmIdJogador) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("select data_inicio, data_fim, estado from indisponibilidade "
				+ "where id = ? and id_jogador = ? for update")) {
			ps.setInt(1, parmId);
			ps.setInt(2, parmIdJogador);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new AcessoNegadoException();
				}
				return new int[] { rs.getInt(1), rs.getInt(2), ATIVA.equals(rs.getString(3)) ? 1 : 0 };
			}
		}
	}

	/** R4: sem sobreposicao com outra ATIVA do mesmo atleta (409). */
	private static void exigeSemSobreposicao(Connection conn, int parmIdJogador, int parmId, int parmInicio, int parmFim)
			throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("select data_inicio, data_fim from indisponibilidade "
				+ "where id_jogador = ? and estado = 'ATIVA' and id <> ? and data_inicio <= ? and data_fim >= ? "
				+ "order by data_inicio LIMIT 1")) {
			ps.setInt(1, parmIdJogador);
			ps.setInt(2, parmId);
			ps.setInt(3, parmFim);
			ps.setInt(4, parmInicio);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					throw new ConflitoException("Já tens uma indisponibilidade de " + MicroCicloHelper.ddmm(rs.getInt(1))
							+ " a " + MicroCicloHelper.ddmm(rs.getInt(2)) + ".");
				}
			}
		}
	}

	/** O que uma acao tocou: treinos e jogos sincronizados ({id, data, idEquipa}) e linhas retiradas. */
	private static final class Efeitos {
		final List<int[]> treinos = new ArrayList<>();
		final List<int[]> jogos = new ArrayList<>();
		int retiradas;
	}

	/** Sincroniza os treinos e os jogos do intervalo (e os que ainda tem efeitos desta indisponibilidade). */
	private Efeitos aplica(Connection conn, int parmTenantId, int parmIdJogador, int parmIdIndisp, int parmDe, int parmAte)
			throws SQLException {
		Efeitos efeitos = new Efeitos();
		try (PreparedStatement ps = conn.prepareStatement("select t.id, t.`data`, t.id_equipa from treino t "
				+ "inner join micro_ciclo m on m.id = t.id_micro_ciclo where m.tenant_id = ? and t.id_equipa is not null "
				+ "and t.id_treino_principal is null and ((t.`data` between ? and ? and t.estado <> 'CANCELADO' "
				+ "and (exists (select 1 from escalao_epoca_jogador eej where eej.id_escalao_epoca = t.id_equipa "
				+ "and eej.id_jogador = ?) or exists (select 1 from treino_convocado tc where tc.id_treino = t.id "
				+ "and tc.id_jogador = ? and tc.origem = 'ACRESCENTADO' and tc.estado = '1'))) "
				+ "or exists (select 1 from treino_ausencia ta where ta.id_treino = t.id and ta.id_indisponibilidade = ? "
				+ "and ta.estado = 'ATIVA')) order by t.`data`, t.hora_inicio, t.id")) {
			ps.setInt(1, parmTenantId);
			ps.setInt(2, parmDe);
			ps.setInt(3, parmAte);
			ps.setInt(4, parmIdJogador);
			ps.setInt(5, parmIdJogador);
			ps.setInt(6, parmIdIndisp);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					efeitos.treinos.add(new int[] { rs.getInt(1), rs.getInt(2), rs.getInt(3) });
				}
			}
		}
		for (int[] t : efeitos.treinos) {
			efeitos.retiradas += microCicloHelper.sincronizaTreino(conn, parmTenantId, t[0], parmIdJogador)[1];
		}
		try (PreparedStatement ps = conn.prepareStatement("select j.id, j.`data`, j.equipa_id from jogo j "
				+ "inner join escalao_epoca ee on ee.id = j.equipa_id inner join epoca e on e.id = ee.id_epoca "
				+ "where e.tenant_id = ? and ((j.`data` between ? and ? and ((e.estado = '1' and exists (select 1 "
				+ "from escalao_epoca_jogador eej where eej.id_escalao_epoca = j.equipa_id and eej.id_jogador = ?)) "
				+ "or exists (select 1 from jogo_jogador jj where jj.id_jogo = j.id and jj.id_jogador = ? "
				+ "and jj.estado = 'CONVOCADO'))) or exists (select 1 from indisponibilidade_jogo ij "
				+ "where ij.id_jogo = j.id and ij.id_indisponibilidade = ?)) order by j.`data`, j.hora, j.id")) {
			ps.setInt(1, parmTenantId);
			ps.setDate(2, java.sql.Date.valueOf(MicroCicloHelper.paraData(parmDe)));
			ps.setDate(3, java.sql.Date.valueOf(MicroCicloHelper.paraData(parmAte)));
			ps.setInt(4, parmIdJogador);
			ps.setInt(5, parmIdJogador);
			ps.setInt(6, parmIdIndisp);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					efeitos.jogos.add(new int[] { rs.getInt(1),
							rs.getDate(2) == null ? 0 : MicroCicloHelper.paraInt(rs.getDate(2).toLocalDate()), rs.getInt(3) });
				}
			}
		}
		for (int[] j : efeitos.jogos) {
			sincronizaJogo(conn, j[0], null);
		}
		return efeitos;
	}

	// ==================== jogos (Z5-Z8) ====================

	/**
	 * Z6: na transacao de quem grava o jogo. (1) Com o pedido (id_jogador -> estado), repoe as linhas automaticas que o
	 * pedido nao traz e liberta as que vem CONVOCADO. (2) Se o jogo nao estiver concluido nem comecado, aplica e retira
	 * as linhas automaticas. Nunca escreve em jogo, nem nas outras linhas de jogo_jogador.
	 */
	public void sincronizaJogo(Connection conn, int parmIdJogo, Map<Integer, String> parmPedido) throws SQLException {
		java.sql.Date dataJogo;
		String hora;
		String estado;
		int equipa;
		int tenant;
		try (PreparedStatement ps = conn.prepareStatement("select j.`data`, j.hora, j.estado, j.equipa_id, e.tenant_id "
				+ "from jogo j inner join escalao_epoca ee on ee.id = j.equipa_id inner join epoca e on e.id = ee.id_epoca "
				+ "where j.id = ?")) {
			ps.setInt(1, parmIdJogo);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return;
				}
				dataJogo = rs.getDate(1);
				hora = rs.getString(2);
				estado = rs.getString(3);
				equipa = rs.getInt(4);
				tenant = rs.getInt(5);
			}
		}
		if (dataJogo == null || !funcionalidadeHelper.isAtiva(tenant, FUNCIONALIDADE)) {
			return;
		}
		int data = MicroCicloHelper.paraInt(dataJogo.toLocalDate());

		// ligacoes deste jogo; as AUTOMATICA: id_indisponibilidade -> {id_jogador, estado da indisponibilidade, inicio, fim}
		Map<Integer, Object[]> automaticas = new LinkedHashMap<>();
		Map<Integer, String> ligacoes = new HashMap<>(); // id_indisponibilidade -> estado da ligacao
		try (PreparedStatement ps = conn.prepareStatement("select ij.id_indisponibilidade, ij.estado, i.id_jogador, i.estado, "
				+ "i.data_inicio, i.data_fim from indisponibilidade_jogo ij inner join indisponibilidade i "
				+ "on i.id = ij.id_indisponibilidade where ij.id_jogo = ?")) {
			ps.setInt(1, parmIdJogo);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					ligacoes.put(rs.getInt(1), rs.getString(2));
					if (AUTOMATICA.equals(rs.getString(2))) {
						automaticas.put(rs.getInt(1),
								new Object[] { rs.getInt(3), rs.getString(4), rs.getInt(5), rs.getInt(6) });
					}
				}
			}
		}

		// (1) juncao com o pedido (CA20): nunca apaga em silencio
		if (parmPedido != null) {
			for (Map.Entry<Integer, Object[]> a : automaticas.entrySet()) {
				int idJogador = (Integer) a.getValue()[0];
				String noPedido = parmPedido.get(idJogador);
				if (noPedido == null) {
					insereLinha(conn, parmIdJogo, idJogador);
				} else if (CONVOCADO.equalsIgnoreCase(noPedido.trim())) {
					ligacao(conn, a.getKey(), parmIdJogo, LIBERTADA);
					ligacoes.put(a.getKey(), LIBERTADA);
				}
			}
		}

		// (2) Z7: CONCLUIDO, ou ja passou a data e hora (sem hora: o fim do dia); INICIADO nao conta
		if ("CONCLUIDO".equalsIgnoreCase(estado) || jogoComecou(data, hora)) {
			return;
		}
		// retira: ligacao AUTOMATICA cuja indisponibilidade ja nao cobre a data ou ja nao esta ATIVA (R28)
		for (Map.Entry<Integer, Object[]> a : automaticas.entrySet()) {
			if (!LIBERTADA.equals(ligacoes.get(a.getKey())) && (!ATIVA.equals(a.getValue()[1])
					|| data < (Integer) a.getValue()[2] || data > (Integer) a.getValue()[3])) {
				try (PreparedStatement ps = conn.prepareStatement("delete from jogo_jogador where id_jogo = ? "
						+ "and id_jogador = ? and estado = ?")) {
					ps.setInt(1, parmIdJogo);
					ps.setInt(2, (Integer) a.getValue()[0]);
					ps.setString(3, INDISPONIVEL);
					ps.executeUpdate();
				}
				ligacao(conn, a.getKey(), parmIdJogo, RETIRADA);
				ligacoes.put(a.getKey(), RETIRADA);
			}
		}
		// aplica: jogadores ativos da equipa e linhas CONVOCADO, com uma indisponibilidade ATIVA que cobre a data
		List<int[]> candidatos = new ArrayList<>(); // {id_indisponibilidade, id_jogador}
		try (PreparedStatement ps = conn.prepareStatement("select i.id, i.id_jogador from indisponibilidade i "
				+ "where i.estado = 'ATIVA' and i.data_inicio <= ? and i.data_fim >= ? and i.id_jogador in ("
				+ "select eej.id_jogador from escalao_epoca_jogador eej inner join jogador jg on jg.id = eej.id_jogador "
				+ "and jg.estado = '1' where eej.id_escalao_epoca = ? "
				+ "union select jj.id_jogador from jogo_jogador jj where jj.id_jogo = ? and jj.estado = 'CONVOCADO')")) {
			ps.setInt(1, data);
			ps.setInt(2, data);
			ps.setInt(3, equipa);
			ps.setInt(4, parmIdJogo);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					candidatos.add(new int[] { rs.getInt(1), rs.getInt(2) });
				}
			}
		}
		for (int[] c : candidatos) {
			if (LIBERTADA.equals(ligacoes.get(c[0]))) {
				continue; // o treinador convocou-o
			}
			// Q9: com linha (CONVOCADO, ou INDISPONIVEL manual ou automatica) nao escreve nada; o alerta e derivado
			if (insereLinha(conn, parmIdJogo, c[1])) {
				ligacao(conn, c[0], parmIdJogo, AUTOMATICA);
			}
		}
	}

	/** Uma so linha INDISPONIVEL (as colunas de salvarConvocatoria), so se o jogador ainda nao estiver no jogo. */
	private static boolean insereLinha(Connection conn, int parmIdJogo, int parmIdJogador) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("insert into jogo_jogador (id_jogo, id_jogador, estado, obs, numero) "
				+ "select ?, jg.id, ?, ?, CASE WHEN jg.numero = '' OR jg.numero IS NULL THEN 0 ELSE CAST(jg.numero AS UNSIGNED) END "
				+ "from jogador jg where jg.id = ? and not exists (select 1 from jogo_jogador jj where jj.id_jogo = ? "
				+ "and jj.id_jogador = ?)")) {
			ps.setInt(1, parmIdJogo);
			ps.setString(2, INDISPONIVEL);
			ps.setString(3, obsJogo(null));
			ps.setInt(4, parmIdJogador);
			ps.setInt(5, parmIdJogo);
			ps.setInt(6, parmIdJogador);
			return ps.executeUpdate() > 0;
		}
	}

	private static void ligacao(Connection conn, int parmIdIndisp, int parmIdJogo, String parmEstado) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement("insert into indisponibilidade_jogo(id_indisponibilidade, id_jogo, "
				+ "estado, data_alteracao) values (?, ?, ?, now()) on duplicate key update estado = values(estado), "
				+ "data_alteracao = now()")) {
			ps.setInt(1, parmIdIndisp);
			ps.setInt(2, parmIdJogo);
			ps.setString(3, parmEstado);
			ps.executeUpdate();
		}
	}

	/** Z7: sem hora (ou hora invalida), o jogo conta como comecado so no fim do dia. */
	static boolean jogoComecou(int parmData, String parmHora) {
		LocalDateTime inicio;
		try {
			inicio = parmHora == null || parmHora.trim().isEmpty() ? null
					: LocalDateTime.of(MicroCicloHelper.paraData(parmData), LocalTime.parse(parmHora.trim()));
		} catch (DateTimeParseException e) {
			inicio = null;
		}
		if (inicio == null) {
			inicio = MicroCicloHelper.paraData(parmData).plusDays(1).atStartOfDay();
		}
		return !MicroCicloHelper.agora().isBefore(inicio);
	}

	/** Z10: so datas (sem categoria) e se a linha e automatica; a rota e so de staff. */
	public void anotaConvocatoria(Connection conn, int parmIdJogo, List<JogadorConvocado> parmJogadores)
			throws SQLException {
		if (parmJogadores == null || parmJogadores.isEmpty()) {
			return;
		}
		java.sql.Date dataJogo;
		try (PreparedStatement ps = conn.prepareStatement("select `data` from jogo where id = ?")) {
			ps.setInt(1, parmIdJogo);
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next() || rs.getDate(1) == null) {
					return;
				}
				dataJogo = rs.getDate(1);
			}
		}
		int data = MicroCicloHelper.paraInt(dataJogo.toLocalDate());
		Map<Integer, JogadorConvocado> porJogador = new HashMap<>();
		for (JogadorConvocado j : parmJogadores) {
			porJogador.put(j.getId_jogador(), j);
		}
		String marcas = String.join(", ", Collections.nCopies(porJogador.size(), "?"));
		try (PreparedStatement ps = conn.prepareStatement("select i.id_jogador, i.data_inicio, i.data_fim, ij.estado "
				+ "from indisponibilidade i left join indisponibilidade_jogo ij on ij.id_indisponibilidade = i.id "
				+ "and ij.id_jogo = ? where i.estado = 'ATIVA' and i.data_inicio <= ? and i.data_fim >= ? "
				+ "and i.id_jogador in (" + marcas + ")")) {
			int i = 1;
			ps.setInt(i++, parmIdJogo);
			ps.setInt(i++, data);
			ps.setInt(i++, data);
			for (Integer id : porJogador.keySet()) {
				ps.setInt(i++, id);
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					JogadorConvocado j = porJogador.get(rs.getInt(1));
					j.setIndisponivelDe(rs.getInt(2));
					j.setIndisponivelAte(rs.getInt(3));
					j.setAutomatica(AUTOMATICA.equals(rs.getString(4)));
				}
			}
		}
	}

	/** Z15: jogadores (dos pedidos) com uma indisponibilidade ATIVA nessa data, para o email da presenca manual. */
	static Set<Integer> comIndisponibilidade(Connection conn, Collection<Integer> parmIds, int parmData)
			throws SQLException {
		Set<Integer> ids = new HashSet<>();
		if (parmIds == null || parmIds.isEmpty()) {
			return ids;
		}
		String marcas = String.join(", ", Collections.nCopies(parmIds.size(), "?"));
		try (PreparedStatement ps = conn.prepareStatement("select distinct id_jogador from indisponibilidade "
				+ "where estado = 'ATIVA' and data_inicio <= ? and data_fim >= ? and id_jogador in (" + marcas + ")")) {
			int i = 1;
			ps.setInt(i++, parmData);
			ps.setInt(i++, parmData);
			for (Integer id : parmIds) {
				ps.setInt(i++, id);
			}
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getInt(1));
				}
			}
		}
		return ids;
	}

	// ==================== notificacoes (Z13, §6.3) ====================

	/**
	 * Uma notificacao por destinatario (treinadores e ADMIN das equipas, sem duplicados nem o autor), com as contagens
	 * das suas equipas e o primeiro treino afetado delas. Sem o motivo (D8). Q12: sem treinador, nao vai ao ADMIN.
	 *
	 * @return true se nao houve nenhum destinatario (R33)
	 */
	private boolean notifica(Connection conn, Identidade parmIdentidade, int parmIdJogador, int parmIdIndisp,
			String parmTipo, int parmInicio, int parmFim, Efeitos parmEfeitos) throws SQLException {
		int tenant = parmIdentidade.getTenantId();
		Set<Integer> equipas = new LinkedHashSet<>(MicroCicloHelper.equipasDoAtleta(conn, tenant, parmIdJogador));
		parmEfeitos.treinos.forEach(t -> equipas.add(t[2]));
		parmEfeitos.jogos.forEach(j -> equipas.add(j[2]));
		Map<Integer, Set<Integer>> porDestinatario = new LinkedHashMap<>();
		for (Integer equipa : equipas) {
			for (Integer u : notificacaoHelper.destinatariosEquipa(conn, equipa, tenant)) {
				if (u != parmIdentidade.getIdUtilizador()) {
					porDestinatario.computeIfAbsent(u, k -> new HashSet<>()).add(equipa);
				}
			}
		}
		if (porDestinatario.isEmpty()) {
			return true;
		}

		Set<Integer> treinosAtivos = new HashSet<>();
		try (PreparedStatement ps = conn.prepareStatement("select id_treino from treino_ausencia "
				+ "where id_indisponibilidade = ? and estado = 'ATIVA'")) {
			ps.setInt(1, parmIdIndisp);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					treinosAtivos.add(rs.getInt(1));
				}
			}
		}
		Set<Integer> jogosAutomaticos = new HashSet<>();
		try (PreparedStatement ps = conn.prepareStatement("select id_jogo from indisponibilidade_jogo "
				+ "where id_indisponibilidade = ? and estado = 'AUTOMATICA'")) {
			ps.setInt(1, parmIdIndisp);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					jogosAutomaticos.add(rs.getInt(1));
				}
			}
		}
		String nome = "";
		try (PreparedStatement ps = conn.prepareStatement("select nome from jogador where id = ?")) {
			ps.setInt(1, parmIdJogador);
			try (ResultSet rs = ps.executeQuery()) {
				nome = rs.next() ? rs.getString(1) : "";
			}
		}
		boolean cancelar = "INDISPONIBILIDADE_CANCELADA".equals(parmTipo);
		int hoje = MicroCicloHelper.hoje();
		List<int[]> convocado = cancelar ? new ArrayList<>()
				: convocadoEm(conn, tenant, parmIdJogador, Math.max(hoje, parmInicio), parmFim);
		// R28: linhas INDISPONIVEL postas a mao em jogos por jogar, que a indisponibilidade ja nao cobre
		List<int[]> aMao = new ArrayList<>();
		if (cancelar) {
			try (PreparedStatement ps = conn.prepareStatement("select 1 from jogo j inner join jogo_jogador jj "
					+ "on jj.id_jogo = j.id and jj.id_jogador = ? and jj.estado = ? where j.id = ? "
					+ "and coalesce(j.estado, '') <> 'CONCLUIDO' and not exists (select 1 from indisponibilidade_jogo ij "
					+ "where ij.id_jogo = j.id and ij.id_indisponibilidade = ? and ij.estado = 'AUTOMATICA')")) {
				for (int[] j : parmEfeitos.jogos) {
					if (j[1] < hoje) {
						continue;
					}
					ps.setInt(1, parmIdJogador);
					ps.setString(2, INDISPONIVEL);
					ps.setInt(3, j[0]);
					ps.setInt(4, parmIdIndisp);
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							aMao.add(j);
						}
					}
				}
			}
		}

		String intervalo = MicroCicloHelper.ddmm(parmInicio) + " a " + MicroCicloHelper.ddmm(parmFim);
		for (Map.Entry<Integer, Set<Integer>> d : porDestinatario.entrySet()) {
			Set<Integer> minhas = d.getValue();
			int n = 0;
			Integer primeiroTreino = null;
			Integer primeiroAfetado = null;
			for (int[] t : parmEfeitos.treinos) { // ja ordenados por data e hora
				if (!minhas.contains(t[2])) {
					continue;
				}
				if (primeiroAfetado == null) {
					primeiroAfetado = t[0];
				}
				if (treinosAtivos.contains(t[0])) {
					n++;
					if (primeiroTreino == null) {
						primeiroTreino = t[0];
					}
				}
			}
			int m = 0;
			for (int[] j : parmEfeitos.jogos) {
				if (minhas.contains(j[2]) && jogosAutomaticos.contains(j[0])) {
					m++;
				}
			}
			String contagens = ": " + n + (n == 1 ? " treino" : " treinos") + " e " + m + (m == 1 ? " jogo" : " jogos")
					+ " das tuas equipas";
			String texto;
			if (cancelar) {
				texto = nome + " cancelou a indisponibilidade de " + intervalo + sufixo(aMao, minhas,
						"; continua indisponível à mão no jogo de ");
			} else if ("INDISPONIBILIDADE_ALTERADA".equals(parmTipo)) {
				texto = nome + " alterou a indisponibilidade (agora de " + intervalo + ")" + contagens
						+ sufixo(convocado, minhas, "; já convocado para o jogo de ");
			} else {
				texto = nome + " indicou indisponibilidade de " + intervalo + contagens
						+ sufixo(convocado, minhas, "; já convocado para o jogo de ");
			}
			notificacaoHelper.cria(conn, List.of(d.getKey()), FUNCIONALIDADE, parmTipo,
					primeiroTreino != null ? primeiroTreino : cancelar ? primeiroAfetado : null, parmIdJogador, texto);
		}
		return false;
	}

	/** O primeiro jogo ({id, data, equipa}) das equipas do destinatario, com o fim "rever a convocatória". */
	private static String sufixo(List<int[]> parmJogos, Set<Integer> parmEquipas, String parmInicio) {
		for (int[] j : parmJogos) {
			if (parmEquipas.contains(j[2])) {
				return parmInicio + MicroCicloHelper.ddmm(j[1]) + ": rever a convocatória";
			}
		}
		return "";
	}
}
