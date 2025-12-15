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
import sm.core.data.JogoData;

@Component
public class JogoHelper {

	private final DBUtils dbUtils;

	public JogoHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;
	}

public ArrayList<JogoData> getAllJogosByEquipa(int parmEquipaID) {

		
		ArrayList<JogoData> jogos = new ArrayList<>();

		try {
			Connection conn = dbUtils.getConnection();
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

			dbUtils.closeConnection(conn);
			return jogos;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}


	public ArrayList<CompeticaoData> getAllCompeticoes() {

		
		ArrayList<CompeticaoData> competicoes = new ArrayList<>();

		try {
			Connection conn = dbUtils.getConnection();
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

			dbUtils.closeConnection(conn);
			return competicoes;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}	

	public boolean createJogo(JogoData jogo) {

		
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("INSERT INTO jogo (epoca_id, equipa_id, tipoEquipa, Data, Hora, local, golos_equipa, equipa_adv_id, tipoEquipa_adv, golos_equipa_adv, tipo_local, competicao_id, competicao_descritivo, arbitro_1, arbitro_2, estado, hora_concentracao, obs, numeroJogo) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

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
			dbUtils.closeConnection(conn);

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

		
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("UPDATE jogo SET epoca_id = ?, equipa_id = ?, tipoEquipa = ?, Data = ?, Hora = ?, local = ?, golos_equipa = ?, equipa_adv_id = ?, tipoEquipa_adv = ?, golos_equipa_adv = ?, tipo_local = ?, competicao_id = ?, competicao_descritivo=?, arbitro_1 = ?, arbitro_2 = ?, estado = ?, hora_concentracao = ?, obs = ?, numeroJogo=? WHERE id = ?");

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
				//Atualizar convocatória se existirem jogadores associados
				preparedStatement = conn
						.prepareStatement("DELETE FROM jogo_jogador WHERE id_jogo = ?");
				preparedStatement.setInt(1, jogo.getId());
				preparedStatement.executeUpdate();	
				preparedStatement.close();
				PreparedStatement insertStatement = conn
						.prepareStatement("INSERT INTO jogo_jogador (id_jogo, id_jogador, capitao, numero, amarelo, azul, vermelho, golo_p, golo_ld, golo_pp, golo_up, golo_normal, golo_s_p, golo_s_ld, golo_s_up, golo_s_pp, golo_s_normal, estado, obs, faltas, assistencias, recuperacoes_bola, perdas_bola, remates, penalty_falhado, penalty_defesa, ld_falhado, ld_defesa) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
					insertStatement.executeUpdate();
				}
				insertStatement.close();
				
			}

			dbUtils.closeConnection(conn);
			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public boolean deleteJogo(int id) {

		
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("DELETE FROM jogo WHERE id = ?");

			preparedStatement.setInt(1, id);

			int rowsAffected = preparedStatement.executeUpdate();
			dbUtils.closeConnection(conn);
			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public JogoData getJogoById(int id) {

		
		JogoData jogo = null;

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select jogo.id,epoca_id,equipa_id,tipoEquipa,Data,Hora,local,golos_equipa,equipa_adv_id, clube.nome , tipoEquipa_adv,golos_equipa_adv,tipo_local,competicao_id, competicao_descritivo, arbitro_1,arbitro_2,estado, hora_concentracao, obs, numeroJogo \r\n" + //
												"From jogo\r\n" + //
												"inner join clube on clube.id=equipa_adv_id where jogo.ID=? ");

			preparedStatement.setInt(1, id);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			if (rs.next()) {

				jogo = new JogoData(rs.getInt("id"), rs.getInt("epoca_id"), rs.getInt("equipa_id"),
						rs.getString("tipoEquipa"), rs.getString("data"), rs.getString("hora"), rs.getString("local"),
						rs.getInt("golos_equipa"), rs.getInt("equipa_adv_id"), rs.getString("tipoEquipa_adv"),
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
					rsJogadores.getInt("ld_defesa")
				);
				listaJogadores.add(jogador);
			}
			
			jogo.setJogadores(listaJogadores);
			dbUtils.closeConnection(conn);

			return jogo;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;


	}

	public boolean salvarConvocatoria(ConvocatoriaData convocatoriaData) {

		
		try {
			// Primeiro, eliminar convocatória existente para o jogo, se houver
			Connection conn = dbUtils.getConnection();
			PreparedStatement deleteStatement = conn.prepareStatement("DELETE FROM jogo_jogador WHERE id_jogo = ?");
			deleteStatement.setInt(1, convocatoriaData.getId());
			deleteStatement.executeUpdate();
			deleteStatement.close();

			// Depois, inserir a nova convocatória
			PreparedStatement insertStatement = conn.prepareStatement("INSERT INTO jogo_jogador (id_jogo, id_jogador, estado, obs, numero) select ?, ?, ?, ?, CASE WHEN numero = '' OR numero IS NULL THEN 0 ELSE CAST(numero AS UNSIGNED) END AS numero FROM jogador where id= ?");

			for (JogadorConvocado jogador : convocatoriaData.getJogadoresConvocatoria()) {
				insertStatement.setInt(1, convocatoriaData.getId());
				insertStatement.setInt(2, jogador.getId_jogador());
				insertStatement.setString(3, jogador.getEstado());
				insertStatement.setString(4, jogador.getObs());
				insertStatement.setInt(5, jogador.getId_jogador());
				insertStatement.addBatch();
			}

			int[] rowsAffected = insertStatement.executeBatch();
			insertStatement.close();
			dbUtils.closeConnection(conn);

			

			//Atualizado estado do jogo para INICIADO
			updateEstadoJogo(convocatoriaData.getId(), "INICIADO");

			return rowsAffected.length > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public ConvocatoriaData getConvocatoriaByJogoId(int jogoId) {

		
		ArrayList<JogadorConvocado> jogadoresConvocados = new ArrayList<JogadorConvocado>();

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement("SELECT id_jogador, nome, jogo_jogador.estado, obs, licença FROM jogo_jogador inner join jogador on jogo_jogador.id_jogador = jogador.id WHERE id_jogo = ?");

			preparedStatement.setInt(1, jogoId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				jogadoresConvocados.add(new JogadorConvocado(rs.getInt("id_jogador"), rs.getString("nome"), rs.getString("estado"), rs.getString("obs"), rs.getString("Licença")));
			}

			dbUtils.closeConnection(conn);
			return new ConvocatoriaData(jogoId, jogadoresConvocados);

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean updateEstadoJogo(int jogoId, String estado) {

		
		try {
			System.out.println("JogoHelper | updateEstadoJogo | jogoId: " + jogoId + ", estado: " + estado);
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement("UPDATE jogo SET estado = ? WHERE id = ?");

			preparedStatement.setString(1, estado);
			preparedStatement.setInt(2, jogoId);

			int rowsAffected = preparedStatement.executeUpdate();
			dbUtils.closeConnection(conn);
			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}	

	public ArrayList<JogoData> getJogosByJogadorId(int jogadorId) {
		
		ArrayList<JogoData> jogos = new ArrayList<>();

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement("SELECT j.id, j.epoca_id, j.equipa_id, j.tipoEquipa, j.Data, j.Hora, j.local, j.golos_equipa, j.equipa_adv_id, clube.nome AS clube_nome, j.tipoEquipa_adv, j.golos_equipa_adv, j.tipo_local, j.competicao_id, competicao_descritivo AS competicao_nome, j.arbitro_1, j.arbitro_2, j.estado, j.hora_concentracao, j.obs, j.numeroJogo, jj.*, jg.nome AS jogador_nome " +
										"FROM jogo_jogador jj " +
										"INNER JOIN jogo j ON jj.id_jogo = j.id " +
										"INNER JOIN JOGADOR jg ON jg.id = jj.id_jogador " +
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
						rs.getString("tipoEquipa"), rs.getString("data"), rs.getString("hora"), rs.getString("local"),
						rs.getInt("golos_equipa"), rs.getInt("equipa_adv_id"), rs.getString("tipoEquipa_adv"),
						rs.getString("clube_nome"), rs.getInt("golos_equipa_adv"), rs.getString("tipo_local"), rs.getInt("competicao_id"),
						rs.getString("competicao_nome"), rs.getString("arbitro_1"), rs.getString("arbitro_2"), rs.getString("estado"),
						rs.getString("hora_concentracao"), rs.getString("obs"), rs.getString("numeroJogo"));

				JogadorJogo jogadorNoJogo = new JogadorJogo(rs.getInt("id_jogador"), rs.getString("jogador_nome"), rs.getBoolean("capitao"), rs.getInt("numero"),
						rs.getInt("amarelo"), rs.getInt("azul"), rs.getInt("vermelho"), rs.getInt("golo_p"),
						rs.getInt("golo_ld"), rs.getInt("golo_pp"), rs.getInt("golo_up"), rs.getInt("golo_normal"),
						rs.getInt("golo_s_p"), rs.getInt("golo_s_ld"), rs.getInt("golo_s_up"), rs.getInt("golo_s_pp"),
						rs.getInt("golo_s_normal"), rs.getString("estado"), rs.getString("obs"), rs.getInt("faltas"), rs.getInt("assistencias"),
						rs.getInt("recuperacoes_bola"), rs.getInt("perdas_bola"), rs.getInt("remates"), rs.getInt("penalty_falhado"), rs.getInt("penalty_defesa"),
						rs.getInt("ld_falhado"), rs.getInt("ld_defesa"));

				ArrayList<JogadorJogo> jogadores = new ArrayList<>();
				jogadores.add(jogadorNoJogo);
				jogo.setJogadores(jogadores);

				jogos.add(jogo);
			}

			dbUtils.closeConnection(conn);
			return jogos;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
		} 
		return null;
	}
}

