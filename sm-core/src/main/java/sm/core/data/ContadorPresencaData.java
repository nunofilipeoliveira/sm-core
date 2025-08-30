package sm.core.data;

public class ContadorPresencaData {

	private int id_jogador;
	private String escalao;
	private int set;
	private int out;
	private int nov;
	private int dez;
	private int jan;
	private int fev;
	private int mar;
	private int abr;
	private int mai;
	private int jun;
	private int jul;
	private int ago;



	public ContadorPresencaData(int id_jogador, String escalao, int set, int out, int nov, int dez, int jan, int fev,
			int mar, int abr, int mai, int jun, int jul, int ago) {
		super();
		this.id_jogador = id_jogador;
		this.escalao = escalao;
		this.set = set;
		this.out = out;
		this.nov = nov;
		this.dez = dez;
		this.jan = jan;
		this.fev = fev;
		this.mar = mar;
		this.abr = abr;
		this.mai = mai;
		this.jun = jun;
		this.jul = jul;
	}

	public int getId_jogador() {
		return id_jogador;
	}

	public void setId_jogador(int id_jogador) {
		this.id_jogador = id_jogador;
	}

	public String getEscalao() {
		return escalao;
	}

	public void setEscalao(String escalao) {
		this.escalao = escalao;
	}

	public int getSet() {
		return set;
	}

	public void setSet(int set) {
		this.set = set;
	}

	public int getOut() {
		return out;
	}

	public void setOut(int out) {
		this.out = out;
	}

	public int getNov() {
		return nov;
	}

	public void setNov(int nov) {
		this.nov = nov;
	}

	public int getDez() {
		return dez;
	}

	public void setDez(int dez) {
		this.dez = dez;
	}

	public int getJan() {
		return jan;
	}

	public void setJan(int jan) {
		this.jan = jan;
	}

	public int getFev() {
		return fev;
	}

	public void setFev(int fev) {
		this.fev = fev;
	}

	public int getMar() {
		return mar;
	}

	public void setMar(int mar) {
		this.mar = mar;
	}

	public int getAbr() {
		return abr;
	}

	public void setAbr(int abr) {
		this.abr = abr;
	}

	public int getMai() {
		return mai;
	}

	public void setMai(int mai) {
		this.mai = mai;
	}

	public int getJun() {
		return jun;
	}

	public void setJun(int jun) {
		this.jun = jun;
	}

	public int getJul() {
		return jul;
	}

	public void setJul(int jul) {
		this.jul = jul;
	}

		public int getAgo() {
		return ago;
	}

	public void setAgo(int ago) {
		this.ago = ago;
	}

	

}
