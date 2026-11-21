package sm.core.data;

import java.util.List;

/**
 * v8 (Y8): corpo de confirmarTodos: os treinos {id, sessao} que o atleta viu (no maximo 30, sem repetidos).
 */
public class PedidoConfirmarTodosData {

	/** Treino a confirmar, com a sessao vista. */
	public static class Item {
		private Integer id;
		private String sessao;

		public Item() {
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getSessao() {
			return sessao;
		}

		public void setSessao(String sessao) {
			this.sessao = sessao;
		}
	}

	private List<Item> itens;

	public PedidoConfirmarTodosData() {
	}

	public List<Item> getItens() {
		return itens;
	}

	public void setItens(List<Item> itens) {
		this.itens = itens;
	}
}
