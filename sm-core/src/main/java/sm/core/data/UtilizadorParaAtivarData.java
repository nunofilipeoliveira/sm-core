package sm.core.data;

public class UtilizadorParaAtivarData {

	private String user;
	private String estado;
	private String idsescalao;
	private String nome;
	private String email;
	private String perfil;
	private String code;
	private Integer id_jogador;
	private int tenant_id;

	
	public UtilizadorParaAtivarData(String user, String estado, String iDsEscalao, String nome, String email,
			String perfil, String code) {
		super();
		this.user = user;
		this.estado = estado;
		idsescalao = iDsEscalao;
		this.nome = nome;
		this.email = email;
		this.perfil = perfil;
		this.code = code;
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
	public String getIdsescalao() {
		return idsescalao;
	}
	public void setIdsescalao(String idsescalao) {
		this.idsescalao = idsescalao;
	}
	public String getNome() {
		return nome;
	}
	public void setNome(String nome) {
		this.nome = nome;
	}
	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public String getPerfil() {
		return perfil;
	}
	public void setPerfil(String perfil) {
		this.perfil = perfil;
	}
	public String getCode() {
		return code;
	}
	public void setCode(String code) {
		this.code = code;
	}
	public Integer getId_jogador() {
		return id_jogador;
	}
	public void setId_jogador(Integer id_jogador) {
		this.id_jogador = id_jogador;
	}
	public int getTenant_id() {
		return tenant_id;
	}
	public void setTenant_id(int tenant_id) {
		this.tenant_id = tenant_id;
	}
	
	
	
	
	
	
	
	
}
