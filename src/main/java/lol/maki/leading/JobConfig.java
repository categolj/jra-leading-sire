package lol.maki.leading;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods = false)
public class JobConfig {

	@Bean
	public Step scrapeStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			JraLeadingSireScrapeTasklet tasklet) {
		return new StepBuilder("Scrape", jobRepository).tasklet(tasklet, transactionManager).build();
	}

	@Bean
	public Job scrapeJob(JobRepository jobRepository, Step scrapeStep) {
		return new JobBuilder("JraLeadingSireScrape", jobRepository).incrementer(new RunIdIncrementer())
			.start(scrapeStep)
			.build();
	}

}
