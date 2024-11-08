package sm.core.data;

public class EscalaoEpocaData {

	private int id_escalao_epoca;
	private String descritivo_escalao;

	public EscalaoEpocaData(int parm_id_escalao_epoca, String parm_descritivo_escalao) {
		this.id_escalao_epoca = parm_id_escalao_epoca;
		this.descritivo_escalao = parm_descritivo_escalao;

	}

	public int getId_escalao_epoca() {
		return id_escalao_epoca;
	}

	public void setId_escalao_epoca(int id_escalao_epoca) {
		this.id_escalao_epoca = id_escalao_epoca;
	}

	public String getDescritivo_escalao() {
		return descritivo_escalao;
	}

	public void setDescritivo_escalao(String descritivo_escalao) {
		this.descritivo_escalao = descritivo_escalao;
	}

}
