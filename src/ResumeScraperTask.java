import org.openqa.selenium.firefox.FirefoxDriver;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

public class ResumeScraperTask implements Callable<Integer> {
    private String filename;
    private String occupation;
    private BlockingQueue<FirefoxDriver> queue;
    private FirefoxDriver driver;

    ResumeScraperTask(String filename, String occupation, BlockingQueue<FirefoxDriver> queue) {
        this.filename = filename;
        this.occupation = occupation;
        this.queue = queue;
    }

    @Override
    public Integer call() throws InterruptedException {
        driver = queue.take();
        try {
            return scrape();
        } catch (Throwable e) {
            e.printStackTrace();
            return 0;
        } finally {
            queue.put(driver);
        }
    }

    private int scrape() {
        ResumeScraper resumeScraper = new ResumeScraper(driver);
        int start = 0;
        while (true) {
            try {
                return resumeScraper.getResumeInfo(occupation, filename, start, 2000);
            } catch (Exception e) {
                e.printStackTrace();
                // Set start position
                start = resumeScraper.getNumResumes();
                // Restart driver to be safe
                driver.quit();
                driver = CareerClusterExtractor.getDriver();
                resumeScraper = new ResumeScraper(driver);
            }
        }
    }
}
