package sm.core.helper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
