/*
 * Implementation of Sentence Topic/Opinion
 *   - Different THETAs for different sentiments: THETA[S]
 *   - Positive/Negative
 * Author: Yohan Jo
 */
package asum;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import util.DoubleMatrix;
import util.IntegerMatrix;
import util.TMDocument;
import util.TMSentence;
import util.TMWord;
import util.Utility;

public class ASUMCore {
	private int numUniqueWords;
	private int numTopics;
	private int numSenti;
	private int numThreads;
	private int numRealIterations;
	private int numDocuments;
	private List<String> wordList = null;
	private int numProbWords = 50;
	
	public String inputDir = null;
	public String outputDir = null;
	private Integer intvalTmpOutput = null;
	
	private double alpha;
	private double sumAlpha;
	private double [] betas;  // betas[3]: Common Words, Corresponding Lexicon, The Other Lexicons
	private double [] sumBeta;  // sumBeta[senti]
	private double [] gammas;
	private double sumGamma;
	
	public DoubleMatrix [] Phi; // Phi[senti][word][topic]
	public DoubleMatrix [] Theta;  // Theta[senti][document][topic]
	public DoubleMatrix Pi;
	
	public List<TreeSet<Integer>> sentiWordsList;
	
	private IntegerMatrix [] matrixSWT;
	private IntegerMatrix [] matrixSDT;
	private IntegerMatrix matrixDS;
	
	private int[][] sumSTW;  // sumSTW[S][T]
	private int[][] sumDST;  // sumDST[D][S]
	private int[] sumDS;  // sumDS[D]
	

	
	private TreeMap<String,TMDocument> documents;
	final private int maxSentenceLength = 50;
	
