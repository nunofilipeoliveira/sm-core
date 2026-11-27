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

import jakarta.servlet.http.HttpServletRequest;
import sm.core.data.BalnearioData;
import sm.core.data.ConfigBalneariosData;
import sm.core.data.PedidoBalneariosData;
import sm.core.helper.AutorizacaoHelper;
import sm.core.helper.BalnearioHelper;
import sm.core.helper.FuncionalidadeHelper;
import sm.core.security.AcessoNegadoException;
import sm.core.security.Identidade;

/**
 * Balnearios (desenho balnearios §5.1). Todas as rotas: tenant da rota = token, MICRO_CICLO ligada (P1) e so ADMIN
 * (P3), com 403 tambem em modo REPORT (excecao do controller, R30). As leituras com balnearios vivem no MicroCicloWS.
 */
@RestController
@RequestMapping("/sm")
public class BalnearioWS {

	private static final Logger log = LoggerFactory.getLogger(BalnearioWS.class);

	@Autowired
	private BalnearioHelper balnearioHelper;

	@Autowired
	private AutorizacaoHelper autorizacaoHelper;

	private Identidade exigeAdmin(HttpServletRequest parmRequest, String parmTenantId) {
		Identidade identidade = autorizacaoHelper.exigeFuncionalidade(parmRequest, MicroCicloWS.numero(parmTenantId, "Tenant"),
				FuncionalidadeHelper.MICRO_CICLO);
		if (!identidade.isAdmin()) {
			throw new AcessoNegadoException();
		}
		return identidade;
	}

	@PutMapping("/getConfigBalnearios/{tenantId}")
	@ResponseBody
	public String getConfigBalnearios(@PathVariable String tenantId, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("BalnearioWS | getConfigBalnearios | tenant:{}", identidade.getTenantId());
		return MicroCicloWS.json(balnearioHelper.getConfig(identidade), "getConfigBalnearios");
	}

	@PutMapping("/gravarBalneario/{tenantId}")
	@ResponseBody
	public String gravarBalneario(@PathVariable String tenantId, @RequestBody(required = false) BalnearioData parmBalneario,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("BalnearioWS | gravarBalneario | tenant:{} id:{}", identidade.getTenantId(),
				parmBalneario == null ? null : parmBalneario.getId());
		return MicroCicloWS.json(balnearioHelper.gravaBalneario(identidade, parmBalneario), "gravarBalneario");
	}

	@PutMapping("/apagarBalneario/{tenantId}/{idBalneario}")
	@ResponseBody
	public String apagarBalneario(@PathVariable String tenantId, @PathVariable String idBalneario,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("BalnearioWS | apagarBalneario | tenant:{} id:{}", identidade.getTenantId(), idBalneario);
		return MicroCicloWS.json(balnearioHelper.apagaBalneario(identidade, MicroCicloWS.numero(idBalneario, "Balneário")),
				"apagarBalneario");
	}

	@PutMapping("/gravarRegrasBalnearios/{tenantId}")
	@ResponseBody
	public String gravarRegrasBalnearios(@PathVariable String tenantId,
			@RequestBody(required = false) ConfigBalneariosData parmConfig, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("BalnearioWS | gravarRegrasBalnearios | tenant:{}", identidade.getTenantId());
		return MicroCicloWS.json(balnearioHelper.gravaRegras(identidade, parmConfig), "gravarRegrasBalnearios");
	}

	@PutMapping("/gravarBalnearios/{tenantId}")
	@ResponseBody
	public String gravarBalnearios(@PathVariable String tenantId,
			@RequestBody(required = false) PedidoBalneariosData parmPedido, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("BalnearioWS | gravarBalnearios | tenant:{} idTreino:{} idJogo:{}", identidade.getTenantId(),
				parmPedido == null ? null : parmPedido.getIdTreino(), parmPedido == null ? null : parmPedido.getIdJogo());
		return MicroCicloWS.json(balnearioHelper.gravaAtribuicao(identidade, parmPedido), "gravarBalnearios");
	}

	@PutMapping("/calcularBalnearios/{tenantId}/{idMicroCiclo}")
	@ResponseBody
	public String calcularBalnearios(@PathVariable String tenantId, @PathVariable String idMicroCiclo,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("BalnearioWS | calcularBalnearios | tenant:{} idMicroCiclo:{}", identidade.getTenantId(), idMicroCiclo);
		return MicroCicloWS.json(balnearioHelper.calcula(identidade, MicroCicloWS.numero(idMicroCiclo, "Micro ciclo")),
				"calcularBalnearios");
	}

	@PutMapping("/gravarCalculoBalnearios/{tenantId}/{idMicroCiclo}")
	@ResponseBody
	public String gravarCalculoBalnearios(@PathVariable String tenantId, @PathVariable String idMicroCiclo,
			@RequestBody(required = false) Map<String, String> parmCorpo, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("BalnearioWS | gravarCalculoBalnearios | tenant:{} idMicroCiclo:{}", identidade.getTenantId(),
				idMicroCiclo);
		return MicroCicloWS.json(balnearioHelper.gravaCalculo(identidade, MicroCicloWS.numero(idMicroCiclo, "Micro ciclo"),
				parmCorpo == null ? null : parmCorpo.get("assinatura")), "gravarCalculoBalnearios");
	}
}
