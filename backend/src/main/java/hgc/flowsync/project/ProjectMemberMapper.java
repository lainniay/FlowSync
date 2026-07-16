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

	default boolean existsByProjectIdAndUserId(Long projectId, Long userId) {
		return selectCount(Wrappers.<ProjectMember>lambdaQuery()
			.eq(ProjectMember::getProjectId, projectId)
			.eq(ProjectMember::getUserId, userId)) > 0;
	}
}
