package com.saaketh.budget.report;

import com.saaketh.budget.auth.AuthenticatedUser;
import com.saaketh.budget.report.DashboardService.Dashboard;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** GET /api/dashboard?month=2026-09 */
    @GetMapping
    public Dashboard dashboard(@AuthenticationPrincipal AuthenticatedUser user, @RequestParam String month) {
        return dashboardService.forMonth(user.getId(), month);
    }
}
