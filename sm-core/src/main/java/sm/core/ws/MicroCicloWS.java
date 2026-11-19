package sm.core.ws;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import sm.core.data.PedidoAusenciaData;
import sm.core.data.PedidoConvocatoriaData;
import sm.core.data.PedidoCopiaTreinoData;
import sm.core.data.PedidoEstadoTreinoData;
import sm.core.data.PedidoMicroCicloData;
import sm.core.data.PresencaData;
import sm.core.data.TreinoData;
import sm.core.helper.AutorizacaoHelper;
import sm.core.helper.FuncionalidadeHelper;
import sm.core.helper.MicroCicloHelper;
import sm.core.security.AcessoNegadoException;
import sm.core.security.Identidade;
import sm.core.security.PedidoInvalidoException;

/**
 * Micro Ciclo (desenho §4.2). Cada metodo exige identidade, tenant da rota = token
 * e a funcionalidade ligada, sempre (D9); as regras de perfil e de "so as suas
 * equipas" sao verificadas aqui e no helper (D10).
 */
@RestController
@RequestMapping("/sm")
public class MicroCicloWS {

	private static final Logger log = LoggerFactory.getLogger(MicroCicloWS.class);

	@Autowired
	private MicroCicloHelper microCicloHelper;

	@Autowired
	private AutorizacaoHelper autorizacaoHelper;

	static int numero(String parmValor, String parmCampo) {
		try {
			return Integer.parseInt(parmValor);
		} catch (NumberFormatException e) {
			throw new PedidoInvalidoException(parmCampo + " inválido.");
		}
	}

	private Identidade exige(HttpServletRequest parmRequest, String parmTenantId) {
		return autorizacaoHelper.exigeFuncionalidade(parmRequest, numero(parmTenantId, "Tenant"),
				FuncionalidadeHelper.MICRO_CICLO);
	}

	private Identidade exigeAdmin(HttpServletRequest parmRequest, String parmTenantId) {
		Identidade identidade = exige(parmRequest, parmTenantId);
		if (!identidade.isAdmin()) {
			throw new AcessoNegadoException();
		}
		return identidade;
	}

	private static Identidade semAtleta(Identidade parmIdentidade) {
		if (parmIdentidade.isAtleta()) {
			throw new AcessoNegadoException();
		}
		return parmIdentidade;
	}

	static String json(Object parmValor, String parmMetodo) {
		try {
			return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(parmValor);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("MicroCicloWS | {} | Error End", parmMetodo);
		}
		return "";
	}

	@PutMapping("/getMicroCiclo/{tenantId}/{data}")
	@ResponseBody
	public String getMicroCiclo(@PathVariable String tenantId, @PathVariable String data, HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		log.info("MicroCicloWS | getMicroCiclo | tenant:{} data:{}", identidade.getTenantId(), data);
		return json(microCicloHelper.getSemana(identidade, numero(data, "Data")), "getMicroCiclo");
	}

	@PutMapping("/createMicroCiclo/{tenantId}")
	@ResponseBody
	public String createMicroCiclo(@PathVariable String tenantId, @RequestBody PedidoMicroCicloData parmPedido,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("MicroCicloWS | createMicroCiclo | tenant:{} dataInicio:{}", identidade.getTenantId(),
				parmPedido.getDataInicio());
		return json(microCicloHelper.criaMicroCiclo(identidade, parmPedido), "createMicroCiclo");
	}

	@PutMapping("/publicarMicroCiclo/{tenantId}/{idMicroCiclo}")
	@ResponseBody
	public String publicarMicroCiclo(@PathVariable String tenantId, @PathVariable String idMicroCiclo,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("MicroCicloWS | publicarMicroCiclo | idMicroCiclo:{}", idMicroCiclo);
		return json(microCicloHelper.publica(identidade, numero(idMicroCiclo, "Micro ciclo")), "publicarMicroCiclo");
	}