	public static void main(String [] args) throws Exception {
		int numTopics = 20;
		int numIterations = 1000;
		int numSenti = 2;
		int numThreads = 3;
		String inputDir = "data/";
		String outputDir = "asum_output/";
		String dicDir = null;
		double alpha = 0.01;
		double [] betas = null;
		double [] gammas = null;
		String [] betasStr = {"0.001","0.01","0"};
		String [] gammasStr = {"1","1"};
		boolean randomInit = false;
		
		String sentiFilePrefix = "seeds-";
		String wordListFileName = "WordList.txt";
		String docListFileName = "DocumentList.txt";
		
		/*** Options ***/
/*		for (int i = 0; i < args.length/2; i++) {
			String option = args[2*i];
			String value = args[2*i+1];
			if (option.equals("-t")) numTopics = Integer.valueOf(value);
			else if (option.equals("-s")) numSenti = Integer.valueOf(value);
			else if (option.equals("-i")) numIterations = Integer.valueOf(value);
			else if (option.equals("-th")) numThreads = Integer.valueOf(value);
			else if (option.equals("-d")) inputDir = value.replaceAll("\\\\", "/").replaceAll("/$", "");
			else if (option.equals("-o")) outputDir = value.replaceAll("\\\\", "/").replaceAll("/$", "");
			else if (option.equals("-dic")) dicDir = value.replaceAll("\\\\", "/").replaceAll("/$", "");
			else if (option.equals("-a")) alpha = Double.valueOf(value);
			else if (option.equals("-b")) betasStr = value.split("/");
			else if (option.equals("-g")) gammasStr = value.split("/");
			else if (option.equals("-r")) randomInit = value.toLowerCase().equals("true")?true:false;
		}*/
		if (inputDir == null) inputDir = ".";
		if (outputDir == null) outputDir = new String(inputDir);
		if (dicDir == null) dicDir = new String(inputDir);

		// Exceptions
		if (!new File(inputDir).exists()) throw new Exception("There's no such input directory as " + inputDir);
		if (!new File(outputDir).exists()) throw new Exception("There's no such output directory as " + outputDir);
		if (!new File(dicDir).exists()) throw new Exception("Tehre's no such dictionary directory as " + dicDir);
		
		if (alpha <= 0) throw new Exception("Alpha should be specified as a positive real number.");
		if (betasStr == null) throw new Exception("Beta should be specified as positive real numbers.");
		else {
			betas = new double[3];
			if (betasStr.length != 3) throw new Exception("Betas should be length of 3: Common / Lexicon / Rest");
			else for (int i = 0; i < betas.length; i++) betas[i] = Double.valueOf(betasStr[i]);
		}
		if (gammasStr == null) throw new Exception("Gamma should be specified as positive real numbers.");
		else {
			gammas = new double[numSenti];
			if (gammasStr.length == 1) 
				for (int i = 0; i < gammas.length; i++){
					gammas[i] = Double.valueOf(gammasStr[0]);
		//			System.out.println(gammas[i]);
				}
			else if (gammasStr.length == numSenti) 
				for (int i = 0; i < gammas.length; i++){
					gammas[i] = Double.valueOf(gammasStr[i]);
			//		System.out.println(gammas[i]);
				}
			else throw new Exception("Gammas have a different size than the number of sentiments");
		}
		
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
		
		System.out.println("Documents: "+documents.size());
		System.out.println("Unique Words: "+wordList.size());

		ArrayList<TreeSet<String>> sentiWordsStrList = new ArrayList<TreeSet<String>>();
		for (int s = 0; s < numSenti; s++) {
			String dicFilePath = dicDir + "/" + sentiFilePrefix+s+".txt"; 
			if (new File(dicFilePath).exists()) {
				sentiWordsStrList.add(Utility.makeSetOfWordsFromFile(dicFilePath, true));
			}
			
			System.out.print("SentiWords-"+s+": ");
			for (String sentiWord : sentiWordsStrList.get(sentiWordsStrList.size()-1)) {
				System.out.print(sentiWord+" ");
			}
			System.out.println();
		}
		
		ArrayList<TreeSet<Integer>> sentiWordsList = new ArrayList<TreeSet<Integer>>(sentiWordsStrList.size());
		for (Set<String> sentiWordsStr : sentiWordsStrList) {
			TreeSet<Integer> sentiWords = new TreeSet<Integer>();
			for (String word : sentiWordsStr)
				sentiWords.add(wordList.indexOf(word));
			sentiWordsList.add(sentiWords);
		}
		
		// Print the configuration
		System.out.println("Documents: "+documents.size());
		System.out.println("Unique Words: "+wordList.size());
		System.out.println("Topics: "+numTopics);
		System.out.println("Sentiments: "+numSenti+" (dictionary: "+sentiWordsList.size()+")");
		System.out.println("Alpha: "+alpha);
		System.out.println("Beta: ");
		for (String betaStr : betasStr) System.out.print(betaStr+" ");
		System.out.println();
		System.out.print("Gamma: ");
		for (String gammaStr : gammasStr) System.out.print(gammaStr+" ");
		System.out.println();
		System.out.println("Iterations: "+numIterations);
		System.out.println("Threads: "+numThreads);
		System.out.println("Input Dir: "+inputDir);
		System.out.println("Dictionary Dir: "+dicDir);
		System.out.println("Output Dir: "+outputDir);
		
		ASUMCore core = new ASUMCore(numTopics, numSenti, wordList, documents, sentiWordsList, alpha, betas, gammas);
		core.generateTmpOutputFiles(inputDir, outputDir, 1000);
		core.initialization(randomInit);
		core.gibbsSampling(numIterations, numThreads);
		core.generateOutputFiles(outputDir);
		core.classification(inputDir+"/Polarity.txt", outputDir);
	}
	
	public ASUMCore(int numTopics, int numSenti, List<String> wordList, TreeMap<String,TMDocument> documents, List<TreeSet<Integer>> sentiWordsList, double alpha, double[] betas, double [] gammas) {
		this.numTopics = numTopics;
		this.numSenti = numSenti;
		this.numUniqueWords = wordList.size();
		this.numDocuments = documents.size();
		this.documents = documents;
		this.wordList = wordList;
		this.sentiWordsList = sentiWordsList;
		this.alpha = alpha;
		this.betas = betas;
		this.gammas = gammas;
		this.sumBeta = new double[numSenti];
	}
	
	

