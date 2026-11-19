package sm.core.data;

import java.sql.Timestamp;

/**
 * Ausencia avisada por um atleta. categoria: DOENCA, LESAO, ESCOLA, FAMILIA ou OUTRO; estado: ATIVA, ANULADA ou DESCARTADA.
 */
public class TreinoAusenciaData {

	private int idJogador;
	private String nome;
	private String categoria;
	private String motivo;
	private Timestamp dataIndicacao;
	private String estado;
	private boolean pendenteConfirmacao;

	public TreinoAusenciaData() {
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

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public Timestamp getDataIndicacao() {
		return dataIndicacao;
	}

	public void setDataIndicacao(Timestamp dataIndicacao) {
		this.dataIndicacao = dataIndicacao;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public boolean isPendenteConfirmacao() {
		return pendenteConfirmacao;
	}

	public void setPendenteConfirmacao(boolean pendenteConfirmacao) {
		this.pendenteConfirmacao = pendenteConfirmacao;
	}
}
