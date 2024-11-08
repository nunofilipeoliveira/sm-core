package sm.core.data;

public class JogadorData {

	public void setId(int id) {
		this.id = id;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public void setNome_completo(String nome_completo) {
		this.nome_completo = nome_completo;
	}

	public void setData_nascimento(int data_nascimento) {
		this.data_nascimento = data_nascimento;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public void setTelemovel(String telemovel) {
		this.telemovel = telemovel;
	}

	public void setPai_nome(String pai_nome) {
		this.pai_nome = pai_nome;
	}

	public void setPai_email(String pai_email) {
		this.pai_email = pai_email;
	}

	public void setPai_telemovel(String pai_telemovel) {
		this.pai_telemovel = pai_telemovel;
	}

	public void setMae_nome(String mae_nome) {
		this.mae_nome = mae_nome;
	}

	public void setMae_email(String mae_email) {
		this.mae_email = mae_email;
	}

	public void setMae_telemovel(String mae_telemovel) {
		this.mae_telemovel = mae_telemovel;
	}

	public void setMorada(String morada) {
		this.morada = morada;
	}

	public void setCidade(String cidade) {
		this.cidade = cidade;
	}

	public void setCodigo_postal(String codigo_postal) {
		this.codigo_postal = codigo_postal;
	}

	public void setObservacoes(String observacoes) {
		this.observacoes = observacoes;
	}

	public void setNumero(String numero) {
		this.numero = numero;
	}

	public JogadorData(int id, String nome, String nome_completo, int data_nascimento, String email, String telemovel,
			String pai_nome, String pai_email, String pai_telemovel, String mae_nome, String mae_email,
			String mae_telemovel, String morada, String cidade, String codigo_postal, String observacoes, String numero,
			String cc, int nif, int licenca) {
		this.nome = "";
		this.nome_completo = "";
		this.data_nascimento = 0;
		this.email = "";
		this.telemovel = "";
		this.pai_nome = "";
		this.pai_email = "";
		this.pai_telemovel = "";
		this.mae_nome = "";
		this.mae_email = "";
		this.mae_telemovel = "";
		this.morada = "";
		this.cidade = "";
		this.codigo_postal = "";
		this.observacoes = "";
		this.numero = "";
		this.CC = "";
		this.NIF = 0;
		this.licenca = 0;
		this.id = id;
		if (nome != null)
			this.nome = nome;
		if (nome_completo != null)
			this.nome_completo = nome_completo;
		if (data_nascimento != 0)
			this.data_nascimento = data_nascimento;
		if (email != null)
			this.email = email;
		if (telemovel != null)
			this.telemovel = telemovel;
		if (pai_nome != null)
			this.pai_nome = pai_nome;
		if (pai_email != null)
			this.pai_email = pai_email;
		if (pai_telemovel != null)
			this.pai_telemovel = pai_telemovel;
		if (mae_nome != null)
			this.mae_nome = mae_nome;
		if (mae_email != null)
			this.mae_email = mae_email;
		if (mae_telemovel != null)
			this.mae_telemovel = mae_telemovel;
		if (morada != null)
			this.morada = morada;
		if (cidade != null)
			this.cidade = cidade;
		if (codigo_postal != null)
			this.codigo_postal = codigo_postal;
		if (observacoes != null)
			this.observacoes = observacoes;
		if (numero != null)
			this.numero = numero;
		if (cc != null)
			this.CC = cc;
		this.NIF = nif;
		this.licenca = licenca;
	}

	public JogadorData() {
		this.nome = "";
		this.nome_completo = "";
		this.data_nascimento = 0;
		this.email = "";
		this.telemovel = "";
		this.pai_nome = "";
		this.pai_email = "";
		this.pai_telemovel = "";
		this.mae_nome = "";
		this.mae_email = "";
		this.mae_telemovel = "";
		this.morada = "";
		this.cidade = "";
		this.codigo_postal = "";
		this.observacoes = "";
		this.numero = "";
		this.CC = "";
		this.NIF = 0;
		this.licenca = 0;
	}

	private int id;

	public int getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getNome_completo() {
		return nome_completo;
	}

	public int getData_nascimento() {
		return data_nascimento;
	}

	public String getEmail() {
		return email;
	}

	public String getTelemovel() {
		return telemovel;
	}

	public String getPai_nome() {
		return pai_nome;
	}

	public String getPai_email() {
		return pai_email;
	}

	public String getPai_telemovel() {
		return pai_telemovel;
	}

	public String getMae_nome() {
		return mae_nome;
	}

	public String getMae_email() {
		return mae_email;
	}

	public String getMae_telemovel() {
		return mae_telemovel;
	}

	public String getMorada() {
		return morada;
	}

	public String getCidade() {
		return cidade;
	}

	public String getCodigo_postal() {
		return codigo_postal;
	}

	public String getObservacoes() {
		return observacoes;
	}

	private String nome;
	private String nome_completo;
	private int data_nascimento;
	private String email;
	private String telemovel;
	private String pai_nome;
	private String pai_email;
	private String pai_telemovel;
	private String mae_nome;
	private String mae_email;
	private String mae_telemovel;
	private String morada;
	private String cidade;
	private String codigo_postal;
	private String observacoes;
	private String numero;
	private int NIF;
	private String CC;
	private int licenca;

	public int getNIF() {
		return NIF;
	}

	public void setNIF(int nIF) {
		NIF = nIF;
	}

	public String getCC() {
		return CC;
	}

	public void setCC(String cC) {
		CC = cC;
	}

	public int getLicenca() {
		return licenca;
	}

	public void setLicenca(int licenca) {
		this.licenca = licenca;
	}

	public String getNumero() {
		return numero;
	}

}
