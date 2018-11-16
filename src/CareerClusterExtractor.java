import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;

public class CareerClusterExtractor {
    private ExecutorService pool;
    private BlockingQueue<FirefoxDriver> queue;

    private CareerClusterExtractor() {
        System.setProperty("webdriver.gecko.driver", "geckodriver");
        System.setProperty(FirefoxDriver.SystemProperty.DRIVER_USE_MARIONETTE,"true");
        System.setProperty(FirefoxDriver.SystemProperty.BROWSER_LOGFILE,"/dev/null");

        pool = Executors.newFixedThreadPool(10);
        queue = firefoxDriverQueue(10);
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        CareerClusterExtractor careerClusterExtractor = new CareerClusterExtractor();
        careerClusterExtractor.scrapeCareers();
        int numResumes = careerClusterExtractor.scrapeResumes();
        System.out.println("Number of resumes collected: " + numResumes);
    }

    private int scrapeResumes() throws IOException, ClassNotFoundException {
        int numResumes = 0;
        // find the hierarchy info files
        File folder = new File("hierarchy_info");
        File[] clusterFiles = folder.listFiles();
        assert clusterFiles != null;
        for (File clusterFile : clusterFiles) {
            Set<Map.Entry<String, List<String>>> pathToCareer = deserializeCareers(clusterFile).entrySet();
            for (Map.Entry<String, List<String>> entry : pathToCareer) {
                numResumes += collectResumes(clusterFile.getName(), entry);
                System.out.println("Number of resumes collected: " + numResumes);
            }
        }
        for (FirefoxDriver driver : queue) {
            driver.quit();
        }
        return numResumes;
    }

    private int collectResumes(String clusterName, Map.Entry<String, List<String>> pathToCareerEntry) {
        int numResumes = 0;
        String pathwayName = pathToCareerEntry.getKey().replaceAll("\\W+", "_");
        String pathName = clusterName + "/" + pathwayName;
        List<String> occupationNames = pathToCareerEntry.getValue();
        List<Future<Integer>> numCountList = new ArrayList<>();
        for (String occupation : occupationNames) {
            String filename = pathName + "/" + occupation.replaceAll("\\W+", "_") + ".txt";
            File file = new File(filename);
            if (!file.exists()) {
                ResumeScraperTask resumeScraperTask = new ResumeScraperTask(filename, occupation, queue);
                numCountList.add(pool.submit(resumeScraperTask));
            }
        }
        for (Future<Integer> count : numCountList) {
            try {
                numResumes += count.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }
        return numResumes;
    }

    private Map<String, List<String>> deserializeCareers(File clusterFile) throws IOException, ClassNotFoundException {
        FileInputStream fileIn = new FileInputStream(clusterFile);
        ObjectInputStream in = new ObjectInputStream(fileIn);
        String clusterName = clusterFile.getName();
        Map<String, List<String>> pathToOccupations = (Map<String, List<String>>) in.readObject();
        in.close();
        fileIn.close();
        return pathToOccupations;
    }

    private void scrapeCareers() throws IOException {
        FirefoxDriver driver = getDriver();
        Elements clusters = extractClusters(driver);
        saveClusters(clusters);
        for (Element cluster : clusters) {
            Map<String, List<String>> careers = extractCareers(driver, cluster.attr("value"));
            saveCareers(cluster.wholeText(), careers);
        }
        driver.quit();
    }

    private static Elements extractClusters(FirefoxDriver driver) {
        String url = "https://www.onetonline.org/find/career";
        driver.get(url);

        // parse dynamically generated HTML into JSoup
        Document document = Jsoup.parse(driver.getPageSource());

        // identify the drop down options (clusters)
        return document.select("option[value~=[1-9][0-9]*]");
    }

    private static void saveClusters(Elements clusters) throws IOException {
        // create text file for cluster names
        File clusterFile = new File( "cluster_names.txt");
        FileWriter writer = new FileWriter(clusterFile);

        // extract occupations for each cluster
        for (Element cluster : clusters) {
            String clusterName = cluster.wholeText().replaceAll("\\W+", "_");
            writer.write(clusterName + "\n");
            new File(clusterName).mkdir();
        }
        writer.flush();
        writer.close();
    }

    private static Map<String, List<String>> extractCareers(FirefoxDriver driver, String clusterIndex) throws IOException {
        String url = "https://www.onetonline.org/find/career?c=" + clusterIndex + "&g=Go";

        driver.get(url);
        Document document = Jsoup.parse(driver.getPageSource());
        Elements rows = document.select("tr td.report2ed[width=35%]");

        // extract career pathway names, removing duplicates
        Set<String> careerPathNames = new HashSet<>(rows.eachText());

        // extract occupations for each career pathway
        Map<String, List<String>> careers = new TreeMap<>();
        for (String careerPathName : careerPathNames) {
            // find occupations under this career pathway
            List<String> occupationNames = rows.select("td:contains(" + careerPathName + ")")
                    .nextAll().nextAll().eachText();
            careers.put(careerPathName, occupationNames);
        }
        return careers;
    }

    private static void saveCareers(String clusterName, Map<String, List<String>> careers) throws IOException {
        clusterName = clusterName.replaceAll("\\W+", "_");
        // create text file for pathway names
        File pathFile = new File(clusterName + "/pathway_names.txt");
        FileWriter pwriter = new FileWriter(pathFile);

        for (Map.Entry<String, List<String>> careerPath : careers.entrySet()) {
            String careerPathName = careerPath.getKey().replaceAll("\\W+", "_");
            pwriter.write(careerPathName + "\n");
            String path = clusterName + "/" + careerPathName;
            new File(path).mkdir();

            // create text file for occupation names
            File occuFile = new File(path + "/occupation_names.txt");
            FileWriter owriter = new FileWriter(occuFile);
            for (String occupation : careerPath.getValue()) {
                owriter.write(occupation + "\n");
            }
            owriter.flush();
            owriter.close();
        }
        pwriter.flush();
        pwriter.close();
        new File("hierarchy_info").mkdir();
        FileOutputStream fileOut = new FileOutputStream("hierarchy_info/" + clusterName);
        ObjectOutputStream out = new ObjectOutputStream(fileOut);
        out.writeObject(careers);
        out.close();
        fileOut.close();
    }

    private static BlockingQueue<FirefoxDriver> firefoxDriverQueue(int capacity) {

        // create Firefox options instance in headless mode
        FirefoxOptions firefoxOptions = new FirefoxOptions();
        firefoxOptions.addArguments("--headless");

        BlockingQueue<FirefoxDriver> queue = new LinkedBlockingDeque<>(capacity);
        for (int i = 0; i < capacity; i++) {
            // instantiate Selenium web browser driver for Firefox
            queue.offer(new FirefoxDriver(firefoxOptions));
        }
        return queue;
    }

    public static FirefoxDriver getDriver() {
        // create Firefox options instance in headless mode
        FirefoxOptions firefoxOptions = new FirefoxOptions();
        firefoxOptions.addArguments("--headless");
        return new FirefoxDriver(firefoxOptions);
    }
}
