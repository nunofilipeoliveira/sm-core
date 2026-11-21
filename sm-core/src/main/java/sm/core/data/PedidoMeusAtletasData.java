package sm.core.data;

import java.util.List;

/**
 * v7 (X8): corpo de getMeusAtletas. equipas vazia ou null = as equipas do utilizador.
 */
public class PedidoMeusAtletasData {

	private List<Integer> equipas;

	public PedidoMeusAtletasData() {
	}

	public List<Integer> getEquipas() {
		return equipas;
	}

	public void setEquipas(List<Integer> equipas) {
		this.equipas = equipas;
	}
}
