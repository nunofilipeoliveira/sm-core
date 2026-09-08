package sm.core.data;

import java.util.ArrayList;

/**
 * Resumo de performance de um atleta nos treinos.
 * Alimentado pelo endpoint /sm/performance/jogador/{idJogador}/{idUtilizador}/{tenantId}
 * e reutilizado no resumo da equipa.
 */
public class PerformanceResumoJogadorData {

	private int id_jogador;
	private String nome_jogador;
	// Número de treinos com registo de presença do atleta no período.
	private int total_treinos;
	private int total_presencas;
	private int total_ausencias_avisou;
	private int total_ausencias_nao_avisou;
	private int total_lesoes;
	private double percentagem_presencas;
	// Número de treinos em que o atleta foi avaliado (classificação preenchida).
	private int treinos_avaliados;
	// Média das classificações (1 a 5) do atleta. Null se não houver treinos avaliados.
	private Double media_classificacao;
	// Evolução da classificação do atleta ao longo dos treinos.
	private ArrayList<PerformanceEvolucaoTreinoData> evolucao;
	// Histórico completo de presenças/faltas do atleta.
	private ArrayList<PerformanceHistoricoData> historico;

	public PerformanceResumoJogadorData() {
		this.id_jogador = 0;
		this.nome_jogador = "";
		this.total_treinos = 0;
		this.total_presencas = 0;
		this.total_ausencias_avisou = 0;
		this.total_ausencias_nao_avisou = 0;
		this.total_lesoes = 0;
		this.percentagem_presencas = 0;
		this.treinos_avaliados = 0;
		this.media_classificacao = null;
		this.evolucao = new ArrayList<PerformanceEvolucaoTreinoData>();
		this.historico = new ArrayList<PerformanceHistoricoData>();
	}

	public int getId_jogador() {
		return id_jogador;
	}

	public void setId_jogador(int id_jogador) {
		this.id_jogador = id_jogador;
	}

	public String getNome_jogador() {
		return nome_jogador;
	}

	public void setNome_jogador(String nome_jogador) {
		this.nome_jogador = nome_jogador;
	}

	public int getTotal_treinos() {
		return total_treinos;
	}

	public void setTotal_treinos(int total_treinos) {
		this.total_treinos = total_treinos;
	}

	public int getTotal_presencas() {
		return total_presencas;
	}

	public void setTotal_presencas(int total_presencas) {
		this.total_presencas = total_presencas;
	}

	public int getTotal_ausencias_avisou() {
		return total_ausencias_avisou;
	}

	public void setTotal_ausencias_avisou(int total_ausencias_avisou) {
		this.total_ausencias_avisou = total_ausencias_avisou;
	}

	public int getTotal_ausencias_nao_avisou() {
		return total_ausencias_nao_avisou;
	}

	public void setTotal_ausencias_nao_avisou(int total_ausencias_nao_avisou) {
		this.total_ausencias_nao_avisou = total_ausencias_nao_avisou;
	}

	public int getTotal_lesoes() {
		return total_lesoes;
	}

	public void setTotal_lesoes(int total_lesoes) {
		this.total_lesoes = total_lesoes;
	}

	public double getPercentagem_presencas() {
		return percentagem_presencas;
	}

	public void setPercentagem_presencas(double percentagem_presencas) {
		this.percentagem_presencas = percentagem_presencas;
	}

	public int getTreinos_avaliados() {
		return treinos_avaliados;
	}

	public void setTreinos_avaliados(int treinos_avaliados) {
		this.treinos_avaliados = treinos_avaliados;
	}

	public Double getMedia_classificacao() {
		return media_classificacao;
	}

	public void setMedia_classificacao(Double media_classificacao) {
		this.media_classificacao = media_classificacao;
	}

	public ArrayList<PerformanceEvolucaoTreinoData> getEvolucao() {
		return evolucao;
	}

	public void setEvolucao(ArrayList<PerformanceEvolucaoTreinoData> evolucao) {
		this.evolucao = evolucao;
	}

	public ArrayList<PerformanceHistoricoData> getHistorico() {
		return historico;
	}

	public void setHistorico(ArrayList<PerformanceHistoricoData> historico) {
		this.historico = historico;
	}

}
