package sm.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de validarImportacao/gravarImportacao (desenho importar-csv §4.1). So texto e inteiros: o conteudo de
 * uma celula nunca provoca erro de Jackson. valores[i] corresponde a campos[i].
 */
public class PedidoImportacaoData {

	private String tipo;
	private int idEquipa;
	private String funcao;
	private List<String> campos = new ArrayList<>();
	private List<Linha> linhas = new ArrayList<>();

	public PedidoImportacaoData() {
	}

	public String getTipo() {
		return tipo;
	}

	public void setTipo(String tipo) {
		this.tipo = tipo;
	}

	public int getIdEquipa() {
		return idEquipa;
	}

	public void setIdEquipa(int idEquipa) {
		this.idEquipa = idEquipa;
	}

	public String getFuncao() {
		return funcao;
	}

	public void setFuncao(String funcao) {
		this.funcao = funcao;
	}

	public List<String> getCampos() {
		return campos;
	}

	public void setCampos(List<String> campos) {
		this.campos = campos;
	}

	public List<Linha> getLinhas() {
		return linhas;
	}

	public void setLinhas(List<Linha> linhas) {
		this.linhas = linhas;
	}

	/** Uma linha do ficheiro. acao, idExistente, idsVistos e atualizar so na gravacao. */
	public static class Linha {

		private int linha;
		private List<String> valores = new ArrayList<>();
		private String acao;
		private int idExistente;
		private List<Integer> idsVistos = new ArrayList<>();
		private List<String> atualizar = new ArrayList<>();

		public Linha() {
		}

		public int getLinha() {
			return linha;
		}

		public void setLinha(int linha) {
			this.linha = linha;
		}

		public List<String> getValores() {
			return valores;
		}

		public void setValores(List<String> valores) {
			this.valores = valores;
		}

		public String getAcao() {
			return acao;
		}

		public void setAcao(String acao) {
			this.acao = acao;
		}

		public int getIdExistente() {
			return idExistente;
		}

		public void setIdExistente(int idExistente) {
			this.idExistente = idExistente;
		}

		public List<Integer> getIdsVistos() {
			return idsVistos;
		}

		public void setIdsVistos(List<Integer> idsVistos) {
			this.idsVistos = idsVistos;
		}

		public List<String> getAtualizar() {
			return atualizar;
		}

		public void setAtualizar(List<String> atualizar) {
			this.atualizar = atualizar;
		}
	}
}
