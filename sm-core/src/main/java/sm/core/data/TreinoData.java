package sm.core.data;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Treino do micro ciclo. estado inclui REALIZADO (derivado: APROVADO com idPresenca). Corpo de gravarTreino (id=0 cria).
 */
public class TreinoData {

	private int id;
	private int idMicroCiclo;
	private int idEquipa;
	private String nomeEquipa;
	/** Cor da equipa (#RRGGBB) ou null; escalao_epoca.cor (V8). */
	private String corEquipa;
	/** v5 (W1): evento do clube sem equipa: idGrupo > 0 e idEquipa 0; nos treinos 0 e null. */
	private int idGrupo;
	private String nomeGrupo;
	/** v5 (W15): evento que passa a Aprovado ao publicar (ou nasce Aprovado num publicado). */
	private boolean aprovarAoPublicar;

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

	public boolean isAprovarAoPublicar() {
		return aprovarAoPublicar;
	}

	public void setAprovarAoPublicar(boolean aprovarAoPublicar) {
		this.aprovarAoPublicar = aprovarAoPublicar;
	}
	private String tipo;
	private String tipoDescricao;
	private String subGrupo;
	private int data;
	private String horaInicio;
	private String horaFim;
	private String local;
	private String observacoes;
	private String estado;
	private String motivoEstado;
	private int versao;
	private Integer idPresenca;
	private String aprovadoPor;
	private Timestamp dataAprovacao;
	private int numConvocados;
	private int numAusencias;
	private boolean naoAprovadoATempo;
	private boolean podeAprovar;
	/** v3: TREINADOR, ADMINISTRADOR ou null (so com podeAprovar). */
	private String qualidadeSeAprovar;
	private List<String> avisos = new ArrayList<>();

	public TreinoData() {
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getIdMicroCiclo() {
		return idMicroCiclo;
	}

	public void setIdMicroCiclo(int idMicroCiclo) {
		this.idMicroCiclo = idMicroCiclo;
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

	public String getMotivoEstado() {
		return motivoEstado;
	}

	public void setMotivoEstado(String motivoEstado) {
		this.motivoEstado = motivoEstado;
	}

	public int getVersao() {
		return versao;
	}

	public void setVersao(int versao) {
		this.versao = versao;
	}

	public Integer getIdPresenca() {
		return idPresenca;
	}

	public void setIdPresenca(Integer idPresenca) {
		this.idPresenca = idPresenca;
	}

	public String getAprovadoPor() {
		return aprovadoPor;
	}

	public void setAprovadoPor(String aprovadoPor) {
		this.aprovadoPor = aprovadoPor;
	}

	public Timestamp getDataAprovacao() {
		return dataAprovacao;
	}

	public void setDataAprovacao(Timestamp dataAprovacao) {
		this.dataAprovacao = dataAprovacao;
	}

	public int getNumConvocados() {
		return numConvocados;
	}

	public void setNumConvocados(int numConvocados) {
		this.numConvocados = numConvocados;
	}

	public int getNumAusencias() {
		return numAusencias;
	}

	public void setNumAusencias(int numAusencias) {
		this.numAusencias = numAusencias;
	}

	public boolean isNaoAprovadoATempo() {
		return naoAprovadoATempo;
	}

	public void setNaoAprovadoATempo(boolean naoAprovadoATempo) {
		this.naoAprovadoATempo = naoAprovadoATempo;
	}

	public boolean isPodeAprovar() {
		return podeAprovar;
	}

	public void setPodeAprovar(boolean podeAprovar) {
		this.podeAprovar = podeAprovar;
	}

	public String getQualidadeSeAprovar() {
		return qualidadeSeAprovar;
	}

	public void setQualidadeSeAprovar(String qualidadeSeAprovar) {
		this.qualidadeSeAprovar = qualidadeSeAprovar;
	}

	public List<String> getAvisos() {
		return avisos;
	}

	public void setAvisos(List<String> avisos) {
		this.avisos = avisos;
	}

	public String getCorEquipa() {
		return corEquipa;
	}

	public void setCorEquipa(String corEquipa) {
		this.corEquipa = corEquipa;
	}
}
