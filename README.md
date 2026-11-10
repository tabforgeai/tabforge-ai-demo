# TabForge AI Demo

**▶️ [Watch the demo](https://youtu.be/qNDD9mfFEJk)** — live Activity panel: an autonomous agent's tool calls streaming into the UI in real time.

A demonstration application built with [TabForge AI](https://github.com/tabforgeai/tabforge-ai) —
showing DynTabs multi-tab UI and EasyAI assistant features running on Jakarta EE.

---

## Before You Build

### Configure your AI providers

Edit `src/main/resources/easyai.properties`. Everything else is already filled in —
you only supply **four keys**, and each one unlocks a different part of the demo:

```properties
easyai.api-key=              # Groq    — chat, the AI panel, speech-to-text
easyai.tts.api-key=          # OpenAI  — text-to-speech (server voice)
easyai.decision.api-key=     # OpenAI  — the Ticket Triage tab, EasyAI.decide()
DEEPSEEK_API_KEY=            # DeepSeek — the vision tab (read an invoice image)
```

| Key | Provider | Get one at | What stops working without it |
|---|---|---|---|
| `easyai.api-key` | **Groq** (free) | [console.groq.com](https://console.groq.com) | Everything — chat, tools, the AI panel, 🎤 voice input |
| `easyai.tts.api-key` | **OpenAI** | [platform.openai.com](https://platform.openai.com) | Nothing visibly: 🔊 falls back to the browser's own voice |
| `easyai.decision.api-key` | **OpenAI** | same key as above | The **Ticket Triage** tab shows a configuration note instead of results |
| `DEEPSEEK_API_KEY` | **DeepSeek** | [platform.deepseek.com](https://platform.deepseek.com) | The vision tab cannot read an attached invoice |

Only the first is required to see the demo run. The other three each disable one
feature, with a readable message rather than an error.

**Why four providers?** That is the point, not an accident. One `easyai.properties`
drives four capabilities across three services — each capability can name its own
provider, and anything it does not name is inherited from the chat settings. Note
also that `easyai.decision.api-key` *must* be OpenAI: a decision model is a dedicated
endpoint (`POST {baseUrl}/decisions`), which Groq and DeepSeek do not implement.

Every key is documented, with its default and what inherits from what, in
[easyai_properties_guide.txt](https://github.com/tabforgeai/tabforge-ai/blob/main/docs/easyai_properties_guide.txt).

> ⚠️ Never commit a real key. Keep the values empty in git and fill them in locally.

---

## Build and Deploy

```
mvn clean package
```

Deploy `target/tabforge_ai_demo_1.0.war` to your Jakarta EE 11 application server
(GlassFish 8, Payara 7, WildFly with EE 11 support).

---

## Requirements

- Java 21+
- Jakarta EE 11 application server
- PrimeFaces 16.0.1 (bundled — see pom.xml; PrimeFaces 16 dropped the `javax` artifacts,
  so the dependency no longer takes `<classifier>jakarta</classifier>`)
- At minimum a Groq key; the three optional keys above each unlock one more feature
