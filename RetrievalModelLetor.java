import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
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
	private HashSet<Integer> disabledFeatures;
	private String pageRankFile;
	private HashMap<String, Double> pageRanks;
	private int numBaseFeatures; // this is the maximum number of features
	private static final int initialTestingDocs = 100;

	/**
	 * Custom constructor
	 */
	public RetrievalModelLetor(RetrievalModelBM25 bm25, RetrievalModelIndri indrimodel, 
			SVMRankModel svmrankmodel, List<Integer> dfeats, String pagerank, int numBaseFeatures){
		this.bm25model = bm25;
		this.indrimodel = indrimodel;
		this.svmrankmodel = svmrankmodel;
		this.disabledFeatures = new HashSet(dfeats);
		this.pageRankFile = pagerank;
		this.numBaseFeatures = numBaseFeatures;
		buildPageRanks();
	}
	
	// get the bm25 model
	public RetrievalModelBM25 getBM25Model(){
		return this.bm25model;
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
	 * Train this model using supplied training parameters
	 */
	public void train(String queries, String relevances, String outputFeatures){
		try {

			// Generate the training data
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
				Double[] featureMax = new Double[numBaseFeatures];
				Double[] featureMin = new Double[numBaseFeatures];
				for(int i=0; i<numBaseFeatures; i++){
					featureMax[i] = Double.MIN_VALUE;
					featureMin[i] = Double.MAX_VALUE;
				}

				while(rLine !=null){
					Double[] featureVector = new Double[numBaseFeatures];
					String[] rTokens = rLine.split(" ");
					Integer rId = Integer.parseInt(rTokens[0]);
					if(rId != qId)	break;
					String externalDocId = rTokens[2];
					Integer relevanceScore = Integer.parseInt(rTokens[3]); 

					// get unnormalized for query-document pair
					buildFeatures(featureVector, externalDocId, queryTerms, 
										featureMax, featureMin);
					docFeatures.put(externalDocId, featureVector);
					docRelevances.put(externalDocId, relevanceScore);
					rLine = relevanceReader.readLine();
				}

				for(String doc: docFeatures.keySet()){
					Double[] fVec = docFeatures.get(doc);
					Integer relevance = docRelevances.get(doc);
					normalizeFeatures(fVec, featureMax, featureMin);
					printFeatures(output, relevance, qId, fVec, doc);
				}

				docFeatures.clear();
				docRelevances.clear();
			}

			queryReader.close();
			relevanceReader.close();
			output.close();

			// Train the model
			svmrankmodel.trainModel(outputFeatures);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	/**
	 * Classify the testing data
	 */
	public ScoreList classify(String queryString, ScoreList initialRanking, 
								String testFeaturesFile, String testScoresFile){
		ScoreList result = new ScoreList();
		try {
			BufferedWriter testFeatures = new BufferedWriter(new FileWriter(testFeaturesFile, true));
			// generate the testing features
			initialRanking.sort();
			Double[] featureMax = new Double[numBaseFeatures];
			Double[] featureMin = new Double[numBaseFeatures];
			for(int i=0; i<numBaseFeatures; i++){
				featureMax[i] = Double.MIN_VALUE;
				featureMin[i] = Double.MAX_VALUE;
			}
			HashMap<String, Double[]> docFeatures = new HashMap<String, Double[]>();
			String[] qTokens = queryString.split(":");
			Integer qId = Integer.parseInt(qTokens[0]);
			String qString = qTokens[1];
			String[] queryTerms = QryEval.tokenizeQuery(qString);
			
			// seems that the query "40:michworks" produces only 15 documents in the initial ranking
			int numDocs = Math.min(initialRanking.size(), initialTestingDocs);
			for(int j=0; j<numDocs; j++){
				if(j >= initialRanking.size())	System.out.println(queryString);
				int internalDocId = initialRanking.getDocid(j);
				String externalDocId = Idx.getExternalDocid(internalDocId);
				Double[] featureVector = new Double[numBaseFeatures];

				// get unnormalized features for this query-document pair
				buildFeatures(featureVector, externalDocId, queryTerms, 
								featureMax, featureMin);
				docFeatures.put(externalDocId, featureVector); 
			}
			
			for(String doc: docFeatures.keySet()){
				Double[] fVec = docFeatures.get(doc);
				normalizeFeatures(fVec, featureMax, featureMin);
				printFeatures(testFeatures, 0, qId, fVec, doc);
			}
			
			testFeatures.close();

			// produce svm rank score for the test data
			svmrankmodel.score(testFeaturesFile, testScoresFile);
			
			// read the test scores file into the result scorelist
			BufferedReader testScoresReader = new BufferedReader(new FileReader(testScoresFile));
			String line;
			for(int i=0; i<numDocs; i++){
				int internalDocId = initialRanking.getDocid(i);
				line = testScoresReader.readLine();
				double score = Double.parseDouble(line);
				result.add(internalDocId, score);
			}
			testScoresReader.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
	}

	private void printFeatures(BufferedWriter output, Integer relevance,
			Integer qId, Double[] fVec, String doc) {
		try {
			String result = relevance + " qid:" + qId + " ";
			int featuresIndex = 1;
			for(int i=0; i<fVec.length; i++){
				if(fVec[i] != Double.MIN_VALUE){
					result += featuresIndex + ":" + String.format( "%.2f", fVec[i]) + " ";
					featuresIndex += 1;
				}
			}
			result += "# " + doc;
			output.write(result + "\n");
		} catch (IOException e) {
			System.out.println("Failed to print feature vectors during training.");
		}

	}

	// normalize the feature values to be between [0..1]
	private void normalizeFeatures(Double[] featureVector, Double[] featureMax, Double[] featureMin) {
		for(int i=0; i<numBaseFeatures; i++){
			Double feature = featureVector[i];
			if(feature == Double.MIN_VALUE)	continue;
			Double range = featureMax[i] - featureMin[i];
			if(range == 0.0)	featureVector[i] = 0.0;
			else				featureVector[i] = (feature - featureMin[i]) / range;
		}
	}

	/*
	 * This is the crucial part generating the feature vectors
	 */
	private void buildFeatures(Double[] featureVector, String externalDocId,
			String[] queryTerms, Double[] featureMax, Double[] featureMin) {

		try {
			int internalDocId = Idx.getInternalDocid(externalDocId);
			String rawUrl = Idx.getAttribute ("rawUrl", internalDocId);

			// Spam score
			if(!disabledFeatures.contains(1)){
				int spamScore = Integer.parseInt (Idx.getAttribute ("score", internalDocId));
				featureVector[0] = (double) spamScore;
				if(featureVector[0] > featureMax[0])	featureMax[0] = featureVector[0];
				if(featureVector[0] < featureMin[0])	featureMin[0] = featureVector[0];
			}

			// URL depth
			if(!disabledFeatures.contains(2)){			
				// assuming that every URL starts with "http://"
				int urlDepth = rawUrl.split("/", -1).length - 3;
				featureVector[1] = (double) urlDepth;
				if(featureVector[1] > featureMax[1])	featureMax[1] = featureVector[1];
				if(featureVector[1] < featureMin[1])	featureMin[1] = featureVector[1];
			}

			// fromWikipedia score
			if(!disabledFeatures.contains(3)){
				int fromWikipedia = rawUrl.contains("wikipedia.org") ? 1 : 0;
				featureVector[2] = (double) fromWikipedia;
				if(featureVector[2] > featureMax[2])	featureMax[2] = featureVector[2];
				if(featureVector[2] < featureMin[2])	featureMin[2] = featureVector[2];
			}

			// PageRank score
			if(!disabledFeatures.contains(4)){
				Double pageRank = pageRanks.get(externalDocId);
				//TODO: How to deal with pageranks for documents not present in file
				if(pageRank == null)	pageRank = -10.0;		
				featureVector[3] = pageRank;
				if(featureVector[3] > featureMax[3])	featureMax[3] = featureVector[3];
				if(featureVector[3] < featureMin[3])	featureMin[3] = featureVector[3];
			}

			Double[] contentFeatures = new Double[]{0.0, 0.0, 0.0};

			// content features for the different fields
			String[] fields = new String[]{"body", "title", "url", "inlink"};
			for(int i=0; i<fields.length; i++){
				int start = 3*i+4;
				getContentFeatures(contentFeatures, queryTerms, internalDocId, fields[i]);
				featureVector[start] = contentFeatures[0];
				featureVector[start+1] = contentFeatures[1];
				featureVector[start+2] = contentFeatures[2];
				if(featureVector[start] > featureMax[start])	featureMax[start] = featureVector[start];
				if(featureVector[start+1] > featureMax[start+1])	featureMax[start+1] = featureVector[start+1];
				if(featureVector[start+2] > featureMax[start+2])	featureMax[start+2] = featureVector[start+2];
				if(featureVector[start] < featureMin[start])	featureMin[start] = featureVector[start];
				if(featureVector[start+1] < featureMin[start+1])	featureMin[start+1] = featureVector[start+1];
				if(featureVector[start+2] < featureMin[start+2])	featureMin[start+2] = featureVector[start+2];
			}

			// custom features, using my imagination
			featureVector[16] = 0.0;
			if(featureVector[16] > featureMax[16])	featureMax[16] = featureVector[16];
			if(featureVector[16] < featureMin[16])	featureMin[16] = featureVector[16];
			featureVector[17] = 0.0;
			if(featureVector[17] > featureMax[17])	featureMax[17] = featureVector[17];
			if(featureVector[17] < featureMin[17])	featureMin[17] = featureVector[17];

			// write out negative values for all disabled features
			for(Integer i : disabledFeatures)
				featureVector[i-1] = Double.MIN_VALUE;

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("Unable to build features.");
		}


	}

	private void getContentFeatures(Double[] contentFeats, String[] queryTerms, 
			int internalDocId, String field) {
		// contentFeats contain scores for bm25, indri and term overlap
		try {		  
			TermVector forwardIndex = new TermVector(internalDocId, field);
			int queryTermMatches = 0;
			HashMap<String, Double> queryTermFreqs = new HashMap<String, Double>();
			for(String qTerm: queryTerms){
				if(queryTermFreqs.containsKey(qTerm))	queryTermFreqs.put(qTerm, queryTermFreqs.get(qTerm + 1.0));
				else									queryTermFreqs.put(qTerm, 1.0);
			}
			boolean docMatches = false;

			int numStems = forwardIndex.stemsLength();
			for(int i=1; i<numStems; i++){
				if(queryTermFreqs.containsKey(forwardIndex.stemString(i))){
					docMatches = true;
					contentFeats[1] = 1.0;
					break;
				}
			}

			if(docMatches){

				double power = 1.0 / (double) queryTerms.length;
				for(int i=1; i<numStems; i++){
					String stem = forwardIndex.stemString(i);
					boolean termMatch = queryTermFreqs.containsKey(stem);

					if(termMatch){
						queryTermMatches++;
						// compute the BM25 score
						double qtf = queryTermFreqs.get(stem);
						contentFeats[0] += getBM25Score(forwardIndex, field, qtf, i);
						// compute the Indri score
						contentFeats[1] *= Math.pow(getIndriScore(forwardIndex, field, i), power);	
						queryTermFreqs.remove(stem);
					}

				}

				// Is this necessary ??
				// add default scores for query terms not present in document
				/*
			for(String qTerm : queryTermFreqs.keySet()){
				// bm25 default
				double df = (double) forwardIndex.stemDf(idx);		// ??
				double tf = 0.0;
				double qtf = queryTermFreqs.get(qTerm);
				double rsj = Math.max(0.0, Math.log((N - df + 0.5) / (df + 0.5)));
				double tf_weight = tf / (tf + k_1*(1 - b + (b * doclen / avg_doclen)));
				double user_weight = (k_3 + 1) * qtf / (k_3 + qtf);
				bm25 += rsj * tf_weight * user_weight;
				// indri default
				double ctf = (double) forwardIndex.totalStemFreq(idx);	
				double score = ((1.0 - lambda) * (tf + (mu * ctf / corpuslen)) / (doclen + mu)) + 
							(lambda * ctf / corpuslen);
				indri *= Math.pow(score, power);
			}
				 */
			}

			// compute the term overlap score
			contentFeats[2] = (double) queryTermMatches / (double) queryTerms.length;

		} catch (IOException e) {
			System.out.println("Could not build content features.");
		}

	}

	private double getIndriScore(TermVector forwardIndex, String field, int idx) {
		double score = 0.0;
		try {
			double ctf = (double) forwardIndex.totalStemFreq(idx);
			double lambda = this.indrimodel.lambda;
			double mu = (double) this.indrimodel.mu;
			double tf = (double) forwardIndex.stemFreq(idx);
			double corpuslen = (double) Idx.getSumOfFieldLengths(field);
			double doclen = (double) forwardIndex.positionsLength();
			score = ((1.0 - lambda) * (tf + (mu * ctf / corpuslen)) / (doclen + mu)) +
					(lambda * ctf / corpuslen);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return score;
	}

	public double getBM25Score(TermVector forwardIndex, String field, double qtf, int idx){
		double score = 0.0;
		try {
			double N = (double) Idx.getNumDocs();
			double k_1 = this.bm25model.k_1;
			double b = this.bm25model.b;
			double k_3 = this.bm25model.k_3;
			double doclen = (double) forwardIndex.positionsLength();
			double corpuslen = (double) Idx.getSumOfFieldLengths(field);
			double avg_doclen = corpuslen / (double) Idx.getDocCount(field);			

			double df = (double) forwardIndex.stemDf(idx);
			double tf = (double) forwardIndex.stemFreq(idx);
			double rsj = Math.max(0.0, Math.log((N - df + 0.5) / (df + 0.5)));
			double tf_weight = tf / (tf + k_1*(1 - b + (b * doclen / avg_doclen)));
			double user_weight = (k_3 + 1) * qtf / (k_3 + qtf);
			score = rsj * tf_weight * user_weight;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return score;
	}

}
