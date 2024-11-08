package sm.core.data;

public class PresencaJogadorData {

	private int id_jogador;
	private String nome_jogador;
	private String estado;
	private String motivo;

	public PresencaJogadorData(int id_jogador, String nome_jogador, String estado, String motivo) {
		super();
		this.id_jogador = id_jogador;
		this.nome_jogador = nome_jogador;
		this.estado = estado;
		this.motivo = motivo;
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
}