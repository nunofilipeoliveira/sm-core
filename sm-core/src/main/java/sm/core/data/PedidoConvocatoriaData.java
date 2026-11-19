package sm.core.data;

import java.util.List;

/**
 * Corpo de ajustarConvocatoria: ids de jogadores a adicionar e a retirar, e repor a convocatoria por omissao.
 */
public class PedidoConvocatoriaData {

	private List<Integer> adicionar;
	private List<Integer> retirar;
	private boolean repor;

	public PedidoConvocatoriaData() {
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
