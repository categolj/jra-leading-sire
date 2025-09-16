package lol.maki.leading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.util.StringUtils;

public abstract class AbstractJraLeadingSireScrapeTasklet implements Tasklet {

	protected final String dir;

	protected final ObjectMapper objectMapper;

	protected final Logger log;

	public AbstractJraLeadingSireScrapeTasklet(String dir, ObjectMapper objectMapper) {
		this.dir = dir;
		this.objectMapper = objectMapper;
		this.log = LoggerFactory.getLogger(this.getClass());
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
		log.info("Scraper started");
		ArrayNode jsonArray = this.objectMapper.createArrayNode();
		AtomicReference<LocalDate> date = new AtomicReference<>();

		if (StringUtils.hasText(this.dir)) {
			log.info("Loading {}", dir);
			try (Stream<Path> list = Files.list(Path.of(dir))) {
				Pattern pattern = Pattern.compile("([0-9]+)\\.html$");
				list.filter(Files::isRegularFile)
					.filter(f -> f.getFileName().toString().endsWith(".html"))
					.sorted(Comparator.comparingInt(path -> {
						Matcher matcher = pattern.matcher(path.getFileName().toString());
						return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
					}))
					.forEachOrdered(file -> {
						try {
							log.info("Parsing file {}", file.getFileName());
							Document doc = Jsoup.parse(file);
							parseDocument(doc, jsonArray);
							if (date.get() == null) {
								targetDate(doc.select("#leading_horse").html()).ifPresent(date::set);
							}
						}
						catch (IOException e) {
							throw new UncheckedIOException(e.getMessage(), e);
						}
					});
			}
		}
		else {
			Playwright playwright = Playwright.create();
			Browser browser = playwright.chromium().launch();
			BrowserContext context = browser.newContext();
			context.setDefaultTimeout(30_000);
			try {
				Page page = context.newPage();
				page.navigate("https://www.jra.go.jp/datafile/leading/");
				page.locator("a:has-text(\"種牡馬\")").click();
				performAdditionalPageActions(page);
				Optional<LocalDate> targetDate = targetDate(page.innerHTML("#leading_horse"));
				targetDate.ifPresent(date::set);
				parsePage(page, jsonArray);
			}
			finally {
				context.close();
				playwright.close();
			}
		}

		if (date.get() == null) {
			log.error("Unable to find date");
		}
		else {
			String json = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonArray);
			String fileName = getOutputFileName(date.get());
			log.info("output to {}", fileName);
			Files.copy(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Path.of(fileName),
					StandardCopyOption.REPLACE_EXISTING);
		}
		return RepeatStatus.FINISHED;
	}

	protected abstract void performAdditionalPageActions(Page page);

	protected abstract String getOutputFileName(LocalDate date);

	Optional<LocalDate> targetDate(String html) {
		Pattern pattern = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日現在");
		Matcher matcher = pattern.matcher(html);
		if (!matcher.find()) {
			return Optional.empty();
		}
		int year = Integer.parseInt(matcher.group(1));
		int month = Integer.parseInt(matcher.group(2));
		int day = Integer.parseInt(matcher.group(3));
		LocalDate date = LocalDate.of(year, month, day);
		return Optional.of(date);
	}

	void parsePage(Page page, ArrayNode jsonArray) throws Exception {
		log.info("Parsing page [{}]({})", page.title(), page.url());
		Document doc = Jsoup.parse(page.innerHTML("body"));
		parseDocument(doc, jsonArray);
		if (doc.select("#leading_horse > div.pager_block > div").text().contains("次の20件")) {
			Locator next = page.locator("a:has-text(\"次の20件\")");
			Thread.sleep(1000);
			next.click();
			parsePage(page, jsonArray);
		}
	}

	void parseDocument(Document doc, ArrayNode jsonArray) {
		Element table = doc.getElementById("leading_horse").selectFirst("table.basic");
		Elements rows = table.select("tbody tr");
		for (Element row : rows) {
			Elements cols = row.select("td");
			if (!cols.isEmpty()) {
				ObjectNode horseData = this.objectMapper.createObjectNode();
				horseData.put("rank", Integer.valueOf(cols.get(0).text()));
				String horseNameAndYear = cols.get(1).text();
				String[] nameAndYear = horseNameAndYear.split("（|）");
				horseData.put("name", nameAndYear[0]);
				if (nameAndYear.length > 1) {
					horseData.put("birthYear", Integer.valueOf(nameAndYear[1].replace("年", "")));
				}
				horseData.put("color", cols.get(2).text());
				horseData.put("origin", cols.get(3).text());
				horseData.put("runners", parseLong(cols.get(4).text()));
				horseData.put("winners", parseLong(cols.get(5).text()));
				horseData.put("starts", parseLong(cols.get(6).text()));
				horseData.put("wins", parseLong(cols.get(7).text()));
				horseData.put("prize", parseLong(cols.get(8).text()));
				horseData.put("prizePerStart", parseLong(cols.get(9).text()));
				horseData.put("prizePerHorse", parseLong(cols.get(10).text()));
				horseData.put("winRate", new BigDecimal(cols.get(11).text()));
				horseData.put("earningIndex", new BigDecimal(cols.get(12).text()));
				jsonArray.add(horseData);
			}
		}
	}

	static long parseLong(String text) {
		return Long.parseLong(text.replaceAll("[^0-9]", ""));
	}

}