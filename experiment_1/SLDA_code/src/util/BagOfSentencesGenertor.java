package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;

public class BagOfSentencesGenertor {
	private static File dataset_folder = new File("bugs_data/cxf/");
	private static Set<String> stopwords = null;
	
	private static String DocumentListPath = "data/DocumentList.txt";
	private static String BagOfSentencesPath = "data/BagOfSentences.txt";
		
	public static void main(String[] args) {
		try {
			stopwords = new HashSet<String>(FileUtils.readLines(new File("data/StopWords.txt"), Charsets.UTF_8));
			PorterStemmer stemmer = new PorterStemmer();

			File[] listOfFiles = dataset_folder.listFiles();
			String line = null;
			boolean flag =false;
			/** creating dictionary vector **/
			Vector<String> wordList = new Vector<String>();
			BufferedReader wordListFile = new BufferedReader(new FileReader(
					new File("data/WordList.txt")));
			while ((line = wordListFile.readLine()) != null)
				if (line != "")
					wordList.add(line);
			wordListFile.close();

			for (int i = 0; i < listOfFiles.length; i++) {
				File file = listOfFiles[i];
				StringBuffer document = new StringBuffer();
				if (file.isFile()) {
					// Stanford CoreNLP for analyzing each document: splitting sentences, tokanizing, etc. 
					DocumentPreprocessor dp = new DocumentPreprocessor(file.getAbsolutePath());
					for (List sentence : dp) {
						// processing each sentence  
						for (int j = 0; j < sentence.size(); j++) {
							String terms = sentence.get(j).toString();
							String cleanTerm = terms.replaceAll("[^a-zA-Z0-9]+", "!!");
							String[] tokens = cleanTerm.split("!!");
							if (tokens.length > 0) {
								for (String term : tokens) {
									if (!containsstopword(term.toString().toLowerCase())
											&& (!term.isEmpty())) {
									/*	String word = stemmer.stemming(term
												.toString().toLowerCase());*/
										if (wordList
												.indexOf(term.toLowerCase()) != -1) {
											document.append(wordList
													.indexOf(term.toLowerCase()));
											document.append(" ");
											flag = true;
										}
									}
								}
							}
						}
						if(flag){
							/**
							 * if the flag is true, that means there is a sentence
							 * has been added, then we should add a tab space to separate
							 * that sentence from the coming one.
							 */
							document.append("\t");
							flag = false;
						}
					}
					if (document != null && !(document.toString().isEmpty())) {
						//write file name into Doc list.
						wrintingtoDocumentList(listOfFiles[i].getName());
						System.out.println("\nDocument: " + document);
						//write the content into BagOfSentences.txt
						writingtoBagOfSentences(document);
					}else{
						System.err.println("Docuemtn "+ listOfFiles[i].getName() +" is Empty !!");
					}
				}
			}
			
		}catch(Exception e){
			System.out.println("Exception Message: "+e.getMessage());
		}
		
	}
	
	public static void writingtoBagOfSentences(StringBuffer documnet) throws IOException{
		
		File file = new File(BagOfSentencesPath);
		if (!file.exists()) {
			// file.getParentFile().mkdirs();
			file.createNewFile();
			// System.out.println("Creating file: "+fn);
		}
		PrintWriter  bwr = new PrintWriter (new FileWriter(file,true));
        
        //write contents of StringBuffer to a file
        bwr.println(documnet);
       
        //flush the stream
        bwr.flush();
       
        //close the stream
        bwr.close();
       
        System.out.println("Content of the document written to File.");
	}

	public static void wrintingtoDocumentList(String fn)
			throws IOException {

		File file = new File(DocumentListPath);
		if (!file.exists()) {
			// file.getParentFile().mkdirs();
			file.createNewFile();
			// System.out.println("Creating file: "+fn);
		}
		
		PrintWriter bwr = new PrintWriter(new FileWriter(file, true));

		// write contents of StringBuffer to a file
		bwr.println(fn);

		// flush the stream
		bwr.flush();

		// close the stream
		bwr.close();

		System.out.println("File name "+fn+" written to DocumentList.txt file.");
	}
	
	public static boolean containsstopword(String token) throws IOException {
		return stopwords.contains(token);
	}

}
