package sm.core.data;

public class JogadorConvocado {
    
    private int id_jogador;
    private String nome;
    private String estado;
    private String obs;
    private String licenca;
    // Anotacoes das indisponibilidades (null por omissao): intervalo yyyymmdd e se a linha foi posta automaticamente
    private Integer indisponivelDe;
    private Integer indisponivelAte;
    private Boolean automatica;

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

    public Integer getIndisponivelDe() {
        return indisponivelDe;
    }

    public void setIndisponivelDe(Integer indisponivelDe) {
        this.indisponivelDe = indisponivelDe;
    }

    public Integer getIndisponivelAte() {
        return indisponivelAte;
    }

    public void setIndisponivelAte(Integer indisponivelAte) {
        this.indisponivelAte = indisponivelAte;
    }

    public Boolean getAutomatica() {
        return automatica;
    }

    public void setAutomatica(Boolean automatica) {
        this.automatica = automatica;
    }

    
}
