/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 *  The SUM operator for the Indri ranked retrieval model.
 */
public class QrySopWeightedSum extends QrySop {
	
	/**
	 * The weights corresponding to the arguments
	 */
	protected ArrayList<Double> weights = new ArrayList<Double>();
	protected Double sumOfWeights = 0.0;

	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch (RetrievalModel r) {
		if(r instanceof RetrievalModelIndri)
			return this.docIteratorHasMatchMin(r);
		else
			return false;
	}

	/**
	 *  Get a score for the document that docIteratorHasMatch matched.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	public double getScore (RetrievalModel r) throws IOException {

		if (r instanceof RetrievalModelIndri) {
			return this.getScoreIndri (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the WSUM operator.");
		}
	}
	
	  /**
	   * A default score for the probabilistic Indri ranked retrieval model
	   */
	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
		
		if (r instanceof RetrievalModelIndri) {
			double score = 0.0;
			// #WSUM operator combines the default scores of its arguments
			int numArgs = this.args.size();
			for(int i=0; i<numArgs; i++){
				Qry arg = this.args.get(i);
				double weight = this.weights.get(i) / this.sumOfWeights; 
				score += weight * ((QrySop) arg).getDefaultScore(r, docid);  
			}
			return score;
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the default score for WSUM operator.");
		}
	}

	
	/**
	 *  getScore for the Indri retrieval model.
	 *  @param r The Indri retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreIndri (RetrievalModel r) throws IOException {
		double score = 0.0;
		if (this.docIteratorHasMatchCache()) {
			int docId = this.docIteratorGetMatch();
			// #WSUM operator combines the score with weighted average
			int numArgs = this.args.size();
			for(int i=0; i<numArgs; i++){
				Qry arg = this.args.get(i);
				double weight = this.weights.get(i) / this.sumOfWeights;
				if(!arg.docIteratorHasMatch(r) || docId != arg.docIteratorGetMatch()){
					score += weight * ((QrySop) arg).getDefaultScore(r, docId);  
				}
				else
					score += weight * ((QrySop) arg).getScore(r);
			}
		}
		return score;
	}
	
	/**
	 * Method to add a weight along side an argument
	 */
	public void addWeight(Double weight){
		this.weights.add(weight);
		this.sumOfWeights += weight;
	}
	
	/**
	 *  Get a string version of this query operator.  This is a specific
	 *  method that works for WSUM.
	 *  @return The string version of this query operator.
	 */
	@Override 
	public String toString(){

		String result = new String ();
		for (int i=0; i<this.args.size(); i++)
			result += this.weights.get(i) + " " + this.args.get(i) + " ";
			
		return (this.getDisplayName() + "( " + result + ")");
	}

}