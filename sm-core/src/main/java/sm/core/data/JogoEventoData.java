package sm.core.data;

/**
 * Representa um evento registado na timeline de um jogo.
 *
 * Em modo cronómetro cada evento fica associado ao tempo (MM:SS exibido e
 * tempo absoluto em segundos) para permitir reconstruir a timeline, calcular
 * tempos de jogo e gerir substituições / exclusões (cartão azul = 2 min).
 */
public class JogoEventoData {

    public static final String TIPO_GOLO = "GOLO";
    public static final String TIPO_GOLO_SOFRIDO = "GOLO_SOFRIDO";
    public static final String TIPO_AMARELO = "AMARELO";
    public static final String TIPO_AZUL = "AZUL";
    public static final String TIPO_VERMELHO = "VERMELHO";
    public static final String TIPO_FALTA = "FALTA";
    public static final String TIPO_ASSISTENCIA = "ASSISTENCIA";
    public static final String TIPO_RECUPERACAO_BOLA = "RECUPERACAO_BOLA";
    public static final String TIPO_PERDA_BOLA = "PERDA_BOLA";
    public static final String TIPO_REMATE = "REMATE";
    public static final String TIPO_PENALTY_FALHADO = "PENALTY_FALHADO";
    public static final String TIPO_PENALTY_DEFESA = "PENALTY_DEFESA";
    public static final String TIPO_LD_FALHADO = "LD_FALHADO";
    public static final String TIPO_LD_DEFESA = "LD_DEFESA";
    public static final String TIPO_SUBSTITUICAO = "SUBSTITUICAO";
    public static final String TIPO_INICIO_JOGO = "INICIO_JOGO";
    public static final String TIPO_INICIO_PARTE = "INICIO_PARTE";
    public static final String TIPO_FIM_PARTE = "FIM_PARTE";
    public static final String TIPO_FIM_JOGO = "FIM_JOGO";

    // Constantes para id_equipa (identifica a equipa à qual o evento pertence)
    public static final int EQUIPA_NOSSA = 0;
    public static final int EQUIPA_ADVERSARIA = 1;

    private int id;
    private int id_jogo;
    private int id_parte;
    private String tempo_evento;
    private int tempo_segundos;
    private String tipo_evento;
    private int id_jogador;
    private int id_jogador_secundario;
    private String detalhe;
    private String obs;
    private int id_equipa;

    // Campos de apresentação preenchidos pelo backend (JOIN à jogador)
    private String nome_jogador;
    private String nome_jogador_secundario;
    private String criado_em;

    public JogoEventoData() {
        super();
    }

    public JogoEventoData(int id, int id_jogo, int id_parte, String tempo_evento, int tempo_segundos,
            String tipo_evento, int id_jogador, int id_jogador_secundario, String detalhe, String obs) {
        this.id = id;
        this.id_jogo = id_jogo;
        this.id_parte = id_parte;
        this.tempo_evento = tempo_evento;
        this.tempo_segundos = tempo_segundos;
        this.tipo_evento = tipo_evento;
        this.id_jogador = id_jogador;
        this.id_jogador_secundario = id_jogador_secundario;
        this.detalhe = detalhe;
        this.obs = obs;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId_jogo() {
        return id_jogo;
    }

    public void setId_jogo(int id_jogo) {
        this.id_jogo = id_jogo;
    }

    public int getId_parte() {
        return id_parte;
    }

    public void setId_parte(int id_parte) {
        this.id_parte = id_parte;
    }

    public String getTempo_evento() {
        return tempo_evento;
    }

    public void setTempo_evento(String tempo_evento) {
        this.tempo_evento = tempo_evento;
    }

    public int getTempo_segundos() {
        return tempo_segundos;
    }

    public void setTempo_segundos(int tempo_segundos) {
        this.tempo_segundos = tempo_segundos;
    }

    public String getTipo_evento() {
        return tipo_evento;
    }

    public void setTipo_evento(String tipo_evento) {
        this.tipo_evento = tipo_evento;
    }

    public int getId_jogador() {
        return id_jogador;
    }

    public void setId_jogador(int id_jogador) {
        this.id_jogador = id_jogador;
    }

    public int getId_jogador_secundario() {
        return id_jogador_secundario;
    }

    public void setId_jogador_secundario(int id_jogador_secundario) {
        this.id_jogador_secundario = id_jogador_secundario;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public void setDetalhe(String detalhe) {
        this.detalhe = detalhe;
    }

    public String getObs() {
        return obs;
    }

    public void setObs(String obs) {
        this.obs = obs;
    }

    public int getId_equipa() {
        return id_equipa;
    }

    public void setId_equipa(int id_equipa) {
        this.id_equipa = id_equipa;
    }

    public String getNome_jogador() {
        return nome_jogador;
    }

    public void setNome_jogador(String nome_jogador) {
        this.nome_jogador = nome_jogador;
    }

    public String getNome_jogador_secundario() {
        return nome_jogador_secundario;
    }

    public void setNome_jogador_secundario(String nome_jogador_secundario) {
        this.nome_jogador_secundario = nome_jogador_secundario;
    }

    public String getCriado_em() {
        return criado_em;
    }

    public void setCriado_em(String criado_em) {
        this.criado_em = criado_em;
    }

    @Override
    public String toString() {
        return "JogoEventoData [id=" + id + ", id_jogo=" + id_jogo + ", id_parte=" + id_parte + ", tempo_evento="
                + tempo_evento + ", tempo_segundos=" + tempo_segundos + ", tipo_evento=" + tipo_evento + ", id_jogador="
                + id_jogador + ", id_jogador_secundario=" + id_jogador_secundario + ", detalhe=" + detalhe
                + ", id_equipa=" + id_equipa + "]";
    }
}

