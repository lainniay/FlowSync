package hgc.flowsync.task;

import java.time.LocalDate;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

	default Task selectByIdForUpdate(Long taskId) {
		return taskId == null ? null : selectOne(Wrappers.<Task>lambdaQuery()
			.eq(Task::getId, taskId)
			.last("FOR UPDATE"));
	}

	@Select({
		"<script>",
		"SELECT task_id AS taskId, progress_percent AS progressPercent FROM (",
		"SELECT task_id, progress_percent,",
		"ROW_NUMBER() OVER (PARTITION BY task_id ORDER BY created_at DESC, id DESC) AS row_num",
		"FROM task_logs",
		"WHERE task_id IN",
		"<foreach collection='taskIds' item='taskId' open='(' separator=',' close=')'>",
		"#{taskId}",
		"</foreach>",
		") latest_logs WHERE row_num = 1",
		"</script>"
	})
	List<LatestProgress> selectLatestProgressByTaskIdsQuery(@Param("taskIds") List<Long> taskIds);

	@Select({
		"<script>",
		"SELECT project_id AS projectId, COUNT(*) AS total,",
		"SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed",
		"FROM tasks WHERE project_id IN",
		"<foreach collection='projectIds' item='projectId' open='(' separator=',' close=')'>",
		"#{projectId}",
		"</foreach>",
		"GROUP BY project_id",
		"</script>"
	})
	List<ProjectTaskStats> selectProjectStatsQuery(@Param("projectIds") List<Long> projectIds);

	@Select({
		"<script>",
		"SELECT status, COUNT(*) AS taskCount,",
		"SUM(CASE WHEN due_date &lt; #{today}",
		"AND status NOT IN ('COMPLETED', 'CANCELLED') THEN 1 ELSE 0 END) AS overdueCount",
		"FROM tasks WHERE project_id IN",
		"<foreach collection='projectIds' item='projectId' open='(' separator=',' close=')'>",
		"#{projectId}",
		"</foreach>",
		"GROUP BY status",
		"</script>"
	})
	List<OverviewTaskStats> selectOverviewStatsQuery(
		@Param("projectIds") List<Long> projectIds,
		@Param("today") LocalDate today);

	default List<LatestProgress> selectLatestProgressByTaskIds(List<Long> taskIds) {
		return taskIds == null || taskIds.isEmpty() ? List.of() : selectLatestProgressByTaskIdsQuery(taskIds);
	}

	default List<ProjectTaskStats> selectProjectStats(List<Long> projectIds) {
		return projectIds == null || projectIds.isEmpty() ? List.of() : selectProjectStatsQuery(projectIds);
	}

	default List<OverviewTaskStats> selectOverviewStats(List<Long> projectIds, LocalDate today) {
		return projectIds == null || projectIds.isEmpty()
			? List.of() : selectOverviewStatsQuery(projectIds, today);
	}

	default List<Task> selectRecentByProjectIds(List<Long> projectIds) {
		return projectIds == null || projectIds.isEmpty() ? List.of()
			: selectList(Wrappers.<Task>lambdaQuery()
				.in(Task::getProjectId, projectIds)
				.orderByDesc(Task::getCreatedAt, Task::getId)
				.last("LIMIT 10"));
	}

	class LatestProgress {
		private Long taskId;
		private int progressPercent;

		public Long getTaskId() {
			return taskId;
		}

		public void setTaskId(Long taskId) {
			this.taskId = taskId;
		}

		public int getProgressPercent() {
			return progressPercent;
		}

		public void setProgressPercent(int progressPercent) {
			this.progressPercent = progressPercent;
		}
	}

	default boolean existsIncompleteByAssigneeId(Long assigneeId) {
		return selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getAssigneeId, assigneeId)
			.notIn(Task::getStatus, TaskStatus.COMPLETED, TaskStatus.CANCELLED)) > 0;
	}

	default boolean existsIncompleteByAssigneeIdForUpdate(Long assigneeId) {
		return !selectList(Wrappers.<Task>lambdaQuery()
			.select(Task::getId)
			.eq(Task::getAssigneeId, assigneeId)
			.notIn(Task::getStatus, TaskStatus.COMPLETED, TaskStatus.CANCELLED)
			.last("LIMIT 1 FOR UPDATE")).isEmpty();
	}

	default boolean existsIncompleteByProjectIdAndAssigneeId(Long projectId, Long assigneeId) {
		return selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, projectId)
			.eq(Task::getAssigneeId, assigneeId)
			.notIn(Task::getStatus, TaskStatus.COMPLETED, TaskStatus.CANCELLED)) > 0;
	}

	class ProjectTaskStats {
		private Long projectId;
		private long total;
		private long completed;

		public Long getProjectId() {
			return projectId;
		}

		public void setProjectId(Long projectId) {
			this.projectId = projectId;
		}

		public long getTotal() {
			return total;
		}

		public void setTotal(long total) {
			this.total = total;
		}

		public long getCompleted() {
			return completed;
		}

		public void setCompleted(long completed) {
			this.completed = completed;
		}
	}

	class OverviewTaskStats {
		private TaskStatus status;
		private long taskCount;
		private long overdueCount;

		public TaskStatus getStatus() {
			return status;
		}

		public void setStatus(TaskStatus status) {
			this.status = status;
		}

		public long getTaskCount() {
			return taskCount;
		}

		public void setTaskCount(long taskCount) {
			this.taskCount = taskCount;
		}

		public long getOverdueCount() {
			return overdueCount;
		}

		public void setOverdueCount(long overdueCount) {
			this.overdueCount = overdueCount;
		}
	}
}
