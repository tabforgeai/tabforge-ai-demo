# TabForge AI Demo

**▶️ [Watch the demo](https://youtu.be/qNDD9mfFEJk)** — live Activity panel: an autonomous agent's tool calls streaming into the UI in real time.

A demonstration application built with [TabForge AI](https://github.com/tabforgeai/tabforge-ai) —
showing DynTabs multi-tab UI and EasyAI assistant features running on Jakarta EE.

---

## Before You Build

### Configure your AI provider

Edit `src/main/resources/easyai.properties` and add your API key:

```properties
easyai.provider=openai
easyai.api-key=YOUR-API-KEY-HERE
easyai.model-name=gpt-4o-mini
```

**Using Groq** (free, OpenAI-compatible):

```properties
easyai.provider=openai
easyai.api-key=YOUR-GROQ-API-KEY-HERE
easyai.base-url=https://api.groq.com/openai/v1/
easyai.model-name=llama-3.3-70b-versatile
```

Get a free Groq API key at [console.groq.com](https://console.groq.com).

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
- PrimeFaces 13+ (provided by the server or bundled — see pom.xml)
- A valid API key for OpenAI, Groq, or a compatible provider
