package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import sm.core.data.PresencaData;

public class PresencaHelper {

	public PresencaHelper() {

	}

	public ArrayList<PresencaData> loadPresencasbyData(int DataInicio, int DataFim, int equipaID) {

		DBUtils dbUtils = new DBUtils();
		PresencaData presencaData = null;
		ArrayList<PresencaData> presencas = new ArrayList<PresencaData>();

		try {
//			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement("select\r\n" + "	*\r\n"
//					+ "	from PRESENCAS P\r\n" + "inner join presenca_jogador pj on\r\n" + "	PJ.id_presenca = P.id\r\n"
//					+ "inner join escalao_epoca ee on\r\n" + "	ee.id = p.id_equipa\r\n"
//					+ "inner join utilizadores u on\r\n" + "	u.id = p.utilizador_criacao\r\n"
//					+ "inner join jogador j on\r\n" + "	j.id = pj.id_jogador\r\n" + "where\r\n" + "	ee.id =?\r\n"
//					+ "	and \r\n" + "	((?=1 and ?=1) or \r\n" + "	P.`data` between ? and ?)\r\n" + "order by\r\n"
//					+ "	p.data,\r\n" + "	p.hora,\r\n" + "	p.id ");

			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement("select * FROM(\r\n"
					+ "select p.id, p.data, p.hora, p.id_equipa , ee.nome as NOMEEQUIPA, p.datacriacao , p.utilizador_criacao , u.nome as NOMEUTILIZADOR, pj.id_jogador as id_Item, j.nome, pj.estado, pj.motivo, 'J' as TIPO\r\n"
					+ "from PRESENCAS P inner join presenca_jogador pj on PJ.id_presenca = P.id\r\n"
					+ "inner join escalao_epoca ee on 	ee.id = p.id_equipa\r\n"
					+ "inner join utilizadores u on 	u.id = p.utilizador_criacao\r\n"
					+ "inner join jogador j on j.id = pj.id_jogador where 	ee.id =?\r\n"
					+ "and  ((?=1 and ?=1) or  P.`data` between ? and ?) \r\n" + "union\r\n"
					+ "select  p.id, p.data, p.hora, p.id_equipa , ee.nome as NOMEEQUIPA, p.datacriacao , p.utilizador_criacao , u.nome as NOMEUTILIZADOR, ps.id_staff  as id_Item, j.nome, ps.estado, ps.motivo, 'S' as TIPO \r\n"
					+ "from PRESENCAS P inner join presenca_staff ps on Ps.id_presenca = P.id\r\n"
					+ "inner join escalao_epoca ee on 	ee.id = p.id_equipa\r\n"
					+ "inner join utilizadores u on 	u.id = p.utilizador_criacao\r\n"
					+ "inner join staff j on j.id = ps.id_staff where 	ee.id =? and j.id_jogador=0\r\n"
					+ "and  ((?=1 and ?=1) or  P.`data` between ? and ?) \r\n" + "union\r\n"
					+ "select  p.id, p.data, p.hora, p.id_equipa , ee.nome as NOMEEQUIPA, p.datacriacao , p.utilizador_criacao , u.nome as NOMEUTILIZADOR, ps.id_staff  as id_Item, j.nome, ps.estado, ps.motivo, 'S' as TIPO \r\n"
					+ "from PRESENCAS P inner join presenca_staff ps on Ps.id_presenca = P.id\r\n"
					+ "inner join escalao_epoca ee on 	ee.id = p.id_equipa\r\n"
					+ "inner join utilizadores u on 	u.id = p.utilizador_criacao\r\n"
					+ "inner join staff s on s.id = ps.id_staff \r\n"
					+ "inner join jogador j on j.id = s.id_jogador where 	ee.id =? and s.id_jogador<>0\r\n"
					+ "and  ((?=1 and ?=1) or  P.`data` between ? and ?) \r\n" + ") A \r\n"
					+ "order by data, hora, id, TIPO");

			preparedStatement.setInt(1, equipaID);
			preparedStatement.setInt(2, DataInicio);
			preparedStatement.setInt(3, DataFim);
			preparedStatement.setInt(4, DataInicio);
			preparedStatement.setInt(5, DataFim);

			preparedStatement.setInt(6, equipaID);
			preparedStatement.setInt(7, DataInicio);
			preparedStatement.setInt(8, DataFim);
			preparedStatement.setInt(9, DataInicio);
			preparedStatement.setInt(10, DataFim);

			preparedStatement.setInt(11, equipaID);
			preparedStatement.setInt(12, DataInicio);
			preparedStatement.setInt(13, DataFim);
			preparedStatement.setInt(14, DataInicio);
			preparedStatement.setInt(15, DataFim);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				if (presencaData == null) {
					presencaData = new PresencaData(rs.getInt("id"), rs.getInt("data"), rs.getString("hora"),
							rs.getInt("id_equipa"), rs.getString("NOMEEQUIPA"), rs.getString("datacriacao"),
							rs.getInt("utilizador_criacao"), rs.getString("NOMEUTILIZADOR"));

				} else {
					if (presencaData.getId() != rs.getInt("id")) {
						presencas.add(presencaData);
						presencaData = new PresencaData(rs.getInt("id"), rs.getInt("data"), rs.getString("hora"),
								rs.getInt("id_equipa"), rs.getString("NOMEEQUIPA"), rs.getString("datacriacao"),
								rs.getInt("utilizador_criacao"), rs.getString("NOMEUTILIZADOR"));

					}

				}

				if (rs.getString("Tipo").equals("J")) {
					presencaData.addJogador(rs.getInt("id_Item"), rs.getString("nome"),
							rs.getString("estado"), rs.getString("motivo"));
				}

				if (rs.getString("Tipo").equals("S")) {
					presencaData.addStaff(rs.getInt("id_Item"), rs.getString("nome"), rs.getString("estado"),
							rs.getString("motivo"));
				}

			}

			presencas.add(presencaData);

			dbUtils.closeConnection();
			return presencas;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public PresencaData loadPresencasbyID(int id) {

		DBUtils dbUtils = new DBUtils();
		PresencaData presencaData = null;

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select\r\n" + "	p.id,\r\n" + "	data,\r\n" + "	hora,\r\n" + "	id_equipa,\r\n"
							+ "	ee.nome,\r\n" + "	datacriacao,\r\n" + "	utilizador_criacao,\r\n" + "	u.nome,\r\n"
							+ "	pj.id_jogador,\r\n" + "	j.nome,\r\n" + "	pj.estado,\r\n" + "	pj.motivo\r\n"
							+ "from\r\n" + "	PRESENCAS P\r\n" + "inner join presenca_jogador pj on\r\n"
							+ "	PJ.id_presenca = P.id\r\n" + "inner join escalao_epoca ee on\r\n"
							+ "	ee.id = p.id_equipa\r\n" + "inner join utilizadores u on\r\n"
							+ "	u.id = p.utilizador_criacao\r\n" + "inner join jogador j on\r\n"
							+ "	j.id = pj.id_jogador\r\n" + "where\r\n" + "	p.id = ? order by p.id");

			preparedStatement.setInt(1, id);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}
			presencaData = null;
			while (rs.next()) {
				if (presencaData == null) {
					presencaData = new PresencaData(rs.getInt("id"), rs.getInt("data"), rs.getString("hora"),
							rs.getInt("id_equipa"), rs.getString("ee.nome"), rs.getString("datacriacao"),
							rs.getInt("utilizador_criacao"), rs.getString("u.nome"));

				}

				presencaData.addJogador(rs.getInt("pj.id_jogador"), rs.getString("j.nome"), rs.getString("pj.estado"),
						rs.getString("pj.motivo"));
			}

