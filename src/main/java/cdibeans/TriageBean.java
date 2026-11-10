package cdibeans;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dyntabs.BaseDyntabCdiBean;
import dyntabs.ai.EasyAI;
import dyntabs.ai.decision.Decisions;
import dyntabs.annotation.DynTab;
import dyntabs.scope.TabScoped;
import jakarta.inject.Named;

/**
 * The <b>{@code EasyAI.decide()}</b> showcase: a support ticket in, three judgements out — as
 * <em>numbers</em>.
 *
 * <h2>What this tab is arguing</h2>
 * <p>Triage is normally done with a full chat completion and a structured-output prompt. That is
 * slow, costs a whole generation, and hands back a <em>word</em> ({@code "urgent"}) when what the
 * code needed was a <em>number it can threshold</em>. {@code decide()} returns the number, and all
 * three questions ride in <b>one</b> small call:</p>
 *
 * <pre>{@code
 * Decisions d = EasyAI.decide(ticketText)
 *     .yesNo("urgent",   "Is the customer completely blocked from working?")
 *     .choice("team",    "Which team should handle this?", "billing", "technical", "sales")
 *     .scale("severity", "How severe is the business impact?", "low", "medium", "high")
 *     .ask();
 * }</pre>
 *
 * <h2>The two things the page is meant to make obvious</h2>
 * <ol>
 *   <li><b>The threshold is ours, not the model's.</b> {@link #URGENT_THRESHOLD} is a plain Java
 *       constant, shown on the page next to the measured probability. There is deliberately no
 *       {@code isYes(question)} without a threshold in the API — a probability only becomes a
 *       decision at a line a human chose.</li>
 *   <li><b>"Too close to call" is a real answer.</b> {@code margin()} is the gap between the winning
 *       option and the runner-up. Below {@link #HUMAN_REVIEW_MARGIN} this page says so instead of
 *       routing, which is exactly what a support desk should do.</li>
 * </ol>
 *
 * <h2>Provider note</h2>
 * <p>A decision model is a <b>dedicated endpoint</b>, not a chat completion — OpenAI serves it at
 * {@code POST {baseUrl}/decisions}. The chat providers this demo otherwise uses (Groq for chat and
 * speech-to-text, DeepSeek for vision) do not implement it, so this tab needs
 * {@code easyai.decision.api-key} pointed at OpenAI. Without it the page shows the library's own
 * error, which names the property to set.</p>
 *
 * <p><b>Analogy:</b> a thermometer, not a doctor. It reports 38.7 °C. What counts as a fever, and
 * what to do about it, stays with the person who has to answer for the decision.</p>
 */
@Named
@TabScoped
@DynTab(name = "TriageDynTab", uniqueIdentifier = "Triage", title = "Ticket Triage - decide()",
        includePage = "/WEB-INF/include/triage/triage.xhtml", closeable = true, trackActivity = true)
public class TriageBean extends BaseDyntabCdiBean {

    private static final Logger log = LoggerFactory.getLogger(TriageBean.class);

    /** Our line for "wake somebody". A business judgement, in Java, visible to a reviewer. */
    public static final double URGENT_THRESHOLD = 0.80;

    /** Below this gap between the top two options, nobody should be routing automatically. */
    public static final double HUMAN_REVIEW_MARGIN = 0.15;

    private static final List<String> TEAMS = List.of("billing", "technical", "sales");
    private static final List<String> LEVELS = List.of("low", "medium", "high");

    private String ticketText = "Već treći dan mi ne prolaze isplate, niko ne odgovara na mejlove. "
            + "Ovo nas košta svaki dan i razmišljamo da pređemo kod konkurencije.";

    private boolean answered;
    private String error;

    private double urgentProbability;
    private boolean urgent;
    private String team;
    private Double teamMargin;          // null when the model reported no probabilities
    private List<Bar> teamBars = new ArrayList<>();
    private double severityMean;
    private String severityLabel;
    private String summary;

