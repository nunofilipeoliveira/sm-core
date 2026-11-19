package sm.core.data;

import java.sql.Timestamp;

/**
 * Notificacao na aplicacao. motivo so para ausencias ainda ativas (D8); semanaTreino = segunda-feira, para o link.
 */
public class NotificacaoData {

	private int id;
	private String tipo;
	private String texto;
	private Integer idTreino;
	private Integer semanaTreino;
	private String motivo;
	private Timestamp dataCriacao;
	private boolean lida;

	public NotificacaoData() {
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getTipo() {
		return tipo;
	}

	public void setTipo(String tipo) {
		this.tipo = tipo;
	}

	public String getTexto() {
		return texto;
	}

	public void setTexto(String texto) {
		this.texto = texto;
	}

	public Integer getIdTreino() {
		return idTreino;
	}

	public void setIdTreino(Integer idTreino) {
		this.idTreino = idTreino;
	}

	public Integer getSemanaTreino() {
		return semanaTreino;
	}

	public void setSemanaTreino(Integer semanaTreino) {
		this.semanaTreino = semanaTreino;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public Timestamp getDataCriacao() {
		return dataCriacao;
	}

	public void setDataCriacao(Timestamp dataCriacao) {
		this.dataCriacao = dataCriacao;
	}

	public boolean isLida() {
		return lida;
	}

	public void setLida(boolean lida) {
		this.lida = lida;
	}
}
