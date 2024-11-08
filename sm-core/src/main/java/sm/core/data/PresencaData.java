package sm.core.data;

import java.util.ArrayList;

public class PresencaData {

	public PresencaData(int id, int data, String hora, int id_escalao, String escalao_descricao, String data_criacao,
			int id_utilizador_criacao, String user_criacao) {
		super();
		this.id = id;
		this.data = data;
		this.hora = hora;
		this.id_escalao = id_escalao;
		this.escalao_descricao = escalao_descricao;
		this.data_criacao = data_criacao;
		this.id_utilizador_criacao = id_utilizador_criacao;
		this.user_criacao = user_criacao;

	}

	private int id;
	private int data;
	private String hora;
	private int id_escalao;
	private String escalao_descricao;
	private String data_criacao;
	private int id_utilizador_criacao;

	public ArrayList<PresencaJogadorData> getJogadoresPresenca() {
		return jogadoresPresenca;
	}

	public void setJogadoresPresenca(ArrayList<PresencaJogadorData> jogadoresPresenca) {
		this.jogadoresPresenca = jogadoresPresenca;
	}

	private String user_criacao;
	private ArrayList<PresencaJogadorData> jogadoresPresenca;
	private ArrayList<PresencaStaffData> staffPresenca;

	public ArrayList<PresencaStaffData> getStaffPresenca() {
		return staffPresenca;
	}

	public void setStaffPresenca(ArrayList<PresencaStaffData> staffPresenca) {
		this.staffPresenca = staffPresenca;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getData() {
		return data;
	}

	public void setData(int data) {
		this.data = data;
	}

	public String getHora() {
		return hora;
	}

	public void setHora(String hora) {
		this.hora = hora;
	}

	public int getId_escalao() {
		return id_escalao;
	}

	public void setId_escalao(int id_escalao) {
		this.id_escalao = id_escalao;
	}

	public String getEscalao_descricao() {
		return escalao_descricao;
	}

	public void setEscalao_descricao(String escalao_descricao) {
		this.escalao_descricao = escalao_descricao;
	}

	public String getData_criacao() {
		return data_criacao;
	}

	public void setData_criacao(String data_criacao) {
		this.data_criacao = data_criacao;
	}

	public int getId_utilizador_criacao() {
		return id_utilizador_criacao;
	}

	public void setId_utilizador_criacao(int id_utilizador_criacao) {
		this.id_utilizador_criacao = id_utilizador_criacao;
	}

	public String getUser_criacao() {
		return user_criacao;
	}

	public void setUser_criacao(String user_criacao) {
		this.user_criacao = user_criacao;
	}

	public void addJogador(int idjogador, String nome, String estado, String motivo) {
		if (this.jogadoresPresenca == null) {
			this.jogadoresPresenca = new ArrayList<PresencaJogadorData>();
		}

		this.jogadoresPresenca.add(new PresencaJogadorData(idjogador, nome, estado, motivo));
	}

	public void addStaff(int idstaff, String nome, String estado, String motivo) {
		if (this.staffPresenca == null) {
			this.staffPresenca = new ArrayList<PresencaStaffData>();
		}

		this.staffPresenca.add(new PresencaStaffData(idstaff, nome, estado, motivo));
	}

}
