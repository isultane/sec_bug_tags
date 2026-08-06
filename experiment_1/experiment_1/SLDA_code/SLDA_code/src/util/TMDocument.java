package util;

import java.io.File;
import java.util.TreeMap;
import java.util.Vector;

public class TMDocument {

	private Vector<TMSentence> sentences;
	private Vector<TMWord> words;

	private int docNo;
	private String docId;
	private int author;
	private int senti;
//	private int[] topicDist;
	private int classNo;
	
	public TMDocument(){
		this.words = new Vector<TMWord>();
		sentences = new Vector<TMSentence>();
	}
	
	public TMDocument(int docLength){
		this.words = new Vector<TMWord>(docLength);
	}
	
	public void setDocId(String docId) {
		this.docId = docId;
	}
	
	public String getDocId() {
		return this.docId;
	}

	public void setClassNo(int classNo) {
		this.classNo = classNo;
	}

	public int getClassNo() {
		return this.classNo;
	}
	
//	public void newTopicVector(int numOfTopic){
//		topicDist = new int[numOfTopic];
//	}

	public void setDocNo(int docNo) {
		this.docNo = docNo;
	}
	
	public int getDocNo() {
		return docNo;
	}

	public void addWord(TMWord word){
		words.add(word);
		sentences.lastElement().addWord(word);
	}
	
	public void addWord(int wordNo) {
		addWord(new TMWord(wordNo));
	}
	
	public int getLength() {
		return words.size();
	}

	public int getNumWords() {
		return words.size();
	}
	
	public Vector<TMWord> getWords() {
		return words;
	}
	
	public void setWordsList(Vector<TMWord> wordsList){
		this.words = wordsList;
	}

	public void setAuthor(int author) {
		this.author = author;
	}

	public int getAuthor() {
		return author;
	}

	public void setSenti(int senti) {
		this.senti = senti;
	}

	public int getSenti() {
		return senti;
	}
	
	public TreeMap<Integer,Integer> getWordCount() {
		TreeMap<Integer,Integer> wordCntTable = new TreeMap<Integer,Integer>();
		for (TMWord word : this.words) {
			Integer cnt = wordCntTable.get(word.wordNo);
			if (cnt == null) wordCntTable.put(word.wordNo, 1);
			else wordCntTable.put(word.wordNo, cnt+1);
		}
		return wordCntTable;
	}
	
	public void addSentence(TMSentence sentence) {
		sentences.add(sentence);
		for (TMWord word : sentence.getWords())
			words.add(word);
	}
	
	public Vector<TMSentence> getSentences() {
		return sentences;
	}
	
	
	// Map< docId, doc >
	public static TreeMap<String,TMDocument> loadDocs (String inDir) throws Exception {
		String bosPath = inDir+"/BagOfSentences.txt";
		String docListPath = inDir+"/DocumentList.txt";

		boolean docListExists = new File(docListPath).exists();
		
		BufferedFileReader bosFile = new BufferedFileReader(bosPath);
		BufferedFileReader docListFile = null;
		if (docListExists) docListFile = new BufferedFileReader(docListPath);

		TreeMap<String,TMDocument> docs = new TreeMap<String,TMDocument>();
		int docCount=0;
		while (bosFile.nextLine()) {
			String docId;
			if (docListExists) docId = docListFile.readLine();
			else docId = String.valueOf(docCount);
			
			TMDocument doc = new TMDocument();	
			doc.setDocNo(docCount);
			doc.setDocId(docId);
			docs.put(docId, doc);

			String line = bosFile.readLine();
			String [] sentenceStrs = line.split("\t");
			for (String sentenceStr : sentenceStrs) {
				TMSentence sentence = new TMSentence();
				String [] wordNos = sentenceStr.split(" ");
				for (String wordNo : wordNos) {
					if (!wordNo.isEmpty()) {
						// System.out.println(Integer.valueOf(wordNo));
						sentence.addWord(new TMWord(Integer.valueOf(wordNo)));
					}
				}
				doc.addSentence(sentence);
			}
			docCount++;
		}
		bosFile.close();
		if (docListExists) docListFile.close();
		
		return docs;
	}
	
}