	@PutMapping("/cancelarMicroCiclo/{tenantId}/{idMicroCiclo}")
	@ResponseBody
	public String cancelarMicroCiclo(@PathVariable String tenantId, @PathVariable String idMicroCiclo,
			@RequestBody(required = false) Map<String, String> parmCorpo, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("MicroCicloWS | cancelarMicroCiclo | idMicroCiclo:{}", idMicroCiclo);
		String motivo = parmCorpo == null ? null : parmCorpo.get("motivo");
		return json(microCicloHelper.cancelaMicroCiclo(identidade, numero(idMicroCiclo, "Micro ciclo"), motivo),
				"cancelarMicroCiclo");
	}

	@PutMapping("/eliminarMicroCiclo/{tenantId}/{idMicroCiclo}")
	@ResponseBody
	public String eliminarMicroCiclo(@PathVariable String tenantId, @PathVariable String idMicroCiclo,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("MicroCicloWS | eliminarMicroCiclo | idMicroCiclo:{}", idMicroCiclo);
		return json(microCicloHelper.eliminaMicroCiclo(identidade, numero(idMicroCiclo, "Micro ciclo")),
				"eliminarMicroCiclo");
	}

	@PutMapping("/gravarTreino/{tenantId}")
	@ResponseBody
	public String gravarTreino(@PathVariable String tenantId, @RequestBody TreinoData parmTreino,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("MicroCicloWS | gravarTreino | id:{} idEquipa:{} data:{}", parmTreino.getId(), parmTreino.getIdEquipa(),
				parmTreino.getData());
		return json(microCicloHelper.gravaTreino(identidade, parmTreino), "gravarTreino");
	}

	@PutMapping("/eliminarTreino/{tenantId}/{idTreino}")
	@ResponseBody
	public String eliminarTreino(@PathVariable String tenantId, @PathVariable String idTreino,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("MicroCicloWS | eliminarTreino | idTreino:{}", idTreino);
		return json(microCicloHelper.eliminaTreino(identidade, numero(idTreino, "Treino")), "eliminarTreino");
	}

	@PutMapping("/getTreino/{tenantId}/{idTreino}")
	@ResponseBody
	public String getTreino(@PathVariable String tenantId, @PathVariable String idTreino, HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		log.info("MicroCicloWS | getTreino | idTreino:{}", idTreino);
		return json(microCicloHelper.getDetalhe(identidade, numero(idTreino, "Treino")), "getTreino");
	}

	@PutMapping("/mudarEstadoTreino/{tenantId}/{idTreino}")
	@ResponseBody
	public String mudarEstadoTreino(@PathVariable String tenantId, @PathVariable String idTreino,
			@RequestBody PedidoEstadoTreinoData parmPedido, HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		log.info("MicroCicloWS | mudarEstadoTreino | idTreino:{} acao:{}", idTreino, parmPedido.getAcao());
		return json(microCicloHelper.mudaEstado(identidade, numero(idTreino, "Treino"), parmPedido),
				"mudarEstadoTreino");
	}

	@PutMapping("/ajustarConvocatoria/{tenantId}/{idTreino}")
	@ResponseBody
	public String ajustarConvocatoria(@PathVariable String tenantId, @PathVariable String idTreino,
			@RequestBody PedidoConvocatoriaData parmPedido, HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		log.info("MicroCicloWS | ajustarConvocatoria | idTreino:{}", idTreino);
		return json(microCicloHelper.ajustaConvocatoria(identidade, numero(idTreino, "Treino"), parmPedido),
				"ajustarConvocatoria");
	}

	// ==================== v4 ====================

	// ==================== v5 ====================

	/** W5: ADMIN e TREINADOR (UTILIZADOR: 403 no helper); cada item na sua transacao. */
	@PutMapping("/aprovarLote/{tenantId}/{idMicroCiclo}")
	@ResponseBody
	public String aprovarLote(@PathVariable String tenantId, @PathVariable String idMicroCiclo,
			@RequestBody(required = false) sm.core.data.PedidoAprovacaoLoteData parmPedido, HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		log.info("MicroCicloWS | aprovarLote | idMicroCiclo:{}", idMicroCiclo);
		return json(microCicloHelper.aprovaLote(identidade, numero(idMicroCiclo, "Micro ciclo"), parmPedido),
				"aprovarLote");
	}

