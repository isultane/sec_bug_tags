package slda;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import util.DoubleMatrix;
import util.IntegerMatrix;
import util.TMDocument;
import util.TMSentence;
import util.TMWord;

public class SLDACore  {
	
	public int threadId;
	
	private double alpha;
	private double beta;
	private int numProbWords = 50;
	
	private int numTopics;
	private int numWords;
	private int numDocs;
	private int numRealIterations;
	private int numThreads;
	private int tmpOutIterations;
	
	private int[] sum_WT;
	private int[] sum_DT;
	private DoubleMatrix Phi;
	private DoubleMatrix Theta;
	private IntegerMatrix C_WT;
	private IntegerMatrix C_DT;
	private TreeMap<String,TMDocument> documents;
	private List<String> wordList;
	
	public double perplexity = 999999999;
	public double perplexityThres = -1;
	
	private boolean stop = false;
	
	public String outputDir = null;


	public static void main (String [] args) throws Exception {
		String wordListFileName = "WordList.txt";
		String docListFileName = "DocumentList.txt";
		
		int numTopics = 20;
		int numIterations = 1000;
		int numThreads = 3;
		int tmpOutIterations = 200;
		double perplexityThres = 3;
		double alpha = 0.01;
		double beta = 0.01;
		String inputDir = "data/";
		String outputDir = "slda_output/";
		
		/*** Options ***/
	//	for (int i = 0; i < args.length/2; i++) {
	//		String option = args[2*i];
	//		String value = args[2*i+1];
	//		if (option.equals("-t")) numTopics = Integer.valueOf(value);
		//	else if (option.equals("-i")) numIterations = Integer.valueOf(value);
		//	else if (option.equals("-th")) numThreads = Integer.valueOf(value);
		//	else if (option.equals("-pp")) perplexityThres = Double.valueOf(value);
		//	else if (option.equals("-a")) alpha = Double.valueOf(value);
		//	else if (option.equals("-b")) beta = Double.valueOf(value);
		//	else if (option.equals("-d")) inputDir = value.replaceAll("\\\\","/").replaceAll("/$", "");
		//	else if (option.equals("-o")) outputDir = value.replaceAll("\\\\","/").replaceAll("/$", "");
		//	else if (option.equals("-to")) tmpOutIterations = Integer.valueOf(value);
	//	}
		if (inputDir == null) inputDir = ".";
		if (outputDir == null) outputDir = new String(inputDir);
		
		// Exceptions
		if (!new File(inputDir).exists()) throw new Exception("There's no such an input directory as " + inputDir);
		if (!new File(outputDir).exists()) throw new Exception("There's no such an output directory as " + outputDir);
		
		if (alpha <= 0) throw new Exception("Alpha should be specified as a positive real number.");
		if (beta <= 0) throw new Exception("Beta should be specified as a positive real number.");

		String line;
		
		Vector<String> wordList = new Vector<String>();
		BufferedReader wordListFile = new BufferedReader(new FileReader(new File(inputDir+"/"+wordListFileName)));
		while ((line = wordListFile.readLine()) != null)
			if (line != "") wordList.add(line);
		wordListFile.close();
		
		Vector<String> docList = new Vector<String>();
		BufferedReader docListFile = new BufferedReader(new FileReader(new File(inputDir+"/"+docListFileName)));
		while ((line = docListFile.readLine()) != null)
			if (line != "") docList.add(line);
		docListFile.close();
		
		TreeMap<String,TMDocument> documents = TMDocument.loadDocs(inputDir);
		
		
		// Print the configuration
		System.out.println("Documents: "+documents.size());
		System.out.println("Unique Words: "+wordList.size());
		System.out.println("Topics: "+numTopics);
		System.out.println("Alpha: "+alpha);
		System.out.println("Beta: "+beta);
		System.out.println("Iterations: "+numIterations);
		System.out.println("Tmp Output Iterations: "+tmpOutIterations);
		System.out.println("Threads: "+numThreads);
		System.out.println("Input Dir: "+inputDir);
		System.out.println("Output Dir: "+outputDir);

		
		// Run the model
		SLDACore core = new SLDACore(numTopics, wordList, documents, alpha, beta, perplexityThres, tmpOutIterations);
		core.outputDir = outputDir;
		core.initialization();
		core.gibbsSampling(numIterations, numThreads);
	}
	
	
	public void stopIteration(){
		this.stop = true;
	}
	
