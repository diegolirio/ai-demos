package poc.a2a.ana.cliente;

import java.util.Optional;

/** CPF normalizado (11 digitos). toString() mascarado para nunca vazar em log. */
public record Cpf(String digitos) {

    public Cpf {
        if (!valido(digitos)) {
            throw new IllegalArgumentException("CPF invalido");
        }
    }

    /** Aceita com ou sem mascara (digitos, pontos, hifen e espacos). */
    public static Optional<Cpf> de(String bruto) {
        if (bruto == null || !bruto.matches("[\\d.\\-\\s]+")) {
            return Optional.empty();
        }
        String digitos = bruto.replaceAll("\\D", "");
        return valido(digitos) ? Optional.of(new Cpf(digitos)) : Optional.empty();
    }

    public String mascarado() {
        return "***.***.*" + digitos.substring(7, 9) + "-" + digitos.substring(9);
    }

    @Override
    public String toString() {
        return mascarado();
    }

    private static boolean valido(String d) {
        if (d == null || !d.matches("\\d{11}") || d.chars().distinct().count() == 1) {
            return false;
        }
        return d.charAt(9) - '0' == digitoVerificador(d, 9) && d.charAt(10) - '0' == digitoVerificador(d, 10);
    }

    /** Pesos n+1..2 sobre os n primeiros digitos. */
    private static int digitoVerificador(String d, int n) {
        int soma = 0;
        for (int i = 0; i < n; i++) {
            soma += (d.charAt(i) - '0') * (n + 1 - i);
        }
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }
}
