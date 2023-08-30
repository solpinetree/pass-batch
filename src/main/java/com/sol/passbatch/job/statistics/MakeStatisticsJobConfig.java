package com.sol.passbatch.job.statistics;

import com.sol.passbatch.repository.booking.BookingEntity;
import com.sol.passbatch.repository.statistics.StatisticsEntity;
import com.sol.passbatch.repository.statistics.StatisticsRepository;
import com.sol.passbatch.util.LocalDateTimeUtils;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class MakeStatisticsJobConfig {

    private final int CHUNK_SIZE = 10;

    private final EntityManagerFactory entityManagerFactory;
    private final StatisticsRepository statisticsRepository;
    private final MakeDailyStatisticsTasklet makeDailyStatisticsTasklet;
    private final MakeWeeklyStatisticsTasklet makeWeeklyStatisticsTasklet;

    @Bean
    public Job makeStatisticsJob(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager) {
        Flow addStatisticsFlow = new FlowBuilder<Flow>("addStatisticsFlow")
                .start(addStatisticsStep(jobRepository, platformTransactionManager))
                .build();

        Flow makeDailyStatisticsFlow = new FlowBuilder<Flow>("makeDailyStatisticsFlow")
                .start(addStatisticsStep(jobRepository, platformTransactionManager))
                .build();

        Flow makeWeeklyStatisticsFlow = new FlowBuilder<Flow>("makeWeeklyStatisticsFlow")
                .start(addStatisticsStep(jobRepository, platformTransactionManager))
                .build();

        Flow parallelMakeStatisticsFlow = new FlowBuilder<Flow>("parallelMakeStatisticsFlow")
                .split(new SimpleAsyncTaskExecutor())
                .add(makeDailyStatisticsFlow, makeWeeklyStatisticsFlow)
                .build();

        return new JobBuilder("makeStatisticsJob")
                .start(addStatisticsFlow)
                .next(parallelMakeStatisticsFlow)
                .build()
                .build();
    }

    @Bean
    public Step addStatisticsStep(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager) {
        return new StepBuilder("addStatisticsStep", jobRepository)
                .<BookingEntity, BookingEntity>chunk(CHUNK_SIZE, platformTransactionManager)
                .reader(addStatisticsItemReader(null, null))
                .writer(addStatisticsItemWriter())
                .build();
    }

    @Bean
    @StepScope // job parameter 사용지 **반드시** BeanScope 정의를 해줘야함
    public JpaCursorItemReader<BookingEntity> addStatisticsItemReader(@Value("#{jobParameters[from]}") String fromString, @Value("#{jobParameters[to]}") String toString) {
        final LocalDateTime from = LocalDateTimeUtils.parse(fromString);
        final LocalDateTime to = LocalDateTimeUtils.parse(toString);

        return new JpaCursorItemReaderBuilder<BookingEntity>()
                .name("addStatisticsItemReader")
                .entityManagerFactory(entityManagerFactory)
                // JobParameter를 받아 종료 일시(endedAt) 기준으로 통계 대상 예약(Booking)을 조회
                .queryString("select b from BookingEntity b where b.endedAt between :from and :to")
                .parameterValues(Map.of("from", from, "to", to))
                .build();
    }

    @Bean
    public ItemWriter<BookingEntity> addStatisticsItemWriter() {
        return bookingEntities -> {
            Map<LocalDateTime, StatisticsEntity> statisticsEntityMap = new LinkedHashMap<>(); // key가 순서대로 나열이 됐으면 해서 linkedHashMap으로 저장

            for (BookingEntity bookingEntity : bookingEntities) {
                final LocalDateTime statisticsAt = bookingEntity.getStatisticsAt();
                StatisticsEntity statisticsEntity = statisticsEntityMap.get(statisticsAt);

                if (statisticsAt == null) {
                    statisticsEntityMap.put(statisticsAt, StatisticsEntity.create(bookingEntity));
                } else {
                    statisticsEntity.add(bookingEntity);
                }
            }

            final List<StatisticsEntity> statisticsEntities = new ArrayList<>(statisticsEntityMap.values());
            statisticsRepository.saveAll(statisticsEntities);
        };
    }

    @Bean
    public Step makeDailyStatisticsStep((JobRepository jobRepository, PlatformTransactionManager platformTransactionManager) {
        return new StepBuilder("makeDailyStatisticsStep", jobRepository)
                .tasklet(makeDailyStatisticsTasklet)
                .build();
    }

    @Bean
    public Step makeWeeklyStatisticsStep((JobRepository jobRepository, PlatformTransactionManager platformTransactionManager) {
        return new StepBuilder("makeWeeklyStatisticsStep", jobRepository)
                .tasklet(makeWeeklyStatisticsTasklet)
                .build();
    }
}
