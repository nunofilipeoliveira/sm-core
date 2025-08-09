package sm.core.data;

import java.util.ArrayList;

public class EquipaData {

	private int id;
	private String epoca;
	private String escalao;
	public void setEscalao(String escalao) {
		this.escalao = escalao;
	}

	private ArrayList<JogadorData> jogadores;
	private ArrayList<StaffData> staff;

	public EquipaData(int id, String epoca, String escalao) {
		super();
		this.id = id;
		this.epoca = epoca;
		this.escalao = escalao;

	}

	public int getId() {
		return id;
	}

	public String getEpoca() {
		return epoca;
	}

	public String getEscalao() {
		return escalao;
	}

	public ArrayList<JogadorData> getJogadores() {
		return jogadores;
	}

	public ArrayList<StaffData> getStaff() {
		return staff;
	}

	public void addJogador(JogadorData jogadorData) {
		if (jogadores == null) {
			jogadores = new ArrayList<JogadorData>();
		}

		jogadores.add(jogadorData);
	}

	public void addStaff(StaffData staffData) {
		if (staff == null) {
			staff = new ArrayList<StaffData>();
		}

		staff.add(staffData);
	}

}
