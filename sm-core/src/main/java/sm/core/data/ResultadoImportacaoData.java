package sm.core.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Resposta de validarImportacao/gravarImportacao (desenho importar-csv §4.2), uma entrada por linha do pedido,
 * pela mesma ordem.
 */
public class ResultadoImportacaoData {

	private List<Linha> linhas = new ArrayList<>();

	public ResultadoImportacaoData() {
	}

	public List<Linha> getLinhas() {
		return linhas;
	}

	public void setLinhas(List<Linha> linhas) {
		this.linhas = linhas;
	}

	/**
	 * Estado: validar CRIAR | ATUALIZAR | SEM_ALTERACOES | POSSIVEL_DUPLICADO | ERRO; gravar CRIADO | ATUALIZADO |
	 * SEM_ALTERACOES | ERRO | MUDOU.
	 */
	public static class Linha {

		private int linha;
		private String estado = "";
		private String mensagem = "";
		private List<Mensagem> erros = new ArrayList<>();
		private List<Mensagem> avisos = new ArrayList<>();
		private int idExistente;
		private List<Pessoa> candidatos = new ArrayList<>();
		private List<Diferenca> diferencas = new ArrayList<>();
		private boolean jaNaEquipa;
		private boolean associadoEquipa;
		private int idCriado;

		public Linha() {
		}

		public int getLinha() {
			return linha;
		}

		public void setLinha(int linha) {
			this.linha = linha;
		}

		public String getEstado() {
			return estado;
		}

		public void setEstado(String estado) {
			this.estado = estado;
		}

		public String getMensagem() {
			return mensagem;
		}

		public void setMensagem(String mensagem) {
			this.mensagem = mensagem;
		}

		public List<Mensagem> getErros() {
			return erros;
		}

		public void setErros(List<Mensagem> erros) {
			this.erros = erros;
		}

		public List<Mensagem> getAvisos() {
			return avisos;
		}

		public void setAvisos(List<Mensagem> avisos) {
			this.avisos = avisos;
		}

		public int getIdExistente() {
			return idExistente;
		}

		public void setIdExistente(int idExistente) {
			this.idExistente = idExistente;
		}

		public List<Pessoa> getCandidatos() {
			return candidatos;
		}

		public void setCandidatos(List<Pessoa> candidatos) {
			this.candidatos = candidatos;
		}

		public List<Diferenca> getDiferencas() {
			return diferencas;
		}

		public void setDiferencas(List<Diferenca> diferencas) {
			this.diferencas = diferencas;
		}

		public boolean isJaNaEquipa() {
			return jaNaEquipa;
		}

		public void setJaNaEquipa(boolean jaNaEquipa) {
			this.jaNaEquipa = jaNaEquipa;
		}

		public boolean isAssociadoEquipa() {
			return associadoEquipa;
		}

		public void setAssociadoEquipa(boolean associadoEquipa) {
			this.associadoEquipa = associadoEquipa;
		}

		public int getIdCriado() {
			return idCriado;
		}

		public void setIdCriado(int idCriado) {
			this.idCriado = idCriado;
		}
	}

	/** Erro ou aviso de um campo (codigo do campo; "" = da linha). */
	public static class Mensagem {

		private String campo;
		private String mensagem;

		public Mensagem() {
		}

		public Mensagem(String campo, String mensagem) {
			this.campo = campo;
			this.mensagem = mensagem;
		}

		public String getCampo() {
			return campo;
		}

		public void setCampo(String campo) {
			this.campo = campo;
		}

		public String getMensagem() {
			return mensagem;
		}

		public void setMensagem(String mensagem) {
			this.mensagem = mensagem;
		}
	}

	/** Registo existente encontrado (candidato). */
	public static class Pessoa {

		private int id;
		private String nome;
		private String nomeCompleto;
		private int dataNascimento;
		private boolean inativo;
		private boolean ligadoJogador;

		public Pessoa() {
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public String getNome() {
			return nome;
		}

		public void setNome(String nome) {
			this.nome = nome;
		}

		public String getNomeCompleto() {
			return nomeCompleto;
		}

		public void setNomeCompleto(String nomeCompleto) {
			this.nomeCompleto = nomeCompleto;
		}

		public int getDataNascimento() {
			return dataNascimento;
		}

		public void setDataNascimento(int dataNascimento) {
			this.dataNascimento = dataNascimento;
		}

		public boolean isInativo() {
			return inativo;
		}

		public void setInativo(boolean inativo) {
			this.inativo = inativo;
		}

		public boolean isLigadoJogador() {
			return ligadoJogador;
		}

		public void setLigadoJogador(boolean ligadoJogador) {
			this.ligadoJogador = ligadoJogador;
		}
	}

	/** Diferenca entre o valor atual e o do CSV num campo mapeado. */
	public static class Diferenca {

		private String campo;
		private String rotulo;
		private String atual;
		private String novo;
		private boolean editavel;

		public Diferenca() {
		}

		public Diferenca(String campo, String rotulo, String atual, String novo, boolean editavel) {
			this.campo = campo;
			this.rotulo = rotulo;
			this.atual = atual;
			this.novo = novo;
			this.editavel = editavel;
		}

		public String getCampo() {
			return campo;
		}

		public void setCampo(String campo) {
			this.campo = campo;
		}

		public String getRotulo() {
			return rotulo;
		}

		public void setRotulo(String rotulo) {
			this.rotulo = rotulo;
		}

		public String getAtual() {
			return atual;
		}

		public void setAtual(String atual) {
			this.atual = atual;
		}

		public String getNovo() {
			return novo;
		}

		public void setNovo(String novo) {
			this.novo = novo;
		}

		public boolean isEditavel() {
			return editavel;
		}

		public void setEditavel(boolean editavel) {
			this.editavel = editavel;
		}
	}
}
