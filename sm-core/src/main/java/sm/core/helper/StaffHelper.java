package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

import org.springframework.stereotype.Component;

import sm.core.data.ElementoSeleccao;
import sm.core.data.StaffData;

@Component
public class StaffHelper {

	private final DBUtils dbUtils;

	public StaffHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;
	}

	public StaffData getStaffByID(int parmId) {

		
		StaffData staffData = null;
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement(
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

			dbUtils.closeConnection(conn);
			return staffData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean updateStaff(StaffData parmStaff, int parmIdUtilizador) {

		
		StaffData staffData = new StaffData();

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn
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
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Nome", staffData.getNome(),
						parmStaff.getNome());

			}

			if (!parmStaff.getCodigo_postal().equals(staffData.getCodigo_postal())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Código Postal",
						parmStaff.getCodigo_postal(), staffData.getCodigo_postal());
			}
			if (parmStaff.getData_nascimento() != staffData.getData_nascimento()) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Data Nascimento",
						String.valueOf(staffData.getData_nascimento()), String.valueOf(parmStaff.getData_nascimento()));
			}
			if (!parmStaff.getEmail().equals(staffData.getEmail())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Email", staffData.getEmail(),
						parmStaff.getEmail());
			}
			if (!parmStaff.getMorada().equals(staffData.getMorada())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Morada", staffData.getMorada(),
						parmStaff.getMorada());
			}

			if (!parmStaff.getNome_completo().equals(staffData.getNome_completo())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Nome Completo",
						staffData.getNome_completo(), parmStaff.getNome_completo());
			}

			if (!parmStaff.getTelemovel().equals(staffData.getTelemovel())) {
				registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Telemovel",
						staffData.getTelemovel(), parmStaff.getTelemovel());
			}

			// Atualiza a informação

			preparedStatement = conn
					.prepareStatement("update\r\n" + "	staff \r\n" + "set\r\n" + "	nome =? ,\r\n"
							+ "	data_nascimento =? ,\r\n" + "	email =? ,\r\n" + "	telemovel =? ,\r\n"
							+ "	morada =? ,\r\n" + "	codigo_postal =? ,\r\n" + "	nome_completo =? \r\n" + "where\r\n"
							+ "	ID =?");

			preparedStatement.setString(1, parmStaff.getNome());
			preparedStatement.setInt(2, parmStaff.getData_nascimento());
			preparedStatement.setString(3, parmStaff.getEmail());
			preparedStatement.setString(4, parmStaff.getTelemovel());
			preparedStatement.setString(5, parmStaff.getMorada());
			preparedStatement.setString(6, parmStaff.getCodigo_postal());
			preparedStatement.setString(7, parmStaff.getNome_completo());
			preparedStatement.setInt(8, parmStaff.getId());
			preparedStatement.executeUpdate();

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public boolean addStaff(StaffData parmStaff, int parmTenantID, int parmIdUtilizador) {

		
		StaffData staffData = new StaffData();

		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement(
					"INSERT INTO staff(nome,nome_completo,telemovel,email,morada,codigo_postal,data_nascimento,id_jogador, estado,Tenant_id) VALUES\r\n"
							+ "	 (?,?,?,?,?,?,?,0,1,?)",
					Statement.RETURN_GENERATED_KEYS);

			preparedStatement.setString(1, parmStaff.getNome());
			preparedStatement.setString(2, parmStaff.getNome_completo());
			preparedStatement.setString(3, parmStaff.getTelemovel());
			preparedStatement.setString(4, parmStaff.getEmail());
			preparedStatement.setString(5, parmStaff.getMorada());
			preparedStatement.setString(6, parmStaff.getCodigo_postal());
			preparedStatement.setInt(7, parmStaff.getData_nascimento());
			preparedStatement.setInt(8, parmTenantID);
			int affectedRows = preparedStatement.executeUpdate();

			// Verifica se a inserção foi bem-sucedida
			if (affectedRows > 0) {
				// Obtém as chaves geradas
				try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
					if (generatedKeys.next()) {
						int generatedId = generatedKeys.getInt(1); // Obtém o ID gerado
						System.out.println("ID gerado: " + generatedId);

						parmStaff.setId(generatedId);

						// Realiza a comparação campo a campo e regita no histórico.

						registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Nome", staffData.getNome(),
								parmStaff.getNome());

						registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Código Postal",
								parmStaff.getCodigo_postal(), staffData.getCodigo_postal());

						registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Data Nascimento",
								String.valueOf(staffData.getData_nascimento()),
								String.valueOf(parmStaff.getData_nascimento()));

						registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Email",
								staffData.getEmail(), parmStaff.getEmail());

						registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Morada",
								staffData.getMorada(), parmStaff.getMorada());

						registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Nome Completo",
								staffData.getNome_completo(), parmStaff.getNome_completo());

						registaHistoricoStaff(parmIdUtilizador, parmStaff.getId(), conn, "Telemovel",
								staffData.getTelemovel(), parmStaff.getTelemovel());

					} else {
						throw new SQLException("Falha ao obter o ID gerado.");
					}
				}
			}

			dbUtils.closeConnection(conn);

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	private void registaHistoricoStaff(int parmIdUtilizador, int id_Staff, Connection conn, String campo,
			String valorAntigo, String valorNovo) throws SQLException {
		PreparedStatement preparedStatement;
		preparedStatement = conn.prepareStatement("insert into staff_historico VALUES (?, ?, ?, ?, ?, ?)");

		preparedStatement.setInt(1, id_Staff);
		preparedStatement.setString(2, campo);
		preparedStatement.setString(3, valorAntigo);
		preparedStatement.setString(4, valorNovo);
		Date date = new Date();
		preparedStatement.setString(5, String.valueOf(new Timestamp(date.getTime())));
		preparedStatement.setInt(6, parmIdUtilizador);
		preparedStatement.executeUpdate();
	}

	public ArrayList<ElementoSeleccao> getAllStaffAtivo(int parmTenantID) {
	
		
		ElementoSeleccao staff = null;
		ArrayList<ElementoSeleccao> staffDisponiveis = new ArrayList<ElementoSeleccao>();
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select id, nome from staff where ESTADO='1' and TENANT_ID=? and id_jogador =0\r\n" + "union\r\n"
							+ "select s.id, j.nome from staff s \r\n" + "inner join jogador j on s.id_jogador =j.id\r\n"
							+ "where s.ESTADO='1' and s.TENANT_ID=? and s.id_jogador <>0\r\n" + "order by 1 ");
	
			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setInt(2, parmTenantID);
			ResultSet rs = preparedStatement.executeQuery();
	
			if (rs == null) {
				return null;
			}
	
			while (rs.next()) {
	
				staff = new ElementoSeleccao(rs.getInt("id"), rs.getString("nome"), "");
				staffDisponiveis.add(staff);
	
			}

			dbUtils.closeConnection(conn);
			return staffDisponiveis;
	
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		return null;
	}

	public ArrayList<ElementoSeleccao> getAllStaffAtivoDisponivel(int parmTenantID, int parmIDEquipa) {
	
		
		ElementoSeleccao staff = null;
		ArrayList<ElementoSeleccao> staffDisponiveis = new ArrayList<ElementoSeleccao>();
		try {
			Connection conn = dbUtils.getConnection();
			PreparedStatement preparedStatement = conn.prepareStatement("select * from (\r\n"
					+ "select id, nome from staff where ESTADO='1' and TENANT_ID=? and id_jogador =0\r\n" + "union\r\n"
					+ "select s.id, j.nome from staff s\r\n" + "inner join jogador j on s.id_jogador =j.id\r\n"
					+ "where s.ESTADO='1' and s.TENANT_ID=? and s.id_jogador <>0\r\n" + "order by 1 \r\n" + ") a \r\n"
					+ "where \r\n" + "not exists (select * from escalao_epoca_staff X \r\n" + "where \r\n"
					+ "X.id_staff  = a.id  AND\r\n" + "X.id_escalao_epoca = ?)");
	
			preparedStatement.setInt(1, parmTenantID);
			preparedStatement.setInt(2, parmTenantID);
			preparedStatement.setInt(3, parmIDEquipa);
			ResultSet rs = preparedStatement.executeQuery();
	
			if (rs == null) {
				return null;
			}
	
			while (rs.next()) {
	
				staff = new ElementoSeleccao(rs.getInt("id"), rs.getString("nome"), "");
				staffDisponiveis.add(staff);
	
			}
	
			dbUtils.closeConnection(conn);
			return staffDisponiveis;
	
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		return null;
	}

}
