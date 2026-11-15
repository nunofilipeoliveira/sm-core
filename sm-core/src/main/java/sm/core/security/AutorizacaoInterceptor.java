package sm.core.security;

import java.util.Map;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import sm.core.config.Auth;
import sm.core.config.SecurityProperties;
import sm.core.helper.AutorizacaoHelper;

/**
 * Autorizacao central (D3) dos pedidos {@code /sm/**} pela matriz {@link RegrasAcesso}:
 * perfil, tenant (T), utilizador (U) e pertenca do recurso (R) contra a identidade do token.
 *
 * <p>Pedidos sem identidade (sem token ou token antigo) passam: o
 * {@code TokenAuthenticationFilter} ja os tratou (401 em ENFORCE). As recusas de
 * ATLETA aplicam-se sempre; as de staff so com {@code authorization-mode=ENFORCE}.</p>
 */
public class AutorizacaoInterceptor implements HandlerInterceptor {

    private final Auth auth;
    private final AutorizacaoHelper autorizacaoHelper;

    public AutorizacaoInterceptor(SecurityProperties properties, AutorizacaoHelper autorizacaoHelper) {
        this.auth = properties.getAuth();
        this.autorizacaoHelper = autorizacaoHelper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // preflight CORS e recursos estaticos nao chegam a um controller
        if (!(handler instanceof HandlerMethod) || auth.isPublicPath(request.getRequestURI())) {
            return true;
        }
        Identidade identidade = Identidade.de(request);
        if (identidade == null) {
            return true;
        }

        String padrao = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        RegrasAcesso.Regra regra = RegrasAcesso.get(padrao);
        if (regra == null) {
            autorizacaoHelper.nega(identidade, request, "rota sem regra " + padrao);
            return true;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> variaveis = (Map<String, String>) request
                .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String motivo = verifica(regra, identidade, variaveis);
        if (motivo != null) {
            autorizacaoHelper.nega(identidade, request, motivo + " " + padrao);
        }
        return true;
    }

    /** @return o motivo da recusa, ou {@code null} se o pedido cumpre a regra */
    private String verifica(RegrasAcesso.Regra regra, Identidade identidade, Map<String, String> variaveis) {
        if (identidade.isAtleta()) {
            if (!regra.isAtleta()) {
                return "perfil";
            }
        } else if (regra.getStaff() == RegrasAcesso.Staff.ADMIN && !identidade.isAdmin()) {
            return "perfil";
        }

        if (regra.getTenant() != null) {
            Integer tenant = regra.getTenant().valor(variaveis);
            if (tenant == null || tenant != identidade.getTenantId()) {
                return "tenant";
            }
        }
        if (regra.getUtilizador() != null) {
            Integer utilizador = regra.getUtilizador().valor(variaveis);
            if (utilizador == null || utilizador != identidade.getIdUtilizador()) {
                return "utilizador";
            }
        }
        if (regra.getRecurso() != null) {
            Integer id = regra.getRecurso().valor(variaveis);
            if (id == null || !autorizacaoHelper.temAcessoAoRecurso(identidade, regra.getTipoRecurso(), id)) {
                return "recurso " + regra.getTipoRecurso();
            }
        }
        if (regra.getFotoAtleta() != null && identidade.isAtleta()) {
            String nome = variaveis == null ? null : variaveis.get(regra.getFotoAtleta());
            String jid = String.valueOf(identidade.getIdJogador());
            if (identidade.getIdJogador() == null || !(jid.equals(nome) || (jid + "_avatar").equals(nome))) {
                return "foto de outro jogador";
            }
        }
        return null;
    }
}
