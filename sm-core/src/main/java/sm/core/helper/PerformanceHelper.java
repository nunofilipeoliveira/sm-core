package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import sm.core.data.PerformanceConfigData;
import sm.core.data.PerformanceEquipaData;
import sm.core.data.PerformanceEvolucaoTreinoData;
import sm.core.data.PerformanceHistoricoData;
import sm.core.data.PerformanceResumoJogadorData;
import sm.core.data.PerformanceUltimaClassificacaoData;

/**
 * Helper com a lógica de análise de performance dos atletas nos treinos,
 * usada pelo PerformanceWS. Baseia-se nos registos de presenças
 * (presencas / presenca_jogador), no estado de cada atleta e na
 * classificação de desempenho (1 a 5) atribuída em cada treino.
 */
@Component
public class PerformanceHelper {

	private static final Logger log = LoggerFactory.getLogger(PerformanceHelper.class);

	// Estados registados em presenca_jogador ('Presente' por igualdade, restantes por prefixo).
	private static final String ESTADO_PRESENTE = "Presente";
	private static final String ESTADO_AUSENTE_AVISOU = "Ausente (Avisou%";
	private static final String ESTADO_AUSENTE_NAO_AVISOU = "Ausente (Não Avisou%";
	private static final String ESTADO_LESAO = "Lesão%";

	private final DBUtils dbUtils;

