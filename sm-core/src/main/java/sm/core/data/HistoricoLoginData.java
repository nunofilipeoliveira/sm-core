package sm.core.data;

public class HistoricoLoginData {

	private String nome;
	private String dataHistorico;
	private String tenant_name;

	public HistoricoLoginData(String nome, java.sql.Timestamp timestamp, String tenant_name) {
		super();
		this.nome = nome;
		this.dataHistorico = timestamp.toString();
		this.tenant_name = tenant_name;

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

	public String getTenant_name() {
		return tenant_name;
	}

	public void setTenant_name(String tenant_name) {
		this.tenant_name = tenant_name;
	}

}
