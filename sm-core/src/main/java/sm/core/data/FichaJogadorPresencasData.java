package sm.core.data;

public class FichaJogadorPresencasData {

	private int id_jogador;
	private String nome_jogador;
	private int id_equipa;
	private String nome_escalao;
	private String estado;
	private String motivo;
	private int data;
	private String hora;
	public FichaJogadorPresencasData(int id_jogador, String nome_jogador, int id_equipa, String nome_escalao,
			String estado, String motivo, int data, String hora) {
		super();
		this.id_jogador = id_jogador;
		this.nome_jogador = nome_jogador;
		this.id_equipa = id_equipa;
		this.nome_escalao = nome_escalao;
		this.estado = estado;
		this.motivo = motivo;
		this.data = data;
		this.hora = hora;
	}
	public int getId_jogador() {
		return id_jogador;
	}
	public void setId_jogador(int id_jogador) {
		this.id_jogador = id_jogador;
	}
	public String getNome_jogador() {
		return nome_jogador;
	}
	public void setNome_jogador(String nome_jogador) {
		this.nome_jogador = nome_jogador;
	}
	public int getId_equipa() {
		return id_equipa;
	}
	public void setId_equipa(int id_equipa) {
		this.id_equipa = id_equipa;
	}
	public String getNome_escalao() {
		return nome_escalao;
	}
	public void setNome_escalao(String nome_escalao) {
		this.nome_escalao = nome_escalao;
	}
	public String getEstado() {
		return estado;
	}
	public void setEstado(String estado) {
		this.estado = estado;
	}
	public String getMotivo() {
		return motivo;
	}
	public void setMotivo(String motivo) {
		this.motivo = motivo;
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
	
	
	
}
