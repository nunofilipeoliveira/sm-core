package sm.core.data;

/**
 * Linha da convocatoria de um treino (detalhe). origem: OMISSAO ou ACRESCENTADO.
 */
public class TreinoConvocadoData {

	private int idJogador;
	private String nome;
	private String equipaOrigem;
	private boolean convocado;
	private String origem;
	private boolean novoNaEquipa;
	private boolean avisou;

	public TreinoConvocadoData() {
	}

	public int getIdJogador() {
		return idJogador;
	}

	public void setIdJogador(int idJogador) {
		this.idJogador = idJogador;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getEquipaOrigem() {
		return equipaOrigem;
	}

	public void setEquipaOrigem(String equipaOrigem) {
		this.equipaOrigem = equipaOrigem;
	}

	public boolean isConvocado() {
		return convocado;
	}

	public void setConvocado(boolean convocado) {
		this.convocado = convocado;
	}

	public String getOrigem() {
		return origem;
	}

	public void setOrigem(String origem) {
		this.origem = origem;
	}

	public boolean isNovoNaEquipa() {
		return novoNaEquipa;
	}

	public void setNovoNaEquipa(boolean novoNaEquipa) {
		this.novoNaEquipa = novoNaEquipa;
	}

	public boolean isAvisou() {
		return avisou;
	}

	public void setAvisou(boolean avisou) {
		this.avisou = avisou;
	}
}
