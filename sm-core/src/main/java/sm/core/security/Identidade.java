package sm.core.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Identidade do pedido, lida do token (v2): utilizador, tenant, perfil e,
 * para o perfil ATLETA, o jogador ligado a conta.
 */
public final class Identidade {

    /** Atributo do pedido onde fica a identidade autenticada. */
    public static final String ATTRIBUTE = "sm.auth.identidade";

    public static final String PERFIL_ADMIN = "ADMIN";
    public static final String PERFIL_ATLETA = "ATLETA";
    public static final String PERFIL_UTILIZADOR = "UTILIZADOR";
    public static final String PERFIL_TREINADOR = "TREINADOR";

    private final int idUtilizador;
    private final String user;
    private final int tenantId;
    private final String perfil;
    private final Integer idJogador;

    public Identidade(int idUtilizador, String user, int tenantId, String perfil, Integer idJogador) {
        this.idUtilizador = idUtilizador;
        this.user = user;
        this.tenantId = tenantId;
        // perfil em maiusculas; nulo/vazio e tratado como UTILIZADOR (D2)
        this.perfil = perfil == null || perfil.isBlank() ? PERFIL_UTILIZADOR : perfil.trim().toUpperCase();
        this.idJogador = idJogador;
    }

    /** Identidade guardada no pedido pelo filtro de token, ou {@code null}. */
    public static Identidade de(HttpServletRequest request) {
        Object valor = request.getAttribute(ATTRIBUTE);
        return valor instanceof Identidade ? (Identidade) valor : null;
    }

    public int getIdUtilizador() {
        return idUtilizador;
    }

    public String getUser() {
        return user;
    }

    public int getTenantId() {
        return tenantId;
    }

    public String getPerfil() {
        return perfil;
    }

    public Integer getIdJogador() {
        return idJogador;
    }

    public boolean isAtleta() {
        return PERFIL_ATLETA.equals(perfil);
    }

    public boolean isAdmin() {
        return PERFIL_ADMIN.equals(perfil);
    }

    public boolean isTreinador() {
        return PERFIL_TREINADOR.equals(perfil);
    }
}
