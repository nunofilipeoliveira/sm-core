package sm.core.data;

/**
 * Corpo de mudarEstadoTreino. acao: APROVAR, REJEITAR, SUBMETER ou CANCELAR.
 */
public class PedidoEstadoTreinoData {

	private String acao;
	private String motivo;
	private Integer versao;

	public PedidoEstadoTreinoData() {
	}

	public String getAcao() {
		return acao;
	}

	public void setAcao(String acao) {
		this.acao = acao;
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
