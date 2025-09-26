package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

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

}
