package hgc.flowsync.summary;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import hgc.flowsync.common.error.BusinessException;
import hgc.flowsync.common.error.ErrorCode;
import hgc.flowsync.project.Priority;
import hgc.flowsync.project.Project;
import hgc.flowsync.project.ProjectAccessService;
import hgc.flowsync.project.ProjectMapper;
import hgc.flowsync.project.ProjectMember;
import hgc.flowsync.project.ProjectMemberMapper;
import hgc.flowsync.project.ProjectStatus;
import hgc.flowsync.user.CurrentUserService;
import hgc.flowsync.user.SystemRole;
import hgc.flowsync.user.User;
import hgc.flowsync.user.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryAccessServiceTests {

	private static final long PROJECT_ID = 101L;
	private static final long SUMMARY_ID = 701L;

	@Mock
	private SummaryMapper summaryMapper;
	@Mock
	private CurrentUserService currentUserService;
	@Mock
	private ProjectAccessService projectAccessService;
	@Mock
	private Authentication authentication;

	private SummaryAccessService summaryAccessService;
	private Project project;
	private Summary summary;
	private User owner;
	private User creator;
	private User member;
	private User admin;
	private User outsider;

	@BeforeEach
	void setUp() {
		summaryAccessService = new SummaryAccessService(
			summaryMapper,
			currentUserService,
			projectAccessService);
		owner = user(1L, SystemRole.USER);
		creator = user(2L, SystemRole.USER);
		member = user(3L, SystemRole.USER);
		admin = user(4L, SystemRole.ADMIN);
		outsider = user(5L, SystemRole.USER);

		project = new Project();
		project.setId(PROJECT_ID);
		project.setOwnerId(owner.getId());
		summary = new Summary();
		summary.setId(SUMMARY_ID);
		summary.setProjectId(PROJECT_ID);
		summary.setCreatedBy(creator.getId());
	}

	@Test
	void missingSummaryReturnsNotFoundWithoutLoadingProject() {
		when(currentUserService.require(authentication)).thenReturn(member);

		assertBusinessError(
			() -> summaryAccessService.requireReadable(authentication, SUMMARY_ID),
			ErrorCode.NOT_FOUND);

		verify(projectAccessService, never()).requireProject(any());
	}

	@Test
	void memberAndAdminCanReadWhileOutsiderCannotSeeTheSummary() {
		stubRead(member);
		when(projectAccessService.isMember(project, member)).thenReturn(true);
		SummaryAccessService.SummaryContext memberContext =
			summaryAccessService.requireReadable(authentication, SUMMARY_ID);
		assertThat(memberContext.summary()).isSameAs(summary);
		assertThat(memberContext.project()).isSameAs(project);

		when(currentUserService.require(authentication)).thenReturn(admin);
		when(projectAccessService.isAdmin(admin)).thenReturn(true);
		assertThat(summaryAccessService.requireReadable(authentication, SUMMARY_ID).currentUser())
			.isSameAs(admin);

		when(currentUserService.require(authentication)).thenReturn(outsider);
		when(projectAccessService.isAdmin(outsider)).thenReturn(false);
		when(projectAccessService.isMember(project, outsider)).thenReturn(false);
		assertBusinessError(
			() -> summaryAccessService.requireReadable(authentication, SUMMARY_ID),
			ErrorCode.NOT_FOUND);
	}

	@Test
	void onlyCurrentUserMembersCanCreateAndAdminCannotWrite() {
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);
		when(currentUserService.requireForUpdate(authentication)).thenReturn(member);
		when(projectAccessService.isMember(project, member)).thenReturn(true);
		assertThat(summaryAccessService.requireCreatable(authentication, PROJECT_ID).currentUser())
			.isSameAs(member);

		when(currentUserService.requireForUpdate(authentication)).thenReturn(admin);
		when(projectAccessService.isAdmin(admin)).thenReturn(true);
		assertBusinessError(
			() -> summaryAccessService.requireCreatable(authentication, PROJECT_ID),
			ErrorCode.FORBIDDEN);

		when(currentUserService.requireForUpdate(authentication)).thenReturn(outsider);
		when(projectAccessService.isAdmin(outsider)).thenReturn(false);
		when(projectAccessService.isMember(project, outsider)).thenReturn(false);
		assertBusinessError(
			() -> summaryAccessService.requireCreatable(authentication, PROJECT_ID),
			ErrorCode.NOT_FOUND);
	}

	@Test
	void creatorAndOwnerCanWriteButOtherVisibleMemberCannot() {
		stubWrite(creator);
		when(projectAccessService.isMember(project, creator)).thenReturn(true);
		assertThat(summaryAccessService.requireWritable(authentication, SUMMARY_ID).summary())
			.isSameAs(summary);

		when(currentUserService.requireForUpdate(authentication)).thenReturn(owner);
		when(projectAccessService.isMember(project, owner)).thenReturn(true);
		when(projectAccessService.isOwner(project, owner)).thenReturn(true);
		assertThatCode(() -> summaryAccessService.requireWritable(authentication, SUMMARY_ID))
			.doesNotThrowAnyException();

		when(currentUserService.requireForUpdate(authentication)).thenReturn(member);
		when(projectAccessService.isMember(project, member)).thenReturn(true);
		when(projectAccessService.isOwner(project, member)).thenReturn(false);
		assertBusinessError(
			() -> summaryAccessService.requireWritable(authentication, SUMMARY_ID),
			ErrorCode.FORBIDDEN);
	}

	@Test
	void removedCreatorAndOutsiderAreHiddenBeforeArchiveStateIsChecked() {
		project.setArchivedAt(LocalDateTime.now());
		stubWrite(creator);
		when(projectAccessService.isMember(project, creator)).thenReturn(false);
		assertBusinessError(
			() -> summaryAccessService.requireWritable(authentication, SUMMARY_ID),
			ErrorCode.NOT_FOUND);

		when(currentUserService.requireForUpdate(authentication)).thenReturn(outsider);
		when(projectAccessService.isMember(project, outsider)).thenReturn(false);
		assertBusinessError(
			() -> summaryAccessService.requireWritable(authentication, SUMMARY_ID),
			ErrorCode.NOT_FOUND);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	@Test
	void adminWriteIsForbiddenBeforeArchiveStateIsChecked() {
		project.setArchivedAt(LocalDateTime.now());
		stubWrite(admin);
		when(projectAccessService.isAdmin(admin)).thenReturn(true);

		assertBusinessError(
			() -> summaryAccessService.requireWritable(authentication, SUMMARY_ID),
			ErrorCode.FORBIDDEN);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	@Test
	void archivedProjectBlocksAuthorizedCreateAndExistingSummaryWrites() {
		project.setArchivedAt(LocalDateTime.now());
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);
		when(currentUserService.requireForUpdate(authentication)).thenReturn(creator);
		when(projectAccessService.isMember(project, creator)).thenReturn(true);
		doThrow(new BusinessException(ErrorCode.PROJECT_ARCHIVED))
			.when(projectAccessService).requireUnarchived(project);

		assertBusinessError(
			() -> summaryAccessService.requireCreatable(authentication, PROJECT_ID),
			ErrorCode.PROJECT_ARCHIVED);
		when(summaryMapper.selectById(SUMMARY_ID)).thenReturn(summary);
		when(summaryMapper.selectOne(any())).thenReturn(summary);
		assertBusinessError(
			() -> summaryAccessService.requireWritable(authentication, SUMMARY_ID),
			ErrorCode.PROJECT_ARCHIVED);
	}

	@Test
	void writeLocksUserThenProjectThenReloadsAndLocksSummary() {
		stubWrite(creator);
		when(projectAccessService.isMember(project, creator)).thenReturn(true);

		summaryAccessService.requireWritable(authentication, SUMMARY_ID);

		var order = inOrder(currentUserService, summaryMapper, projectAccessService);
		order.verify(currentUserService).requireForUpdate(authentication);
		order.verify(summaryMapper).selectById(SUMMARY_ID);
		order.verify(projectAccessService).requireProjectForUpdate(PROJECT_ID);
		order.verify(summaryMapper).selectOne(any());
	}

	@Test
	void lockedReloadThatMovedProjectsIsHidden() {
		Summary moved = new Summary();
		moved.setId(SUMMARY_ID);
		moved.setProjectId(PROJECT_ID + 1);
		moved.setCreatedBy(creator.getId());
		when(currentUserService.requireForUpdate(authentication)).thenReturn(creator);
		when(summaryMapper.selectById(SUMMARY_ID)).thenReturn(summary);
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);
		when(summaryMapper.selectOne(any())).thenReturn(moved);

		assertBusinessError(
			() -> summaryAccessService.requireWritable(authentication, SUMMARY_ID),
			ErrorCode.NOT_FOUND);
		verify(projectAccessService, never()).requireUnarchived(project);
	}

	private void stubRead(User currentUser) {
		when(currentUserService.require(authentication)).thenReturn(currentUser);
		when(summaryMapper.selectById(SUMMARY_ID)).thenReturn(summary);
		when(projectAccessService.requireProject(PROJECT_ID)).thenReturn(project);
	}

	private void stubWrite(User currentUser) {
		when(currentUserService.requireForUpdate(authentication)).thenReturn(currentUser);
		when(summaryMapper.selectById(SUMMARY_ID)).thenReturn(summary);
		when(projectAccessService.requireProjectForUpdate(PROJECT_ID)).thenReturn(project);
		when(summaryMapper.selectOne(any())).thenReturn(summary);
	}

	private static User user(Long id, SystemRole role) {
		User user = new User();
		user.setId(id);
		user.setSystemRole(role);
		return user;
	}

	private static void assertBusinessError(
		org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
		ErrorCode expected) {
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.code()).isEqualTo(expected));
	}

	@Nested
	@SpringBootTest
	class LockingTests {

		private final SummaryAccessService accessService;
		private final SummaryMapper realSummaryMapper;
		private final ProjectMapper projectMapper;
		private final ProjectMemberMapper projectMemberMapper;
		private final UserMapper userMapper;
		private final TransactionTemplate transactionTemplate;

		private Long summaryId;
		private Long projectId;
		private Long ownerId;
		private String ownerUsername;

		@Autowired
		LockingTests(
			SummaryAccessService accessService,
			SummaryMapper summaryMapper,
			ProjectMapper projectMapper,
			ProjectMemberMapper projectMemberMapper,
			UserMapper userMapper,
			PlatformTransactionManager transactionManager) {
			this.accessService = accessService;
			this.realSummaryMapper = summaryMapper;
			this.projectMapper = projectMapper;
			this.projectMemberMapper = projectMemberMapper;
			this.userMapper = userMapper;
			this.transactionTemplate = new TransactionTemplate(transactionManager);
		}

		@BeforeEach
		void createSummary() {
			transactionTemplate.executeWithoutResult(status -> {
				User owner = new User();
				ownerUsername = "summary-lock-" + UUID.randomUUID();
				owner.setUsername(ownerUsername);
				owner.setPasswordHash("test-password-hash");
				owner.setDisplayName("Summary Lock Owner");
				owner.setSystemRole(SystemRole.USER);
				userMapper.insert(owner);
				ownerId = owner.getId();

				Project project = new Project();
				project.setOwnerId(ownerId);
				project.setName("Summary Lock Project");
				project.setStatus(ProjectStatus.IN_PROGRESS);
				project.setPriority(Priority.MEDIUM);
				projectMapper.insert(project);
				projectId = project.getId();

				ProjectMember member = new ProjectMember();
				member.setProjectId(projectId);
				member.setUserId(ownerId);
				projectMemberMapper.insert(member);

				Summary entity = new Summary();
				entity.setProjectId(projectId);
				entity.setCreatedBy(ownerId);
				entity.setType(SummaryType.STAGE);
				entity.setContent("Locked summary");
				realSummaryMapper.insert(entity);
				summaryId = entity.getId();
			});
		}

		@AfterEach
		void removeSummary() {
			if (projectId == null) {
				return;
			}
			transactionTemplate.executeWithoutResult(status -> {
				realSummaryMapper.delete(Wrappers.<Summary>lambdaQuery()
					.eq(Summary::getProjectId, projectId));
				projectMemberMapper.delete(Wrappers.<ProjectMember>lambdaQuery()
					.eq(ProjectMember::getProjectId, projectId));
				projectMapper.deleteById(projectId);
				userMapper.deleteById(ownerId);
			});
		}

		@Test
		void writeAccessRequiresOuterTransactionWhileReadDoesNot() {
			assertThatThrownBy(() -> accessService.requireCreatable(authentication(), projectId))
				.isInstanceOf(IllegalTransactionStateException.class);
			assertThatThrownBy(() -> accessService.requireWritable(authentication(), summaryId))
				.isInstanceOf(IllegalTransactionStateException.class);
			assertThat(accessService.requireReadable(authentication(), summaryId).summary().getId())
				.isEqualTo(summaryId);

			transactionTemplate.executeWithoutResult(status -> {
				assertThatCode(() -> accessService.requireCreatable(authentication(), projectId))
					.doesNotThrowAnyException();
				assertThatCode(() -> accessService.requireWritable(authentication(), summaryId))
					.doesNotThrowAnyException();
			});
		}

		@Test
		void writableContextHoldsLocksUntilTransactionCompletes() throws Exception {
			CountDownLatch accessGranted = new CountDownLatch(1);
			CountDownLatch releaseAccess = new CountDownLatch(1);
			ExecutorService executor = Executors.newFixedThreadPool(2);
			try {
				Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
					accessService.requireWritable(authentication(), summaryId);
					accessGranted.countDown();
					await(releaseAccess);
				}));
				assertThat(accessGranted.await(5, TimeUnit.SECONDS)).isTrue();

				Future<Integer> deletion = executor.submit(() -> transactionTemplate.execute(status ->
					realSummaryMapper.deleteById(summaryId)));
				assertThatThrownBy(() -> deletion.get(500, TimeUnit.MILLISECONDS))
					.isInstanceOf(TimeoutException.class);

				releaseAccess.countDown();
				holder.get(5, TimeUnit.SECONDS);
				assertThat(deletion.get(5, TimeUnit.SECONDS)).isOne();
			} finally {
				releaseAccess.countDown();
				executor.shutdownNow();
				executor.awaitTermination(5, TimeUnit.SECONDS);
			}
		}

		private Authentication authentication() {
			return UsernamePasswordAuthenticationToken.authenticated(ownerUsername, "", List.of());
		}

		private static void await(CountDownLatch latch) {
			try {
				if (!latch.await(5, TimeUnit.SECONDS)) {
					throw new AssertionError("Timed out waiting to release summary access");
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new AssertionError(exception);
			}
		}
	}
}
