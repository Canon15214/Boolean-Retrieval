/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.HashMap;
import java.util.Set;

/**
 *  The SUM operator for BM25 ranked retrieval model.
 */
public class QrySopSum extends QrySop {

	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch (RetrievalModel r) {
		return this.docIteratorHasMatchMin (r);
	}

	/**
	 *  Get a score for the document that docIteratorHasMatch matched.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	public double getScore (RetrievalModel r) throws IOException {

		if (r instanceof RetrievalModelBM25) {
			return this.getScoreBM25 (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the SUM operator.");
		}
	}
	
	  /**
	   * A default score for the probabilistic Indri ranked retrieval model
	   */
	// TODO: Implement the defaultScore for Indri SUM operator 
	// (right now, this is identical to Indri AND)
	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
		
		if (r instanceof RetrievalModelIndri) {
			double score = 1.0;
			// #AND operator combines the default scores of its arguments
			double power = 1.0 / (double) this.args.size();
			for(Qry arg : this.args)
				score *= Math.pow(((QrySop) arg).getDefaultScore(r, docid), power);  
			return score;
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the default score for AND operator.");
		}
	}

	
	/**
	 *  getScore for the BM25 retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreBM25 (RetrievalModel r) throws IOException {
		double score = 0.0;
		int docId = this.docIteratorGetMatch();
		// #SUM operator combines the scores by summing them
		clearQueries();
		for(Qry arg: this.args)	{
			this.addQuery(arg);
		}
		Set<Qry> querySet = getQueries();
		for(Qry arg : querySet){
			if(arg.docIteratorHasMatch(r) && docId == arg.docIteratorGetMatch()){
				if(arg instanceof QrySopScore && r instanceof RetrievalModelBM25){
					double qtf = (double) this.getQueryFrequency(arg);
					score += ((QrySopScore) arg).getUserWeightedScore((RetrievalModelBM25)r, qtf);
				}
				else score += ((QrySop) arg).getScore(r);
			}
		}
		return score;
	}
	
}