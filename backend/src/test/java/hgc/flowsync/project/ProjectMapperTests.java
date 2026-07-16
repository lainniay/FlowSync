package hgc.flowsync.project;

import java.time.LocalDate;
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
class ProjectMapperTests {

	private final ProjectMapper projectMapper;
	private final UserMapper userMapper;

	@Autowired
	ProjectMapperTests(ProjectMapper projectMapper, UserMapper userMapper) {
		this.projectMapper = projectMapper;
		this.userMapper = userMapper;
	}

	@Test
	void insertAndSelectMapProjectFields() {
		User owner = new User();
		owner.setUsername("project-owner-" + UUID.randomUUID());
		owner.setPasswordHash("test-password-hash");
		owner.setDisplayName("Project Owner");
		owner.setSystemRole(SystemRole.USER);
		assertThat(userMapper.insert(owner)).isOne();

		LocalDateTime archivedAt = LocalDateTime.of(2026, 7, 16, 12, 0);
		Project project = new Project();
		project.setOwnerId(owner.getId());
		project.setName("Mapper Project");
		project.setDescription("Project mapping test");
		project.setStatus(ProjectStatus.IN_PROGRESS);
		project.setPriority(Priority.HIGH);
		project.setStartDate(LocalDate.of(2026, 7, 1));
		project.setEndDate(LocalDate.of(2026, 7, 31));
		project.setArchivedAt(archivedAt);

		assertThat(projectMapper.insert(project)).isOne();
		assertThat(project.getId()).isNotNull();

		Project saved = projectMapper.selectById(project.getId());
		assertThat(saved.getOwnerId()).isEqualTo(owner.getId());
		assertThat(saved.getName()).isEqualTo("Mapper Project");
		assertThat(saved.getDescription()).isEqualTo("Project mapping test");
		assertThat(saved.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
		assertThat(saved.getPriority()).isEqualTo(Priority.HIGH);
		assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
		assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 31));
		assertThat(saved.getArchivedAt()).isEqualTo(archivedAt);
		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}
}
