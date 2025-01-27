package lol.maki.leading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JraLeadingSireApplicationTests {

	static Playwright playwright;

	static Browser browser;

	BrowserContext context;

	Page page;

	@BeforeAll
	static void before() {
		playwright = Playwright.create();
		browser = playwright.chromium().launch();
	}

	@BeforeEach
	void beforeEach() {
		context = browser.newContext();
		context.setDefaultTimeout(30000);
		page = context.newPage();
	}

	@AfterEach
	void afterEach() {
		context.close();
	}

	@AfterAll
	static void after() {
		playwright.close();
	}

	@Test
	void contextLoads() throws Exception {
		page.navigate("https://www.jra.go.jp/datafile/leading/");
		page.locator("a:has-text(\"種牡馬\")").click();
		Pattern pattern = Pattern.compile("(\\d{4})年(\\d{1,2})月(\\d{1,2})日現在");
		Matcher matcher = pattern.matcher(page.innerHTML("#leading_horse"));
		if (!matcher.find()) {
			System.err.println("Unable to find date");
			return;
		}
		int year = Integer.parseInt(matcher.group(1));
		int month = Integer.parseInt(matcher.group(2));
		int day = Integer.parseInt(matcher.group(3));
		LocalDate date = LocalDate.of(year, month, day);
		ObjectMapper mapper = new ObjectMapper();
		ArrayNode jsonArray = mapper.createArrayNode();
		parsePage(page, jsonArray, mapper);
		String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonArray);
		Files.copy(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), Path.of(date + ".json"),
				StandardCopyOption.REPLACE_EXISTING);
	}

	void parsePage(Page page, ArrayNode jsonArray, ObjectMapper mapper) throws Exception {
		Document doc = Jsoup.parse(page.innerHTML("body"));
		parseDocument(doc, jsonArray, mapper);
		if (doc.select("#leading_horse > div.pager_block > div").text().contains("次の20件")) {
			var next = page.locator("a:has-text(\"次の20件\")");
			Thread.sleep(1000);
			next.click();
			parsePage(page, jsonArray, mapper);
		}
	}

	void parseDocument(Document doc, ArrayNode jsonArray, ObjectMapper mapper) {
		Element table = doc.getElementById("leading_horse").selectFirst("table.basic");
		Elements rows = table.select("tbody tr");
		for (Element row : rows) {
			Elements cols = row.select("td");
			if (!cols.isEmpty()) {
				ObjectNode horseData = mapper.createObjectNode();
				horseData.put("rank", cols.get(0).text()); // 順位
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
