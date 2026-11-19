package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import sm.core.data.FuncionalidadeData;
import sm.core.security.PedidoInvalidoException;

/**
 * Funcionalidades ligadas/desligadas por tenant (tabelas da V6). Sem linha em
 * funcionalidade_tenant vale funcionalidade.estado_omissao. Sem cache: o efeito
 * no backend e imediato (R63).
 */
@Component
public class FuncionalidadeHelper {

	private static final Logger log = LoggerFactory.getLogger(FuncionalidadeHelper.class);

	public static final String MICRO_CICLO = "MICRO_CICLO";

	private static final ZoneId LISBOA = ZoneId.of("Europe/Lisbon");

	private static final String SQL_ADMIN = "select f.codigo, f.nome, f.descricao, "
			+ "coalesce(ft.estado, f.estado_omissao) as estado, ft.data_alteracao, u.nome as alterado_por "
			+ "from funcionalidade f "
			+ "left join funcionalidade_tenant ft on ft.id_funcionalidade = f.id and ft.tenant_id = ? "
			+ "left join utilizadores u on u.id = ft.id_utilizador_alteracao ";

	private final DBUtils dbUtils;

	public FuncionalidadeHelper(DBUtils dbUtils) {
		this.dbUtils = dbUtils;
	}

	/** Estado da funcionalidade no tenant. Codigo desconhecido ou erro de SQL (ex.: V6 em falta) = desligada. */
	public boolean isAtiva(int parmTenantId, String parmCodigo) {
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select coalesce(ft.estado, f.estado_omissao) from funcionalidade f "
							+ "left join funcionalidade_tenant ft on ft.id_funcionalidade = f.id and ft.tenant_id = ? "
							+ "where f.codigo = ?");
			preparedStatement.setInt(1, parmTenantId);
			preparedStatement.setString(2, parmCodigo);
			ResultSet rs = preparedStatement.executeQuery();
			return rs.next() && "1".equals(rs.getString(1));
		} catch (SQLException e) {
			log.error("FuncionalidadeHelper | isAtiva | Erro: " + e.getMessage(), e);
			return false;
		}
	}

	/** Todas as funcionalidades do catalogo, so com codigo e ativo. Erro de SQL = lista vazia (tudo desligado). */
	public List<Map<String, Object>> getFuncionalidades(int parmTenantId) {
		List<Map<String, Object>> lista = new ArrayList<>();
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select f.codigo, coalesce(ft.estado, f.estado_omissao) as estado from funcionalidade f "
							+ "left join funcionalidade_tenant ft on ft.id_funcionalidade = f.id and ft.tenant_id = ? "
							+ "order by f.codigo");
			preparedStatement.setInt(1, parmTenantId);
			ResultSet rs = preparedStatement.executeQuery();
			while (rs.next()) {
				Map<String, Object> item = new LinkedHashMap<>();
				item.put("codigo", rs.getString("codigo"));
				item.put("ativo", "1".equals(rs.getString("estado")));
				lista.add(item);
			}
		} catch (SQLException e) {
			log.error("FuncionalidadeHelper | getFuncionalidades | Erro: " + e.getMessage(), e);
		}
		return lista;
	}

	/** Catalogo com estado, quem alterou e quando, e o impacto de desligar (ADMIN). */
	public ArrayList<FuncionalidadeData> getFuncionalidadesAdmin(int parmTenantId) {
		ArrayList<FuncionalidadeData> lista = new ArrayList<>();
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(SQL_ADMIN + "order by f.nome");
			preparedStatement.setInt(1, parmTenantId);
			ResultSet rs = preparedStatement.executeQuery();
			while (rs.next()) {
				lista.add(carregaAdmin(conn, rs, parmTenantId));
			}
		} catch (SQLException e) {
			log.error("FuncionalidadeHelper | getFuncionalidadesAdmin | Erro: " + e.getMessage(), e);
		}
		return lista;
	}

	/**
	 * Liga/desliga a funcionalidade no tenant e regista no historico, numa transacao.
	 *
	 * @return o item atualizado (como em getFuncionalidadesAdmin)
	 * @throws PedidoInvalidoException codigo em falta ou desconhecido
	 */
	public FuncionalidadeData setFuncionalidade(int parmTenantId, String parmCodigo, boolean parmAtivo,
			int parmIdUtilizador) {
		if (parmCodigo == null || parmCodigo.isBlank()) {
			throw new PedidoInvalidoException("Código da funcionalidade em falta.");
		}
		String estado = parmAtivo ? "1" : "0";
		Connection conn = null;
		try {
			conn = dbUtils.getConnection();
			conn.setAutoCommit(false);

			PreparedStatement psId = conn.prepareStatement("select id from funcionalidade where codigo = ?");
			psId.setString(1, parmCodigo.trim());
			ResultSet rsId = psId.executeQuery();
			if (!rsId.next()) {
				conn.rollback();
				throw new PedidoInvalidoException("Funcionalidade desconhecida: " + parmCodigo);
			}
			int idFuncionalidade = rsId.getInt("id");

			PreparedStatement psEstado = conn.prepareStatement(
					"insert into funcionalidade_tenant(id_funcionalidade, tenant_id, estado, data_alteracao, "
							+ "id_utilizador_alteracao) values (?, ?, ?, now(), ?) "
							+ "on duplicate key update estado = values(estado), data_alteracao = now(), "
							+ "id_utilizador_alteracao = values(id_utilizador_alteracao)");
			psEstado.setInt(1, idFuncionalidade);
			psEstado.setInt(2, parmTenantId);
			psEstado.setString(3, estado);
			psEstado.setInt(4, parmIdUtilizador);
			psEstado.executeUpdate();

			PreparedStatement psHistorico = conn.prepareStatement(
					"insert into funcionalidade_tenant_historico(id_funcionalidade, tenant_id, estado, `data`, "
							+ "id_utilizador) values (?, ?, ?, now(), ?)");
			psHistorico.setInt(1, idFuncionalidade);
			psHistorico.setInt(2, parmTenantId);
			psHistorico.setString(3, estado);
			psHistorico.setInt(4, parmIdUtilizador);
			psHistorico.executeUpdate();

			conn.commit();
			log.info("FuncionalidadeHelper | setFuncionalidade | tenant:{} codigo:{} estado:{} utilizador:{}",
					parmTenantId, parmCodigo, estado, parmIdUtilizador);

			PreparedStatement psItem = conn.prepareStatement(SQL_ADMIN + "where f.id = ?");
			psItem.setInt(1, parmTenantId);
			psItem.setInt(2, idFuncionalidade);
			ResultSet rsItem = psItem.executeQuery();
			return rsItem.next() ? carregaAdmin(conn, rsItem, parmTenantId) : null;
		} catch (SQLException e) {
			log.error("FuncionalidadeHelper | setFuncionalidade | Erro: " + e.getMessage(), e);
			if (conn != null) {
				try {
					conn.rollback();
				} catch (Exception ex) {
					// ignore
				}
			}
			// sem gravacao: o ADMIN tem de ver o erro (500), nao um resultado vazio
			throw new IllegalStateException("Erro ao gravar a funcionalidade.", e);
		} finally {
			dbUtils.closeConnection(conn);
		}
	}

	private FuncionalidadeData carregaAdmin(Connection conn, ResultSet rs, int parmTenantId) throws SQLException {
		FuncionalidadeData item = new FuncionalidadeData();
		item.setCodigo(rs.getString("codigo"));
		item.setNome(rs.getString("nome"));
		item.setDescricao(rs.getString("descricao"));
		item.setAtivo("1".equals(rs.getString("estado")));
		item.setDataAlteracao(rs.getTimestamp("data_alteracao"));
		item.setAlteradoPor(rs.getString("alterado_por"));
		if (item.isAtivo() && MICRO_CICLO.equals(item.getCodigo())) {
			item.setImpactoDesligar(impactoDesligarMicroCiclo(conn, parmTenantId));
		}
		return item;
	}

	/**
	 * Treinos aprovados sem presenca de hoje em diante e notificacoes por ler do Micro Ciclo no tenant.
	 * As tabelas sao da V7: sem elas (Entrega A sozinha) devolve null, sem falhar a lista.
	 */
	private String impactoDesligarMicroCiclo(Connection conn, int parmTenantId) {
		int hoje = Integer.parseInt(LocalDate.now(LISBOA).format(DateTimeFormatter.ofPattern("yyyyMMdd")));
		try {
			PreparedStatement psTreinos = conn.prepareStatement("select count(*) from treino t "
					+ "inner join micro_ciclo m on m.id = t.id_micro_ciclo "
					+ "where m.tenant_id = ? and t.estado = 'APROVADO' and t.id_presenca is null and t.`data` >= ?");
			psTreinos.setInt(1, parmTenantId);
			psTreinos.setInt(2, hoje);
			ResultSet rsTreinos = psTreinos.executeQuery();
			int treinos = rsTreinos.next() ? rsTreinos.getInt(1) : 0;

			PreparedStatement psNotificacoes = conn.prepareStatement("select count(*) from notificacao n "
					+ "inner join funcionalidade f on f.id = n.id_funcionalidade "
					+ "inner join utilizadores u on u.id = n.id_utilizador "
					+ "where u.tenant_id = ? and f.codigo = ? and n.data_leitura is null");
			psNotificacoes.setInt(1, parmTenantId);
			psNotificacoes.setString(2, MICRO_CICLO);
			ResultSet rsNotificacoes = psNotificacoes.executeQuery();
			int notificacoes = rsNotificacoes.next() ? rsNotificacoes.getInt(1) : 0;

			return treinos + " treinos aprovados futuros deixam de estar visíveis aos atletas; " + notificacoes
					+ " notificações por ler deixam de aparecer";
		} catch (SQLException e) {
			log.warn("FuncionalidadeHelper | impactoDesligarMicroCiclo | Sem contagens (V7 em falta?): {}",
					e.getMessage());
			return null;
		}
	}
}
