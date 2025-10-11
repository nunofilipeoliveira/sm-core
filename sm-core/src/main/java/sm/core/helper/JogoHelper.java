package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import sm.core.data.CompeticaoData;
import sm.core.data.ConvocatoriaData;
import sm.core.data.JogadorConvocado;
import sm.core.data.JogadorJogo;
import sm.core.data.JogoData;

public class JogoHelper {


public ArrayList<JogoData> getAllJogosByEquipa(int parmEquipaID) {

		DBUtils dbUtils = new DBUtils();
		ArrayList<JogoData> jogos = new ArrayList<>();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select jogo.id,epoca_id,equipa_id,tipoEquipa,Data,Hora,local,golos_equipa,equipa_adv_id, clube.nome , tipoEquipa_adv,golos_equipa_adv,tipo_local,competicao_id, c.nome, arbitro_1,arbitro_2,estado, hora_concentracao, jogo.obs \r\n" + //
												"From jogo\r\n" + //
												"inner join competicao c on c.id=competicao_id \r\n" + //
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
						rs.getString("c.nome"), rs.getString("arbitro_1"), rs.getString("arbitro_2"), rs.getString("estado"),
						rs.getString("hora_concentracao"), rs.getString("jogo.obs"));

				jogos.add(jogo);
			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return jogos;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}


	public ArrayList<CompeticaoData> getAllCompeticoes() {

		DBUtils dbUtils = new DBUtils();
		ArrayList<CompeticaoData> competicoes = new ArrayList<>();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select id,nome from competicao");

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				CompeticaoData competicao = new CompeticaoData(rs.getInt("id"), rs.getString("nome"));

				competicoes.add(competicao);
			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return competicoes;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}	

	public boolean createJogo(JogoData jogo) {

		DBUtils dbUtils = new DBUtils();
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("INSERT INTO jogo (epoca_id, equipa_id, tipoEquipa, Data, Hora, local, golos_equipa, equipa_adv_id, tipoEquipa_adv, golos_equipa_adv, tipo_local, competicao_id, arbitro_1, arbitro_2, estado, hora_concentracao, obs) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

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
			preparedStatement.setString(13, jogo.getArbitro_1());
			preparedStatement.setString(14, jogo.getArbitro_2());
			preparedStatement.setString(15, jogo.getEstado());
			preparedStatement.setString(16, calculaHoraConcentracao(jogo.getHora()));
			preparedStatement.setString(17, jogo.getObs());

			//obter ID quando realiza o insert
			int rowsAffected = preparedStatement.executeUpdate();
			dbUtils.closeConnection(preparedStatement.getConnection());

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

		DBUtils dbUtils = new DBUtils();
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("UPDATE jogo SET epoca_id = ?, equipa_id = ?, tipoEquipa = ?, Data = ?, Hora = ?, local = ?, golos_equipa = ?, equipa_adv_id = ?, tipoEquipa_adv = ?, golos_equipa_adv = ?, tipo_local = ?, competicao_id = ?, arbitro_1 = ?, arbitro_2 = ?, estado = ?, hora_concentracao = ?, obs = ? WHERE id = ?");

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
			preparedStatement.setString(13, jogo.getArbitro_1());
			preparedStatement.setString(14, jogo.getArbitro_2());
			preparedStatement.setString(15, jogo.getEstado());
			preparedStatement.setString(16, jogo.getHora_concentracao());
			preparedStatement.setString(17, jogo.getObs());	
			preparedStatement.setInt(18, jogo.getId());

			int rowsAffected = preparedStatement.executeUpdate();
			dbUtils.closeConnection(preparedStatement.getConnection());

			if(jogo.getJogadores()!=null && jogo.getJogadores().size()>0) {
				//Atualizar convocatória se existirem jogadores associados
				preparedStatement = dbUtils.getConnection()
						.prepareStatement("DELETE FROM jogo_jogador WHERE id_jogo = ?");
				preparedStatement.setInt(1, jogo.getId());
				preparedStatement.executeUpdate();	
				preparedStatement.close();
				PreparedStatement insertStatement = dbUtils.getConnection()
						.prepareStatement("INSERT INTO jogo_jogador (id_jogo, id_jogador, capitao, numero, amarelo, azul, vermelho, golo_p, golo_ld, golo_pp, golo_up, golo_normal, golo_s_p, golo_s_ld, golo_s_up, golo_s_pp, golo_s_normal, estado, obs) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
				for(JogadorJogo jogador : jogo.getJogadores()) {
					insertStatement.setInt(1, jogo.getId());
					insertStatement.setInt(2, jogador.getId_jogador());
					insertStatement.setString(3, jogador.getCapitao());
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
					insertStatement.executeUpdate();
				}
				insertStatement.close();
				dbUtils.closeConnection(dbUtils.getConnection());
			}


			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public boolean deleteJogo(int id) {

		DBUtils dbUtils = new DBUtils();
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("DELETE FROM jogo WHERE id = ?");

			preparedStatement.setInt(1, id);

			int rowsAffected = preparedStatement.executeUpdate();
			dbUtils.closeConnection(preparedStatement.getConnection());
			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public JogoData getJogoById(int id) {

		DBUtils dbUtils = new DBUtils();
		JogoData jogo = null;

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select jogo.id,epoca_id,equipa_id,tipoEquipa,Data,Hora,local,golos_equipa,equipa_adv_id, clube.nome , tipoEquipa_adv,golos_equipa_adv,tipo_local,competicao_id, c.nome, arbitro_1,arbitro_2,estado, hora_concentracao, obs \r\n" + //
												"From jogo\r\n" + //
												"inner join competicao c on c.id=competicao_id \r\n" + //
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
						rs.getString("c.nome"), rs.getString("arbitro_1"), rs.getString("arbitro_2"), rs.getString("estado"), rs.getString("hora_concentracao"), rs.getString("obs"));

				dbUtils.closeConnection(preparedStatement.getConnection());
				
			}

			
			
			PreparedStatement psJogadores = dbUtils.getConnection()
					.prepareStatement("SELECT jj.id_jogador, j.nome, jj.capitao, jj.numero, jj.amarelo, jj.azul, jj.vermelho, jj.golo_p, jj.golo_ld, jj.golo_pp, jj.golo_up, jj.golo_normal, jj.golo_s_p, jj.golo_s_ld, jj.golo_s_up, jj.golo_s_pp, jj.golo_s_normal, jj.estado, jj.obs " +
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
					rsJogadores.getString("capitao"),
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
					rsJogadores.getString("obs")
				);
				listaJogadores.add(jogador);
			}
			
			jogo.setJogadores(listaJogadores);
			dbUtils.closeConnection(psJogadores.getConnection());

			return jogo;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;


	}

	public boolean salvarConvocatoria(ConvocatoriaData convocatoriaData) {

		DBUtils dbUtils = new DBUtils();
		try {
			// Primeiro, eliminar convocatória existente para o jogo, se houver
			PreparedStatement deleteStatement = dbUtils.getConnection()
					.prepareStatement("DELETE FROM jogo_jogador WHERE id_jogo = ?");
			deleteStatement.setInt(1, convocatoriaData.getId());
			deleteStatement.executeUpdate();
			deleteStatement.close();

			// Depois, inserir a nova convocatória
			PreparedStatement insertStatement = dbUtils.getConnection()
					.prepareStatement("INSERT INTO jogo_jogador (id_jogo, id_jogador, estado, obs, numero) select ?, ?, ?, ?, CASE WHEN numero = '' OR numero IS NULL THEN 0 ELSE CAST(numero AS UNSIGNED) END AS numero FROM jogador where id= ?");

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
			dbUtils.closeConnection(dbUtils.getConnection());

			

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

		DBUtils dbUtils = new DBUtils();
		ArrayList<JogadorConvocado> jogadoresConvocados = new ArrayList<JogadorConvocado>();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("SELECT id_jogador, nome, jogo_jogador.estado, obs FROM jogo_jogador inner join jogador on jogo_jogador.id_jogador = jogador.id WHERE id_jogo = ?");

			preparedStatement.setInt(1, jogoId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				jogadoresConvocados.add(new JogadorConvocado(rs.getInt("id_jogador"), rs.getString("nome"), rs.getString("estado"), rs.getString("obs")));
			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return new ConvocatoriaData(jogoId, jogadoresConvocados);

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean updateEstadoJogo(int jogoId, String estado) {

		DBUtils dbUtils = new DBUtils();
		try {
			System.out.println("JogoHelper | updateEstadoJogo | jogoId: " + jogoId + ", estado: " + estado);
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("UPDATE jogo SET estado = ? WHERE id = ?");

			preparedStatement.setString(1, estado);
			preparedStatement.setInt(2, jogoId);

			int rowsAffected = preparedStatement.executeUpdate();
			dbUtils.closeConnection(preparedStatement.getConnection());
			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}	

	public ArrayList<JogoData> getJogosByJogadorId(int jogadorId) {
		DBUtils dbUtils = new DBUtils();
		ArrayList<JogoData> jogos = new ArrayList<>();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("SELECT j.id, j.epoca_id, j.equipa_id, j.tipoEquipa, j.Data, j.Hora, j.local, j.golos_equipa, j.equipa_adv_id, clube.nome AS clube_nome, j.tipoEquipa_adv, j.golos_equipa_adv, j.tipo_local, j.competicao_id, c.nome AS competicao_nome, j.arbitro_1, j.arbitro_2, j.estado, j.hora_concentracao, j.obs, jj.*, jg.nome AS jogador_nome " +
										"FROM jogo_jogador jj " +
										"INNER JOIN jogo j ON jj.id_jogo = j.id " +
										"INNER JOIN JOGADOR jg ON jg.id = jj.id_jogador " +
										"INNER JOIN competicao c ON c.id = j.competicao_id " +
										"INNER JOIN clube ON clube.id = j.equipa_adv_id " +
										"WHERE jj.id_jogador = ? " +
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
						rs.getString("hora_concentracao"), rs.getString("obs"));

				JogadorJogo jogadorNoJogo = new JogadorJogo(rs.getInt("id_jogador"), rs.getString("jogador_nome"), rs.getString("capitao"), rs.getInt("numero"),
						rs.getInt("amarelo"), rs.getInt("azul"), rs.getInt("vermelho"), rs.getInt("golo_p"),
						rs.getInt("golo_ld"), rs.getInt("golo_pp"), rs.getInt("golo_up"), rs.getInt("golo_normal"),
						rs.getInt("golo_s_p"), rs.getInt("golo_s_ld"), rs.getInt("golo_s_up"), rs.getInt("golo_s_pp"),
						rs.getInt("golo_s_normal"), rs.getString("estado"), rs.getString("obs"));

				ArrayList<JogadorJogo> jogadores = new ArrayList<>();
				jogadores.add(jogadorNoJogo);
				jogo.setJogadores(jogadores);

				jogos.add(jogo);
			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return jogos;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
		} 
		return null;
	}
}

