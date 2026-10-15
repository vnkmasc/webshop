package vn.saibuoc.shop.simulation;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.saibuoc.shop.common.ApiResponse;
import vn.saibuoc.shop.common.BusinessException;
import vn.saibuoc.shop.events.OrderQueueMonitor;
import vn.saibuoc.shop.security.SecurityUtils;

@RestController
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulation;
    private final ObjectProvider<OrderQueueMonitor> queue;

    /** queue is null when the order queue is not configured. */
    public record Status(List<SimulationService.ScenarioState> scenarios, int retainedMb, int taggedSeries,
                         OrderQueueMonitor.Snapshot queue) {
    }

    public record ScenarioUpdate(boolean active, Map<String, Object> params, Integer durationMinutes) {
    }

    /** Flags the storefront reads on start-up. */
    public record SiteConfig(boolean frontendError) {
    }

    @GetMapping("/api/site-config")
    public ApiResponse<SiteConfig> siteConfig() {
        return ApiResponse.ok(new SiteConfig(simulation.frontendErrorActive()));
    }

    @GetMapping("/api/admin/simulation")
    public ApiResponse<Status> status() {
        requireEnabled();
        return ApiResponse.ok(new Status(simulation.list(), simulation.retainedMb(), simulation.taggedSeries(),
                queue.getIfAvailable() == null ? null : queue.getObject().snapshot()));
    }

    @PutMapping("/api/admin/simulation/{scenario}")
    public ApiResponse<SimulationService.ScenarioState> update(@PathVariable SimulationService.Scenario scenario,
                                                               @RequestBody ScenarioUpdate body) {
        requireEnabled();
        String by = SecurityUtils.currentUser().email();
        return ApiResponse.ok(body.active()
                ? simulation.activate(scenario, body.params(), body.durationMinutes() == null ? 15 : body.durationMinutes(), by)
                : simulation.deactivate(scenario, by));
    }

    @PostMapping("/api/admin/simulation/release-memory")
    public ApiResponse<Integer> releaseMemory() {
        requireEnabled();
        return ApiResponse.ok(simulation.releaseMemory(), "Đã giải phóng bộ nhớ");
    }

    private void requireEnabled() {
        if (!simulation.enabled()) {
            throw BusinessException.notFound("tài nguyên");
        }
    }
}
