package sm.core.data;

/**
 * Corpo de indicarAusencia. acao: INDICAR, ANULAR ou CONFIRMAR; motivo ate 90 caracteres.
 */
public class PedidoAusenciaData {

	private String acao;
	private String categoria;
	private String motivo;
	private Integer versao;

	public PedidoAusenciaData() {
	}

	public String getAcao() {
		return acao;
	}

	public void setAcao(String acao) {
		this.acao = acao;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public Integer getVersao() {
		return versao;
	}

	public void setVersao(Integer versao) {
		this.versao = versao;
	}
}
