package sm.core.data;

public class JogadorConvocado {
    
    private int id_jogador;
    private String nome;
    private String estado;
    private String obs;
    private String licenca;

    public JogadorConvocado(int id_jogador, String nome, String estado, String obs, String licenca) {
        this.id_jogador = id_jogador;
        this.nome = nome;
        this.estado = estado;
        this.obs = obs;
        this.licenca=licenca;
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

    public String getLicenca() {
        return licenca;
    }

    public void setLicenca(String licenca) {
        this.licenca = licenca;
    }

    
}
