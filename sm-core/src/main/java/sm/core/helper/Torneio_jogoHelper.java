package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.springframework.stereotype.Component;

import sm.core.data.Torneio_jogo;

@Component
public class Torneio_jogoHelper {

    private final DBUtils dbUtils;
    public Torneio_jogoHelper(DBUtils dbUtils) {
        this.dbUtils = dbUtils;
    }
    public static String getTableName() {
        return "torneio_jogo";
    }

    public ArrayList<Torneio_jogo> loadAllGames() {
        ArrayList<Torneio_jogo> games = new ArrayList<>();
        Connection conn = null;
        
        try {
            conn = dbUtils.getConnection();
            PreparedStatement preparedStatement = conn
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
                jogo.setTier(rs.getString("tier")); // exemplo: Sub-9, Sub-11, Sub-13, etc.
                jogo.setDate(rs.getString("date"));
                jogo.setRound_number(rs.getString("round_number"));
                jogo.setRound_action(rs.getString("round_action"));
                jogo.setHasTable(rs.getBoolean("hasTable")); // Indica se o jogo possui tabela associada

                games.add(jogo);

            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    dbUtils.closeConnection(conn);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return games;
    }

    public boolean saveMatch(Torneio_jogo match) {
        // lógica para salvar o jogo no banco de dados
        Connection conn = null;
        
        try {
            conn = dbUtils.getConnection();
            PreparedStatement preparedStatement = conn
                    .prepareStatement(
                            "UPDATE torneio_jogo SET court = ?, time = ?, homeTeamId = ?, awayTeamId = ?, homeTeam = ?, awayTeam = ?, goalsHomeTeam = ?, goalsAwayTeam = ?, status = ?, result = ?, round = ?, tier = ? WHERE id = ?");

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
            preparedStatement.setString(12, match.getTier());
            preparedStatement.setInt(13, match.getId());

            int rowsAffected = preparedStatement.executeUpdate();

  

            // vai realizar a ação definida para a equipa vencedora e vencida se o jogo
            // estiver
            // concluído
            if (match.getStatus().equals("completed") == false) {
                return true;
            }

            // Processar round_action se existir
            if (match.getRound_action() != null && !match.getRound_action().isEmpty()) {
                processRoundActions(conn, match);
            }

            // Home vitoria

            if (match.getGoalsHomeTeam() > match.getGoalsAwayTeam()) {

                preparedStatement = conn
                        .prepareStatement(
                                "update torneio_jogo set homeTeamId=?, homeTeam=?  where id= (select  SUBSTRING_INDEX(vitory_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(vitory_action, '.', -1)='H' )");

                preparedStatement.setInt(1, match.getHomeTeamId());
                preparedStatement.setString(2, match.getHomeTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
            

                preparedStatement = conn
                        .prepareStatement(
                                "update torneio_jogo set awayTeamId=?, awayTeam=?  where id= (select  SUBSTRING_INDEX(vitory_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(vitory_action, '.', -1)='A' )");

                preparedStatement.setInt(1, match.getHomeTeamId());
                preparedStatement.setString(2, match.getHomeTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
              

                // Derrota away

                preparedStatement = conn
                        .prepareStatement(
                                "update torneio_jogo set homeTeamId=?, homeTeam=?  where id= (select  SUBSTRING_INDEX(lose_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(lose_action, '.', -1)='H' )");

                preparedStatement.setInt(1, match.getAwayTeamId());
                preparedStatement.setString(2, match.getAwayTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
                

                // Away
                preparedStatement = conn
                        .prepareStatement(
                                "update torneio_jogo set awayTeamId=?, awayTeam=?  where id= (select  SUBSTRING_INDEX(lose_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(lose_action, '.', -1)='A' )");

                preparedStatement.setInt(1, match.getAwayTeamId());
                preparedStatement.setString(2, match.getAwayTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
              

            } else {

                preparedStatement = conn
                        .prepareStatement(
                                "update torneio_jogo set homeTeamId=?, homeTeam=?  where id= (select  SUBSTRING_INDEX(vitory_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(vitory_action, '.', -1)='H' )");

                preparedStatement.setInt(1, match.getAwayTeamId());
                preparedStatement.setString(2, match.getAwayTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
     

                preparedStatement = conn
                        .prepareStatement(
                                "update torneio_jogo set awayTeamId=?, awayTeam=?  where id= (select  SUBSTRING_INDEX(vitory_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(vitory_action, '.', -1)='A' )");

                preparedStatement.setInt(1, match.getAwayTeamId());
                preparedStatement.setString(2, match.getAwayTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
               

                // Derrota away

                preparedStatement = conn
                        .prepareStatement(
                                "update torneio_jogo set homeTeamId=?, homeTeam=?  where id= (select  SUBSTRING_INDEX(lose_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(lose_action, '.', -1)='H' )");

                preparedStatement.setInt(1, match.getHomeTeamId());
                preparedStatement.setString(2, match.getHomeTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
              

                // Away
                preparedStatement = conn
                        .prepareStatement(
                                "update torneio_jogo set awayTeamId=?, awayTeam=?  where id= (select  SUBSTRING_INDEX(lose_action, '.', 1) AS parte1   from torneio_jogo where id=? and SUBSTRING_INDEX(lose_action, '.', -1)='A' )");

                preparedStatement.setInt(1, match.getHomeTeamId());
                preparedStatement.setString(2, match.getHomeTeam());
                preparedStatement.setInt(3, match.getId());

                rowsAffected = preparedStatement.executeUpdate();
             

            }

            return true;

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    dbUtils.closeConnection(conn);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return false;
    }

    public boolean resetMatch(int matchId) {
        // lógica para resetar o jogo no banco de dados
        Connection conn = null;
        
        try {
            conn = dbUtils.getConnection();
            PreparedStatement preparedStatement = conn
                    .prepareStatement(
                            "UPDATE torneio_jogo SET goalsHomeTeam = 0, goalsAwayTeam = 0, status = 'scheduled', result = '', homeTeamId=homeTeamIdDefault, awayTeamId=awayTeamIdDefault, homeTeam=homeTeamDefault, awayTeam=awayTeamDefault, homeTeam=homeTeamDefault WHERE id = ?");

            preparedStatement.setInt(1, matchId);

            int rowsAffected = preparedStatement.executeUpdate();

            return rowsAffected > 0;

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (conn != null) {
                try {
                    dbUtils.closeConnection(conn);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return false;
    }

    public ArrayList<sm.core.data.Torneio_classificacao> getClassificacaoPorRound(String round) {
        ArrayList<sm.core.data.Torneio_classificacao> classificacao = new ArrayList<>();
        Connection conn = null;
        
        try {
            conn = dbUtils.getConnection();
            
            // Mapa para armazenar estatísticas de cada equipa
            java.util.HashMap<Integer, sm.core.data.Torneio_classificacao> statsMap = new java.util.HashMap<>();
            
            // PRIMEIRO: Carregar TODAS as equipas do round (incluindo jogos não completados)
            PreparedStatement psAllTeams = conn.prepareStatement(
                "SELECT DISTINCT homeTeamId, homeTeam FROM torneio_jogo WHERE round = ? AND homeTeamId > 0 " +
                "UNION " +
                "SELECT DISTINCT awayTeamId, awayTeam FROM torneio_jogo WHERE round = ? AND awayTeamId > 0"
            );
            psAllTeams.setString(1, round);
            psAllTeams.setString(2, round);
            ResultSet rsAllTeams = psAllTeams.executeQuery();
            
            // Inicializar todas as equipas com estatísticas zeradas
            while (rsAllTeams.next()) {
                int teamId = rsAllTeams.getInt(1);
                String teamName = rsAllTeams.getString(2);
                
                if (!statsMap.containsKey(teamId)) {
                    sm.core.data.Torneio_classificacao stats = new sm.core.data.Torneio_classificacao();
                    stats.setTeamId(teamId);
                    stats.setTeamName(teamName);
                    // Todos os valores já inicializados em 0 pelo construtor
                    statsMap.put(teamId, stats);
                }
            }
            
            // SEGUNDO: Processar apenas os jogos completados para atualizar estatísticas
            PreparedStatement preparedStatement = conn.prepareStatement(
                "SELECT * FROM torneio_jogo WHERE round = ? AND status = 'completed' ORDER BY id"
            );
            preparedStatement.setString(1, round);
            ResultSet rs = preparedStatement.executeQuery();
            
            // Processar todos os jogos completados
            while (rs.next()) {
                int homeTeamId = rs.getInt("homeTeamId");
                int awayTeamId = rs.getInt("awayTeamId");
                int goalsHome = rs.getInt("goalsHomeTeam");
                int goalsAway = rs.getInt("goalsAwayTeam");

                // Atualizar estatísticas da equipa da casa
                sm.core.data.Torneio_classificacao homeStats = statsMap.get(homeTeamId);
                if (homeStats != null) {
                    homeStats.setJogos(homeStats.getJogos() + 1);
                    homeStats.setGolosMarcados(homeStats.getGolosMarcados() + goalsHome);
                    homeStats.setGolosSofridos(homeStats.getGolosSofridos() + goalsAway);
                }
                
                // Atualizar estatísticas da equipa visitante
                sm.core.data.Torneio_classificacao awayStats = statsMap.get(awayTeamId);
                if (awayStats != null) {
                    awayStats.setJogos(awayStats.getJogos() + 1);
                    awayStats.setGolosMarcados(awayStats.getGolosMarcados() + goalsAway);
                    awayStats.setGolosSofridos(awayStats.getGolosSofridos() + goalsHome);
                }

                // Determinar resultado
                if (goalsHome > goalsAway) {
                    // Vitória da casa
                    if (homeStats != null) {
                        homeStats.setVitorias(homeStats.getVitorias() + 1);
                        homeStats.setPontos(homeStats.getPontos() + 3);
                    }
                    if (awayStats != null) {
                        awayStats.setDerrotas(awayStats.getDerrotas() + 1);
                    }
                } else if (goalsHome < goalsAway) {
                    // Vitória visitante
                    if (awayStats != null) {
                        awayStats.setVitorias(awayStats.getVitorias() + 1);
                        awayStats.setPontos(awayStats.getPontos() + 3);
                    }
                    if (homeStats != null) {
                        homeStats.setDerrotas(homeStats.getDerrotas() + 1);
                    }
                } else {
                    // Empate
                    if (homeStats != null) {
                        homeStats.setEmpates(homeStats.getEmpates() + 1);
                        homeStats.setPontos(homeStats.getPontos() + 1);
                    }
                    if (awayStats != null) {
                        awayStats.setEmpates(awayStats.getEmpates() + 1);
                        awayStats.setPontos(awayStats.getPontos() + 1);
                    }
                }
            }

            // Calcular diferença de golos para todas as equipas
            for (sm.core.data.Torneio_classificacao stats : statsMap.values()) {
                stats.setDiferencaGolos(stats.getGolosMarcados() - stats.getGolosSofridos());
                classificacao.add(stats);
            }

            // Pré-calcular critérios de desempate antes de ordenar
            java.util.HashMap<String, Integer> confrontosDiretos = new java.util.HashMap<>();
            java.util.HashMap<String, int[]> diferencasConfrontoDireto = new java.util.HashMap<>();
            
            for (int i = 0; i < classificacao.size(); i++) {
                for (int j = i + 1; j < classificacao.size(); j++) {
                    int teamA = classificacao.get(i).getTeamId();
                    int teamB = classificacao.get(j).getTeamId();
                    String key = teamA + "-" + teamB;
                    
                    confrontosDiretos.put(key, calcularConfrontoDireto(teamA, teamB, round, conn));
                    diferencasConfrontoDireto.put(key, calcularDiferencaGolosConfrontoDireto(teamA, teamB, round, conn));
                }
            }

            // Ordenar a classificação
            classificacao.sort((a, b) -> {
                // 1. Ordenar por pontos (decrescente)
                if (a.getPontos() != b.getPontos()) {
                    return b.getPontos() - a.getPontos();
                }
                
                // 2. Em caso de empate, verificar confronto direto
                String key = a.getTeamId() + "-" + b.getTeamId();
                String keyInversa = b.getTeamId() + "-" + a.getTeamId();
                int confrontoDireto = confrontosDiretos.containsKey(key) ? 
                    confrontosDiretos.get(key) : 
                    -confrontosDiretos.getOrDefault(keyInversa, 0);
                
                if (confrontoDireto != 0) {
                    return confrontoDireto;
                }
                
                // 3. Diferença de golos no confronto direto
                int[] diferencaCD = diferencasConfrontoDireto.containsKey(key) ?
                    diferencasConfrontoDireto.get(key) :
                    new int[] { diferencasConfrontoDireto.getOrDefault(keyInversa, new int[]{0,0})[1],
                               diferencasConfrontoDireto.getOrDefault(keyInversa, new int[]{0,0})[0] };
                
                int difA = diferencaCD[0];
                int difB = diferencaCD[1];
                if (difA != difB) {
                    return difB - difA;
                }
                
                // 4. Diferença de golos geral (já calculada)
                if (a.getDiferencaGolos() != b.getDiferencaGolos()) {
                    return b.getDiferencaGolos() - a.getDiferencaGolos();
                }
                
                // 5. Golos marcados geral
                return b.getGolosMarcados() - a.getGolosMarcados();
            });

            // Atribuir posições
            for (int i = 0; i < classificacao.size(); i++) {
                classificacao.get(i).setPosicao(i + 1);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            // IMPORTANTE: Fechar a conexão SEMPRE, mesmo em caso de erro
            if (conn != null) {
                try {
                    dbUtils.closeConnection(conn);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return classificacao;
    }

    private int calcularConfrontoDireto(int teamA, int teamB, String round, Connection conn) {
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT goalsHomeTeam, goalsAwayTeam FROM torneio_jogo " +
                "WHERE round = ? AND status = 'completed' AND " +
                "((homeTeamId = ? AND awayTeamId = ?) OR (homeTeamId = ? AND awayTeamId = ?))"
            );
            ps.setString(1, round);
            ps.setInt(2, teamA);
            ps.setInt(3, teamB);
            ps.setInt(4, teamB);
            ps.setInt(5, teamA);
            
            ResultSet rs = ps.executeQuery();
            
            int pontosA = 0;
            int pontosB = 0;
            
            while (rs.next()) {
                int goalsHome = rs.getInt("goalsHomeTeam");
                int goalsAway = rs.getInt("goalsAwayTeam");
                
                // Determinar qual equipa era casa e qual visitante
                PreparedStatement psCheck = conn.prepareStatement(
                    "SELECT homeTeamId FROM torneio_jogo WHERE round = ? AND " +
                    "goalsHomeTeam = ? AND goalsAwayTeam = ? LIMIT 1"
                );
                psCheck.setString(1, round);
                psCheck.setInt(2, goalsHome);
                psCheck.setInt(3, goalsAway);
                ResultSet rsCheck = psCheck.executeQuery();
                
                if (rsCheck.next()) {
                    int homeId = rsCheck.getInt("homeTeamId");
                    
                    if (homeId == teamA) {
                        if (goalsHome > goalsAway) pontosA += 3;
                        else if (goalsHome < goalsAway) pontosB += 3;
                        else { pontosA += 1; pontosB += 1; }
                    } else {
                        if (goalsHome > goalsAway) pontosB += 3;
                        else if (goalsHome < goalsAway) pontosA += 3;
                        else { pontosA += 1; pontosB += 1; }
                    }
                }
            }
            
            if (pontosA > pontosB) return -1;
            if (pontosB > pontosA) return 1;
            return 0;
            
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private int[] calcularDiferencaGolosConfrontoDireto(int teamA, int teamB, String round, Connection conn) {
        int golosMarcadosA = 0;
        int golosSofridosA = 0;
        int golosMarcadosB = 0;
        int golosSofridosB = 0;
        
        try {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT homeTeamId, awayTeamId, goalsHomeTeam, goalsAwayTeam FROM torneio_jogo " +
                "WHERE round = ? AND status = 'completed' AND " +
                "((homeTeamId = ? AND awayTeamId = ?) OR (homeTeamId = ? AND awayTeamId = ?))"
            );
            ps.setString(1, round);
            ps.setInt(2, teamA);
            ps.setInt(3, teamB);
            ps.setInt(4, teamB);
            ps.setInt(5, teamA);
            
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                int homeId = rs.getInt("homeTeamId");
                int goalsHome = rs.getInt("goalsHomeTeam");
                int goalsAway = rs.getInt("goalsAwayTeam");
                
                if (homeId == teamA) {
                    golosMarcadosA += goalsHome;
                    golosSofridosA += goalsAway;
                    golosMarcadosB += goalsAway;
                    golosSofridosB += goalsHome;
                } else {
                    golosMarcadosA += goalsAway;
                    golosSofridosA += goalsHome;
                    golosMarcadosB += goalsHome;
                    golosSofridosB += goalsAway;
                }
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
        
        return new int[] { golosMarcadosA - golosSofridosA, golosMarcadosB - golosSofridosB };
    }

    /**
     * Processa as ações do round_action para atualizar equipas baseado na classificação
     * Formato: "jogoId.tipo;jogoId.tipo;..." onde tipo é H (home) ou A (away)
     */
    private void processRoundActions(Connection conn, Torneio_jogo match) {
        try {
            String roundAction = match.getRound_action();
            if (roundAction == null || roundAction.trim().isEmpty()) {
                return;
            }

            // Obter a classificação do round
            ArrayList<sm.core.data.Torneio_classificacao> classificacao = getClassificacaoPorRound(match.getRound());
            
            if (classificacao == null || classificacao.isEmpty()) {
                System.out.println("Nenhuma classificação encontrada para o round: " + match.getRound());
                return;
            }

            // Dividir as ações por ;
            String[] actions = roundAction.split(";");
            
            for (int i = 0; i < actions.length && i < classificacao.size(); i++) {
                String action = actions[i].trim();
                if (action.isEmpty()) {
                    continue;
                }

                // Parse do formato "jogoId.tipo"
                String[] parts = action.split("\\.");
                if (parts.length != 2) {
                    System.out.println("Formato inválido de action: " + action);
                    continue;
                }

                int targetGameId = Integer.parseInt(parts[0]);
                String teamType = parts[1].toUpperCase(); // H ou A

                // Obter a equipa da posição i+1 da classificação (1º, 2º, 3º, etc.)
                sm.core.data.Torneio_classificacao teamStats = classificacao.get(i);
                int teamId = teamStats.getTeamId();
                String teamName = teamStats.getTeamName();

                // Atualizar o jogo de destino
                String sql;
                if ("H".equals(teamType)) {
                    sql = "UPDATE torneio_jogo SET homeTeamId = ?, homeTeam = ? WHERE id = ?";
                } else if ("A".equals(teamType)) {
                    sql = "UPDATE torneio_jogo SET awayTeamId = ?, awayTeam = ? WHERE id = ?";
                } else {
                    System.out.println("Tipo de equipa inválido: " + teamType);
                    continue;
                }

                PreparedStatement ps = conn.prepareStatement(sql);
                ps.setInt(1, teamId);
                ps.setString(2, teamName);
                ps.setInt(3, targetGameId);
                
                int updated = ps.executeUpdate();
                System.out.println("Round action processada: Posição " + (i+1) + 
                                 " (" + teamName + ") -> Jogo " + targetGameId + 
                                 " como " + (teamType.equals("H") ? "Home" : "Away") + 
                                 " (rows updated: " + updated + ")");
                ps.close();
            }

        } catch (Exception e) {
            System.err.println("Erro ao processar round_action: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
