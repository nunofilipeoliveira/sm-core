package sm.core.data;

/**
 * Jogo da semana no Micro Ciclo (so leitura). data yyyymmdd; casaFora CASA/FORA/null; golos so com
 * estado CONCLUIDO. Sem obs nem dados de jogadores.
 */
public class JogoSemanaData {

	private int id;
	private int idEquipa;
	private String nomeEquipa;
	private String tipoEquipa;
	private int data;
	private String hora;
	private String horaConcentracao;
	private String casaFora;
	private Integer idAdversario;
	private String nomeAdversario;
	private String competicao;
	private String estado;
	private Integer golosEquipa;
	private Integer golosAdversario;
	/** v5 (R110): o atleta consta em jogo_jogador (nao INDISPONIVEL); null nas leituras de staff. */
	private Boolean convocado;

	public JogoSemanaData() {
	}

	public Boolean getConvocado() {
		return convocado;
	}

	public void setConvocado(Boolean convocado) {
		this.convocado = convocado;
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

	public String getHoraConcentracao() {
		return horaConcentracao;
	}

	public void setHoraConcentracao(String horaConcentracao) {
		this.horaConcentracao = horaConcentracao;
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

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public Integer getGolosEquipa() {
		return golosEquipa;
	}

	public void setGolosEquipa(Integer golosEquipa) {
		this.golosEquipa = golosEquipa;
	}

	public Integer getGolosAdversario() {
		return golosAdversario;
	}

	public void setGolosAdversario(Integer golosAdversario) {
		this.golosAdversario = golosAdversario;
	}

	// ==================== balnearios (V17): so jogos em casa; null = sem dados ====================

	private java.util.List<BalnearioAtribuidoData> balnearios;
	private java.util.List<BalnearioAtribuidoData> balneariosAdversario;
	private java.util.List<String> avisosBalnearios;

	public java.util.List<BalnearioAtribuidoData> getBalnearios() {
		return balnearios;
	}

	public void setBalnearios(java.util.List<BalnearioAtribuidoData> balnearios) {
		this.balnearios = balnearios;
	}

	public java.util.List<BalnearioAtribuidoData> getBalneariosAdversario() {
		return balneariosAdversario;
	}

	public void setBalneariosAdversario(java.util.List<BalnearioAtribuidoData> balneariosAdversario) {
		this.balneariosAdversario = balneariosAdversario;
	}

	public java.util.List<String> getAvisosBalnearios() {
		return avisosBalnearios;
	}

	public void setAvisosBalnearios(java.util.List<String> avisosBalnearios) {
		this.avisosBalnearios = avisosBalnearios;
	}

	/** V18, so ADMIN: ids dos balnearios que cada lado nao pode usar; null sem regras. */
	private java.util.List<Integer> balneariosNaoPermitidos;
	private java.util.List<Integer> balneariosNaoPermitidosAdversario;

	public java.util.List<Integer> getBalneariosNaoPermitidos() {
		return balneariosNaoPermitidos;
	}

	public void setBalneariosNaoPermitidos(java.util.List<Integer> balneariosNaoPermitidos) {
		this.balneariosNaoPermitidos = balneariosNaoPermitidos;
	}

	public java.util.List<Integer> getBalneariosNaoPermitidosAdversario() {
		return balneariosNaoPermitidosAdversario;
	}

	public void setBalneariosNaoPermitidosAdversario(java.util.List<Integer> balneariosNaoPermitidosAdversario) {
		this.balneariosNaoPermitidosAdversario = balneariosNaoPermitidosAdversario;
	}
}
