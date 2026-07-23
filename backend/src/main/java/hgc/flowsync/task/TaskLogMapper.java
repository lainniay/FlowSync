package hgc.flowsync.task;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskLogMapper extends BaseMapper<TaskLog> {

	@Select({
		"<script>",
		"SELECT task_logs.id, task_logs.operator_id AS operatorId,",
		"task_logs.progress_percent AS progressPercent, task_logs.created_at AS createdAt,",
		"tasks.title AS taskTitle",
		"FROM task_logs JOIN tasks ON tasks.id = task_logs.task_id",
		"WHERE tasks.project_id IN",
		"<foreach collection='projectIds' item='projectId' open='(' separator=',' close=')'>",
		"#{projectId}",
		"</foreach>",
		"ORDER BY task_logs.created_at DESC, task_logs.id DESC LIMIT 10",
		"</script>"
	})
	List<RecentActivity> selectRecentActivitiesQuery(@Param("projectIds") List<Long> projectIds);

	default List<RecentActivity> selectRecentActivities(List<Long> projectIds) {
		return projectIds == null || projectIds.isEmpty()
			? List.of() : selectRecentActivitiesQuery(projectIds);
	}

	class RecentActivity {
		private Long id;
		private Long operatorId;
		private int progressPercent;
		private LocalDateTime createdAt;
		private String taskTitle;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Long getOperatorId() {
			return operatorId;
		}

		public void setOperatorId(Long operatorId) {
			this.operatorId = operatorId;
		}

		public int getProgressPercent() {
			return progressPercent;
		}

		public void setProgressPercent(int progressPercent) {
			this.progressPercent = progressPercent;
		}

		public LocalDateTime getCreatedAt() {
			return createdAt;
		}

		public void setCreatedAt(LocalDateTime createdAt) {
			this.createdAt = createdAt;
		}

		public String getTaskTitle() {
			return taskTitle;
		}

		public void setTaskTitle(String taskTitle) {
			this.taskTitle = taskTitle;
		}
	}
}
