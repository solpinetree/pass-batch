package com.sol.passbatch.job.pass;

import com.sol.passbatch.repository.pass.PassEntity;
import com.sol.passbatch.repository.pass.PassStatus;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.Map;

@RequiredArgsConstructor
@Configuration
public class AddPassesJobConfig {

    private final int CHUNK_SIZE = 5;

    private final EntityManagerFactory entityManagerFactory;

    private final AddPassesTasklet addPassesTasklet;

    @Bean
    public Job addPassesJob(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager) {
        return new JobBuilder("addPassesJob", jobRepository)
                .start(addPassesStep(jobRepository, platformTransactionManager))
                .build();
    }

    @Bean
    public Step addPassesStep(JobRepository jobRepository, PlatformTransactionManager platformTransactionManager) {
        return new StepBuilder("addPassesStep", jobRepository)
                .tasklet(addPassesTasklet)
                .build();
    }

}
