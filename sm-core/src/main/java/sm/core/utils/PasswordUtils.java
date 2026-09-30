package sm.core.utils;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utilitarios de password do SM-Core.
 *
 * <p>
 * As passwords passam a ser guardadas na base de dados (coluna
 * utilizadores.password) apenas em formato BCrypt (prefixo "$2a$/$2b$/$2y$").
 * As passwords legadas, guardadas em texto simples, continuam a ser aceites no
 * login e sao convertidas para BCrypt nesse mesmo login (migracao lazy - ver
 * {@code LoginHelper.Dologin}).
 * </p>
 */
public final class PasswordUtils {

	private static final Logger log = LoggerFactory.getLogger(PasswordUtils.class);

	/**
	 * Custo do BCrypt (2^strength iteracoes). 10 e o valor por omissao do Spring.
	 */
	private static final int BCRYPT_STRENGTH = 10;

	/**
	 * Padrao de uma password ja encriptada com BCrypt: "$2a$" + custo (2 digitos) +
	 * "$" + 53 caracteres (salt + hash) = 60 caracteres.
	 */
	private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(BCRYPT_STRENGTH);

	private PasswordUtils() {
		// classe utilitaria: nao deve ser instanciada
	}

	/**
	 * Encripta a password recebida com BCrypt.
	 *
	 * @param parmRawPassword password em texto simples
	 * @return hash BCrypt; se a password for nula ou vazia devolve o valor recebido
	 */
	public static String encode(String parmRawPassword) {

		if (parmRawPassword == null || parmRawPassword.isEmpty()) {
			return parmRawPassword;
		}

		return ENCODER.encode(parmRawPassword);
	}

	/**
	 * Encripta a password apenas se esta ainda nao estiver encriptada. Garante
	 * idempotencia (evita reencriptar um hash BCrypt ja existente).
	 *
	 * @param parmPassword password em texto simples ou ja encriptada
	 * @return password encriptada
	 */
	public static String encodeIfNeeded(String parmPassword) {

		if (parmPassword == null || parmPassword.isEmpty()) {
			return parmPassword;
		}

		return isEncoded(parmPassword) ? parmPassword : ENCODER.encode(parmPassword);
	}

	/**
	 * Indica se o valor guardado na base de dados ja esta encriptado (BCrypt).
	 *
	 * @param parmStoredPassword valor da coluna password
	 * @return true se for um hash BCrypt
	 */
	public static boolean isEncoded(String parmStoredPassword) {

		return parmStoredPassword != null && BCRYPT_PATTERN.matcher(parmStoredPassword).matches();
	}

	/**
	 * Valida a password recebida no login contra o valor guardado na base de dados,
	 * suportando os dois formatos durante a migracao:
	 * <ul>
	 * <li>BCrypt - comparacao com BCryptPasswordEncoder.matches;</li>
	 * <li>Texto simples (legado) - comparacao directa.</li>
	 * </ul>
	 *
	 * @param parmRawPassword    password recebida no login (texto simples)
	 * @param parmStoredPassword password guardada na base de dados
	 * @return true se a password estiver correcta
	 */
	public static boolean matches(String parmRawPassword, String parmStoredPassword) {

		if (parmRawPassword == null || parmStoredPassword == null) {
			return false;
		}

		if (isEncoded(parmStoredPassword)) {
			try {
				return ENCODER.matches(parmRawPassword, parmStoredPassword);
			} catch (IllegalArgumentException e) {
				log.warn("Password guardada com formato BCrypt invalido: " + e.getMessage());
				return false;
			}
		}

		// password legada, ainda em texto simples
		return parmStoredPassword.equals(parmRawPassword);
	}
}
