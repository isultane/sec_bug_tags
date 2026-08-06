package util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;

public class CyberSecurityTermsSeeds {
	private static String CyberSecuritySeedsFile = "data/CyberSecuritySeedsFile.txt";
	private static String ISDictioary = "data/CyberSecurityTerms";
	private static Set<String> stopwords = null;

	public static void main(String[] args) throws IOException {
		stopwords = new HashSet<String>(FileUtils.readLines(new File("data/StopWords"), Charsets.UTF_8));
		PorterStemmer stemmer = new PorterStemmer();
		FileInputStream fis = new FileInputStream(ISDictioary);
		BufferedReader br = new BufferedReader(new InputStreamReader(fis));
		String line = null;
		String [] words = null;
		
		/** creating dictionary vector **/
		Vector<String> wordList = new Vector<String>();
		BufferedReader wordListFile = new BufferedReader(new FileReader(
				new File("data/WordList.txt")));
		while ((line = wordListFile.readLine()) != null)
			if (line != "")
				wordList.add(line);
		wordListFile.close();
		System.out.println("Wordlist size: " +wordList.size());
		System.out.println("Reading ISDictionary ...");
		while ((line = br.readLine()) != null) {
			words = line.split("\\s+");
			for (String term : words) {
				/** stop words removing **/
				if (!containsstopword(term.toLowerCase())) {
					String word = stemmer.stemming(term);
					if (wordList.indexOf(word.toLowerCase()) != -1) {
						wrintingtoCyberSecuritySeedsFile(word.toLowerCase());
					}
				}
			}
		}
		
		System.out.println("CyberSecuritySeedsFile is created !!");
	}
	
	public static void wrintingtoCyberSecuritySeedsFile(String CyberSecuritySeedWord)
			throws IOException {

		File ISDictionary = new File(CyberSecuritySeedsFile);
		if(!ISDictionary.exists()) {
			ISDictionary.createNewFile();
		} 
		PrintWriter bwr = new PrintWriter(new FileWriter(new File(CyberSecuritySeedsFile), true));

		// write contents of StringBuffer to a file
		bwr.println(CyberSecuritySeedWord);

		// flush the stream
		bwr.flush();

		// close the stream
		bwr.close();
	}
	
	private static boolean containsstopword(String token) {
		// TODO Auto-generated method stub
		return stopwords.contains(token);
	}
}
