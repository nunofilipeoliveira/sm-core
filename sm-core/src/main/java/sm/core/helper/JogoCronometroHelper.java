package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import sm.core.data.JogadorJogo;
import sm.core.data.JogoConfigData;
import sm.core.data.JogoEventoData;

/**
 * Suporte ao registo de atividade no jogo:
 *
 *  - modo normal ou modo cronómetro (duração das partes, 5 inicial);
 *  - botão de controlo do tempo (o cronómetro corre/para no front-end e a
 *    posição é persistida em jogo_config.tempo_atual_segundos);
 *  - timeline de eventos com tempo (jogo_evento), com edição/eliminação;
 *  - substituições (entrada/saída de jogadores);
 *  - tempo de jogo de cada jogador, calculado a partir da timeline;
 *  - cartão azul exclui o jogador do jogo durante 2 minutos.
 */
@Component
public class JogoCronometroHelper {

    private static final int EXCLUSAO_AZUL_PADRAO_SEGUNDOS = 120;

    private final DBUtils dbUtils;

    public JogoCronometroHelper(DBUtils dbUtils) {
        this.dbUtils = dbUtils;
    }

    // ==================================================================
    // Verificação de duplicação de eventos INICIO_JOGO / INICIO_PARTE
    // ==================================================================

    /**
     * Verifica se já existe um evento de início registado, para evitar
     * duplicação.
     *
     * INICIO_JOGO só pode existir uma vez por jogo (nunca se repete).
     * INICIO_PARTE tem de existir uma vez por cada parte — por isso esta
     * verificação tem de ser sensível ao id_parte; sem isso, qualquer
     * INICIO_PARTE (2ª parte em diante) era sempre visto como duplicado do
     * INICIO_JOGO já gravado para a 1ª parte, e ficava silenciosamente por
     * gravar.
     */
    private boolean existeEventoInicio(Connection conn, int idJogo, int idParte, String tipoEvento) throws SQLException {
        PreparedStatement ps;
        if (JogoEventoData.TIPO_INICIO_JOGO.equals(tipoEvento)) {
            ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS total FROM jogo_evento WHERE id_jogo = ? AND tipo_evento = ?");
            ps.setInt(1, idJogo);
            ps.setString(2, JogoEventoData.TIPO_INICIO_JOGO);
        } else {
            ps = conn.prepareStatement(
                    "SELECT COUNT(*) AS total FROM jogo_evento WHERE id_jogo = ? AND id_parte = ? AND tipo_evento = ?");
            ps.setInt(1, idJogo);
            ps.setInt(2, idParte);
            ps.setString(3, JogoEventoData.TIPO_INICIO_PARTE);
        }
        ResultSet rs = ps.executeQuery();
        int total = 0;
        if (rs.next()) {
            total = rs.getInt("total");
        }
        rs.close();
        ps.close();
        return total > 0;
    }

    // ==================================================================
    // Configuração do jogo (modo normal / cronómetro)
    // ==================================================================

    public JogoConfigData getConfigJogo(int idJogo) {
        JogoConfigData config = null;
        try (Connection conn = dbUtils.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, id_jogo, modo_registo, duracao_parte_minutos, numero_partes, num_jogadores_iniciais, "
                            + "duracao_exclusao_azul_segundos, tempo_atual_segundos FROM jogo_config WHERE id_jogo = ?");
            ps.setInt(1, idJogo);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                config = new JogoConfigData(rs.getInt("id"), rs.getInt("id_jogo"), rs.getString("modo_registo"),
                        rs.getInt("duracao_parte_minutos"), rs.getInt("numero_partes"),
                        rs.getInt("num_jogadores_iniciais"), rs.getInt("duracao_exclusao_azul_segundos"),
                        rs.getInt("tempo_atual_segundos"));
            }
            rs.close();
            ps.close();

