package sm.core.data;

public class ElementoSeleccao {

	private Integer id_Jogador;
	private String nome_Jogador;
	private String escalao;
	public ElementoSeleccao(Integer id_Jogador, String nome_Jogador, String escalao) {
		super();
		this.id_Jogador = id_Jogador;
		this.nome_Jogador = nome_Jogador;
		this.escalao = escalao;
	}
	public Integer getId_Jogador() {
		return id_Jogador;
	}
	public void setId_Jogador(Integer id_Jogador) {
		this.id_Jogador = id_Jogador;
	}
	public String getNome_Jogador() {
		return nome_Jogador;
	}
	public void setNome_Jogador(String nome_Jogador) {
		this.nome_Jogador = nome_Jogador;
	}
	public String getEscalao() {
		return escalao;
	}
	public void setEscalao(String escalao) {
		this.escalao = escalao;
	}
	
	
	
}
