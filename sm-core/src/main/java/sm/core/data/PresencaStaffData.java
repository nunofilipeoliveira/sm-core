package sm.core.data;

public class PresencaStaffData {

	private int id_staff;
	private String nome_staff;
	private String estado;
	private String motivo;

	public PresencaStaffData(int id_staff, String nome_staff, String estado, String motivo) {
		super();
		this.id_staff = id_staff;
		this.nome_staff = nome_staff;
		this.estado = estado;
		this.motivo = motivo;
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


	public int getid_staff() {
		return id_staff;
	}

	public void setid_staff(int id_staff) {
		this.id_staff = id_staff;
	}

	public String getnome_staff() {
		return nome_staff;
	}

	public void setnome_staff(String nome_staff) {
		this.nome_staff = nome_staff;
	}
}