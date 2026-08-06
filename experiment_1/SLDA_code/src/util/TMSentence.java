package util;

import java.util.TreeMap;
import java.util.Vector;

public class TMSentence {

	private Vector<TMWord> words;
	private TreeMap<TMWord,Integer> wordCnt;  // Somehow HashMap doesn't work correctly
	private int topic;
	private int senti;
	public int numSenti;
	
	public TMSentence() {
		words = new Vector<TMWord>();
		wordCnt = new TreeMap<TMWord,Integer>();
	}
	
	public void addWord(TMWord word) {
		words.add(word);
		Integer cnt = wordCnt.get(word);
		if (cnt == null) wordCnt.put(word, 1);
		else wordCnt.put(word, cnt+1);
	}
	
	public Vector<TMWord> getWords() {
		return words;
	}
	
	public TreeMap<TMWord,Integer> getWordCnt() {
		return wordCnt;
	}
	
	public int getTopic() {
		return topic;
	}
	
	public int getSenti() {
		return senti;
	}
	
	public void setTopic(int topic) {
		this.topic = topic;
	}
	
	public void setSenti(int senti) {
		this.senti = senti;
	}
}
