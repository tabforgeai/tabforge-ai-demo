package services;

/**
 * <h2>VisionEkstraktor — the assistant's "eye": an image turns into facts</h2>
 *
 * <p><b>What it does:</b> takes the raw bytes of a picture (an invoice photo, a scanned form,
 * a screenshot) plus a short instruction, and returns what the picture <em>says</em> as plain
 * text or JSON. Nothing more — it does not reason, compare, or act. It only <em>reads</em>.</p>
 *
 * <h3>Why this is an interface (the provider-agnostic seam)</h3>
 * <p>Reading an image is done by a vision-capable LLM, and those come and go (DeepSeek today,
 * someone cheaper tomorrow). By hiding that behind one tiny interface, the rest of the demo never
 * learns <em>who</em> read the image — swapping the provider is swapping one class
 * ({@link DeepSeekVision} → whatever), not touching the endpoint or the assistant. This is the
 * same "universal remote" idea the library preaches: the socket stays, the device behind it may
 * change.</p>
 *
 * <h3>Where it sits in the flow (TWO deliberate steps)</h3>
 * <pre>
 *   image bytes ──▶ VisionEkstraktor.procitaj(...)  ──▶ facts (text/JSON)   ← STEP 1: SEE (this)
 *                                                           │
 *                                                           ▼
 *                       GlobalAiAssistantBean.ask(session, text+facts)      ← STEP 2: THINK + ACT
 *                       (tab-aware tools, the other tab, proactive chip)
 * </pre>
 * <p>Keeping "see" separate from "think" is on purpose: a cheap, fast vision model extracts the
 * cold facts, and the <em>existing</em> tab-aware assistant does the clever part (compare with the
 * purchase order in the other tab, offer to file a claim). The image never touches the library's
 * language model — only the extracted text does.</p>
 *
 * <p><b>Analogy:</b> a receptionist who reads a delivered letter aloud and hands you the key
 * numbers. They do not decide what to do about it — that is the manager's job (the assistant).</p>
 */
public interface VisionEkstraktor {

    /**
     * Read a single image and return what it contains, following {@code uputstvo}.
     *
     * @param slika     the raw image bytes exactly as they arrived from the browser (no re-encoding)
     * @param mimeType  the image's MIME type, e.g. {@code "image/jpeg"} (a phone photo) or
     *                  {@code "image/png"} (a screenshot). The provider only accepts jpeg/png/gif/webp.
     * @param uputstvo  what to extract, in plain language — e.g.
     *                  {@code "Return JSON: {iznos, valuta, stavke:[{naziv,iznos}]}"}. The caller
     *                  (the endpoint) owns this wording so the extractor stays generic.
     * @return the model's answer as a string (free text or a JSON blob, per {@code uputstvo})
     * @throws RuntimeException if the provider is not configured (missing key) or the call fails;
     *                          the REST layer turns this into a friendly sentence for the panel.
     */
    String procitaj(byte[] slika, String mimeType, String uputstvo);
}
