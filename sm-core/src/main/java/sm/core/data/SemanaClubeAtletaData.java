package sm.core.data;

import java.util.ArrayList;
import java.util.List;

/*
 * REGRA R138 / R141 (RGPD, dados de menores) - LER ANTES DE MEXER NESTA CLASSE.
 *
 * Esta e a resposta de getMicroCicloAtleta: o que o ATLETA ve do calendario do clube. O contrato
 * e FECHADO (02-desenho-v5.md §5.3): o que nao esta aqui nunca sai para o atleta.
 *   - So treinos/eventos APROVADOS de micro ciclos PUBLICADOS (sem estado, versao nem acoes).
 *   - De cada treino de outra equipa: so equipa, tipo, sub-grupo, data, horas e local.
 *   - observacoes so nos itens do proprio atleta (meu = true); nos outros vem sempre null.
 *   - NUNCA acrescentar: convocatorias, contagens, ausencias, motivos, avisos, quem aprovou,
 *     qualidade, historico, presencas, foraDoPlano, locais, pendentes ou rascunhos.
 * Nao reutilizar TreinoData nem JogoSemanaData aqui: um campo novo nessas classes chegaria ao
 * atleta por engano. Qualquer campo novo tem de passar pela especificacao (R138) e pelo CA97.
 */
public class SemanaClubeAtletaData {

	/** Treino ou evento aprovado (R138). */
	public static class Item {
		private int id;
		private int idEquipa;
		private String nomeEquipa;
		private int idGrupo;
		private String nomeGrupo;
		private String tipo;
		private String tipoDescricao;
		private String subGrupo;
		private int data;
		private String horaInicio;
		private String horaFim;
		private String local;
		private boolean meu;
		/** So com meu = true (Q76). */
		private String observacoes;
		/** v6: fisicos ligados (so horas, local, sub-grupo e posicao; nada de R138). */
		private List<FisicoAtletaData> fisicos;

		public Item() {
		}

		public List<FisicoAtletaData> getFisicos() {
			return fisicos;
		}

		public void setFisicos(List<FisicoAtletaData> fisicos) {
			this.fisicos = fisicos;
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public int getIdEquipa() {
			return idEquipa;
		}

		public void setIdEquipa(int idEquipa) {
			this.idEquipa = idEquipa;
		}

		public String getNomeEquipa() {
			return nomeEquipa;
		}

		public void setNomeEquipa(String nomeEquipa) {
			this.nomeEquipa = nomeEquipa;
		}

		public int getIdGrupo() {
			return idGrupo;
		}

		public void setIdGrupo(int idGrupo) {
			this.idGrupo = idGrupo;
		}

		public String getNomeGrupo() {
			return nomeGrupo;
		}

		public void setNomeGrupo(String nomeGrupo) {
			this.nomeGrupo = nomeGrupo;
		}

		public String getTipo() {
			return tipo;
		}

		public void setTipo(String tipo) {
			this.tipo = tipo;
		}

		public String getTipoDescricao() {
			return tipoDescricao;
		}

		public void setTipoDescricao(String tipoDescricao) {
			this.tipoDescricao = tipoDescricao;
		}

		public String getSubGrupo() {
			return subGrupo;
		}

		public void setSubGrupo(String subGrupo) {
			this.subGrupo = subGrupo;
		}

		public int getData() {
			return data;
		}

		public void setData(int data) {
			this.data = data;
		}

		public String getHoraInicio() {
			return horaInicio;
		}

		public void setHoraInicio(String horaInicio) {
			this.horaInicio = horaInicio;
		}

		public String getHoraFim() {
			return horaFim;
		}

		public void setHoraFim(String horaFim) {
			this.horaFim = horaFim;
		}

		public String getLocal() {
			return local;
		}

		public void setLocal(String local) {
			this.local = local;
		}

		public boolean isMeu() {
			return meu;
		}

		public void setMeu(boolean meu) {
			this.meu = meu;
		}

		public String getObservacoes() {
			return observacoes;
		}

		public void setObservacoes(String observacoes) {
			this.observacoes = observacoes;
		}
	}

	/** Jogo da semana, sem concentracao, golos, obs nem jogadores. */
	public static class Jogo {
		private int id;
		private int idEquipa;
		private String nomeEquipa;
		private String tipoEquipa;
		private int data;
		private String hora;
		private String casaFora;
		private Integer idAdversario;
		private String nomeAdversario;
		private String competicao;
		private boolean meu;
		private boolean convocado;
		private boolean podeAbrir;

