package sm.core.data;

/**
 * Treino visto pelo atleta (sem contagens nem dados de colegas). estado: APROVADO, REALIZADO ou CANCELADO.
 */
public class TreinoAtletaData {

	private int id;
	private String tipo;
	private String tipoDescricao;
	private String nomeEquipa;
	/** Cor da equipa (#RRGGBB) ou null; escalao_epoca.cor (V8). */
	private String corEquipa;
	/** v5 (Q79): ha uma notificacao CONVOCATORIA_ENTROU por ler deste treino para esta conta. */
	private boolean convocatoriaAlterada;

	public boolean isConvocatoriaAlterada() {
		return convocatoriaAlterada;
	}

	public void setConvocatoriaAlterada(boolean convocatoriaAlterada) {
		this.convocatoriaAlterada = convocatoriaAlterada;
	}
	private String subGrupo;
	private int data;
	private String horaInicio;
	private String horaFim;
	private String local;
	private String observacoes;
	private String estado;
	private int versao;
	private boolean sobreposto;
	private boolean prazoTerminado;
	private TreinoAusenciaData ausencia;
	/** v6: fisicos ligados a este treino (so horas, local, sub-grupo e posicao). */
	private java.util.List<FisicoAtletaData> fisicos;

	/** v8: VOU, NAO_VOU ou null (sem resposta). So dados do proprio atleta. */
	private String resposta;
	private boolean caducada;
	private java.sql.Timestamp dataConfirmacao;
	/** v8 (Y6): a sessao que o atleta ve; vai no pedido VOU. */
	private String sessao;
	private boolean podeResponder;

	/** v9 (Z4): Pendente/Rejeitado ja visto (micro ciclo publicado): sem horas, local, fisicos nem Vou. */
	private boolean emRevisao;

	public TreinoAtletaData() {
	}

	public boolean isEmRevisao() {
		return emRevisao;
	}

	public void setEmRevisao(boolean emRevisao) {
		this.emRevisao = emRevisao;
	}

	public String getResposta() {
		return resposta;
	}

	public void setResposta(String resposta) {
		this.resposta = resposta;
	}

	public boolean isCaducada() {
		return caducada;
	}

	public void setCaducada(boolean caducada) {
		this.caducada = caducada;
	}

	public java.sql.Timestamp getDataConfirmacao() {
		return dataConfirmacao;
	}

	public void setDataConfirmacao(java.sql.Timestamp dataConfirmacao) {
		this.dataConfirmacao = dataConfirmacao;
	}

	public String getSessao() {
		return sessao;
	}

	public void setSessao(String sessao) {
		this.sessao = sessao;
	}

	public boolean isPodeResponder() {
		return podeResponder;
	}

	public void setPodeResponder(boolean podeResponder) {
		this.podeResponder = podeResponder;
	}

	public java.util.List<FisicoAtletaData> getFisicos() {
		return fisicos;
	}

	public void setFisicos(java.util.List<FisicoAtletaData> fisicos) {
		this.fisicos = fisicos;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
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

	public String getNomeEquipa() {
		return nomeEquipa;
	}

	public void setNomeEquipa(String nomeEquipa) {
		this.nomeEquipa = nomeEquipa;
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

	public String getObservacoes() {
		return observacoes;
	}

	public void setObservacoes(String observacoes) {
		this.observacoes = observacoes;
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public int getVersao() {
		return versao;
	}

	public void setVersao(int versao) {
		this.versao = versao;
	}

	public boolean isSobreposto() {
		return sobreposto;
	}

	public void setSobreposto(boolean sobreposto) {
		this.sobreposto = sobreposto;
	}

	public boolean isPrazoTerminado() {
		return prazoTerminado;
	}

	public void setPrazoTerminado(boolean prazoTerminado) {
		this.prazoTerminado = prazoTerminado;
	}

	public TreinoAusenciaData getAusencia() {
		return ausencia;
	}

	public void setAusencia(TreinoAusenciaData ausencia) {
		this.ausencia = ausencia;
	}

	public String getCorEquipa() {
		return corEquipa;
	}

	public void setCorEquipa(String corEquipa) {
		this.corEquipa = corEquipa;
	}

	/** Balnearios (V17): nomes; null em "em revisao", CANCELADO ou sem nenhum. */
	private java.util.List<String> balnearios;

	public java.util.List<String> getBalnearios() {
		return balnearios;
	}

	public void setBalnearios(java.util.List<String> balnearios) {
		this.balnearios = balnearios;
	}
}
