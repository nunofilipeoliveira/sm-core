package sm.core.data;

public class JogadorJogo {
    
private int id_jogador;
private String nome;
private boolean capitao;
private int numero;
private int amarelo;
private int azul;
private int vermelho;
private int golos_p;
private int golos_ld;
private int golos_pp;
private int golos_up;
private int golos_normal;
private int golos_s_p;
private int golos_s_ld;
private int golos_s_up;
private int golos_s_pp;
private int golos_s_normal;
private String estado;
private String obs;

public JogadorJogo(int id_jogador, String nome, boolean capitao, int numero, int amarelo, int azul, int vermelho, int golos_p, int golos_ld,
        int golos_pp, int golos_up, int golos_normal, int golos_s_p, int golos_s_ld, int golos_s_up, int golos_s_pp,
        int golos_s_normal, String estado, String obs) {
    this.id_jogador = id_jogador;
    this.nome = nome;
    this.capitao = capitao;
    this.numero = numero;
    this.amarelo = amarelo;
    this.azul = azul;
    this.vermelho = vermelho;
    this.golos_p = golos_p;
    this.golos_ld = golos_ld;
    this.golos_pp = golos_pp;
    this.golos_up = golos_up;
    this.golos_normal = golos_normal;
    this.golos_s_p = golos_s_p;
    this.golos_s_ld = golos_s_ld;
    this.golos_s_up = golos_s_up;
    this.golos_s_pp = golos_s_pp;
    this.golos_s_normal = golos_s_normal;
    this.estado = estado;
    this.obs = obs;
        }

    

public String getEstado() {
    return estado;
}



public void setEstado(String estado) {
    this.estado = estado;
}



public String getObs() {
    return obs;
}



public void setObs(String obs) {
    this.obs = obs;
}



public int getId_jogador() {
    return id_jogador;
}

public void setId_jogador(int id_jogador) {
    this.id_jogador = id_jogador;
}

public String getNome() {
    return nome;
}

public void setNome(String nome) {
    this.nome = nome;
}

public boolean getCapitao() {
    return capitao;
}

public void setCapitao(boolean capitao) {
    this.capitao = capitao;
}

public int getNumero() {
    return numero;
}

public void setNumero(int numero) {
    this.numero = numero;
}

public int getAmarelo() {
    return amarelo;
}

public void setAmarelo(int amarelo) {
    this.amarelo = amarelo;
}

public int getAzul() {
    return azul;
}

public void setAzul(int azul) {
    this.azul = azul;
}

public int getVermelho() {
    return vermelho;
}

public void setVermelho(int vermelho) {
    this.vermelho = vermelho;
}

public int getGolos_p() {
    return golos_p;
}

public void setGolos_p(int golos_p) {
    this.golos_p = golos_p;
}

public int getGolos_ld() {
    return golos_ld;
}

public void setGolos_ld(int golos_ld) {
    this.golos_ld = golos_ld;
}

public int getGolos_pp() {
    return golos_pp;
}

public void setGolos_pp(int golos_pp) {
    this.golos_pp = golos_pp;
}

public int getGolos_up() {
    return golos_up;
}

public void setGolos_up(int golos_up) {
    this.golos_up = golos_up;
}

public int getGolos_normal() {
    return golos_normal;
}

public void setGolos_normal(int golos_normal) {
    this.golos_normal = golos_normal;
}

public int getGolos_s_p() {
    return golos_s_p;
}

public void setGolos_s_p(int golos_s_p) {
    this.golos_s_p = golos_s_p;
}

public int getGolos_s_ld() {
    return golos_s_ld;
}

public void setGolos_s_ld(int golos_s_ld) {
    this.golos_s_ld = golos_s_ld;
}

public int getGolos_s_up() {
    return golos_s_up;
}

public void setGolos_s_up(int golos_s_up) {
    this.golos_s_up = golos_s_up;
}

public int getGolos_s_pp() {
    return golos_s_pp;
}

public void setGolos_s_pp(int golos_s_pp) {
    this.golos_s_pp = golos_s_pp;
}

public int getGolos_s_normal() {
    return golos_s_normal;
}

public void setGolos_s_normal(int golos_s_normal) {
    this.golos_s_normal = golos_s_normal;
}



        
}
