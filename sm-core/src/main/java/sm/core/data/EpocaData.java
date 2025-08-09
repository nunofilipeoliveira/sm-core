package sm.core.data;

public class EpocaData {
	
	private int id;
	private String epocaDescritivo;
	private String estado;
	public EpocaData(int id, String epocaDescritivo, String estado) {
		super();
		this.id = id;
		this.epocaDescritivo = epocaDescritivo;
		this.estado = estado;
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

}
