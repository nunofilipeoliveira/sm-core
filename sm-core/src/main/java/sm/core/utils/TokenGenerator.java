package sm.core.utils;

import java.util.Date;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import sm.core.security.Identidade;

public class TokenGenerator {

	// Valor por omissao (o atual); substituido por sm.core.security.auth.jwt-secret se definido (BE-opt)
	private static volatile String secretKey = "chave_SM_login";

	/** Define a chave de assinatura. Valor vazio ou nulo mantem a chave por omissao. */
	public static void setSecretKey(String parmSecretKey) {
		if (parmSecretKey != null && !parmSecretKey.isBlank()) {
			secretKey = parmSecretKey.trim();
		}
	}

	/** Chave usada a gerar e a validar os tokens (a mesma nos dois lados). */
	static String getSecretKey() {
		return secretKey;
	}
	private static final long EXPIRATION_TIME = 15 * 60 * 1000; // 15 minutos em milissegundos

	/** Versao das claims de identidade (D2). Tokens sem esta versao sao "antigos". */
	public static final int TOKEN_VERSAO = 2;

	/** Token v2 com a identidade: uid, tid, perfil, jid (so se existir) e ver=2. */
	public static String generateToken(Identidade identidade) {
		JwtBuilder builder = Jwts.builder().setSubject(identidade.getUser())
				.claim("uid", identidade.getIdUtilizador())
				.claim("tid", identidade.getTenantId())
				.claim("perfil", identidade.getPerfil())
				.claim("ver", TOKEN_VERSAO);
		if (identidade.getIdJogador() != null) {
			builder.claim("jid", identidade.getIdJogador());
		}
		return builder.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
				.signWith(SignatureAlgorithm.HS512, secretKey).compact();
	}

	/**
	 * Token antigo (so com o username).
	 *
	 * @deprecated ja nao e usado no codigo principal. Mantido porque os testes de
	 *             seguranca ainda o usam e o CI compila os testes ({@code -DskipTests}).
	 *             Remover quando esses testes passarem a usar {@link #generateToken(Identidade)}.
	 */
	@Deprecated
	public static String generateToken(String username) {
		return Jwts.builder().setSubject(username).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
				.signWith(SignatureAlgorithm.HS512, secretKey).compact();
	}
}