	public SLDACore(int numTopics, List<String> wordList, TreeMap<String,TMDocument> documents, double alpha, double beta, double perplexityThres, int tmpOutIterations) {
		this.numTopics = numTopics;
		this.numWords = wordList.size();
		this.numDocs = documents.size();
		this.documents = documents;
		this.wordList = wordList;
		this.alpha = alpha;
		this.beta = beta;
		this.perplexityThres = perplexityThres;
		this.tmpOutIterations = tmpOutIterations;
		
		this.sum_WT = new int[numTopics];
		this.sum_DT = new int[numDocs];
	}
	
	public void initialization(){
		C_WT = new IntegerMatrix(numWords, numTopics);
		C_DT = new IntegerMatrix(numDocs, numTopics);
		
		
		for (TMDocument currentDoc : documents.values()) {
			int docNo = currentDoc.getDocNo();
			
			for (TMSentence sentence : currentDoc.getSentences()) {
				int newTopic = (int)(Math.random() * numTopics);
				sentence.setTopic(newTopic);
				C_DT.incValue(docNo, newTopic);
				sum_DT[docNo] += 1;
				for (TMWord word : sentence.getWords()) {
					word.setTopic(newTopic);
					C_WT.incValue(word.getWordNo(), newTopic);
					sum_WT[newTopic] += 1;
				}
			}
		}
	}

	public void gibbsSampling(int numIterations, int numThreads) throws Exception{
		this.numThreads = numThreads;
		System.out.println("Gibbs sampling started (Iterations: "+numIterations+", Threads: "+numThreads+")");
		
		SLDACoreThread [] thread = new SLDACoreThread[numThreads];

		long startTime, endTime;
		for(int i = 0; i < numIterations ; i ++){
		
			System.out.println("  - Iteration "+i);	
			
			startTime = new Date().getTime();
			
			// make threads
			for(int threadId = 0; threadId < numThreads ; threadId++){
				thread[threadId] = new SLDACoreThread(threadId);
			}
			
			// run threads
			for(int threadId = 0; threadId < numThreads ; threadId++){
				thread[threadId].start();
			}
			
			// finalize & update
			for(int threadId = 0; threadId < numThreads ; threadId++){
				try {
					thread[threadId].join();
				} catch (InterruptedException exception) {
					exception.printStackTrace();
				}
			}
			
			endTime = new Date().getTime();
			double seconds = (int)(endTime - startTime)/1000.0;
			int minutes = (int)(seconds * (numIterations - i - 1) / 60);
			System.out.println("    Iteration "+i+" took "+seconds+"s. (Estimated Time: "+(minutes/60)+"h "+(minutes%60)+"m)");
			
			this.numRealIterations = i + 1;
			if (this.outputDir != null && tmpOutIterations > 0 && numRealIterations % tmpOutIterations == 0 && this.numRealIterations < numIterations) {
				this.Phi = SLDAUtil.calculatePhi(C_WT, sum_WT, beta);
				this.Theta = SLDAUtil.calculateTheta(C_DT, sum_DT, alpha);
				generateOutputFiles(this.outputDir);
			}
			
			if (this.stop == true) break;
			if (this.perplexity <= this.perplexityThres) break;
		}
		System.out.println("Gibbs sampling terminated");
		
		if (this.outputDir != null) {
			this.Phi = SLDAUtil.calculatePhi(C_WT, sum_WT, beta);
			this.Theta = SLDAUtil.calculateTheta(C_DT, sum_DT, alpha);
			generateOutputFiles(this.outputDir);
		}
	}

