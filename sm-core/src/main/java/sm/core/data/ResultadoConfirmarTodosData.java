package sm.core.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v8 (Y8): resultado de confirmarTodos: os confirmados, as falhas [{id, motivo}] e os treinos do atleta das semanas
 * dos itens (so dados do proprio atleta).
 */
public class ResultadoConfirmarTodosData {

	private List<Integer> confirmados = new ArrayList<>();
	private List<Map<String, Object>> falhas = new ArrayList<>();
	private List<TreinoAtletaData> treinos = new ArrayList<>();

	public ResultadoConfirmarTodosData() {
	}

	public List<Integer> getConfirmados() {
		return confirmados;
	}

	public void setConfirmados(List<Integer> confirmados) {
		this.confirmados = confirmados;
	}

	public List<Map<String, Object>> getFalhas() {
		return falhas;
	}

	public void setFalhas(List<Map<String, Object>> falhas) {
		this.falhas = falhas;
	}

	public List<TreinoAtletaData> getTreinos() {
		return treinos;
	}

	public void setTreinos(List<TreinoAtletaData> treinos) {
		this.treinos = treinos;
	}
}
