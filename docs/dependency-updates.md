# Dependency updates: decisions for the open Dependabot PRs (5 Nov 2026)

The PRs were opened against the old `main` and failed there (the build did not compile on Linux). This branch applies the safe
ones on top of the current `main` in one change. Close the Dependabot PRs below once it is merged; Dependabot would otherwise
rebase them onto an identical result.

| PR | Bump | Decision | Why |
|---|---|---|---|
| 10, 12, 13 | jjwt jackson / impl / api 0.11.5 -> 0.13.0 | **Applied, with code changes** | The three artifacts must move together (now grouped in `dependabot.yml`). `JwtUtil` moved to the 0.12+ API (`subject/issuedAt/expiration`, `Jwts.SIG.HS256`, `Jwts.parser().verifyWith(key)`); `UserService` reuses `JwtUtil.parseClaims` instead of its own parser. `JwtUtilTest` (new, 10 tests) was written against 0.11.5 first and passes unchanged on 0.13.0, including fixed legacy tokens built outside the library, so tokens users already hold stay valid. |
| 7 | `org.sonarqube` Gradle plugin 5.0 -> 7.5 | **Applied** | Build and the whole test suite ran with the new plugin. Not exercised: an actual `sonar` analysis (CI does not run one yet). |
| 11 | `actions/checkout` 4 -> 7 | **Applied** | Workflow YAML valid; only CI can prove it. |
| 9 | `actions/setup-java` 4 -> 6 | **Applied** | v6.2.0 was published one day before this change; if you prefer to wait, revert that line. Only CI can prove it. |
| 14 | `docker/setup-buildx-action` 3 -> 4 | **Applied** | As above. |
| 6 | `docker/build-push-action` 6 -> 7 | **Applied** | As above (two jobs use it). |
| 8 | `springdoc-openapi-starter-webmvc-ui` 2.8.17 -> 3.2.0 | **Rejected, ignore rule added** | springdoc 3.x targets Spring Boot 4. Its CI run failed with `NoClassDefFoundError` / `ClassNotFoundException` on Boot classes, not because the PR was stale. Stays on 2.8.x until the Boot 4 migration. |
| 5 | `eclipse-temurin` 21-jre-alpine -> 25-jre-alpine | **Rejected, ignore rule added** | The build uses a Java 21 toolchain and a 21 JDK build stage; running on 25 changes the Java version for production without testing it. Move Java on purpose (build.gradle and Dockerfile together), not through a bump. |

## Changes to Dependabot's behaviour

- Gradle: the jjwt artifacts are one group; other minor/patch updates are one group; major updates of springdoc and the Spring Boot plugin are ignored.
- GitHub Actions: all updates in one weekly PR.
- Docker: major versions of `eclipse-temurin` are ignored.

## What was verified and what was not

Verified here: `./gradlew spotlessCheck test jacocoFullReport jacocoFullCoverageVerification` (224 tests), a Docker build of the image, YAML validity of the workflow and Dependabot config.
Not verified: the GitHub Actions major bumps (checkout 7, setup-java 6, setup-buildx 4, build-push 7) can only be checked by a CI run; the Boot-pinned overrides (Tomcat, Netty, BouncyCastle) were left as they are, since none of these bumps changes them.
