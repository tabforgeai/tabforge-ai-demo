package services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import api.AgentEvent;
import api.AiEventChannel;
import dyntabs.ai.EasyAI;
import dyntabs.ai.activity.ActivityStore;
import dyntabs.ai.activity.UserActivityEvent;
import interfaces.SuggestionPhraser;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * The engine that turns a user's recent activity into an unprompted, dismissible suggestion in the AI
 * panel — the middle of the proactive pipeline <b>SENSE → DECIDE → PHRASE → PUSH</b>.
 *
 * <h2>The pipeline, and which part is fast vs. slow</h2>
 * <ol>
 *   <li><b>SENSE</b> — the app already records what the user does into the {@link ActivityStore}
 *       (opening an order, etc.). Nothing new here.</li>
 *   <li><b>DECIDE</b> — {@link #onOrderViewed(String)} is poked right after an activity is recorded. It
 *       reads the recent window and runs every {@link SuggestionRule}. This is deterministic and cheap,
 *       so it runs <em>synchronously</em> on the caller's thread and must not block the user's click.</li>
 *   <li><b>PHRASE</b> — only if a rule fires: an LLM ({@link SuggestionPhraser}) turns the rule's factual
 *       briefing into one sentence. This is slow, so it runs on a {@link ManagedExecutorService} thread,
 *       off the click path.</li>
 *   <li><b>PUSH</b> — the phrased suggestion is emitted over the already-open SSE channel
 *       ({@link AiEventChannel}); the browser, which is always listening, renders it as a chip. The
 *       server "speaks first" over a connection the client opened for the opposite direction.</li>
 * </ol>
 *
 * <h2>Not annoying by construction</h2>
 * <p>Every suggestion carries a cooldown {@code key}. Firing one starts a {@link #COOLDOWN}; a
 * user <b>dismiss</b> starts a much longer {@link #MUTE}; an <b>accept</b> just restarts the cooldown. At
 * most one suggestion is pushed per trigger. So the same nag cannot repeat back-to-back, and a user who
 * says "not now" is left alone for a good while. See {@link #accepted(String)} / {@link #dismissed(String)}.</p>
 *
 * <h2>Why the phraser is built on the caller's thread but called on a background thread</h2>
 * <p>Building an EasyAI assistant may touch CDI (config lookup), which is reliable on the JSF/CDI request
 * thread that pokes us but not necessarily on a pooled executor thread. So we build (and cache) the
 * phraser here, synchronously, and hand the ready proxy to the background task, which only <em>invokes</em>
 * it — a plain HTTP call to the model that needs no CDI context.</p>
 *
 * <p><b>Familiar analogy:</b> a good concierge. They quietly notice what you are doing (sense), apply a
 * fixed sense of when it is worth a word (decide), think for a moment about how to phrase it (phrase),
 * and then lean over and offer — once, politely, and not again if you wave them off (push + cooldown).</p>
 *
 * @see SuggestionRule the deterministic detectors this runs
 * @see SuggestionPhraser the LLM that only writes the sentence
 * @see AiEventChannel the SSE channel the suggestion is pushed over
 */
@ApplicationScoped
public class SuggestionAdvisor {

    private static final Logger log = LoggerFactory.getLogger(SuggestionAdvisor.class);

    /** After a suggestion fires (or is accepted), suppress the same key for this long. */
    private static final Duration COOLDOWN = Duration.ofMinutes(3);

    /** After the user dismisses a suggestion, suppress the same key for this much longer. */
    private static final Duration MUTE = Duration.ofMinutes(30);

    /** All detector rules the container found — "add proactivity" = add another {@link SuggestionRule} bean. */
    @Inject
    private Instance<SuggestionRule> rules;

    /** The session-scoped ambient timeline the rules read from. */
    @Inject
    private ActivityStore activityStore;

    /** The SSE registry used to push the finished suggestion to the user's browser. */
    @Inject
    private AiEventChannel channel;

    /** Container-managed thread pool for the slow phrasing step, so the user's click is never blocked. */
    @Resource
    private ManagedExecutorService executor;

    /** key → instant until which that key is suppressed (cooldown or mute). */
    private final ConcurrentMap<String, Instant> suppressedUntil = new ConcurrentHashMap<>();

    /** Lazily built, then reused; no tools/context/memory — it only rephrases briefings. */
    private volatile SuggestionPhraser phraser;

    /**
     * Proactive trigger: called right after the user views an order (see {@code OrdersBean.openOrder}).
     *
     * <p>Runs every rule against the session's recent activity. On the first rule that fires (and is not
     * currently suppressed) it reserves the cooldown immediately — so a slow phrasing step cannot let a
     * second identical suggestion slip through — then dispatches the phrase-and-push to a background
     * thread. At most one suggestion is pushed per call. Never throws: proactivity must not break the
     * click that triggered it.</p>
     *
     * @param sessionId the user's HTTP session id (scopes both the activity read and the SSE push);
     *                  a {@code null}/blank id is a no-op
     */
    public void onOrderViewed(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try {
            List<UserActivityEvent> recent = activityStore.recent(sessionId, null, 50);
            for (SuggestionRule rule : rules) {
                Optional<Suggestion> hit;
                try {
                    hit = rule.evaluate(recent);
                } catch (RuntimeException ex) {
                    log.warn("Suggestion rule {} threw — skipping it", rule.getClass().getSimpleName(), ex);
                    continue;
                }
                if (hit.isEmpty()) {
                    continue;
                }
                Suggestion s = hit.get();
                String sk = suppressKey(sessionId, s.key());
                if (isSuppressed(sk)) {
                    log.debug("Suggestion '{}' suppressed (cooldown/mute) for session {} — not firing",
                            s.key(), sessionId);
                    continue;
                }
                // Reserve the cooldown now, before the async phrasing, so we cannot double-fire.
                suppress(sk, COOLDOWN);
                dispatch(sessionId, s, phraser());
                return; // one suggestion per trigger
            }
        } catch (RuntimeException ex) {
            // Belt and braces: a proactive nicety must never surface as an error on the user's action.
            log.warn("SuggestionAdvisor.onOrderViewed failed for session {}", sessionId, ex);
        }
    }

    /**
     * Record that the user accepted a suggestion — restart its cooldown so it does not immediately reappear.
     *
     * <p>The actual work of an accepted suggestion happens client-side (its prompt is injected into the
     * chat), so this method only manages suppression. Suppression is <b>per session</b>: accepting for one
     * user never silences the same suggestion for another.</p>
     *
     * @param sessionId the user's session (namespaces the suppression so it is per-user)
     * @param key       the suggestion's cooldown key (echoed back from the pushed event)
     */
    public void accepted(String sessionId, String key) {
        if (sessionId != null && key != null && !key.isBlank()) {
            suppress(suppressKey(sessionId, key), COOLDOWN);
            log.debug("Suggestion '{}' accepted (session {}) — cooldown restarted", key, sessionId);
        }
    }

    /**
     * Record that the user dismissed a suggestion — mute this key (for this session) for a good while so
     * we stop nagging.
     *
     * @param sessionId the user's session (namespaces the suppression so it is per-user)
     * @param key       the suggestion's cooldown key (echoed back from the pushed event)
     */
    public void dismissed(String sessionId, String key) {
        if (sessionId != null && key != null && !key.isBlank()) {
            suppress(suppressKey(sessionId, key), MUTE);
            log.debug("Suggestion '{}' dismissed (session {}) — muted for {}", key, sessionId, MUTE);
        }
    }

    // ── internals ───────────────────────────────────────────────────────────

    /** Phrase the suggestion on a background thread, then push it over SSE. */
    private void dispatch(String sessionId, Suggestion s, SuggestionPhraser p) {
        executor.execute(() -> {
            try {
                String text = p.phrase(s.phrasingBrief());
                if (text == null || text.isBlank()) {
                    log.debug("Phraser returned nothing for '{}' — not pushing", s.key());
                    return;
                }
                channel.emit(sessionId,
                        AgentEvent.suggestion(s.actionId(), text.trim(), s.promptForAccept(), s.key()));
                log.debug("Pushed suggestion '{}' to session {}", s.key(), sessionId);
            } catch (RuntimeException ex) {
                log.warn("Failed to phrase/push suggestion '{}'", s.key(), ex);
            }
        });
    }

    private boolean isSuppressed(String key) {
        Instant until = suppressedUntil.get(key);
        return until != null && Instant.now().isBefore(until);
    }

    private void suppress(String key, Duration duration) {
        suppressedUntil.put(key, Instant.now().plus(duration));
    }

    /**
     * Namespace a rule's cooldown key by session, so suppression is per-user. Without this, the advisor
     * being {@code @ApplicationScoped} would make one user's suggestion silence everyone else's (and it
     * would survive logout/login until the cooldown/mute expired).
     */
    private String suppressKey(String sessionId, String ruleKey) {
        return sessionId + '|' + ruleKey;
    }

    /** Build the phraser once (on the CDI-active caller thread), then reuse it. */
    private SuggestionPhraser phraser() {
        SuggestionPhraser p = phraser;
        if (p == null) {
            synchronized (this) {
                p = phraser;
                if (p == null) {
                    // No tools, no activity context, no memory: it only rephrases the briefing.
                    p = EasyAI.assistant(SuggestionPhraser.class).withMemory(0).build();
                    phraser = p;
                }
            }
        }
        return p;
    }
}
