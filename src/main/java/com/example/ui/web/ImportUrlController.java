package com.example.ui.web;

import com.example.ui.client.RecipeCrawlerClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;

/**
 * Import recipes via the recipe-crawler service:
 * {@code /recipes/import-url} (one URL: preview → commit) and
 * {@code /recipes/crawl} (seed URLs: walk + bulk import).
 */
@Controller
public class ImportUrlController {

    private final RecipeCrawlerClient crawler;

    public ImportUrlController(RecipeCrawlerClient crawler) {
        this.crawler = crawler;
    }

    @GetMapping("/recipes/import-url")
    public String form() {
        return "recipes/import-url";
    }

    @PostMapping("/recipes/import-url/preview")
    public String preview(@RequestParam String url, Model model) {
        model.addAttribute("url", url);
        if (!StringUtils.hasText(url)) {
            model.addAttribute("error", "Paste a recipe URL first.");
            return "recipes/import-url";
        }
        try {
            model.addAttribute("draft", crawler.preview(url.trim()));
        } catch (RestClientResponseException e) {
            model.addAttribute("error", explain(e));
        } catch (RuntimeException e) {
            model.addAttribute("error", "The crawler service is unreachable right now.");
        }
        return "recipes/import-url";
    }

    @PostMapping("/recipes/import-url/commit")
    public String commit(@RequestParam String url,
                         @RequestParam(required = false) String creator,
                         RedirectAttributes flash) {
        try {
            RecipeCrawlerClient.Imported r = crawler.commit(url.trim(), creator);
            flash.addFlashAttribute("notice", "Imported \"" + r.name() + "\" — "
                    + r.ingredientCount() + " ingredients (" + r.ingredientsCreated() + " new), "
                    + r.stepCount() + " steps.");
            return "redirect:/recipes/" + r.recipeId();
        } catch (RestClientResponseException e) {
            flash.addFlashAttribute("error", explain(e));
        } catch (RuntimeException e) {
            flash.addFlashAttribute("error", "The crawler service is unreachable right now.");
        }
        flash.addFlashAttribute("url", url);
        return "redirect:/recipes/import-url";
    }

    // ── crawl (seed URLs → walk + bulk import) ──────────────────────────────

    @GetMapping("/recipes/crawl")
    public String crawlForm() {
        return "recipes/crawl";
    }

    @PostMapping("/recipes/crawl")
    public String crawl(@RequestParam String seeds,
                        @RequestParam(defaultValue = "8") int maxPages,
                        @RequestParam(defaultValue = "1") int maxDepth,
                        @RequestParam(defaultValue = "false") boolean allHosts,
                        @RequestParam(defaultValue = "false") boolean dryRun,
                        @RequestParam(required = false) String creator,
                        Model model) {
        model.addAttribute("seeds", seeds);
        model.addAttribute("maxPages", maxPages);
        model.addAttribute("maxDepth", maxDepth);
        model.addAttribute("allHosts", allHosts);
        model.addAttribute("dryRun", dryRun);

        List<String> seedList = Arrays.stream(seeds.split("[\\s,]+"))
                .map(String::trim).filter(StringUtils::hasText).toList();
        if (seedList.isEmpty()) {
            model.addAttribute("error", "Add at least one seed URL.");
            return "recipes/crawl";
        }
        try {
            model.addAttribute("report", crawler.crawl(seedList, maxPages, maxDepth,
                    !allHosts, dryRun, creator));
        } catch (RestClientResponseException e) {
            model.addAttribute("error", explain(e));
        } catch (RuntimeException e) {
            model.addAttribute("error", "The crawler service is unreachable or the crawl timed out.");
        }
        return "recipes/crawl";
    }

    private static String explain(RestClientResponseException e) {
        int code = e.getStatusCode().value();
        return switch (code) {
            case 422 -> "That page has no machine-readable recipe (no schema.org/JSON-LD Recipe).";
            case 403 -> "robots.txt on that site disallows fetching this URL.";
            case 400 -> "That doesn't look like a valid http(s) recipe URL.";
            case 502, 503, 504 -> "Couldn't reach the page or a catalogue service. Try again.";
            default -> "Import failed (HTTP " + code + ").";
        };
    }
}