			preparedStatement = dbUtils.getConnection()
					.prepareStatement("select ps.id_staff, s.nome, ps.estado, ps.motivo\r\n" + "from PRESENCAS P\r\n"
							+ "inner join presenca_staff ps on Ps.id_presenca = P.id\r\n"
							+ "inner join escalao_epoca ee on ee.id = p.id_equipa\r\n"
							+ "inner join utilizadores u on u.id = p.utilizador_criacao\r\n"
							+ "inner join staff s on s.id = ps.id_staff \r\n" + "where p.id = ? and s.id_jogador =0\r\n"
							+ "union\r\n" + "select ps.id_staff, j.nome, ps.estado, ps.motivo\r\n"
							+ "from PRESENCAS P\r\n" + "inner join presenca_staff ps on Ps.id_presenca = P.id\r\n"
							+ "inner join escalao_epoca ee on ee.id = p.id_equipa\r\n"
							+ "inner join utilizadores u on u.id = p.utilizador_criacao\r\n"
							+ "inner join staff s on s.id = ps.id_staff \r\n"
							+ "inner join jogador j on s.id_jogador =j.id \r\n"
							+ "where p.id = ? and s.id_jogador <>0");

			preparedStatement.setInt(1, id);
			preparedStatement.setInt(2, id);

