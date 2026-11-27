package sm.core.ws;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

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
import sm.core.data.PedidoImportacaoData;
import sm.core.data.ResultadoImportacaoData;
import sm.core.helper.AutorizacaoHelper;
import sm.core.helper.ImportacaoHelper;
import sm.core.security.AcessoNegadoException;
import sm.core.security.Identidade;
import sm.core.security.PedidoInvalidoException;
import sm.core.security.TipoRecurso;

/**
 * Importacao de jogadores/staff por CSV (desenho importar-csv §4, B6). So ADMIN do tenant do token, com 403 tambem
 * em modo REPORT. Tenant e utilizador vem sempre da Identidade. Logs so com tenant, tipo e contagens (R4).
 */
@RestController
@RequestMapping("/sm")
public class ImportacaoWS {

	private static final Logger log = LoggerFactory.getLogger(ImportacaoWS.class);

	@Autowired
	private ImportacaoHelper importacaoHelper;

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

	/** V4: equipa de outro tenant = 403 do pedido inteiro. */
	private void exigeEquipaDoTenant(PedidoImportacaoData parmPedido, Identidade parmIdentidade) {
		if (parmPedido == null) {
			throw new PedidoInvalidoException("Pedido sem dados.");
		}
		if (parmPedido.getIdEquipa() > 0 && !autorizacaoHelper.pertenceAoTenant(TipoRecurso.EQUIPA,
				parmPedido.getIdEquipa(), parmIdentidade.getTenantId())) {
			throw new AcessoNegadoException();
		}
	}

	private static Map<String, Long> contagens(ResultadoImportacaoData parmResultado) {
		return parmResultado.getLinhas().stream()
				.collect(Collectors.groupingBy(ResultadoImportacaoData.Linha::getEstado, TreeMap::new,
						Collectors.counting()));
	}

	private static String json(Object parmValor, String parmMetodo) {
		try {
			return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(parmValor);
		} catch (JsonProcessingException e) {
			log.error("ImportacaoWS | {} | Error End", parmMetodo);
		}
		return "";
	}

	@PutMapping("/validarImportacao/{tenantId}")
	@ResponseBody
	public String validarImportacao(@PathVariable String tenantId,
			@RequestBody(required = false) PedidoImportacaoData parmPedido, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		exigeEquipaDoTenant(parmPedido, identidade);
		log.info("ImportacaoWS | validarImportacao | tenant:{} tipo:{} linhas:{}", identidade.getTenantId(),
				parmPedido.getTipo(), parmPedido.getLinhas() == null ? 0 : parmPedido.getLinhas().size());
		ResultadoImportacaoData resultado = importacaoHelper.valida(parmPedido, identidade);
		log.info("ImportacaoWS | validarImportacao | tenant:{} estados:{}", identidade.getTenantId(),
				contagens(resultado));
		return json(resultado, "validarImportacao");
	}

	@PutMapping("/gravarImportacao/{tenantId}")
	@ResponseBody
	public String gravarImportacao(@PathVariable String tenantId,
			@RequestBody(required = false) PedidoImportacaoData parmPedido, HttpServletRequest request) {
		Identidade identidade = exigeAdmin(request, tenantId);
		exigeEquipaDoTenant(parmPedido, identidade);
		log.info("ImportacaoWS | gravarImportacao | tenant:{} tipo:{} linhas:{} utilizador:{}", identidade.getTenantId(),
				parmPedido.getTipo(), parmPedido.getLinhas() == null ? 0 : parmPedido.getLinhas().size(),
				identidade.getIdUtilizador());
		ResultadoImportacaoData resultado = importacaoHelper.grava(parmPedido, identidade);
		log.info("ImportacaoWS | gravarImportacao | tenant:{} estados:{}", identidade.getTenantId(),
				contagens(resultado));
		return json(resultado, "gravarImportacao");
	}
}
