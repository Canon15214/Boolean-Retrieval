import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the Letor
 *  retrieval model and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelLetor extends RetrievalModel {

  /**
   * Parameters for the model
   */
	
  private RetrievalModelBM25 bm25model;
  private RetrievalModelIndri indrimodel;
  private SVMRankModel svmrankmodel;
  private List<Integer> disabledFeatures;
  private String pageRankFile;
  private HashMap<String, Double> pageRanks;
  private int numBaseFeatures; // this is the maximum number of features
	
  /**
   * Custom constructor
   */
  public RetrievalModelLetor(RetrievalModelBM25 bm25, RetrievalModelIndri indrimodel, 
		  SVMRankModel svmrankmodel, List<Integer> dfeats, String pagerank){
	  this.bm25model = bm25;
	  this.indrimodel = indrimodel;
	  this.svmrankmodel = svmrankmodel;
	  this.disabledFeatures = dfeats;
	  this.pageRankFile = pagerank;
	  buildPageRanks();
  }
  
  private void buildPageRanks(){
	pageRanks = new HashMap<String, Double>();
	BufferedReader prFile = null;
	try {
		prFile = new BufferedReader(new FileReader(this.pageRankFile));
		String input = null;
		while((input=prFile.readLine())!=null){
			String[] tokens = input.split("\t");
			String docId = tokens[0];
			Double prScore = Double.parseDouble(tokens[1]);
			pageRanks.put(docId, prScore);
		}
		prFile.close();
	} catch (IOException e) {
		System.out.println("Could not open page rank file.");
	}
	
  }
  
  public String defaultQrySopName () {
    return new String ("#and");
  }
  
  /**
   * Train this model using supplied training params
   */
  public void train(String queries, String relevances, String outputFeatures){
	  try {
		BufferedReader queryReader = new BufferedReader(new FileReader(queries));
		BufferedReader relevanceReader = new BufferedReader(new FileReader(relevances));
		BufferedWriter output = new BufferedWriter(new FileWriter(outputFeatures, true));
		HashMap<String, Double[]> docFeatures = new HashMap<String, Double[]>();
		HashMap<String, Integer> docRelevances = new HashMap<String, Integer>();
		
		String qLine = null;
		String rLine = relevanceReader.readLine();
		while((qLine = queryReader.readLine())!=null){
			String[] qTokens = qLine.split(":");
			Integer qId = Integer.parseInt(qTokens[0]);
			String qString = qTokens[1];
			String[] queryTerms = QryEval.tokenizeQuery(qString);
			Double[] featureVector;
			Double max, min;
			max = min = 0.0;
			
			while(rLine !=null){
				featureVector = new Double[numBaseFeatures];
				String[] rTokens = rLine.split(" ");
				Integer rId = Integer.parseInt(rTokens[0]);
				if(rId != qId)	break;
				String externalDocId = rTokens[2];
				Integer relevanceScore = Integer.parseInt(rTokens[3]); 
				
				// get unnormalized for query-document pair
				buildFeatures(featureVector, externalDocId, relevanceScore, queryTerms, max, min);
				docFeatures.put(externalDocId, featureVector);
				docRelevances.put(externalDocId, relevanceScore);
				rLine = relevanceReader.readLine();
			}
			
			for(String doc: docFeatures.keySet()){
				Double[] fVec = docFeatures.get(doc);
				Integer relevance = docRelevances.get(doc);
				normalizeFeatures(fVec, max, min);
				printFeatures(output, relevance, qId, fVec, doc);
			}
			
			docFeatures.clear();
			docRelevances.clear();
		}
		
		queryReader.close();
		relevanceReader.close();
		output.close();
		
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	  
  }

  private void printFeatures(BufferedWriter output, Integer relevance,
		  Integer qId, Double[] fVec, String doc) {
	  try {
		  String result = relevance + " qid:" + qId + " ";
		  for(int i=0; i<fVec.length; i++){
			  result += (i+1) + ":" + fVec[i] + " ";
		  }
		  result += "# " + doc;
		  output.write(result + "\n");
	  } catch (IOException e) {
		  System.out.println("Failed to print feature vectors during training.");
	  }

  }

  private void normalizeFeatures(Double[] featureVector, Double max, Double min) {
	  // TODO Auto-generated method stub

  }

  private void buildFeatures(Double[] featureVector, String externalDocId, Integer relevanceScore,
		  String[] queryTerms, Double max, Double min) {
	  // TODO Auto-generated method stub
  }
  
}
