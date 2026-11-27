package sm.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Indisponibilidade de um atleta. estado: ATIVA ou CANCELADA; situacao (derivada): AGENDADA, EM_CURSO, TERMINADA ou
 * CANCELADA. treinos/jogos = linhas ATIVA e ligacoes AUTOMATICA desta indisponibilidade; convocadoEm = datas (yyyymmdd)
 * dos jogos abrangidos em que o atleta esta CONVOCADO. semTreinador e simulacao so vem nas respostas de escrita.
 */
public class IndisponibilidadeData {

	private int id;
	private int dataInicio;
	private int dataFim;
	private String categoria;
	private String motivo;
	private String estado;
	private String situacao;
	private int treinos;
	private int jogos;
	private List<Integer> convocadoEm = new ArrayList<>();
	private boolean podeEditar;
	private boolean podeMudarInicio;
	private boolean podeCancelar;
	private Boolean semTreinador;
	private Boolean simulacao;

	public IndisponibilidadeData() {
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getDataInicio() {
		return dataInicio;
	}

	public void setDataInicio(int dataInicio) {
		this.dataInicio = dataInicio;
	}

	public int getDataFim() {
		return dataFim;
	}

	public void setDataFim(int dataFim) {
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

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public String getSituacao() {
		return situacao;
	}

	public void setSituacao(String situacao) {
		this.situacao = situacao;
	}

	public int getTreinos() {
		return treinos;
	}

	public void setTreinos(int treinos) {
		this.treinos = treinos;
	}

	public int getJogos() {
		return jogos;
	}

	public void setJogos(int jogos) {
		this.jogos = jogos;
	}

	public List<Integer> getConvocadoEm() {
		return convocadoEm;
	}

	public void setConvocadoEm(List<Integer> convocadoEm) {
		this.convocadoEm = convocadoEm;
	}

	public boolean isPodeEditar() {
		return podeEditar;
	}

	public void setPodeEditar(boolean podeEditar) {
		this.podeEditar = podeEditar;
	}

	public boolean isPodeMudarInicio() {
		return podeMudarInicio;
	}

	public void setPodeMudarInicio(boolean podeMudarInicio) {
		this.podeMudarInicio = podeMudarInicio;
	}

	public boolean isPodeCancelar() {
		return podeCancelar;
	}

	public void setPodeCancelar(boolean podeCancelar) {
		this.podeCancelar = podeCancelar;
	}

	public Boolean getSemTreinador() {
		return semTreinador;
	}

	public void setSemTreinador(Boolean semTreinador) {
		this.semTreinador = semTreinador;
	}

	public Boolean getSimulacao() {
		return simulacao;
	}

	public void setSimulacao(Boolean simulacao) {
		this.simulacao = simulacao;
	}
}
