package sm.core.data;

/**
 * Ponto de evolução da classificação de um atleta num treino
 * (utilizado para o gráfico de evolução da performance).
 */
public class PerformanceEvolucaoTreinoData {

	private int id_presenca;
	// Data do treino no formato yyyyMMdd.
	private int data;
	private String hora;
	// Classificação atribuída ao atleta no treino (1 a 5).
	private Integer classificacao;

	public PerformanceEvolucaoTreinoData() {
		this.id_presenca = 0;
		this.data = 0;
		this.hora = "";
		this.classificacao = null;
	}

	public PerformanceEvolucaoTreinoData(int id_presenca, int data, String hora, Integer classificacao) {
		this.id_presenca = id_presenca;
		this.data = data;
		this.hora = hora != null ? hora : "";
		this.classificacao = classificacao;
	}

	public int getId_presenca() {
		return id_presenca;
	}

	public void setId_presenca(int id_presenca) {
		this.id_presenca = id_presenca;
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

	public Integer getClassificacao() {
		return classificacao;
	}

	public void setClassificacao(Integer classificacao) {
		this.classificacao = classificacao;
	}

}
