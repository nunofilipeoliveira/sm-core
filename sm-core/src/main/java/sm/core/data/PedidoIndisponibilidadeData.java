package sm.core.data;

/**
 * Corpo de gravarIndisponibilidade e cancelarIndisponibilidade. id = 0 para criar; datas yyyymmdd (inclusivas);
 * categoria: DOENCA, LESAO, ESCOLA, FAMILIA ou OUTRO; motivo ate 90 caracteres; simular = so devolve as contagens.
 */
public class PedidoIndisponibilidadeData {

	private Integer id;
	private Integer dataInicio;
	private Integer dataFim;
	private String categoria;
	private String motivo;
	private Boolean simular;

	public PedidoIndisponibilidadeData() {
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getDataInicio() {
		return dataInicio;
	}

	public void setDataInicio(Integer dataInicio) {
		this.dataInicio = dataInicio;
	}

	public Integer getDataFim() {
		return dataFim;
	}

	public void setDataFim(Integer dataFim) {
		this.dataFim = dataFim;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public Boolean getSimular() {
		return simular;
	}

	public void setSimular(Boolean simular) {
		this.simular = simular;
	}
}
