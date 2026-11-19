package sm.core.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resposta de aprovarLote: ids aprovados, falhas por treino [{id, motivo}] e a semana atualizada.
 */
public class ResultadoAprovacaoLoteData {

	private List<Integer> aprovados = new ArrayList<>();
	private List<Map<String, Object>> falhas = new ArrayList<>();
	private SemanaMicroCicloData semana;

	public ResultadoAprovacaoLoteData() {
	}

	public List<Integer> getAprovados() {
		return aprovados;
	}

	public void setAprovados(List<Integer> aprovados) {
		this.aprovados = aprovados;
	}

	public List<Map<String, Object>> getFalhas() {
		return falhas;
	}

	public void setFalhas(List<Map<String, Object>> falhas) {
		this.falhas = falhas;
	}

	public SemanaMicroCicloData getSemana() {
		return semana;
	}

	public void setSemana(SemanaMicroCicloData semana) {
		this.semana = semana;
	}
}
