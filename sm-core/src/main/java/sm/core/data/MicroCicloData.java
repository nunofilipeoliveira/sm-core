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

	public MicroCicloData() {
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