            if (config != null) {
                config.setJogadores(getJogadoresResumo(conn, idJogo));
            }
            return config;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean guardarConfigJogo(JogoConfigData config) {
        if (config == null || config.getId_jogo() <= 0) {
            return false;
        }
        Connection conn = null;
        try {
            conn = dbUtils.getConnection();
            conn.setAutoCommit(false);

            String modo = (config.getModo_registo() != null && !config.getModo_registo().isBlank())
                    ? config.getModo_registo()
                    : JogoConfigData.MODO_NORMAL;
            if (!JogoConfigData.MODO_NORMAL.equalsIgnoreCase(modo)
                    && !JogoConfigData.MODO_CRONOMETRO.equalsIgnoreCase(modo)) {
                modo = JogoConfigData.MODO_NORMAL;
            }
            int numIniciais = config.getNum_jogadores_iniciais() > 0 ? config.getNum_jogadores_iniciais() : 0;
            if (JogoConfigData.MODO_CRONOMETRO.equalsIgnoreCase(modo) && numIniciais <= 0) {
                numIniciais = 5;
            }
            int duracaoParte = config.getDuracao_parte_minutos() > 0 ? config.getDuracao_parte_minutos() : 25;
            int numPartes = config.getNumero_partes() > 0 ? config.getNumero_partes() : 2;
            int duracaoAzul = config.getDuracao_exclusao_azul_segundos() > 0
                    ? config.getDuracao_exclusao_azul_segundos()
                    : EXCLUSAO_AZUL_PADRAO_SEGUNDOS;

            // upsert compatível com MySQL/MariaDB (evita deprecação do VALUES())
            boolean existe = false;
            PreparedStatement check = conn.prepareStatement("SELECT id FROM jogo_config WHERE id_jogo = ?");
            check.setInt(1, config.getId_jogo());
            ResultSet rsCheck = check.executeQuery();
            existe = rsCheck.next();
            rsCheck.close();
            check.close();

            if (existe) {
                PreparedStatement update = conn.prepareStatement(
                        "UPDATE jogo_config SET modo_registo = ?, duracao_parte_minutos = ?, numero_partes = ?, "
                                + "num_jogadores_iniciais = ?, duracao_exclusao_azul_segundos = ? WHERE id_jogo = ?");
                update.setString(1, modo);
                update.setInt(2, duracaoParte);
                update.setInt(3, numPartes);
                update.setInt(4, numIniciais);
                update.setInt(5, duracaoAzul);
                update.setInt(6, config.getId_jogo());
                update.executeUpdate();
                update.close();
            } else {
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO jogo_config (id_jogo, modo_registo, duracao_parte_minutos, numero_partes, "
                                + "num_jogadores_iniciais, duracao_exclusao_azul_segundos) VALUES (?, ?, ?, ?, ?, ?)");
                insert.setInt(1, config.getId_jogo());
                insert.setString(2, modo);
                insert.setInt(3, duracaoParte);
                insert.setInt(4, numPartes);
                insert.setInt(5, numIniciais);
                insert.setInt(6, duracaoAzul);
                insert.executeUpdate();
                insert.close();
            }

            // marcar o "5 inicial" (titulares) a partir da lista enviada
            PreparedStatement reset = conn.prepareStatement("UPDATE jogo_jogador SET titular = 0 WHERE id_jogo = ?");
            reset.setInt(1, config.getId_jogo());
            reset.executeUpdate();
            reset.close();

            if (config.getJogadores() != null) {
                int contador = 0;
                PreparedStatement setTitular = conn
                        .prepareStatement("UPDATE jogo_jogador SET titular = 1 WHERE id_jogo = ? AND id_jogador = ?");
                for (JogadorJogo j : config.getJogadores()) {
                    if (j.isTitular() && j.getId_jogador() > 0
                            && (numIniciais <= 0 || contador < numIniciais)) {
                        setTitular.setInt(1, config.getId_jogo());
                        setTitular.setInt(2, j.getId_jogador());
                        setTitular.executeUpdate();
                        contador++;
                    }
                }
                setTitular.close();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            rollback(conn);
            e.printStackTrace();
        } finally {
            fechar(conn);
        }
        return false;
    }

    /**
     * Persiste a posição atual do cronómetro para permitir retomar após recarregar
     * a página. É também atualizado automaticamente a cada evento registado.
     */
    public boolean atualizarTempoAtual(int idJogo, int tempoSegundos) {
        if (idJogo <= 0) {
            return false;
        }
        Connection conn = null;
        try {
            conn = dbUtils.getConnection();
            conn.setAutoCommit(false);

            boolean existe = false;
            PreparedStatement check = conn.prepareStatement("SELECT id FROM jogo_config WHERE id_jogo = ?");
            check.setInt(1, idJogo);
            ResultSet rsCheck = check.executeQuery();
            existe = rsCheck.next();
            rsCheck.close();
            check.close();

            if (existe) {
                PreparedStatement upd = conn
                        .prepareStatement("UPDATE jogo_config SET tempo_atual_segundos = ? WHERE id_jogo = ?");
                upd.setInt(1, Math.max(0, tempoSegundos));
                upd.setInt(2, idJogo);
                upd.executeUpdate();
                upd.close();
            } else {
                PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO jogo_config (id_jogo, modo_registo, tempo_atual_segundos) VALUES (?, 'NORMAL', ?)");
                ins.setInt(1, idJogo);
                ins.setInt(2, Math.max(0, tempoSegundos));
                ins.executeUpdate();
                ins.close();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            rollback(conn);
            e.printStackTrace();
        } finally {
            fechar(conn);
        }
        return false;
    }

    private ArrayList<JogadorJogo> getJogadoresResumo(Connection conn, int idJogo) throws SQLException {
        ArrayList<JogadorJogo> lista = new ArrayList<>();
        PreparedStatement ps = conn.prepareStatement(
                "SELECT jj.id_jogador, j.nome, jj.titular, jj.em_campo, jj.excluido_ate_segundos, jj.tempo_jogo_segundos "
                        + "FROM jogo_jogador jj INNER JOIN jogador j ON j.id = jj.id_jogador WHERE jj.id_jogo = ? "
                        + "ORDER BY jj.titular DESC, jj.numero ASC, j.nome ASC");
        ps.setInt(1, idJogo);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            lista.add(mapJogadorResumo(rs));
        }
        rs.close();
        ps.close();
        return lista;
    }

    private JogadorJogo mapJogadorResumo(ResultSet rs) throws SQLException {
        Object excluido = rs.getObject("excluido_ate_segundos");
        return new JogadorJogo(rs.getInt("id_jogador"), rs.getString("nome"), rs.getBoolean("titular"),
                rs.getBoolean("em_campo"), excluido != null ? rs.getInt("excluido_ate_segundos") : null,
                rs.getInt("tempo_jogo_segundos"));
    }

    // ==================================================================
    // Timeline de eventos
    // ==================================================================

    /**
     * Devolve a timeline completa de eventos do jogo, ordenada por tempo.
     * Os eventos de início (INICIO_JOGO, INICIO_PARTE) e fim (FIM_JOGO, FIM_PARTE)
     * estão sempre incluídos para permitir a reconstrução do estado do jogo.
     */
    public ArrayList<JogoEventoData> getTimeline(int idJogo) {
        ArrayList<JogoEventoData> eventos = new ArrayList<>();
        try (Connection conn = dbUtils.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT e.id, e.id_jogo, e.id_parte, e.tempo_evento, e.tempo_segundos, e.tipo_evento, "
                            + "e.id_jogador, e.id_jogador_secundario, e.detalhe, e.obs, e.criado_em, e.id_equipa, "
                            + "j1.nome AS nome_jogador, j2.nome AS nome_jogador_secundario "
                            + "FROM jogo_evento e "
                            + "LEFT JOIN jogador j1 ON j1.id = e.id_jogador "
                            + "LEFT JOIN jogador j2 ON j2.id = e.id_jogador_secundario "
                            + "WHERE e.id_jogo = ? ORDER BY e.tempo_segundos ASC, e.id ASC");
            ps.setInt(1, idJogo);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                eventos.add(mapEvento(rs));
            }
            rs.close();
            ps.close();
            return eventos;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Devolve apenas os eventos de controle do jogo (INICIO_JOGO, INICIO_PARTE,
     * FIM_PARTE, FIM_JOGO) para o modo de visualização do jogo.
     * Estes eventos definem os marcos principais da linha do tempo.
     */
    public ArrayList<JogoEventoData> getEventosControleJogo(int idJogo) {
        ArrayList<JogoEventoData> eventos = new ArrayList<>();
        try (Connection conn = dbUtils.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT e.id, e.id_jogo, e.id_parte, e.tempo_evento, e.tempo_segundos, e.tipo_evento, "
                            + "e.id_jogador, e.id_jogador_secundario, e.detalhe, e.obs, e.criado_em, e.id_equipa, "
                            + "j1.nome AS nome_jogador, j2.nome AS nome_jogador_secundario "
                            + "FROM jogo_evento e "
                            + "LEFT JOIN jogador j1 ON j1.id = e.id_jogador "
                            + "LEFT JOIN jogador j2 ON j2.id = e.id_jogador_secundario "
                            + "WHERE e.id_jogo = ? AND e.tipo_evento IN (?, ?, ?, ?) "
                            + "ORDER BY e.tempo_segundos ASC, e.id ASC");
            ps.setInt(1, idJogo);
            ps.setString(2, JogoEventoData.TIPO_INICIO_JOGO);
            ps.setString(3, JogoEventoData.TIPO_INICIO_PARTE);
            ps.setString(4, JogoEventoData.TIPO_FIM_PARTE);
            ps.setString(5, JogoEventoData.TIPO_FIM_JOGO);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                eventos.add(mapEvento(rs));
            }
            rs.close();
            ps.close();
            return eventos;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Regista um evento de jogo na timeline e aplica os efeitos laterais:
     *
     *  - GOLO / GOLO_SOFRIDO, cartões e estatísticas -> contadores em jogo_jogador;
     *  - AZUL -> exclui o jogador durante 2 minutos (excluido_ate_segundos);
     *  - VERMELHO -> em_campo = falso;
     *  - SUBSTITUICAO -> id_jogador (entra) / id_jogador_secundario (sai);
     *  - INICIO_JOGO -> coloca em campo o 5 inicial.
     */
    public JogoEventoData registarEvento(JogoEventoData evento) {
        if (evento == null || evento.getId_jogo() <= 0 || evento.getTipo_evento() == null
                || evento.getTipo_evento().isBlank()) {
            return null;
        }
        Connection conn = null;
        try {
            conn = dbUtils.getConnection();
            conn.setAutoCommit(false);

            // Verificar se é um evento de início e se já existe (evitar duplicação)
            String tipo = evento.getTipo_evento();
            int idParte = evento.getId_parte() > 0 ? evento.getId_parte() : 1;
            if (JogoEventoData.TIPO_INICIO_JOGO.equals(tipo) || JogoEventoData.TIPO_INICIO_PARTE.equals(tipo)) {
                if (existeEventoInicio(conn, evento.getId_jogo(), idParte, tipo)) {
                    System.out.println("⚠️ Evento de início (" + tipo + ", parte " + idParte + ") já existe para o jogo "
                            + evento.getId_jogo() + ". A ignorar duplicação.");
                    return null;
                }
            }

            // Se for FIM_JOGO, atualizar o status do jogo para 'completed'
            if (JogoEventoData.TIPO_FIM_JOGO.equals(tipo)) {
                atualizarStatusJogo(conn, evento.getId_jogo(), "completed");
            }
            // Se for INICIO_JOGO, atualizar o status para 'in-progress'
            else if (JogoEventoData.TIPO_INICIO_JOGO.equals(tipo)) {
                atualizarStatusJogo(conn, evento.getId_jogo(), "in-progress");
            }

            aplicarEvento(conn, evento);

            PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO jogo_evento (id_jogo, id_parte, tempo_evento, tempo_segundos, tipo_evento, "
                            + "id_jogador, id_jogador_secundario, detalhe, obs, id_equipa) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            insert.setInt(1, evento.getId_jogo());
            insert.setInt(2, evento.getId_parte() > 0 ? evento.getId_parte() : 1);
            insert.setString(3, normalizarTempo(evento.getTempo_evento()));
            insert.setInt(4, Math.max(0, evento.getTempo_segundos()));
            insert.setString(5, evento.getTipo_evento());
            setJogadorOuNull(insert, 6, evento.getId_jogador());
            setJogadorOuNull(insert, 7, evento.getId_jogador_secundario());
            insert.setString(8, evento.getDetalhe());
            insert.setString(9, evento.getObs());
            insert.setInt(10, evento.getId_equipa());
            insert.executeUpdate();

            ResultSet keys = insert.getGeneratedKeys();
            if (keys.next()) {
                evento.setId(keys.getInt(1));
            }
            keys.close();
            insert.close();

            sincronizarTempoAtual(conn, evento.getId_jogo(), evento.getTempo_segundos());

            conn.commit();
            return buscarEvento(conn, evento.getId());
        } catch (SQLException e) {
            rollback(conn);
            e.printStackTrace();
        } finally {
            fechar(conn);
        }
        return null;
    }

    private JogoEventoData buscarEvento(Connection conn, int idEvento) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT e.id, e.id_jogo, e.id_parte, e.tempo_evento, e.tempo_segundos, e.tipo_evento, "
                        + "e.id_jogador, e.id_jogador_secundario, e.detalhe, e.obs, e.criado_em, e.id_equipa, "
                        + "j1.nome AS nome_jogador, j2.nome AS nome_jogador_secundario "
                        + "FROM jogo_evento e "
                        + "LEFT JOIN jogador j1 ON j1.id = e.id_jogador "
                        + "LEFT JOIN jogador j2 ON j2.id = e.id_jogador_secundario "
                        + "WHERE e.id = ?");
        ps.setInt(1, idEvento);
        ResultSet rs = ps.executeQuery();
        JogoEventoData evento = null;
        if (rs.next()) {
            evento = mapEvento(rs);
        }
        rs.close();
        ps.close();
        return evento;
    }

