package com.splitsmart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Direct links and reloads use the same shell; group APIs still enforce membership. */
@Controller
public class PageController {
    @GetMapping("/groups/{id}")
    public String group() {
        return "forward:/index.html";
    }
}
