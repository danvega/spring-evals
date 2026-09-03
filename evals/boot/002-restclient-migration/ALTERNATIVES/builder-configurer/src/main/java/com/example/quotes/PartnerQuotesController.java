package com.example.quotes;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local stand-in for the partner's quote API, so the app runs without
 * their sandbox. Serves the same JSON shape as the real thing.
 * DO NOT MODIFY.
 */
@RestController
@RequestMapping("/partner/quotes")
public class PartnerQuotesController {

    @GetMapping
    public List<Quote> quotes() {
        return List.of(
                new Quote(1L, "Grace Hopper", "The most dangerous phrase in the language is, we've always done it this way."),
                new Quote(2L, "Alan Kay", "The best way to predict the future is to invent it."),
                new Quote(3L, "Barbara Liskov", "Modularity based on abstraction is the way things get done."));
    }
}
