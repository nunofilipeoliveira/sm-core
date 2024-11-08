package sm.core.data;

public class HistoricoLoginData {

	private String nome;
	private String dataHistorico;

	public HistoricoLoginData(String nome, java.sql.Timestamp timestamp) {
		super();
		this.nome = nome;
		this.dataHistorico = timestamp.toString();

	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getDataHistorico() {
		return dataHistorico;
	}

	public void setDataHistorico(String dataHistorico) {
		this.dataHistorico = dataHistorico;
	}

}
