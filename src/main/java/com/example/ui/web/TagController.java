package com.example.ui.web;

import com.example.ui.client.IngredientClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tag management (tags are owned by IngredientCatalogue and shared by both catalogues). */
@Controller
public class TagController {

    private final IngredientClient client;

    public TagController(IngredientClient client) {
        this.client = client;
    }

    @GetMapping("/tags")
    public String list(@RequestParam(required = false) String prefix,
                       @RequestParam(required = false) String namespace,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(name = "jump", required = false) Integer jump,
                       Model model) {
        if (jump != null && jump > 0) {
            page = jump - 1;
        }
        var result = client.tags(prefix, namespace, page, 40);
        model.addAttribute("page", result);
        model.addAttribute("prefix", prefix);
        model.addAttribute("namespace", namespace);
        model.addAttribute("pageNo", page);

        Map<String, String> pagerParams = new LinkedHashMap<>();
        pagerParams.put("prefix", prefix);
        pagerParams.put("namespace", namespace);
        model.addAttribute("pager", new Pager("/tags", pagerParams, page, result.totalPages()));
        return "tags/list";
    }

    @PostMapping("/tags")
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String namespace,
                         @RequestParam(required = false) String description) {
        client.createTag(name.trim(), namespace, description);
        return "redirect:/tags";
    }

    @PostMapping("/tags/{id}/delete")
    public String delete(@org.springframework.web.bind.annotation.PathVariable long id) {
        client.deleteTag(id);
        return "redirect:/tags";
    }

    /**
     * Merge one or more source tags into a target. Each field accepts a tag
     * <em>name</em> or a numeric id — names are resolved against the catalogue.
     */
    @PostMapping("/tags/merge")
    public String merge(@RequestParam String from, @RequestParam String into,
                        RedirectAttributes flash) {
        List<Long> fromIds = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (String token : from.split("[,\\n]")) {
            String t = token.trim();
            if (t.isEmpty()) {
                continue;
            }
            Long id = resolveTagId(t);
            if (id == null) {
                unresolved.add(t);
            } else {
                fromIds.add(id);
            }
        }
        Long intoId = resolveTagId(into.trim());
        if (intoId == null) {
            unresolved.add(into.trim());
        }
        if (!unresolved.isEmpty() || fromIds.isEmpty()) {
            flash.addFlashAttribute("error", "No tag found for: " + String.join(", ", unresolved.isEmpty()
                    ? List.of(from) : unresolved));
            return "redirect:/tags";
        }
        client.mergeTags(fromIds, intoId);
        return "redirect:/tags";
    }

    /** A bare number is taken as an id; anything else is looked up by name. */
    private Long resolveTagId(String token) {
        if (token.matches("\\d+")) {
            return Long.valueOf(token);
        }
        IngredientClient.Tag tag = client.findTagByName(token);
        return tag == null ? null : tag.id();
    }
}
