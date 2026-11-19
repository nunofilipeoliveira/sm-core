package sm.core.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resposta de copiarTreino: treinos criados (com avisos) e falhas por data [{data, motivo}].
 */
public class ResultadoCopiaTreinoData {

	private List<TreinoData> criados = new ArrayList<>();
	private List<Map<String, Object>> falhas = new ArrayList<>();

	public ResultadoCopiaTreinoData() {
	}

	public List<TreinoData> getCriados() {
		return criados;
	}

	public void setCriados(List<TreinoData> criados) {
		this.criados = criados;
	}

	public List<Map<String, Object>> getFalhas() {
		return falhas;
	}

	public void setFalhas(List<Map<String, Object>> falhas) {
		this.falhas = falhas;
	}
}
