package sm.core.data;

import java.util.List;

/**
 * Corpo de gravarBalnearios: exatamente um de idTreino/idJogo. As listas ...Antes sao o que o utilizador viu
 * (compare-and-set, B4; null = vazia). No jogo, um lado com a lista nova null nao muda.
 */
public class PedidoBalneariosData {

	private Integer idTreino;
	private List<Integer> ids;
	private List<Integer> idsAntes;
	private Integer idJogo;
	private List<Integer> idsEquipa;
	private List<Integer> idsEquipaAntes;
	private List<Integer> idsAdversario;
	private List<Integer> idsAdversarioAntes;

	public PedidoBalneariosData() {
	}

	public Integer getIdTreino() {
		return idTreino;
	}

	public void setIdTreino(Integer idTreino) {
		this.idTreino = idTreino;
	}

	public List<Integer> getIds() {
		return ids;
	}

	public void setIds(List<Integer> ids) {
		this.ids = ids;
	}

	public List<Integer> getIdsAntes() {
		return idsAntes;
	}

	public void setIdsAntes(List<Integer> idsAntes) {
		this.idsAntes = idsAntes;
	}

	public Integer getIdJogo() {
		return idJogo;
	}

	public void setIdJogo(Integer idJogo) {
		this.idJogo = idJogo;
	}

	public List<Integer> getIdsEquipa() {
		return idsEquipa;
	}

	public void setIdsEquipa(List<Integer> idsEquipa) {
		this.idsEquipa = idsEquipa;
	}

	public List<Integer> getIdsEquipaAntes() {
		return idsEquipaAntes;
	}

	public void setIdsEquipaAntes(List<Integer> idsEquipaAntes) {
		this.idsEquipaAntes = idsEquipaAntes;
	}

	public List<Integer> getIdsAdversario() {
		return idsAdversario;
	}

	public void setIdsAdversario(List<Integer> idsAdversario) {
		this.idsAdversario = idsAdversario;
	}

	public List<Integer> getIdsAdversarioAntes() {
		return idsAdversarioAntes;
	}

	public void setIdsAdversarioAntes(List<Integer> idsAdversarioAntes) {
		this.idsAdversarioAntes = idsAdversarioAntes;
	}
}
