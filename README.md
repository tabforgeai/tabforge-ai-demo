# TabForge AI Demo

A demonstration application built with [TabForge AI](https://github.com/YOUR-USERNAME/tabforge-ai) —
showing DynTabs multi-tab UI and EasyAI assistant features running on Jakarta EE.

---

## Before You Build

### 1. Configure your AI provider

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

### 2. Groq users: import the CA certificate into your application server truststore

Application servers maintain their own SSL truststore, separate from the JDK truststore.
If you use Groq (or any other provider whose certificate is not already trusted),
you need to import its CA certificate into your application server's truststore.

**Find your truststore location** by checking the system property at runtime:

```java
System.getProperty("javax.net.ssl.trustStore")
```

On GlassFish, this is typically:
```
<glassfish-home>/glassfish/domains/domain1/config/cacerts.p12
```

**Import the certificate** using the JDK `keytool`:

```
keytool -importcert -trustcacerts
        -alias groq-ca
        -file groq-ca.pem
        -keystore <path-to-truststore>
        -storepass changeit
```

Download the Groq certificate chain from your browser (visit `api.groq.com`,
export the full certificate chain as PEM) or use `openssl`:

```
openssl s_client -connect api.groq.com:443 -showcerts
```

Restart your application server after importing.

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
