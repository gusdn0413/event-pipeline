package com.hyun.eventpipeline.consumer.mapper;

import com.hyun.eventpipeline.consumer.model.ApiLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ApiLogMapper {

    void insertApiLogBulk(@Param("logs") List<ApiLog> apiLogs);
}
