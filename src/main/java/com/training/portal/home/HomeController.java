package com.training.portal.home;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.training.portal.home.dto.HomeDashboardResponse;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final HomeService service;

    public HomeController(HomeService service) {
        this.service = service;
    }

    @GetMapping
    public HomeDashboardResponse getDashboard(Authentication authentication) {
        return service.getDashboard(authentication.getName());
    }
}
