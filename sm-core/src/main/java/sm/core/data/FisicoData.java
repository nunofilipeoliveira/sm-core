package sm.core.data;

/**
 * v6: treino fisico ligado a um treino principal (treino.id_treino_principal). Sem observacoes, estado, convocatoria nem
 * versao (sao os do principal). posicao (ANTES, DURANTE, DEPOIS) so na resposta.
 */
public class FisicoData {

	private int id;
	private String horaInicio;
	private String horaFim;
	private String local;
	private String subGrupo;
	private String posicao;

	public FisicoData() {
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getHoraInicio() {
		return horaInicio;
	}

	public void setHoraInicio(String horaInicio) {
		this.horaInicio = horaInicio;
	}

	public String getHoraFim() {
		return horaFim;
	}

	public void setHoraFim(String horaFim) {
		this.horaFim = horaFim;
	}

	public String getLocal() {
		return local;
	}

	public void setLocal(String local) {
		this.local = local;
	}

	public String getSubGrupo() {
		return subGrupo;
	}

	public void setSubGrupo(String subGrupo) {
		this.subGrupo = subGrupo;
	}

	public String getPosicao() {
		return posicao;
	}

	public void setPosicao(String posicao) {
		this.posicao = posicao;
	}
}
