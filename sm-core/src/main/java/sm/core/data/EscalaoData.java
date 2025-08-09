package sm.core.data;

public class EscalaoData {
	
	private int id;
	private String escalaoDescritivo;
	public EscalaoData(int id, String escalaoDescritivo) {
		super();
		this.id = id;
		this.escalaoDescritivo = escalaoDescritivo;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getEscalaoDescritivo() {
		return escalaoDescritivo;
	}
	public void setEscalaoDescritivo(String escalaoDescritivo) {
		this.escalaoDescritivo = escalaoDescritivo;
	}
	
}
