package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import sm.core.config.Auth;
import sm.core.config.SecurityProperties;
import sm.core.data.JogadorJogo;
import sm.core.data.JogoData;
import sm.core.security.AcessoNegadoException;
import sm.core.security.Identidade;
import sm.core.security.TipoRecurso;

/**
 * Verificacoes de pertenca dos recursos (D4) usadas pela matriz de acesso
 * ({@code AutorizacaoInterceptor}) e pelos controllers para os ids que vem no corpo.
 */
@Component
public class AutorizacaoHelper {

	private static final Logger log = LoggerFactory.getLogger(AutorizacaoHelper.class);

	private final DBUtils dbUtils;
	private final Auth auth;
	private final AtomicLong negadosDesdeUltimoLog = new AtomicLong();
	private final AtomicLong ultimoLogMillis = new AtomicLong();

	@Autowired
	private FuncionalidadeHelper funcionalidadeHelper;

	public AutorizacaoHelper(DBUtils dbUtils, SecurityProperties properties) {
		this.dbUtils = dbUtils;
		this.auth = properties.getAuth();
	}

	private static String sqlPertenca(TipoRecurso parmTipo) {
		switch (parmTipo) {
		case JOGADOR:
			return "select 1 from jogador where id=? and tenant_id=? LIMIT 1";
		case STAFF:
			return "select 1 from staff where id=? and tenant_id=? LIMIT 1";
		case UTILIZADOR:
			return "select 1 from utilizadores where id=? and tenant_id=? LIMIT 1";
		case EPOCA:
			return "select 1 from epoca where id=? and tenant_id=? LIMIT 1";
		case EQUIPA:
			return "select 1 from escalao_epoca ee inner join epoca e on e.id=ee.id_epoca where ee.id=? and e.tenant_id=? LIMIT 1";
		case JOGO:
			return "select 1 from jogo j inner join epoca e on e.id=j.epoca_id where j.id=? and e.tenant_id=? LIMIT 1";
		case PRESENCA:
			return "select 1 from presencas p inner join escalao_epoca ee on ee.id=p.id_equipa "
					+ "inner join epoca e on e.id=ee.id_epoca where p.id=? and e.tenant_id=? LIMIT 1";
		case EVENTO:
			return "select 1 from jogo_evento ev inner join jogo j on j.id=ev.id_jogo "
					+ "inner join epoca e on e.id=j.epoca_id where ev.id=? and e.tenant_id=? LIMIT 1";
		case MICRO_CICLO:
			return "select 1 from micro_ciclo where id=? and tenant_id=? LIMIT 1";
		case TREINO:
			return "select 1 from treino t inner join micro_ciclo m on m.id=t.id_micro_ciclo "
					+ "where t.id=? and m.tenant_id=? LIMIT 1";
		default:
			throw new IllegalArgumentException("Tipo de recurso sem regra: " + parmTipo);
		}
	}

