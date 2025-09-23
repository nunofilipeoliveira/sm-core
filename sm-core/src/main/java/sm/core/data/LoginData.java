package sm.core.data;

import java.util.ArrayList;

public class LoginData {

	private int id;
	private String nome;
	private String user;
	private String password;
	private ArrayList<EscalaoEpocaData> escalaoEpoca;
	private String token;
	private String perfil;

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
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

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setEscalaoEpoca(ArrayList<EscalaoEpocaData> escalaoEpoca) {
		this.escalaoEpoca = escalaoEpoca;
	}

	public String getPerfil() {
		return perfil;
	}
	public void setPerfil(String perfil) {
		this.perfil = perfil;
	}

	public LoginData(int id, String nome, String user, String password, String token, String perfil) {

		this.id = id;
		this.nome = nome;
		this.user = user;
		this.password = password;
		this.escalaoEpoca = new ArrayList<EscalaoEpocaData>();
		this.token=token;
		this.perfil=perfil;
	}

	public void adicionarEscalaoEpoca(int idEscalaoEpoca, String descritivoEscalao) {

		EscalaoEpocaData tmp = new EscalaoEpocaData(idEscalaoEpoca, descritivoEscalao);

		this.escalaoEpoca.add(tmp);

	}

	public ArrayList<EscalaoEpocaData> getEscalaoEpoca() {
		return escalaoEpoca;
	}

}
