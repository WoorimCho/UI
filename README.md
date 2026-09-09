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

The UI keeps **its own `HttpSession`**. Login = User `POST /api/authenticate`
(for the account id) **and** BFF `POST /bff/login` as a form post (to capture its
`JSESSIONID`); both live in `CurrentUser`. Every `/bff/**` call relays that cookie.

## Pages

| Path | |
|---|---|
| `/` | dashboard (counts + tiles) |
| `/login`, `/register`, `POST /logout` | auth |
| `/ingredients` … `/ingredients/{id}` `/new` `/{id}/edit` `POST /{id}/nutrition` `POST /{id}/delete` | ingredient CRUD + nutrition; name search + autocomplete; delete blocked when a recipe uses it |
| `/tags` `POST /tags` `POST /tags/{id}/delete` `POST /tags/merge` | tag browser; **merge by name or id** |
| `/nutrition-reference` `POST /nutrition-reference` | browse + **add a food** |
| `/recipes` … `/recipes/{id}` `/random` `/new` `/{id}/edit` `POST /{id}/delete` | recipe CRUD; dynamic step/ingredient form with `amount`+`unit`; "Surprise me" |
| `/recipes/{id}/nutrition` `/calories` `/portions` | calculators (login-gated) — total / per-serving / **per 100 g** / **per gram** |
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
| `ZIPKIN_ENDPOINT` | `http://localhost:9411/api/v2/spans` |
| `TRACING_SAMPLE_RATE` | `1.0` |

## Tests

```bash
./mvnw test     # 5: context load + @WebMvcTest page renders + auth-guard redirect
```

## Notes / known gaps

- `reactive-streams` is a direct dependency — `RestClient`'s multipart writer
  needs `org.reactivestreams.Publisher` and it isn't on a webmvc-only classpath.
- `RecipeForm` is built from a `@RequestParam MultiValueMap`, not
  `@ModelAttribute` — Spring comma-splits a single value bound to `List<String>`,
  which would shred a recipe step.
- **No Spring Security on the UI** → no CSRF tokens, `JSESSIONID` has no
  `SameSite`, no `X-Frame-Options`/CSP, session id not rotated on login. See
  `../IngredientCatalogue/SECURITY.md` (findings H2, M1, M5).

## Status

**v1 complete.** All catalogue + BFF surfaces represented; Phase 4 features
(structured quantities, calculators incl. per-gram, CSV import, random, themes,
name-based forms, nutrition-reference add, favourite toggle) done and
browser-verified against the Docker stack.
