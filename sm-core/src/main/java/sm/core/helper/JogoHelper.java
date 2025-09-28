package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import sm.core.data.CompeticaoData;
import sm.core.data.ConvocatoriaData;
import sm.core.data.JogoData;

public class JogoHelper {


public ArrayList<JogoData> getAllJogosByEquipa(int parmEquipaID) {

		DBUtils dbUtils = new DBUtils();
		ArrayList<JogoData> jogos = new ArrayList<>();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select jogo.id,epoca_id,equipa_id,tipoEquipa,Data,Hora,local,golos_equipa,equipa_adv_id, clube.nome , tipoEquipa_adv,golos_equipa_adv,tipo_local,competicao_id, c.nome, arbitro_1,arbitro_2,estado \r\n" + //
												"From jogo\r\n" + //
												"inner join competicao c on c.id=competicao_id \r\n" + //
												"inner join clube on clube.id=equipa_adv_id where EQUIPA_ID=? ");

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
						rs.getString("c.nome"), rs.getString("arbitro_1"), rs.getString("arbitro_2"), rs.getString("estado"));

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
					.prepareStatement("INSERT INTO jogo (epoca_id, equipa_id, tipoEquipa, Data, Hora, local, golos_equipa, equipa_adv_id, tipoEquipa_adv, golos_equipa_adv, tipo_local, competicao_id, arbitro_1, arbitro_2, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

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

			int rowsAffected = preparedStatement.executeUpdate();
			dbUtils.closeConnection(preparedStatement.getConnection());
			return rowsAffected > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public boolean updateJogo(JogoData jogo) {

		DBUtils dbUtils = new DBUtils();
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("UPDATE jogo SET epoca_id = ?, equipa_id = ?, tipoEquipa = ?, Data = ?, Hora = ?, local = ?, golos_equipa = ?, equipa_adv_id = ?, tipoEquipa_adv = ?, golos_equipa_adv = ?, tipo_local = ?, competicao_id = ?, arbitro_1 = ?, arbitro_2 = ?, estado = ? WHERE id = ?");

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
			preparedStatement.setInt(16, jogo.getId());

			int rowsAffected = preparedStatement.executeUpdate();
			dbUtils.closeConnection(preparedStatement.getConnection());
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

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select jogo.id,epoca_id,equipa_id,tipoEquipa,Data,Hora,local,golos_equipa,equipa_adv_id, clube.nome , tipoEquipa_adv,golos_equipa_adv,tipo_local,competicao_id, c.nome, arbitro_1,arbitro_2,estado \r\n" + //
												"From jogo\r\n" + //
												"inner join competicao c on c.id=competicao_id \r\n" + //
												"inner join clube on clube.id=equipa_adv_id where jogo.ID=? ");

			preparedStatement.setInt(1, id);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			if (rs.next()) {

				JogoData jogo = new JogoData(rs.getInt("id"), rs.getInt("epoca_id"), rs.getInt("equipa_id"),
						rs.getString("tipoEquipa"), rs.getString("data"), rs.getString("hora"), rs.getString("local"),
						rs.getInt("golos_equipa"), rs.getInt("equipa_adv_id"), rs.getString("tipoEquipa_adv"),
						rs.getString("clube.nome"), rs.getInt("golos_equipa_adv"), rs.getString("tipo_local"), rs.getInt("competicao_id"),
						rs.getString("c.nome"), rs.getString("arbitro_1"), rs.getString("arbitro_2"), rs.getString("estado"));

				dbUtils.closeConnection(preparedStatement.getConnection());
				return jogo;
			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return null;

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
					.prepareStatement("DELETE FROM convocatoria_jogador WHERE id_jogo = ?");
			deleteStatement.setInt(1, convocatoriaData.getId());
			deleteStatement.executeUpdate();
			deleteStatement.close();

			// Depois, inserir a nova convocatória
			PreparedStatement insertStatement = dbUtils.getConnection()
					.prepareStatement("INSERT INTO convocatoria_jogador (id_jogo, id_jogador) VALUES (?, ?)");

			for (Integer jogadorId : convocatoriaData.getJogadoresConvocados()) {
				insertStatement.setInt(1, convocatoriaData.getId());
				insertStatement.setInt(2, jogadorId);
				insertStatement.addBatch();
			}

			int[] rowsAffected = insertStatement.executeBatch();
			insertStatement.close();
			dbUtils.closeConnection(dbUtils.getConnection());
			return rowsAffected.length > 0;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public ConvocatoriaData getConvocatoriaByJogoId(int jogoId) {

		DBUtils dbUtils = new DBUtils();
		ArrayList<Integer> jogadoresConvocados = new ArrayList<>();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("SELECT id_jogador FROM convocatoria_jogador WHERE id_jogo = ?");

			preparedStatement.setInt(1, jogoId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				jogadoresConvocados.add(rs.getInt("id_jogador"));
			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return new ConvocatoriaData(jogoId, jogadoresConvocados);

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

}
