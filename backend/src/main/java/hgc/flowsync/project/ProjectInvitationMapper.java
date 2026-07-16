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
}
