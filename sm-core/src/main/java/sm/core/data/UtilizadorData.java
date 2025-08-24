package sm.core.data;

public class UtilizadorData {

	private int id;
	private String nome;
	private String user;
	private String perfil;
	private String email;
	private String estado;

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public UtilizadorData(int id, String nome, String user, String perfil, String email, String estado) {
		super();
		this.id = id;
		this.nome = nome;
		this.user = user;
		this.perfil = perfil;
		this.email = email;
		this.estado = estado;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getPerfil() {
		return perfil;
	}

	public void setPerfil(String perfil) {
		this.perfil = perfil;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

}
