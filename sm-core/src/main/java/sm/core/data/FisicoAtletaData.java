package sm.core.data;

/**
 * v7 (X5): fisico ligado visto pelo atleta. Contrato fechado (R138): sem contagens, excluidos nem nomes; copiado campo
 * a campo nas leituras do atleta.
 */
public class FisicoAtletaData extends FisicoData {

	/** O atleta faz este fisico. */
	private boolean meu;

	public FisicoAtletaData() {
	}

	/** Copia so os campos base (FisicoData) e o meu. */
	public static FisicoAtletaData de(FisicoData parmFisico, boolean parmMeu) {
		FisicoAtletaData f = new FisicoAtletaData();
		f.setId(parmFisico.getId());
		f.setHoraInicio(parmFisico.getHoraInicio());
		f.setHoraFim(parmFisico.getHoraFim());
		f.setLocal(parmFisico.getLocal());
		f.setSubGrupo(parmFisico.getSubGrupo());
		f.setPosicao(parmFisico.getPosicao());
		f.setMeu(parmMeu);
		return f;
	}

	public boolean isMeu() {
		return meu;
	}

	public void setMeu(boolean meu) {
		this.meu = meu;
	}
}
