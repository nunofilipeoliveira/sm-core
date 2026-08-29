package sm.core.data;

/**
 * Pedido para corrigir manualmente o tempo de jogo de um jogador.
 *
 * tempo_correcao_segundos: novo tempo de jogo do jogador (segundos).
 * tempo_atual_segundos: posição atual do cronómetro (para registar o evento
 * CORRECAO_TEMPO na timeline).
 */
public class AtualizarTempoJogoRequest {

    private int id_jogo;
    private int id_jogador;
    private int tempo_correcao_segundos;
    private int tempo_atual_segundos;
    private String tempo_atual_display;

    public AtualizarTempoJogoRequest() {
        super();
    }

    public int getId_jogo() {
        return id_jogo;
    }

    public void setId_jogo(int id_jogo) {
        this.id_jogo = id_jogo;
    }

    public int getId_jogador() {
        return id_jogador;
    }

    public void setId_jogador(int id_jogador) {
        this.id_jogador = id_jogador;
    }

    public int getTempo_correcao_segundos() {
        return tempo_correcao_segundos;
    }

    public void setTempo_correcao_segundos(int tempo_correcao_segundos) {
        this.tempo_correcao_segundos = tempo_correcao_segundos;
    }

    public int getTempo_atual_segundos() {
        return tempo_atual_segundos;
    }

    public void setTempo_atual_segundos(int tempo_atual_segundos) {
        this.tempo_atual_segundos = tempo_atual_segundos;
    }

    public String getTempo_atual_display() {
        return tempo_atual_display;
    }

    public void setTempo_atual_display(String tempo_atual_display) {
        this.tempo_atual_display = tempo_atual_display;
    }
}