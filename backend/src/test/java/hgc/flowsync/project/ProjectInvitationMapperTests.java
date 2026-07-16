package hgc.flowsync.project;

import java.time.LocalDateTime;
import java.util.UUID;

import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ProjectInvitationMapperTests {

	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final ProjectInvitationMapper projectInvitationMapper;
	private final UserMapper userMapper;

	@Autowired
	ProjectInvitationMapperTests(ProjectMapper projectMapper, ProjectMemberMapper projectMemberMapper,
			ProjectInvitationMapper projectInvitationMapper, UserMapper userMapper) {
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.projectInvitationMapper = projectInvitationMapper;
		this.userMapper = userMapper;
	}

	@Test
	void insertAndSelectMapProjectInvitationFields() {
		User inviter = new User();
		inviter.setUsername("inviter-" + UUID.randomUUID());
		inviter.setPasswordHash("test-password-hash");
		inviter.setDisplayName("Project Owner");
		inviter.setSystemRole(SystemRole.USER);
		assertThat(userMapper.insert(inviter)).isOne();

		User invitee = new User();
		invitee.setUsername("invitee-" + UUID.randomUUID());
		invitee.setPasswordHash("test-password-hash");
		invitee.setDisplayName("Project Invitee");
		invitee.setSystemRole(SystemRole.USER);
		assertThat(userMapper.insert(invitee)).isOne();

		Project project = new Project();
		project.setOwnerId(inviter.getId());
		project.setName("Invitation Mapper Project");
		project.setStatus(ProjectStatus.NOT_STARTED);
		project.setPriority(Priority.MEDIUM);
		assertThat(projectMapper.insert(project)).isOne();

		ProjectMember ownerMember = new ProjectMember();
		ownerMember.setProjectId(project.getId());
		ownerMember.setUserId(inviter.getId());
		assertThat(projectMemberMapper.insert(ownerMember)).isOne();

		ProjectMember inviteeMember = new ProjectMember();
		inviteeMember.setProjectId(project.getId());
		inviteeMember.setUserId(invitee.getId());
		assertThat(projectMemberMapper.insert(inviteeMember)).isOne();

		LocalDateTime respondedAt = LocalDateTime.of(2026, 7, 16, 12, 0);
		ProjectInvitation invitation = new ProjectInvitation();
		invitation.setProjectId(project.getId());
		invitation.setInviteeId(invitee.getId());
		invitation.setInvitedBy(inviter.getId());
		invitation.setStatus(InvitationStatus.ACCEPTED);
		invitation.setRespondedAt(respondedAt);

		assertThat(projectInvitationMapper.insert(invitation)).isOne();
		assertThat(invitation.getId()).isNotNull();

		ProjectInvitation saved = projectInvitationMapper.selectById(invitation.getId());
		assertThat(saved.getProjectId()).isEqualTo(project.getId());
		assertThat(saved.getInviteeId()).isEqualTo(invitee.getId());
		assertThat(saved.getInvitedBy()).isEqualTo(inviter.getId());
		assertThat(saved.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getRespondedAt()).isEqualTo(respondedAt);
	}
}
