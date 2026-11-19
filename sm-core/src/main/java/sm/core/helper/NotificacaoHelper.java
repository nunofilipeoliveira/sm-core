package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import sm.core.data.NotificacaoData;
import sm.core.security.AcessoNegadoException;
import sm.core.security.Identidade;

/**
 * Notificacoes na aplicacao (Micro Ciclo, desenho §5.5). Os destinatarios sao
 * calculados no momento do evento (D7); o texto guardado nao tem o motivo da
 * ausencia (D8). Sem limpeza automatica: a visibilidade e filtrada na leitura (D17).
 */
@Component
public class NotificacaoHelper {

	private static final Logger log = LoggerFactory.getLogger(NotificacaoHelper.class);

	private static final int MAXIMO_LISTA = 50;

	/*
	 * Visibilidade (D17): do proprio, funcionalidade ligada no tenant e, com treino,
	 * semana do treino >= segunda-feira da semana anterior e a conta ainda com a
	 * equipa (ou ADMIN). v5 (W7): o ATLETA ve so as suas e so CONVOCATORIA_ENTROU/SAIU (que tem
	 * id_jogador NULL, por isso o join a treino_ausencia nunca lhe junta um motivo).
	 * Parametros: tenant, utilizador, atleta, tenant, data minima, admin, atleta.
	 */
	private static final String SQL_VISIVEIS = "from notificacao n "
			+ "inner join funcionalidade f on f.id = n.id_funcionalidade "
			+ "left join funcionalidade_tenant ft on ft.id_funcionalidade = f.id and ft.tenant_id = ? "
			+ "left join treino t on t.id = n.id_treino "
			+ "left join micro_ciclo m on m.id = t.id_micro_ciclo "
			// no maximo uma linha (uq_treino_ausencia): nao duplica as contagens
			+ "left join treino_ausencia ta on ta.id_treino = n.id_treino and ta.id_jogador = n.id_jogador "
			+ "and ta.estado = 'ATIVA' "
			+ "where n.id_utilizador = ? and coalesce(ft.estado, f.estado_omissao) = '1' "
			+ "and (? = 0 or n.tipo in ('CONVOCATORIA_ENTROU', 'CONVOCATORIA_SAIU')) "
			+ "and (n.id_treino is null or (m.tenant_id = ? and t.`data` >= ? and (? = 1 or ? = 1 or exists (select 1 "
			+ "from utilizadores_escalao ue inner join escalao_epoca ee on ee.id = ue.id_escalao_epoca "
			+ "inner join epoca e on e.id = ee.id_epoca where e.estado = '1' and ue.id_utilizador = n.id_utilizador "
			+ "and ue.id_escalao_epoca = t.id_equipa)))) ";

	private final DBUtils dbUtils;

