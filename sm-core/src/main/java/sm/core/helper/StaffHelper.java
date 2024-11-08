package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;

import sm.core.data.StaffData;

public class StaffHelper {

	public StaffHelper() {

	}

	public StaffData getStaffByID(int parmId) {

		DBUtils dbUtils = new DBUtils();
		StaffData staffData = null;
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement(
					"select ID, NOME, NOME_COMPLETO, TELEMOVEL, EMAIL, MORADA, CODIGO_POSTAL, DATA_NASCIMENTO, ID_JOGADOR from staff where ID_JOGADOR=0 and ID=?\r\n"
							+ "union\r\n"
							+ "select STAFF.ID, J.NOME, J.NOME_COMPLETO, J.TELEMOVEL, J.EMAIL, J.MORADA, J.CODIGO_POSTAL, J.DATA_NASCIMENTO, ID_JOGADOR from staff\r\n"
							+ "inner join jogador j on J.ID=id_jogador \r\n" + "where ID_JOGADOR<>0 and STAFF.ID=?");

			preparedStatement.setInt(1, parmId);
			preparedStatement.setInt(2, parmId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				if (staffData == null) {
					staffData = new StaffData(rs.getInt("id"), rs.getString("nome"), rs.getString("nome_completo"),
							rs.getString("telemovel"), rs.getString("email"), rs.getString("morada"),
							rs.getString("Codigo_postal"), rs.getInt("data_nascimento"), rs.getInt("Id_jogador"), "");
				}

			}

			dbUtils.closeConnection();
			return staffData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean updateStaff(StaffData parmStaff, int parmIdUtilizador) {

		DBUtils dbUtils = new DBUtils();
		StaffData staffData = new StaffData();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("SELECT * FROM STAFF WHERE ID=?");

			preparedStatement.setInt(1, parmStaff.getId());
			ResultSet rs = preparedStatement.executeQuery();

			while (rs.next()) {
				staffData = new StaffData(rs.getInt("id"), rs.getString("nome"), rs.getString("nome_completo"),
						rs.getString("telemovel"), rs.getString("email"), rs.getString("morada"),
						rs.getString("codigo_postal"), rs.getInt("data_nascimento"));

			}

			// Realiza a comparação campo a campo e regita no histórico.
			if (!parmStaff.getNome().equals(staffData.getNome())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), dbUtils, "Nome", staffData.getNome(),
						parmStaff.getNome());

			}

			if (!parmStaff.getCodigo_postal().equals(staffData.getCodigo_postal())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), dbUtils, "Código Postal",
						parmStaff.getCodigo_postal(), staffData.getCodigo_postal());
			}
			if (parmStaff.getData_nascimento() != staffData.getData_nascimento()) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), dbUtils, "Data Nascimento",
						String.valueOf(staffData.getData_nascimento()), String.valueOf(parmStaff.getData_nascimento()));
			}
			if (!parmStaff.getEmail().equals(staffData.getEmail())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), dbUtils, "Email", staffData.getEmail(),
						parmStaff.getEmail());
			}
			if (!parmStaff.getMorada().equals(staffData.getMorada())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), dbUtils, "Morada", staffData.getMorada(),
						parmStaff.getMorada());
			}

			if (!parmStaff.getNome_completo().equals(staffData.getNome_completo())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), dbUtils, "Nome Completo",
						staffData.getNome_completo(), parmStaff.getNome_completo());
			}

			if (!parmStaff.getTelemovel().equals(staffData.getTelemovel())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), dbUtils, "Telemovel",
						staffData.getTelemovel(), parmStaff.getTelemovel());
			}

			// Atualiza a informação

			preparedStatement = dbUtils.getConnection()
					.prepareStatement("update\r\n" + "	staff \r\n" + "set\r\n" + "	nome =? ,\r\n"
							+ "	data_nascimento =? ,\r\n" + "	email =? ,\r\n" + "	telemovel =? ,\r\n"
							+ "	morada =? ,\r\n" + "	codigo_postal =? ,\r\n" + "	nome_completo =? \r\n"
							+ "where\r\n" + "	ID =?");

			preparedStatement.setString(1, parmStaff.getNome());
			preparedStatement.setInt(2, parmStaff.getData_nascimento());
			preparedStatement.setString(3, parmStaff.getEmail());
			preparedStatement.setString(4, parmStaff.getTelemovel());
			preparedStatement.setString(5, parmStaff.getMorada());
			preparedStatement.setString(6, parmStaff.getCodigo_postal());
			preparedStatement.setString(7, parmStaff.getNome_completo());
			preparedStatement.setInt(8, parmStaff.getId());
			preparedStatement.executeUpdate();

			dbUtils.closeConnection();

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	private void registaHistoricoStaff(int parmIdUtilizador, int id_Staff, DBUtils dbUtils, String campo,
			String valorAntigo, String valorNovo) throws SQLException {
		PreparedStatement preparedStatement;
		preparedStatement = dbUtils.getConnection()
				.prepareStatement("insert into staff_historico VALUES (?, ?, ?, ?, ?, ?)");

		preparedStatement.setInt(1, id_Staff);
		preparedStatement.setString(2, campo);
		preparedStatement.setString(3, valorAntigo);
		preparedStatement.setString(4, valorNovo);
		Date date = new Date();
		preparedStatement.setString(5, String.valueOf(new Timestamp(date.getTime())));
		preparedStatement.setInt(6, parmIdUtilizador);
		preparedStatement.executeUpdate();
	}

}
