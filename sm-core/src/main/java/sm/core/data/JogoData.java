package sm.core.data;


import java.util.ArrayList;

public class JogoData {
    private int id;
    private int epoca_id;
    private int equipa_id;
    private String tipoEquipa;
    private String data;
    private String hora;
    private String local;
    private int golos_equipa;
    private int equipa_adv_id;
    private String equipa_adv_nome;
    private String tipoEquipa_adv;
    private int golos_equipa_adv;
    private String tipo_local;
    private int competicao_id;
    private String competicao_nome;
    private String arbitro_1;
    private String arbitro_2;
    private String estado;
    private ArrayList<JogadorJogo> jogadores;
    private String hora_concentracao;
    private String obs;
    private String numeroJogo;


    

    public String getHora_concentracao() {
        return hora_concentracao;
    }
    public void setHora_concentracao(String hora_concentracao) {
        this.hora_concentracao = hora_concentracao;
    }
    public String getObs() {
        return obs;
    }
    public void setObs(String obs) {
        this.obs = obs;
    }
    public String getEstado() {
        return estado;
    }
    public void setEstado(String estado) {
        this.estado = estado;
    }
    public String getCompeticao_nome() {
        return competicao_nome;
    }
    public void setCompeticao_nome(String competicao_nome) {
        this.competicao_nome = competicao_nome;
    }
    
    public String getEquipa_adv_nome() {
        return equipa_adv_nome;
    }
    public void setEquipa_adv_nome(String equipa_adv_nome) {
        this.equipa_adv_nome = equipa_adv_nome;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public int getEpoca_id() {
        return epoca_id;
    }
    public void setEpoca_id(int epoca_id) {
        this.epoca_id = epoca_id;
    }
    public int getEquipa_id() {
        return equipa_id;
    }
    public void setEquipa_id(int equipa_id) {
        this.equipa_id = equipa_id;
    }
    public String getTipoEquipa() {
        return tipoEquipa;
    }
    public void setTipoEquipa(String tipoEquipa) {
        this.tipoEquipa = tipoEquipa;
    }
    public String getData() {
        return data;
    }
    public void setData(String data) {
        this.data = data;
    }
    public String getHora() {
        return hora;
    }
    public void setHora(String hora) {
        this.hora = hora;
    }
    public String getLocal() {
        return local;
    }
    public void setLocal(String local) {
        this.local = local;
    }
    public int getGolos_equipa() {
        return golos_equipa;
    }
    public void setGolos_equipa(int golos_equipa) {
        this.golos_equipa = golos_equipa;
    }
    public int getEquipa_adv_id() {
        return equipa_adv_id;
    }
    public void setEquipa_adv_id(int equipa_adv_id) {
        this.equipa_adv_id = equipa_adv_id;
    }
    public String getTipoEquipa_adv() {
        return tipoEquipa_adv;
    }
    public void setTipoEquipa_adv(String tipoEquipa_adv) {
        this.tipoEquipa_adv = tipoEquipa_adv;
    }
    public int getGolos_equipa_adv() {
        return golos_equipa_adv;
    }
    public void setGolos_equipa_adv(int golos_equipa_adv) {
        this.golos_equipa_adv = golos_equipa_adv;
    }
    public String getTipo_local() {
        return tipo_local;
    }
    public void setTipo_local(String tipo_local) {
        this.tipo_local = tipo_local;
    }
    public int getCompeticao_id() {
        return competicao_id;
    }
    public void setCompeticao_id(int competicao_id) {
        this.competicao_id = competicao_id;
    }
    public String getArbitro_1() {
        return arbitro_1;
    }
    public void setArbitro_1(String arbitro_1) {
        this.arbitro_1 = arbitro_1;
    }
    public String getArbitro_2() {
        return arbitro_2;
    }
    public void setArbitro_2(String arbitro_2) {
        this.arbitro_2 = arbitro_2;
    }
    


    public JogoData(int id, int epoca_id, int equipa_id, String tipoEquipa, String data, String hora, String local,
            int golos_equipa, int equipa_adv_id, String tipoEquipa_adv, String equipa_adv_nome, int golos_equipa_adv, String tipo_local,
            int competicao_id, String competicao_nome, String arbitro_1, String arbitro_2, String estado, String hora_concentracao, String obs, String numeroJogo) {
        this.id = id;
        this.epoca_id = epoca_id;
        this.equipa_id = equipa_id;
        this.tipoEquipa = tipoEquipa;
        this.data = data;
        this.hora = hora;
        this.local = local;
        this.golos_equipa = golos_equipa;
        this.equipa_adv_id = equipa_adv_id;
        this.tipoEquipa_adv = tipoEquipa_adv;
        this.golos_equipa_adv = golos_equipa_adv;
        this.tipo_local = tipo_local;
        this.competicao_id = competicao_id;
        this.arbitro_1 = arbitro_1;
        this.arbitro_2 = arbitro_2;
        this.equipa_adv_nome = equipa_adv_nome;
        this.competicao_nome = competicao_nome;
        this.estado = estado;
        this.hora_concentracao = hora_concentracao;
        this.obs = obs;
        this.numeroJogo=numeroJogo;
    }

    public ArrayList<JogadorJogo> getJogadores() {
        return jogadores;
    }
    public void setJogadores(ArrayList<JogadorJogo> jogadores) {
        this.jogadores = jogadores;
    }
    public String getNumeroJogo() {
        return numeroJogo;
    }
    public void setNumeroJogo(String numeroJogo) {
        this.numeroJogo = numeroJogo;
    }

    
}
