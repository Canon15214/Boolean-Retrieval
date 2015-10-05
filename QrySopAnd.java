/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch (RetrievalModel r) {
		if(r instanceof RetrievalModelUnrankedBoolean || r instanceof RetrievalModelRankedBoolean)
			return this.docIteratorHasMatchAll (r);
		else if(r instanceof RetrievalModelIndri)
			return true;
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

		if (r instanceof RetrievalModelUnrankedBoolean) {
			return this.getScoreUnrankedBoolean (r);
		} else if (r instanceof RetrievalModelRankedBoolean) {
			return this.getScoreRankedBoolean (r);
		} else if (r instanceof RetrievalModelIndri) {
			return this.getScoreIndri (r);
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the AND operator.");
		}
	}
	
	  /**
	   * A default score for the probabilistic Indri ranked retrieval model
	   */
	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {

		if(this.defaultScore != Double.MIN_VALUE)
			return this.defaultScore;
		
		if (r instanceof RetrievalModelIndri) {
			double score = 1.0;
			// #AND operator combines the default scores of its arguments
			double power = 1.0 / (double) this.args.size();
			for(Qry arg : this.args)
				score *= Math.pow(((QrySop) arg).getDefaultScore(r, docid), power);  
			this.defaultScore = score;
			
			return this.defaultScore;
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
		double score = Double.MAX_VALUE;
		if (this.docIteratorHasMatchCache()) {
			int docId = this.docIteratorGetMatch();
			// #AND operator combines the score with MIN
			for(Qry arg : this.args){
				if(!arg.docIteratorHasMatch(r) || docId != arg.docIteratorGetMatch()){
					score = 0.0;  
					break;
				}
				double argScore = ((QrySop) arg).getScore(r);
				if(argScore < score)	score = argScore;
			}
		}
		return score;
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
			// #AND operator combines the score with MIN
			double power = 1.0 / (double) this.args.size();
			for(Qry arg : this.args){
				if(!arg.docIteratorHasMatch(r) || docId != arg.docIteratorGetMatch()){
					score *= Math.pow(((QrySop) arg).getDefaultScore(r, docId), power);  
				}
				else
					score *= Math.pow(((QrySop) arg).getScore(r), power);
			}
		}
		return score;
	}

}
