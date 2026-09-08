package sm.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Valida que o JSON enviado pelo frontend (marcar_presenca / PUT /sm/presenca e
 * PUT /sm/updatepresenca) é corretamente desserializado para PresencaData.
 *
 * Regressão: a adição do campo classificacao introduziu um segundo construtor
 * em PresencaJogadorData, removendo o construtor por omissão implícito e
 * impedindo o Jackson de desserializar (InvalidDefinitionException: no
 * Creators, like default constructor, exist).
 */
class PresencaDataDeserializationTest {

	/**
	 * ObjectMapper configurado como o Spring Boot usa por omissão: ignora
	 * propriedades desconhecidas (o frontend envia estilo_estado e apagar, que
	 * não existem nos DTOs do backend).
	 */
	private ObjectMapper newSpringBootLikeMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
	}

	@Test
	void testDeserializacaoPayloadFrontendComClassificacao() throws Exception {
		String json = "{" +
				"\"id\": 123," +
				"\"data\": 20260908," +
				"\"hora\": \"19:30\"," +
				"\"id_escalao\": 7," +
				"\"escalao_descricao\": \"Sub-12\"," +
				"\"data_criacao\": \"20260908\"," +
				"\"id_utilizador_criacao\": 1," +
				"\"user_criacao\": \"admin\"," +
				"\"jogadoresPresenca\": [" +
				"  {\"id_jogador\": 10, \"nome_jogador\": \"Joao\", \"estado\": \"Presente\", \"motivo\": \"\", \"estilo_estado\": \"\", \"apagar\": false, \"classificacao\": 4}," +
				"  {\"id_jogador\": 11, \"nome_jogador\": \"Maria\", \"estado\": \"Ausente (Avisou)\", \"motivo\": \"Doenca\", \"estilo_estado\": \"\", \"apagar\": false, \"classificacao\": null}" +
				"]," +
				"\"staffPresenca\": [" +
				"  {\"id_staff\": 3, \"nome_staff\": \"Treinador X\", \"estado\": \"Presente\", \"motivo\": \"\"}" +
				"]}";

		PresencaData presenca = newSpringBootLikeMapper().readValue(json, PresencaData.class);

		assertEquals(123, presenca.getId());
		assertEquals(20260908, presenca.getData());
		assertEquals("19:30", presenca.getHora());
		assertEquals(7, presenca.getId_escalao());
		assertEquals("Sub-12", presenca.getEscalao_descricao());
		assertEquals(1, presenca.getId_utilizador_criacao());
		assertEquals("admin", presenca.getUser_criacao());

		assertNotNull(presenca.getJogadoresPresenca(), "jogadoresPresenca não deve ser null");
		assertEquals(2, presenca.getJogadoresPresenca().size());

		PresencaJogadorData primeiro = presenca.getJogadoresPresenca().get(0);
		assertEquals(10, primeiro.getId_jogador());
		assertEquals("Joao", primeiro.getNome_jogador());
		assertEquals("Presente", primeiro.getEstado());
		assertEquals(Integer.valueOf(4), primeiro.getClassificacao(), "classificacao deve ser desserializada");

		PresencaJogadorData segundo = presenca.getJogadoresPresenca().get(1);
		assertEquals("Ausente (Avisou)", segundo.getEstado());
		assertNull(segundo.getClassificacao(), "classificacao null deve permanecer null");

		assertNotNull(presenca.getStaffPresenca(), "staffPresenca não deve ser null");
		assertEquals(1, presenca.getStaffPresenca().size());
		assertEquals(3, presenca.getStaffPresenca().get(0).getid_staff());
		assertEquals("Treinador X", presenca.getStaffPresenca().get(0).getnome_staff());
	}

	@Test
	void testDeserializacaoSemListasDeveProduzirListasVazias() throws Exception {
		String json = "{" +
				"\"id\": 5," +
				"\"data\": 20260901," +
				"\"hora\": \"10:00\"," +
				"\"id_escalao\": 7," +
				"\"escalao_descricao\": \"Sub-12\"," +
				"\"data_criacao\": \"20260901\"," +
				"\"id_utilizador_criacao\": 1," +
				"\"user_criacao\": \"admin\"" +
				"}";

		PresencaData presenca = newSpringBootLikeMapper().readValue(json, PresencaData.class);

		assertNotNull(presenca.getJogadoresPresenca(), "jogadoresPresenca deve ser lista vazia, não null");
		assertTrue(presenca.getJogadoresPresenca().isEmpty());
		assertNotNull(presenca.getStaffPresenca(), "staffPresenca deve ser lista vazia, não null");
		assertTrue(presenca.getStaffPresenca().isEmpty());
	}

	@Test
	void testConstrutorPorOmissaoPresencaJogadorEStaff() {
		PresencaJogadorData jogador = new PresencaJogadorData();
		assertEquals(0, jogador.getId_jogador());
		assertNull(jogador.getClassificacao());

		PresencaStaffData staff = new PresencaStaffData();
		assertEquals(0, staff.getid_staff());

		PresencaData presenca = new PresencaData();
		assertNotNull(presenca.getJogadoresPresenca());
		assertNotNull(presenca.getStaffPresenca());
	}
}