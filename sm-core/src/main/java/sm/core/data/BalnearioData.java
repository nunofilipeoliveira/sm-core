package sm.core.data;

/**
 * Balneario do clube (balneario, V17): pedido e resposta de gravarBalneario e catalogo da semana. id 0 cria.
 * libertacao null = a global do clube; ordem e emUso so resposta (a ordem muda-se em gravarRegrasBalnearios).
 */
public class BalnearioData {

	private int id;
	private String nome;
	private int ordem;
	private Integer libertacao;
	private boolean ativo = true;
	private boolean emUso;

	public BalnearioData() {
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

	public int getOrdem() {
		return ordem;
	}

	public void setOrdem(int ordem) {
		this.ordem = ordem;
	}

	public Integer getLibertacao() {
		return libertacao;
	}

	public void setLibertacao(Integer libertacao) {
		this.libertacao = libertacao;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void setAtivo(boolean ativo) {
		this.ativo = ativo;
	}

	public boolean isEmUso() {
		return emUso;
	}

	public void setEmUso(boolean emUso) {
		this.emUso = emUso;
	}

	// ==================== regra de uso (V18); sem inicializador: no pedido, uso null = nao mexer ====================

	/** TODOS, DEDICADO ou EXCLUSAO. */
	private String uso;
	private java.util.List<Integer> usoEscaloes;
	private Boolean usoAdversarios;
	/** So resposta: "só Sub-15, Sub-17" / "exclui Sub-13, Adversários"; null em TODOS. */
	private String notaUso;

	public String getUso() {
		return uso;
	}

	public void setUso(String uso) {
		this.uso = uso;
	}

	public java.util.List<Integer> getUsoEscaloes() {
		return usoEscaloes;
	}

	public void setUsoEscaloes(java.util.List<Integer> usoEscaloes) {
		this.usoEscaloes = usoEscaloes;
	}

	public Boolean getUsoAdversarios() {
		return usoAdversarios;
	}

	public void setUsoAdversarios(Boolean usoAdversarios) {
		this.usoAdversarios = usoAdversarios;
	}

	public String getNotaUso() {
		return notaUso;
	}

	public void setNotaUso(String notaUso) {
		this.notaUso = notaUso;
	}
}
