package hgc.flowsync.project;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

	default boolean existsByOwnerId(Long ownerId) {
		return selectCount(Wrappers.<Project>lambdaQuery()
			.eq(Project::getOwnerId, ownerId)) > 0;
	}
}