		public Jogo() {
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public int getIdEquipa() {
			return idEquipa;
		}

		public void setIdEquipa(int idEquipa) {
			this.idEquipa = idEquipa;
		}

		public String getNomeEquipa() {
			return nomeEquipa;
		}

		public void setNomeEquipa(String nomeEquipa) {
			this.nomeEquipa = nomeEquipa;
		}

		public String getTipoEquipa() {
			return tipoEquipa;
		}

		public void setTipoEquipa(String tipoEquipa) {
			this.tipoEquipa = tipoEquipa;
		}

		public int getData() {
			return data;
		}

		public void setData(int data) {
			this.data = data;
		}

		public String getHora() {
			return hora;
		}

		public void setHora(String hora) {
			this.hora = hora;
		}

		public String getCasaFora() {
			return casaFora;
		}

		public void setCasaFora(String casaFora) {
			this.casaFora = casaFora;
		}

		public Integer getIdAdversario() {
			return idAdversario;
		}

		public void setIdAdversario(Integer idAdversario) {
			this.idAdversario = idAdversario;
		}

		public String getNomeAdversario() {
			return nomeAdversario;
		}

		public void setNomeAdversario(String nomeAdversario) {
			this.nomeAdversario = nomeAdversario;
		}

		public String getCompeticao() {
			return competicao;
		}

		public void setCompeticao(String competicao) {
			this.competicao = competicao;
		}

		public boolean isMeu() {
			return meu;
		}

		public void setMeu(boolean meu) {
			this.meu = meu;
		}

		public boolean isConvocado() {
			return convocado;
		}

		public void setConvocado(boolean convocado) {
			this.convocado = convocado;
		}

		public boolean isPodeAbrir() {
			return podeAbrir;
		}

		public void setPodeAbrir(boolean podeAbrir) {
			this.podeAbrir = podeAbrir;
		}
	}

	/** Cor de uma equipa com itens ou jogos na semana. */
	public static class Cor {
		private int idEquipa;
		private String nome;
		private String cor;

		public Cor() {
		}

		public Cor(int idEquipa, String nome, String cor) {
			this.idEquipa = idEquipa;
			this.nome = nome;
			this.cor = cor;
		}

		public int getIdEquipa() {
			return idEquipa;
		}

		public void setIdEquipa(int idEquipa) {
			this.idEquipa = idEquipa;
		}

		public String getNome() {
			return nome;
		}

		public void setNome(String nome) {
			this.nome = nome;
		}

		public String getCor() {
			return cor;
		}

		public void setCor(String cor) {
			this.cor = cor;
		}
	}

	/** Grupo de um evento da semana (sem o estado ativo/inativo). */
	public static class Grupo {
		private int idGrupo;
		private String nome;
		private String cor;

		public Grupo() {
		}

		public Grupo(int idGrupo, String nome, String cor) {
			this.idGrupo = idGrupo;
			this.nome = nome;
			this.cor = cor;
		}

		public int getIdGrupo() {
			return idGrupo;
		}

		public void setIdGrupo(int idGrupo) {
			this.idGrupo = idGrupo;
		}

		public String getNome() {
			return nome;
		}

		public void setNome(String nome) {
			this.nome = nome;
		}

		public String getCor() {
			return cor;
		}

		public void setCor(String cor) {
			this.cor = cor;
		}
	}

	private int dataInicio;
	private List<Item> itens = new ArrayList<>();
	private List<Jogo> jogos = new ArrayList<>();
	private List<Cor> cores = new ArrayList<>();
	private List<Grupo> grupos = new ArrayList<>();

	/** v9 (R250): ha micro ciclo PUBLICADO nesta semana; com false, itens vazio (os jogos como antes). */
	private boolean publicado;

	public SemanaClubeAtletaData() {
	}

	public boolean isPublicado() {
		return publicado;
	}

	public void setPublicado(boolean publicado) {
		this.publicado = publicado;
	}

	public int getDataInicio() {
		return dataInicio;
	}

	public void setDataInicio(int dataInicio) {
		this.dataInicio = dataInicio;
	}

	public List<Item> getItens() {
		return itens;
	}

	public void setItens(List<Item> itens) {
		this.itens = itens;
	}

	public List<Jogo> getJogos() {
		return jogos;
	}

	public void setJogos(List<Jogo> jogos) {
		this.jogos = jogos;
	}

	public List<Cor> getCores() {
		return cores;
	}

	public void setCores(List<Cor> cores) {
		this.cores = cores;
	}

	public List<Grupo> getGrupos() {
		return grupos;
	}

	public void setGrupos(List<Grupo> grupos) {
		this.grupos = grupos;
	}
}
