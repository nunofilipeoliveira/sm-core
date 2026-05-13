package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.springframework.stereotype.Component;

import sm.core.data.EpocaData;
import sm.core.data.EquipaData;
import sm.core.data.EscalaoData;
import sm.core.data.EscalaoEpocaData;
import sm.core.data.JogadorData;
import sm.core.data.StaffData;

@Component
public class EquipaHelper {

	private final DBUtils dbUtils;

	public EquipaHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;
		
	}

	public EquipaData getEquipaID(int parmId) {

		
		EquipaData equipaData = null;
		JogadorData tmpJogador = null;
		JogadorHelper jogadorHelper = null;
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select ee.id, e2.descritivo , e.nome from escalao_epoca ee\r\n"
							+ "inner join escalao e on e.id=ee.id_escalao \r\n"
							+ "inner join epoca e2 on ee.id_epoca=e2.id \r\n" + "where ee.id =?");

			preparedStatement.setInt(1, parmId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				if (equipaData == null) {
					equipaData = new EquipaData(rs.getInt("id"), rs.getString("descritivo"), rs.getString("nome"));
				}

			}

			// ler jogadores

			preparedStatement = conn.prepareStatement("select *from escalao_epoca_jogador where id_escalao_epoca =?");

			preparedStatement.setInt(1, parmId);
			rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				jogadorHelper = new JogadorHelper(dbUtils);
				tmpJogador = jogadorHelper.getJogadorbyID(rs.getInt("id_jogador"));

				equipaData.addJogador(tmpJogador);

			}

			dbUtils.closeConnection(conn);
			return equipaData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean addJogadorEquipa(int parmIdEquipa, JogadorData parmJogadorData) {

		

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("INSERT INTO escalao_epoca_jogador VALUES (?, ?)");

			preparedStatement.setInt(1, parmIdEquipa);
			preparedStatement.setInt(2, parmJogadorData.getId());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	public boolean addStaffEquipa(int parmIdEquipa, StaffData parmStaffData) {

		

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("INSERT INTO escalao_epoca_staff VALUES (?, ?, ?)");

			preparedStatement.setInt(1, parmIdEquipa);
			preparedStatement.setInt(2, parmStaffData.getId());
			preparedStatement.setString(3, parmStaffData.getTipo());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	public boolean removeJogadorEquipa(int parmIdEquipa, JogadorData parmJogadorData) {

		

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("DELETE FROM escalao_epoca_jogador where id_escalao_epoca=? and id_jogador=?");

			preparedStatement.setInt(1, parmIdEquipa);
			preparedStatement.setInt(2, parmJogadorData.getId());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	public boolean removeStaffEquipa(int parmIdEquipa, StaffData parmStaffData) {

		

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("DELETE FROM escalao_epoca_staff where id_escalao_epoca=? and id_staff=?");

			preparedStatement.setInt(1, parmIdEquipa);
			preparedStatement.setInt(2, parmStaffData.getId());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	public ArrayList<EquipaData> getAllEquipaLightEpocaAtual(int parmTenantID) {

		
		int tmpIdEquipa = 0;
		ArrayList<EquipaData> equipas = new ArrayList<>();
		EquipaData equipaData = null;

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select *from escalao_epoca inner join \r\n"
							+ " epoca ON estado='1' and tenant_id=? and epoca.id=escalao_epoca.id_epoca");

			preparedStatement.setInt(1, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				tmpIdEquipa = rs.getInt("id");

				equipaData = new EquipaData(tmpIdEquipa, rs.getString("epoca.descritivo"), rs.getString("escalao_epoca.nome"));

				equipas.add(equipaData);

			}

			dbUtils.closeConnection(conn);

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return equipas;
	}

	public EquipaData getEquipaIDLight(int parmId) {

		
		EquipaData equipaData = null;
		JogadorData tmpJogador = null;

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select	ee.id,	e2.Descritivo ,	e.Nome,	j.id, j.nome, j.numero,	j.data_nascimento, j.Tenant_id\r\n"
							+ "from escalao_epoca ee\r\n"
							+ "inner join escalao e on	e.id = ee.id_escalao\r\n"
							+ "inner join epoca e2 on	ee.id_epoca = e2.id\r\n"
							+ "left join escalao_epoca_jogador eej on	eej.id_escalao_epoca = ee.id\r\n"
							+ "left join jogador j on	j.id = eej.id_jogador \r\n"
							+ "where   ee.id = ? and j.estado = '1'");

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
				tmpJogador.setTenant_id(rs.getInt("tenant_id"));
				equipaData.addJogador(tmpJogador);

			}

			// ler staff

			PreparedStatement preparedStatement2 = conn.prepareStatement(
					"select	ee.id as id,	e2.descritivo, 	e.nome, 	j.id,	j.nome as nome,	j.data_nascimento,	eej.tipo, 0 as id_Jogador, j.licença, j.tenant_id\r\n"
							+ "from	escalao_epoca ee\r\n" + "inner join escalao e on	e.id = ee.id_escalao\r\n"
							+ "inner join epoca e2 on ee.id_epoca=e2.id	\r\n"
							+ "inner join escalao_epoca_staff eej on eej.id_escalao_epoca = ee.id\r\n"
							+ "inner join staff j on j.id = eej.id_staff\r\n"
							+ "where	ee.id = ? and j.id_jogador = 0\r\n" + "union\r\n"
							+ "select 	ee.id as id,	e2.descritivo,	e.nome,	j.id,	j2.nome as nome,	j2.data_nascimento,	eej.tipo, id_jogador, j.licença, j.tenant_id\r\n"
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
				tmpStaff.setLicenca(rs.getString("licença"));
				equipaData.addStaff(tmpStaff);

			}

			dbUtils.closeConnection(conn);
			return equipaData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public EpocaData getEpocaAtiva(int parmTenantID) {

		
		EpocaData epocaAtual = null;

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select *from epoca where estado='1' and tenant_id=? ");

			preparedStatement.setInt(1, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				epocaAtual = new EpocaData(rs.getInt("id"), rs.getString("descritivo"), rs.getString("estado"),
						rs.getInt("anoinicio"));

			}

			dbUtils.closeConnection(conn);
			return epocaAtual;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean setEpocaAtual(int idepoca, int parmTenantID) {

		

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("update epoca set estado='1' where tenant_id=? and id=?");

			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setInt(2, idepoca);

			preparedStatement.executeUpdate();

			

			preparedStatement = conn
					.prepareStatement("update epoca set estado='0' where tenant_id=? and id<>?");

			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setInt(2, idepoca);

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean createEscalaoEpoca(EscalaoEpocaData parmEscalaoEpocaData, int parmTenantID) {

		

		// obter o id da epoca em vigor

		EpocaData epoca = getEpocaAtiva(parmTenantID);

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("insert into escalao_epoca(id_escalao, id_epoca, nome) values (?, ?, ?)");

			preparedStatement.setInt(1, parmEscalaoEpocaData.getId_escalao_epoca());
			preparedStatement.setInt(2, epoca.getId());
			preparedStatement.setString(3, parmEscalaoEpocaData.getDescritivo_escalao());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean deleteEscalaoEpoca(EscalaoEpocaData parmEscalaoEpocaData, int parmTenantID) {

		

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("delete from escalao_epoca where id=?");

			preparedStatement.setInt(1, parmEscalaoEpocaData.getId_escalao_epoca());

			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public ArrayList<EpocaData> getAllEpocas(int parmTenantID) {

		
		EpocaData epocaAtual = null;
		ArrayList<EpocaData> epocas = new ArrayList<>();

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
					.prepareStatement("select *from epoca where tenant_id=? ");

			preparedStatement.setInt(1, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				epocaAtual = new EpocaData(rs.getInt("id"), rs.getString("descritivo"), rs.getString("estado"),
						rs.getInt("anoinicio"));
				epocas.add(epocaAtual);

			}

			dbUtils.closeConnection(conn);
			return epocas;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<EscalaoData> getAllEscaloes() {

		
		EscalaoData escalao = null;
		ArrayList<EscalaoData> escaloes = new ArrayList<>();

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement("select *from escalao ");

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				escalao = new EscalaoData(rs.getInt("id"), rs.getString("nome"));
				escaloes.add(escalao);

			}

			dbUtils.closeConnection(conn);
			return escaloes;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public String getEscalaoByEquipa(int parmEquipaID) {

		
		String escalao = "";

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement(
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

			dbUtils.closeConnection(conn);
			return escalao;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return escalao;
	}

}
