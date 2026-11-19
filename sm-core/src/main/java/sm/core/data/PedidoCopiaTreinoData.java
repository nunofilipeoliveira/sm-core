package sm.core.data;

import java.util.List;

/**
 * Corpo de copiarTreino: 1 a 28 datas yyyymmdd e manter os ajustes da convocatoria (retirados e acrescentados).
 */
public class PedidoCopiaTreinoData {

	private List<Integer> datas;
	private boolean manterAjustes;

	public PedidoCopiaTreinoData() {
	}

	public List<Integer> getDatas() {
		return datas;
	}

	public void setDatas(List<Integer> datas) {
		this.datas = datas;
	}

	public boolean isManterAjustes() {
		return manterAjustes;
	}

	public void setManterAjustes(boolean manterAjustes) {
		this.manterAjustes = manterAjustes;
	}
}
