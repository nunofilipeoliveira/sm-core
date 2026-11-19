package sm.core.ws;

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
import sm.core.data.FuncionalidadeData;
import sm.core.helper.AutorizacaoHelper;
import sm.core.helper.FuncionalidadeHelper;
import sm.core.security.AcessoNegadoException;
import sm.core.security.Identidade;
import sm.core.security.PedidoInvalidoException;

/**
 * Funcionalidades ligadas/desligadas por clube (R57-R64). Identidade e tenant
 * do token verificados sempre (REPORT e ENFORCE); so o ADMIN do proprio tenant
 * ve a gestao e altera.
 */
@RestController
@RequestMapping("/sm")
public class FuncionalidadeWS {

	private static final Logger log = LoggerFactory.getLogger(FuncionalidadeWS.class);

	@Autowired
	private FuncionalidadeHelper funcionalidadeHelper;

	@Autowired
	private AutorizacaoHelper autorizacaoHelper;

	private static int tenant(String parmTenantId) {
		try {
			return Integer.parseInt(parmTenantId);
		} catch (NumberFormatException e) {
			throw new PedidoInvalidoException("Tenant inválido.");
		}
	}

	private Identidade exigeAdmin(HttpServletRequest parmRequest, String parmTenantId) {
		Identidade identidade = autorizacaoHelper.exigeIdentidade(parmRequest, tenant(parmTenantId));
		if (!identidade.isAdmin()) {
			throw new AcessoNegadoException();
		}
		return identidade;
	}

	private static String json(Object parmValor, String parmMetodo) {
		try {
			return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(parmValor);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("FuncionalidadeWS | {} | Error End", parmMetodo);
		}
		return "";
	}

	@PutMapping("/getFuncionalidades/{tenantId}")
	@ResponseBody
	public String getFuncionalidades(@PathVariable String tenantId, HttpServletRequest request) {
		Identidade identidade = autorizacaoHelper.exigeIdentidade(request, tenant(tenantId));
		log.info("FuncionalidadeWS | getFuncionalidades | tenant:{}", identidade.getTenantId());
		return json(funcionalidadeHelper.getFuncionalidades(identidade.getTenantId()), "getFuncionalidades");
	}

	@PutMapping("/getFuncionalidadesAdmin/{tenantId}")
	@ResponseBody
	public String getFuncionalidadesAdmin(@PathVariable String tenantId, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		log.info("FuncionalidadeWS | getFuncionalidadesAdmin | tenant:{}", identidade.getTenantId());
		return json(funcionalidadeHelper.getFuncionalidadesAdmin(identidade.getTenantId()), "getFuncionalidadesAdmin");
	}

	@PutMapping("/setFuncionalidade/{tenantId}")
	@ResponseBody
	public String setFuncionalidade(@PathVariable String tenantId, @RequestBody FuncionalidadeData parmFuncionalidade,
			HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		if (parmFuncionalidade == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		log.info("FuncionalidadeWS | setFuncionalidade | tenant:{} codigo:{} ativo:{} utilizador:{}",
				identidade.getTenantId(), parmFuncionalidade.getCodigo(), parmFuncionalidade.isAtivo(),
				identidade.getIdUtilizador());
		FuncionalidadeData item = funcionalidadeHelper.setFuncionalidade(identidade.getTenantId(),
				parmFuncionalidade.getCodigo(), parmFuncionalidade.isAtivo(), identidade.getIdUtilizador());
		return json(item, "setFuncionalidade");
	}
}
