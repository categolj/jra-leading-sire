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
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class Scraper implements ApplicationRunner {

	private final ObjectMapper objectMapper;

	private static final Logger log = LoggerFactory.getLogger(Scraper.class);

	public static final String DIR_OPT = "dir";

	public Scraper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		log.info("Scraper started");
		ArrayNode jsonArray = this.objectMapper.createArrayNode();
		AtomicReference<LocalDate> date = new AtomicReference<>();
		if (args.containsOption(DIR_OPT) && !args.getOptionValues(DIR_OPT).isEmpty()) {
			String dir = args.getOptionValues(DIR_OPT).getFirst();
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
			return;
		}
		String json = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonArray);
		log.info("output to {}.json", date.get());
		Files.copy(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Path.of(date.get() + ".json"),
				StandardCopyOption.REPLACE_EXISTING);
	}

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
				horseData.put("rank", Integer.valueOf(cols.get(0).text())); // 順位
				String horseNameAndYear = cols.get(1).text();
				String[] nameAndYear = horseNameAndYear.split("（|）"); // 「（」と「）」で分割
				horseData.put("name", nameAndYear[0]); // 種牡馬名
				if (nameAndYear.length > 1) {
					horseData.put("birthYear", Integer.valueOf(nameAndYear[1].replace("年", ""))); // 生年
				}
				horseData.put("color", cols.get(2).text()); // 毛色
				horseData.put("origin", cols.get(3).text()); // 産地
				horseData.put("runners", Integer.valueOf(cols.get(4).text())); // 出走頭数
				horseData.put("winners", Integer.valueOf(cols.get(5).text())); // 勝馬頭数
				horseData.put("starts", Integer.valueOf(cols.get(6).text())); // 出走回数
				horseData.put("wins", Integer.valueOf(cols.get(7).text())); // 勝利回数
				horseData.put("prize", Long.valueOf(cols.get(8).text().replaceAll("[^0-9]", ""))); // 賞金
				horseData.put("prizePerStart", Long.valueOf(cols.get(9).text().replaceAll("[^0-9]", ""))); // 1出走賞金
				horseData.put("prizePerHorse", Long.valueOf(cols.get(10).text().replaceAll("[^0-9]", ""))); // 1頭平均賞金
				horseData.put("winRate", new BigDecimal(cols.get(11).text())); // 勝馬率
				horseData.put("earningIndex", new BigDecimal(cols.get(12).text())); // E・I
				jsonArray.add(horseData);
			}
		}
	}

}
