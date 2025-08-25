package sm.core.data;

public class EpocaData {

	private int id;
	private String epocaDescritivo;
	private String estado;
	private int anoInicio;

	public EpocaData(int id, String epocaDescritivo, String estado, int anoInicio) {
		super();
		this.id = id;
		this.epocaDescritivo = epocaDescritivo;
		this.estado = estado;
		this.anoInicio = anoInicio;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getEpocaDescritivo() {
		return epocaDescritivo;
	}

	public void setEpocaDescritivo(String epocaDescritivo) {
		this.epocaDescritivo = epocaDescritivo;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public int getAnoInicio() {
		return anoInicio;
	}

	public void setAnoInicio(int anoInicio) {
		this.anoInicio = anoInicio;
	}

}