    private JogoEventoData mapEvento(ResultSet rs) throws SQLException {
        JogoEventoData e = new JogoEventoData(rs.getInt("id"), rs.getInt("id_jogo"), rs.getInt("id_parte"),
                rs.getString("tempo_evento"), rs.getInt("tempo_segundos"), rs.getString("tipo_evento"),
                rs.getInt("id_jogador"), rs.getInt("id_jogador_secundario"), rs.getString("detalhe"),
                rs.getString("obs"));
        e.setNome_jogador(rs.getString("nome_jogador"));
        e.setNome_jogador_secundario(rs.getString("nome_jogador_secundario"));
        e.setId_equipa(getIntOuZero(rs, "id_equipa"));
        java.sql.Timestamp ts = rs.getTimestamp("criado_em");
        if (ts != null) {
            e.setCriado_em(ts.toString());
        }
        return e;
    }

    /**
     * Edita um evento da timeline. Permite corrigir o tempo (tempo_evento /
     * tempo_segundos / id_parte) e o próprio evento (tipo e jogadores). Quando há
     * alterações estruturais, os efeitos laterais do evento antigo são revertidos
     * e os do novo são aplicados.
     */
    public boolean editarEvento(JogoEventoData novo) {
        if (novo == null || novo.getId() <= 0) {
            return false;
        }
        Connection conn = null;
        try {
            conn = dbUtils.getConnection();
            conn.setAutoCommit(false);

            JogoEventoData antigo = buscarEvento(conn, novo.getId());
            if (antigo == null) {
                rollback(conn);
                return false;
            }

            boolean estruturaAlterada = !same(antigo.getTipo_evento(), novo.getTipo_evento())
                    || antigo.getId_jogador() != novo.getId_jogador()
                    || antigo.getId_jogador_secundario() != novo.getId_jogador_secundario()
                    || !same(antigo.getDetalhe(), novo.getDetalhe());

            if (estruturaAlterada) {
                reverterEvento(conn, antigo, antigo.getId());
                                aplicarEvento(conn, novo);
            }

            PreparedStatement upd = conn.prepareStatement(
                    "UPDATE jogo_evento SET id_parte = ?, tempo_evento = ?, tempo_segundos = ?, tipo_evento = ?, "
                            + "id_jogador = ?, id_jogador_secundario = ?, detalhe = ?, obs = ?, id_equipa = ? WHERE id = ?");
            upd.setInt(1, novo.getId_parte() > 0 ? novo.getId_parte() : 1);
            upd.setString(2, normalizarTempo(novo.getTempo_evento()));
            upd.setInt(3, Math.max(0, novo.getTempo_segundos()));
            upd.setString(4, novo.getTipo_evento());
            setJogadorOuNull(upd, 5, novo.getId_jogador());
            setJogadorOuNull(upd, 6, novo.getId_jogador_secundario());
            upd.setString(7, novo.getDetalhe());
            upd.setString(8, novo.getObs());
            upd.setInt(9, novo.getId_equipa());
            upd.setInt(10, novo.getId());
            upd.executeUpdate();
            upd.close();

            sincronizarTempoAtual(conn, novo.getId_jogo(), novo.getTempo_segundos());

            conn.commit();
            return true;
        } catch (SQLException e) {
            rollback(conn);
            e.printStackTrace();
        } finally {
            fechar(conn);
        }
        return false;
    }

