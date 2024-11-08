package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

import sm.core.data.ContadorPresencaData;
import sm.core.data.FichaJogadorPresencasData;
import sm.core.data.JogadorData;
import sm.core.data.JogadorSeleccao;

public class JogadorHelper {

	public JogadorHelper() {

	}

	public JogadorData getJogadorbyID(int parmId) {

		DBUtils dbUtils = new DBUtils();
		JogadorData jogadorData = null;
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("SELECT * FROM JOGADOR WHERE ID=?");

			preparedStatement.setInt(1, parmId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {
				if (jogadorData == null) {
					jogadorData = new JogadorData(rs.getInt("id"), rs.getString("nome"), rs.getString("nome_completo"),
							rs.getInt("data_nascimento"), rs.getString("email"), rs.getString("telemovel"),
							rs.getString("pai_nome"), rs.getString("pai_email"), rs.getString("pai_telemovel"),
							rs.getString("mae_nome"), rs.getString("mae_email"), rs.getString("mae_telemovel"),
							rs.getString("morada"), rs.getString("cidade"), rs.getString("codigo_postal"),
							rs.getString("observacoes"), rs.getString("numero"), rs.getString("CC"), rs.getInt("NIF"),
							rs.getInt("licença"));
				}

			}

			dbUtils.closeConnection();
			return jogadorData;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<FichaJogadorPresencasData> getFaltasByJogador(int parmId) {

		DBUtils dbUtils = new DBUtils();
		ArrayList<FichaJogadorPresencasData> faltas = new ArrayList<FichaJogadorPresencasData>();
		FichaJogadorPresencasData tmpFalta = null;

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement(
					"select j.id, j.nome , ee.id, ee.nome , pj.estado , pj.motivo , p.`data` , p.hora from jogador j \r\n"
							+ "inner join presenca_jogador pj on j.id=pj.id_jogador \r\n"
							+ "inner join presencas p on pj.id_presenca =p.id\r\n"
							+ "inner join escalao_epoca ee on ee.id_escalao =p.id_equipa\r\n"
							+ "inner join epoca e on e.id=ee.id_epoca \r\n"
							+ "where e.Estado =1 and pj.estado like 'Ausente%'\r\n" + "and j.id=?\r\n"
							+ "order by data, hora");

			preparedStatement.setInt(1, parmId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				tmpFalta = new FichaJogadorPresencasData(rs.getInt("j.id"), rs.getString("j.nome"), rs.getInt("ee.id"),
						rs.getString("ee.nome"), rs.getString("pj.estado"), rs.getString("pj.motivo"),
						rs.getInt("p.data"), rs.getString("p.hora"));
				faltas.add(tmpFalta);

			}

			dbUtils.closeConnection();
			return faltas;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<ContadorPresencaData> getPresencasByJogador(int parmId) {

		DBUtils dbUtils = new DBUtils();
		ArrayList<ContadorPresencaData> presencaPorEscalao = new ArrayList<ContadorPresencaData>();
		ContadorPresencaData tmpContadorPorEscalao = null;

		try {
			PreparedStatement preparedStatement = dbUtils.getConnection().prepareStatement(
					"select id_jogador, ee.nome, mid(data, 5, 2) mes, count(*) from presenca_jogador pj\r\n"
							+ "inner join presencas p ON P.id =PJ.id_presenca\r\n"
							+ "inner join escalao_epoca ee on EE.ID=P.id_equipa \r\n" + "where id_jogador =?\r\n"
							+ "and estado='Presente'\r\n" + "group by ee.nome, mid(data, 5, 2)\r\n"
							+ "order by 1, 2, 3");

			preparedStatement.setInt(1, parmId);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			int tmpMes = 0;

			while (rs.next()) {
				tmpMes = 0;
				if (tmpContadorPorEscalao == null) {
					tmpContadorPorEscalao = new ContadorPresencaData(rs.getInt(1), rs.getString(2), 0, 0, 0, 0, 0, 0, 0,
							0, 0, 0, 0);
				}
				if (!tmpContadorPorEscalao.getEscalao().equals(rs.getString(2))) {
					presencaPorEscalao.add(tmpContadorPorEscalao);
					tmpContadorPorEscalao = new ContadorPresencaData(rs.getInt(1), rs.getString(2), 0, 0, 0, 0, 0, 0, 0,
							0, 0, 0, 0);
				}

				tmpMes = rs.getInt(3);
				switch (tmpMes) {
				case 1: {
					tmpContadorPorEscalao.setJan(rs.getInt(4));
					break;
				}
				case 2: {
					tmpContadorPorEscalao.setFev(rs.getInt(4));
					break;
				}
				case 3: {
					tmpContadorPorEscalao.setMar(rs.getInt(4));
					break;
				}
				case 4: {
					tmpContadorPorEscalao.setAbr(rs.getInt(4));
					break;
				}
				case 5: {
					tmpContadorPorEscalao.setMai(rs.getInt(4));
					break;
				}
				case 6: {
					tmpContadorPorEscalao.setJun(rs.getInt(4));
					break;
				}
				case 7: {
					tmpContadorPorEscalao.setJul(rs.getInt(4));
					break;
				}
				case 9: {
					tmpContadorPorEscalao.setSet(rs.getInt(4));
					break;
				}
				case 10: {
					tmpContadorPorEscalao.setOut(rs.getInt(4));
					break;
				}
				case 11: {
					tmpContadorPorEscalao.setNov(rs.getInt(4));
					break;
				}
				case 12: {
					tmpContadorPorEscalao.setDez(rs.getInt(4));
					break;
				}
				default:
					throw new IllegalArgumentException("Unexpected value: " + tmpMes);
				}

			}
			presencaPorEscalao.add(tmpContadorPorEscalao);

			dbUtils.closeConnection();
			return presencaPorEscalao;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public ArrayList<JogadorSeleccao> getJogadorDisponiveis(int parmIdEscalao) {

		DBUtils dbUtils = new DBUtils();
		JogadorSeleccao jogador = null;
		ArrayList<JogadorSeleccao> jogadoresDisponiveis = new ArrayList<JogadorSeleccao>();
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("select j.id, j.nome, ee.nome  from jogador j \r\n"
							+ "inner join escalao_epoca_jogador eej on J.id =EEJ.id_jogador \r\n"
							+ "inner join escalao_epoca ee ON EE.id_escalao =EEJ.id_escalao_epoca\r\n"
							+ "inner join epoca e on e.id =ee.id_epoca \r\n" + "where EEJ.id_escalao_epoca <>?\r\n"
							+ "and e.Estado =1");

			preparedStatement.setInt(1, parmIdEscalao);
			ResultSet rs = preparedStatement.executeQuery();

			if (rs == null) {
				return null;
			}

			while (rs.next()) {

				jogador = new JogadorSeleccao(rs.getInt("j.id"), rs.getString("j.nome"), rs.getString("ee.nome"));
				jogadoresDisponiveis.add(jogador);

			}

			dbUtils.closeConnection();
			return jogadoresDisponiveis;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	public boolean updateJogador(JogadorData parmJogador, int parmIdUtilizador) {

		DBUtils dbUtils = new DBUtils();
		JogadorData jogadorData = new JogadorData();
		try {
			PreparedStatement preparedStatement = dbUtils.getConnection()
					.prepareStatement("SELECT * FROM JOGADOR WHERE ID=?");

			preparedStatement.setInt(1, parmJogador.getId());
			ResultSet rs = preparedStatement.executeQuery();

			while (rs.next()) {
				jogadorData = new JogadorData(rs.getInt("id"), rs.getString("nome"), rs.getString("nome_completo"),
						rs.getInt("data_nascimento"), rs.getString("email"), rs.getString("telemovel"),
						rs.getString("pai_nome"), rs.getString("pai_email"), rs.getString("pai_telemovel"),
						rs.getString("mae_nome"), rs.getString("mae_email"), rs.getString("mae_telemovel"),
						rs.getString("morada"), rs.getString("cidade"), rs.getString("codigo_postal"),
						rs.getString("observacoes"), rs.getString("numero"), rs.getString("CC"), rs.getInt("NIF"),
						rs.getInt("licença"));

			}

			// Realiza a comparação campo a campo e regita no histórico.
			if (!parmJogador.getNome().equals(jogadorData.getNome())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Nome", jogadorData.getNome(),
						parmJogador.getNome());

			}
			if (!parmJogador.getCC().equals(jogadorData.getCC())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "CC", jogadorData.getCC(),
						parmJogador.getCC());
			}
			if (!parmJogador.getCidade().equals(jogadorData.getCidade())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Cidade", jogadorData.getCidade(),
						parmJogador.getCidade());
			}
			if (!parmJogador.getCodigo_postal().equals(jogadorData.getCodigo_postal())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Código Postal",
						jogadorData.getCodigo_postal(), parmJogador.getCodigo_postal());
			}
			if (parmJogador.getData_nascimento() != jogadorData.getData_nascimento()) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Data Nascimento",
						String.valueOf(jogadorData.getData_nascimento()),
						String.valueOf(parmJogador.getData_nascimento()));
			}
			if (!parmJogador.getEmail().equals(jogadorData.getEmail())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Email", jogadorData.getEmail(),
						parmJogador.getEmail());
			}
			if (parmJogador.getLicenca() != jogadorData.getLicenca()) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Licença",
						String.valueOf(jogadorData.getLicenca()), String.valueOf(parmJogador.getLicenca()));
			}
			if (!parmJogador.getMae_email().equals(jogadorData.getMae_email())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Mãe - Email",
						jogadorData.getMae_email(), parmJogador.getMae_email());
			}
			if (!parmJogador.getMae_nome().equals(jogadorData.getMae_nome())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Mãe - Nome",
						jogadorData.getMae_nome(), parmJogador.getMae_nome());
			}
			if (!parmJogador.getMae_telemovel().equals(jogadorData.getMae_telemovel())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Mãe - Nome",
						jogadorData.getMae_telemovel(), parmJogador.getMae_telemovel());
			}
			if (!parmJogador.getPai_email().equals(jogadorData.getPai_email())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Pai - Email",
						jogadorData.getPai_email(), parmJogador.getPai_email());
			}
			if (!parmJogador.getPai_nome().equals(jogadorData.getPai_nome())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Pai - Nome",
						jogadorData.getPai_nome(), parmJogador.getPai_nome());
			}
			if (!parmJogador.getPai_telemovel().equals(jogadorData.getPai_telemovel())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Pai - Nome",
						jogadorData.getPai_telemovel(), parmJogador.getPai_telemovel());
			}
			if (!parmJogador.getMorada().equals(jogadorData.getMorada())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Morada", jogadorData.getMorada(),
						parmJogador.getMorada());
			}
			if (parmJogador.getNIF() != jogadorData.getNIF()) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "NIF",
						String.valueOf(jogadorData.getNIF()), String.valueOf(parmJogador.getNIF()));
			}
			if (!parmJogador.getNome_completo().equals(jogadorData.getNome_completo())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Nome Completo",
						jogadorData.getNome_completo(), parmJogador.getNome_completo());
			}
			if (!parmJogador.getNumero().equals(jogadorData.getNumero())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Numero", jogadorData.getNumero(),
						parmJogador.getNumero());
			}
			if (!parmJogador.getObservacoes().equals(jogadorData.getObservacoes())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Observações",
						jogadorData.getObservacoes(), parmJogador.getObservacoes());
			}
			if (!parmJogador.getTelemovel().equals(jogadorData.getTelemovel())) {
				registaHistorico(parmIdUtilizador, parmJogador.getId(), dbUtils, "Telemovel",
						jogadorData.getTelemovel(), parmJogador.getTelemovel());
			}

			// Atualiza a informação

			preparedStatement = dbUtils.getConnection()
					.prepareStatement("update\r\n" + "	jogador\r\n" + "set\r\n" + "	nome =? ,\r\n"
							+ "	data_nascimento =? ,\r\n" + "	email =? ,\r\n" + "	telemovel =? ,\r\n"
							+ "	pai_nome =? ,\r\n" + "	pai_email =? ,\r\n" + "	pai_telemovel =? ,\r\n"
							+ "	mae_nome =? ,\r\n" + "	mae_email =? ,\r\n" + "	mae_telemovel =? ,\r\n"
							+ "	morada =? ,\r\n" + "	cidade =? ,\r\n" + "	codigo_postal =? ,\r\n"
							+ "	observacoes =? ,\r\n" + "	numero =? ,\r\n" + "	nome_completo =? ,\r\n"
							+ "	NIF =? ,\r\n" + "	CC =? ,\r\n" + "	Licença =?\r\n" + "where\r\n" + "	ID =?");

			preparedStatement.setString(1, parmJogador.getNome());
			preparedStatement.setInt(2, parmJogador.getData_nascimento());
			preparedStatement.setString(3, parmJogador.getEmail());
			preparedStatement.setString(4, parmJogador.getTelemovel());
			preparedStatement.setString(5, parmJogador.getPai_nome());
			preparedStatement.setString(6, parmJogador.getPai_email());
			preparedStatement.setString(7, parmJogador.getPai_telemovel());
			preparedStatement.setString(8, parmJogador.getMae_nome());
			preparedStatement.setString(9, parmJogador.getMae_email());
			preparedStatement.setString(10, parmJogador.getMae_telemovel());
			preparedStatement.setString(11, parmJogador.getMorada());
			preparedStatement.setString(12, parmJogador.getCidade());
			preparedStatement.setString(13, parmJogador.getCodigo_postal());
			preparedStatement.setString(14, parmJogador.getObservacoes());
			preparedStatement.setString(15, parmJogador.getNumero());
			preparedStatement.setString(16, parmJogador.getNome_completo());
			preparedStatement.setInt(17, parmJogador.getNIF());
			preparedStatement.setString(18, parmJogador.getCC());
			preparedStatement.setInt(19, parmJogador.getLicenca());
			preparedStatement.setInt(20, parmJogador.getId());
			preparedStatement.executeUpdate();

			dbUtils.closeConnection();

			return true;

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	private void registaHistorico(int parmIdUtilizador, int id_Jogador, DBUtils dbUtils, String campo,
			String valorAntigo, String valorNovo) throws SQLException {
		PreparedStatement preparedStatement;
		preparedStatement = dbUtils.getConnection()
				.prepareStatement("insert into jogador_historico VALUES (?, ?, ?, ?, ?, ?)");

		preparedStatement.setInt(1, id_Jogador);
		preparedStatement.setString(2, campo);
		preparedStatement.setString(3, valorAntigo);
		preparedStatement.setString(4, valorNovo);
		Date date = new Date();
		preparedStatement.setString(5, String.valueOf(new Timestamp(date.getTime())));
		preparedStatement.setInt(6, parmIdUtilizador);
		preparedStatement.executeUpdate();
	}

}
