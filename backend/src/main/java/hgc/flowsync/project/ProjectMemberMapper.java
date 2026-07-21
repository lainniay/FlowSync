package hgc.flowsync.project;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

	@Select({
		"<script>",
		"SELECT project_id AS projectId, COUNT(*) AS memberCount FROM project_members",
		"WHERE project_id IN",
		"<foreach collection='projectIds' item='projectId' open='(' separator=',' close=')'>",
		"#{projectId}",
		"</foreach>",
		"GROUP BY project_id",
		"</script>"
	})
	List<ProjectMemberCount> countByProjectIdsQuery(@Param("projectIds") List<Long> projectIds);

	@Select({
		"<script>",
		"SELECT COUNT(DISTINCT user_id) FROM project_members WHERE project_id IN",
		"<foreach collection='projectIds' item='projectId' open='(' separator=',' close=')'>",
		"#{projectId}",
		"</foreach>",
		"</script>"
	})
	long countDistinctUsersByProjectIdsQuery(@Param("projectIds") List<Long> projectIds);

	default List<ProjectMemberCount> countByProjectIds(List<Long> projectIds) {
		return projectIds == null || projectIds.isEmpty() ? List.of() : countByProjectIdsQuery(projectIds);
	}

	default long countDistinctUsersByProjectIds(List<Long> projectIds) {
		return projectIds == null || projectIds.isEmpty()
			? 0 : countDistinctUsersByProjectIdsQuery(projectIds);
	}

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

	class ProjectMemberCount {
		private Long projectId;
		private long memberCount;

		public Long getProjectId() {
			return projectId;
		}

		public void setProjectId(Long projectId) {
			this.projectId = projectId;
		}

		public long getMemberCount() {
			return memberCount;
		}

		public void setMemberCount(long memberCount) {
			this.memberCount = memberCount;
		}
	}
}
