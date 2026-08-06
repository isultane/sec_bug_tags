package util;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.csvreader.CsvReader;
import com.csvreader.CsvWriter;

class Topic implements Comparable<Topic> {
    double tscore;
    String tname;

    public Topic(double tscore, String tname) {
        this.tscore = tscore;
        this.tname = tname;
    }

    public int compareTo(Topic o) {
        return tscore < o.tscore ? -1 : tscore > o.tscore ? 1 : 0;
    }
    
    double getTscore(){
    	return this.tscore;
    }
    
    String getTname(){
    	return this.tname;
    }
}

public class AssigingTopicsToDocuments {
	private static String ResultsFile = "DocumentsTopics.csv";
	public static void SLDA_topic_assigment(String results, ArrayList<String> file_list) throws NumberFormatException, IOException{
		String line = null;
		int counter = 0;
		int index = 0;
		
		BufferedReader stream = new BufferedReader(new FileReader(results));
		while ((line = stream.readLine()) != null) {
			String[] splits = line.split(",");
			List<Topic> topics = new ArrayList<Topic>();
			for (String topic_wight : splits){
				
				topics.add(new Topic(Double.parseDouble(topic_wight),"T"+counter));
				counter++;
			}
			
			Comparator DescendingComparator = Collections.reverseOrder();
	        Collections.sort(topics,DescendingComparator);
	        
	        StringBuffer br = new StringBuffer();
	        for(Topic t : topics){
	        	br.append(t.getTname()+"-");
	        }
	        StringBuffer br_line = new StringBuffer();
	        br_line.append(file_list.get(index)+","+br);
	        
	        System.out.println("Writing topics for Doc: "+file_list.get(index));
	        writeToFile(br_line);
	        index++;
	        counter = 0;
		}
	}
	
	public static void SlicingResults(String results) throws NumberFormatException, IOException{
		String line = null;
		int counter = 0;
		BufferedReader lines = new BufferedReader(new FileReader(results));
		Map<String, Integer> map = new HashMap<String, Integer>();
		while ((line = lines.readLine()) != null) {
			String[] splits = line.split(",");
			String DocName = splits[0];
			String Topics = splits[1];
			
			String[] DocNameSplits = DocName.split("-");
			String year = DocNameSplits[1];
			
			String[] TopicsSplits = Topics.split("-");
			String BestTopic = TopicsSplits[0];
			
			String Results = ""+BestTopic+"-"+year;
				
			if (map.containsKey(Results)) 
			{
				map.put(Results,map.get(Results)+1);
			}else {
				map.put(Results,1);
			}
		}
		System.out.println("Writing Topics Usages into File ...");
	
		// writing Topic usages results to csv file
		 for (String key : map.keySet()) {
			 StringBuffer csv = new StringBuffer();
			 String[] data = key.split("-");
			 String Topic = data[0];
			 String Year = data[1];
			 Integer Count = map.get(key);
			 csv.append(Topic+","+Year+","+Count);
			 System.out.println("Topic: "+Topic + " Year: " + Year +" Counts: "+ Count); 
			 writeToTopicUsageFile(csv,"TopicsUsage.csv");
		 }
		System.out.println("Done !");

	}
	
	public void ASUM_topic_assigment(){
	       // to be completed... 
	}
	
	public static void main(String[] args) throws IOException {
		String filename = "slda_output/SLDA-T20-A0.01-B0.01-I1000-DTTheta.csv";
		
		Scanner s = new Scanner(new File("data/DocumentList.txt"));
		ArrayList<String> filelists = new ArrayList<String>();
		while (s.hasNext()){
			filelists.add(s.next());
		}
		s.close();
	//	SLDA_topic_assigment(filename,filelists);
		SlicingResults(ResultsFile);
		
	}

	public static void writeToTopicUsageFile(StringBuffer documnet,String FileName)
			throws IOException {

		File file = new File(FileName);
		if (!file.exists()) {
			// file.getParentFile().mkdirs();
			file.createNewFile();
			// System.out.println("Creating file: "+fn);
		}
		PrintWriter bwr = new PrintWriter(new FileWriter(file, true));

		// write contents of StringBuffer to a file
		bwr.println(documnet);

		// flush the stream
		bwr.flush();

		// close the stream
		bwr.close();
	}
	public static void writeToFile(StringBuffer documnet)
			throws IOException {

		File file = new File(ResultsFile);
		if (!file.exists()) {
			// file.getParentFile().mkdirs();
			file.createNewFile();
			// System.out.println("Creating file: "+fn);
		}
		PrintWriter bwr = new PrintWriter(new FileWriter(file, true));

		// write contents of StringBuffer to a file
		bwr.println(documnet);

		// flush the stream
		bwr.flush();

		// close the stream
		bwr.close();

		System.out.println("Content of the document written to File.");
	}

}
