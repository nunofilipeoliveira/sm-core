package sm.core.data;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginData {

	private int id;
	private String nome;
	private String user;
	// so de escrita: vem no pedido de ativacao, nunca vai nas respostas (R19)
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String password;
	private ArrayList<EscalaoEpocaData> escalaoEpoca = new ArrayList<EscalaoEpocaData>();
	private String token;
	private String perfil;
	private Integer idJogador;
	// codigo de ativacao, so no pedido de createuser
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String code;

	public LoginData() {
	}

	public Integer getIdJogador() {
		return idJogador;
	}

	public void setIdJogador(Integer idJogador) {
		this.idJogador = idJogador;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

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