	@PutMapping("/gravarGrupoEvento/{tenantId}")
	@ResponseBody
	public String gravarGrupoEvento(@PathVariable String tenantId,
			@RequestBody(required = false) sm.core.data.GrupoEventoData parmGrupo, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("MicroCicloWS | gravarGrupoEvento | idGrupo:{}", parmGrupo == null ? null : parmGrupo.getIdGrupo());
		return json(microCicloHelper.gravaGrupoEvento(identidade, parmGrupo), "gravarGrupoEvento");
	}

	/** W6 (R138/R141): leitura fechada do clube pelo ATLETA; staff: 403. */
	@PutMapping("/getMicroCicloAtleta/{tenantId}/{idJogador}/{data}")
	@ResponseBody
	public String getMicroCicloAtleta(@PathVariable String tenantId, @PathVariable String idJogador,
			@PathVariable String data, HttpServletRequest request) {
		Identidade identidade = exige(request, tenantId);
		log.info("MicroCicloWS | getMicroCicloAtleta | idJogador:{} data:{}", idJogador, data);
		return json(microCicloHelper.getSemanaClubeAtleta(identidade, numero(idJogador, "Jogador"), numero(data, "Data")),
				"getMicroCicloAtleta");
	}

	@PutMapping("/getPendentesMeus/{tenantId}")
	@ResponseBody
	public String getPendentesMeus(@PathVariable String tenantId, HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		return json(microCicloHelper.pendentesMeus(identidade), "getPendentesMeus");
	}

	@PutMapping("/reativarMicroCiclo/{tenantId}/{idMicroCiclo}")
	@ResponseBody
	public String reativarMicroCiclo(@PathVariable String tenantId, @PathVariable String idMicroCiclo,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("MicroCicloWS | reativarMicroCiclo | idMicroCiclo:{}", idMicroCiclo);
		return json(microCicloHelper.reativaMicroCiclo(identidade, numero(idMicroCiclo, "Micro ciclo")),
				"reativarMicroCiclo");
	}

	@PutMapping("/copiarTreino/{tenantId}/{idTreino}")
	@ResponseBody
	public String copiarTreino(@PathVariable String tenantId, @PathVariable String idTreino,
			@RequestBody(required = false) PedidoCopiaTreinoData parmPedido, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("MicroCicloWS | copiarTreino | idTreino:{}", idTreino);
		return json(microCicloHelper.copiaTreino(identidade, numero(idTreino, "Treino"), parmPedido), "copiarTreino");
	}

	@PutMapping("/getJogosAtleta/{tenantId}/{idJogador}/{data}")
	@ResponseBody
	public String getJogosAtleta(@PathVariable String tenantId, @PathVariable String idJogador,
			@PathVariable String data, HttpServletRequest request) {
		Identidade identidade = exige(request, tenantId);
		log.info("MicroCicloWS | getJogosAtleta | idJogador:{} data:{}", idJogador, data);
		return json(microCicloHelper.getJogosAtleta(identidade, numero(idJogador, "Jogador"), numero(data, "Data")),
				"getJogosAtleta");
	}

	/** Cor da equipa (por epoca): corpo {"idEquipa": n, "cor": "#RRGGBB" | null}. So ADMIN. */
	@PutMapping("/setCorEquipa/{tenantId}")
	@ResponseBody
	public String setCorEquipa(@PathVariable String tenantId, @RequestBody(required = false) Map<String, Object> parmCorpo,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		if (parmCorpo == null || !(parmCorpo.get("idEquipa") instanceof Number)
				|| (parmCorpo.get("cor") != null && !(parmCorpo.get("cor") instanceof String))) {
			throw new PedidoInvalidoException("Pedido inválido.");
		}
		int idEquipa = ((Number) parmCorpo.get("idEquipa")).intValue();
		log.info("MicroCicloWS | setCorEquipa | idEquipa:{}", idEquipa);
		return json(microCicloHelper.setCorEquipa(identidade, idEquipa, (String) parmCorpo.get("cor")), "setCorEquipa");
	}

