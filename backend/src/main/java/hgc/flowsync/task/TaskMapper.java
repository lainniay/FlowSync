package hgc.flowsync.task;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

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

	default List<LatestProgress> selectLatestProgressByTaskIds(List<Long> taskIds) {
		return taskIds == null || taskIds.isEmpty() ? List.of() : selectLatestProgressByTaskIdsQuery(taskIds);
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

	default boolean existsIncompleteByProjectIdAndAssigneeId(Long projectId, Long assigneeId) {
		return selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, projectId)
			.eq(Task::getAssigneeId, assigneeId)
			.notIn(Task::getStatus, TaskStatus.COMPLETED, TaskStatus.CANCELLED)) > 0;
	}

	default long countByProjectId(Long projectId) {
		return selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, projectId));
	}

	default long countCompletedByProjectId(Long projectId) {
		return selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getProjectId, projectId)
			.eq(Task::getStatus, TaskStatus.COMPLETED));
	}
}
