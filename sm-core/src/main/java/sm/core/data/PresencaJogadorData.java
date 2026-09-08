package sm.core.data;

public class PresencaJogadorData {

	private int id_jogador;
	private String nome_jogador;
	private String estado;
	private String motivo;
	// Classificação de desempenho do atleta no treino (1 a 5 estrelas). Null se ainda não avaliado.
	private Integer classificacao;

	/**
	 * Construtor por omissão. Necessário para o Jackson conseguir desserializar o
	 * JSON enviado pelo frontend (ex.: PUT /sm/updatepresenca). Sem este
	 * construtor — e existindo mais do que um construtor com argumentos — o
	 * Jackson não consegue escolher um creator e devolve
	 * InvalidDefinitionException ("no Creators, like default constructor, exist").
	 */
	public PresencaJogadorData() {
		super();
	}

	public PresencaJogadorData(int id_jogador, String nome_jogador, String estado, String motivo) {
		super();
		this.id_jogador = id_jogador;
		this.nome_jogador = nome_jogador;
		this.estado = estado;
		this.motivo = motivo;
	}

	public PresencaJogadorData(int id_jogador, String nome_jogador, String estado, String motivo,
			Integer classificacao) {
		super();
		this.id_jogador = id_jogador;
		this.nome_jogador = nome_jogador;
		this.estado = estado;
		this.motivo = motivo;
		this.classificacao = classificacao;
	}

	public Integer getClassificacao() {
		return classificacao;
	}

	public void setClassificacao(Integer classificacao) {
		this.classificacao = classificacao;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}


	public int getId_jogador() {
		return id_jogador;
	}

	public void setId_jogador(int id_jogador) {
		this.id_jogador = id_jogador;
	}

	public String getNome_jogador() {
		return nome_jogador;
	}

	public void setNome_jogador(String nome_jogador) {
		this.nome_jogador = nome_jogador;
	}
}