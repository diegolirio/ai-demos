package poc.a2a.ana.atendimento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import poc.a2a.ana.investimentos.RespostaInvestimentos;
import poc.a2a.ana.investimentos.SituacaoGarantia;

/** Historico no Postgres (schema do currentSchema da URL). Cria a tabela no startup, como o SQLChatMemoryStore. */
public class JdbcHistoricoAtendimentos implements HistoricoAtendimentos {

    private static final String CRIAR_TABELA = """
            CREATE TABLE IF NOT EXISTS atendimento (
              id                       BIGSERIAL PRIMARY KEY,
              customer_id              TEXT         NOT NULL,
              session_id               TEXT         NOT NULL,
              criado_em                TIMESTAMPTZ  NOT NULL DEFAULT now(),
              resumo                   TEXT         NOT NULL,
              confidence               NUMERIC(3,2) NOT NULL,
              garantia_status          TEXT,
              garantia_valor_resgatado NUMERIC(15,2),
              garantia_valor_retido    NUMERIC(15,2),
              garantia_valor_liberado  NUMERIC(15,2),
              garantia_proximo_passo   TEXT
            )""";

    private static final String CRIAR_INDICE =
            "CREATE INDEX IF NOT EXISTS atendimento_customer_criado ON atendimento (customer_id, criado_em DESC)";

    private static final String INSERIR = """
            INSERT INTO atendimento (customer_id, session_id, resumo, confidence, garantia_status,
              garantia_valor_resgatado, garantia_valor_retido, garantia_valor_liberado, garantia_proximo_passo)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private static final String RECENTES = """
            SELECT criado_em, resumo, confidence, garantia_status, garantia_valor_resgatado,
                   garantia_valor_retido, garantia_valor_liberado, garantia_proximo_passo
              FROM atendimento
             WHERE customer_id = ? AND session_id <> ?
             ORDER BY criado_em DESC, id DESC
             LIMIT ?""";

    private final DataSource dataSource;

    public JdbcHistoricoAtendimentos(DataSource dataSource) {
        this.dataSource = dataSource;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(CRIAR_TABELA);
            statement.execute(CRIAR_INDICE);
        } catch (SQLException e) {
            throw new IllegalStateException("Nao foi possivel criar a tabela atendimento", e);
        }
    }

    @Override
    public void registrar(String customerId, String sessionId, RespostaInvestimentos resposta) {
        SituacaoGarantia g = resposta.situacaoGarantia();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERIR)) {
            statement.setString(1, customerId);
            statement.setString(2, sessionId);
            statement.setString(3, resposta.answerDraft());
            statement.setBigDecimal(4, BigDecimal.valueOf(resposta.confidence()).setScale(2, RoundingMode.HALF_UP));
            statement.setString(5, g == null ? null : g.status());
            statement.setBigDecimal(6, g == null ? null : g.valorResgatado());
            statement.setBigDecimal(7, g == null ? null : g.valorRetido());
            statement.setBigDecimal(8, g == null ? null : g.valorLiberado());
            statement.setString(9, g == null ? null : g.proximoPasso());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao registrar atendimento", e);
        }
    }

    @Override
    public List<Atendimento> recentesDeOutrasSessoes(String customerId, String sessionIdAtual, int limite) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(RECENTES)) {
            statement.setString(1, customerId);
            statement.setString(2, sessionIdAtual);
            statement.setInt(3, limite);
            List<Atendimento> atendimentos = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("garantia_status");
                    SituacaoGarantia g = status == null ? null : new SituacaoGarantia(status,
                            rs.getBigDecimal("garantia_valor_resgatado"), rs.getBigDecimal("garantia_valor_retido"),
                            rs.getBigDecimal("garantia_valor_liberado"), rs.getString("garantia_proximo_passo"));
                    atendimentos.add(new Atendimento(rs.getObject("criado_em", OffsetDateTime.class),
                            rs.getString("resumo"), rs.getBigDecimal("confidence").doubleValue(), g));
                }
            }
            return atendimentos;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao consultar atendimentos", e);
        }
    }
}
