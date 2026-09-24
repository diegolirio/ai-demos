package poc.a2a.ana.cliente;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Cadastro mock CPF -> customerId (CPFs de teste ficticios, com digitos verificadores validos). */
@Component
public class CadastroClientes {

    private final Map<String, String> customerIdPorCpf = Map.ofEntries(
            Map.entry("11100100105", "cli-001"),
            Map.entry("22200200293", "cli-002"),
            Map.entry("33300300380", "cli-003"),
            Map.entry("44400400476", "cli-004"),
            Map.entry("55500500562", "cli-005"),
            Map.entry("66600600659", "cli-006"),
            Map.entry("77700700745", "cli-007"),
            Map.entry("88800800831", "cli-008"),
            Map.entry("99900900928", "cli-009"),
            Map.entry("10101001061", "cli-010"),
            Map.entry("12101101130", "cli-011"),
            Map.entry("13101201292", "cli-012"));

    public Optional<String> customerId(Cpf cpf) {
        return Optional.ofNullable(customerIdPorCpf.get(cpf.digitos()));
    }
}
