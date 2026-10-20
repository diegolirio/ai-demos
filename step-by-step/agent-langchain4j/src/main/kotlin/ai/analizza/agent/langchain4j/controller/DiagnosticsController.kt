package ai.analizza.agent.langchain4j.controller

import dev.langchain4j.agentic.observability.AgentMonitor
import dev.langchain4j.agentic.observability.HtmlReportGenerator
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DiagnosticsController(private val agentMonitor: AgentMonitor) {

    @GetMapping("/diagnostics/report", produces = ["text/html;charset=UTF-8"])
    fun report(): String {
        return if (agentMonitor.allMemoryIds().isEmpty()) {
            EMPTY_REPORT
        } else {
            HtmlReportGenerator.generateReport(agentMonitor)
        }
    }

    private companion object {
        const val EMPTY_REPORT = """<!doctype html>
                <html lang="pt-BR">
                <head><meta charset="UTF-8"><title>Relatório de agentes</title></head>
                <body><p>Nenhuma execução registrada ainda.</p></body>
                </html>"""
    }
}