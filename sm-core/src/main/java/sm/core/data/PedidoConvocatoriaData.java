package sm.core.data;

import java.util.List;

/**
 * Corpo de ajustarConvocatoria: ids de jogadores a adicionar e a retirar, e repor a convocatoria por omissao.
 */
public class PedidoConvocatoriaData {

	private List<Integer> adicionar;
	private List<Integer> retirar;
	private boolean repor;
	/** v7 (X3): participantes dos fisicos do principal; null (ou sem o campo) = nao mexer. */
	private List<Fisico> fisicos;

	/** v7: excluidos substitui o conjunto de quem nao faz o fisico idFisico. */
	public static class Fisico {
		private Integer idFisico;
		private List<Integer> excluidos;

		public Fisico() {
		}

		public Integer getIdFisico() {
			return idFisico;
		}

		public void setIdFisico(Integer idFisico) {
			this.idFisico = idFisico;
		}

		public List<Integer> getExcluidos() {
			return excluidos;
		}

		public void setExcluidos(List<Integer> excluidos) {
			this.excluidos = excluidos;
		}
	}

	public PedidoConvocatoriaData() {
	}

	public List<Fisico> getFisicos() {
		return fisicos;
	}

	public void setFisicos(List<Fisico> fisicos) {
		this.fisicos = fisicos;
	}

	public List<Integer> getAdicionar() {
		return adicionar;
	}

	public void setAdicionar(List<Integer> adicionar) {
		this.adicionar = adicionar;
	}

	public List<Integer> getRetirar() {
		return retirar;
	}

	public void setRetirar(List<Integer> retirar) {
		this.retirar = retirar;
	}

	public boolean isRepor() {
		return repor;
	}

	public void setRepor(boolean repor) {
		this.repor = repor;
	}
}
