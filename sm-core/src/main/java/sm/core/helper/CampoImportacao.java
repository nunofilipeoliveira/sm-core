package sm.core.helper;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Campos da importacao de CSV (desenho importar-csv §5 B4). As colunas do UPDATE da importacao vem so daqui (nunca do
 * pedido). Rotulos do historico = os da criacao individual (addJogador/addStaff, V1). Sem acesso a BD.
 */
public enum CampoImportacao {

	NOME("nome", "nome", "Nome", 30, 100, Formato.TEXTO, true, true),
	NOME_COMPLETO("nome_completo", "nome_completo", "Nome Completo", 100, 100, Formato.TEXTO, true, true),
	DATA_NASCIMENTO("data_nascimento", "data_nascimento", "Data Nascimento", 0, 0, Formato.DATA, true, true),
	NIF("nif", "nif", "NIF", 0, 0, Formato.NIF, true, false),
	CC("cc", "cc", "CC", 100, 0, Formato.TEXTO, true, false),
	LICENCA("licenca", "licença", "Licença", 0, 0, Formato.LICENCA, true, true),
	NUMERO("numero", "numero", "Numero", 3, 0, Formato.TEXTO, true, false),
	EMAIL("email", "email", "Email", 100, 100, Formato.EMAIL, true, true),
	TELEMOVEL("telemovel", "telemovel", "Telemovel", 100, 100, Formato.TEXTO, true, true),
	MORADA("morada", "morada", "Morada", 100, 100, Formato.TEXTO, true, true),
	CIDADE("cidade", "cidade", "Cidade", 100, 0, Formato.TEXTO, true, false),
	CODIGO_POSTAL("codigo_postal", "codigo_postal", "Código Postal", 100, 100, Formato.TEXTO, true, true),
	PAI_NOME("pai_nome", "pai_nome", "Pai - Nome", 100, 0, Formato.TEXTO, true, false),
	PAI_EMAIL("pai_email", "pai_email", "Pai - Email", 100, 0, Formato.EMAIL, true, false),
	PAI_TELEMOVEL("pai_telemovel", "pai_telemovel", "Pai - Telemovel", 100, 0, Formato.TEXTO, true, false),
	MAE_NOME("mae_nome", "mae_nome", "Mãe - Nome", 100, 0, Formato.TEXTO, true, false),
	MAE_EMAIL("mae_email", "mae_email", "Mãe - Email", 100, 0, Formato.EMAIL, true, false),
	MAE_TELEMOVEL("mae_telemovel", "mae_telemovel", "Mãe - Telemovel", 100, 0, Formato.TEXTO, true, false),
	OBSERVACOES("observacoes", "observacoes", "Observações", 500, 0, Formato.TEXTO, true, false),
	/** So staff: tipo na equipa (escalao_epoca_staff.tipo); nao e coluna de staff, nunca entra no UPDATE. */
	FUNCAO("funcao", null, "Função", 0, 0, Formato.FUNCAO, false, true);

	public enum Formato {
		TEXTO, EMAIL, DATA, NIF, LICENCA, FUNCAO
	}

	public static final String JOGADOR = "JOGADOR";
	public static final String STAFF = "STAFF";

