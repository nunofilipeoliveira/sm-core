package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import sm.core.data.EpocaData;
import sm.core.data.EquipaData;
import sm.core.data.EscalaoData;
import sm.core.data.EscalaoEpocaData;
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
							+ "inner join epoca e2 on ee.id_epoca=e2.id \r\n" + "where ee.id =?");

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

			dbUtils.closeConnection(preparedStatement.getConnection());
			return equipaData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean addJogadorEquipa(int parmIdEquipa, JogadorData parmJogadorData) {

		DBUtils dbUtils = new DBUtils();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("INSERT INTO escalao_epoca_jogador VALUES (?, ?)");

			preparedStatement.setInt(1, parmIdEquipa);
			preparedStatement.setInt(2, parmJogadorData.getId());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(preparedStatement.getConnection());

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	public boolean addStaffEquipa(int parmIdEquipa, StaffData parmStaffData) {

		DBUtils dbUtils = new DBUtils();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("INSERT INTO escalao_epoca_staff VALUES (?, ?, ?)");

			preparedStatement.setInt(1, parmIdEquipa);
			preparedStatement.setInt(2, parmStaffData.getId());
			preparedStatement.setString(3, parmStaffData.getTipo());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(preparedStatement.getConnection());

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	public boolean removeJogadorEquipa(int parmIdEquipa, JogadorData parmJogadorData) {

		DBUtils dbUtils = new DBUtils();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("DELETE FROM escalao_epoca_jogador where id_escalao_epoca=? and id_jogador=?");

			preparedStatement.setInt(1, parmIdEquipa);
			preparedStatement.setInt(2, parmJogadorData.getId());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(preparedStatement.getConnection());

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	public boolean removeStaffEquipa(int parmIdEquipa, StaffData parmStaffData) {

		DBUtils dbUtils = new DBUtils();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("DELETE FROM escalao_epoca_staff where id_escalao_epoca=? and id_staff=?");

			preparedStatement.setInt(1, parmIdEquipa);
			preparedStatement.setInt(2, parmStaffData.getId());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(preparedStatement.getConnection());

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	public ArrayList<EquipaData> getAllEquipaLightEpocaAtual(int parmTenantID) {

		DBUtils dbUtils = new DBUtils();
		int tmpIdEquipa = 0;
		ArrayList<EquipaData> equipas = new ArrayList<>();
		EquipaData equipaData = null;

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select *from ESCALAO_EPOCA inner join \r\n"
							+ " EPOCA ON ESTADO='1' and TENANT_ID=? and EPOCA.ID=ESCALAO_EPOCA.ID_EPOCA");

			preparedStatement.setInt(1, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				tmpIdEquipa = rs.getInt("id");

				equipaData = new EquipaData(tmpIdEquipa, rs.getString("EPOCA.DESCRITIVO"),
						rs.getString("ESCALAO_EPOCA.NOME"));

				equipas.add(equipaData);

			}

			dbUtils.closeConnection(preparedStatement.getConnection());

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return equipas;
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
							+ "	e.id = ee.id_escalao\r\n" + "inner join epoca e2 on\r\n" + "	ee.id_epoca=e2.id\r\n"
							+ "inner join escalao_epoca_jogador eej \r\n" + "on eej.id_escalao_epoca =ee.id\r\n"
							+ "inner join jogador j \r\n" + "on j.id=eej.id_jogador \r\n" + "where\r\n"
							+ " j.estado='1' and ee.id = ?");

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

			// ler staff

			PreparedStatement preparedStatement2 = dbUtils.getConnection().prepareStatement(
					"select	ee.id as id,	e2.Descritivo, 	e.Nome, 	j.id,	j.nome as nome,	j.data_nascimento,	eej.tipo, 0 as id_Jogador\r\n"
							+ "from	escalao_epoca ee\r\n" + "inner join escalao e on	e.id = ee.id_escalao\r\n"
							+ "inner join epoca e2 on ee.id_epoca=e2.id	\r\n"
							+ "inner join escalao_epoca_staff eej on eej.id_escalao_epoca = ee.id\r\n"
							+ "inner join staff j on j.id = eej.id_staff\r\n"
							+ "where	ee.id = ? and j.id_jogador = 0\r\n" + "union\r\n"
							+ "select 	ee.id as id,	e2.Descritivo,	e.Nome,	j.id,	j2.nome as nome,	j2.data_nascimento,	eej.tipo, id_jogador\r\n"
							+ "from escalao_epoca ee\r\n" + "inner join escalao e on	e.id = ee.id_escalao\r\n"
							+ "inner join epoca e2 on	ee.id_epoca=e2.id\r\n"
							+ "inner join escalao_epoca_staff eej on	eej.id_escalao_epoca = ee.id\r\n"
							+ "inner join staff j on	j.id = eej.id_staff\r\n"
							+ "inner join jogador j2 on	j.id_jogador = j2.id\r\n"
							+ "where	ee.id = ?	and j.id_jogador <> 0");
			preparedStatement2.setInt(1, parmId);
			preparedStatement2.setInt(2, parmId);
			rs = preparedStatement2.executeQuery();

			StaffData tmpStaff = null;
			while (rs.next()) {

				// ler jogadores

				tmpStaff = new StaffData(rs.getInt(4), rs.getString(5), rs.getString("tipo"), rs.getInt("id_Jogador"));
				equipaData.addStaff(tmpStaff);

			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			dbUtils.closeConnection(preparedStatement2.getConnection());
			return equipaData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public EpocaData getEpocaAtiva(int parmTenantID) {

		DBUtils dbUtils = new DBUtils();
		EpocaData epocaAtual = null;

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select *from epoca where estado='1' and TENANT_ID=? ");

			preparedStatement.setInt(1, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				epocaAtual = new EpocaData(rs.getInt("id"), rs.getString("Descritivo"), rs.getString("Estado"),
						rs.getInt("AnoInicio"));

			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return epocaAtual;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean setEpocaAtual(int idepoca, int parmTenantID) {

		DBUtils dbUtils = new DBUtils();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("UPDATE EPOCA SET ESTADO='1' WHERE TENANT_ID=? AND ID=?");

			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setInt(2, idepoca);

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(preparedStatement.getConnection());

			preparedStatement = dbUtils.getConnection()
					.prepareStatement("UPDATE EPOCA SET ESTADO='0' WHERE TENANT_ID=? AND ID<>?");

			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setInt(2, idepoca);

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(preparedStatement.getConnection());

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean createEscalaoEpoca(EscalaoEpocaData parmEscalaoEpocaData, int parmTenantID) {

		DBUtils dbUtils = new DBUtils();

		// obter o id da epoca em vigor

		EpocaData epoca = getEpocaAtiva(parmTenantID);

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("INSERT INTO ESCALAO_EPOCA(ID_ESCALAO, ID_EPOCA, NOME) VALUES (?, ?, ?)");

			preparedStatement.setInt(1, parmEscalaoEpocaData.getId_escalao_epoca());
			preparedStatement.setInt(2, epoca.getId());
			preparedStatement.setString(3, parmEscalaoEpocaData.getDescritivo_escalao());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(preparedStatement.getConnection());

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean deleteEscalaoEpoca(EscalaoEpocaData parmEscalaoEpocaData, int parmTenantID) {

		DBUtils dbUtils = new DBUtils();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("DELETE FROM ESCALAO_EPOCA WHERE ID=?");

			preparedStatement.setInt(1, parmEscalaoEpocaData.getId_escalao_epoca());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(preparedStatement.getConnection());

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public ArrayList<EpocaData> getAllEpocas(int parmTenantID) {

		DBUtils dbUtils = new DBUtils();
		EpocaData epocaAtual = null;
		ArrayList<EpocaData> epocas = new ArrayList<>();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select *from epoca where TENANT_ID=? ");

			preparedStatement.setInt(1, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				epocaAtual = new EpocaData(rs.getInt("id"), rs.getString("Descritivo"), rs.getString("Estado"),
						rs.getInt("AnoInicio"));
				epocas.add(epocaAtual);

			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return epocas;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<EscalaoData> getAllEscaloes() {

		DBUtils dbUtils = new DBUtils();
		EscalaoData escalao = null;
		ArrayList<EscalaoData> escaloes = new ArrayList<>();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement("select *from ESCALAO ");

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				escalao = new EscalaoData(rs.getInt("id"), rs.getString("Nome"));
				escaloes.add(escalao);

			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return escaloes;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public String getEscalaoByEquipa(int parmEquipaID) {

		DBUtils dbUtils = new DBUtils();
		String escalao = "";

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement(
					"SELECT escalao.nome FROM escalao inner join escalao_epoca e on e.id_escalao = escalao.id where e.id =?");

			preparedStatement.setInt(1, parmEquipaID);
			;
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				escalao = rs.getString(1);

			}

			dbUtils.closeConnection(preparedStatement.getConnection());
			return escalao;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return escalao;
	}

}
