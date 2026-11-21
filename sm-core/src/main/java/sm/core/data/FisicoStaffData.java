package sm.core.data;

import java.util.List;

/**
 * v7 (X5): fisico ligado visto pelo staff (TreinoData.fisicos). excluidos e podeAjustar so no getDetalhe (null nas listas).
 */
public class FisicoStaffData extends FisicoData {

	/** Convocados efetivos do principal menos os excluidos deste fisico. */
	private int numParticipantes;
	/** Participantes com o "Nao vou" da sessao ativo. */
	private int numAusencias;
	/** So no detalhe: quem nao faz este fisico (ids de jogador, todos convocados efetivos do principal). */
	private List<Integer> excluidos;
	/** So no detalhe: o utilizador ainda pode mudar os participantes (ate ao inicio deste fisico). */
	private Boolean podeAjustar;

	public FisicoStaffData() {
	}

	public int getNumParticipantes() {
		return numParticipantes;
	}

	public void setNumParticipantes(int numParticipantes) {
		this.numParticipantes = numParticipantes;
	}

	public int getNumAusencias() {
		return numAusencias;
	}

	public void setNumAusencias(int numAusencias) {
		this.numAusencias = numAusencias;
	}

	public List<Integer> getExcluidos() {
		return excluidos;
	}

	public void setExcluidos(List<Integer> excluidos) {
		this.excluidos = excluidos;
	}

	public Boolean getPodeAjustar() {
		return podeAjustar;
	}

	public void setPodeAjustar(Boolean podeAjustar) {
		this.podeAjustar = podeAjustar;
	}
}
