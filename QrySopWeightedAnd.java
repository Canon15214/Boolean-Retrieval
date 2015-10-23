/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.ArrayList;

/**
 *  The Weighted AND operator for the Indri retrieval model.
 */
public class QrySopWeightedAnd extends QrySop {

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
			(r.getClass().getName() + " doesn't support the WAND operator.");
		}
	}
	
	  /**
	   * A default score for the probabilistic Indri ranked retrieval model
	   */
	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {

		if (r instanceof RetrievalModelIndri) {
			double score = 1.0;
			// #WAND operator combines the default scores of its arguments
			//double power = 1.0 / (double) this.args.size();
			int numArgs = this.args.size();
			for(int i=0; i<numArgs; i++){
				Qry arg = this.args.get(i);
				double power = this.weights.get(i) / this.sumOfWeights; 
				score *= Math.pow(((QrySop) arg).getDefaultScore(r, docid), power);  
			}
			return score;
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the default score for AND operator.");
		}
	}

	
	/**
	 *  getScore for the Indri retrieval model.
	 *  @param r The Indri retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreIndri (RetrievalModel r) throws IOException {
		double score = 1.0;
		if (this.docIteratorHasMatchCache()) {
			int docId = this.docIteratorGetMatch();
			// #WAND operator combines the score with MIN
			//double power = 1.0 / (double) this.args.size();
			int numArgs = this.args.size();
			for(int i=0; i<numArgs; i++){
				Qry arg = this.args.get(i);
				double power = this.weights.get(i) / this.sumOfWeights;
				if(!arg.docIteratorHasMatch(r) || docId != arg.docIteratorGetMatch()){
					score *= Math.pow(((QrySop) arg).getDefaultScore(r, docId), power);  
				}
				else
					score *= Math.pow(((QrySop) arg).getScore(r), power);
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
	 *  method that works for WAND.
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
