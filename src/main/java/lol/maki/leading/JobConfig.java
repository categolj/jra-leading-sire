package lol.maki.leading;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JobConfig {

	@Bean
	public Step scrapeStep(JobRepository jobRepository, JraLeadingSireScrapeTasklet tasklet) {
		return new StepBuilder("Scrape", jobRepository).tasklet(tasklet).build();
	}

	@Bean
	public Step scrape2SaiStep(JobRepository jobRepository, JraLeading2SaiSireScrapeTasklet tasklet) {
		return new StepBuilder("Scrape2Sai", jobRepository).tasklet(tasklet).build();
	}

	@Bean
	public Job scrapeJob(JobRepository jobRepository, Step scrapeStep, Step scrape2SaiStep) {
		return new JobBuilder("JraLeadingSireScrape", jobRepository).incrementer(new RunIdIncrementer())
			.start(scrapeStep)
			// .next(scrape2SaiStep)
			.build();
	}

}
