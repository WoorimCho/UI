# UI — Allergen Information System front end

Server-rendered Thymeleaf front end. Holds **no data and no business logic** —
every page is a call to a catalogue, the User service, or the BFF, rendered.

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen)
![Java](https://img.shields.io/badge/Java-17-orange)
![Build](https://img.shields.io/badge/build-Maven-blue)
![Port](https://img.shields.io/badge/port-8081-lightgrey)

## What it talks to

| Downstream | Used for |
|---|---|
| IngredientCatalogue `:8082` | ingredients, tags, nutrition, nutrition-reference, CSV import |
| RecipeCatalogue `:8083` | recipes, steps/tools, structured quantities, random, CSV import |
| User `:8084` | register / authenticate, profile, password, restrictions, favourites |
| BFF `:8080` | composed personalised recipe, restriction conflicts, the 3 calculators, `JSESSIONID` |
| recipe-crawler `:8086` | import a recipe from a URL (`/recipes/import-url`) or crawl a site (`/recipes/crawl`) |

The UI keeps **its own `HttpSession`**. Login = User `POST /api/authenticate`
(for the account id) **and** BFF `POST /bff/login` as a form post (to capture its
`JSESSIONID`); both live in `CurrentUser`. Every `/bff/**` call relays that cookie.

## Pages

| Path | |
|---|---|
| `/` | dashboard (counts + tiles) |
| `/login`, `/register`, `POST /logout` | auth |
| `/ingredients` … `/ingredients/{id}` `/new` `/{id}/edit` `POST /{id}/nutrition` `POST /{id}/delete` | ingredient CRUD + nutrition + **density (g/ml)**; name search + autocomplete; delete blocked when a recipe uses it |
| `/tags` `POST /tags` `POST /tags/{id}/delete` `POST /tags/merge` | tag browser; **merge by name or id** |
| `/nutrition-reference` `POST /nutrition-reference` | browse + **add a food** |
| `/recipes` … `/recipes/{id}` `/random` `/new` `/{id}/edit` `POST /{id}/delete` | recipe CRUD; dynamic step/ingredient form with `amount`+`unit`; "Surprise me" |
| `/recipes/import-url` `POST …/preview` `POST …/commit` | **import a recipe from a URL** (via the `recipe-crawler` service): preview the parsed draft, then create it |
| `/recipes/crawl` `POST /recipes/crawl` | **crawl a site** from seed URLs — same-host link-following, robots.txt + rate-limited, bulk import; dry-run option |
| `/recipes/{id}/nutrition` `/calories` `/portions` `/portions.json` | calculators (login-gated) — total / per-serving / **per 100 g** / **per gram**; nutrition shows a **weight ⇄ volume** column; portions has a **live batch-size slider** and a grams / ml / as-written toggle |
| `/account` `/account/restrictions` `/account/favourites` | profile, password, **theme picker**, dietary restrictions (incl. **custom** codes), favourite recipes, favourite substitutions (**by name**) |
| `/import` | CSV upload for ingredients / recipes |
| `/suggest/{tags,ingredients,recipes}` | JSON, backs the `<datalist>` autocompletes |
| `POST /settings/theme` | writes the `ui.theme` cookie |

**Themes:** four light presets — Cherry Blossom 🌸, Blueberry 🫐, Matcha 🍵,
Lavender 🪻 — as `[data-theme]` blocks in `static/css/app.css`. Chosen on the
profile page, stored in a year-long `ui.theme` cookie, applied before first
paint by a `<head>` script; the brand emoji tracks the theme.

## Run

```bash
cd .. && docker compose up --build ui      # with the rest of the stack

# local, against the Dockerised downstreams
./mvnw spring-boot:run                      # http://localhost:8081
```

`spring.thymeleaf.cache=false` → templates hot-reload from `target/classes`.

## Configuration (env)

| Var | Default |
|---|---|
| `INGREDIENT_CATALOGUE_URL` | `http://localhost:8082` |
| `RECIPE_CATALOGUE_URL` | `http://localhost:8083` |
| `USER_SERVICE_URL` | `http://localhost:8084` |
| `BFF_URL` | `http://localhost:8080` |
| `RECIPE_CRAWLER_URL` | `http://localhost:8086` |
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` |
| `TRACING_SAMPLE_RATE` | `1.0` |
| `DOWNSTREAM_CONNECT_TIMEOUT` / `DOWNSTREAM_READ_TIMEOUT` | `2s` / `8s` — outbound HTTP timeouts |

## Tests

```bash
./mvnw test     # 5: context load + @WebMvcTest page renders + auth-guard redirect
```

## Resilience

Every downstream `RestClient` (the three catalogues and the BFF) has a short
connect/read timeout (`spring.http.clients.*`) and a per-service **circuit
breaker** (`config/DownstreamResilience` + `CircuitBreakerInterceptor`,
resilience4j). A timeout or a 5xx is a fault; a 4xx is not. When a breaker opens,
the page fails fast to the 503 error page instead of hanging. Breaker state is on
`/actuator/prometheus` (`resilience4j_circuitbreaker_*`).

The **recipe-crawler** client is the exception: a 3 s connect timeout but a
3-minute read timeout (a crawl walks many pages with a politeness delay) and no
breaker (an intentionally slow call would otherwise count against its slow-call
rate).

## Security

`spring-boot-starter-security` + `config/SecurityConfig` — `permitAll()` (the UI
keeps its own session guard; anonymous browsing is a feature), Spring's form
login / basic / logout disabled. It's on for the cross-cutting hardening:

- **CSRF** on for state-changing form posts; `th:action` auto-injects the hidden
  `_csrf` field. The one `fetch()` (portion slider) is a GET.
- `JSESSIONID`: `SameSite=Strict`, `HttpOnly`, cookie-only (no `;jsessionid=`).
- Headers: `X-Frame-Options: DENY`, `nosniff`, a CSP (inline script/style still
  allowed — nonces are a later pass), `Referrer-Policy: no-referrer`.
- `AuthController` rotates the session id (`request.changeSessionId()`) on
  login / register.
- Direct calls to the catalogues / User / crawler carry an HMAC
  `X-Internal-Auth` header (`internal/`), signed with the session's account id.

See `../IngredientCatalogue/SECURITY.md` for the full assessment (H2 / M1 / M5 /
C1 / C2 addressed 2026-09-09).

## Notes / known gaps

- `reactive-streams` is a direct dependency — `RestClient`'s multipart writer
  needs `org.reactivestreams.Publisher` and it isn't on a webmvc-only classpath.
- `RecipeForm` is built from a `@RequestParam MultiValueMap`, not
  `@ModelAttribute` — Spring comma-splits a single value bound to `List<String>`,
  which would shred a recipe step.

## Status

**v1 complete.** All catalogue + BFF surfaces represented; Phase 4 features done
and browser-verified against the Docker stack: structured quantities, the
calculators (per-gram, per-100 g, **weight ⇄ volume** via density, **live
portion slider**), CSV import, **import from URL**, random, themes, name-based
forms, nutrition-reference add, favourite toggle, ingredient density, optional
registration email.
