package com.example.ui.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** SECURITY.md N7: reading is public, every write needs a login, a few pages need one just to open. */
class AuthInterceptorTest {

    @ParameterizedTest(name = "{0} {1} -> login required: {2}")
    @CsvSource({
            // public reads
            "GET,/,false", "GET,/ingredients,false", "GET,/ingredients/{id},false",
            "GET,/recipes,false", "GET,/recipes/random,false", "GET,/recipes/{id},false",
            "GET,/tags,false", "GET,/nutrition-reference,false", "GET,/login,false", "GET,/register,false",
            "GET,/suggest/tags,false", "HEAD,/ingredients,false", "OPTIONS,/recipes,false",
            // reads that need a session
            "GET,/account,true", "GET,/account/favourites,true", "GET,/account/restrictions,true",
            "GET,/account/anything-new,true", "GET,/favourites/recipes/{recipeId},true",
            "GET,/recipes/{id}/nutrition,true", "GET,/recipes/{id}/calories,true", "GET,/recipes/{id}/portions,true",
            "GET,/recipes/{id}/portions.json,true",
            "GET,/ingredients/new,true", "GET,/ingredients/{id}/edit,true",
            "GET,/recipes/new,true", "GET,/recipes/{id}/edit,true",
            "GET,/recipes/import-url,true", "GET,/recipes/crawl,true", "GET,/import,true",
            // the only writes that work anonymously
            "POST,/login,false", "POST,/register,false", "POST,/logout,false", "POST,/settings/theme,false",
            // every other write needs a login - including routes that don't exist yet
            "POST,/ingredients,true", "POST,/ingredients/{id},true", "POST,/ingredients/{id}/delete,true",
            "POST,/ingredients/{id}/nutrition,true", "POST,/recipes,true", "POST,/recipes/{id},true",
            "POST,/recipes/{id}/delete,true", "POST,/tags,true", "POST,/tags/merge,true", "POST,/tags/{id}/delete,true",
            "POST,/nutrition-reference,true", "POST,/nutrition-reference/{id},true",
            "POST,/import/ingredients,true", "POST,/import/recipes,true",
            "POST,/recipes/import-url/preview,true", "POST,/recipes/import-url/commit,true", "POST,/recipes/crawl,true",
            "POST,/account/profile,true", "POST,/favourites/alternatives,true",
            "POST,/some/future/route,true", "PUT,/anything,true", "DELETE,/anything,true", "PATCH,/anything,true"
    })
    void accessRules(String method, String route, boolean loginRequired) {
        assertThat(AuthInterceptor.requiresLogin(method, route)).isEqualTo(loginRequired);
    }

    @Test
    void methodNamesAreCaseInsensitive() {
        assertThat(AuthInterceptor.requiresLogin("get", "/ingredients")).isFalse();
        assertThat(AuthInterceptor.requiresLogin("post", "/ingredients")).isTrue();
    }

    @Test
    void aPrefixOfAnAccountRouteIsNotAnAccountRoute() {
        // "/accounts-info" must not be swept into the /account area by a sloppy startsWith("/account")
        assertThat(AuthInterceptor.requiresLogin("GET", "/accounts-info")).isFalse();
    }
}
