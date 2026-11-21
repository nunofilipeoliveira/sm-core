package sm.core.data;

import java.sql.Timestamp;

/**
 * v8: estado da resposta num item de "Os meus atletas" (VOU, NAO_VOU, SEM_RESPOSTA, CADUCADA, SEM_CONTA); data so nos itens minha.
 */
public class RespostaItemData {

	private String estado;
	private Timestamp data;

	public RespostaItemData() {
	}

	public String getEstado() {
		return estado;
	}

	public void setEstado(String estado) {
		this.estado = estado;
	}

	public Timestamp getData() {
		return data;
	}

	public void setData(Timestamp data) {
		this.data = data;
	}
}
