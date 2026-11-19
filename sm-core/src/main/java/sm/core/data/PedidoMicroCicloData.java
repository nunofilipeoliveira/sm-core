package sm.core.data;

/**
 * Corpo de createMicroCiclo: dataInicio (segunda-feira yyyymmdd) e copiarDe (segunda-feira de outra semana, ou null).
 */
public class PedidoMicroCicloData {

	private int dataInicio;
	private Integer copiarDe;

	public PedidoMicroCicloData() {
	}

	public int getDataInicio() {
		return dataInicio;
	}

	public void setDataInicio(int dataInicio) {
		this.dataInicio = dataInicio;
	}

	public Integer getCopiarDe() {
		return copiarDe;
	}

	public void setCopiarDe(Integer copiarDe) {
		this.copiarDe = copiarDe;
	}
}
