package sm.core.utils;

import java.util.Date;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class TokenGenerator {

	private static final String SECRET_KEY = "chave_SM_login"; // Mantenha isso seguro
	private static final long EXPIRATION_TIME = 30 * 60 * 1000;; // 30 minutos

	public static String generateToken(String username) {
		return Jwts.builder().setSubject(username).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
				.signWith(SignatureAlgorithm.HS512, SECRET_KEY).compact();
	}
}