	public NotificacaoHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;
	}

	/**
	 * Q30 (a): contas ativas TREINADOR/ADMIN ligadas a equipa em utilizadores_escalao
	 * na epoca ativa. Ponto unico a mudar para a opcao (b).
	 */
	public List<Integer> destinatariosEquipa(Connection conn, int parmIdEquipa, int parmTenantId)
			throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select distinct u.id from utilizadores u "
				+ "inner join utilizadores_escalao ue on ue.id_utilizador = u.id "
				+ "inner join escalao_epoca ee on ee.id = ue.id_escalao_epoca "
				+ "inner join epoca e on e.id = ee.id_epoca "
				+ "where ue.id_escalao_epoca = ? and e.estado = '1' and e.tenant_id = ? and u.tenant_id = ? "
				+ "and u.estado = '1' and upper(u.perfil) in ('TREINADOR', 'ADMIN')");
		preparedStatement.setInt(1, parmIdEquipa);
		preparedStatement.setInt(2, parmTenantId);
		preparedStatement.setInt(3, parmTenantId);
		return ids(preparedStatement.executeQuery());
	}

	/** Q31: contas ADMIN ativas do tenant (rejeicao de um treino). */
	public List<Integer> destinatariosAdmin(Connection conn, int parmTenantId) throws SQLException {
		PreparedStatement preparedStatement = conn.prepareStatement("select id from utilizadores "
				+ "where tenant_id = ? and estado = '1' and upper(perfil) = 'ADMIN'");
		preparedStatement.setInt(1, parmTenantId);
		return ids(preparedStatement.executeQuery());
	}

	/** Uma linha por destinatario, na transacao de quem chama. Sem destinatarios: nada (R46). */
	public void cria(Connection conn, List<Integer> parmDestinatarios, String parmCodigoFuncionalidade, String parmTipo,
			Integer parmIdTreino, Integer parmIdJogador, String parmTexto) throws SQLException {
		if (parmDestinatarios.isEmpty()) {
			return;
		}
		PreparedStatement psFuncionalidade = conn.prepareStatement("select id from funcionalidade where codigo = ?");
		psFuncionalidade.setString(1, parmCodigoFuncionalidade);
		ResultSet rsFuncionalidade = psFuncionalidade.executeQuery();
		if (!rsFuncionalidade.next()) {
			return;
		}
		int idFuncionalidade = rsFuncionalidade.getInt(1);
		String texto = parmTexto.length() > 255 ? parmTexto.substring(0, 255) : parmTexto;
		PreparedStatement preparedStatement = conn.prepareStatement("insert into notificacao(id_utilizador, "
				+ "id_funcionalidade, tipo, id_treino, id_jogador, texto, datacriacao) values (?, ?, ?, ?, ?, ?, now())");
		for (Integer destinatario : parmDestinatarios) {
			preparedStatement.setInt(1, destinatario);
			preparedStatement.setInt(2, idFuncionalidade);
			preparedStatement.setString(3, parmTipo);
			setInteger(preparedStatement, 4, parmIdTreino);
			setInteger(preparedStatement, 5, parmIdJogador);
			preparedStatement.setString(6, texto);
			preparedStatement.addBatch();
		}
		preparedStatement.executeBatch();
		log.info("NotificacaoHelper | cria | tipo:{} treino:{} destinatarios:{}", parmTipo, parmIdTreino,
				parmDestinatarios.size());
	}

	private static void setVisiveis(PreparedStatement parmPs, Identidade parmIdentidade) throws SQLException {
		parmPs.setInt(1, parmIdentidade.getTenantId());
		parmPs.setInt(2, parmIdentidade.getIdUtilizador());
		parmPs.setInt(3, parmIdentidade.isAtleta() ? 1 : 0);
		parmPs.setInt(4, parmIdentidade.getTenantId());
		parmPs.setInt(5, MicroCicloHelper.somaDias(MicroCicloHelper.segundaDe(MicroCicloHelper.hoje()), -7));
		parmPs.setInt(6, parmIdentidade.isAdmin() ? 1 : 0);
		parmPs.setInt(7, parmIdentidade.isAtleta() ? 1 : 0);
	}

	public int conta(Identidade parmIdentidade) {
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn
					.prepareStatement("select count(*) " + SQL_VISIVEIS + "and n.data_leitura is null");
			setVisiveis(preparedStatement, parmIdentidade);
			ResultSet rs = preparedStatement.executeQuery();
			return rs.next() ? rs.getInt(1) : 0;
		} catch (SQLException e) {
			log.error("NotificacaoHelper | conta | Erro: " + e.getMessage(), e);
			throw new IllegalStateException("Erro de base de dados.", e);
		}
	}

	public List<NotificacaoData> lista(Identidade parmIdentidade) {
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement("select n.id, n.tipo, n.texto, n.id_treino, "
					+ "m.data_inicio, n.datacriacao, n.data_leitura, ta.categoria, ta.motivo " + SQL_VISIVEIS
					+ "order by n.datacriacao desc, n.id desc LIMIT " + MAXIMO_LISTA);
			setVisiveis(preparedStatement, parmIdentidade);
			ResultSet rs = preparedStatement.executeQuery();
			List<NotificacaoData> lista = new ArrayList<>();
			while (rs.next()) {
				NotificacaoData n = new NotificacaoData();
				n.setId(rs.getInt("id"));
				n.setTipo(rs.getString("tipo"));
				n.setTexto(rs.getString("texto"));
				int idTreino = rs.getInt("id_treino");
				n.setIdTreino(rs.wasNull() ? null : idTreino);
				int semana = rs.getInt("data_inicio");
				n.setSemanaTreino(rs.wasNull() ? null : semana);
				n.setDataCriacao(rs.getTimestamp("datacriacao"));
				n.setLida(rs.getTimestamp("data_leitura") != null);
				// D8: o motivo e lido agora, so para ausencias ainda ativas
				String categoria = rs.getString("categoria");
				if (categoria != null && n.getTipo() != null && n.getTipo().startsWith("AUSENCIA_")) {
					String motivo = rs.getString("motivo");
					String texto = MicroCicloHelper.categoriaTexto(categoria);
					n.setMotivo(motivo == null ? texto : texto + ": " + motivo);
				}
				lista.add(n);
			}
			return lista;
		} catch (SQLException e) {
			log.error("NotificacaoHelper | lista | Erro: " + e.getMessage(), e);
			throw new IllegalStateException("Erro de base de dados.", e);
		}
	}

	/** @throws AcessoNegadoException a notificacao nao e do utilizador */
	public boolean marcaLida(Identidade parmIdentidade, int parmIdNotificacao) {
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement psExiste = conn
					.prepareStatement("select 1 from notificacao where id = ? and id_utilizador = ?");
			psExiste.setInt(1, parmIdNotificacao);
			psExiste.setInt(2, parmIdentidade.getIdUtilizador());
			if (!psExiste.executeQuery().next()) {
				throw new AcessoNegadoException();
			}
			PreparedStatement preparedStatement = conn.prepareStatement("update notificacao set data_leitura = now() "
					+ "where id = ? and id_utilizador = ? and data_leitura is null");
			preparedStatement.setInt(1, parmIdNotificacao);
			preparedStatement.setInt(2, parmIdentidade.getIdUtilizador());
			preparedStatement.executeUpdate();
			return true;
		} catch (SQLException e) {
			log.error("NotificacaoHelper | marcaLida | Erro: " + e.getMessage(), e);
			throw new IllegalStateException("Erro de base de dados.", e);
		}
	}

	/** @return numero de notificacoes marcadas (so as visiveis) */
	public int marcaTodas(Identidade parmIdentidade) {
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement psIds = conn
					.prepareStatement("select n.id " + SQL_VISIVEIS + "and n.data_leitura is null");
			setVisiveis(psIds, parmIdentidade);
			List<Integer> ids = ids(psIds.executeQuery());
			PreparedStatement preparedStatement = conn.prepareStatement("update notificacao set data_leitura = now() "
					+ "where id = ? and id_utilizador = ? and data_leitura is null");
			int marcadas = 0;
			for (Integer id : ids) {
				preparedStatement.setInt(1, id);
				preparedStatement.setInt(2, parmIdentidade.getIdUtilizador());
				marcadas += preparedStatement.executeUpdate();
			}
			return marcadas;
		} catch (SQLException e) {
			log.error("NotificacaoHelper | marcaTodas | Erro: " + e.getMessage(), e);
			throw new IllegalStateException("Erro de base de dados.", e);
		}
	}

	private static void setInteger(PreparedStatement parmPs, int parmIndice, Integer parmValor) throws SQLException {
		if (parmValor == null) {
			parmPs.setNull(parmIndice, java.sql.Types.INTEGER);
		} else {
			parmPs.setInt(parmIndice, parmValor);
		}
	}

	private static List<Integer> ids(ResultSet rs) throws SQLException {
		List<Integer> ids = new ArrayList<>();
		while (rs.next()) {
			ids.add(rs.getInt(1));
		}
		return ids;
	}
}
