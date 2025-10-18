package sm.core.helper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.springframework.stereotype.Component;

import sm.core.data.ClubeData;

@Component
public class ClubeHelper {

    private final DBUtils dbUtils;

    public ClubeHelper(DBUtils dbUtils) {
        this.dbUtils = dbUtils;
    }

    public ClubeData getClubebyId(int parmClubeId) {

        ClubeData clube = null;

        try {
            PreparedStatement preparedStatement = dbUtils.getConnection()
                    .prepareStatement("select *from clube where id=? ");

            preparedStatement.setInt(1, parmClubeId);
            ResultSet rs = preparedStatement.executeQuery();

            if (rs == null) {
                return null;
            }

            while (rs.next()) {

                clube = new ClubeData(rs.getInt("id"), rs.getString("nome"), rs.getString("pav_nome"),
                        rs.getString("pav_endereco"), rs.getString("pav_link"));

            }

            dbUtils.closeConnection(preparedStatement.getConnection());
            return clube;

        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    public ArrayList<ClubeData> getAllClubes() {

        ArrayList<ClubeData> clubes = new ArrayList<>();

        try {
            PreparedStatement preparedStatement = dbUtils.getConnection()
                    .prepareStatement("select *from clube");

            ResultSet rs = preparedStatement.executeQuery();

            if (rs == null) {
                return null;
            }

            while (rs.next()) {
                ClubeData clube = new ClubeData(rs.getInt("id"), rs.getString("nome"), rs.getString("pav_nome"),
                        rs.getString("pav_endereco"), rs.getString("pav_link"));
                clubes.add(clube);
            }

            dbUtils.closeConnection(preparedStatement.getConnection());
            return clubes;

        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return null;
    }

    public boolean updateClube(ClubeData parmClube) {

        try {

            if (parmClube.getId() == 0) {
                // Inserir novo clube
                PreparedStatement preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "INSERT INTO clube (nome, pav_nome, pav_endereco, pav_link) VALUES (?, ?, ?, ?)");

                preparedStatement.setString(1, parmClube.getNome());
                preparedStatement.setString(2, parmClube.getPav_nome());
                preparedStatement.setString(3, parmClube.getPav_endereco());
                preparedStatement.setString(4, parmClube.getPav_link());
                preparedStatement.executeUpdate();

                dbUtils.closeConnection(preparedStatement.getConnection());

                return true;
            } else {
                // Atualizar clube existente
                PreparedStatement preparedStatement = dbUtils.getConnection()
                        .prepareStatement(
                                "UPDATE clube SET nome = ?, pav_nome = ?, pav_endereco = ?, pav_link = ? WHERE id = ?");

                preparedStatement.setString(1, parmClube.getNome());
                preparedStatement.setString(2, parmClube.getPav_nome());
                preparedStatement.setString(3, parmClube.getPav_endereco());
                preparedStatement.setString(4, parmClube.getPav_link());
                preparedStatement.setInt(5, parmClube.getId());
                preparedStatement.executeUpdate();

                dbUtils.closeConnection(preparedStatement.getConnection());

                return true;
            }

        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return false;
    }

}
