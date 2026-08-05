# Ticket triage keeps breaking on real ticket text

This service triages support tickets with an AI model. `TriageService.triage(String)` takes the raw ticket text and should return a `TicketTriage` (category, priority, summary). It is half-built and it shows:

1. The model's reply is parsed by splitting on `|` and `:`. In production the replies kept drifting away from that pipe format, so the parser blew up or produced garbage. Tickets that themselves contain delimiters, like `dashboard is down | error 500 | region: eu-west`, made it even worse.
2. The prompt is one big hand-concatenated string. Our agreed triage rules live in `src/main/resources/prompts/triage-system.st`, but the service ignores that file and pastes its own half-copy of the rules inline, mixed into the same string as the ticket.
3. Nothing guarantees the reply maps onto our types. We want a typed result, not string surgery.

Rebuild the triage flow properly on the AI framework this project already uses:

- The rules in `prompts/triage-system.st` must reach the model as its standing instructions on every request, exactly as written in that file.
- The ticket text goes in the per-request message. Use the reusable template in `prompts/triage-user.st` with its `{ticket}` variable rather than concatenating strings.
- The model's reply must come back as a `TicketTriage` with category, priority, and summary populated. Have the framework drive the response format and the mapping; do not hand-parse the reply with splits or regexes.
- Any ticket text must be safe, including text full of `|` and `:` characters.

Constraints:

- Keep the `TriageService` public API as is: `triage(String)` returning `TicketTriage`. Other modules call it.
- Keep the `TicketTriage` record and its enums as they are.
- We test offline against the stub model in `com.example.triage.stub`. It mimics the production drift: unless a request spells out exactly what reply shape it needs, the reply comes back as free-form prose. Leave the stub classes and the `ai.stub` property wiring unchanged; the team relies on them.
- Do not downgrade Spring Boot or Spring AI, and do not add new model-provider dependencies.

You are done when triage returns correct typed results for ordinary tickets and for tickets containing delimiter characters, with the agreed rules and the ticket reaching the model separately.
