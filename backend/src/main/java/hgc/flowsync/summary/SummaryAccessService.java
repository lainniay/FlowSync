package hgc.flowsync.summary;

import java.util.Objects;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.User;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummaryAccessService {

	private final SummaryMapper summaryMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;

	public SummaryAccessService(
		SummaryMapper summaryMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService) {
		this.summaryMapper = summaryMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
	}

	public SummaryContext requireReadable(Authentication authentication, Long summaryId) {
		User currentUser = currentUserService.require(authentication);
		Summary summary = requireSummary(summaryId);
		Project project = projectAccessService.requireProject(summary.getProjectId());
		requireVisible(project, currentUser);
		return new SummaryContext(summary, project, currentUser);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public ProjectContext requireCreatable(Authentication authentication, Long projectId) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Project project = projectAccessService.requireProjectForUpdate(projectId);
		requireWritableMember(project, currentUser);
		projectAccessService.requireUnarchived(project);
		return new ProjectContext(project, currentUser);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public SummaryContext requireWritable(Authentication authentication, Long summaryId) {
		User currentUser = currentUserService.requireForUpdate(authentication);
		Summary snapshot = requireSummary(summaryId);
		Project project = projectAccessService.requireProjectForUpdate(snapshot.getProjectId());
		Summary summary = requireSummaryForUpdate(summaryId);
		if (!Objects.equals(summary.getProjectId(), project.getId())) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		requireVisible(project, currentUser);
		if (projectAccessService.isAdmin(currentUser)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (!projectAccessService.isOwner(project, currentUser)
			&& !Objects.equals(summary.getCreatedBy(), currentUser.getId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		projectAccessService.requireUnarchived(project);
		return new SummaryContext(summary, project, currentUser);
	}

	private Summary requireSummary(Long summaryId) {
		Summary summary = summaryId == null ? null : summaryMapper.selectById(summaryId);
		if (summary == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return summary;
	}

	private Summary requireSummaryForUpdate(Long summaryId) {
		Summary summary = summaryId == null ? null : summaryMapper.selectOne(
			Wrappers.<Summary>lambdaQuery()
				.eq(Summary::getId, summaryId)
				.last("FOR UPDATE"));
		if (summary == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return summary;
	}

	private void requireVisible(Project project, User currentUser) {
		if (!projectAccessService.isAdmin(currentUser)
			&& !projectAccessService.isMember(project, currentUser)) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
	}

	private void requireWritableMember(Project project, User currentUser) {
		if (projectAccessService.isAdmin(currentUser)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (!projectAccessService.isMember(project, currentUser)) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
	}

	public record ProjectContext(Project project, User currentUser) {
	}

	public record SummaryContext(Summary summary, Project project, User currentUser) {
	}
}
