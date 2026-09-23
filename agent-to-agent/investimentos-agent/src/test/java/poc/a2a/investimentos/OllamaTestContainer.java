package poc.a2a.investimentos;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import com.github.dockerjava.api.model.Image;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * LLM real dos testes de integracao: Ollama em container, singleton por JVM (mesmo estilo do Postgres do
 * {@link BaseIntegrationTest}: iniciado de forma eager, sem @Container/@Testcontainers/withReuse).
 *
 * <p>Para nao baixar o modelo (~2GB) a cada execucao, segue o padrao documentado do Testcontainers: na primeira
 * vez sobe a imagem base, faz {@code ollama pull <modelo>} e grava o container como a imagem local
 * {@code tc-ollama-<modelo>} ({@link OllamaContainer#commitToImage(String)}); nas proximas, sobe direto dessa
 * imagem. Para refazer o cache: {@code docker rmi tc-ollama-<modelo>}.
 *
 * <p>Modelo: {@code qwen2.5:3b} (faz tool calling e nao emite tags de "thinking"); troque com a system
 * property ou variavel de ambiente {@code IT_OLLAMA_MODEL}.
 */
final class OllamaTestContainer {

    static final String IMAGEM_OLLAMA = "ollama/ollama:0.34.3";
    static final String MODELO_PADRAO = "qwen2.5:3b";
    static final String MODELO = modelo();
    static final OllamaContainer OLLAMA = iniciar();

    private OllamaTestContainer() {
    }

    /** URL OpenAI-compatible consumida pelo OpenAiChatModel (propriedade llm.base-url). */
    static String baseUrlOpenAi() {
        return OLLAMA.getEndpoint() + "/v1";
    }

    private static String modelo() {
        String modelo = System.getProperty("IT_OLLAMA_MODEL", System.getenv("IT_OLLAMA_MODEL"));
        return modelo == null || modelo.isBlank() ? MODELO_PADRAO : modelo.trim();
    }

    private static OllamaContainer iniciar() {
        String imagemComModelo = "tc-ollama-" + MODELO.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        List<Image> cache = DockerClientFactory.instance().client()
                .listImagesCmd()
                .withReferenceFilter(imagemComModelo)
                .exec();
        boolean temCache = !cache.isEmpty();

        OllamaContainer container = temCache
                ? new OllamaContainer(DockerImageName.parse(imagemComModelo).asCompatibleSubstituteFor("ollama/ollama"))
                : new OllamaContainer(IMAGEM_OLLAMA);
        container.start();
        if (!temCache) {
            baixarModelo(container);
            container.commitToImage(imagemComModelo);
        }
        return container;
    }

    private static void baixarModelo(OllamaContainer container) {
        try {
            ExecResult pull = container.execInContainer("ollama", "pull", MODELO);
            if (pull.getExitCode() != 0) {
                throw new IllegalStateException("ollama pull " + MODELO + " falhou: " + pull.getStderr());
            }
        } catch (IOException e) {
            throw new IllegalStateException("ollama pull " + MODELO + " falhou", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ollama pull " + MODELO + " interrompido", e);
        }
    }
}
