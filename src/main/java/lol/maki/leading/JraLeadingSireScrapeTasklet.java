package lol.maki.leading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class JraLeadingSireScrapeTasklet extends AbstractJraLeadingSireScrapeTasklet {

	public JraLeadingSireScrapeTasklet(@Value("#{jobParameters['dir']?:null}") String dir, ObjectMapper objectMapper) {
		super(dir, objectMapper);
	}

	@Override
	protected void performAdditionalPageActions(Page page) {
		// No additional actions needed for the default leading sire scraping
	}

	@Override
	protected String getOutputFileName(LocalDate date) {
		return date + ".json";
	}

}
