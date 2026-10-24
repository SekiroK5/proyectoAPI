package com.proyecto.servicios.validation;

/**
 * Expresiones regulares compartidas por los DTOs de entrada.
 */
public final class ValidacionPatrones {

    private ValidacionPatrones() {
    }

    /** Solo letras (incluye acentos, enie y dieresis) separadas por espacios simples. */
    public static final String SOLO_LETRAS = "^\\p{L}+( \\p{L}+)*$";

    /**
     * CURP oficial: 4 letras, fecha AAMMDD valida, sexo (H/M/X), entidad federativa,
     * 3 consonantes internas, homoclave y digito verificador.
     */
    public static final String CURP = "^[A-Z][AEIOUX][A-Z]{2}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[HMX]"
            + "(AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)"
            + "[B-DF-HJ-NP-TV-Z]{3}[0-9A-Z]\\d$";

    /** RFC: 3 letras (moral, 12 car.) o 4 letras (fisica, 13 car.), fecha AAMMDD valida y homoclave. */
    public static final String RFC = "^[A-Z&Ñ]{3,4}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[A-Z0-9]{3}$";

    /** Correo con dominio que contiene al menos un punto (mas estricto que @Email). */
    public static final String CORREO = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    /** Texto libre con al menos un caracter distinto de espacio. */
    public static final String TEXTO_NO_VACIO = "^(?s).*\\S.*$";

    public static final String TELEFONO = "^\\d{10}$";

    public static final String CODIGO_POSTAL = "^\\d{5}$";

    public static final String NUMERO_CUENTA = "^\\d{16}$";

    /** 8 a 64 caracteres sin espacios, con al menos una mayuscula, una minuscula, un digito y un caracter especial. */
    public static final String CONTRASENA = "^(?=.*\\p{Lu})(?=.*\\p{Ll})(?=.*\\d)(?=.*[^\\p{L}\\d])\\S{8,64}$";

    /** Codigo de la app autenticadora (TOTP). */
    public static final String CODIGO_A2F = "^\\d{6}$";
}
