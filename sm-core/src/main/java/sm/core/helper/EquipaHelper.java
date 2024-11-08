package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import sm.core.data.EquipaData;
import sm.core.data.JogadorData;
import sm.core.data.StaffData;

public class EquipaHelper {

	public EquipaHelper() {

	}

	public EquipaData getEquipaID(int parmId) {

		DBUtils dbUtils = new DBUtils();
		EquipaData equipaData = null;
		JogadorData tmpJogador = null;
		JogadorHelper jogadorHelper = null;
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select ee.id, e2.Descritivo , e.Nome from escalao_epoca ee\r\n"
							+ "inner join escalao e on e.id=ee.id_escalao \r\n"
							+ "inner join epoca e2 on ee.id_epoca \r\n" + "where ee.id =?");

			preparedStatement.setInt(1, parmId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				if (equipaData == null) {
					equipaData = new EquipaData(rs.getInt("id"), rs.getString("Descritivo"), rs.getString("Nome"));
				}

			}

			// ler jogadores

			preparedStatement = dbUtils.getConnection()
					.prepareStatement("select *from escalao_epoca_jogador where id_escalao_epoca =?");

			preparedStatement.setInt(1, parmId);
			rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				jogadorHelper = new JogadorHelper();
				tmpJogador = jogadorHelper.getJogadorbyID(rs.getInt("id_jogador"));

				equipaData.addJogador(tmpJogador);

			}

			dbUtils.closeConnection();
			return equipaData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public EquipaData getEquipaIDLight(int parmId) {

		DBUtils dbUtils = new DBUtils();
		EquipaData equipaData = null;
		JogadorData tmpJogador = null;

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select\r\n" + "	ee.id,\r\n" + "	e2.Descritivo ,\r\n" + "	e.Nome,\r\n"
							+ "	j.id,\r\n" + "	j.nome,\r\n" + "	j.numero,\r\n" + "	j.data_nascimento \r\n"
							+ "from\r\n" + "	escalao_epoca ee\r\n" + "inner join escalao e on\r\n"
							+ "	e.id = ee.id_escalao\r\n" + "inner join epoca e2 on\r\n" + "	ee.id_epoca\r\n"
							+ "inner join escalao_epoca_jogador eej \r\n" + "on eej.id_escalao_epoca =ee.id\r\n"
							+ "inner join jogador j \r\n" + "on j.id=eej.id_jogador \r\n" + "where\r\n" + " j.estado='1' and ee.id = ?");

			preparedStatement.setInt(1, parmId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				if (equipaData == null) {
					equipaData = new EquipaData(rs.getInt("id"), rs.getString("Descritivo"), rs.getString("Nome"));
				}

				// ler jogadores

				tmpJogador = new JogadorData();
				tmpJogador.setId(rs.getInt("j.id"));
				tmpJogador.setNome(rs.getString("j.nome"));
				tmpJogador.setNumero(rs.getString("j.numero"));
				tmpJogador.setData_nascimento(rs.getInt("j.data_nascimento"));
				equipaData.addJogador(tmpJogador);

			}
			
			//ler staff
			
			preparedStatement = dbUtils.getConnection()
					.prepareStatement("select	ee.id as id,	e2.Descritivo, 	e.Nome, 	j.id,	j.nome as nome,	j.data_nascimento,	eej.tipo, 0 as id_Jogador\r\n"
							+ "from	escalao_epoca ee\r\n"
							+ "inner join escalao e on	e.id = ee.id_escalao\r\n"
							+ "inner join epoca e2 on ee.id_epoca\r\n"
							+ "inner join escalao_epoca_staff eej on eej.id_escalao_epoca = ee.id\r\n"
							+ "inner join staff j on j.id = eej.id_staff\r\n"
							+ "where	ee.id = ? and j.id_jogador = 0\r\n"
							+ "union\r\n"
							+ "select 	ee.id as id,	e2.Descritivo,	e.Nome,	j.id,	j2.nome as nome,	j2.data_nascimento,	eej.tipo, id_jogador\r\n"
							+ "from escalao_epoca ee\r\n"
							+ "inner join escalao e on	e.id = ee.id_escalao\r\n"
							+ "inner join epoca e2 on	ee.id_epoca\r\n"
							+ "inner join escalao_epoca_staff eej on	eej.id_escalao_epoca = ee.id\r\n"
							+ "inner join staff j on	j.id = eej.id_staff\r\n"
							+ "inner join jogador j2 on	j.id_jogador = j2.id\r\n"
							+ "where	ee.id = ?	and j.id_jogador <> 0");
			preparedStatement.setInt(1, parmId);
			preparedStatement.setInt(2, parmId);
			rs = preparedStatement.executeQuery();
			
			StaffData tmpStaff = null;
			while (rs.next()) {
			
				// ler jogadores

				tmpStaff = new StaffData(rs.getInt(4), rs.getString(5), rs.getString("tipo"), rs.getInt("id_Jogador"));
				equipaData.addStaff(tmpStaff);
				
			}
			
			

			dbUtils.closeConnection();
			return equipaData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

}
