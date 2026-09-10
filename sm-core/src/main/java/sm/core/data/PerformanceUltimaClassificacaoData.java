package sm.core.data;

/**
 * Entrada da lista "Últimas Classificações" apresentada na ficha do jogador.
 */
public class PerformanceUltimaClassificacaoData {

	// Data do treino no formato yyyyMMdd.
	private int data;
	// Classificação atribuída ao atleta no treino (1 a 5).
	private Integer classificacao;

	public PerformanceUltimaClassificacaoData() {
		this.data = 0;
		this.classificacao = null;
	}

	public PerformanceUltimaClassificacaoData(int data, Integer classificacao) {
		this.data = data;
		this.classificacao = classificacao;
	}

	public int getData() {
		return data;
	}

	public void setData(int data) {
		this.data = data;
	}

	public Integer getClassificacao() {
		return classificacao;
	}

	public void setClassificacao(Integer classificacao) {
		this.classificacao = classificacao;
	}

}
