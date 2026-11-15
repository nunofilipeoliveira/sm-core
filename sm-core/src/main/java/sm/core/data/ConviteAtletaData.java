package sm.core.data;

/** Pedido de convite de um atleta (PUT /sm/convidarAtleta/{parmTenantId}). */
public class ConviteAtletaData {

	private int idJogador;
	private String user;
	private String email;

	public ConviteAtletaData() {
	}

	public int getIdJogador() {
		return idJogador;
	}

	public void setIdJogador(int idJogador) {
		this.idJogador = idJogador;
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

}
