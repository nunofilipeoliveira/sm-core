package sm.core.helper;

/**
 * Utilitarios partilhados pelos historicos (jogador_historico, staff_historico).
 */
public final class HistoricoUtils {

	/** Tamanho de valor_antigo/valor_novo nas tabelas *_historico (varchar(100)). */
	public static final int MAX_VALOR = 100;

	private HistoricoUtils() {
	}

	/**
	 * Corta o valor a {@link #MAX_VALOR} caracteres, sem partir caracteres fora do BMP.
	 * shortcut: o historico perde o fim dos valores longos (ex.: observacoes ate 500), a alternativa
	 * e um V6 que aumente valor_antigo/valor_novo nas tabelas *_historico.
	 */
	public static String truncaValor(String valor) {
		if (valor == null || valor.codePointCount(0, valor.length()) <= MAX_VALOR) {
			return valor;
		}
		return valor.substring(0, valor.offsetByCodePoints(0, MAX_VALOR));
	}
}