	public void initialization(boolean randomInit) {
		sumSTW = new int[numSenti][numTopics];
		sumDST = new int[numDocuments][numSenti];
		sumDS = new int[numDocuments];
		
		matrixSWT = new IntegerMatrix[numSenti];
		for (int i = 0; i < numSenti; i++)
			matrixSWT[i] = new IntegerMatrix(numUniqueWords, numTopics);
		matrixSDT = new IntegerMatrix[numSenti];
		for (int i = 0; i < numSenti; i++)
			matrixSDT[i] = new IntegerMatrix(numDocuments, numTopics);
		matrixDS = new IntegerMatrix(numDocuments, numSenti);
		
		int numTooLongSentences = 0;

		for (TMDocument currentDoc : documents.values()){
			int docNo = currentDoc.getDocNo();
			
			for (TMSentence sentence : currentDoc.getSentences()) {
				int newSenti = -1;
				int numSentenceSenti = 0;
				for (TMWord word : sentence.getWords()) {
					int wordNo = word.getWordNo();
					for (int s = 0; s < sentiWordsList.size(); s++) {
						if (sentiWordsList.get(s).contains(wordNo)) {
							if (numSentenceSenti == 0 || s != newSenti) numSentenceSenti++;
							word.sentiLexicons.add(s);
							newSenti = s;
						}
					}
				}
				sentence.numSenti = numSentenceSenti;
				
				if (randomInit || sentence.numSenti != 1)
					newSenti = (int)(Math.random()*numSenti);
				int newTopic = (int)(Math.random()*numTopics);

				if (sentence.getWords().size() > this.maxSentenceLength) numTooLongSentences++;
				
				if (!(numSentenceSenti > 1 || sentence.getWords().size() > this.maxSentenceLength)) {
					sentence.setTopic(newTopic);
					sentence.setSenti(newSenti);
					
					for (TMWord word : sentence.getWords()) {
						word.setSentiment(newSenti);
						word.setTopic(newTopic);
						matrixSWT[newSenti].incValue(word.wordNo, newTopic);
						sumSTW[newSenti][newTopic]++;
					}
					matrixSDT[newSenti].incValue(docNo, newTopic);
					matrixDS.incValue(docNo, newSenti);
	
					sumDST[docNo][newSenti]++;
					sumDS[docNo]++;
				}
			}
		}
		
		System.out.println("Too Long Sentences: "+numTooLongSentences);
	}
	
	public void gibbsSampling(int numIterations, int numThreads) throws Exception {
		this.numThreads = numThreads;
		this.numRealIterations = 0;
		
		this.sumAlpha = this.alpha * this.numTopics;
		int numSentiWords = 0;
		for (Set<Integer> sentiWords : sentiWordsList) numSentiWords += sentiWords.size();
		double sumBetaCommon = this.betas[0] * (this.numUniqueWords - numSentiWords);
		for (int s = 0; s < numSenti; s++) {
			int numLexiconWords = 0;
			if (this.sentiWordsList.size() > s) numLexiconWords = this.sentiWordsList.get(s).size();
			this.sumBeta[s] = sumBetaCommon + this.betas[1]*numLexiconWords + this.betas[2]*(numSentiWords-numLexiconWords);
		}
		this.sumGamma = 0;
		for (double gamma : this.gammas) this.sumGamma += gamma;
		
		System.out.println("Gibbs sampling started (Iterations: "+numIterations+", Threads: "+numThreads+")");
		
		STO2CoreThread [] threads = new STO2CoreThread[numThreads];
		
		long startTime, endTime;
		for(int i = 0; i < numIterations; i++){
			
			System.out.println( "  - Iteration " + i);

			startTime = new Date().getTime();
			
			for (Set<Integer> sentiWords : this.sentiWordsList) {
				for (int wordNo : sentiWords) {
					if (wordNo < 0 || wordNo >= this.wordList.size()) continue;
					System.out.print(this.wordList.get(wordNo)+"/");
					for (int s = 0; s < numSenti; s++) {
						int sum = 0;
						for (int t = 0; t < numTopics; t++) sum += matrixSWT[s].getValue(wordNo, t);
						System.out.print(sum+"/");
					}
					System.out.print(" ");
				}
				System.out.println();
			}
			
			
			// Make threads
			for (int threadId = 0; threadId < numThreads; threadId++) {
				threads[threadId] = new STO2CoreThread(threadId);
			}
			
			// Run threads
			for (int threadId = 0; threadId < numThreads; threadId++) {
				threads[threadId].start();
			}
			
			// Finalize & update
			for (int threadId = 0; threadId < numThreads; threadId++) {
				try {
					threads[threadId].join();
				} catch (InterruptedException exception) {
					exception.printStackTrace();
				}
			}
			
			for (TMDocument currentDoc : this.documents.values())
				sampleForDoc(currentDoc);
			
			endTime = new Date().getTime();
			double seconds = (int)(endTime - startTime)/1000.0;
			int minutes = (int)(seconds * (numIterations - i - 1) / 60);
			System.out.println("    Iteration "+i+" took "+seconds+"s. (Estimated Time: "+(minutes/60)+"h "+(minutes%60)+"m)");
			
			
			this.numRealIterations = i + 1;
			if (this.intvalTmpOutput != null && this.numRealIterations % this.intvalTmpOutput == 0 && this.numRealIterations < numIterations) {
				this.Phi = ASUMUtil.calculatePhi(matrixSWT, sumSTW, this.betas, this.sumBeta, this.sentiWordsList);
				this.Theta = ASUMUtil.calculateTheta(matrixSDT, sumDST, this.alpha, this.sumAlpha);
				this.Pi = ASUMUtil.calculatePi(matrixDS, sumDS, this.gammas, this.sumGamma);
				generateOutputFiles(this.outputDir);
			}
		}
		System.out.println("Gibbs sampling terminated.");
		
		this.Phi = ASUMUtil.calculatePhi(matrixSWT, sumSTW, this.betas, this.sumBeta, this.sentiWordsList);
		this.Theta = ASUMUtil.calculateTheta(matrixSDT, sumDST, this.alpha, this.sumAlpha);
		this.Pi = ASUMUtil.calculatePi(matrixDS, sumDS, this.gammas, this.sumGamma);
	}

