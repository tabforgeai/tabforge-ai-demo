package services;

/**
 * The provider-agnostic seam for <b>speech → text</b> (automatic speech recognition, ASR).
 *
 * <h2>Why this interface exists</h2>
 * <p>This is the voice counterpart of {@code VisionEkstraktor}: one tiny contract that the rest of the
 * app depends on, so the concrete ASR provider (today Groq Whisper) can be swapped in exactly one place
 * ({@code SpeechToTextProducer}) without any caller knowing or caring. Keeping the capability behind a
 * clean, dependency-free interface is also what makes it <em>library-ready</em> — if we later promote
 * ASR into the EasyAI library as a first-class capability, this seam lifts and shifts unchanged.</p>
 *
 * <h2>Where it sits in the voice pipeline</h2>
 * <pre>
 *   🎤 browser mic → POST /api/ai/voice (audio bytes)
 *        → THIS.transcribe(audio, mime)  → text
 *        → the SAME tab-aware assistant answers (GlobalAiAssistantBean)
 * </pre>
 * <p>The transcript then flows through the ordinary text assistant, so voice inherits the tabs, tools,
 * ambient memory and proactive layer for free — voice is a new <em>channel</em>, not a new brain.</p>
 *
 * <p><b>Analogy:</b> a court stenographer. They do one job — turn what was said into written words —
 * and hand the transcript on. They neither decide what it means nor reply; that is the judge's job
 * (the assistant).</p>
 */
public interface SpeechToText {

    /**
     * Transcribe one short spoken utterance into text.
     *
     * @param audio    the recorded audio bytes (e.g. a WebM/Opus blob from the browser's MediaRecorder)
     * @param mimeType the audio MIME type (e.g. {@code "audio/webm"}); used to name the upload part
     * @return the recognised text (may be empty if nothing intelligible was heard)
     */
    String transcribe(byte[] audio, String mimeType);
}