	private static final Pattern EMAIL_VALIDO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Pattern DATA_DMA = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$");
	private static final Pattern DATA_AMD = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$");
	private static final Pattern DATA_COMPACTA = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})$");
	private static final Pattern DIGITOS = Pattern.compile("^\\d+$");

	private final String codigo;
	private final String coluna;
	private final String rotulo;
	private final int maximoJogador;
	private final int maximoStaff;
	private final Formato formato;
	private final boolean jogador;
	private final boolean staff;

	CampoImportacao(String codigo, String coluna, String rotulo, int maximoJogador, int maximoStaff, Formato formato,
			boolean jogador, boolean staff) {
		this.codigo = codigo;
		this.coluna = coluna;
		this.rotulo = rotulo;
		this.maximoJogador = maximoJogador;
		this.maximoStaff = maximoStaff;
		this.formato = formato;
		this.jogador = jogador;
		this.staff = staff;
	}

	public String getCodigo() {
		return codigo;
	}

	/** Nome real da coluna (sem acentos graves); {@code null} na funcao. */
	public String getColuna() {
		return coluna;
	}

	public String getRotulo() {
		return rotulo;
	}

	public Formato getFormato() {
		return formato;
	}

	/** Valor guardado como numero (0 = sem valor). */
	public boolean isNumerico() {
		return formato == Formato.DATA || formato == Formato.NIF || formato == Formato.LICENCA;
	}

	public boolean existeEm(String parmTipo) {
		return JOGADOR.equals(parmTipo) ? jogador : STAFF.equals(parmTipo) && staff;
	}

	/** @return o campo do tipo com este codigo, ou {@code null}. */
	public static CampoImportacao porCodigo(String parmCodigo, String parmTipo) {
		for (CampoImportacao campo : values()) {
			if (campo.codigo.equals(parmCodigo) && campo.existeEm(parmTipo)) {
				return campo;
			}
		}
		return null;
	}

	/** Resultado da validacao de uma celula: valor normalizado ("" = sem valor), erro e aviso (ou {@code null}). */
	public record Valor(String valor, String erro, String aviso) {
		static Valor ok(String parmValor) {
			return new Valor(parmValor, null, null);
		}

		static Valor erro(String parmErro) {
			return new Valor("", parmErro, null);
		}
	}

	public static String limpa(String parmValor) {
		return parmValor == null ? "" : parmValor.trim();
	}

	/**
	 * Valida e normaliza uma celula. As mensagens nunca citam o valor (vao para a resposta e podem ir para logs).
	 * Numeros e datas ficam como texto sem zeros a esquerda (data AAAAMMDD); 0 = sem valor (V9).
	 */
	public Valor valida(String parmTipo, String parmValor) {
		String valor = limpa(parmValor);
		if (valor.isEmpty()) {
			return this == NOME ? Valor.erro("O nome é obrigatório.") : Valor.ok("");
		}
		if (valor.codePoints().anyMatch(c -> c > 0xFFFF)) {
			return Valor.erro("Tem caracteres que não podem ser guardados (ex. emojis).");
		}
		switch (formato) {
		case TEXTO:
		case EMAIL: {
			int maximo = STAFF.equals(parmTipo) ? maximoStaff : maximoJogador;
			if (valor.codePointCount(0, valor.length()) > maximo) {
				return Valor.erro("Tem mais de " + maximo + " caracteres.");
			}
			if (formato == Formato.EMAIL && !EMAIL_VALIDO.matcher(valor).matches()) {
				return Valor.erro("Email inválido.");
			}
			return Valor.ok(valor);
		}
		case DATA:
			return validaData(valor);
		case NIF: {
			String digitos = valor.replace(" ", "").replace(".", "");
			if (!DIGITOS.matcher(digitos).matches()) {
				return Valor.erro("O NIF só pode ter dígitos.");
			}
			if (semZeros(digitos).isEmpty()) {
				return Valor.ok("");
			}
			if (digitos.length() != 9) {
				return Valor.erro("O NIF tem de ter 9 dígitos.");
			}
			return new Valor(semZeros(digitos), null,
					nifControloValido(digitos) ? null : "Dígito de controlo do NIF não confere.");
		}
		case LICENCA: {
			String digitos = semZeros(valor.replace(" ", "").replace(".", ""));
			if (!digitos.isEmpty() && !DIGITOS.matcher(digitos).matches()) {
				return Valor.erro("A licença só pode ter dígitos.");
			}
			if (digitos.length() > 10) {
				return Valor.erro("A licença tem mais de 10 dígitos.");
			}
			// Q6: JogadorData.licenca e int; staff.licença e decimal(10,0) (texto no DTO)
			if (JOGADOR.equals(parmTipo) && !digitos.isEmpty() && Long.parseLong(digitos) > Integer.MAX_VALUE) {
				return Valor.erro("A licença não pode ser maior do que 2147483647.");
			}
			return Valor.ok(digitos);
		}
		case FUNCAO: {
			String funcao = funcao(valor);
			return funcao == null ? Valor.erro("Função inválida (Treinador, T-adjunto ou Seccionista).")
					: Valor.ok(funcao);
		}
		default:
			return Valor.erro("Campo inválido.");
		}
	}

	/** R14: "Treinador", "T-adjunto" ou "Seccionista" (sem acentos, maiusculas nem pontuacao), ou {@code null}. */
	public static String funcao(String parmValor) {
		String chave = semAcentos(limpa(parmValor)).toLowerCase().replaceAll("[^a-z0-9]", "");
		switch (chave) {
		case "treinador":
			return "Treinador";
		case "tadjunto":
			return "T-adjunto";
		case "seccionista":
			return "Seccionista";
		default:
			return null;
		}
	}

	/** R26: sem acentos, minusculas, espacos unicos. */
	public static String normalizaNome(String parmValor) {
		return semAcentos(limpa(parmValor)).toLowerCase().replaceAll("\\s+", " ");
	}

	private static String semAcentos(String parmValor) {
		return Normalizer.normalize(parmValor, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private static String semZeros(String parmDigitos) {
		int i = 0;
		while (i < parmDigitos.length() && parmDigitos.charAt(i) == '0') {
			i++;
		}
		return parmDigitos.substring(i);
	}

	/** Modulo 11 do NIF portugues (so aviso, Q5). */
	static boolean nifControloValido(String parmNif) {
		int soma = 0;
		for (int i = 0; i < 8; i++) {
			soma += (parmNif.charAt(i) - '0') * (9 - i);
		}
		int resto = soma % 11;
		int controlo = resto < 2 ? 0 : 11 - resto;
		return controlo == parmNif.charAt(8) - '0';
	}

	private static Valor validaData(String parmValor) {
		int dia;
		int mes;
		int ano;
		Matcher m = DATA_DMA.matcher(parmValor);
		if (m.matches()) {
			dia = Integer.parseInt(m.group(1));
			mes = Integer.parseInt(m.group(2));
			ano = Integer.parseInt(m.group(3));
		} else if ((m = DATA_AMD.matcher(parmValor)).matches() || (m = DATA_COMPACTA.matcher(parmValor)).matches()) {
			ano = Integer.parseInt(m.group(1));
			mes = Integer.parseInt(m.group(2));
			dia = Integer.parseInt(m.group(3));
		} else {
			return Valor.erro("Data inválida (use dd/mm/aaaa).");
		}
		LocalDate data;
		try {
			data = LocalDate.of(ano, mes, dia);
		} catch (DateTimeException e) {
			return Valor.erro("Data inválida (use dd/mm/aaaa).");
		}
		if (data.isBefore(LocalDate.of(1900, 1, 1)) || data.isAfter(LocalDate.now())) {
			return Valor.erro("A data tem de estar entre 1900 e hoje.");
		}
		return Valor.ok(String.valueOf(ano * 10000 + mes * 100 + dia));
	}
}