	private void sampleForDoc(TMDocument currentDoc) {
		int docNo = currentDoc.getDocNo();
		
		double[][] probTable = new double[numTopics][numSenti];

		for (TMSentence sentence : currentDoc.getSentences()) {
			if (sentence.getSenti() == -1 || sentence.getWords().size() > this.maxSentenceLength) continue;
			
			Map<TMWord,Integer> wordCnt = sentence.getWordCnt();
			
			double sumProb = 0;
			
			int oldTopic = sentence.getTopic();
			int oldSenti = sentence.getSenti();
			
			matrixSDT[oldSenti].decValue(docNo, oldTopic);
			matrixDS.decValue(docNo, oldSenti);
			
			sumDST[docNo][oldSenti]--;
			sumDS[docNo]--;

			synchronized(matrixSWT) {
				for (TMWord sWord : sentence.getWords()) {
					matrixSWT[oldSenti].decValue(sWord.wordNo, oldTopic);
					sumSTW[oldSenti][oldTopic]--;
				}
			}
		
			// Sampling
			for (int si = 0; si < numSenti; si++) {
				boolean trim = false;
				
				// Fast Trimming
				for (TMWord word : wordCnt.keySet()) {
					if (word.hasLexicon() && !word.sentiLexicons.contains(si)) {
						trim = true;
						break;
					}
				}
				if (trim) {
					for (int ti = 0; ti < numTopics; ti++)
						probTable[ti][si] = 0;
				}
				else {
					for (int ti = 0; ti < numTopics; ti++) {
						double beta0 = sumSTW[si][ti] + sumBeta[si];
						int m0 = 0;
						double expectTSW = 1;
						
						for (TMWord word : wordCnt.keySet()) {
							double beta;
							if (!word.hasLexicon()) beta = this.betas[0];
							else if (word.sentiLexicons.contains(si)) beta = this.betas[1];
							else beta = this.betas[2];
							
							double betaw = matrixSWT[si].getValue(word.wordNo, ti) + beta;
	
							int cnt = wordCnt.get(word);
							for (int m = 0; m < cnt; m++) {
								expectTSW *= (betaw + m) / (beta0 + m0);
								m0++;
							}
							
	//						if (word.lexicon != null && word.lexicon != si && expectTSW > 0) {
	//							System.err.println(this.wordList.get(word.wordNo)+": "+ti+", "+si+", "+matrixTWS[ti].getValue(word.wordNo, si)+", "+beta);
	//						}
						}
//						probTable[ti][si] = (matrixSDT[si].getValue(docNo, ti) + this.alpha) / (sumDST[docNo][si] + this.sumAlpha)
//						* (matrixDS.getValue(docNo, si) + this.gammas[si]) / (sumDS[docNo] + this.sumGamma)
//						* expectTSW;
						// Fast version
						probTable[ti][si] = (matrixSDT[si].getValue(docNo, ti) + this.alpha) / (sumDST[docNo][si] + this.sumAlpha)
						* (matrixDS.getValue(docNo, si) + this.gammas[si])
						* expectTSW;
	
						sumProb += probTable[ti][si];
					}
				}
			}

			int newTopic = 0, newSenti = 0;
			double randNo = Math.random() * sumProb;
			double tmpSumProb = 0;
			boolean found = false;
			for (int ti = 0; ti < numTopics; ti++) {
				for (int si = 0; si < numSenti; si++) {
					tmpSumProb += probTable[ti][si];
					if (randNo <= tmpSumProb) {
						newTopic = ti;
						newSenti = si;
						found = true;
					}
					if (found) break;
				}
				if (found) break;
			}
			
			sentence.setTopic(newTopic);
			sentence.setSenti(newSenti);
			
			for (TMWord word : sentence.getWords()) {
				word.setTopic(newTopic);
				word.setSentiment(newSenti);
				synchronized(matrixSWT) {
					matrixSWT[newSenti].incValue(word.wordNo, newTopic);
					sumSTW[newSenti][newTopic]++;
				}
			}
			matrixSDT[newSenti].incValue(docNo, newTopic);
			matrixDS.incValue(docNo, newSenti);
			
			sumDST[docNo][newSenti]++;
			sumDS[docNo]++;
		}
	}
	
