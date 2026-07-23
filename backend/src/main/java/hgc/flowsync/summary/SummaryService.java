package hgc.flowsync.summary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hgc.flowsync.common.api.PageResponse;
import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.task.Task;
import hgc.flowsync.task.TaskMapper;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummaryService {

	private final SummaryMapper summaryMapper;
	private final TaskMapper taskMapper;
	private final UserMapper userMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final CurrentUserService currentUserService;
	private final ProjectAccessService projectAccessService;
	private final SummaryAccessService summaryAccessService;

	public SummaryService(
		SummaryMapper summaryMapper,
		TaskMapper taskMapper,
		UserMapper userMapper,
		ProjectMemberMapper projectMemberMapper,
		CurrentUserService currentUserService,
		ProjectAccessService projectAccessService,
		SummaryAccessService summaryAccessService) {
		this.summaryMapper = summaryMapper;
		this.taskMapper = taskMapper;
		this.userMapper = userMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.currentUserService = currentUserService;
		this.projectAccessService = projectAccessService;
		this.summaryAccessService = summaryAccessService;
	}

	@Transactional(readOnly = true)
	public PageResponse<SummaryResponse> findAll(
		Authentication authentication,
		String requestedProjectId,
		String requestedTaskId,
		SummaryType type,
		String requestedCreatedBy,
		int page,
		int size,
		String sort) {
		User currentUser = currentUserService.require(authentication);
		Long projectId = parseNullableId(requestedProjectId);
		Long createdBy = parseNullableId(requestedCreatedBy);
		Long taskId = requestedTaskId == null || requestedTaskId.equals("none")
			? null
			: parseId(requestedTaskId);

		LambdaQueryWrapper<Summary> query = Wrappers.<Summary>lambdaQuery()
			.eq(projectId != null, Summary::getProjectId, projectId)
			.eq(requestedTaskId != null && !requestedTaskId.equals("none"), Summary::getTaskId, taskId)
			.isNull("none".equals(requestedTaskId), Summary::getTaskId)
			.eq(type != null, Summary::getType, type)
			.eq(createdBy != null, Summary::getCreatedBy, createdBy);

		if (!projectAccessService.isAdmin(currentUser)) {
			List<Long> visibleProjectIds = projectMemberMapper.selectList(
				Wrappers.<ProjectMember>lambdaQuery()
					.select(ProjectMember::getProjectId)
					.eq(ProjectMember::getUserId, currentUser.getId())).stream()
				.map(ProjectMember::getProjectId)
				.toList();
			if (visibleProjectIds.isEmpty()
				|| (projectId != null && !visibleProjectIds.contains(projectId))) {
				return PageResponse.of(List.of(), page, size, 0);
			}
			query.in(Summary::getProjectId, visibleProjectIds);
		}

		long totalElements = summaryMapper.selectCount(query);
		applySort(query, sort);
		query.orderByAsc(Summary::getId)
			.last("LIMIT " + size + " OFFSET " + (long) page * size);
		return PageResponse.of(responses(summaryMapper.selectList(query)), page, size, totalElements);
	}

	@Transactional(readOnly = true)
	public SummaryResponse findById(Authentication authentication, Long summaryId) {
		Summary summary = summaryAccessService.requireReadable(authentication, summaryId).summary();
		return responses(List.of(summary)).getFirst();
	}

	@Transactional
	public SummaryResponse create(
		Authentication authentication,
		String requestedProjectId,
		String requestedTaskId,
		SummaryType type,
		String content) {
		long projectId = parseId(requestedProjectId);
		Long taskId = parseNullableId(requestedTaskId);
		SummaryAccessService.ProjectContext context =
			summaryAccessService.requireCreatable(authentication, projectId);
		validateTask(projectId, taskId);

		Summary summary = new Summary();
		summary.setProjectId(projectId);
		summary.setTaskId(taskId);
		summary.setCreatedBy(context.currentUser().getId());
		summary.setType(type);
		summary.setContent(content);
		summaryMapper.insert(summary);
		return responses(List.of(summaryMapper.selectById(summary.getId()))).getFirst();
	}

	@Transactional
	public SummaryResponse update(
		Authentication authentication,
		Long summaryId,
		SummaryType type,
		String content) {
		summaryAccessService.requireWritable(authentication, summaryId);
		summaryMapper.update(null, Wrappers.<Summary>lambdaUpdate()
			.eq(Summary::getId, summaryId)
			.set(Summary::getType, type)
			.set(Summary::getContent, content));
		return responses(List.of(summaryMapper.selectById(summaryId))).getFirst();
	}

	@Transactional
	public void delete(Authentication authentication, Long summaryId) {
		summaryAccessService.requireWritable(authentication, summaryId);
		summaryMapper.deleteById(summaryId);
	}

	private void validateTask(Long projectId, Long taskId) {
		if (taskId == null) {
			return;
		}
		Task task = taskMapper.selectById(taskId);
		if (task == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (!task.getProjectId().equals(projectId)) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
	}

	private List<SummaryResponse> responses(List<Summary> summaries) {
		if (summaries.isEmpty()) {
			return List.of();
		}
		Set<Long> creatorIds = new HashSet<>();
		for (Summary summary : summaries) {
			creatorIds.add(summary.getCreatedBy());
		}
		Map<Long, User> creators = new HashMap<>();
		for (User creator : userMapper.selectByIds(creatorIds)) {
			creators.put(creator.getId(), creator);
		}
		return summaries.stream()
			.map(summary -> SummaryResponse.from(summary, creators.get(summary.getCreatedBy())))
			.toList();
	}

	private static long parseId(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException exception) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}

	private static Long parseNullableId(String value) {
		return value == null ? null : parseId(value);
	}

	private static void applySort(LambdaQueryWrapper<Summary> query, String sort) {
		String[] parts = sort.split(",", -1);
		if (parts.length != 2 || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		boolean ascending = parts[1].equals("asc");
		switch (parts[0]) {
			case "createdAt" -> query.orderBy(true, ascending, Summary::getCreatedAt);
			case "updatedAt" -> query.orderBy(true, ascending, Summary::getUpdatedAt);
			case "type" -> query.orderBy(true, ascending, Summary::getType);
			default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}
}
