package sm.core.data;

import java.util.ArrayList;

public class HistoricoJogadorData {
    private int id;
    private String nome;
    private ArrayList<EpocaHistoricoData> epocas;

    public HistoricoJogadorData(int id, String nome) {
        this.id = id;
        this.nome = nome;
        this.epocas = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public ArrayList<EpocaHistoricoData> getEpocas() {
        return epocas;
    }

    public void setEpocas(ArrayList<EpocaHistoricoData> epocas) {
        this.epocas = epocas;
    }

    public void addEpoca(EpocaHistoricoData epoca) {
        this.epocas.add(epoca);
    }
}