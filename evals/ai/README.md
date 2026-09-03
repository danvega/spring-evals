# Spring AI evals

Evals for [Spring AI](https://spring.io/projects/spring-ai): connecting enterprise data and APIs with AI models: ChatClient, advisors, and tools.

| Eval | Type | Difficulty | What it measures, and how models fail it |
|---|---|---|---|
| [000-chatclient-basics](000-chatclient-basics) | fix | medium | Rebuilding a brittle ticket-triage flow on Spring AI's structured output support, against a stub model that returns free-form prose unless the request states the shape it needs. Models fail it by patching the string parser or hand-rolling JSON prompt text instead of letting the framework drive the response format and the mapping. |

Have an idea for one? Open a [benchmark proposal](../../../../issues/new?template=benchmark-proposal.yml) or see [CONTRIBUTING.md](../../CONTRIBUTING.md) to build it.
