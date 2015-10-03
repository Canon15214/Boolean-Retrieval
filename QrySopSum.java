/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.HashSet;
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
	 *  getScore for the BM25 retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreBM25 (RetrievalModel r) throws IOException {
		double score = 0.0;
		if (this.docIteratorHasMatchCache()) return score;
			
		int docId = this.docIteratorGetMatch();
		// #SUM operator combines the scores by summing them
		for(Qry arg: this.args)		((RetrievalModelBM25) r).addQuery(arg);
		Set<Qry> querySet = ((RetrievalModelBM25) r).getQueries();
		for(Qry arg : querySet)
			if(arg.docIteratorHasMatch(r) && docId == arg.docIteratorGetMatch())
				score += ((QrySop) arg).getScore(r);
		
		return score;
	}
	
}
