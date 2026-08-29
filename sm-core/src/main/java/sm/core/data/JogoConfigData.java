package sm.core.data;

import java.util.ArrayList;

/**
 * Configuração de registo de um jogo: modo normal ou cronómetro.
 *
 * No modo CRONOMETRO o utilizador define a duração de cada parte e escolhe
 * os jogadores iniciais (5 inicial), que ficam marcados como titulares.
 */
public class JogoConfigData {

    public static final String MODO_NORMAL = "NORMAL";
    public static final String MODO_CRONOMETRO = "CRONOMETRO";

    private int id;
    private int id_jogo;
    private String modo_registo;
    private int duracao_parte_minutos;
    private int numero_partes;
    private int num_jogadores_iniciais;
    private int duracao_exclusao_azul_segundos;
    private int tempo_atual_segundos;

    /** Jogadores convocados para o jogo, com os campos titular/em_campo preenchidos. */
    private ArrayList<JogadorJogo> jogadores;

    public JogoConfigData() {
        super();
        this.modo_registo = MODO_NORMAL;
        this.duracao_parte_minutos = 25;
        this.numero_partes = 2;
        this.num_jogadores_iniciais = 5;
        this.duracao_exclusao_azul_segundos = 120;
    }

    public JogoConfigData(int id, int id_jogo, String modo_registo, int duracao_parte_minutos, int numero_partes,
            int num_jogadores_iniciais, int duracao_exclusao_azul_segundos, int tempo_atual_segundos) {
        this();
        this.id = id;
        this.id_jogo = id_jogo;
        this.modo_registo = modo_registo;
        this.duracao_parte_minutos = duracao_parte_minutos;
        this.numero_partes = numero_partes;
        this.num_jogadores_iniciais = num_jogadores_iniciais;
        this.duracao_exclusao_azul_segundos = duracao_exclusao_azul_segundos;
        this.tempo_atual_segundos = tempo_atual_segundos;
    }

    public boolean isCronometro() {
        return MODO_CRONOMETRO.equalsIgnoreCase(modo_registo);
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

    public String getModo_registo() {
        return modo_registo;
    }

    public void setModo_registo(String modo_registo) {
        this.modo_registo = modo_registo;
    }

    public int getDuracao_parte_minutos() {
        return duracao_parte_minutos;
    }

    public void setDuracao_parte_minutos(int duracao_parte_minutos) {
        this.duracao_parte_minutos = duracao_parte_minutos;
    }

    public int getNumero_partes() {
        return numero_partes;
    }

    public void setNumero_partes(int numero_partes) {
        this.numero_partes = numero_partes;
    }

    public int getNum_jogadores_iniciais() {
        return num_jogadores_iniciais;
    }

    public void setNum_jogadores_iniciais(int num_jogadores_iniciais) {
        this.num_jogadores_iniciais = num_jogadores_iniciais;
    }

    public int getDuracao_exclusao_azul_segundos() {
        return duracao_exclusao_azul_segundos;
    }

    public void setDuracao_exclusao_azul_segundos(int duracao_exclusao_azul_segundos) {
        this.duracao_exclusao_azul_segundos = duracao_exclusao_azul_segundos;
    }

    public int getTempo_atual_segundos() {
        return tempo_atual_segundos;
    }

    public void setTempo_atual_segundos(int tempo_atual_segundos) {
        this.tempo_atual_segundos = tempo_atual_segundos;
    }

    public ArrayList<JogadorJogo> getJogadores() {
        return jogadores;
    }

    public void setJogadores(ArrayList<JogadorJogo> jogadores) {
        this.jogadores = jogadores;
    }
}