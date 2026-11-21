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

	/** v8: VOU, NAO_VOU ou null. */
	private String resposta;
	/** v8 (Y2): o Vou foi dado a uma sessao que mudou. */
	private boolean caducada;
	/** v8 (Y10): data da confirmacao ou da ausencia; null para o TREINADOR de outra equipa. */
	private java.sql.Timestamp dataResposta;
	/** v8 (1.1): sem conta ATLETA ativa. */
	private boolean semConta;

	public TreinoConvocadoData() {
	}

	public String getResposta() {
		return resposta;
	}

	public void setResposta(String resposta) {
		this.resposta = resposta;
	}

	public boolean isCaducada() {
		return caducada;
	}

	public void setCaducada(boolean caducada) {
		this.caducada = caducada;
	}

	public java.sql.Timestamp getDataResposta() {
		return dataResposta;
	}

	public void setDataResposta(java.sql.Timestamp dataResposta) {
		this.dataResposta = dataResposta;
	}

	public boolean isSemConta() {
		return semConta;
	}

	public void setSemConta(boolean semConta) {
		this.semConta = semConta;
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