    /**
     * Ask the three questions in one call and unpack the answers for the page.
     *
     * <p>Everything below the {@code ask()} is plain Java deciding on plain numbers — which is the
     * whole point of the tab.</p>
     */
    public void triage() {
        answered = false;
        error = null;
        teamBars = new ArrayList<>();
        try {
            Decisions d = EasyAI.decide(ticketText)
                    .yesNo("urgent", "Is the customer completely blocked from working?",
                            "the customer cannot transact at all",
                            "there is a workaround, or it is a question")
                    .choice("team", "Which team should handle this ticket?", TEAMS)
                    .scale("severity", "How severe is the business impact?",
                            "low", "medium", "high")
                    .ask();

            urgentProbability = d.probability("urgent");
            urgent = d.isYes("urgent", URGENT_THRESHOLD);   // the threshold is OURS

            team = d.choice("team");
            teamMargin = d.margin("team").isPresent() ? d.margin("team").getAsDouble() : null;
            Map<String, Double> probabilities = d.probabilities("team");
            for (String option : TEAMS) {
                Double p = probabilities.get(option);
                teamBars.add(new Bar(option, p == null ? 0.0 : p, option.equals(team)));
            }

            severityMean = d.mean("severity");
            severityLabel = describeSeverity(severityMean);
            summary = d.summary();
            answered = true;
            log.info("Triage: {}", summary);
        } catch (Exception ex) {
            error = EasyAI.extractErrorMessage(ex);
            log.warn("Triage failed: {}", ex.getMessage());
        }
    }

    /**
     * Turn the weighted index into words without throwing the in-between value away.
     *
     * @param mean the scale answer, 0.0 to {@code LEVELS.size() - 1}
     * @return e.g. {@code "between medium and high (1.40)"}
     */
    private String describeSeverity(double mean) {
        int lower = (int) Math.floor(mean);
        int upper = (int) Math.ceil(mean);
        lower = Math.max(0, Math.min(LEVELS.size() - 1, lower));
        upper = Math.max(0, Math.min(LEVELS.size() - 1, upper));
        String value = String.format(java.util.Locale.ROOT, "%.2f", mean);
        if (lower == upper) {
            return LEVELS.get(lower) + " (" + value + ")";
        }
        return "between " + LEVELS.get(lower) + " and " + LEVELS.get(upper) + " (" + value + ")";
    }

    /** @return {@code true} when the top two options are too close to route automatically. */
    public boolean isTooCloseToCall() {
        return teamMargin != null && teamMargin < HUMAN_REVIEW_MARGIN;
    }

    /** @return the measured probability as a whole percentage, for the bar width. */
    public int getUrgentPercent() {
        return (int) Math.round(urgentProbability * 100);
    }

    /** @return the threshold as a whole percentage, for the marker on the bar. */
    public int getUrgentThresholdPercent() {
        return (int) Math.round(URGENT_THRESHOLD * 100);
    }

    /** @return the severity position as a percentage across the levels. */
    public int getSeverityPercent() {
        return (int) Math.round(severityMean / (LEVELS.size() - 1) * 100);
    }

    // ------------------------------------------------------------------ getters / setters

    public String getTicketText() {
        return ticketText;
    }

    public void setTicketText(String ticketText) {
        this.ticketText = ticketText;
    }

    public boolean isAnswered() {
        return answered;
    }

    public String getError() {
        return error;
    }

    public double getUrgentProbability() {
        return urgentProbability;
    }

    public boolean isUrgent() {
        return urgent;
    }

    public double getUrgentThreshold() {
        return URGENT_THRESHOLD;
    }

    public String getTeam() {
        return team;
    }

    public Double getTeamMargin() {
        return teamMargin;
    }

    public List<Bar> getTeamBars() {
        return teamBars;
    }

    public String getSeverityLabel() {
        return severityLabel;
    }

    public List<String> getLevels() {
        return LEVELS;
    }

    public String getSummary() {
        return summary;
    }

    /** One option of the choice question, ready to draw as a bar. */
    public static class Bar {

        private final String label;
        private final double probability;
        private final boolean winner;

        Bar(String label, double probability, boolean winner) {
            this.label = label;
            this.probability = probability;
            this.winner = winner;
        }

        public String getLabel() {
            return label;
        }

        public double getProbability() {
            return probability;
        }

        public boolean isWinner() {
            return winner;
        }

        /** @return the probability as a whole percentage, for the bar width. */
        public int getPercent() {
            return (int) Math.round(probability * 100);
        }
    }
}
