package sm.core.ws;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import sm.core.helper.AutorizacaoHelper;
import sm.core.helper.FuncionalidadeHelper;
import sm.core.helper.NotificacaoHelper;
import sm.core.security.Identidade;

/**
 * Notificacoes na aplicacao (desenho §4.3; v5: tambem o ATLETA, so as suas de convocatoria); exige
 * sempre identidade, tenant e a funcionalidade ligada (D9).
 */
@RestController
@RequestMapping("/sm")
public class NotificacaoWS {

	private static final Logger log = LoggerFactory.getLogger(NotificacaoWS.class);

	@Autowired
	private NotificacaoHelper notificacaoHelper;

	@Autowired
	private AutorizacaoHelper autorizacaoHelper;

	private Identidade exige(HttpServletRequest parmRequest, String parmTenantId) {
		// v5 (W7): o ATLETA passa a ter o sino; o filtro de visibilidade so lhe mostra as suas
		// notificacoes de convocatoria (NotificacaoHelper.SQL_VISIVEIS)
		return autorizacaoHelper.exigeFuncionalidade(parmRequest, MicroCicloWS.numero(parmTenantId, "Tenant"),
				FuncionalidadeHelper.MICRO_CICLO);
	}

	@PutMapping("/getNotificacoesNaoLidas/{tenantId}")
	@ResponseBody
	public String getNotificacoesNaoLidas(@PathVariable String tenantId, HttpServletRequest request) {
		Identidade identidade = exige(request, tenantId);
		Map<String, Integer> resultado = new LinkedHashMap<>();
		resultado.put("naoLidas", notificacaoHelper.conta(identidade));
		return MicroCicloWS.json(resultado, "getNotificacoesNaoLidas");
	}

	@PutMapping("/getNotificacoes/{tenantId}")
	@ResponseBody
	public String getNotificacoes(@PathVariable String tenantId, HttpServletRequest request) {
		Identidade identidade = exige(request, tenantId);
		log.info("NotificacaoWS | getNotificacoes | utilizador:{}", identidade.getIdUtilizador());
		return MicroCicloWS.json(notificacaoHelper.lista(identidade), "getNotificacoes");
	}

	@PutMapping("/marcarNotificacaoLida/{tenantId}/{idNotificacao}")
	@ResponseBody
	public String marcarNotificacaoLida(@PathVariable String tenantId, @PathVariable String idNotificacao,
			HttpServletRequest request) {
		Identidade identidade = exige(request, tenantId);
		return MicroCicloWS.json(
				notificacaoHelper.marcaLida(identidade, MicroCicloWS.numero(idNotificacao, "Notificação")),
				"marcarNotificacaoLida");
	}

	@PutMapping("/marcarNotificacoesLidas/{tenantId}")
	@ResponseBody
	public String marcarNotificacoesLidas(@PathVariable String tenantId, HttpServletRequest request) {
		Identidade identidade = exige(request, tenantId);
		log.info("NotificacaoWS | marcarNotificacoesLidas | utilizador:{}", identidade.getIdUtilizador());
		return MicroCicloWS.json(notificacaoHelper.marcaTodas(identidade), "marcarNotificacoesLidas");
	}
}
