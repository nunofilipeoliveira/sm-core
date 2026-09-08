package sm.core.data;

/**
 * Configuração de performance de uma equipa (escalao_epoca).
 * Controla se a equipa permite o registo de classificações de desempenho
 * nos treinos e a visualização dos indicadores de performance.
 * Alimentado pela tabela performance_config. Se a equipa não tiver linha
 * na tabela, o backend assume a configuração por omissão (tudo permitido).
 */
public class PerformanceConfigData {

	private int id_equipa;
	private boolean permitir_registo;
	private boolean permitir_visualizacao;

	/**
	 * Construtor por omissão. Necessário para o Jackson conseguir desserializar
	 * o JSON enviado pelo frontend e para inicializar a configuração por
	 * omissão (registo e visualização ativos).
	 */
	public PerformanceConfigData() {
		this.id_equipa = 0;
		this.permitir_registo = true;
		this.permitir_visualizacao = true;
	}

	public PerformanceConfigData(int id_equipa, boolean permitir_registo, boolean permitir_visualizacao) {
		this.id_equipa = id_equipa;
		this.permitir_registo = permitir_registo;
		this.permitir_visualizacao = permitir_visualizacao;
	}

	public int getId_equipa() {
		return id_equipa;
	}

	public void setId_equipa(int id_equipa) {
		this.id_equipa = id_equipa;
	}

	public boolean isPermitir_registo() {
		return permitir_registo;
	}

	public void setPermitir_registo(boolean permitir_registo) {
		this.permitir_registo = permitir_registo;
	}

	public boolean isPermitir_visualizacao() {
		return permitir_visualizacao;
	}

	public void setPermitir_visualizacao(boolean permitir_visualizacao) {
		this.permitir_visualizacao = permitir_visualizacao;
	}

}