package sm.core.data;

public class ClubeData {
    private int id;
    private String nome;
    private String pav_nome;
    private String pav_endereco;
    private String pav_link;

    public ClubeData(int id, String nome, String pav_nome, String pav_morada, String pav_link) {
        super();
        this.id = id;
        this.nome = nome;
        this.pav_nome = pav_nome;
        this.pav_endereco = pav_morada;
        this.pav_link = pav_link;
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
    public String getPav_nome() {
        return pav_nome;
    }
    public void setPav_nome(String pav_nome) {
        this.pav_nome = pav_nome;
    }
    public String getPav_endereco() {
        return pav_endereco;
    }
    public void setPav_endereco(String pav_morada) {
        this.pav_endereco = pav_morada;
    }
    public String getPav_link() {
        return pav_link;
    }
    public void setPav_link(String pav_link) {
        this.pav_link = pav_link;
    }

}
