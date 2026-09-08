package sm.core.data;

import java.util.ArrayList;

/**
 * Resumo de performance dos atletas de uma equipa nos treinos.
 * Alimentado pelo endpoint /sm/performance/equipa/{idEquipa}/{idUtilizador}/{tenantId}.
 */
public class PerformanceEquipaData {

	private int id_equipa;
	private String nome_equipa;
	private int total_treinos;
	private int num_jogadores;
	private int total_presencas;
	private int total_ausencias_avisou;
	private int total_ausencias_nao_avisou;
	private int total_lesoes;
	private double percentagem_presencas;
	// Média das classificações (1 a 5) atribuídas aos atletas nos treinos. Null se não houver treinos avaliados.
	private Double media_classificacao;
	private ArrayList<PerformanceResumoJogadorData> jogadores;

	public PerformanceEquipaData() {
		this.id_equipa = 0;
		this.nome_equipa = "";
		this.total_treinos = 0;
		this.num_jogadores = 0;
		this.total_presencas = 0;
		this.total_ausencias_avisou = 0;
		this.total_ausencias_nao_avisou = 0;
		this.total_lesoes = 0;
		this.percentagem_presencas = 0;
		this.media_classificacao = null;
		this.jogadores = new ArrayList<PerformanceResumoJogadorData>();
	}

	public int getId_equipa() {
		return id_equipa;
	}

	public void setId_equipa(int id_equipa) {
		this.id_equipa = id_equipa;
	}

	public String getNome_equipa() {
		return nome_equipa;
	}

	public void setNome_equipa(String nome_equipa) {
		this.nome_equipa = nome_equipa;
	}

	public int getTotal_treinos() {
		return total_treinos;
	}

	public void setTotal_treinos(int total_treinos) {
		this.total_treinos = total_treinos;
	}

	public int getNum_jogadores() {
		return num_jogadores;
	}

	public void setNum_jogadores(int num_jogadores) {
		this.num_jogadores = num_jogadores;
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

	public Double getMedia_classificacao() {
		return media_classificacao;
	}

	public void setMedia_classificacao(Double media_classificacao) {
		this.media_classificacao = media_classificacao;
	}

	public ArrayList<PerformanceResumoJogadorData> getJogadores() {
		return jogadores;
	}

	public void setJogadores(ArrayList<PerformanceResumoJogadorData> jogadores) {
		this.jogadores = jogadores;
	}

}
