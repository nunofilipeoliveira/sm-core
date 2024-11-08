package sm.core.data;

public class UtilizadorParaAtivarData {

	private String user;
	private String estado;
	private String code;
	private String IDsEscalao;
	private String nome;
	public UtilizadorParaAtivarData(String user, String estado, String code, String iDsEscalao, String nome) {
		super();
		this.user = user;
		this.estado = estado;
		this.code = code;
		IDsEscalao = iDsEscalao;
		this.nome=nome;
	}
	public String getUser() {
		return user;
	}
	public void setUser(String user) {
		this.user = user;
	}
	public String getEstado() {
		return estado;
	}
	public void setEstado(String estado) {
		this.estado = estado;
	}
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public String getIDsEscalao() {
		return IDsEscalao;
	}
	public void setIDsEscalao(String iDsEscalao) {
		IDsEscalao = iDsEscalao;
	}
	public String getNome() {
		return nome;
	}
	public void setNome(String nome) {
		this.nome = nome;
	}
	
	
	
}
