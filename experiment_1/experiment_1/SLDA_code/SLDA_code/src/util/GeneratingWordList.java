package util;
/** This Class used to create the Vocabulary terms for each Project.
 * @author Sultan
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;

public class GeneratingWordList {
	private static File dataset_folder = new File("bugs_data/cxf/");
	private static Set<String> stopwords = null;
	private static Set<String> WordList = new HashSet<String>();
	private static Set<String> Dictionary = null;
	private static Set<String> ISdictionary = null;
	
	public static void main(String[] args) {

		try {
			stopwords = new HashSet<String>(FileUtils.readLines(new File("data/StopWords.txt"), Charsets.UTF_8));
			Dictionary = new HashSet<String>(FileUtils.readLines(new File("data/Dictionary.txt"), Charsets.UTF_8));
			ISdictionary = new HashSet<String>(FileUtils.readLines(new File("data/ISdictionary.txt"), Charsets.UTF_8));
			PorterStemmer stemmer = new PorterStemmer();
			boolean found = false;
			File[] listOfFiles = dataset_folder.listFiles();
			String line = null;
			boolean flag = false;
			
			for (int i = 0; i < listOfFiles.length; i++) {
				System.out.println("Parsing file: "+listOfFiles[i].getName());
				File file = listOfFiles[i];
				if (file.isFile()) {
					// Stanford CoreNLP for analyzing each document: splitting sentences, tokanizing, etc. 
					DocumentPreprocessor dp = new DocumentPreprocessor(file.getAbsolutePath());
					for (List sentence : dp) {
						for (int j = 0; j < sentence.size(); j++) {
							String terms = sentence.get(j).toString();
							String cleanTerm = terms.replaceAll("[^a-zA-Z0-9]+", "!!");
							String[] tokens = cleanTerm.split("!!");
							if (tokens.length > 0) {
								for(String term : tokens){
									if (!containsstopword(term.toString().toLowerCase())
											&& (!term.isEmpty())) {
										if (TermInDictionary(term)
												|| TermInISdictionary(term)) {
										/*	String word = stemmer.stemming(term
													.toString().toLowerCase());*/
											WordList.add(term.toLowerCase());
										}
									}
								}
							}
						}
					}
				}
			}
			
			generatingWordList(WordList);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Returns true if the term is in the StopWord list.
	 * @param token
	 * @return
	 * @throws IOException
	 */
	public static boolean containsstopword(String term) throws IOException {
		return stopwords.contains(term);
	}
	/**
	 * Returns true if the term is in the English dictionary.
	 * @param term
	 * @return
	 * @throws IOException
	 */
	public static boolean TermInDictionary(String term) throws IOException{
		return Dictionary.contains(term);
	}
	/**
	 * Returns true if the term is in Information Security dictionary.
	 * @param term
	 * @return
	 * @throws IOException
	 */
	public static boolean TermInISdictionary(String term) throws IOException{
		return ISdictionary.contains(term);
	}
	
	public static void generatingWordList(Set<String> set) throws IOException{

		BufferedWriter out = new BufferedWriter(new FileWriter("data/WordList.txt"));
		Iterator it = set.iterator(); 
		while(it.hasNext()) {
		    out.write(it.next()+"\n");
		}
		out.close();
	}

}
