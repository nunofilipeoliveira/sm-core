package sm.core.data;

import java.util.ArrayList;

public class EpocaHistoricoData {
    private int id;
    private String descritivo;
    private int anoInicio;
    private String nomeEscalao;
    private ArrayList<PresencaData> treinos;
    private ArrayList<JogoData> jogos;

    public EpocaHistoricoData(int id, String descritivo, int anoInicio, String nomeEscalao) {
        this.id = id;
        this.descritivo = descritivo;
        this.anoInicio = anoInicio;
        this.nomeEscalao = nomeEscalao;
        this.treinos = new ArrayList<>();
        this.jogos = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDescritivo() {
        return descritivo;
    }

    public void setDescritivo(String descritivo) {
        this.descritivo = descritivo;
    }

    public int getAnoInicio() {
        return anoInicio;
    }

    public void setAnoInicio(int anoInicio) {
        this.anoInicio = anoInicio;
    }

    public String getNomeEscalao() {
        return nomeEscalao;
    }

    public void setNomeEscalao(String nomeEscalao) {
        this.nomeEscalao = nomeEscalao;
    }

    public ArrayList<PresencaData> getTreinos() {
        return treinos;
    }

    public void setTreinos(ArrayList<PresencaData> treinos) {
        this.treinos = treinos;
    }

    public ArrayList<JogoData> getJogos() {
        return jogos;
    }

    public void setJogos(ArrayList<JogoData> jogos) {
        this.jogos = jogos;
    }

    public void addTreino(PresencaData treino) {
        this.treinos.add(treino);
    }

    public void addJogo(JogoData jogo) {
        this.jogos.add(jogo);
    }
}