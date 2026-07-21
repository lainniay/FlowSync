package hgc.flowsync.project;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

	default boolean existsByUserId(Long userId) {
		return selectCount(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getUserId, userId)) > 0;
	}

	default boolean existsByUserIdForUpdate(Long userId) {
		return !selectList(Wrappers.<ProjectMember>lambdaQuery()
			.select(ProjectMember::getId)
			.eq(ProjectMember::getUserId, userId)
			.last("LIMIT 1 FOR UPDATE")).isEmpty();
	}

	default boolean existsByProjectIdAndUserId(Long projectId, Long userId) {
		return selectCount(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, projectId)
			.eq(ProjectMember::getUserId, userId)) > 0;
	}

	default boolean existsByProjectIdAndUserIdForUpdate(Long projectId, Long userId) {
		return selectOne(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, projectId)
			.eq(ProjectMember::getUserId, userId)
			.last("FOR UPDATE")) != null;
	}
}