	public PerformanceHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;
	}

	/**
	 * Resumo de performance da equipa nos treinos da época ativa. Quando
	 * parmIdJogador é indicado, a lista de jogadores fica reduzida a esse atleta.
	 */
	public PerformanceEquipaData getResumoEquipa(int parmIdEquipa, Integer parmIdJogador, Integer parmDataInicio,
			Integer parmDataFim) {

		log.info("PerformanceHelper | getResumoEquipa | Start | idEquipa:{} idJogador:{}", parmIdEquipa,
				parmIdJogador);

		PerformanceEquipaData resumo = new PerformanceEquipaData();
		resumo.setId_equipa(parmIdEquipa);

		ArrayList<PerformanceResumoJogadorData> jogadores = new ArrayList<PerformanceResumoJogadorData>();
		double somaClassificacoes = 0;
		int totalAvaliados = 0;
		Connection conn = null;

		try {
			conn = dbUtils.getConnection();

			resumo.setNome_equipa(carregarNomeEquipa(conn, parmIdEquipa));
			resumo.setTotal_treinos(contarTreinosEquipa(conn, parmIdEquipa, parmDataInicio, parmDataFim));

			StringBuilder sql = new StringBuilder();
			sql.append("select eej.id_jogador, j.nome, count(p.id) as total_registos, ");
			sql.append("sum(case when p.id is not null and pj.estado = ? then 1 else 0 end) as total_presencas, ");
			sql.append("sum(case when p.id is not null and pj.estado like ? then 1 else 0 end) as total_ausencias_avisou, ");
			sql.append("sum(case when p.id is not null and pj.estado like ? then 1 else 0 end) as total_ausencias_nao_avisou, ");
			sql.append("sum(case when p.id is not null and pj.estado like ? then 1 else 0 end) as total_lesoes, ");
			sql.append("sum(case when p.id is not null and pj.classificacao is not null then 1 else 0 end) as treinos_avaliados, ");
			sql.append("sum(case when p.id is not null then pj.classificacao else 0 end) as soma_classificacoes ");
			sql.append("from escalao_epoca_jogador eej ");
			sql.append("inner join jogador j on j.id = eej.id_jogador ");
			sql.append("inner join escalao_epoca ee on ee.id = eej.id_escalao_epoca ");
			sql.append("inner join epoca e on e.id = ee.id_epoca ");
			sql.append("left join presenca_jogador pj on pj.id_jogador = eej.id_jogador ");
			sql.append("left join presencas p on p.id = pj.id_presenca and p.id_equipa = eej.id_escalao_epoca ");
			sql.append("where eej.id_escalao_epoca = ? and e.estado = 1 ");
			if (parmIdJogador != null) {
				sql.append("and eej.id_jogador = ? ");
			}
			if (parmDataInicio != null && parmDataFim != null) {
				sql.append("and (pj.id_presenca is null or p.`data` between ? and ?) ");
			}
			sql.append("group by eej.id_jogador, j.nome ");
			sql.append("order by j.nome");

			PreparedStatement preparedStatement = conn.prepareStatement(sql.toString());
			int index = 1;
			preparedStatement.setString(index++, ESTADO_PRESENTE);
			preparedStatement.setString(index++, ESTADO_AUSENTE_AVISOU);
			preparedStatement.setString(index++, ESTADO_AUSENTE_NAO_AVISOU);
			preparedStatement.setString(index++, ESTADO_LESAO);
			preparedStatement.setInt(index++, parmIdEquipa);
			if (parmIdJogador != null) {
				preparedStatement.setInt(index++, parmIdJogador);
			}
			if (parmDataInicio != null && parmDataFim != null) {
				preparedStatement.setInt(index++, parmDataInicio);
				preparedStatement.setInt(index++, parmDataFim);
			}

			ResultSet rs = preparedStatement.executeQuery();

			while (rs.next()) {
				PerformanceResumoJogadorData jogador = new PerformanceResumoJogadorData();
				jogador.setId_jogador(rs.getInt("id_jogador"));
				jogador.setNome_jogador(rs.getString("nome"));
				jogador.setTotal_treinos(rs.getInt("total_registos"));
				jogador.setTotal_presencas(rs.getInt("total_presencas"));
				jogador.setTotal_ausencias_avisou(rs.getInt("total_ausencias_avisou"));
				jogador.setTotal_ausencias_nao_avisou(rs.getInt("total_ausencias_nao_avisou"));
				jogador.setTotal_lesoes(rs.getInt("total_lesoes"));
				jogador.setTreinos_avaliados(rs.getInt("treinos_avaliados"));

				if (jogador.getTotal_treinos() > 0) {
					jogador.setPercentagem_presencas(
							arredondar(((double) jogador.getTotal_presencas() / jogador.getTotal_treinos()) * 100.0));
				}

				if (jogador.getTreinos_avaliados() > 0) {
					int somaJogador = rs.getInt("soma_classificacoes");
					somaClassificacoes += somaJogador;
					totalAvaliados += jogador.getTreinos_avaliados();
					jogador.setMedia_classificacao(arredondar(((double) somaJogador) / jogador.getTreinos_avaliados()));
				}

				resumo.setNum_jogadores(resumo.getNum_jogadores() + 1);
				resumo.setTotal_presencas(resumo.getTotal_presencas() + jogador.getTotal_presencas());
				resumo.setTotal_ausencias_avisou(
						resumo.getTotal_ausencias_avisou() + jogador.getTotal_ausencias_avisou());
				resumo.setTotal_ausencias_nao_avisou(
						resumo.getTotal_ausencias_nao_avisou() + jogador.getTotal_ausencias_nao_avisou());
				resumo.setTotal_lesoes(resumo.getTotal_lesoes() + jogador.getTotal_lesoes());

				jogadores.add(jogador);
			}

			resumo.setJogadores(jogadores);

			int totalRegistos = resumo.getTotal_presencas() + resumo.getTotal_ausencias_avisou()
					+ resumo.getTotal_ausencias_nao_avisou() + resumo.getTotal_lesoes();
			if (totalRegistos > 0) {
				resumo.setPercentagem_presencas(
						arredondar(((double) resumo.getTotal_presencas() / totalRegistos) * 100.0));
			}

			if (totalAvaliados > 0) {
				resumo.setMedia_classificacao(arredondar(somaClassificacoes / totalAvaliados));
			}

			log.info("PerformanceHelper | getResumoEquipa | End | jogadores:{} treinos:{}", resumo.getNum_jogadores(),
					resumo.getTotal_treinos());

			return resumo;
		} catch (SQLException e) {
			log.error("PerformanceHelper | getResumoEquipa | Erro", e);
			e.printStackTrace();
		} finally {
			dbUtils.closeConnection(conn);
		}

		return resumo;
	}

	/**
	 * Resumo de performance de um atleta em todos os treinos da época ativa
	 * (em todas as equipas em que tem registo de presença).
	 */
	public PerformanceResumoJogadorData getResumoJogador(int parmIdJogador, String parmNomeJogador,
			Integer parmDataInicio, Integer parmDataFim) {

		log.info("PerformanceHelper | getResumoJogador | Start | idJogador:{}", parmIdJogador);

		PerformanceResumoJogadorData resumo = new PerformanceResumoJogadorData();
		resumo.setId_jogador(parmIdJogador);
		resumo.setNome_jogador(parmNomeJogador != null ? parmNomeJogador : "");

		Connection conn = null;

		try {
			conn = dbUtils.getConnection();

			if (resumo.getNome_jogador().isEmpty()) {
				resumo.setNome_jogador(carregarNomeJogador(conn, parmIdJogador));
			}

			// 1. Contadores agregados do atleta.
			StringBuilder sql = new StringBuilder();
			sql.append("select count(p.id) as total_registos, ");
			sql.append("sum(case when pj.estado = ? then 1 else 0 end) as total_presencas, ");
			sql.append("sum(case when pj.estado like ? then 1 else 0 end) as total_ausencias_avisou, ");
			sql.append("sum(case when pj.estado like ? then 1 else 0 end) as total_ausencias_nao_avisou, ");
			sql.append("sum(case when pj.estado like ? then 1 else 0 end) as total_lesoes, ");
			sql.append("sum(case when pj.classificacao is not null then 1 else 0 end) as treinos_avaliados, ");
			sql.append("sum(pj.classificacao) as soma_classificacoes ");
			sql.append("from presenca_jogador pj ");
			sql.append("inner join presencas p on p.id = pj.id_presenca ");
			sql.append("inner join escalao_epoca ee on ee.id = p.id_equipa ");
			sql.append("inner join epoca e on e.id = ee.id_epoca ");
			sql.append("where pj.id_jogador = ? and e.estado = 1 ");
			if (parmDataInicio != null && parmDataFim != null) {
				sql.append("and p.`data` between ? and ? ");
			}

			PreparedStatement preparedStatement = conn.prepareStatement(sql.toString());
			int index = 1;
			preparedStatement.setString(index++, ESTADO_PRESENTE);
			preparedStatement.setString(index++, ESTADO_AUSENTE_AVISOU);
			preparedStatement.setString(index++, ESTADO_AUSENTE_NAO_AVISOU);
			preparedStatement.setString(index++, ESTADO_LESAO);
			preparedStatement.setInt(index++, parmIdJogador);
			if (parmDataInicio != null && parmDataFim != null) {
				preparedStatement.setInt(index++, parmDataInicio);
				preparedStatement.setInt(index++, parmDataFim);
			}

			ResultSet rs = preparedStatement.executeQuery();

			if (rs.next()) {
				resumo.setTotal_treinos(rs.getInt("total_registos"));
				resumo.setTotal_presencas(rs.getInt("total_presencas"));
				resumo.setTotal_ausencias_avisou(rs.getInt("total_ausencias_avisou"));
				resumo.setTotal_ausencias_nao_avisou(rs.getInt("total_ausencias_nao_avisou"));
				resumo.setTotal_lesoes(rs.getInt("total_lesoes"));
				resumo.setTreinos_avaliados(rs.getInt("treinos_avaliados"));

				if (resumo.getTotal_treinos() > 0) {
					resumo.setPercentagem_presencas(
							arredondar(((double) resumo.getTotal_presencas() / resumo.getTotal_treinos()) * 100.0));
				}

				if (resumo.getTreinos_avaliados() > 0) {
					resumo.setMedia_classificacao(
							arredondar(((double) rs.getInt("soma_classificacoes") / resumo.getTreinos_avaliados())));
				}
			}

			// 2. Evolução (treinos avaliados) e histórico completo, por ordem de data.
			StringBuilder sqlHistorico = new StringBuilder();
			sqlHistorico.append("select p.id, p.`data`, p.hora, ee.nome as nome_equipa, pj.estado, pj.motivo, pj.classificacao ");
			sqlHistorico.append("from presenca_jogador pj ");
			sqlHistorico.append("inner join presencas p on p.id = pj.id_presenca ");
			sqlHistorico.append("inner join escalao_epoca ee on ee.id = p.id_equipa ");
			sqlHistorico.append("inner join epoca e on e.id = ee.id_epoca ");
			sqlHistorico.append("where pj.id_jogador = ? and e.estado = 1 ");
			if (parmDataInicio != null && parmDataFim != null) {
				sqlHistorico.append("and p.`data` between ? and ? ");
			}
			sqlHistorico.append("order by p.`data`, p.hora");

			PreparedStatement psHistorico = conn.prepareStatement(sqlHistorico.toString());
			psHistorico.setInt(1, parmIdJogador);
			if (parmDataInicio != null && parmDataFim != null) {
				psHistorico.setInt(2, parmDataInicio);
				psHistorico.setInt(3, parmDataFim);
			}

			ResultSet rsHistorico = psHistorico.executeQuery();

			while (rsHistorico.next()) {
				int data = rsHistorico.getInt("data");
				String hora = rsHistorico.getString("hora");
				String nomeEquipa = rsHistorico.getString("nome_equipa");
				String estado = rsHistorico.getString("estado");
				String motivo = rsHistorico.getString("motivo");
				int idPresenca = rsHistorico.getInt("id");

				Integer classificacao = null;
				int classificacaoRegisto = rsHistorico.getInt("classificacao");
				if (!rsHistorico.wasNull()) {
					classificacao = classificacaoRegisto;
				}

				resumo.getHistorico()
						.add(new PerformanceHistoricoData(data, hora, nomeEquipa, estado, motivo, classificacao));

				if (classificacao != null) {
					resumo.getEvolucao().add(new PerformanceEvolucaoTreinoData(idPresenca, data, hora, classificacao));
				}
			}

			// 3. Campos de resumo utilizados pela ficha do jogador (média global,
			// semanal, mensal, tendência e últimas classificações), calculados a
			// partir da evolução de treinos avaliados.
			ArrayList<PerformanceEvolucaoTreinoData> evolucao = resumo.getEvolucao();

			resumo.setTotal_classificacoes(resumo.getTreinos_avaliados());
			resumo.setMedia_global(resumo.getMedia_classificacao());

			if (!evolucao.isEmpty()) {
				LocalDate hoje = LocalDate.now();
				int dataHoje = Integer.parseInt(hoje.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
				int dataSemana = Integer.parseInt(hoje.minusDays(7).format(DateTimeFormatter.ofPattern("yyyyMMdd")));
				int dataMes = Integer.parseInt(hoje.minusDays(30).format(DateTimeFormatter.ofPattern("yyyyMMdd")));

				double somaSemana = 0;
				int totalSemana = 0;
				double somaMes = 0;
				int totalMes = 0;

				for (PerformanceEvolucaoTreinoData ponto : evolucao) {
					if (ponto.getData() >= dataSemana && ponto.getData() <= dataHoje) {
						somaSemana += ponto.getClassificacao();
						totalSemana++;
					}
					if (ponto.getData() >= dataMes && ponto.getData() <= dataHoje) {
						somaMes += ponto.getClassificacao();
						totalMes++;
					}
				}

				if (totalSemana > 0) {
					resumo.setMedia_semanal(arredondar(somaSemana / totalSemana));
				}
				if (totalMes > 0) {
					resumo.setMedia_mensal(arredondar(somaMes / totalMes));
				}

				// Tendência: compara a média da segunda metade com a primeira metade
				// da evolução (ordenada por data).
				if (evolucao.size() >= 2) {
					int metade = evolucao.size() / 2;
					double somaPrimeira = 0;
					for (int i = 0; i < metade; i++) {
						somaPrimeira += evolucao.get(i).getClassificacao();
					}
					double mediaPrimeira = somaPrimeira / metade;

					double somaSegunda = 0;
					int totalSegunda = evolucao.size() - metade;
					for (int i = metade; i < evolucao.size(); i++) {
						somaSegunda += evolucao.get(i).getClassificacao();
					}
					double mediaSegunda = somaSegunda / totalSegunda;

					double diferenca = mediaSegunda - mediaPrimeira;
					if (diferenca > 0.15) {
						resumo.setTendencia("SUBIDA");
					} else if (diferenca < -0.15) {
						resumo.setTendencia("DESCIDA");
					} else {
						resumo.setTendencia("ESTAVEL");
					}
				}

				// Últimas classificações: mais recentes primeiro, limitado a 10.
				ArrayList<PerformanceUltimaClassificacaoData> ultimas = new ArrayList<PerformanceUltimaClassificacaoData>();
				for (PerformanceEvolucaoTreinoData ponto : evolucao) {
					ultimas.add(new PerformanceUltimaClassificacaoData(ponto.getData(), ponto.getClassificacao()));
				}
				Collections.reverse(ultimas);
				if (ultimas.size() > 10) {
					ultimas = new ArrayList<PerformanceUltimaClassificacaoData>(ultimas.subList(0, 10));
				}
				resumo.setUltimasClassificacoes(ultimas);
			}

			log.info("PerformanceHelper | getResumoJogador | End | treinos:{} avaliados:{}", resumo.getTotal_treinos(),
					resumo.getTreinos_avaliados());

		} catch (SQLException e) {
			log.error("PerformanceHelper | getResumoJogador | Erro", e);
			e.printStackTrace();
		} finally {
			dbUtils.closeConnection(conn);
		}

		return resumo;
	}

	// ==================== CONFIGURAÇÃO DE PERFORMANCE POR EQUIPA ====================

	/**
	 * Configuração de performance da equipa. Se a equipa ainda não tiver
	 * registo na tabela performance_config, devolve a configuração por
	 * omissão (registo e visualização ativos).
	 */
	public PerformanceConfigData getPerformanceConfig(int parmIdEquipa) {

		PerformanceConfigData config = new PerformanceConfigData();
		config.setId_equipa(parmIdEquipa);

		Connection conn = null;

		try {
			conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select permitir_registo, permitir_visualizacao from performance_config where id_equipa = ?");
			preparedStatement.setInt(1, parmIdEquipa);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs.next()) {
				config.setPermitir_registo(rs.getBoolean("permitir_registo"));
				config.setPermitir_visualizacao(rs.getBoolean("permitir_visualizacao"));
			}

		} catch (SQLException e) {
			log.error("PerformanceHelper | getPerformanceConfig | Erro", e);
			e.printStackTrace();
		} finally {
			dbUtils.closeConnection(conn);
		}

		return config;
	}

	/**
	 * Grava (insere ou atualiza) a configuração de performance da equipa.
	 */
	public boolean gravarPerformanceConfig(PerformanceConfigData parmConfig, int parmIdUtilizador) {

		Connection conn = null;

		try {
			conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement(
					"insert into performance_config(id_equipa, permitir_registo, permitir_visualizacao) values (?, ?, ?) "
							+ "on duplicate key update permitir_registo = values(permitir_registo), "
							+ "permitir_visualizacao = values(permitir_visualizacao)");
			preparedStatement.setInt(1, parmConfig.getId_equipa());
			preparedStatement.setInt(2, parmConfig.isPermitir_registo() ? 1 : 0);
			preparedStatement.setInt(3, parmConfig.isPermitir_visualizacao() ? 1 : 0);
			preparedStatement.executeUpdate();

			log.info("PerformanceHelper | gravarPerformanceConfig | Equipa:{} registo:{} visualizacao:{} utilizador:{}",
					parmConfig.getId_equipa(), parmConfig.isPermitir_registo(), parmConfig.isPermitir_visualizacao(),
					parmIdUtilizador);

			return true;

		} catch (SQLException e) {
			log.error("PerformanceHelper | gravarPerformanceConfig | Erro", e);
			e.printStackTrace();
		} finally {
			dbUtils.closeConnection(conn);
		}

		return false;
	}

	/**
	 * Indica se a equipa permite o registo de classificações de desempenho
	 * nos treinos. Usado pelo PresencaHelper ao gravar presenças.
	 */
	public boolean permiteRegistoClassificacao(int parmIdEquipa) {
		return getPerformanceConfig(parmIdEquipa).isPermitir_registo();
	}

	/**
	 * Nome da equipa (escalao_epoca) associado ao id indicado. Vazio se não existir.
	 */
	private String carregarNomeEquipa(Connection conn, int parmIdEquipa) throws SQLException {
		PreparedStatement preparedStatement = conn
				.prepareStatement("select ee.nome from escalao_epoca ee where ee.id = ?");
		preparedStatement.setInt(1, parmIdEquipa);
		ResultSet rs = preparedStatement.executeQuery();
		if (rs.next() && rs.getString("nome") != null) {
			return rs.getString("nome");
		}
		return "";
	}

	/**
	 * Nome do jogador associado ao id indicado. Vazio se não existir.
	 */
	private String carregarNomeJogador(Connection conn, int parmIdJogador) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select nome from jogador where id = ?");
		preparedStatement.setInt(1, parmIdJogador);
		ResultSet rs = preparedStatement.executeQuery();
		if (rs.next() && rs.getString("nome") != null) {
			return rs.getString("nome");
		}
		return "";
	}

	/**
	 * Número de treinos registados para a equipa na época ativa e, quando
	 * indicado, dentro do intervalo de datas.
	 */
	private int contarTreinosEquipa(Connection conn, int parmIdEquipa, Integer parmDataInicio, Integer parmDataFim)
			throws SQLException {

		StringBuilder sql = new StringBuilder();
		sql.append("select count(*) from presencas p ");
		sql.append("inner join escalao_epoca ee on ee.id = p.id_equipa ");
		sql.append("inner join epoca e on e.id = ee.id_epoca ");
		sql.append("where p.id_equipa = ? and e.estado = 1 ");
		if (parmDataInicio != null && parmDataFim != null) {
			sql.append("and p.`data` between ? and ? ");
		}

		PreparedStatement preparedStatement = conn.prepareStatement(sql.toString());
		preparedStatement.setInt(1, parmIdEquipa);
		if (parmDataInicio != null && parmDataFim != null) {
			preparedStatement.setInt(2, parmDataInicio);
			preparedStatement.setInt(3, parmDataFim);
		}

		ResultSet rs = preparedStatement.executeQuery();
		if (rs.next()) {
			return rs.getInt(1);
		}
		return 0;
	}

	/**
	 * Arredonda um valor a duas casas decimais (padrão usado no PresencaWS).
	 */
	private double arredondar(double valor) {
		return Math.round(valor * 100.0) / 100.0;
	}

}
