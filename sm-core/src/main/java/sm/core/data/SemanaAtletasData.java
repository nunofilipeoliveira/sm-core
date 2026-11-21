package sm.core.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * v7 (X8, X9): resposta de getMeusAtletas ("Os meus atletas"). Contrato fechado (R210): o que nao esta aqui nao sai.
 * Sem convocatorias, contagens, observacoes, motivoEstado, aprovadoPor, historico, avisos, versao nem podeAprovar. Nos
 * itens de outras equipas (minha = false): idTreino null e ausencia so {"avisou": true}.
 */
public class SemanaAtletasData {

	/** Equipa pedida (ou do utilizador). */
	public static class Equipa {
		private int idEquipa;
		private String nome;
		private String cor;
		private boolean minha;

		public Equipa() {
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

		public boolean isMinha() {
			return minha;
		}

		public void setMinha(boolean minha) {
			this.minha = minha;
		}
	}

	/** Atleta do plantel das equipas, com os itens da semana. */
	public static class Atleta {
		private int idJogador;
		private String nome;
		private List<Integer> equipas = new ArrayList<>();
		private boolean comConflitos;
		private List<Item> itens = new ArrayList<>();
		private List<Conflito> conflitos = new ArrayList<>();
		/** v8: tem um treino Aprovado por comecar Sem resposta ou Caducado (os sem conta nao contam). */
		private boolean porConfirmar;

		public Atleta() {
		}

		public boolean isPorConfirmar() {
			return porConfirmar;
		}

		public void setPorConfirmar(boolean porConfirmar) {
			this.porConfirmar = porConfirmar;
		}

		public int getIdJogador() {
			return idJogador;
		}

		public void setIdJogador(int idJogador) {
			this.idJogador = idJogador;
		}

		public String getNome() {
			return nome;
		}

		public void setNome(String nome) {
			this.nome = nome;
		}

		public List<Integer> getEquipas() {
			return equipas;
		}

		public void setEquipas(List<Integer> equipas) {
			this.equipas = equipas;
		}

		public boolean isComConflitos() {
			return comConflitos;
		}

		public void setComConflitos(boolean comConflitos) {
			this.comConflitos = comConflitos;
		}

		public List<Item> getItens() {
			return itens;
		}

		public void setItens(List<Item> itens) {
			this.itens = itens;
		}

		public List<Conflito> getConflitos() {
			return conflitos;
		}

		public void setConflitos(List<Conflito> conflitos) {
			this.conflitos = conflitos;
		}
	}

	/**
	 * Treino (TREINO: de escalao, outro ou fisico avulso; tipoTreino diz qual), fisico ligado (FISICO, com idPrincipal)
	 * ou jogo (JOGO, com idJogo). estado: APROVADO, REALIZADO, PENDENTE ou RASCUNHO (so ADMIN). ausencia null = nao avisou.
	 */
	public static class Item {
		private String chave;
		private String tipo;
		private Integer idTreino;
		private Integer idPrincipal;
		private Integer idJogo;
		private int idEquipa;
		private String nomeEquipa;
		private String tipoTreino;
		private String tipoDescricao;
		private String subGrupo;
		private String tipoEquipa;
		private int data;
		private String horaInicio;
		private String horaFim;
		private String local;
		private String casaFora;
		private String nomeAdversario;
		private String estado;
		private boolean minha;
		private Map<String, Object> ausencia;
		private boolean conflito;
		/** v8: so nos TREINO APROVADO/REALIZADO (e repetido, sem data, no FISICO); null nos outros. */
		private RespostaItemData resposta;

		public Item() {
		}

		public RespostaItemData getResposta() {
			return resposta;
		}

		public void setResposta(RespostaItemData resposta) {
			this.resposta = resposta;
		}

		public String getChave() {
			return chave;
		}

		public void setChave(String chave) {
			this.chave = chave;
		}

		public String getTipo() {
			return tipo;
		}

		public void setTipo(String tipo) {
			this.tipo = tipo;
		}

		public Integer getIdTreino() {
			return idTreino;
		}

		public void setIdTreino(Integer idTreino) {
			this.idTreino = idTreino;
		}

		public Integer getIdPrincipal() {
			return idPrincipal;
		}

		public void setIdPrincipal(Integer idPrincipal) {
			this.idPrincipal = idPrincipal;
		}

		public Integer getIdJogo() {
			return idJogo;
		}

		public void setIdJogo(Integer idJogo) {
			this.idJogo = idJogo;
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

		public String getTipoTreino() {
			return tipoTreino;
		}

		public void setTipoTreino(String tipoTreino) {
			this.tipoTreino = tipoTreino;
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

		public String getCasaFora() {
			return casaFora;
		}

		public void setCasaFora(String casaFora) {
			this.casaFora = casaFora;
		}

		public String getNomeAdversario() {
			return nomeAdversario;
		}

		public void setNomeAdversario(String nomeAdversario) {
			this.nomeAdversario = nomeAdversario;
		}

		public String getEstado() {
			return estado;
		}

		public void setEstado(String estado) {
			this.estado = estado;
		}

		public boolean isMinha() {
			return minha;
		}

		public void setMinha(boolean minha) {
			this.minha = minha;
		}

		public Map<String, Object> getAusencia() {
			return ausencia;
		}

		public void setAusencia(Map<String, Object> ausencia) {
			this.ausencia = ausencia;
		}

		public boolean isConflito() {
			return conflito;
		}

		public void setConflito(boolean conflito) {
			this.conflito = conflito;
		}
	}

	/** Sobreposicao num dia, com texto. */
	public static class Conflito {
		private int data;
		private String texto;

		public Conflito() {
		}

		public Conflito(int data, String texto) {
			this.data = data;
			this.texto = texto;
		}

		public int getData() {
			return data;
		}

		public void setData(int data) {
			this.data = data;
		}

		public String getTexto() {
			return texto;
		}

		public void setTexto(String texto) {
			this.texto = texto;
		}
	}

	private int dataInicio;
	private boolean semPlano;
	private List<Equipa> equipas = new ArrayList<>();
	private List<Atleta> atletas = new ArrayList<>();
	private int numAtletasComConflitos;
	/** v8 */
	private int numAtletasPorConfirmar;

	public SemanaAtletasData() {
	}

	public int getNumAtletasPorConfirmar() {
		return numAtletasPorConfirmar;
	}

	public void setNumAtletasPorConfirmar(int numAtletasPorConfirmar) {
		this.numAtletasPorConfirmar = numAtletasPorConfirmar;
	}

	public int getDataInicio() {
		return dataInicio;
	}

	public void setDataInicio(int dataInicio) {
		this.dataInicio = dataInicio;
	}

	public boolean isSemPlano() {
		return semPlano;
	}

	public void setSemPlano(boolean semPlano) {
		this.semPlano = semPlano;
	}

	public List<Equipa> getEquipas() {
		return equipas;
	}

	public void setEquipas(List<Equipa> equipas) {
		this.equipas = equipas;
	}

	public List<Atleta> getAtletas() {
		return atletas;
	}

	public void setAtletas(List<Atleta> atletas) {
		this.atletas = atletas;
	}

	public int getNumAtletasComConflitos() {
		return numAtletasComConflitos;
	}

	public void setNumAtletasComConflitos(int numAtletasComConflitos) {
		this.numAtletasComConflitos = numAtletasComConflitos;
	}
}
