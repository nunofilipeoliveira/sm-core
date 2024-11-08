package sm.core.data;

public class StaffData {

	private int id;
	private String nome;
	private String nome_completo;
	private String telemovel;
	private String email;
	private String morada;
	private String codigo_postal;
	private int data_nascimento;
	private int id_jogador;
	private String tipo;

	public StaffData(int id, String nome, String nome_completo, String telemovel, String email, String morada,
			String codigo_postal, int data_nascimento, int id_jogador, String tipo) {
		super();
		this.id = id;
		this.nome = nome;
		this.nome_completo = nome_completo;
		this.telemovel = telemovel;
		this.email = email;
		this.morada = morada;
		this.codigo_postal = codigo_postal;
		this.data_nascimento = data_nascimento;
		this.id_jogador = id_jogador;
		this.tipo = tipo;
	}

	public StaffData(int id, String nome, String nome_completo, String telemovel, String email, String morada,
			String codigo_postal, int data_nascimento) {
		super();
		this.id = id;
		this.nome = nome;
		this.nome_completo = nome_completo;
		this.telemovel = telemovel;
		this.email = email;
		this.morada = morada;
		this.codigo_postal = codigo_postal;
		this.data_nascimento = data_nascimento;

	}

	public StaffData(int id, String nome, String tipo, int id_jogador) {
		super();
		this.id = id;
		this.nome = nome;
		this.tipo = tipo;
		this.id_jogador = id_jogador;
	}

	public StaffData() {

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

	public String getNome_completo() {
		return nome_completo;
	}

	public void setNome_completo(String nome_completo) {
		this.nome_completo = nome_completo;
	}

	public String getTelemovel() {
		return telemovel;
	}

	public void setTelemovel(String telemovel) {
		this.telemovel = telemovel;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getMorada() {
		return morada;
	}

	public void setMorada(String morada) {
		this.morada = morada;
	}

	public String getCodigo_postal() {
		return codigo_postal;
	}

	public void setCodigo_postal(String codigo_postal) {
		this.codigo_postal = codigo_postal;
	}

	public int getData_nascimento() {
		return data_nascimento;
	}

	public void setData_nascimento(int data_nascimento) {
		this.data_nascimento = data_nascimento;
	}

	public int getId_jogador() {
		return id_jogador;
	}

	public void setId_jogador(int id_jogador) {
		this.id_jogador = id_jogador;
	}

	public String getTipo() {
		return tipo;
	}

	public void setTipo(String tipo) {
		this.tipo = tipo;
	}

}
