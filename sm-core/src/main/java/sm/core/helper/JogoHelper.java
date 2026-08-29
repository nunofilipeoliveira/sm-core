package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.springframework.stereotype.Component;

import sm.core.data.CompeticaoData;
import sm.core.data.ConvocatoriaData;
import sm.core.data.JogadorConvocado;
import sm.core.data.JogadorJogo;
import sm.core.data.JogoConfigData;
import sm.core.data.JogoData;

@Component
public class JogoHelper {

	private final DBUtils dbUtils;

	public JogoHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;
	}

public ArrayList<JogoData> getAllJogosByEquipa(int parmEquipaID) {

		
		ArrayList<JogoData> jogos = new ArrayList<>();

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select jogo.id,epoca_id,equipa_id,tipoEquipa,Data,Hora,local,golos_equipa,equipa_adv_id, clube.nome , tipoEquipa_adv,golos_equipa_adv,tipo_local,competicao_id, competicao_descritivo, arbitro_1,arbitro_2,estado, hora_concentracao, jogo.obs, jogo.numeroJogo \r\n" + //
												"From jogo\r\n" + //
												"inner join clube on clube.id=equipa_adv_id where EQUIPA_ID=? order by data, hora");

			preparedStatement.setInt(1, parmEquipaID);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				JogoData jogo = new JogoData(rs.getInt("id"), rs.getInt("epoca_id"), rs.getInt("equipa_id"),
						rs.getString("tipoEquipa"), rs.getString("data"), rs.getString("hora"), rs.getString("local"),
						rs.getInt("golos_equipa"), rs.getInt("equipa_adv_id"), rs.getString("tipoEquipa_adv"),
						rs.getString("clube.nome"), rs.getInt("golos_equipa_adv"), rs.getString("tipo_local"), rs.getInt("competicao_id"),
						rs.getString("competicao_descritivo"), rs.getString("arbitro_1"), rs.getString("arbitro_2"), rs.getString("estado"),
						rs.getString("hora_concentracao"), rs.getString("jogo.obs"), rs.getString("numeroJogo"));

				jogos.add(jogo);
			}

			return jogos;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}


	public ArrayList<CompeticaoData> getAllCompeticoes() {

		
		ArrayList<CompeticaoData> competicoes = new ArrayList<>();

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select id,nome from competicao");

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				CompeticaoData competicao = new CompeticaoData(rs.getInt("id"), rs.getString("nome"));

				competicoes.add(competicao);
			}

			return competicoes;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}	

	public boolean createJogo(JogoData jogo) {

		
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("INSERT INTO jogo (epoca_id, equipa_id, tipoequipa, data, hora, local, golos_equipa, equipa_adv_id, tipoequipa_adv, golos_equipa_adv, tipo_local, competicao_id, competicao_descritivo, arbitro_1, arbitro_2, estado, hora_concentracao, obs, numerojogo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

			preparedStatement.setInt(1, jogo.getEpoca_id());
			preparedStatement.setInt(2, jogo.getEquipa_id());
			preparedStatement.setString(3, jogo.getTipoEquipa());
			preparedStatement.setString(4, jogo.getData());
			preparedStatement.setString(5, jogo.getHora());
			preparedStatement.setString(6, jogo.getLocal());
			preparedStatement.setInt(7, jogo.getGolos_equipa());
			preparedStatement.setInt(8, jogo.getEquipa_adv_id());
			preparedStatement.setString(9, jogo.getTipoEquipa_adv());
			preparedStatement.setInt(10, jogo.getGolos_equipa_adv());
			preparedStatement.setString(11, jogo.getTipo_local().substring(0, 1));
			preparedStatement.setInt(12, jogo.getCompeticao_id());
			preparedStatement.setString(13, jogo.getCompeticao_nome());
			preparedStatement.setString(14, jogo.getArbitro_1());
			preparedStatement.setString(15, jogo.getArbitro_2());
			preparedStatement.setString(16, jogo.getEstado());
			preparedStatement.setString(17, calculaHoraConcentracao(jogo.getHora()));
			preparedStatement.setString(18, jogo.getObs());
			preparedStatement.setString(19, jogo.getNumeroJogo());


			//obter ID quando realiza o insert
			int rowsAffected = preparedStatement.executeUpdate();

			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public static String calculaHoraConcentracao(String horaJogo) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
    LocalTime hora = LocalTime.parse(horaJogo, formatter);
    return hora.minusHours(1).format(formatter);
}

	public boolean updateJogo(JogoData jogo) {

		
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("UPDATE jogo SET epoca_id = ?, equipa_id = ?, tipoequipa = ?, data = ?, hora = ?, local = ?, golos_equipa = ?, equipa_adv_id = ?, tipoequipa_adv = ?, golos_equipa_adv = ?, tipo_local = ?, competicao_id = ?, competicao_descritivo=?, arbitro_1 = ?, arbitro_2 = ?, estado = ?, hora_concentracao = ?, obs = ?, numerojogo=? WHERE id = ?");

			preparedStatement.setInt(1, jogo.getEpoca_id());
			preparedStatement.setInt(2, jogo.getEquipa_id());
			preparedStatement.setString(3, jogo.getTipoEquipa());
			preparedStatement.setString(4, jogo.getData());
			preparedStatement.setString(5, jogo.getHora());
			preparedStatement.setString(6, jogo.getLocal());
			preparedStatement.setInt(7, jogo.getGolos_equipa());
			preparedStatement.setInt(8, jogo.getEquipa_adv_id());
			preparedStatement.setString(9, jogo.getTipoEquipa_adv());
			preparedStatement.setInt(10, jogo.getGolos_equipa_adv());
			preparedStatement.setString(11, jogo.getTipo_local().substring(0, 1));
			preparedStatement.setInt(12, jogo.getCompeticao_id());
			preparedStatement.setString(13, jogo.getCompeticao_nome());
			preparedStatement.setString(14, jogo.getArbitro_1());
			preparedStatement.setString(15, jogo.getArbitro_2());
			preparedStatement.setString(16, jogo.getEstado());
			preparedStatement.setString(17, jogo.getHora_concentracao());
			preparedStatement.setString(18, jogo.getObs());	
			preparedStatement.setString(19, jogo.getNumeroJogo());	
			preparedStatement.setInt(20, jogo.getId());

			int rowsAffected = preparedStatement.executeUpdate();

			if(jogo.getJogadores()!=null && jogo.getJogadores().size()>0) {
				// Jogos registados em modo CRONÓMETRO têm as estatísticas, o "5 inicial",
				// quem está em campo e o tempo de jogo mantidos ao vivo, evento a evento,
				// diretamente na base de dados (ver JogoCronometroHelper). O array de
				// jogadores que chega aqui, nesse modo, não é sincronizado em tempo real
				// com esses eventos e pode estar desatualizado (tipicamente com os valores
				// que tinha quando a página foi carregada) — por isso NÃO se pode apagar e
				// recriar as linhas de jogo_jogador como se faz no modo normal, sob pena de
				// apagar tudo o que já estava corretamente registado durante o jogo.
				if (isModoCronometro(conn, jogo.getId())) {
					atualizarFichaJogadoresPreservandoCronometro(conn, jogo);
				} else {
					//Atualizar convocatória se existirem jogadores associados
					preparedStatement = conn
							.prepareStatement("DELETE FROM jogo_jogador WHERE id_jogo = ?");
					preparedStatement.setInt(1, jogo.getId());
					preparedStatement.executeUpdate();	
					preparedStatement.close();
					PreparedStatement insertStatement = conn
							.prepareStatement("INSERT INTO jogo_jogador (id_jogo, id_jogador, capitao, numero, amarelo, azul, vermelho, golo_p, golo_ld, golo_pp, golo_up, golo_normal, golo_s_p, golo_s_ld, golo_s_up, golo_s_pp, golo_s_normal, estado, obs, faltas, assistencias, recuperacoes_bola, perdas_bola, remates, penalty_falhado, penalty_defesa, ld_falhado, ld_defesa, gr, titular, em_campo, tempo_jogo_segundos, excluido_ate_segundos) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
					for(JogadorJogo jogador : jogo.getJogadores()) {
						insertStatement.setInt(1, jogo.getId());
						insertStatement.setInt(2, jogador.getId_jogador());
						insertStatement.setBoolean(3, jogador.getCapitao());
						insertStatement.setInt(4, jogador.getNumero());
						insertStatement.setInt(5, jogador.getAmarelo());
						insertStatement.setInt(6, jogador.getAzul());
						insertStatement.setInt(7, jogador.getVermelho());
						insertStatement.setInt(8, jogador.getGolos_p());
						insertStatement.setInt(9, jogador.getGolos_ld());
						insertStatement.setInt(10, jogador.getGolos_pp());
						insertStatement.setInt(11, jogador.getGolos_up());
						insertStatement.setInt(12, jogador.getGolos_normal());
						insertStatement.setInt(13, jogador.getGolos_s_p());
						insertStatement.setInt(14, jogador.getGolos_s_ld());
						insertStatement.setInt(15, jogador.getGolos_s_up());
						insertStatement.setInt(16, jogador.getGolos_s_pp());
						insertStatement.setInt(17, jogador.getGolos_s_normal());
						insertStatement.setString(18, jogador.getEstado());
						insertStatement.setString(19, jogador.getObs());
						insertStatement.setInt(20, jogador.getFaltas());
						insertStatement.setInt(21, jogador.getAssistencias());
						insertStatement.setInt(22, jogador.getRecuperacoes_bola());
						insertStatement.setInt(23, jogador.getPerdas_bola());
						insertStatement.setInt(24, jogador.getRemates());
						insertStatement.setInt(25, jogador.getPenalty_falhado());
						insertStatement.setInt(26, jogador.getPenalty_defesa());
						insertStatement.setInt(27, jogador.getLd_falhado());
						insertStatement.setInt(28, jogador.getLd_defesa());
						insertStatement.setBoolean(29, jogador.isGr());
						insertStatement.setBoolean(30, jogador.isTitular());
						insertStatement.setBoolean(31, jogador.getEmCampo());
						insertStatement.setInt(32, jogador.getTempoJogoSegundos());
						if (jogador.getExcluidoAteSegundos() != null) {
							insertStatement.setInt(33, jogador.getExcluidoAteSegundos());
						} else {
							insertStatement.setNull(33, java.sql.Types.INTEGER);
						}
						insertStatement.executeUpdate();
					}
					insertStatement.close();
				}
			}

			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * Indica se o jogo está registado em modo CRONÓMETRO (ver jogo_config). Nesse
	 * modo, os dados por jogador em jogo_jogador (estatísticas, 5 inicial, em
	 * campo, tempo de jogo) são mantidos ao vivo pelo JogoCronometroHelper e não
	 * podem ser substituídos por um "guardar registo" genérico.
	 */
	private boolean isModoCronometro(Connection conn, int idJogo) throws SQLException {
		PreparedStatement ps = conn.prepareStatement("SELECT modo_registo FROM jogo_config WHERE id_jogo = ?");
		ps.setInt(1, idJogo);
		ResultSet rs = ps.executeQuery();
		boolean cronometro = false;
		if (rs.next()) {
			cronometro = "CRONOMETRO".equalsIgnoreCase(rs.getString("modo_registo"));
		}
		rs.close();
		ps.close();
		return cronometro;
	}

	/**
	 * Atualiza só os campos "de ficha" de cada jogador (capitão, número, estado da
	 * convocatória, observações) sem tocar em estatísticas, 5 inicial, em campo,
	 * tempo de jogo ou exclusão — esses ficam exatamente como o cronómetro os
	 * deixou. Se um jogador ainda não tiver linha em jogo_jogador (ex: adicionado
	 * agora à convocatória), cria uma nova, com as estatísticas a começar do zero.
	 */
	private void atualizarFichaJogadoresPreservandoCronometro(Connection conn, JogoData jogo) throws SQLException {
		PreparedStatement update = conn.prepareStatement(
				"UPDATE jogo_jogador SET capitao = ?, numero = ?, estado = ?, obs = ? WHERE id_jogo = ? AND id_jogador = ?");
		PreparedStatement insert = conn.prepareStatement(
				"INSERT INTO jogo_jogador (id_jogo, id_jogador, capitao, numero, estado, obs) VALUES (?, ?, ?, ?, ?, ?)");
		for (JogadorJogo jogador : jogo.getJogadores()) {
			update.setBoolean(1, jogador.getCapitao());
			update.setInt(2, jogador.getNumero());
			update.setString(3, jogador.getEstado());
			update.setString(4, jogador.getObs());
			update.setInt(5, jogo.getId());
			update.setInt(6, jogador.getId_jogador());
			int linhas = update.executeUpdate();
			if (linhas == 0) {
				insert.setInt(1, jogo.getId());
				insert.setInt(2, jogador.getId_jogador());
				insert.setBoolean(3, jogador.getCapitao());
				insert.setInt(4, jogador.getNumero());
				insert.setString(5, jogador.getEstado());
				insert.setString(6, jogador.getObs());
				insert.executeUpdate();
			}
		}
		update.close();
		insert.close();
	}

	public boolean deleteJogo(int id) {

		
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("DELETE FROM jogo WHERE id = ?");

			preparedStatement.setInt(1, id);

			int rowsAffected = preparedStatement.executeUpdate();
			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public JogoData getJogoById(int id) {

		
		JogoData jogo = null;

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select jogo.id,epoca_id,equipa_id,tipoequipa,data,hora,local,golos_equipa,equipa_adv_id, clube.nome , tipoequipa_adv,golos_equipa_adv,tipo_local,competicao_id, competicao_descritivo, arbitro_1,arbitro_2,estado, hora_concentracao, obs, numerojogo \r\n" + //
												"From jogo\r\n" + //
												"inner join clube on clube.id=equipa_adv_id where jogo.id=? ");

			preparedStatement.setInt(1, id);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			if (rs.next()) {

				jogo = new JogoData(rs.getInt("id"), rs.getInt("epoca_id"), rs.getInt("equipa_id"),
						rs.getString("tipoequipa"), rs.getString("data"), rs.getString("hora"), rs.getString("local"),
						rs.getInt("golos_equipa"), rs.getInt("equipa_adv_id"), rs.getString("tipoequipa_adv"),
						rs.getString("clube.nome"), rs.getInt("golos_equipa_adv"), rs.getString("tipo_local"), rs.getInt("competicao_id"),
						rs.getString("competicao_descritivo"), rs.getString("arbitro_1"), rs.getString("arbitro_2"), rs.getString("estado"), rs.getString("hora_concentracao"), rs.getString("obs"), rs.getString("numeroJogo") );

			
				
			}

			
			
			PreparedStatement psJogadores =conn.prepareStatement("SELECT *" +
									  "FROM jogo_jogador jj " +
									  "INNER JOIN jogador j ON jj.id_jogador = j.id " +
									  "WHERE jj.id_jogo = ?");
			psJogadores.setInt(1, id);
			ResultSet rsJogadores = psJogadores.executeQuery();	
			ArrayList<JogadorJogo> listaJogadores = new ArrayList<>();
			while (rsJogadores.next()) {
				JogadorJogo jogador = new JogadorJogo(
					rsJogadores.getInt("id_jogador"),
					rsJogadores.getString("nome"),
					rsJogadores.getBoolean("capitao"),
					rsJogadores.getInt("numero"),
					rsJogadores.getInt("amarelo"),
					rsJogadores.getInt("azul"),
					rsJogadores.getInt("vermelho"),
					rsJogadores.getInt("golo_p"),
					rsJogadores.getInt("golo_ld"),
					rsJogadores.getInt("golo_pp"),
					rsJogadores.getInt("golo_up"),
					rsJogadores.getInt("golo_normal"),
					rsJogadores.getInt("golo_s_p"),
					rsJogadores.getInt("golo_s_ld"),
					rsJogadores.getInt("golo_s_up"),
					rsJogadores.getInt("golo_s_pp"),
					rsJogadores.getInt("golo_s_normal"),
					rsJogadores.getString("estado"),
					rsJogadores.getString("obs"),
					rsJogadores.getInt("faltas"),
					rsJogadores.getInt("assistencias"),
					rsJogadores.getInt("recuperacoes_bola"),
					rsJogadores.getInt("perdas_bola"),
					rsJogadores.getInt("remates"),
					rsJogadores.getInt("penalty_falhado"),
					rsJogadores.getInt("penalty_defesa"),
					rsJogadores.getInt("ld_falhado"),
					rsJogadores.getInt("ld_defesa"),
					rsJogadores.getString("Licença"),
					rsJogadores.getBoolean("gr")	
				);
				jogador.setTitular(rsJogadores.getBoolean("titular"));
				jogador.setEmCampo(rsJogadores.getBoolean("em_campo"));
				jogador.setTempoJogoSegundos(rsJogadores.getInt("tempo_jogo_segundos"));
				Object excluidoAte = rsJogadores.getObject("excluido_ate_segundos");
				jogador.setExcluidoAteSegundos(excluidoAte != null ? rsJogadores.getInt("excluido_ate_segundos") : null);
				listaJogadores.add(jogador);
			}
			
			jogo.setJogadores(listaJogadores);

			// carregar a configuração do jogo (modo normal / cronómetro)
			PreparedStatement psConfig = conn.prepareStatement("SELECT id, id_jogo, modo_registo, duracao_parte_minutos, numero_partes, num_jogadores_iniciais, duracao_exclusao_azul_segundos, tempo_atual_segundos FROM jogo_config WHERE id_jogo = ?");
			psConfig.setInt(1, id);
			ResultSet rsConfig = psConfig.executeQuery();
			if (rsConfig.next()) {
				JogoConfigData config = new JogoConfigData(rsConfig.getInt("id"), rsConfig.getInt("id_jogo"), rsConfig.getString("modo_registo"),
						rsConfig.getInt("duracao_parte_minutos"), rsConfig.getInt("numero_partes"), rsConfig.getInt("num_jogadores_iniciais"),
						rsConfig.getInt("duracao_exclusao_azul_segundos"), rsConfig.getInt("tempo_atual_segundos"));
				jogo.setConfig(config);
			}
			rsConfig.close();
			psConfig.close();


			return jogo;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;


	}

	public boolean salvarConvocatoria(ConvocatoriaData convocatoriaData) {

    Connection conn = null;
    PreparedStatement deleteStatement = null;
    PreparedStatement selectStatement = null;
    PreparedStatement insertStatement = null;

    try {
        conn = dbUtils.getConnection();
        conn.setAutoCommit(false);

        // Primeiro, eliminar convocatória existente para o jogo, se houver
        deleteStatement = conn.prepareStatement("DELETE FROM jogo_jogador WHERE id_jogo = ?");
        deleteStatement.setInt(1, convocatoriaData.getId());
        deleteStatement.executeUpdate();

        // Buscar o número de cada jogador e inserir na convocatória
        selectStatement = conn.prepareStatement(
            "SELECT CASE WHEN numero = '' OR numero IS NULL THEN 0 ELSE CAST(numero AS UNSIGNED) END AS numero " +
            "FROM jogador WHERE id = ?"
        );

        insertStatement = conn.prepareStatement(
            "INSERT INTO jogo_jogador (id_jogo, id_jogador, estado, obs, numero) VALUES (?, ?, ?, ?, ?)"
        );

        for (JogadorConvocado jogador : convocatoriaData.getJogadoresConvocatoria()) {
            // Obter o número do jogador
            selectStatement.setInt(1, jogador.getId_jogador());
            ResultSet rs = selectStatement.executeQuery();
            int numero = 0;
            if (rs.next()) {
                numero = rs.getInt("numero");
            }
            rs.close();

            // Adicionar ao batch de inserção
            insertStatement.setInt(1, convocatoriaData.getId());
            insertStatement.setInt(2, jogador.getId_jogador());
            insertStatement.setString(3, jogador.getEstado());
            insertStatement.setString(4, jogador.getObs());
            insertStatement.setInt(5, numero);
            insertStatement.addBatch();
        }

        int[] rowsAffected = insertStatement.executeBatch();
        conn.commit();

        // Atualizar estado do jogo para INICIADO
        updateEstadoJogo(convocatoriaData.getId(), "INICIADO");

        return rowsAffected.length > 0;

    } catch (SQLException e) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
        e.printStackTrace();
    } finally {
        try {
            if (deleteStatement != null) deleteStatement.close();
            if (selectStatement != null) selectStatement.close();
            if (insertStatement != null) insertStatement.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                dbUtils.closeConnection(conn);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    return false;
	}

	public ConvocatoriaData getConvocatoriaByJogoId(int jogoId) {

		
		ArrayList<JogadorConvocado> jogadoresConvocados = new ArrayList<JogadorConvocado>();

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement("SELECT id_jogador, nome, jogo_jogador.estado, obs, licença FROM jogo_jogador inner join jogador on jogo_jogador.id_jogador = jogador.id WHERE id_jogo = ?");

			preparedStatement.setInt(1, jogoId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				jogadoresConvocados.add(new JogadorConvocado(rs.getInt("id_jogador"), rs.getString("nome"), rs.getString("estado"), rs.getString("obs"), rs.getString("licença")));
			}

			return new ConvocatoriaData(jogoId, jogadoresConvocados);

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean updateEstadoJogo(int jogoId, String estado) {

		
		try (Connection conn = dbUtils.getConnection()) {
			System.out.println("JogoHelper | updateEstadoJogo | jogoId: " + jogoId + ", estado: " + estado);
			PreparedStatement preparedStatement = conn.prepareStatement("UPDATE jogo SET estado = ? WHERE id = ?");

			preparedStatement.setString(1, estado);
			preparedStatement.setInt(2, jogoId);

			int rowsAffected = preparedStatement.executeUpdate();
			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}	

	public ArrayList<JogoData> getJogosByJogadorId(int jogadorId) {
		
		ArrayList<JogoData> jogos = new ArrayList<>();

		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement("SELECT j.id, j.epoca_id, j.equipa_id, j.tipoequipa, j.data, j.hora, j.local, j.golos_equipa, j.equipa_adv_id, clube.nome AS clube_nome, j.tipoequipa_adv, j.golos_equipa_adv, j.tipo_local, j.competicao_id, competicao_descritivo AS competicao_nome, j.arbitro_1, j.arbitro_2, j.estado, j.hora_concentracao, j.obs, j.numerojogo, jj.*, jg.nome AS jogador_nome, jg.licença " +
										"FROM jogo_jogador jj " +
										"INNER JOIN jogo j ON jj.id_jogo = j.id " +
										"INNER JOIN jogador jg ON jg.id = jj.id_jogador " +
										"INNER JOIN clube ON clube.id = j.equipa_adv_id " +
										"WHERE jj.id_jogador = ? and jj.estado<>'INDISPONÍVEL'" +
										"ORDER BY j.data, j.hora");

			preparedStatement.setInt(1, jogadorId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				JogoData jogo = new JogoData(rs.getInt("id"), rs.getInt("epoca_id"), rs.getInt("equipa_id"),
						rs.getString("tipoequipa"), rs.getString("data"), rs.getString("hora"), rs.getString("local"),
						rs.getInt("golos_equipa"), rs.getInt("equipa_adv_id"), rs.getString("tipoequipa_adv"),
						rs.getString("clube_nome"), rs.getInt("golos_equipa_adv"), rs.getString("tipo_local"), rs.getInt("competicao_id"),
						rs.getString("competicao_nome"), rs.getString("arbitro_1"), rs.getString("arbitro_2"), rs.getString("estado"),
						rs.getString("hora_concentracao"), rs.getString("obs"), rs.getString("numerojogo"));

				JogadorJogo jogadorNoJogo = new JogadorJogo(rs.getInt("id_jogador"), rs.getString("jogador_nome"), rs.getBoolean("capitao"), rs.getInt("numero"),
						rs.getInt("amarelo"), rs.getInt("azul"), rs.getInt("vermelho"), rs.getInt("golo_p"),
						rs.getInt("golo_ld"), rs.getInt("golo_pp"), rs.getInt("golo_up"), rs.getInt("golo_normal"),
						rs.getInt("golo_s_p"), rs.getInt("golo_s_ld"), rs.getInt("golo_s_up"), rs.getInt("golo_s_pp"),
						rs.getInt("golo_s_normal"), rs.getString("estado"), rs.getString("obs"), rs.getInt("faltas"), rs.getInt("assistencias"),
						rs.getInt("recuperacoes_bola"), rs.getInt("perdas_bola"), rs.getInt("remates"), rs.getInt("penalty_falhado"), rs.getInt("penalty_defesa"),
						rs.getInt("ld_falhado"), rs.getInt("ld_defesa"), rs.getString("Licença"), rs.getBoolean("gr"));

				jogadorNoJogo.setTitular(rs.getBoolean("titular"));
				jogadorNoJogo.setEmCampo(rs.getBoolean("em_campo"));
				jogadorNoJogo.setTempoJogoSegundos(rs.getInt("tempo_jogo_segundos"));
				Object excluidoAte = rs.getObject("excluido_ate_segundos");
				jogadorNoJogo.setExcluidoAteSegundos(excluidoAte != null ? rs.getInt("excluido_ate_segundos") : null);

				ArrayList<JogadorJogo> jogadores = new ArrayList<>();
				jogadores.add(jogadorNoJogo);
				jogo.setJogadores(jogadores);

				jogos.add(jogo);
			}

			return jogos;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return null;
	}
}

