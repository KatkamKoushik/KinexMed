package com.kinexmed.domain.memory

import com.kinexmed.data.repository.SessionRepository

class RehabAssistantCoordinator(
    private val sessionRepository: SessionRepository,
    private val localLlmClient: LocalRehabLlmClient? = null
) {
    private val retrievalEngine = RehabMemoryRetrievalEngine(sessionRepository)

    suspend fun processQuery(query: String): Pair<String, List<StructuredEvidenceCard>> {
        val parsed = RehabQueryParser.parse(query)
        val context = retrievalEngine.retrieve(parsed)

        // If safety violation, handle immediately via deterministic engine
        if (context.isMedicalSafetyViolation) {
            return DeterministicMemoryExplanationEngine.generateExplanation(context)
        }

        // Try local LLM if configured and enabled
        if (localLlmClient != null && context.summarySnippet.isNotBlank()) {
            val llmResponse = localLlmClient.query(query, context.summarySnippet)
            if (!llmResponse.isNullOrBlank()) {
                return Pair(llmResponse, context.evidenceCards)
            }
        }

        // Default / Offline Fallback: 100% Deterministic explanation engine
        return DeterministicMemoryExplanationEngine.generateExplanation(context)
    }
}
