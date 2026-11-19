package sm.core.data;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Detalhe de um treino (getTreino): convocatoria, ausencias ativas, historico (so ADMIN e TREINADOR) e acoes permitidas.
 */
public class TreinoDetalheData {

	private TreinoData treino;
	private List<TreinoConvocadoData> convocatoria = new ArrayList<>();
	private List<TreinoAusenciaData> ausencias = new ArrayList<>();
	private List<String> historico = new ArrayList<>();
	private boolean podeAprovar;
	private boolean podeAjustar;
	private boolean podeMarcarPresenca;
	private boolean podeEditar;
	/** v3: ultima aprovacao ou rejeicao (APROVADO/REJEITADO), lida do historico; null sem decisao. */
	private String decisao;
	private String decididoPor;
	private Timestamp dataDecisao;
	/** v3: TREINADOR, ADMINISTRADOR ou null (decisoes anteriores a v3). */
	private String qualidadeDecisao;

	public TreinoDetalheData() {
	}

	public TreinoData getTreino() {
		return treino;
	}

	public void setTreino(TreinoData treino) {
		this.treino = treino;
	}

	public List<TreinoConvocadoData> getConvocatoria() {
		return convocatoria;
	}

	public void setConvocatoria(List<TreinoConvocadoData> convocatoria) {
		this.convocatoria = convocatoria;
	}

	public List<TreinoAusenciaData> getAusencias() {
		return ausencias;
	}

	public void setAusencias(List<TreinoAusenciaData> ausencias) {
		this.ausencias = ausencias;
	}

	public List<String> getHistorico() {
		return historico;
	}

	public void setHistorico(List<String> historico) {
		this.historico = historico;
	}

	public boolean isPodeAprovar() {
		return podeAprovar;
	}

	public void setPodeAprovar(boolean podeAprovar) {
		this.podeAprovar = podeAprovar;
	}

	public boolean isPodeAjustar() {
		return podeAjustar;
	}

	public void setPodeAjustar(boolean podeAjustar) {
		this.podeAjustar = podeAjustar;
	}

	public boolean isPodeMarcarPresenca() {
		return podeMarcarPresenca;
	}

	public void setPodeMarcarPresenca(boolean podeMarcarPresenca) {
		this.podeMarcarPresenca = podeMarcarPresenca;
	}

	public boolean isPodeEditar() {
		return podeEditar;
	}

	public void setPodeEditar(boolean podeEditar) {
		this.podeEditar = podeEditar;
	}

	public String getDecisao() {
		return decisao;
	}

	public void setDecisao(String decisao) {
		this.decisao = decisao;
	}

	public String getDecididoPor() {
		return decididoPor;
	}

	public void setDecididoPor(String decididoPor) {
		this.decididoPor = decididoPor;
	}

	public Timestamp getDataDecisao() {
		return dataDecisao;
	}

	public void setDataDecisao(Timestamp dataDecisao) {
		this.dataDecisao = dataDecisao;
	}

	public String getQualidadeDecisao() {
		return qualidadeDecisao;
	}

	public void setQualidadeDecisao(String qualidadeDecisao) {
		this.qualidadeDecisao = qualidadeDecisao;
	}
}
