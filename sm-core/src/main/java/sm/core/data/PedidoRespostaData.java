package sm.core.data;

/**
 * v8 (Y6): corpo de responderTreino. acao VOU (com a sessao que o atleta viu) ou ANULAR.
 */
public class PedidoRespostaData {

	private String acao;
	private String sessao;

	public PedidoRespostaData() {
	}

	public String getAcao() {
		return acao;
	}

	public void setAcao(String acao) {
		this.acao = acao;
	}

	public String getSessao() {
		return sessao;
	}

	public void setSessao(String sessao) {
		this.sessao = sessao;
	}
}
