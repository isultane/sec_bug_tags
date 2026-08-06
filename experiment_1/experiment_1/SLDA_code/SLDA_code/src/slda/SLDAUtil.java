package slda;

import java.util.Date;

import util.DoubleMatrix;
import util.IntegerMatrix;

public class SLDAUtil {
	
	public static long BeforeTime;
	public static long IterationTime;
	public static long PredictedCompleteTime;
	
	public static String predictingCompleteTime(int currentIteration, int totalIteration){
		Date date;
		
		if(currentIteration == 0){
			BeforeTime = System.currentTimeMillis();
			return "Unpredictable";
		}else{
			long currentTime = System.currentTimeMillis(); 
			IterationTime = currentTime - BeforeTime;
			PredictedCompleteTime = IterationTime * (totalIteration - currentIteration);
			date = new Date(PredictedCompleteTime + currentTime); 
			BeforeTime = currentTime;
		}
		
		return date.toString();
	}
	
	public static DoubleMatrix calculatePhi(IntegerMatrix C_WT, int[] sum_WT, double beta){
		System.out.println("Calculating Phi...");
		int numWords = C_WT.getNumRows();
		int numTopics = C_WT.getNumColumns();
		DoubleMatrix Phi = new DoubleMatrix(numWords, numTopics);
		
		for(int ti=0; ti < numTopics ; ti++){
			for(int wi=0; wi < numWords ; wi++){
				Phi.setValue(wi, ti, (C_WT.getValue(wi, ti)+beta)/(sum_WT[ti]+numWords*beta));
			}
		}
		
		return Phi;
	}

	public static DoubleMatrix calculateTheta(IntegerMatrix C_DT, int[] sum_DT, double alpha) {
		System.out.println("Calculating Theta...");
		int numDocs = C_DT.getNumRows();
		int numTopics = C_DT.getNumColumns();
		DoubleMatrix Theta = new DoubleMatrix(numDocs, numTopics);

		for (int di=0; di < numDocs; di++) {
			for(int ti=0; ti < numTopics; ti++) {
				Theta.setValue(di, ti, (C_DT.getValue(di,ti) + alpha)/(sum_DT[di]+numTopics*alpha));
			}
		}

		return Theta;
	}
	
}