    /**
     * Elimina um evento da timeline, revertendo os efeitos laterais.
     */
    public boolean eliminarEvento(int idEvento) {
        if (idEvento <= 0) {
            return false;
        }
        Connection conn = null;
        try {
            conn = dbUtils.getConnection();
            conn.setAutoCommit(false);

            JogoEventoData evento = buscarEvento(conn, idEvento);
            if (evento == null) {
                rollback(conn);
                return false;
            }

            reverterEvento(conn, evento, idEvento);

            PreparedStatement del = conn.prepareStatement("DELETE FROM jogo_evento WHERE id = ?");
            del.setInt(1, idEvento);
            del.executeUpdate();
            del.close();

            conn.commit();
            return true;
        } catch (SQLException e) {
            rollback(conn);
            e.printStackTrace();
        } finally {
            fechar(conn);
        }
        return false;
    }

    /**
     * Atalho para registar uma substituição. id_jogador = quem entra;
     * id_jogador_secundario = quem sai.
     */
    public JogoEventoData marcarSubstituicao(JogoEventoData evento) {
        if (evento != null) {
            evento.setTipo_evento(JogoEventoData.TIPO_SUBSTITUICAO);
        }
        return registarEvento(evento);
    }

    // ==================================================================
    // Efeitos laterais dos eventos
    // ==================================================================

