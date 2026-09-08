package sm.core.data;

/**
 * Registo histórico de presença/falta de um atleta num treino.
 */
public class PerformanceHistoricoData {

	// Data do treino no formato yyyyMMdd.
	private int data;
	private String hora;
	private String nome_equipa;
	private String estado;
	private String motivo;
	// Classificação atribuída ao atleta no treino (1 a 5). Null se não avaliado.
	private Integer classificacao;

	public PerformanceHistoricoData() {
		this.data = 0;
		this.hora = "";
		this.nome_equipa = "";
		this.estado = "";
		this.motivo = "";
		this.classificacao = null;
	}

	public PerformanceHistoricoData(int data, String hora, String nome_equipa, String estado, String motivo,
			Integer classificacao) {
		this.data = data;
		this.hora = hora != null ? hora : "";
		this.nome_equipa = nome_equipa != null ? nome_equipa : "";
		this.estado = estado != null ? estado : "";
		this.motivo = motivo != null ? motivo : "";
		this.classificacao = classificacao;
	}

	public int getData() {
		return data;
	}

	public void setData(int data) {
		this.data = data;
	}

	public String getHora() {
		return hora;
	}

	public void setHora(String hora) {
		this.hora = hora;
	}

	public String getNome_equipa() {
		return nome_equipa;
	}

	public void setNome_equipa(String nome_equipa) {
		this.nome_equipa = nome_equipa;
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

	public Integer getClassificacao() {
		return classificacao;
	}

	public void setClassificacao(Integer classificacao) {
		this.classificacao = classificacao;
	}

}
