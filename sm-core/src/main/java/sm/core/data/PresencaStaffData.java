package sm.core.data;

public class PresencaStaffData {

	private int id_staff;
	private String nome_staff;
	private String estado;
	private String motivo;

	/**
	 * Construtor por omissão. Necessário para o Jackson conseguir desserializar
	 * o JSON enviado pelo frontend (ex.: PUT /sm/updatepresenca, campo
	 * staffPresenca). Com um único construtor com argumentos o Jackson consegue
	 * usá-lo implicitamente, mas qualquer novo construtor com argumentos tornaria
	 * a desserialização ambígua; o construtor por omissão garante robustez.
	 */
	public PresencaStaffData() {
		super();
	}

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