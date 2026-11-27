package sm.core.data;

/**
 * Micro ciclo (semana de treinos do clube). estado: RASCUNHO, PUBLICADO ou CANCELADO; dataInicio = segunda-feira yyyymmdd.
 */
public class MicroCicloData {

	private int id;
	private int dataInicio;
	private String estado;
	private String motivoCancelamento;
	private int idEpoca;

	/** v9 (V15): PUBLICADO ou EM_APROVACAO num cancelado; null = cancelado antes da v9 (a partir de PUBLICADO). So staff. */
	private String estadoAntesCancelamento;

	public MicroCicloData() {
	}

	public String getEstadoAntesCancelamento() {
		return estadoAntesCancelamento;
	}

	public void setEstadoAntesCancelamento(String estadoAntesCancelamento) {
		this.estadoAntesCancelamento = estadoAntesCancelamento;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getDataInicio() {
		return dataInicio;
	}

	public void setDataInicio(int dataInicio) {
		this.dataInicio = dataInicio;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public String getMotivoCancelamento() {
		return motivoCancelamento;
	}

	public void setMotivoCancelamento(String motivoCancelamento) {
		this.motivoCancelamento = motivoCancelamento;
	}

	public int getIdEpoca() {
		return idEpoca;
	}

	public void setIdEpoca(int idEpoca) {
		this.idEpoca = idEpoca;
	}
}