	private void sampleForDoc(TMDocument currentDoc) {
		int docNo = currentDoc.getDocNo();
		for (TMSentence sentence : currentDoc.getSentences()) {
			Map<TMWord,Integer> wordCnt = sentence.getWordCnt();
		
			int oldTopic = sentence.getTopic();
			
			double sum = 0;
			double[] probTable = new double[numTopics];
			
			C_DT.decValue(docNo, oldTopic);
			for (TMWord word : sentence.getWords()) {
				C_WT.decValue(word.getWordNo(), oldTopic);
				synchronized(sum_WT) {
					sum_WT[oldTopic] --;
				}
			}
			
			for (int ti = 0; ti < numTopics; ti++) {
				double beta0 = sum_WT[ti] + this.numWords * beta;
				int m0 = 0;
				double expectWT = 1;
				for (TMWord word : wordCnt.keySet()) {
					int wordNo = word.wordNo;
					int cnt = wordCnt.get(word);
					double betaw = C_WT.getValue(wordNo,ti) + beta;
					for (int m = 0; m < cnt; m++) {
						expectWT *= (betaw + m) / (beta0 + m0);
						m0++;
					}
				}
				probTable[ti] = (C_DT.getValue(docNo, ti) + alpha) / (sum_DT[docNo] - 1 + numTopics * alpha)
								* expectWT;
				sum += probTable[ti];
			}
			
			double randNo = Math.random() * sum;
			
			double tempSum = 0;
			int newTopic = 0;
			for (int ti = 0; ti < numTopics; ti++) {
				tempSum += probTable[ti];
				if (randNo <= tempSum) {
					newTopic = ti;
					break;
				}
			}
			
			sentence.setTopic(newTopic);
			C_DT.incValue(docNo, newTopic);
			for (TMWord word : sentence.getWords()) {
				word.setTopic(newTopic);
				C_WT.incValue(word.getWordNo(), newTopic);
				synchronized(sum_WT) {
					sum_WT[newTopic] ++;
				}
			}
		}
	}
	
	public void generateOutputFiles(String outputDir) throws Exception {
		System.out.println("Generating output files...");
		
		String prefix = "SLDA-T"+this.numTopics+"-A"+this.alpha+"-B"+this.beta+"-I"+this.numRealIterations+"-";
		
		this.Phi.writeMatrixToCSVFile(outputDir + "/" + prefix +"Phi.csv");
		this.Theta.writeMatrixToCSVFile(outputDir + "/" + prefix + "DTTheta.csv");
		
		
		// Most probable words for each topic
		PrintWriter out = new PrintWriter(new FileWriter(new File(outputDir + "/" + prefix + "ProbWords.csv")));
		for (int t = 0; t < numTopics-1; t++) out.print("Topic "+t+",");
		out.println("Topic "+(numTopics-1));
		Vector<Vector<Integer>> sortedWordTopicIndex = new Vector<Vector<Integer>>(numTopics);
		for (int t = 0; t < numTopics; t++) sortedWordTopicIndex.add(this.Phi.getSortedColIndex(t, numProbWords));
		for (int w = 0; w < numProbWords; w++) {
			for (int t = 0; t < numTopics; t++) {
				int index = sortedWordTopicIndex.get(t).get(w);
				out.print(wordList.get(index)+" ("+String.format("%.3f", this.Phi.getValue(index,t))+")");
				if (t < numTopics-1) out.print(",");
			}
			out.println();
		}
		out.close();
		
		/*	
		// Probable sentences
		System.out.println("Calculating sentence probabilities...");
		out = new PrintWriter(new FileWriter(new File(outputDir + "/" + prefix + "-SentenceProb.csv")));
		out.print("Document,Sentence,Length");
		for (int t = 0; t < this.numTopics; t++)
			out.print(",T"+t);
		out.println();
		for (int d = 0; d < this.documents.size(); d++) {
			OrderedDocument doc = this.documents.get(d);
			for (int sen = 0; sen < doc.getSentences().size(); sen++) {
				Sentence sentence = doc.getSentences().get(sen);
				if (sentence.numSenti > 1 || sentence.getWords().size() > 50) continue;
				if (sentence.getWords().size() == 0) throw new Exception("WHAT???");
				out.print(d+","+sen+","+sentence.getWords().size());
				
				double [] prod = new double[this.numTopics];
				double sum = 0;
				for (int t = 0; t < this.numTopics; t++) {
					prod[t] = 1;
					for (Word word : sentence.getWords()) prod[t] *= this.Phi.getValue(word.wordNo, t);
					sum += prod[t];
				}
				for (int t = 0; t < this.numTopics; t++) {
					out.print("," + (prod[t] / sum));
				}
				out.println();
			}
		}
		out.close();
		*/
	}
	
	private class SLDACoreThread extends Thread {
		private int threadId;
		
		public SLDACoreThread(int threadId) {
			this.threadId = threadId;
		}
		
		public void run() {

			try {
				int startDoc = threadId * numDocs / numThreads;
				int endDoc = (threadId+1) * numDocs / numThreads - 1;
				
				Object [] keys = documents.keySet().toArray();
				for (int di = startDoc; di <= endDoc; di++) {
					TMDocument currentDoc = documents.get((String)keys[di]);
					sampleForDoc(currentDoc);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}
}