package util;

import java.util.TreeSet;

public class TMWord implements Comparable<TMWord> {
	private int topic;
	public int wordNo;
	public String pos = null;
	
	private int sentiment;
	public TreeSet<Integer> sentiLexicons = new TreeSet<Integer>();
	

	public TMWord(){
	}
	
	public TMWord(int wordNo){
		this.wordNo = wordNo;
	}
	
	public TMWord(int wordNo, int topic){
		this.topic = topic;
		this.wordNo = wordNo;
	}
	
	public int getTopic() {
		return topic;
	}
	
	public void setTopic(int topic) {
		this.topic = topic;
	}
	
	public int getWordNo() {
		return wordNo;
	}
	
	public void setWordNo(int wordNo) {
		this.wordNo = wordNo;
	}
	
	public void setSentiment(int sentiment) {
		this.sentiment = sentiment;
	}

	public int getSentiment() {
		return sentiment;
	}
	
	public boolean hasLexicon() {
		return !sentiLexicons.isEmpty();
	}
	
	public boolean equals(TMWord word) {
		return (word.wordNo == this.wordNo);
	}

	public int compareTo(TMWord word) {
		return this.wordNo - word.wordNo;
	}
}