    private void aplicarEvento(Connection conn, JogoEventoData e) throws SQLException {
        String tipo = e.getTipo_evento();
        switch (tipo) {
                case JogoEventoData.TIPO_GOLO:
            // id_equipa == EQUIPA_ADVERSARIA -> golo do adversário (golos_equipa_adv)
            // caso contrário (nossa equipa) -> golos_equipa
            if (e.getId_equipa() == JogoEventoData.EQUIPA_ADVERSARIA) {
                incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), colunaGolo(e.getDetalhe(), true), 1);
                ajustarGolosJogo(conn, e.getId_jogo(), "golos_equipa_adv", 1);
            } else {
                incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), colunaGolo(e.getDetalhe(), true), 1);
                ajustarGolosJogo(conn, e.getId_jogo(), "golos_equipa", 1);
            }
            break;
        case JogoEventoData.TIPO_GOLO_SOFRIDO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), colunaGolo(e.getDetalhe(), false), 1);
            ajustarGolosJogo(conn, e.getId_jogo(), "golos_equipa_adv", 1);
            break;
        case JogoEventoData.TIPO_AMARELO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "amarelo", 1);
            break;
        case JogoEventoData.TIPO_AZUL:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "azul", 1);
            definirEmCampo(conn, e.getId_jogo(), e.getId_jogador(), false);
            // cartão azul -> jogador excluído durante 2 minutos
            definirExclusao(conn, e.getId_jogo(), e.getId_jogador(),
                    e.getTempo_segundos() + duracaoExclusaoAzul(conn, e.getId_jogo()));
            break;
        case JogoEventoData.TIPO_VERMELHO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "vermelho", 1);
            definirEmCampo(conn, e.getId_jogo(), e.getId_jogador(), false);
            break;
        case JogoEventoData.TIPO_FALTA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "faltas", 1);
            break;
        case JogoEventoData.TIPO_ASSISTENCIA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "assistencias", 1);
            break;
        case JogoEventoData.TIPO_RECUPERACAO_BOLA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "recuperacoes_bola", 1);
            break;
        case JogoEventoData.TIPO_PERDA_BOLA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "perdas_bola", 1);
            break;
        case JogoEventoData.TIPO_REMATE:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "remates", 1);
            break;
        case JogoEventoData.TIPO_PENALTY_FALHADO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "penalty_falhado", 1);
            break;
        case JogoEventoData.TIPO_PENALTY_DEFESA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "penalty_defesa", 1);
            break;
        case JogoEventoData.TIPO_LD_FALHADO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "ld_falhado", 1);
            break;
        case JogoEventoData.TIPO_LD_DEFESA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "ld_defesa", 1);
            break;
        case JogoEventoData.TIPO_SUBSTITUICAO:
            if (e.getId_jogador() > 0) {
                definirEmCampo(conn, e.getId_jogo(), e.getId_jogador(), true);
            }
            if (e.getId_jogador_secundario() > 0) {
                definirEmCampo(conn, e.getId_jogo(), e.getId_jogador_secundario(), false);
            }
            break;
        case JogoEventoData.TIPO_INICIO_JOGO:
            // colocam em campo os jogadores marcados como titulares (5 inicial)
            PreparedStatement inicio = conn
                    .prepareStatement("UPDATE jogo_jogador SET em_campo = titular WHERE id_jogo = ?");
            inicio.setInt(1, e.getId_jogo());
            inicio.executeUpdate();
            inicio.close();
            break;
        case JogoEventoData.TIPO_FIM_PARTE:
        case JogoEventoData.TIPO_FIM_JOGO:
            PreparedStatement fim = conn.prepareStatement("UPDATE jogo_jogador SET em_campo = 0 WHERE id_jogo = ?");
            fim.setInt(1, e.getId_jogo());
            fim.executeUpdate();
            fim.close();
            break;
        case JogoEventoData.TIPO_INICIO_PARTE:
        default:
            // sem efeito lateral em contadores
            break;
        }
    }

    private void reverterEvento(Connection conn, JogoEventoData e, int idEvento) throws SQLException {
        String tipo = e.getTipo_evento();
        switch (tipo) {
                case JogoEventoData.TIPO_GOLO:
            // id_equipa == EQUIPA_ADVERSARIA -> reverter golos_equipa_adj
            if (e.getId_equipa() == JogoEventoData.EQUIPA_ADVERSARIA) {
                incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), colunaGolo(e.getDetalhe(), true), -1);
                ajustarGolosJogo(conn, e.getId_jogo(), "golos_equipa_adv", -1);
            } else {
                incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), colunaGolo(e.getDetalhe(), true), -1);
                ajustarGolosJogo(conn, e.getId_jogo(), "golos_equipa", -1);
            }
            break;
        case JogoEventoData.TIPO_GOLO_SOFRIDO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), colunaGolo(e.getDetalhe(), false), -1);
            ajustarGolosJogo(conn, e.getId_jogo(), "golos_equipa_adv", -1);
            break;
        case JogoEventoData.TIPO_AMARELO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "amarelo", -1);
            break;
        case JogoEventoData.TIPO_AZUL:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "azul", -1);
            if (isUltimoCartaoExclusao(conn, e.getId_jogo(), e.getId_jogador(), idEvento)) {
                limparExclusao(conn, e.getId_jogo(), e.getId_jogador());
                definirEmCampo(conn, e.getId_jogo(), e.getId_jogador(), true);
            }
            break;
        case JogoEventoData.TIPO_VERMELHO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "vermelho", -1);
            if (isUltimoCartaoExclusao(conn, e.getId_jogo(), e.getId_jogador(), idEvento)) {
                definirEmCampo(conn, e.getId_jogo(), e.getId_jogador(), true);
            }
            break;
        case JogoEventoData.TIPO_FALTA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "faltas", -1);
            break;
        case JogoEventoData.TIPO_ASSISTENCIA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "assistencias", -1);
            break;
        case JogoEventoData.TIPO_RECUPERACAO_BOLA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "recuperacoes_bola", -1);
            break;
        case JogoEventoData.TIPO_PERDA_BOLA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "perdas_bola", -1);
            break;
        case JogoEventoData.TIPO_REMATE:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "remates", -1);
            break;
        case JogoEventoData.TIPO_PENALTY_FALHADO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "penalty_falhado", -1);
            break;
        case JogoEventoData.TIPO_PENALTY_DEFESA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "penalty_defesa", -1);
            break;
        case JogoEventoData.TIPO_LD_FALHADO:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "ld_falhado", -1);
            break;
        case JogoEventoData.TIPO_LD_DEFESA:
            incrementarColuna(conn, e.getId_jogo(), e.getId_jogador(), "ld_defesa", -1);
            break;
        case JogoEventoData.TIPO_SUBSTITUICAO:
            // reverter a substituição: quem entrou sai e quem saiu volta
            if (e.getId_jogador() > 0) {
                definirEmCampo(conn, e.getId_jogo(), e.getId_jogador(), false);
            }
            if (e.getId_jogador_secundario() > 0) {
                definirEmCampo(conn, e.getId_jogo(), e.getId_jogador_secundario(), true);
            }
            break;
        case JogoEventoData.TIPO_INICIO_JOGO:
            // antes do início do jogo ninguém está em campo
            PreparedStatement inicio = conn.prepareStatement("UPDATE jogo_jogador SET em_campo = 0 WHERE id_jogo = ?");
            inicio.setInt(1, e.getId_jogo());
            inicio.executeUpdate();
            inicio.close();
            break;
        case JogoEventoData.TIPO_FIM_PARTE:
        case JogoEventoData.TIPO_FIM_JOGO:
            // best-effort: recolocar em campo os titulares (equivalente a reabrir)
            PreparedStatement rev = conn
                    .prepareStatement("UPDATE jogo_jogador SET em_campo = titular WHERE id_jogo = ?");
            rev.setInt(1, e.getId_jogo());
            rev.executeUpdate();
            rev.close();
            break;
        default:
            break;
        }
    }

    private void incrementarColuna(Connection conn, int idJogo, int idJogador, String coluna, int delta)
            throws SQLException {
        if (idJogador <= 0) {
            return;
        }
        PreparedStatement ps = conn.prepareStatement("UPDATE jogo_jogador SET " + coluna + " = GREATEST(COALESCE("
                + coluna + ", 0) + ?, 0) WHERE id_jogo = ? AND id_jogador = ?");
        ps.setInt(1, delta);
        ps.setInt(2, idJogo);
        ps.setInt(3, idJogador);
        ps.executeUpdate();
        ps.close();
    }

    private void ajustarGolosJogo(Connection conn, int idJogo, String coluna, int delta) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE jogo SET " + coluna + " = GREATEST(COALESCE(" + coluna + ", 0) + ?, 0) WHERE id = ?");
        ps.setInt(1, delta);
        ps.setInt(2, idJogo);
        ps.executeUpdate();
        ps.close();
    }

    private void definirEmCampo(Connection conn, int idJogo, int idJogador, boolean emCampo) throws SQLException {
        if (idJogador <= 0) {
            return;
        }
        PreparedStatement ps = conn.prepareStatement("UPDATE jogo_jogador SET em_campo = ? WHERE id_jogo = ? AND id_jogador = ?");
        ps.setBoolean(1, emCampo);
        ps.setInt(2, idJogo);
        ps.setInt(3, idJogador);
        ps.executeUpdate();
        ps.close();
    }

    private void definirExclusao(Connection conn, int idJogo, int idJogador, int ateSegundos) throws SQLException {
        if (idJogador <= 0) {
            return;
        }
        PreparedStatement ps = conn
                .prepareStatement("UPDATE jogo_jogador SET excluido_ate_segundos = ? WHERE id_jogo = ? AND id_jogador = ?");
        ps.setInt(1, ateSegundos);
        ps.setInt(2, idJogo);
        ps.setInt(3, idJogador);
        ps.executeUpdate();
        ps.close();
    }

    private void limparExclusao(Connection conn, int idJogo, int idJogador) throws SQLException {
        if (idJogador <= 0) {
            return;
        }
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE jogo_jogador SET excluido_ate_segundos = NULL WHERE id_jogo = ? AND id_jogador = ?");
        ps.setInt(1, idJogo);
        ps.setInt(2, idJogador);
        ps.executeUpdate();
        ps.close();
    }

    /**
     * Diz se o evento é o último cartão de exclusão (AZUL/VERMELHO) do jogador,
     * usado para reverter em_campo / exclusão de forma segura.
     */
    private boolean isUltimoCartaoExclusao(Connection conn, int idJogo, int idJogador, int idEvento)
            throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) AS total FROM jogo_evento WHERE id_jogo = ? AND id_jogador = ? "
                        + "AND tipo_evento IN ('AZUL', 'VERMELHO') AND id > ?");
        ps.setInt(1, idJogo);
        ps.setInt(2, idJogador);
        ps.setInt(3, idEvento);
        ResultSet rs = ps.executeQuery();
        int total = 0;
        if (rs.next()) {
            total = rs.getInt("total");
        }
        rs.close();
        ps.close();
        return total == 0;
    }

    private int duracaoExclusaoAzul(Connection conn, int idJogo) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT duracao_exclusao_azul_segundos FROM jogo_config WHERE id_jogo = ?");
        ps.setInt(1, idJogo);
        ResultSet rs = ps.executeQuery();
        int duracao = EXCLUSAO_AZUL_PADRAO_SEGUNDOS;
        if (rs.next()) {
            duracao = rs.getInt(1);
        }
        rs.close();
        ps.close();
        return duracao > 0 ? duracao : EXCLUSAO_AZUL_PADRAO_SEGUNDOS;
    }

    private String colunaGolo(String detalhe, boolean marcado) {
        String d = detalhe == null ? "" : detalhe.trim().toLowerCase();
        String prefixo = marcado ? "golo_" : "golo_s_";
        switch (d) {
        case "p":
            return prefixo + "p";
        case "ld":
            return prefixo + "ld";
        case "pp":
            return prefixo + "pp";
        case "up":
            return prefixo + "up";
        default:
            return prefixo + "normal";
        }
    }

    private void sincronizarTempoAtual(Connection conn, int idJogo, int tempoSegundos) throws SQLException {
        PreparedStatement upd = conn.prepareStatement("UPDATE jogo_config SET tempo_atual_segundos = ? WHERE id_jogo = ?");
        upd.setInt(1, Math.max(0, tempoSegundos));
        upd.setInt(2, idJogo);
        upd.executeUpdate();
        upd.close();
    }

    /**
     * Atualiza o status de um jogo na tabela torneio_jogo.
     *
     * @param conn    conexão ativa
     * @param idJogo  ID do jogo
     * @param status  novo status ('scheduled', 'in-progress', 'completed')
     */
    private void atualizarStatusJogo(Connection conn, int idJogo, String status) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "UPDATE torneio_jogo SET status = ? WHERE id = ?");
        ps.setString(1, status);
        ps.setInt(2, idJogo);
        int rows = ps.executeUpdate();
        ps.close();
        if (rows > 0) {
            System.out.println("✅ Status do jogo #" + idJogo + " atualizado para: " + status);
        }
    }

    // ==================================================================
    // Tempo de jogo dos jogadores
    // ==================================================================

    /**
     * Devolve o tempo de jogo de cada jogador, calculado a partir da timeline
     * (em campo desde INICIO_JOGO/substituições até saída/fim de parte).
     */
    public ArrayList<JogadorJogo> getTemposJogo(int idJogo) {
        ArrayList<JogadorJogo> resultado = new ArrayList<>();
        Connection conn = null;
        try {
            conn = dbUtils.getConnection();

            Map<Integer, PlayerTempo> map = new HashMap<>();
            ArrayList<Integer> ordem = new ArrayList<>();

            PreparedStatement psj = conn.prepareStatement(
                    "SELECT jj.id_jogador, j.nome, jj.titular, jj.em_campo, jj.excluido_ate_segundos "
                            + "FROM jogo_jogador jj INNER JOIN jogador j ON j.id = jj.id_jogador "
                            + "WHERE jj.id_jogo = ? ORDER BY jj.numero ASC, j.nome ASC");
            psj.setInt(1, idJogo);
            ResultSet rsj = psj.executeQuery();
            while (rsj.next()) {
                PlayerTempo pt = new PlayerTempo();
                pt.idJogador = rsj.getInt("id_jogador");
                pt.nome = rsj.getString("nome");
                pt.titular = rsj.getBoolean("titular");
                pt.emCampo = rsj.getBoolean("em_campo");
                Object excluido = rsj.getObject("excluido_ate_segundos");
                pt.excluidoAt = excluido != null ? rsj.getInt("excluido_ate_segundos") : null;
                map.put(pt.idJogador, pt);
                ordem.add(pt.idJogador);
            }
            rsj.close();
            psj.close();

            // percorrer a timeline para reconstruir os intervalos em campo
            PreparedStatement pse = conn.prepareStatement(
                    "SELECT id_parte, tempo_segundos, tipo_evento, id_jogador, id_jogador_secundario "
                            + "FROM jogo_evento WHERE id_jogo = ? ORDER BY tempo_segundos ASC, id ASC");
            pse.setInt(1, idJogo);
            ResultSet rse = pse.executeQuery();
            int ultimoTempo = 0;
            while (rse.next()) {
                String tipo = rse.getString("tipo_evento");
                int t = rse.getInt("tempo_segundos");
                ultimoTempo = Math.max(ultimoTempo, t);
                int idJ = rse.getInt("id_jogador");
                int idJ2 = rse.getInt("id_jogador_secundario");
                switch (tipo) {
                case JogoEventoData.TIPO_INICIO_JOGO:
                    for (PlayerTempo pt : map.values()) {
                        if (pt.titular && !pt.emCampoSimulado) {
                            pt.entrarEmCampo(t);
                        }
                    }
                    break;
                case JogoEventoData.TIPO_SUBSTITUICAO:
                    PlayerTempo sai = map.get(idJ2);
                    if (sai != null && sai.emCampoSimulado) {
                        sai.sairDeCampo(t);
                    }
                    PlayerTempo entra = map.get(idJ);
                    if (entra != null && !entra.emCampoSimulado) {
                        entra.entrarEmCampo(t);
                    }
                    break;
                case JogoEventoData.TIPO_AZUL:
                case JogoEventoData.TIPO_VERMELHO:
                    PlayerTempo excluido = map.get(idJ);
                    if (excluido != null && excluido.emCampoSimulado) {
                        excluido.sairDeCampo(t);
                    }
                    break;
                case JogoEventoData.TIPO_FIM_PARTE:
                case JogoEventoData.TIPO_FIM_JOGO:
                    for (PlayerTempo pt : map.values()) {
                        if (pt.emCampoSimulado) {
                            pt.sairDeCampo(t);
                        }
                    }
                    break;
                default:
                    break;
                }
            }
            rse.close();
            pse.close();

            // fechar intervalos em aberto (jogo ainda em andamento)
            for (PlayerTempo pt : map.values()) {
                if (pt.emCampoSimulado) {
                    pt.acumular(ultimoTempo);
                }
            }

            for (Integer id : ordem) {
                PlayerTempo pt = map.get(id);
                JogadorJogo jj = new JogadorJogo(pt.idJogador, pt.nome, pt.titular, pt.emCampo, pt.excluidoAt,
                        pt.acumulado);
                resultado.add(jj);
            }
            dbUtils.closeConnection(conn);
            return resultado;
        } catch (SQLException e) {
            e.printStackTrace();
            fechar(conn);
        }
        return null;
    }

    // ==================================================================
    // Utilitários
    // ==================================================================

    private void setJogadorOuNull(PreparedStatement ps, int index, int idJogador) throws SQLException {
        if (idJogador > 0) {
            ps.setInt(index, idJogador);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private int getIntOuZero(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? 0 : value;
    }

    private String normalizarTempo(String tempo) {
        if (tempo == null || tempo.isBlank()) {
            return "00:00";
        }
        return tempo.trim();
    }

    public static String formatarTempo(int segundos) {
        int s = Math.max(0, segundos);
        int m = s / 60;
        s = s % 60;
        return String.format("%02d:%02d", m, s);
    }

    private boolean same(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private void rollback(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void fechar(Connection conn) {
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                dbUtils.closeConnection(conn);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // ==================================================================
    // Estado interno para cálculo do tempo de jogo
    // ==================================================================

    /**
     * Estado transitório de um jogador durante a reconstrução dos intervalos
     * em campo a partir da timeline.
     */
    private static class PlayerTempo {
        int idJogador;
        String nome;
        boolean titular;
        boolean emCampo;
        Integer excluidoAt;
        int acumulado;
        Integer inicioIntervalo;
        boolean emCampoSimulado;

        void entrarEmCampo(int t) {
            this.inicioIntervalo = t;
            this.emCampoSimulado = true;
        }

        void sairDeCampo(int t) {
            acumular(t);
            this.emCampoSimulado = false;
            this.inicioIntervalo = null;
        }

        void acumular(int t) {
            if (this.inicioIntervalo != null) {
                int delta = t - this.inicioIntervalo;
                if (delta > 0) {
                    this.acumulado += delta;
                }
                this.inicioIntervalo = null;
            }
        }
    }
}