package com.clearingservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/clearing")
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "Hello World from Bankgood System! 🏦";
    }

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}