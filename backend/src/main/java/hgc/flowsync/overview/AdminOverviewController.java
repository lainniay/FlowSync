package hgc.flowsync.overview;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminOverviewController {

	private final AdminOverviewService service;

	public AdminOverviewController(AdminOverviewService service) {
		this.service = service;
	}

	@GetMapping("/api/admin/overview")
	@PreAuthorize("hasRole('ADMIN')")
	AdminOverviewResponse overview() {
		return service.overview();
	}
}