	public void generateTmpOutputFiles(String inputDir, String outputDir, int interval) throws Exception {
		if (inputDir == null || outputDir == null) throw new Exception("Should specify the input and output dirs for tmp output files");
		if (interval <= 0) throw new Exception("The interval of writing tmp output files should be greater than 0");
		this.inputDir = inputDir;
		this.outputDir = outputDir;
		this.intvalTmpOutput = interval;
	}
	
	public void generateOutputFiles (String dir) throws Exception {
		String prefix = "ASUM-T"+numTopics+"-S"+numSenti+"("+sentiWordsList.size()+")-A"+alpha+"-B"+betas[0];
		for (int i = 1; i < betas.length; i++) prefix += ","+betas[i];
		prefix += "-G"+gammas[0];
		for (int i = 1; i < numSenti; i++) prefix += ","+gammas[i];
		prefix += "-I"+numRealIterations;
		
		// Phi
		System.out.println("Writing Phi...");
		PrintWriter out = new PrintWriter(new FileWriter(new File(dir + "/" + prefix + "-Phi.csv")));
		for (int s = 0; s < this.numSenti; s++)
			for (int t = 0; t < this.numTopics; t++)
				out.print(",S"+s+"-T"+t);
		out.println();
		for (int w = 0; w < this.wordList.size(); w++) {
			out.print("\""+this.wordList.get(w).replaceAll("\"", "\"\"")+"\"");
			for (int s = 0; s < this.numSenti; s++) {
				for (int t = 0; t < this.numTopics; t++) {
					out.print(","+this.Phi[s].getValue(w, t));
				}
			}
			out.println();
		}
		out.close();

		// Theta
		System.out.println("Writing Theta...");
		out = new PrintWriter(new FileWriter(new File(dir + "/" + prefix + "-Theta.csv")));
		for (int s = 0; s < this.numSenti; s++)
			for (int t = 0; t < this.numTopics; t++)
				out.print("S"+s+"-T"+t+",");
		out.println();
		for (int d = 0; d < this.numDocuments; d++) {
			for (int s = 0; s < this.numSenti; s++) {
				for (int t = 0; t < this.numTopics; t++) {
					out.print(this.Theta[s].getValue(d, t)+",");
				}
			}
			out.println();
		}
		out.close();
		
		// Pi
		System.out.println("Writing Pi...");
		this.Pi.writeMatrixToCSVFile(dir + "/" + prefix + "-Pi.csv");
		
		// Most probable words
		System.out.println("Writing the most probable words...");
		out = new PrintWriter(new FileWriter(new File(dir + "/" + prefix + "-ProbWords.csv")));
		for (int s = 0; s < this.numSenti; s++)
			for (int t = 0; t < this.numTopics; t++)
				out.print("S"+s+"-T"+t+",");
		out.println();
		int [][][] wordIndices = new int[this.numSenti][this.numTopics][this.numProbWords];
		for (int s = 0; s < this.numSenti; s++) {
			for (int t = 0; t < this.numTopics; t++) {
				Vector<Integer> sortedIndexList = this.Phi[s].getSortedColIndex(t, this.numProbWords);
				for (int w = 0; w < sortedIndexList.size(); w++)
					wordIndices[s][t][w] = sortedIndexList.get(w);
			}
		}
		for (int w = 0; w < this.numProbWords; w++) {
			for (int s = 0; s < this.numSenti; s++) {
				for (int t = 0; t < this.numTopics; t++) {
					int index = wordIndices[s][t][w];
					out.print(this.wordList.get(index).replaceAll("\"", "\"\"")+" ("+String.format("%.3f", Phi[s].getValue(index,t))+"),");
				}
			}
			out.println();
		}
		out.close();

		
		System.out.println("Calculating sentiment and topic..");
		out = new PrintWriter(new FileWriter(new File(dir + "/" + prefix + "-Classification.csv")));
		out.println("Sentiment,Topic,AggregateSentiment");
		for (TMDocument doc : this.documents.values()) { 
			double [][] prod = new double[this.numSenti][this.numTopics];
			double [] probSum = new double[this.numSenti];
			int maxSenti = -1, maxTopic = -1, aggregateSenti = -1;
			double maxProb = -1, maxAggregateProb = -1;
			for (int s = 0; s < this.numSenti; s++) {
				for (int t = 0; t < this.numTopics; t++) {
					prod[s][t]  = 1;
					for (TMSentence sentence : doc.getSentences()) {
						if (sentence.getWords().size() == 0) throw new Exception("WHAT???");	
						for (TMWord word : sentence.getWords()) prod[s][t] *= this.Phi[s].getValue(word.wordNo, t);
					}
					if (prod[s][t] > maxProb) {
						maxSenti = s;
						maxTopic = t;
						maxProb = prod[s][t];
					}
					probSum[s] += prod[s][t];
				}
			}
			for (int s = 0; s < this.numSenti; s++) {
				if (probSum[s] > maxAggregateProb) {
					aggregateSenti = s;
					maxAggregateProb = probSum[s];
				}
			}
			out.println(maxSenti+","+maxTopic+","+aggregateSenti);
		}
		out.close();
		
		
		
		// Result reviews
		System.out.println("Visualizing reviews...");
		String [] sentiColors = {"green","red","black"};
		out = new PrintWriter(new FileWriter(new File(dir + "/" + prefix + "-VisReviews.html")));
		for (TMDocument doc : this.documents.values()) {
			out.println("<h3>Document "+doc.getDocId()+"</h3>");
			for (TMSentence sentence : doc.getSentences()) {
				if (sentence.getSenti() < 0 || sentence.getSenti() >= this.numSenti || sentence.getWords().size() > this.maxSentenceLength) 
					continue;
				out.print("<p style=\"color:"+sentiColors[sentence.getSenti()]+";\">T"+sentence.getTopic()+":");
				for (TMWord word : sentence.getWords())
					out.print(" "+this.wordList.get(word.wordNo));
				out.println("</p>");
			}
		}
		out.close();
		
		// Sentence probabilities
		System.out.println("Calculating sentence probabilities...");
		out = new PrintWriter(new FileWriter(new File(dir + "/" + prefix + "-SentenceProb.csv")));
		out.print("Document,Sentence,Length");
		for (int s = 0; s < this.numSenti; s++)
			for (int t = 0; t < this.numTopics; t++)
				out.print(",S"+s+"-T"+t);
		out.println();
		for (TMDocument doc : this.documents.values()) {
			
			for (int sen = 0; sen < doc.getSentences().size(); sen++) {
				TMSentence sentence = doc.getSentences().get(sen);
				if (sentence.numSenti > 1 || sentence.getWords().size() > 50) continue;
				if (sentence.getWords().size() == 0) throw new Exception("WHAT???");
				out.print(doc.getDocNo()+",\"");
				for (TMWord word : sentence.getWords())
					out.print(this.wordList.get(word.wordNo)+" ");
				out.print("\","+sentence.getWords().size());
				
				double [][] prod = new double[this.numSenti][this.numTopics];
				double sum = 0;
				for (int s = 0; s < this.numSenti; s++) {
					for (int t = 0; t < this.numTopics; t++) {
						prod[s][t]  = 1;
						for (TMWord word : sentence.getWords()) prod[s][t] *= this.Phi[s].getValue(word.wordNo, t);
						sum += prod[s][t];
					}
				}
				for (int s = 0; s < this.numSenti; s++) {
					for (int t = 0; t < this.numTopics; t++) {
						out.print("," + (prod[s][t] / sum));
					}
				}
				out.println();
			}
		}
		out.close();
		
		// Sentiment lexicon words distribution
		System.out.println("Calculating sentiment lexicon words distributions...");
		out = new PrintWriter(new FileWriter(new File(dir + "/" + prefix + "-SentiLexiWords.csv")));
		for (Set<Integer> sentiWords : this.sentiWordsList) {
			for (int wordNo : sentiWords) {
				if (wordNo < 0 || wordNo >= this.wordList.size()) continue;
				out.print(this.wordList.get(wordNo));
				for (int s = 0; s < numSenti; s++) {
					int sum = 0;
					for (int t = 0; t < numTopics; t++) sum += matrixSWT[s].getValue(wordNo, t);
					out.print(","+sum);
				}
				out.println();
			}
			out.println();
		}
		out.close();
		
	}
	
