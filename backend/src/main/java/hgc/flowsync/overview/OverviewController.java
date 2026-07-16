package hgc.flowsync.overview;

import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OverviewController {

	private final OverviewService overviewService;

	public OverviewController(OverviewService overviewService) {
		this.overviewService = overviewService;
	}

	@GetMapping("/api/overview")
	OverviewResponse overview(
		Authentication authentication,
		@RequestParam(required = false) @Min(1) Long projectId) {
		return overviewService.find(authentication, projectId);
	}
}
