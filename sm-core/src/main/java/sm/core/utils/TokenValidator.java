package sm.core.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import sm.core.security.Identidade;

public class TokenValidator {

	    private static final Logger log =
            LoggerFactory.getLogger(TokenValidator.class);

	private static final long RECENT_EXPIRATION_TIME = 30 * 60 * 1000; // 30 minutos anteriores (D7)

	/**
	 * Valida a assinatura e a expiracao do token.
	 *
	 * <p>Nota: os logs sao de nivel {@code debug} de proposito — em ataque com
	 * muitos tokens invalidos o registo de erro por pedido seria ele proprio um
	 * vetor de esgotamento de disco/CPU. O log (com throttling) e feito pelo
	 * {@code TokenAuthenticationFilter}.</p>
	 *
	 * @return as claims quando o token e valido, {@code null} caso contrario
	 */
	public static Claims parseValidToken(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			return Jwts.parser().setSigningKey(TokenGenerator.getSecretKey()).parseClaimsJws(token).getBody();
		} catch (Exception e) {
			log.debug("Token invalido: " + e.getMessage());
			return null;
		}
	}

	/**
	 * Valida o token (assinatura e expiracao) e devolve a identidade v2.
	 *
	 * @return a identidade, ou {@code null} se o token for invalido ou antigo (sem {@code ver=2})
	 */
	public static Identidade parseIdentidade(String token) {
		Claims claims = parseValidToken(token);
		return claims == null ? null : identidadeDe(claims);
	}

	/**
	 * Le a identidade das claims (validas ou expiradas).
	 *
	 * @return a identidade, ou {@code null} se nao for um token v2 com {@code uid} e {@code tid}
	 */
	public static Identidade identidadeDe(Claims claims) {
		if (claims == null) {
			return null;
		}
		Integer versao = inteiro(claims.get("ver"));
		Integer uid = inteiro(claims.get("uid"));
		Integer tid = inteiro(claims.get("tid"));
		if (versao == null || versao != TokenGenerator.TOKEN_VERSAO || uid == null || tid == null
				|| claims.getSubject() == null) {
			return null;
		}
		Object perfil = claims.get("perfil");
		return new Identidade(uid, claims.getSubject(), tid, perfil == null ? null : perfil.toString(),
				inteiro(claims.get("jid")));
	}

	private static Integer inteiro(Object valor) {
		return valor instanceof Number ? ((Number) valor).intValue() : null;
	}

	public static boolean isTokenValid(String token) {
		Identidade identidade = parseIdentidade(token);
		if (identidade == null) {
			log.debug("Token invalido ou antigo.");
			return false;
		}
		log.debug("Token valido para o utilizador: " + identidade.getUser());
		return true;
	}

	/**
	 * Devolve as claims de um token assinado que expirou ha no maximo
	 * {@code RECENT_EXPIRATION_TIME}. Nao gera token: quem chama revalida o
	 * utilizador na BD e so depois emite um token novo.
	 *
	 * @return as claims expiradas, ou {@code null} se o token nao for renovavel
	 */
	public static Claims getClaimsExpiradasRecentes(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			// Um token ainda valido nao e renovavel (comportamento atual)
			Jwts.parser().setSigningKey(TokenGenerator.getSecretKey()).parseClaimsJws(token);
		} catch (ExpiredJwtException e) {
			long timeSinceExpiration = System.currentTimeMillis() - e.getClaims().getExpiration().getTime();
			if (timeSinceExpiration >= 0 && timeSinceExpiration <= RECENT_EXPIRATION_TIME) {
				log.debug("Token expirado recentemente.");
				return e.getClaims();
			}
			log.debug("Token expirado fora da janela de renovacao.");
		} catch (Exception e) {
			log.debug("Erro ao decodificar o token: " + e.getMessage());
		}
		return null;
	}
}
