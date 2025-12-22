package sm.core.utils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;

public class TokenValidator {

	    private static final Logger log =
            LoggerFactory.getLogger(TokenValidator.class);

	private static final String SECRET_KEY = "chave_SM_login"; // A mesma chave usada para gerar o token
	private static final long EXTENSION_TIME = 15 * 60 * 1000; // 15 minutos em milissegundos
	private static final long RECENT_EXPIRATION_TIME = 5 * 60 * 1000; // 5 minutos anteriores

	public static boolean isTokenValid(String token) {
		try {
			// Verifica a assinatura e decodifica o token
			Claims claims = Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody();

			// Se o token for válido, você pode acessar os dados do token, como o assunto
			String username = claims.getSubject();
			// despejar em log informacoes do token
			log.info("Token valido para o usuario: " + username);

			String dateExpiration=claims.getExpiration().toString();
			log.info("Data de Expiracao do Token: " + dateExpiration);

			return true; // O token é válido
		} catch (SignatureException e) {
			log.error("Token invalido: " + e.getMessage());
			return false; // O token não é válido
		} catch (Exception e) {
			log.error("Erro ao validar o token: " + e.getMessage());
			return false; // O token não é válido
		}
	}

	public static String handleExpiredToken(String token) {
		try {
			// Decodifica o token sem validar a assinatura
			Claims claims = Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody();
			// Verifica a data de expiração
			Date expirationDate = claims.getExpiration();
			long currentTime = System.currentTimeMillis();
			long timeSinceExpiration = currentTime - expirationDate.getTime();
			// Se o token expirou recentemente, gere um novo token
			if (timeSinceExpiration >= 0 && timeSinceExpiration <= RECENT_EXPIRATION_TIME) {
				return generateNewToken(claims);
			}
		} catch (ExpiredJwtException e) {
			log.error("Token expirado: " + e.getMessage());

			Date expirationDate = e.getClaims().getExpiration();

			long currentTime = System.currentTimeMillis();
			long timeSinceExpiration = currentTime - expirationDate.getTime();
			// Se o token expirou recentemente, gere um novo token
			if (timeSinceExpiration >= 0 && timeSinceExpiration <= RECENT_EXPIRATION_TIME) {
				log.info("Token expirado recentemente, gerando um novo token.");
				String newToken = generateNewToken(e.getClaims());
				log.info("Novo token gerado: " + newToken);
				return newToken;
			}

			// Aqui você pode implementar a lógica para gerar um novo token
		} catch (Exception e) {
			log.error("Erro ao decodificar o token: " + e.getMessage());
		}
		return ""; // Retorna vazio se não for possível estender a sessão
	}

	private static String generateNewToken(Claims claims) {
		// Cria um novo token com a data de expiração estendida
		Map<String, Object> claimsMap = new HashMap<>(claims);
		long newExpirationTime = System.currentTimeMillis()  + EXTENSION_TIME;
		return Jwts.builder().setClaims(claimsMap).setSubject(claims.getSubject()).setIssuedAt(new Date())
				.setExpiration(new Date(newExpirationTime)).signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();
	}
}
