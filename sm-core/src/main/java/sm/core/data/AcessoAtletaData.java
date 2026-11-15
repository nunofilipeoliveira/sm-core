package sm.core.data;

/**
 * Estado do acesso de um jogador (PUT /sm/getAcessoAtleta/{idJogador}/{parmTenantId}).
 * estado: SEM_CONTA, PENDENTE, ATIVA, DESATIVADA ou AGUARDA_PASSWORD.
 * O code so e preenchido em PENDENTE.
 */
public class AcessoAtletaData {

	private String estado;
	private Integer idUtilizador;
	private String user;
	private String email;
	private String code;
	private Integer idJogador;

	public AcessoAtletaData() {
	}

	public AcessoAtletaData(String estado, Integer idUtilizador, String user, String email, String code) {
		this.estado = estado;
		this.idUtilizador = idUtilizador;
		this.user = user;
		this.email = email;
		this.code = code;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public Integer getIdUtilizador() {
		return idUtilizador;
	}

	public void setIdUtilizador(Integer idUtilizador) {
		this.idUtilizador = idUtilizador;
	}

	public String getUser() {
		return user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public Integer getIdJogador() {
		return idJogador;
	}

	public void setIdJogador(Integer idJogador) {
		this.idJogador = idJogador;
	}

}
