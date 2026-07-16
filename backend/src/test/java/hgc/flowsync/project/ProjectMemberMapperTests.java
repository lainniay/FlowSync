package hgc.flowsync.project;

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
class ProjectMemberMapperTests {

	private final ProjectMapper projectMapper;
	private final ProjectMemberMapper projectMemberMapper;
	private final UserMapper userMapper;

	@Autowired
	ProjectMemberMapperTests(ProjectMapper projectMapper, ProjectMemberMapper projectMemberMapper,
			UserMapper userMapper) {
		this.projectMapper = projectMapper;
		this.projectMemberMapper = projectMemberMapper;
		this.userMapper = userMapper;
	}

	@Test
	void insertAndSelectMapProjectMemberFields() {
		User user = new User();
		user.setUsername("member-" + UUID.randomUUID());
		user.setPasswordHash("test-password-hash");
		user.setDisplayName("Project Member");
		user.setSystemRole(SystemRole.USER);
		assertThat(userMapper.insert(user)).isOne();

		Project project = new Project();
		project.setOwnerId(user.getId());
		project.setName("Member Mapper Project");
		project.setStatus(ProjectStatus.NOT_STARTED);
		project.setPriority(Priority.MEDIUM);
		assertThat(projectMapper.insert(project)).isOne();

		ProjectMember projectMember = new ProjectMember();
		projectMember.setProjectId(project.getId());
		projectMember.setUserId(user.getId());

		assertThat(projectMemberMapper.insert(projectMember)).isOne();
		assertThat(projectMember.getId()).isNotNull();

		ProjectMember saved = projectMemberMapper.selectById(projectMember.getId());
		assertThat(saved.getProjectId()).isEqualTo(project.getId());
		assertThat(saved.getUserId()).isEqualTo(user.getId());
		assertThat(saved.getJoinedAt()).isNotNull();
	}
}