	public void classification(String polarityFilePath, String outputDir) throws Exception {
		System.out.println("Classifying...");
		if (this.Pi == null) throw new Exception("Pi has not been calculated");
		
		BufferedReader polarityFile = new BufferedReader(new FileReader(new File(polarityFilePath)));
		Vector<Integer> polarity = new Vector<Integer>();
		String line;
		while ((line = polarityFile.readLine()) != null) {
			polarity.add(Integer.valueOf(line));
		}
		polarityFile.close();
		
		if (this.Pi.getNumRows() != polarity.size()) throw new Exception("The number of items in the polarity file is different from the number of documents");
		
		int cntNEU = 0;
		int table[][] = new int[this.numSenti][this.numSenti+1];
		for (int i = 0; i < this.Pi.getNumRows(); i++) {
			int senti = -1;
			double prob = -1;
			for (int j = 0; j < this.Pi.getNumColumns(); j++) {
				if (this.Pi.getValue(i, j) > prob) {
					senti = j;
					prob = this.Pi.getValue(i, j);
				}
			}
			table[senti][polarity.get(i)]++;
			if (polarity.get(i) == this.numSenti) cntNEU++;
		}

		// Compute accuracy
		double accuracy = 0;
		for (int i = 0; i < this.numSenti; i++) {
			accuracy += table[i][i];
		}
		accuracy /= (this.Pi.getNumRows() - cntNEU);
		
		
		// Print
		String prefix = "STO2-T"+numTopics+"-S"+numSenti+"("+sentiWordsList.size()+")-A"+alpha+"-B"+betas[0];
		for (int i = 1; i < betas.length; i++) prefix += ","+betas[i];
		prefix += "-G"+gammas[0];
		for (int i = 1; i < numSenti; i++) prefix += ","+gammas[i];
		prefix += "-I"+numRealIterations;

		PrintWriter outFile = new PrintWriter(new FileWriter(new File(outputDir+"/"+prefix+"-Classification.txt")));
		outFile.println("\tTruth");
		outFile.print("Model");
		for (int j = 0; j < this.numSenti+1; j++) outFile.print("\t"+j);
		outFile.println();
		for (int i = 0; i < this.numSenti; i++) {
			outFile.print(i);
			for (int j = 0; j < this.numSenti+1; j++) {
				outFile.print("\t"+table[i][j]);
			}
			outFile.println();
		}
		
		outFile.println("Accuracy: "+accuracy);
		outFile.close();
	}
	
	
	private class STO2CoreThread extends Thread {
		private int threadId;
		
		public STO2CoreThread(int threadId) {
			this.threadId = threadId;
		}
		
		public void run() {
			try {
				int startDoc = threadId * documents.size() / numThreads;
				int endDoc = (threadId+1) *  documents.size() / numThreads - 1;
				
				Object [] keys = documents.keySet().toArray();
				for (int di = startDoc; di <= endDoc; di++) {
					sampleForDoc(documents.get((String)keys[di]));
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
