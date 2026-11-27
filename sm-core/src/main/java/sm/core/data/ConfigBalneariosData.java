package sm.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuracao dos balnearios do clube (getConfigBalnearios; corpo de gravarRegrasBalnearios). Na escrita le os
 * tempos, ordem (null = nao mexer), escaloes[].idEscalao + preferencias, preferenciaAdversarios e prioridade; o resto
 * e ignorado. Tempos em minutos (0-240).
 */
public class ConfigBalneariosData {

	private Integer antecedencia;
	private Integer permanencia;
	private Integer libertacao;
	private List<BalnearioData> balnearios = new ArrayList<>();
	private List<Integer> ordem;
	private List<Escalao> escaloes;
	private List<Integer> preferenciaAdversarios;
	private List<Integer> prioridade;

	/** Escalao com as suas preferencias (ids de balnearios, por ordem). comEquipas = false: so com regras gravadas (V1). */
	public static class Escalao {
		private int idEscalao;
		private String nome;
		private boolean comEquipas;
		private List<Integer> preferencias;

		public Escalao() {
		}

		public int getIdEscalao() {
			return idEscalao;
		}

		public void setIdEscalao(int idEscalao) {
			this.idEscalao = idEscalao;
		}

		public String getNome() {
			return nome;
		}

		public void setNome(String nome) {
			this.nome = nome;
		}

		public boolean isComEquipas() {
			return comEquipas;
		}

		public void setComEquipas(boolean comEquipas) {
			this.comEquipas = comEquipas;
		}

		public List<Integer> getPreferencias() {
			return preferencias;
		}

		public void setPreferencias(List<Integer> preferencias) {
			this.preferencias = preferencias;
		}
	}

	public ConfigBalneariosData() {
	}

	public Integer getAntecedencia() {
		return antecedencia;
	}

	public void setAntecedencia(Integer antecedencia) {
		this.antecedencia = antecedencia;
	}

	public Integer getPermanencia() {
		return permanencia;
	}

	public void setPermanencia(Integer permanencia) {
		this.permanencia = permanencia;
	}

	public Integer getLibertacao() {
		return libertacao;
	}

	public void setLibertacao(Integer libertacao) {
		this.libertacao = libertacao;
	}

	public List<BalnearioData> getBalnearios() {
		return balnearios;
	}

	public void setBalnearios(List<BalnearioData> balnearios) {
		this.balnearios = balnearios;
	}

	public List<Integer> getOrdem() {
		return ordem;
	}

	public void setOrdem(List<Integer> ordem) {
		this.ordem = ordem;
	}

	public List<Escalao> getEscaloes() {
		return escaloes;
	}

	public void setEscaloes(List<Escalao> escaloes) {
		this.escaloes = escaloes;
	}

	public List<Integer> getPreferenciaAdversarios() {
		return preferenciaAdversarios;
	}

	public void setPreferenciaAdversarios(List<Integer> preferenciaAdversarios) {
		this.preferenciaAdversarios = preferenciaAdversarios;
	}

	public List<Integer> getPrioridade() {
		return prioridade;
	}

	public void setPrioridade(List<Integer> prioridade) {
		this.prioridade = prioridade;
	}
}
