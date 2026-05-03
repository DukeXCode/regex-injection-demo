# Regex Injection (ReDoS) Demo

A small Ktor service that demonstrates a **Regular Expression Denial of Service (ReDoS)** vulnerability, also known as a *regex injection* against a server-side validator. The repo contains:

- `src/main/kotlin/ch/dukex/plugins/Routing.kt` — a Ktor server with a vulnerable `/email` endpoint.
- `script.py` — an attack script that sends crafted input to bring the server to its knees.

## What is a regex injection / ReDoS?

A *regex injection* attack happens when user-controlled input is fed into a regular expression engine that uses **backtracking** and the pattern contains *ambiguous quantifiers* (e.g. `(a+)+`, `(a|a)+`, or repeated character classes that overlap). For certain crafted inputs, the engine has to explore an exponential (or high-polynomial) number of ways to match the string before it finally gives up.

The result: a single small HTTP request can pin a CPU core for seconds, minutes, or longer. Send a handful in parallel and the server stops answering legitimate traffic — that's the *Denial of Service* part.

ReDoS is dangerous because:

- The regex *looks* harmless. Most email/URL/whitespace regexes copied from Stack Overflow are vulnerable.
- It bypasses normal rate limits — the attacker doesn't need volume, they need one expensive request.
- Many languages (Java, JavaScript, Python, .NET, Ruby) ship with backtracking engines by default.

## How this project demonstrates it

### The vulnerable endpoint

`Routing.kt` validates that the request body contains an email address using this regex:

```kotlin
val regex = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}""")
if (regex.containsMatchIn(receivedEmail)) { ... }
```

> **This isn't a hand-picked "gotcha" regex.** It's literally the first result I got from ChatGPT when I asked for an email validation regex. I didn't have to hunt for something vulnerable — the default suggestion from a popular LLM was already exploitable. That's the whole point: ReDoS happens by accident, all the time, in code that looks completely reasonable.

The pattern looks fine, but it has two overlapping greedy quantifiers separated by characters that *can also appear inside both classes*:

- `[A-Za-z0-9._%+-]+` — matches `.` and `-`
- `[A-Za-z0-9.-]+`    — also matches `.` and `-`
- `\.[A-Z|a-z]{2,}`   — anchors a TLD at the end

When the input is a long run of `.` characters with **no `@`** and **no valid TLD** at the end, the engine tries every possible split between the two `+` groups looking for an `@` that never comes, and then re-tries with a different TLD boundary. That's classic catastrophic backtracking.

### The attack

`script.py` builds the malicious payload:

```python
injection = '.' * 54773 + '-.A|'
requests.post("http://localhost:8080/email", injection)
```

A string of ~55,000 dots followed by `-.A|`. Then it fires **150 of them in parallel** through a 50-worker thread pool:

```python
with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
    for i in range(1, total_requests + 1):
        executor.submit(send_email_req)
```

While the attack is running, try `GET /check` — a trivial endpoint that just returns `OK`. You'll see it become unresponsive too, because every Netty worker thread is busy backtracking inside the regex engine.

### Run it yourself

**Requirements:** JDK 21 is the supported runtime. The Gradle build is pinned to a JDK 21 toolchain (`kotlin { jvmToolchain(21) }` in `build.gradle.kts`) because Kotlin 2.1.20 doesn't yet target JDK 25 bytecode and the mismatch breaks the build. If your `JAVA_HOME` points at a newer JDK (24/25), Gradle's toolchain auto-provisioning will download/use a JDK 21 just for compilation — but the simplest path is to install JDK 21 (e.g. Temurin 21) and run with that.

Start the server:

```bash
./gradlew run
```

In another terminal, launch the attack:

```bash
python script.py
```

Watch a single `/email` request take many seconds — and `/check` stop responding promptly — even though the input is just a few kilobytes.

## How to prevent regex injection / ReDoS

1. **Anchor your patterns and bound your quantifiers.** Replace `+` and `*` with `{1,N}` and anchor with `^…$` so the engine can fail fast. The safe email regex below is the same idea as the vulnerable one but anchored, length-capped, and uses a lookahead to bound the domain *before* doing the greedy match:

   ```regex
   ^[A-Za-z0-9._%+-]{1,64}@(?=[A-Za-z0-9.-]{1,253}$)[A-Za-z0-9.-]{1,253}\.[A-Za-z]{2,6}$
   ```

2. **Validate input length first.** Reject requests where the field is longer than your domain expects (e.g. 254 chars for an email per RFC 5321) *before* it ever hits the regex.

3. **Avoid ambiguous alternation and nested quantifiers.** Patterns like `(a+)+`, `(a|a)+`, `(.*)*`, or two adjacent greedy classes that share characters are red flags. Make character classes mutually exclusive where possible.

4. **Use a non-backtracking engine for untrusted input.** Google's [RE2](https://github.com/google/re2) (and its JVM port `re2j`) guarantees linear-time matching. On the JVM you can drop `re2j` in for hostile input and keep `java.util.regex` for trusted patterns.

5. **Set a timeout on regex evaluation.** Wrap matches in a coroutine/thread with a short deadline so a pathological input is killed instead of blocking a worker.

6. **Don't build regexes from user input.** If a user can influence the *pattern* (not just the subject), they can hand-craft catastrophic backtracking on purpose. Treat regex strings as code.

7. **Scan your patterns.** Run existing regexes through a ReDoS analyzer (see links below) as part of CI.

## Useful Links

- [Devina ReDoS checker](https://devina.io/redos-checker)
- [Hacksplaining — Regex Injection](https://hacksplaining.com/prevention/regex-injection)
- [OWASP — Regular Expression Denial of Service (ReDoS)](https://owasp.org/www-community/attacks/Regular_expression_Denial_of_Service_-_ReDoS)
- [Google RE2](https://github.com/google/re2) / [re2j](https://github.com/google/re2j)
