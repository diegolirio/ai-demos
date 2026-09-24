package poc.a2a.ana.cliente;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/** Cadastro mock CPF -> customerId (CPFs de teste ficticios, com digitos verificadores validos). */
@Component
public class CadastroClientes {

    private final Map<String, String> customerIdPorCpf = Map.of(
            "11100100105", "cli-001",
            "22200200293", "cli-002",
            "33300300380", "cli-003",
            "44400400476", "cli-004",
            "55500500562", "cli-005",
            "66600600659", "cli-006",
            "77700700745", "cli-007",
            "88800800831", "cli-008");

    public Optional<String> customerId(Cpf cpf) {
        return Optional.ofNullable(customerIdPorCpf.get(cpf.digitos()));
    }
}
