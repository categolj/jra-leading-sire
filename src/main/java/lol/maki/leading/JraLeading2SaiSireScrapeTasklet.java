package lol.maki.leading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@StepScope
public class JraLeading2SaiSireScrapeTasklet extends AbstractJraLeadingSireScrapeTasklet {

	public JraLeading2SaiSireScrapeTasklet(@Value("#{jobParameters['dir-2sai']?:null}") String dir,
			ObjectMapper objectMapper) {
		super(dir, objectMapper);
	}

	@Override
	protected void performAdditionalPageActions(Page page) {
		page.locator("a:has-text(\"2歳\")").click();
	}

	@Override
	protected String getOutputFileName(LocalDate date) {
		return date + "_2sai.json";
	}

}