			rs = preparedStatement.executeQuery();

			while (rs.next()) {
				presencaData.addStaff(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4));
			}

			dbUtils.closeConnection();
			return presencaData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean isPresencasbyEquipaDataHora(int idEquipa, int Data, String Hora) {

		DBUtils dbUtils = new DBUtils();
		PresencaData presencaData = null;

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement(
					"select *from presencas P inner join presenca_jogador pj on PJ.id_presenca =P.id \r\n"
							+ "inner join escalao_epoca ee on ee.id =p.id_equipa \r\n"
							+ "inner join utilizadores u on u.id=p.utilizador_criacao \r\n"
							+ "inner join jogador j on j.id=pj.id_jogador where id_equipa=? and data=? and hora=?\r\n"
							+ " ");

			preparedStatement.setInt(1, idEquipa);
			preparedStatement.setInt(2, Data);
			preparedStatement.setString(3, Hora);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return false;
			}

			while (rs.next()) {

				presencaData = new PresencaData(rs.getInt("id"), rs.getInt("data"), rs.getString("hora"),
						rs.getInt("id_equipa"), rs.getString("ee.nome"), rs.getString("datacriacao"),
						rs.getInt("utilizador_criacao"), rs.getString("u.nome"));

			}

			dbUtils.closeConnection();
			if (presencaData != null) {
				return true;
			} else {
				return false;
			}

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public ArrayList<String> loadHistoricoByID(int id) {

		DBUtils dbUtils = new DBUtils();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement(
					"select concat( ph.`data`, '  ',  u.nome, '   ', ph.alteracao) as hist from presencas_historico ph \r\n"
							+ "inner join utilizadores u on u.id=ph.id_utilizador \r\n" + "where ph.id_presenca =? ");

			preparedStatement.setInt(1, id);

			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			ArrayList<String> historico = new ArrayList<String>();
			String historicoItem = "";

			while (rs.next()) {
				historicoItem = rs.getString("hist");
				historico.add(historicoItem);
			}

			dbUtils.closeConnection();
			return historico;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean createPresenca(PresencaData parmPresencaData) {

		DBUtils dbUtils = new DBUtils();

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement(
					"insert into PRESENCAS(DATA, HORA, ID_EQUIPA, DATACRIACAO, UTILIZADOR_CRIACAO)\r\n"
							+ "values (?, ?, ?, NOW(), ?)",
					PreparedStatement.RETURN_GENERATED_KEYS);

			preparedStatement.setInt(1, parmPresencaData.getData());
			preparedStatement.setString(2, parmPresencaData.getHora());
			preparedStatement.setInt(3, parmPresencaData.getId_escalao());
			preparedStatement.setInt(4, parmPresencaData.getId_utilizador_criacao());
			preparedStatement.executeUpdate();

			ResultSet rs = preparedStatement.getGeneratedKeys();
			if (rs.next()) {
				parmPresencaData.setId(rs.getInt(1));

			}

			for (int i = 0; i < parmPresencaData.getJogadoresPresenca().size(); i++) {

				preparedStatement = dbUtils.getConnection()
						.prepareStatement("insert into presenca_jogador VALUES(?, ?, ?, ?)");

				preparedStatement.setInt(1, parmPresencaData.getId());
				preparedStatement.setInt(2, parmPresencaData.getJogadoresPresenca().get(i).getId_jogador());
				preparedStatement.setString(3, parmPresencaData.getJogadoresPresenca().get(i).getEstado());
				preparedStatement.setString(4, parmPresencaData.getJogadoresPresenca().get(i).getMotivo());
				preparedStatement.executeUpdate();

			}

			for (int i = 0; i < parmPresencaData.getStaffPresenca().size(); i++) {

				preparedStatement = dbUtils.getConnection()
						.prepareStatement("insert into presenca_staff VALUES(?, ?, ?, ?)");

				preparedStatement.setInt(1, parmPresencaData.getId());
				preparedStatement.setInt(2, parmPresencaData.getStaffPresenca().get(i).getid_staff());
				preparedStatement.setString(3, parmPresencaData.getStaffPresenca().get(i).getEstado());
				preparedStatement.setString(4, parmPresencaData.getStaffPresenca().get(i).getMotivo());
				preparedStatement.executeUpdate();

			}

			dbUtils.closeConnection();
			return true;

		} catch (SQLException e) {
			return false;
		}

	}

	public boolean updatePresenca(PresencaData parmPresencaData, Integer parmIdUtilizador) {

		PresencaData oldPresenca = loadPresencasbyID(parmPresencaData.getId());

		try {
			// compara se o dia e/ou hora foram alterados:
			if (oldPresenca.getData() != parmPresencaData.getData()) {
				registaHistorico(parmPresencaData.getId(), parmIdUtilizador,
						"Alteração da Data | " + oldPresenca.getData() + " => " + parmPresencaData.getData());
			}
			if (!oldPresenca.getHora().trim().equals(parmPresencaData.getHora().trim())) {
				// regista alteração da hora
				registaHistorico(parmPresencaData.getId(), parmIdUtilizador,
						"Alteração da Hora | " + oldPresenca.getHora() + " => " + parmPresencaData.getHora());
			}

			for (int i = 0; i < oldPresenca.getJogadoresPresenca().size(); i++) {

				for (int z = 0; z < parmPresencaData.getJogadoresPresenca().size(); z++) {
					if (oldPresenca.getJogadoresPresenca().get(i).getId_jogador() == parmPresencaData
							.getJogadoresPresenca().get(z).getId_jogador()) {
						// compara o estado
						if (!oldPresenca.getJogadoresPresenca().get(i).getEstado()
								.equals(parmPresencaData.getJogadoresPresenca().get(z).getEstado())) {
							// regista alteração de estado
							registaHistorico(parmPresencaData.getId(), parmIdUtilizador,
									"Alteração do estado do Jogador | "
											+ parmPresencaData.getJogadoresPresenca().get(z).getNome_jogador() + ": "
											+ oldPresenca.getJogadoresPresenca().get(i).getEstado() + " => "
											+ parmPresencaData.getJogadoresPresenca().get(z).getEstado());
						}
						if (!oldPresenca.getJogadoresPresenca().get(i).getMotivo()
								.equals(parmPresencaData.getJogadoresPresenca().get(z).getMotivo())) {
							// regista alteração de motivo
							registaHistorico(parmPresencaData.getId(), parmIdUtilizador,
									"Alteração do motivo do Jogador | "
											+ parmPresencaData.getJogadoresPresenca().get(z).getNome_jogador() + ": "
											+ oldPresenca.getJogadoresPresenca().get(i).getMotivo() + " => "
											+ parmPresencaData.getJogadoresPresenca().get(z).getMotivo());

						}
					}
				}

			}

			// STAFF

			for (int i = 0; i < oldPresenca.getStaffPresenca().size(); i++) {

				for (int z = 0; z < parmPresencaData.getStaffPresenca().size(); z++) {
					if (oldPresenca.getStaffPresenca().get(i).getid_staff() == parmPresencaData.getStaffPresenca()
							.get(z).getid_staff()) {
						// compara o estado
						if (!oldPresenca.getStaffPresenca().get(i).getEstado()
								.equals(parmPresencaData.getStaffPresenca().get(z).getEstado())) {
							// regista alteração de estado
							registaHistorico(parmPresencaData.getId(), parmIdUtilizador,
									"Alteração do estado do Staff | "
											+ parmPresencaData.getStaffPresenca().get(z).getnome_staff() + ": "
											+ oldPresenca.getStaffPresenca().get(i).getEstado() + " => "
											+ parmPresencaData.getStaffPresenca().get(z).getEstado());
						}
						if (!oldPresenca.getStaffPresenca().get(i).getMotivo()
								.equals(parmPresencaData.getStaffPresenca().get(z).getMotivo())) {
							// regista alteração de motivo
							registaHistorico(parmPresencaData.getId(), parmIdUtilizador,
									"Alteração do motivo do Jogador | "
											+ parmPresencaData.getStaffPresenca().get(z).getnome_staff() + ": "
											+ oldPresenca.getStaffPresenca().get(i).getMotivo() + " => "
											+ parmPresencaData.getStaffPresenca().get(z).getMotivo());

						}
					}
				}

			}

			
			for (int i = oldPresenca.getJogadoresPresenca().size(); i < parmPresencaData.getJogadoresPresenca().size(); i++) {
				
								
					
						// significa que é um jogador novo na ficha. Registar no histórico
						registaHistorico(parmPresencaData.getId(), parmIdUtilizador,
								"Novo Jogador:" + parmPresencaData.getJogadoresPresenca().get(i).getNome_jogador()
										+ " | Estado:" + parmPresencaData.getJogadoresPresenca().get(i).getEstado()
										+ " Motivo:" + parmPresencaData.getJogadoresPresenca().get(i).getMotivo());
						
						//insere novo jogador na presenca
						
						DBUtils dbUtils = new DBUtils();

						PreparedStatement preparedStatement = dbUtils.getConnection()
								.prepareStatement("insert into  presenca_jogador(id_presenca, id_jogador, estado, motivo) VALUES(?,?,?,?)");
						
						preparedStatement.setString(3, parmPresencaData.getJogadoresPresenca().get(i).getEstado());
						preparedStatement.setString(4, parmPresencaData.getJogadoresPresenca().get(i).getMotivo());
						preparedStatement.setInt(1, parmPresencaData.getId());
						preparedStatement.setInt(2, parmPresencaData.getJogadoresPresenca().get(i).getId_jogador());
						preparedStatement.executeUpdate();
						
							

			}

			DBUtils dbUtils = new DBUtils();

			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("update PRESENCAS set DATA=?, HORA=?\r\n" + "where id=?");

			preparedStatement.setInt(1, parmPresencaData.getData());
			preparedStatement.setString(2, parmPresencaData.getHora());
			preparedStatement.setInt(3, parmPresencaData.getId());
			preparedStatement.executeUpdate();

			for (int i = 0; i < parmPresencaData.getJogadoresPresenca().size(); i++) {

				preparedStatement = dbUtils.getConnection().prepareStatement(
						"update presenca_jogador set estado=?, motivo=? where id_presenca=? and id_jogador=?");

				preparedStatement.setString(1, parmPresencaData.getJogadoresPresenca().get(i).getEstado());
				preparedStatement.setString(2, parmPresencaData.getJogadoresPresenca().get(i).getMotivo());
				preparedStatement.setInt(3, parmPresencaData.getId());
				preparedStatement.setInt(4, parmPresencaData.getJogadoresPresenca().get(i).getId_jogador());
				preparedStatement.executeUpdate();

			}

			for (int i = 0; i < parmPresencaData.getStaffPresenca().size(); i++) {

				preparedStatement = dbUtils.getConnection().prepareStatement(
						"update presenca_staff set estado=?, motivo=? where id_presenca=? and id_staff=?");

				preparedStatement.setString(1, parmPresencaData.getStaffPresenca().get(i).getEstado());
				preparedStatement.setString(2, parmPresencaData.getStaffPresenca().get(i).getMotivo());
				preparedStatement.setInt(3, parmPresencaData.getId());
				preparedStatement.setInt(4, parmPresencaData.getStaffPresenca().get(i).getid_staff());
				preparedStatement.executeUpdate();

			}

			dbUtils.closeConnection();
			return true;

		} catch (SQLException e) {
			return false;
		}

	}

	private void registaHistorico(int idPresenca, int idUtilizador, String alteracao) throws SQLException {
		DBUtils dbUtils = new DBUtils();
		PreparedStatement preparedStatement = dbUtils.getConnection()
				.prepareStatement("insert PRESENCAS_historico values (?, now(), ?, ?)");

		preparedStatement.setInt(1, idPresenca);
		preparedStatement.setInt(2, idUtilizador);
		preparedStatement.setString(3, alteracao);
		preparedStatement.executeUpdate();
		dbUtils.closeConnection();

	}

}
