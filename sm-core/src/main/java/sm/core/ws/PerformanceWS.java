package sm.core.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import sm.core.data.JogadorData;
import sm.core.data.PerformanceEquipaData;
import sm.core.data.PerformanceResumoJogadorData;
import sm.core.data.UtilizadorData;
import sm.core.helper.JogadorHelper;
import sm.core.helper.LoginHelper;
import sm.core.helper.PerformanceHelper;

/**
 * Endpoints de análise de Performance dos atletas nos treinos. Acesso restrito
 * a utilizadores com perfil ADMIN ou TREINADOR (validado em cada pedido).
 */
@RestController
@RequestMapping("/sm")
public class PerformanceWS {

	private static final Logger log = LoggerFactory.getLogger(PerformanceWS.class);

	@Autowired
	private PerformanceHelper performanceHelper;

	@Autowired
	private LoginHelper loginHelper;

	@Autowired
	private JogadorHelper jogadorHelper;

	private boolean temPermissao(int idUtilizador, int tenantId) {
		UtilizadorData utilizador = loginHelper.getUser(idUtilizador, tenantId);
		if (utilizador == null || utilizador.getPerfil() == null) {
			return false;
		}
		String perfil = utilizador.getPerfil().toUpperCase();
		return "ADMIN".equals(perfil) || "TREINADOR".equals(perfil);
	}

	@CrossOrigin
	@GetMapping("/performance/equipa/{idEquipa}/{idUtilizador}/{tenantId}")
	@ResponseBody
	public String getPerformanceEquipa(@PathVariable String idEquipa, @PathVariable String idUtilizador,
			@PathVariable String tenantId, @RequestParam(required = false) Integer idJogador,
			@RequestParam(required = false) Integer dataInicio, @RequestParam(required = false) Integer dataFim) {

		log.info("PerformanceWS | getPerformanceEquipa | idEquipa:{} idUtilizador:{}", idEquipa, idUtilizador);

		ObjectMapper mapper = new ObjectMapper();

		if (!temPermissao(Integer.parseInt(idUtilizador), Integer.parseInt(tenantId))) {
			log.warn("PerformanceWS | getPerformanceEquipa | Utilizador {} sem permissão (requer ADMIN/TREINADOR)",
					idUtilizador);
			try {
				return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new PerformanceEquipaData());
			} catch (JsonProcessingException e) {
				e.printStackTrace();
				return "";
			}
		}

		PerformanceEquipaData resultado = performanceHelper.getResumoEquipa(Integer.parseInt(idEquipa), idJogador,
				dataInicio, dataFim);

		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultado);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("PerformanceWS | getPerformanceEquipa | Error End");
		}

		return "";
	}

	@CrossOrigin
	@GetMapping("/performance/jogador/{idJogador}/{idUtilizador}/{tenantId}")
	@ResponseBody
	public String getPerformanceJogador(@PathVariable String idJogador, @PathVariable String idUtilizador,
			@PathVariable String tenantId, @RequestParam(required = false) Integer dataInicio,
			@RequestParam(required = false) Integer dataFim) {

		log.info("PerformanceWS | getPerformanceJogador | idJogador:{} idUtilizador:{}", idJogador, idUtilizador);

		ObjectMapper mapper = new ObjectMapper();

		if (!temPermissao(Integer.parseInt(idUtilizador), Integer.parseInt(tenantId))) {
			log.warn("PerformanceWS | getPerformanceJogador | Utilizador {} sem permissão (requer ADMIN/TREINADOR)",
					idUtilizador);
			try {
				return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(new PerformanceResumoJogadorData());
			} catch (JsonProcessingException e) {
				e.printStackTrace();
				return "";
			}
		}

		JogadorData jogador = jogadorHelper.getJogadorbyID(Integer.parseInt(idJogador));
		String nomeJogador = jogador != null ? jogador.getNome() : "";

		PerformanceResumoJogadorData resumo = performanceHelper.getResumoJogador(Integer.parseInt(idJogador),
				nomeJogador, dataInicio, dataFim);

		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resumo);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("PerformanceWS | getPerformanceJogador | Error End");
		}

		return "";
	}

}
