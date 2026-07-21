package hgc.flowsync.project;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectInvitationMapper extends BaseMapper<ProjectInvitation> {

	default boolean existsPendingByInviteeId(Long inviteeId) {
		return selectCount(Wrappers.<ProjectInvitation>lambdaQuery()
			.eq(ProjectInvitation::getInviteeId, inviteeId)
			.eq(ProjectInvitation::getStatus, InvitationStatus.PENDING)) > 0;
	}

	default boolean existsPendingByInviteeIdForUpdate(Long inviteeId) {
		return !selectList(Wrappers.<ProjectInvitation>lambdaQuery()
			.select(ProjectInvitation::getId)
			.eq(ProjectInvitation::getInviteeId, inviteeId)
			.eq(ProjectInvitation::getStatus, InvitationStatus.PENDING)
			.last("LIMIT 1 FOR UPDATE")).isEmpty();
	}

	default ProjectInvitation selectByProjectIdAndInviteeIdForUpdate(Long projectId, Long inviteeId) {
		return selectOne(Wrappers.<ProjectInvitation>lambdaQuery()
			.eq(ProjectInvitation::getProjectId, projectId)
			.eq(ProjectInvitation::getInviteeId, inviteeId)
			.last("FOR UPDATE"));
	}
}