	/** O recurso pertence ao tenant. Em erro de BD devolve {@code false} (recusa). */
	public boolean pertenceAoTenant(TipoRecurso parmTipo, int parmId, int parmTenantId) {
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(sqlPertenca(parmTipo));
			preparedStatement.setInt(1, parmId);
			preparedStatement.setInt(2, parmTenantId);
			return preparedStatement.executeQuery().next();
		} catch (SQLException e) {
			log.error("Erro em pertenceAoTenant: " + e.getMessage(), e);
			return false;
		}
	}

	/** O jogador consta no jogo (os mesmos jogos de getJogosByJogadorId). */
	public boolean jogoDoAtleta(int parmIdJogo, int parmIdJogador) {
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement(
					"select 1 from jogo_jogador where id_jogo=? and id_jogador=? and estado<>'INDISPONÍVEL' LIMIT 1");
			preparedStatement.setInt(1, parmIdJogo);
			preparedStatement.setInt(2, parmIdJogador);
			return preparedStatement.executeQuery().next();
		} catch (SQLException e) {
			log.error("Erro em jogoDoAtleta: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * D24: treino aprovado alguma vez, em APROVADO ou CANCELADO, do tenant, em que o
	 * jogador (ativo) e convocado efetivo (ACRESCENTADO ou ainda na equipa).
	 */
	public boolean treinoDoAtleta(int parmIdTreino, int parmIdJogador, int parmTenantId) {
		try (Connection conn = dbUtils.getConnection()) {
			PreparedStatement preparedStatement = conn.prepareStatement("select 1 from treino t "
					+ "inner join micro_ciclo m on m.id=t.id_micro_ciclo "
					+ "inner join treino_convocado tc on tc.id_treino=t.id and tc.id_jogador=? and tc.estado='1' "
					+ "inner join jogador j on j.id=tc.id_jogador and j.estado='1' "
					+ "where t.id=? and m.tenant_id=? and t.estado in ('APROVADO','CANCELADO') "
					+ "and t.data_aprovacao is not null and (tc.origem='ACRESCENTADO' or exists (select 1 "
					+ "from escalao_epoca_jogador eej where eej.id_escalao_epoca=t.id_equipa and eej.id_jogador=tc.id_jogador)) "
					+ "LIMIT 1");
			preparedStatement.setInt(1, parmIdJogador);
			preparedStatement.setInt(2, parmIdTreino);
			preparedStatement.setInt(3, parmTenantId);
			return preparedStatement.executeQuery().next();
		} catch (SQLException e) {
			log.error("Erro em treinoDoAtleta: " + e.getMessage(), e);
			return false;
		}
	}

	/**
	 * Staff: o recurso e do tenant do token. ATLETA: so o proprio jogador e os seus jogos.
	 */
	public boolean temAcessoAoRecurso(Identidade parmIdentidade, TipoRecurso parmTipo, int parmId) {
		if (parmIdentidade.isAtleta()) {
			Integer jid = parmIdentidade.getIdJogador();
			if (jid == null) {
				return false;
			}
			if (parmTipo == TipoRecurso.JOGADOR) {
				return parmId == jid;
			}
			if (parmTipo == TipoRecurso.JOGO) {
				return jogoDoAtleta(parmId, jid);
			}
			if (parmTipo == TipoRecurso.TREINO) {
				return treinoDoAtleta(parmId, jid, parmIdentidade.getTenantId());
			}
			return false;
		}
		return pertenceAoTenant(parmTipo, parmId, parmIdentidade.getTenantId());
	}

	/**
	 * Verifica um id que vem no corpo do pedido. Sem identidade (token antigo ou
	 * ausente) nao faz nada: o filtro de token ja tratou do pedido.
	 */
	public void verificaCorpo(HttpServletRequest parmRequest, TipoRecurso parmTipo, int parmId) {
		Identidade identidade = Identidade.de(parmRequest);
		if (identidade != null && !temAcessoAoRecurso(identidade, parmTipo, parmId)) {
			nega(identidade, parmRequest, "corpo " + parmTipo + "=" + parmId);
		}
	}

	/**
	 * Recusa: ATLETA sempre; staff so com {@code authorization-mode=ENFORCE}, senao
	 * fica so o registo "REPORT: seria negado".
	 *
	 * @throws AcessoNegadoException quando a recusa e aplicada
	 */
	public void nega(Identidade parmIdentidade, HttpServletRequest parmRequest, String parmMotivo) {
		boolean aplica = parmIdentidade.isAtleta() || auth.isAuthorizationEnforce();
		logNegado(aplica ? "negado" : "REPORT: seria negado", parmIdentidade, parmRequest, parmMotivo);
		if (aplica) {
			throw new AcessoNegadoException();
		}
	}

	/**
	 * Identidade obrigatoria (token v2) e tenant da rota igual ao do token. Aplicado
	 * sempre, em REPORT e em ENFORCE (D9).
	 *
	 * @throws AcessoNegadoException sem identidade ou com outro tenant
	 */
	public Identidade exigeIdentidade(HttpServletRequest parmRequest, int parmTenantRota) {
		Identidade identidade = Identidade.de(parmRequest);
		if (identidade == null || identidade.getTenantId() != parmTenantRota) {
			throw new AcessoNegadoException();
		}
		return identidade;
	}

	/**
	 * {@link #exigeIdentidade} e a funcionalidade ligada no tenant. Aplicado sempre (D9, R59).
	 *
	 * @throws AcessoNegadoException sem identidade, com outro tenant ou funcionalidade desligada
	 */
	public Identidade exigeFuncionalidade(HttpServletRequest parmRequest, int parmTenantRota, String parmCodigo) {
		Identidade identidade = exigeIdentidade(parmRequest, parmTenantRota);
		if (!funcionalidadeHelper.isAtiva(identidade.getTenantId(), parmCodigo)) {
			throw new AcessoNegadoException();
		}
		return identidade;
	}

	/** Q7: para o ATLETA, o obs de cada jogador do jogo (motivo de indisponibilidade) vem a null. */
	public static void limpaObsJogadores(JogoData parmJogo) {
		if (parmJogo == null) {
			return;
		}
		limpaObsJogadores(parmJogo.getJogadores());
		if (parmJogo.getConfig() != null) {
			limpaObsJogadores(parmJogo.getConfig().getJogadores());
		}
	}

	public static void limpaObsJogadores(List<JogadorJogo> parmJogadores) {
		if (parmJogadores != null) {
			for (JogadorJogo jogador : parmJogadores) {
				jogador.setObs(null);
			}
		}
	}

	/** Log com throttling (R22), como no TokenAuthenticationFilter. */
	private void logNegado(String parmTipo, Identidade parmIdentidade, HttpServletRequest parmRequest,
			String parmMotivo) {
		long count = negadosDesdeUltimoLog.incrementAndGet();
		long now = System.currentTimeMillis();
		long interval = Math.max(1000L, auth.getReportLogIntervalMillis());
		long last = ultimoLogMillis.get();
		if (now - last >= interval && ultimoLogMillis.compareAndSet(last, now)) {
			log.warn("AutorizacaoInterceptor | {} pedidos recusados desde o ultimo aviso | {} | user={} tid={} perfil={} "
					+ "uri={} motivo={}", count, parmTipo, parmIdentidade.getUser(), parmIdentidade.getTenantId(),
					parmIdentidade.getPerfil(), parmRequest.getRequestURI(), parmMotivo);
			negadosDesdeUltimoLog.set(0L);
		} else {
			log.debug("AutorizacaoInterceptor | {} | user={} tid={} perfil={} uri={} motivo={}", parmTipo,
					parmIdentidade.getUser(), parmIdentidade.getTenantId(), parmIdentidade.getPerfil(),
					parmRequest.getRequestURI(), parmMotivo);
		}
	}
}
