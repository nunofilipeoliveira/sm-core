package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import sm.core.data.Torneio_jogo;

public class Torneio_jogoHelper {

    public static String getTableName() {
        return "torneio_jogo";
    }

    public ArrayList<Torneio_jogo> loadAllGames() {
        ArrayList<Torneio_jogo> games = new ArrayList<>();
        // lógica para carregar todos os jogos do banco de dados

        DBUtils dbUtils = new DBUtils();

        try {
            PreparedStatement preparedStatement = dbUtils.getConnection()
                    .prepareStatement("select *From torneio_jogo order by id ");

            ResultSet rs = preparedStatement.executeQuery();

            if (rs == null) {
                return null;
            }

            Torneio_jogo jogo = null;

            while (rs.next()) {

                jogo = new Torneio_jogo();
                jogo.setId(rs.getInt("id"));
                jogo.setCourt(rs.getInt("court"));
                jogo.setTime(rs.getString("time"));
                jogo.setHomeTeamId(rs.getInt("homeTeamId"));
                jogo.setAwayTeamId(rs.getInt("awayTeamId"));
                jogo.setHomeTeam(rs.getString("homeTeam"));
                jogo.setAwayTeam(rs.getString("awayTeam"));
                jogo.setStatus(rs.getString("status"));
                jogo.setResult(rs.getString("result"));
                jogo.setRound(rs.getString("round"));
                jogo.setGoalsHomeTeam(rs.getInt("goalsHomeTeam"));
                jogo.setGoalsAwayTeam(rs.getInt("goalsAwayTeam"));

                games.add(jogo);

            }

            dbUtils.closeConnection(preparedStatement.getConnection());

        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return games;
    }

    public boolean saveMatch(Torneio_jogo match) {
        // lógica para salvar o jogo no banco de dados
        DBUtils dbUtils = new DBUtils();
        try {
            PreparedStatement preparedStatement = dbUtils.getConnection()
                    .prepareStatement(
                            "UPDATE torneio_jogo SET court = ?, time = ?, homeTeamId = ?, awayTeamId = ?, homeTeam = ?, awayTeam = ?, goalsHomeTeam = ?, goalsAwayTeam = ?, status = ?, result = ?, round = ? WHERE id = ?");

            preparedStatement.setInt(1, match.getCourt());
            preparedStatement.setString(2, match.getTime());
            preparedStatement.setInt(3, match.getHomeTeamId());
            preparedStatement.setInt(4, match.getAwayTeamId());
            preparedStatement.setString(5, match.getHomeTeam());
            preparedStatement.setString(6, match.getAwayTeam());
            preparedStatement.setInt(7, match.getGoalsHomeTeam());
            preparedStatement.setInt(8, match.getGoalsAwayTeam());
            preparedStatement.setString(9, match.getStatus());
            preparedStatement.setString(10,
                    String.valueOf(match.getGoalsHomeTeam()) + "-" + String.valueOf(match.getGoalsAwayTeam()));
            preparedStatement.setString(11, match.getRound());
            preparedStatement.setInt(12, match.getId());

            int rowsAffected = preparedStatement.executeUpdate();

            dbUtils.closeConnection(preparedStatement.getConnection());

            // vai realizar a ação definida para a equipa vencedora e vencida se o jogo
            // estiver
            // concluído
            if (match.getStatus().equals("completed") == false) {
                return true;
            }

            // Home vitoria

            if (match.getGoalsHomeTeam() > match.getGoalsAwayTeam()) {

                preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "update torneio_jogo set homeTeamId=?, homeTeam=?  where id= (select  SUBSTRING_INDEX(vitory_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(vitory_action, '.', -1)='H' )");

                preparedStatement.setInt(1, match.getHomeTeamId());
                preparedStatement.setString(2, match.getHomeTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
                dbUtils.closeConnection(preparedStatement.getConnection());

                preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "update torneio_jogo set awayTeamId=?, awayTeam=?  where id= (select  SUBSTRING_INDEX(vitory_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(vitory_action, '.', -1)='A' )");

                preparedStatement.setInt(1, match.getHomeTeamId());
                preparedStatement.setString(2, match.getHomeTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
                dbUtils.closeConnection(preparedStatement.getConnection());

                // Derrota away

                preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "update torneio_jogo set homeTeamId=?, homeTeam=?  where id= (select  SUBSTRING_INDEX(lose_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(lose_action, '.', -1)='H' )");

                preparedStatement.setInt(1, match.getAwayTeamId());
                preparedStatement.setString(2, match.getAwayTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
                dbUtils.closeConnection(preparedStatement.getConnection());

                // Away
                preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "update torneio_jogo set awayTeamId=?, awayTeam=?  where id= (select  SUBSTRING_INDEX(lose_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(lose_action, '.', -1)='A' )");

                preparedStatement.setInt(1, match.getAwayTeamId());
                preparedStatement.setString(2, match.getAwayTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
                dbUtils.closeConnection(preparedStatement.getConnection());

            } else {

                preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "update torneio_jogo set homeTeamId=?, homeTeam=?  where id= (select  SUBSTRING_INDEX(vitory_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(vitory_action, '.', -1)='H' )");

                preparedStatement.setInt(1, match.getAwayTeamId());
                preparedStatement.setString(2, match.getAwayTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
                dbUtils.closeConnection(preparedStatement.getConnection());

                preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "update torneio_jogo set awayTeamId=?, awayTeam=?  where id= (select  SUBSTRING_INDEX(vitory_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(vitory_action, '.', -1)='A' )");

                preparedStatement.setInt(1, match.getAwayTeamId());
                preparedStatement.setString(2, match.getAwayTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
                dbUtils.closeConnection(preparedStatement.getConnection());

                // Derrota away

                preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "update torneio_jogo set homeTeamId=?, homeTeam=?  where id= (select  SUBSTRING_INDEX(lose_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(lose_action, '.', -1)='H' )");

                preparedStatement.setInt(1, match.getHomeTeamId());
                preparedStatement.setString(2, match.getHomeTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
                dbUtils.closeConnection(preparedStatement.getConnection());

                // Away
                preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "update torneio_jogo set awayTeamId=?, awayTeam=?  where id= (select  SUBSTRING_INDEX(lose_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(lose_action, '.', -1)='A' )");

                preparedStatement.setInt(1, match.getHomeTeamId());
                preparedStatement.setString(2, match.getHomeTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
                dbUtils.closeConnection(preparedStatement.getConnection());

            }

            return true;

        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return false;
    }

    public boolean resetMatch(int matchId) {
        // lógica para resetar o jogo no banco de dados
        DBUtils dbUtils = new DBUtils();
        try {
            PreparedStatement preparedStatement = dbUtils.getConnection()
                    .prepareStatement(
                            "UPDATE torneio_jogo SET goalsHomeTeam = 0, goalsAwayTeam = 0, status = 'scheduled', result = '', hometeamid=0, awayteamid=0, hometeam=hometeamdefault, awayteam=awayteamdefault WHERE id = ?");

            preparedStatement.setInt(1, matchId);

            int rowsAffected = preparedStatement.executeUpdate();

            dbUtils.closeConnection(preparedStatement.getConnection());

            return rowsAffected > 0;

        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return false;
    }

}
