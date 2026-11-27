package sm.core.data;

/**
 * Balneario atribuido a um treino ou a um lado de um jogo (so resposta). origem MANUAL/AUTOMATICA.
 * No atleta so id e nome (ativo e origem null).
 */
public class BalnearioAtribuidoData {

	private int id;
	private String nome;
	private Boolean ativo;
	private String origem;

	public BalnearioAtribuidoData() {
	}

	public BalnearioAtribuidoData(int id, String nome, Boolean ativo, String origem) {
		this.id = id;
		this.nome = nome;
		this.ativo = ativo;
		this.origem = origem;
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

	public Boolean getAtivo() {
		return ativo;
	}

	public void setAtivo(Boolean ativo) {
		this.ativo = ativo;
	}

	public String getOrigem() {
		return origem;
	}

	public void setOrigem(String origem) {
		this.origem = origem;
	}
}
