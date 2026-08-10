package interfaces;

import dyntabs.ai.annotation.EasyAIAssistant;

/**
 * The tiny AI assistant whose <b>only</b> job is to turn a factual briefing into one friendly suggestion
 * sentence — the single place the LLM is allowed to touch the proactive pipeline.
 *
 * <h2>Why the model's role is this small</h2>
 * <p>Proactivity's hard problems — <em>should</em> we interrupt, and <em>with what</em> — are decided by a
 * deterministic {@code services.SuggestionRule}, never here. By the time this interface is called, the
 * decision is already made and the facts are fixed; all that is left is wording. Confining the model to
 * phrasing keeps the feature cheap, predictable, and safe: it cannot invent an action, call a tool, or
 * scan the activity log. It writes one sentence, full stop.</p>
 *
 * <p>It is built with <b>no tools, no activity context, and no memory</b> (see
 * {@code services.SuggestionAdvisor}), because none of those are needed to rephrase a briefing — and
 * leaving them off makes each phrasing independent and side-effect-free.</p>
 *
 * <p><b>Familiar analogy:</b> a headline writer handed a finished, fact-checked note. They do not report,
 * investigate, or decide what runs — they craft the one line readers see. If the note says nothing, they
 * write nothing.</p>
 *
 * @see services.SuggestionAdvisor which builds this and feeds it a {@code Suggestion.phrasingBrief()}
 */
@EasyAIAssistant(systemMessage = """
        You write proactive suggestion messages for a back-office web application's AI panel.

        You are given a short factual briefing describing something the user just did and a specific offer
        of help. Rewrite it as EXACTLY ONE short, warm, natural sentence, phrased as an offer the user can
        accept or dismiss (usually a question).

        Hard rules:
        - Output only the single sentence — no greeting, no preamble, no sign-off, no quotation marks.
        - Do NOT invent any facts, order ids, amounts, names, or statuses beyond what the briefing states.
        - Keep it under about 20 words and easy to skim.
        - If the briefing does not clearly describe an offer, reply with an empty string.
        """)
public interface SuggestionPhraser {

    /**
     * Turn a factual briefing into the one sentence the user will read.
     *
     * @param briefing the {@code Suggestion.phrasingBrief()} — facts plus the offer to make, no wording
     * @return one short suggestion sentence, or an empty/blank string if nothing sensible can be said
     */
    String phrase(String briefing);
}