	// ==================== Entrega C: presencas a partir do plano ====================

	@PutMapping("/getTreinosEquipaDia/{tenantId}/{idEquipa}/{data}")
	@ResponseBody
	public String getTreinosEquipaDia(@PathVariable String tenantId, @PathVariable String idEquipa,
			@PathVariable String data, HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		log.info("MicroCicloWS | getTreinosEquipaDia | idEquipa:{} data:{}", idEquipa, data);
		return json(microCicloHelper.getTreinosEquipaDia(identidade, numero(idEquipa, "Equipa"), numero(data, "Data")),
				"getTreinosEquipaDia");
	}

	@PutMapping("/getPresencaTreino/{tenantId}/{idTreino}")
	@ResponseBody
	public String getPresencaTreino(@PathVariable String tenantId, @PathVariable String idTreino,
			HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		log.info("MicroCicloWS | getPresencaTreino | idTreino:{}", idTreino);
		return json(microCicloHelper.getPresencaTreino(identidade, numero(idTreino, "Treino")), "getPresencaTreino");
	}

	@PutMapping("/marcarPresencaTreino/{tenantId}/{idTreino}")
	@ResponseBody
	public String marcarPresencaTreino(@PathVariable String tenantId, @PathVariable String idTreino,
			@RequestBody PresencaData parmPresenca, HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		log.info("MicroCicloWS | marcarPresencaTreino | idTreino:{}", idTreino);
		return json(microCicloHelper.marcaPresencaTreino(identidade, numero(idTreino, "Treino"), parmPresenca),
				"marcarPresencaTreino");
	}

	@PutMapping("/ligarPresencaTreino/{tenantId}/{idTreino}/{idPresenca}")
	@ResponseBody
	public String ligarPresencaTreino(@PathVariable String tenantId, @PathVariable String idTreino,
			@PathVariable String idPresenca, HttpServletRequest request) {
		Identidade identidade = semAtleta(exige(request, tenantId));
		log.info("MicroCicloWS | ligarPresencaTreino | idTreino:{} idPresenca:{}", idTreino, idPresenca);
		return json(microCicloHelper.ligaPresenca(identidade, numero(idTreino, "Treino"), numero(idPresenca, "Presença")),
				"ligarPresencaTreino");
	}

	// ==================== Entrega D: atleta (staff: 403) ====================

	@PutMapping("/getTreinosAtleta/{tenantId}/{idJogador}/{data}")
	@ResponseBody
	public String getTreinosAtleta(@PathVariable String tenantId, @PathVariable String idJogador,
			@PathVariable String data, HttpServletRequest request) {
		Identidade identidade = exige(request, tenantId);
		log.info("MicroCicloWS | getTreinosAtleta | idJogador:{} data:{}", idJogador, data);
		return json(microCicloHelper.getTreinosAtleta(identidade, numero(idJogador, "Jogador"), numero(data, "Data")),
				"getTreinosAtleta");
	}

	@PutMapping("/indicarAusencia/{tenantId}/{idTreino}")
	@ResponseBody
	public String indicarAusencia(@PathVariable String tenantId, @PathVariable String idTreino,
			@RequestBody PedidoAusenciaData parmPedido, HttpServletRequest request) {
		Identidade identidade = exige(request, tenantId);
		// sem o motivo no log (dados de saude, RGPD)
		log.info("MicroCicloWS | indicarAusencia | idTreino:{} acao:{}", idTreino, parmPedido.getAcao());
		return json(microCicloHelper.ausencia(identidade, numero(idTreino, "Treino"), parmPedido), "indicarAusencia");
	}
}
