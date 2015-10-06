/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

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
		if (r instanceof RetrievalModelUnrankedBoolean) {
			return this.getScoreUnrankedBoolean (r);
		} else if (r instanceof RetrievalModelRankedBoolean) {
			return this.getScoreRankedBoolean (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the OR operator.");
		}
	}
	
	  /**
	   * A default score for the probabilistic Indri ranked retrieval model
	   */
	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
		
		if (r instanceof RetrievalModelIndri) {
			double score = 1.0;
			// #OR operator combines the default scores of its arguments
			for(Qry arg : this.args)
				score *= (1.0 - ((QrySop) arg).getDefaultScore(r, docid));  
			score = 1.0 - score;
			return score;
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the default score for AND operator.");
		}
	}

	/**
	 *  getScore for the UnrankedBoolean retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
		double score = getScoreRankedBoolean(r);
		return score > 0.0 ? 1.0 : 0.0;
	}

	/**
	 *  getScore for the RankedBoolean retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
		double score = 0.0;
		if (this.docIteratorHasMatchCache()) {
			int docId = this.docIteratorGetMatch();
			// #OR operator combines the score with MAX	
			for(Qry arg : this.args){    	  
				if(arg.docIteratorHasMatch(r) && docId == arg.docIteratorGetMatch()){
					double argScore = ((QrySop) arg).getScore(r);
					if(argScore > score)
						score = argScore;
				}
			}
		}
		return score;
	}

}
