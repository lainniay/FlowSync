package hgc.flowsync.task;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import hgc.flowsync.common.api.PageResponse;
import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskLogService {

	private final TaskLogMapper taskLogMapper;
	private final UserMapper userMapper;
	private final ProjectAccessService projectAccessService;
	private final TaskAccessService taskAccessService;

	public TaskLogService(
		TaskLogMapper taskLogMapper,
		UserMapper userMapper,
		ProjectAccessService projectAccessService,
		TaskAccessService taskAccessService) {
		this.taskLogMapper = taskLogMapper;
		this.userMapper = userMapper;
		this.projectAccessService = projectAccessService;
		this.taskAccessService = taskAccessService;
	}

	@Transactional(readOnly = true)
	public PageResponse<TaskLogResponse> findAll(
		Authentication authentication,
		Long taskId,
		int page,
		int size,
		String sort) {
		taskAccessService.requireReadable(authentication, taskId);
		LambdaQueryWrapper<TaskLog> query = Wrappers.<TaskLog>lambdaQuery()
			.eq(TaskLog::getTaskId, taskId);
		long totalElements = taskLogMapper.selectCount(query);
		applySort(query, sort);
		query.orderByAsc(TaskLog::getId)
			.last("LIMIT " + size + " OFFSET " + (long) page * size);
		return PageResponse.of(responses(taskLogMapper.selectList(query)), page, size, totalElements);
	}

	@Transactional
	public TaskLogResponse create(
		Authentication authentication,
		Long taskId,
		int progressPercent,
		String content) {
		TaskAccessService.TaskContext context =
			taskAccessService.requireTaskLogCreatable(authentication, taskId);
		TaskLog taskLog = new TaskLog();
		taskLog.setTaskId(context.task().getId());
		taskLog.setOperatorId(context.currentUser().getId());
		taskLog.setProgressPercent(progressPercent);
		taskLog.setContent(content);
		taskLogMapper.insert(taskLog);
		return responses(List.of(taskLogMapper.selectById(taskLog.getId()))).getFirst();
	}

	@Transactional
	public void delete(Authentication authentication, Long taskId, Long logId) {
		TaskLog snapshot = requireTaskLog(logId);
		TaskAccessService.TaskContext context =
			taskAccessService.requireTaskLogDeleteContext(authentication, snapshot.getTaskId());
		TaskLog taskLog = requireTaskLogForUpdate(logId);
		if (!Objects.equals(taskLog.getTaskId(), taskId)
			|| !Objects.equals(taskLog.getTaskId(), context.task().getId())) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (projectAccessService.isAdmin(context.currentUser())
			|| (!projectAccessService.isOwner(context.project(), context.currentUser())
				&& !Objects.equals(taskLog.getOperatorId(), context.currentUser().getId()))) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		projectAccessService.requireUnarchived(context.project());
		taskLogMapper.deleteById(taskLog.getId());
	}

	private TaskLog requireTaskLog(Long logId) {
		TaskLog taskLog = logId == null ? null : taskLogMapper.selectById(logId);
		if (taskLog == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return taskLog;
	}

	private TaskLog requireTaskLogForUpdate(Long logId) {
		TaskLog taskLog = logId == null ? null : taskLogMapper.selectOne(
			Wrappers.<TaskLog>lambdaQuery()
				.eq(TaskLog::getId, logId)
				.last("FOR UPDATE"));
		if (taskLog == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return taskLog;
	}

	private List<TaskLogResponse> responses(List<TaskLog> taskLogs) {
		if (taskLogs.isEmpty()) {
			return List.of();
		}
		Set<Long> operatorIds = new HashSet<>();
		for (TaskLog taskLog : taskLogs) {
			operatorIds.add(taskLog.getOperatorId());
		}
		Map<Long, User> operators = new HashMap<>();
		for (User operator : userMapper.selectBatchIds(operatorIds)) {
			operators.put(operator.getId(), operator);
		}
		return taskLogs.stream()
			.map(taskLog -> TaskLogResponse.from(taskLog, operators.get(taskLog.getOperatorId())))
			.toList();
	}

	private static void applySort(LambdaQueryWrapper<TaskLog> query, String sort) {
		String[] parts = sort.split(",", -1);
		if (parts.length != 2 || !(parts[1].equals("asc") || parts[1].equals("desc"))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
		boolean ascending = parts[1].equals("asc");
		switch (parts[0]) {
			case "createdAt" -> query.orderBy(true, ascending, TaskLog::getCreatedAt);
			case "progressPercent" -> query.orderBy(true, ascending, TaskLog::getProgressPercent);
			default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
		}
	}
}
