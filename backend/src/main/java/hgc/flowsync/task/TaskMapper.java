package hgc.flowsync.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

	default boolean existsIncompleteByAssigneeId(Long assigneeId) {
		return selectCount(Wrappers.<Task>lambdaQuery()
			.eq(Task::getAssigneeId, assigneeId)
			.notIn(Task::getStatus, TaskStatus.COMPLETED, TaskStatus.CANCELLED)) > 0;
	}
}
