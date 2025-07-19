package sm.core.utils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;

public class TokenValidator {

	private static final String SECRET_KEY = "chave_SM_login"; // A mesma chave usada para gerar o token
	private static final long EXTENSION_TIME = 5 * 60 * 1000; // 5 minutos em milissegundos
	private static final long RECENT_EXPIRATION_TIME = 3 * 60 * 1000; // 3 minutos anteriores

	public static boolean isTokenValid(String token) {
		try {
			// Verifica a assinatura e decodifica o token
			Claims claims = Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody();

			// Se o token for válido, você pode acessar os dados do token, como o assunto
			String username = claims.getSubject();
			System.out.println("Token válido para o usuário: " + username);
			return true; // O token é válido
		} catch (SignatureException e) {
			System.out.println("Token inválido: " + e.getMessage());
			return false; // O token não é válido
		} catch (Exception e) {
			System.out.println("Erro ao validar o token: " + e.getMessage());
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
			System.out.println("Token expirado: " + e.getMessage());

			Date expirationDate = e.getClaims().getExpiration();

			long currentTime = System.currentTimeMillis();
			long timeSinceExpiration = currentTime - expirationDate.getTime();
			// Se o token expirou recentemente, gere um novo token
			if (timeSinceExpiration >= 0 && timeSinceExpiration <= RECENT_EXPIRATION_TIME) {
				return generateNewToken(e.getClaims());
			}

			// Aqui você pode implementar a lógica para gerar um novo token
		} catch (Exception e) {
			System.out.println("Erro ao decodificar o token: " + e.getMessage());
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
