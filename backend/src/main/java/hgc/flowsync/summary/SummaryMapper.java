package hgc.flowsync.summary;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SummaryMapper extends BaseMapper<Summary> {

	default long countByProjectIds(List<Long> projectIds) {
		return projectIds == null || projectIds.isEmpty() ? 0
			: selectCount(Wrappers.<Summary>lambdaQuery().in(Summary::getProjectId, projectIds));
	}

	default List<Summary> selectRecentByProjectIds(List<Long> projectIds) {
		return projectIds == null || projectIds.isEmpty() ? List.of()
			: selectList(Wrappers.<Summary>lambdaQuery()
				.in(Summary::getProjectId, projectIds)
				.orderByDesc(Summary::getCreatedAt, Summary::getId)
				.last("LIMIT 10"));
	}
}
