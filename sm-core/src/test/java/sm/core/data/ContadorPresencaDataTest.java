package sm.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Valida que os contadores mensais de presenças da ficha do jogador
 * são serializados pela ordem da época desportiva: Agosto a Julho.
 */
class ContadorPresencaDataTest {

	@Test
	void testOrdemMesesAgostoAJullho() throws Exception {
		ContadorPresencaData contador = new ContadorPresencaData(1, "Sub-12", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

		ObjectMapper mapper = new ObjectMapper();
		String json = mapper.writeValueAsString(contador);
		JsonNode node = mapper.readTree(json);

		// Os campos do objeto JSON devem surgir pela ordem: ago, set, out, nov, dez,
		// jan, fev, mar, abr, mai, jun, jul
		String[] ordemEsperada = { "id_jogador", "escalao", "ago", "set", "out", "nov", "dez", "jan", "fev", "mar",
				"abr", "mai", "jun", "jul" };

		int i = 0;
		java.util.Iterator<String> fieldNames = node.fieldNames();
		while (fieldNames.hasNext()) {
			assertTrue(i < ordemEsperada.length, "JSON tem mais campos do que o esperado");
			assertEquals(ordemEsperada[i], fieldNames.next(),
					"Campo na posição " + i + " deveria ser " + ordemEsperada[i]);
			i++;
		}
		assertEquals(ordemEsperada.length, i, "JSON deve ter exatamente " + ordemEsperada.length + " campos");
	}

	@Test
	void testValoresCorretosPorMes() throws Exception {
		// ago=1, set=2, out=3, nov=4, dez=5, jan=6, fev=7, mar=8, abr=9, mai=10,
		// jun=11, jul=12
		ContadorPresencaData contador = new ContadorPresencaData(7, "Sub-10", 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree(mapper.writeValueAsString(contador));

		assertEquals(1, node.get("ago").asInt(), "Agosto deve ter o valor passado ao construtor");
		assertEquals(2, node.get("set").asInt());
		assertEquals(3, node.get("out").asInt());
		assertEquals(4, node.get("nov").asInt());
		assertEquals(5, node.get("dez").asInt());
		assertEquals(6, node.get("jan").asInt());
		assertEquals(7, node.get("fev").asInt());
		assertEquals(8, node.get("mar").asInt());
		assertEquals(9, node.get("abr").asInt());
		assertEquals(10, node.get("mai").asInt());
		assertEquals(11, node.get("jun").asInt());
		assertEquals(12, node.get("jul").asInt());
	}
}