package sm.core.data;

import java.util.List;

/**
 * v9 (Z1, Z2): corpo opcional de publicarMicroCiclo. Com aprovarPendentes, itens = os pendentes {id, versao} que o ADMIN
 * viu; aprovam-se e publica-se na mesma transacao (tudo ou nada).
 */
public class PedidoPublicarData {

	private boolean aprovarPendentes;
	private List<PedidoAprovacaoLoteData.Item> itens;

	public PedidoPublicarData() {
	}

	public boolean isAprovarPendentes() {
		return aprovarPendentes;
	}

	public void setAprovarPendentes(boolean aprovarPendentes) {
		this.aprovarPendentes = aprovarPendentes;
	}

	public List<PedidoAprovacaoLoteData.Item> getItens() {
		return itens;
	}

	public void setItens(List<PedidoAprovacaoLoteData.Item> itens) {
		this.itens = itens;
	}
}
