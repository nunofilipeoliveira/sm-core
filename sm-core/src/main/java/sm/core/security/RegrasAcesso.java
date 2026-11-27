package sm.core.security;

import java.util.HashMap;
import java.util.Map;

/**
 * Matriz de acesso por padrao de rota (desenho §4.3). Negacao por omissao: um
 * padrao sem regra e recusado. Os caminhos publicos ({@code auth.public-paths})
 * ficam fora da matriz.
 */
public final class RegrasAcesso {

    /** Perfis de staff autorizados. */
    public enum Staff {
        /** ADMIN, TREINADOR, UTILIZADOR ou perfil nulo. */
        TODOS,
        /** So ADMIN. */
        ADMIN
    }

    /** Variavel da rota, inteira ou uma parte separada por {@code _} (indice a partir de 0). */
    public static final class Variavel {
        private final String nome;
        private final int parte;

        Variavel(String nome, int parte) {
            this.nome = nome;
            this.parte = parte;
        }

        /** @return o valor inteiro, ou {@code null} se faltar ou nao for numerico */
        public Integer valor(Map<String, String> variaveis) {
            String valor = variaveis == null ? null : variaveis.get(nome);
            if (valor == null) {
                return null;
            }
            if (parte >= 0) {
                String[] partes = valor.split("_");
                if (parte >= partes.length) {
                    return null;
                }
                valor = partes[parte];
            }
            try {
                return Integer.valueOf(valor.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** Regra de uma rota. */
    public static final class Regra {
        private final Staff staff;
        private final boolean atleta;
        private Variavel tenant;
        private Variavel utilizador;
        private TipoRecurso tipoRecurso;
        private Variavel recurso;
        private String fotoAtleta;

        Regra(Staff staff, boolean atleta) {
            this.staff = staff;
            this.atleta = atleta;
        }

        Regra tenant(String nome) {
            return tenant(nome, -1);
        }

        Regra tenant(String nome, int parte) {
            this.tenant = new Variavel(nome, parte);
            return this;
        }

        Regra utilizador(String nome) {
            return utilizador(nome, -1);
        }

        Regra utilizador(String nome, int parte) {
            this.utilizador = new Variavel(nome, parte);
            return this;
        }

        Regra recurso(TipoRecurso tipo, String nome) {
            return recurso(tipo, nome, -1);
        }

        Regra recurso(TipoRecurso tipo, String nome, int parte) {
            this.tipoRecurso = tipo;
            this.recurso = new Variavel(nome, parte);
            return this;
        }

        /** ATLETA: a variavel tem de ser {@code "<jid>"} ou {@code "<jid>_avatar"}. */
        Regra fotoAtleta(String nome) {
            this.fotoAtleta = nome;
            return this;
        }

        public Staff getStaff() {
            return staff;
        }

        public boolean isAtleta() {
            return atleta;
        }

        public Variavel getTenant() {
            return tenant;
        }

        public Variavel getUtilizador() {
            return utilizador;
        }

        public TipoRecurso getTipoRecurso() {
            return tipoRecurso;
        }

        public Variavel getRecurso() {
            return recurso;
        }

        public String getFotoAtleta() {
            return fotoAtleta;
        }
    }

    private static final Map<String, Regra> REGRAS = new HashMap<>();

    private static Regra staff(String padrao) {
        return regra(padrao, Staff.TODOS, false);
    }

    private static Regra staffEAtleta(String padrao) {
        return regra(padrao, Staff.TODOS, true);
    }

    private static Regra admin(String padrao) {
        return regra(padrao, Staff.ADMIN, false);
    }

    private static Regra regra(String padrao, Staff staff, boolean atleta) {
        Regra regra = new Regra(staff, atleta);
        REGRAS.put("/sm" + padrao, regra);
        return regra;
    }

    static {
        // TorneioWS: sem tenant (torneio global; P3: nao usado)
        staff("/loadAllGames");
        staff("/saveMatch");
        staff("/resetMatch");
        staff("/getClassificacaoPorRound");

        // ClubeWS e listas globais
        staffEAtleta("/getClube/{id}");
        staff("/getAllClubes");
        staff("/updateClube");
        staff("/getEscaloes");
        staff("/getAllCompeticoes");
        staff("/getPresenca");

        // EquipaWS
        staff("/getEquipa/{id}").recurso(TipoRecurso.EQUIPA, "id");
        staff("/getEquipaLight/{id}").recurso(TipoRecurso.EQUIPA, "id");
        staff("/getEscalaoByEquipa/{parmEquipaId}").recurso(TipoRecurso.EQUIPA, "parmEquipaId");
        staff("/getJogadoreDisponiveis/{id}/{all}/{tenant_id}").tenant("tenant_id").recurso(TipoRecurso.EQUIPA, "id");
        staff("/getAllStaffDisponivel/{tenant_id}/{idequipa}").tenant("tenant_id")
                .recurso(TipoRecurso.EQUIPA, "idequipa");
        staff("/getAllJogadores/{tenant_id}").tenant("tenant_id");
        staff("/getAllStaff/{tenant_id}").tenant("tenant_id");
        staff("/getAllEpocas/{tenant_id}").tenant("tenant_id");
        staffEAtleta("/getAllEquipasEpocaAtual/{tenant_id}").tenant("tenant_id");
        staffEAtleta("/getEpocaAtual/{tenant_id}").tenant("tenant_id");
        admin("/setEpocaAtual/{idepoca}/{tenant_id}").tenant("tenant_id").recurso(TipoRecurso.EPOCA, "idepoca");
        admin("/createEscalaoEpoca/{tenant_id}").tenant("tenant_id");
        admin("/deleteEscalaoEpoca/{tenant_id}").tenant("tenant_id");
        staff("/addStaff/{tenant_id}/{utilizador_id}").tenant("tenant_id").utilizador("utilizador_id");
        staff("/addJogador/{tenant_id}/{utilizador_id}").tenant("tenant_id").utilizador("utilizador_id");
        staff("/updateStaff/{idUtilizador}").utilizador("idUtilizador");
        staffEAtleta("/updateJogador/{idUtilizador}").utilizador("idUtilizador");
        staff("/equipa/addJogadorEquipa/{idEquipa}").recurso(TipoRecurso.EQUIPA, "idEquipa");
        staff("/equipa/removeJogadorEquipa/{idEquipa}").recurso(TipoRecurso.EQUIPA, "idEquipa");
        staff("/equipa/addStaffEquipa/{idEquipa}").recurso(TipoRecurso.EQUIPA, "idEquipa");
        staff("/equipa/removeStaffEquipa/{idEquipa}").recurso(TipoRecurso.EQUIPA, "idEquipa");
        staffEAtleta("/getJogador/{id}").recurso(TipoRecurso.JOGADOR, "id");
        staffEAtleta("/getFaltas/{id}").recurso(TipoRecurso.JOGADOR, "id");
        staffEAtleta("/getCountPresencas/{id}").recurso(TipoRecurso.JOGADOR, "id");
        staffEAtleta("/getHistoricobyJogador/{idJogador}/{tenant_id}").tenant("tenant_id")
                .recurso(TipoRecurso.JOGADOR, "idJogador");
        staff("/getStaff/{id}").recurso(TipoRecurso.STAFF, "id");

        // FicheirosWS
        staffEAtleta("/uploadfoto/{nomeFoto}/{tenantId}").tenant("tenantId").fotoAtleta("nomeFoto");
        staff("/uploadLogo/{nomeFoto}/{tenantId}").tenant("tenantId");

        // JogoWS
        staff("/getAllJogosByEquipa/{id}").recurso(TipoRecurso.EQUIPA, "id");
        staffEAtleta("/getJogosByJogadorId/{id}").recurso(TipoRecurso.JOGADOR, "id");
        staffEAtleta("/getJogoById/{id}").recurso(TipoRecurso.JOGO, "id");
        staff("/deleteJogo/{id}").recurso(TipoRecurso.JOGO, "id");
        staff("/getConvocatoriaByJogoId/{id}").recurso(TipoRecurso.JOGO, "id");
        staff("/createJogo");
        staff("/updateJogo");
        staff("/atualizarJogo");
        staff("/salvarConvocatoria");

        // JogoCronometroWS
        staffEAtleta("/getConfigJogo/{idJogo}").recurso(TipoRecurso.JOGO, "idJogo");
        staffEAtleta("/getTimeline/{idJogo}").recurso(TipoRecurso.JOGO, "idJogo");
        staffEAtleta("/getTemposJogo/{idJogo}").recurso(TipoRecurso.JOGO, "idJogo");
        staff("/getEventosControle/{idJogo}").recurso(TipoRecurso.JOGO, "idJogo");
        staff("/eliminarEvento/{id}").recurso(TipoRecurso.EVENTO, "id");
        staff("/guardarConfigJogo");
        staff("/registarEvento");
        staff("/marcarSubstituicao");
        staff("/editarEvento");
        staff("/atualizarTempoAtual");

        // PerformanceWS (o controller mantem a regra ADMIN/TREINADOR)
        staff("/performance/equipa/{idEquipa}/{idUtilizador}/{tenantId}").utilizador("idUtilizador")
                .tenant("tenantId").recurso(TipoRecurso.EQUIPA, "idEquipa");
        staff("/performance/config/{idEquipa}/{idUtilizador}/{tenantId}").utilizador("idUtilizador")
                .tenant("tenantId").recurso(TipoRecurso.EQUIPA, "idEquipa");
        staff("/performance/jogador/{idJogador}/{idUtilizador}/{tenantId}").utilizador("idUtilizador")
                .tenant("tenantId").recurso(TipoRecurso.JOGADOR, "idJogador");
        staff("/performance/config/{idUtilizador}/{tenantId}").utilizador("idUtilizador").tenant("tenantId");

        // PresencaWS
        staff("/presenca/{parmTenantId}").tenant("parmTenantId");
        staff("/updatepresenca/{idUtilizador}").utilizador("idUtilizador");
        // datas = ini_fim_equipa
        staff("/getPresencaByDatas/{datas}").recurso(TipoRecurso.EQUIPA, "datas", 2);
        // datas = equipa_data_hora
        staff("/isPresencabyEquipaDataHora/{datas}").recurso(TipoRecurso.EQUIPA, "datas", 0);
        staff("/getPresencaById/{id}").recurso(TipoRecurso.PRESENCA, "id");
        staff("/getHistoricoById/{id}").recurso(TipoRecurso.PRESENCA, "id");
        // parms = idPresenca_idUtilizador_tenantId
        admin("/eliminarPresenca/{parms}").utilizador("parms", 1).tenant("parms", 2)
                .recurso(TipoRecurso.PRESENCA, "parms", 0);
        staff("/dashboard/getTotalTrainings/{parmEquipaID}").recurso(TipoRecurso.EQUIPA, "parmEquipaID");
        staff("/dashboard/getAverageAthletes/{parmEquipaID}").recurso(TipoRecurso.EQUIPA, "parmEquipaID");
        staff("/dashboard/getAbsencePercentage/{parmEquipaID}").recurso(TipoRecurso.EQUIPA, "parmEquipaID");

        // LoginWS (gestao de contas: so ADMIN)
        admin("/getAllUser/{parmTenantId}").tenant("parmTenantId");
        admin("/getAllUserWait/{parmTenantId}").tenant("parmTenantId");
        admin("/getUserByUserName/{parmUserName}/{parmTenantId}").tenant("parmTenantId");
        admin("/createUtilizador/{parmTenantId}").tenant("parmTenantId");
        admin("/reenviarEmailAtivacao/{parmTenantId}").tenant("parmTenantId");
        admin("/convidarAtleta/{parmTenantId}").tenant("parmTenantId");
        admin("/getUser/{parmUserId}/{parmTenantId}").tenant("parmTenantId")
                .recurso(TipoRecurso.UTILIZADOR, "parmUserId");
        admin("/updateUser/{parmUserId}").recurso(TipoRecurso.UTILIZADOR, "parmUserId");
        admin("/updateUserWithEscaloes/{parmUserId}").recurso(TipoRecurso.UTILIZADOR, "parmUserId");
        admin("/enableUser/{parmUserId}").recurso(TipoRecurso.UTILIZADOR, "parmUserId");
        admin("/disableUser/{parmUserId}").recurso(TipoRecurso.UTILIZADOR, "parmUserId");
        admin("/resetPWD/{parmUserId}").recurso(TipoRecurso.UTILIZADOR, "parmUserId");
        admin("/getEscaloesByUser/{parmUserId}").recurso(TipoRecurso.UTILIZADOR, "parmUserId");
        admin("/getAcessoAtleta/{idJogador}/{parmTenantId}").tenant("parmTenantId")
                .recurso(TipoRecurso.JOGADOR, "idJogador");
        admin("/getAcessoAtletas/{parmTenantId}").tenant("parmTenantId");
        admin("/cancelarConvite/{parmTenantId}").tenant("parmTenantId");
        admin("/reenviarConvite/{parmTenantId}").tenant("parmTenantId");
        admin("/getcode/{parmCode}");
        admin("/gethistoricoLogins");

        // FuncionalidadeWS (o controller exige sempre identidade, tenant e, nas de ADMIN, o perfil)
        staffEAtleta("/getFuncionalidades/{tenantId}").tenant("tenantId");
        admin("/getFuncionalidadesAdmin/{tenantId}").tenant("tenantId");
        admin("/setFuncionalidade/{tenantId}").tenant("tenantId");

        // MicroCicloWS (o controller exige sempre a funcionalidade, o perfil e as equipas do treinador)
        staff("/getMicroCiclo/{tenantId}/{data}").tenant("tenantId");
        admin("/createMicroCiclo/{tenantId}").tenant("tenantId");
        admin("/submeterMicroCiclo/{tenantId}/{idMicroCiclo}").tenant("tenantId") // v9
                .recurso(TipoRecurso.MICRO_CICLO, "idMicroCiclo");
        admin("/publicarMicroCiclo/{tenantId}/{idMicroCiclo}").tenant("tenantId")
                .recurso(TipoRecurso.MICRO_CICLO, "idMicroCiclo");
        admin("/cancelarMicroCiclo/{tenantId}/{idMicroCiclo}").tenant("tenantId")
                .recurso(TipoRecurso.MICRO_CICLO, "idMicroCiclo");
        admin("/eliminarMicroCiclo/{tenantId}/{idMicroCiclo}").tenant("tenantId")
                .recurso(TipoRecurso.MICRO_CICLO, "idMicroCiclo");
        admin("/gravarTreino/{tenantId}").tenant("tenantId");
        admin("/eliminarTreino/{tenantId}/{idTreino}").tenant("tenantId").recurso(TipoRecurso.TREINO, "idTreino");
        staff("/getTreino/{tenantId}/{idTreino}").tenant("tenantId").recurso(TipoRecurso.TREINO, "idTreino");
        staff("/mudarEstadoTreino/{tenantId}/{idTreino}").tenant("tenantId").recurso(TipoRecurso.TREINO, "idTreino");
        staff("/ajustarConvocatoria/{tenantId}/{idTreino}").tenant("tenantId").recurso(TipoRecurso.TREINO, "idTreino");
        // v5 (o controller/helper aplicam sempre o perfil e as equipas)
        staff("/aprovarLote/{tenantId}/{idMicroCiclo}").tenant("tenantId")
                .recurso(TipoRecurso.MICRO_CICLO, "idMicroCiclo");
        admin("/gravarGrupoEvento/{tenantId}").tenant("tenantId");
        staffEAtleta("/getMicroCicloAtleta/{tenantId}/{idJogador}/{data}").tenant("tenantId")
                .recurso(TipoRecurso.JOGADOR, "idJogador");
        staff("/getPendentesMeus/{tenantId}").tenant("tenantId");
        // v7 (as equipas pedidas sao verificadas no helper)
        staff("/getMeusAtletas/{tenantId}/{data}").tenant("tenantId");
        // v4
        admin("/reativarMicroCiclo/{tenantId}/{idMicroCiclo}").tenant("tenantId")
                .recurso(TipoRecurso.MICRO_CICLO, "idMicroCiclo");
        admin("/copiarTreino/{tenantId}/{idTreino}").tenant("tenantId").recurso(TipoRecurso.TREINO, "idTreino");
        staffEAtleta("/getJogosAtleta/{tenantId}/{idJogador}/{data}").tenant("tenantId")
                .recurso(TipoRecurso.JOGADOR, "idJogador");
        // equipa do corpo: pertenca verificada no helper (sempre)
        admin("/setCorEquipa/{tenantId}").tenant("tenantId");
        staff("/getTreinosEquipaDia/{tenantId}/{idEquipa}/{data}").tenant("tenantId")
                .recurso(TipoRecurso.EQUIPA, "idEquipa");
        staff("/getPresencaTreino/{tenantId}/{idTreino}").tenant("tenantId").recurso(TipoRecurso.TREINO, "idTreino");
        staff("/marcarPresencaTreino/{tenantId}/{idTreino}").tenant("tenantId").recurso(TipoRecurso.TREINO, "idTreino");
        staff("/ligarPresencaTreino/{tenantId}/{idTreino}/{idPresenca}").tenant("tenantId")
                .recurso(TipoRecurso.TREINO, "idTreino");
        staffEAtleta("/getTreinosAtleta/{tenantId}/{idJogador}/{data}").tenant("tenantId")
                .recurso(TipoRecurso.JOGADOR, "idJogador");
        staffEAtleta("/indicarAusencia/{tenantId}/{idTreino}").tenant("tenantId").recurso(TipoRecurso.TREINO, "idTreino");
        // v8 (so ATLETA: o staff recebe 403 no helper)
        staffEAtleta("/responderTreino/{tenantId}/{idTreino}").tenant("tenantId").recurso(TipoRecurso.TREINO, "idTreino");
        staffEAtleta("/confirmarTodos/{tenantId}/{idJogador}").tenant("tenantId")
                .recurso(TipoRecurso.JOGADOR, "idJogador");
        staffEAtleta("/getPorConfirmarAtleta/{tenantId}/{idJogador}").tenant("tenantId")
                .recurso(TipoRecurso.JOGADOR, "idJogador");
        // Indisponibilidades (so ATLETA, o proprio jogador: o staff recebe 403 no helper)
        staffEAtleta("/getIndisponibilidades/{tenantId}/{idJogador}").tenant("tenantId")
                .recurso(TipoRecurso.JOGADOR, "idJogador");
        staffEAtleta("/gravarIndisponibilidade/{tenantId}/{idJogador}").tenant("tenantId")
                .recurso(TipoRecurso.JOGADOR, "idJogador");
        staffEAtleta("/cancelarIndisponibilidade/{tenantId}/{idJogador}/{idIndisponibilidade}").tenant("tenantId")
                .recurso(TipoRecurso.JOGADOR, "idJogador");

        // BalnearioWS (o controller exige MICRO_CICLO e ADMIN; o helper valida treinos, jogos e balnearios do tenant)
        admin("/getConfigBalnearios/{tenantId}").tenant("tenantId");
        admin("/gravarBalneario/{tenantId}").tenant("tenantId");
        admin("/apagarBalneario/{tenantId}/{idBalneario}").tenant("tenantId");
        admin("/gravarRegrasBalnearios/{tenantId}").tenant("tenantId");
        admin("/gravarBalnearios/{tenantId}").tenant("tenantId");
        admin("/calcularBalnearios/{tenantId}/{idMicroCiclo}").tenant("tenantId")
                .recurso(TipoRecurso.MICRO_CICLO, "idMicroCiclo");
        admin("/gravarCalculoBalnearios/{tenantId}/{idMicroCiclo}").tenant("tenantId")
                .recurso(TipoRecurso.MICRO_CICLO, "idMicroCiclo");

        // ImportacaoWS (o controller exige sempre ADMIN e o tenant do token)
        admin("/validarImportacao/{tenantId}").tenant("tenantId");
        admin("/gravarImportacao/{tenantId}").tenant("tenantId");

        // NotificacaoWS (so as do proprio utilizador; v5: tambem o ATLETA, so as de convocatoria)
        staffEAtleta("/getNotificacoesNaoLidas/{tenantId}").tenant("tenantId");
        staffEAtleta("/getNotificacoes/{tenantId}").tenant("tenantId");
        staffEAtleta("/marcarNotificacaoLida/{tenantId}/{idNotificacao}").tenant("tenantId");
        staffEAtleta("/marcarNotificacoesLidas/{tenantId}").tenant("tenantId");
    }

    private RegrasAcesso() {
    }

    /** @return a regra do padrao, ou {@code null} (rota sem regra: negada) */
    public static Regra get(String padrao) {
        return padrao == null ? null : REGRAS.get(padrao.replace("//", "/"));
    }
}
