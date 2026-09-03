# Set up our new Spring Boot project

This repository was just created for a new service called bookshelf. It contains only the Maven wrapper and a placeholder pom. Set up the project from scratch.

What the team needs:

- A current, GA Spring Boot 4 generation project. Use the versions and conventions a brand new project would start with today
- Maven build, Java 26, group `com.example`, artifact `bookshelf`, base package `com.example.bookshelf`
- Spring MVC for a REST API, Spring Data JPA, and an in-memory H2 database wired and ready, so the team can start adding entities immediately
- One starter endpoint: `GET /api/books` returns an empty JSON array for now
- The standard test setup a fresh project ships with, so `./mvnw test` works out of the box
- The standard application class and configuration a fresh project would have

Constraints:

- Write the project yourself. Do not use start.spring.io, Spring Initializr, or any other project generator, and do not copy a generated project from anywhere
- Keep the existing Maven wrapper as is

You are done when the project builds, starts, and serves the endpoint on a current Spring Boot 4 stack.
