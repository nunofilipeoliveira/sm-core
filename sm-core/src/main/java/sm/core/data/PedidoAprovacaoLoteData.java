package sm.core.data;

import java.util.List;

/**
 * Corpo de aprovarLote: a lista {id, versao} que o utilizador viu na confirmacao. ambito (SEMANA,
 * EQUIPA:&lt;id&gt; ou MEUS) so serve para o texto do historico: o servidor aprova os itens, nao o ambito.
 */
public class PedidoAprovacaoLoteData {

	/** Treino a aprovar, com a versao vista. */
	public static class Item {
		private Integer id;
		private Integer versao;

		public Item() {
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Integer getVersao() {
			return versao;
		}

		public void setVersao(Integer versao) {
			this.versao = versao;
		}
	}

	private String ambito;
	private List<Item> itens;

	public PedidoAprovacaoLoteData() {
	}

	public String getAmbito() {
		return ambito;
	}

	public void setAmbito(String ambito) {
		this.ambito = ambito;
	}

	public List<Item> getItens() {
		return itens;
	}

	public void setItens(List<Item> itens) {
		this.itens = itens;
	}
}
