import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.firefox.FirefoxDriver;

/**
 *
 * @author Ashley Lee
 *
 *
 * This program is written for a research project supervised by Maria Tomprou 
 * @Carnegie Mellon University, Human Computer Interaction Institute 
 *
 *
 */


public class ResumeScraper {
    private FirefoxDriver driver;
    private int numResumes = 0;

    ResumeScraper(FirefoxDriver driver) {
        // Selenium web browser driver for Firefox
        this.driver = driver;
    }

    // main function just calls getResumeInfo function via search key
    public static void main(String[] args)
    {
//        // for benchmarking
//        long startTime = System.nanoTime();
//        String searchTerm = "Business+Analyst";
//        ResumeScraper resumeScraper = new ResumeScraper("test");
//        resumeScraper.getResumeInfo(searchTerm);
//        long endTime = System.nanoTime();
//        System.out.println(endTime - startTime);
    }

    // Below function will help getting resume information from the search key typed by the user & connects to web
    int getResumeInfo(String searchTerm, String filename, int start, int limit) throws IOException {
        File file = new File(filename); // create file name for the occupation
        FileWriter fwriter = new FileWriter(file);
        BufferedWriter writer = new BufferedWriter(fwriter);
        numResumes = 0;
        int numEntries = 50; // every run you get approximately 50 results
        int i = 0;

        while(true) {
            int marker = start + numEntries * i;
            String url = "https://www.indeed.com/resumes?q=" + searchTerm + "&co=US&start=" + marker;

            // make request through browser driver
            driver.get(url);

            // parse dynamically generated HTML into JSoup
            Document document = Jsoup.parse(driver.getPageSource());

            // identify the resume page for each entry
            Elements resume = document.select(".rezemp-ResumeSearchCard-contents a");

            // create resume list with URLs of each resume page
            List<String> resumeList = resume.eachAttr("href");

            // Break condition -- no more resumes or past limit
            if (resumeList.size() == 0 || marker >= limit) {
                break;
            }

            // Trim resumeList if near limit
            int numToKeep = Math.min(limit - marker, resumeList.size());
            if (numToKeep < resumeList.size()) {
                resumeList = resumeList.subList(0, numToKeep);
            }

            // trim each URL
            resumeList = resumeList.stream().map(s -> s.substring(0, s.indexOf('?'))).collect(Collectors.toList());

            writeFile(resumeList, writer); // call to write it into a file
            i = i + 1;
        }
        writer.flush();
        fwriter.close();
        writer.close();
        return numResumes;
    }

    // Below function will look at the resume list array and help writing resume to the file in the end
    private int writeFile(List<String> resumeList, BufferedWriter writer) {
        try {
            // Iterate over the resume list
            for (String aResume : resumeList) {

                // get the resume from below url
                String url = "https://resumes.indeed.com" + aResume;

                Document document = Jsoup.connect(url).get();

                // Select element with state
                Element state = document.selectFirst("script:containsData(window.initialState)");
                // Extract text from the element
                String stateString = state.data();
                // Trim the text to extract just the JSON content, keeping in mind that it is improperly escaped
                String jsonPrelim =
                        stateString.substring(stateString.indexOf("{"), stateString.lastIndexOf("}") + 1);
                // Replace x with u
                String jsonEscapeX = jsonPrelim.replaceAll("\\\\x", "\\\\u00");
                // Replace double escapes with single escapes
                String jsonEscaped = jsonEscapeX.replaceAll("\\\\\\\\u00", "\\\\u00");
                // Replace with correct escapes, then use library to unescape JSON entirely
                String jsonUnescaped = StringEscapeUtils.unescapeJava(jsonEscaped);
                // Re-escape string literals
                String json = jsonUnescaped.replaceAll("\\\\", "\\\\\\\\") + "\n";

                // write to file in buffered fashion
                writer.write(json);
                numResumes++;
            }
        } catch (HttpStatusException e) { // in case any exception
            System.out.println("Exceptions occurred");
        } catch (IOException ex) {
            Logger.getLogger(ResumeScraper.class.getName()).log(Level.SEVERE, null, ex);
        }
        return numResumes;
    }

    public int getNumResumes() {
        return numResumes;
    }
}
