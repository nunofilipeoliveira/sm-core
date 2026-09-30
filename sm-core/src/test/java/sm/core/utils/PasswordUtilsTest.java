package sm.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordUtilsTest {

	private static final String PASSWORD = "Password123!";

	@Test
	void encode_deveGerarHashBCrypt() {
		String hash = PasswordUtils.encode(PASSWORD);

		assertTrue(PasswordUtils.isEncoded(hash), "O hash gerado deve ser reconhecido como BCrypt");
		assertTrue(hash.startsWith("$2"), "O hash BCrypt deve comecar por $2");
		assertEquals(60, hash.length(), "O hash BCrypt tem 60 caracteres");
		assertFalse(hash.contains(PASSWORD), "O hash nao deve conter a password em texto simples");
	}

	@Test
	void encode_deveGerarHashesDiferentesParaAMesmaPassword() {
		assertNotEquals(PasswordUtils.encode(PASSWORD), PasswordUtils.encode(PASSWORD),
				"Cada hash deve ter um salt diferente");
	}

	@Test
	void encode_deveDevolverOValorParaPasswordNulaOuVazia() {
		assertNull(PasswordUtils.encode(null));
		assertEquals("", PasswordUtils.encode(""));
	}

	@Test
	void encodeIfNeeded_naoDeveReencriptarUmHashExistente() {
		String hash = PasswordUtils.encode(PASSWORD);

		assertEquals(hash, PasswordUtils.encodeIfNeeded(hash));
	}

	@Test
	void encodeIfNeeded_deveEncriptarTextoSimples() {
		String hash = PasswordUtils.encodeIfNeeded(PASSWORD);

		assertTrue(PasswordUtils.isEncoded(hash));
		assertTrue(PasswordUtils.matches(PASSWORD, hash));
	}

	@Test
	void isEncoded_deveDetetarPasswordsLegadas() {
		assertFalse(PasswordUtils.isEncoded("123"));
		assertFalse(PasswordUtils.isEncoded("oliveira"));
		assertFalse(PasswordUtils.isEncoded(""));
		assertFalse(PasswordUtils.isEncoded(null));
	}

	@Test
	void matches_deveAceitarPasswordLegadaEmTextoSimples() {
		assertTrue(PasswordUtils.matches(PASSWORD, PASSWORD));
		assertFalse(PasswordUtils.matches(PASSWORD, "outraPassword"));
	}

	@Test
	void matches_deveAceitarPasswordEncriptada() {
		assertTrue(PasswordUtils.matches(PASSWORD, PasswordUtils.encode(PASSWORD)));
		assertFalse(PasswordUtils.matches("outraPassword", PasswordUtils.encode(PASSWORD)));
	}

	@Test
	void matches_deveDevolverFalseParaValoresNulos() {
		assertFalse(PasswordUtils.matches(null, PASSWORD));
		assertFalse(PasswordUtils.matches(PASSWORD, null));
		assertFalse(PasswordUtils.matches(null, null));
	}

	@Test
	void migracaoLazy_deveConverterTextoSimplesEmBCryptSemAlterarAPassword() {
		// password tal como esta hoje na base de dados (texto simples)
		String storedLegacy = "oliveira";
		assertFalse(PasswordUtils.isEncoded(storedLegacy));

		// 1) o login valida a password legada
		assertTrue(PasswordUtils.matches("oliveira", storedLegacy));

		// 2) o login grava a password ja encriptada
		String newStored = PasswordUtils.encodeIfNeeded(storedLegacy);
		assertTrue(PasswordUtils.isEncoded(newStored));

		// 3) os logins seguintes (hash BCrypt) continuam a funcionar
		assertTrue(PasswordUtils.matches("oliveira", newStored));
		assertFalse(PasswordUtils.matches("errada", newStored));

		// 4) um novo login com o hash ja gravado nao o re-encripta
		assertEquals(newStored, PasswordUtils.encodeIfNeeded(newStored));
	}
}
