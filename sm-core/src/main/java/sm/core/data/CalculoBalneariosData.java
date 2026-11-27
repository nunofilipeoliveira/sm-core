package sm.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-visualizacao do calculo automatico (calcularBalnearios). Nao grava nada; a assinatura (SHA-256) volta em
 * gravarCalculoBalnearios. mudanca: NOVO, ALTERADO, RETIRADO, IGUAL, SEM_LIVRE; alteracoes = NOVO + ALTERADO + RETIRADO.
 */
public class CalculoBalneariosData {

	private int idMicroCiclo;
	private String assinatura;
	private List<Item> itens = new ArrayList<>();
	private List<String> semBalneario = new ArrayList<>();
	private int alteracoes;

	/** Um pedido do calculo (sessao de treino ou lado de jogo). inicio/fim = a janela (HH:MM). */
	public static class Item {
		private String tipo;
		private int id;
		private String lado;
		private int data;
		private String inicio;
		private String fim;
		private String descricao;
		private List<String> atuais = new ArrayList<>();
		private Integer idProposto;
		private String nomeProposto;
		private String origem;
		private String mudanca;

		public Item() {
		}

		public String getTipo() {
			return tipo;
		}

		public void setTipo(String tipo) {
			this.tipo = tipo;
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public String getLado() {
			return lado;
		}

		public void setLado(String lado) {
			this.lado = lado;
		}

		public int getData() {
			return data;
		}

		public void setData(int data) {
			this.data = data;
		}

		public String getInicio() {
			return inicio;
		}

		public void setInicio(String inicio) {
			this.inicio = inicio;
		}

		public String getFim() {
			return fim;
		}

		public void setFim(String fim) {
			this.fim = fim;
		}

		public String getDescricao() {
			return descricao;
		}

		public void setDescricao(String descricao) {
			this.descricao = descricao;
		}

		public List<String> getAtuais() {
			return atuais;
		}

		public void setAtuais(List<String> atuais) {
			this.atuais = atuais;
		}

		public Integer getIdProposto() {
			return idProposto;
		}

		public void setIdProposto(Integer idProposto) {
			this.idProposto = idProposto;
		}

		public String getNomeProposto() {
			return nomeProposto;
		}

		public void setNomeProposto(String nomeProposto) {
			this.nomeProposto = nomeProposto;
		}

		public String getOrigem() {
			return origem;
		}

		public void setOrigem(String origem) {
			this.origem = origem;
		}

		public String getMudanca() {
			return mudanca;
		}

		public void setMudanca(String mudanca) {
			this.mudanca = mudanca;
		}

		/** V18: SEM_LIVRE ou SEM_PERMITIDO sem proposta; null com proposta. */
		private String motivo;

		public String getMotivo() {
			return motivo;
		}

		public void setMotivo(String motivo) {
			this.motivo = motivo;
		}
	}

	public CalculoBalneariosData() {
	}

	public int getIdMicroCiclo() {
		return idMicroCiclo;
	}

	public void setIdMicroCiclo(int idMicroCiclo) {
		this.idMicroCiclo = idMicroCiclo;
	}

	public String getAssinatura() {
		return assinatura;
	}

	public void setAssinatura(String assinatura) {
		this.assinatura = assinatura;
	}

	public List<Item> getItens() {
		return itens;
	}

	public void setItens(List<Item> itens) {
		this.itens = itens;
	}

	public List<String> getSemBalneario() {
		return semBalneario;
	}

	public void setSemBalneario(List<String> semBalneario) {
		this.semBalneario = semBalneario;
	}

	public int getAlteracoes() {
		return alteracoes;
	}

	public void setAlteracoes(int alteracoes) {
		this.alteracoes = alteracoes;
	}
}
