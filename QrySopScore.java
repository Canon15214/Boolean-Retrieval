/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

	/**
	 *  Document-independent values that should be determined just once.
	 *  Some retrieval models have these, some don't.
	 */

	/**
	 *  Indicates whether the query has a match.
	 *  @param r The retrieval model that determines what is a match
	 *  @return True if the query matches, otherwise false.
	 */
	public boolean docIteratorHasMatch (RetrievalModel r) {
		return this.docIteratorHasMatchFirst (r);
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
		}
		else if (r instanceof RetrievalModelRankedBoolean) {
			return this.getScoreRankedBoolean (r);
		}
		else if (r instanceof RetrievalModelBM25) {
			return this.getScoreBM25 (r);
		}
		else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the SCORE operator.");
		}
	}

	/**
	 *  getScore for the Unranked retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
		Qry q = this.args.get(0);
		double count = (double) ((QryIop) q).docIteratorGetMatchPosting().tf;
		return count > 0.0 ? 1.0 : 0.0;
	}

	/**
	 *  getScore for the RankedBoolean retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
		QryIop q = (QryIop) this.args.get(0);
		return (double) q.docIteratorGetMatchPosting().tf;
	}
	
	/**
	 *  getScore for the BM25 retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	private double getScoreBM25 (RetrievalModel r) throws IOException {
		QryIop q = (QryIop) this.args.get(0);
		RetrievalModelBM25 model = (RetrievalModelBM25) r;
		String field = q.getField();
		int docid = q.docIteratorGetMatch();
		if (docid == INVALID_DOCID)	return 0.0;
		
		// collect the statistics
		double N = (double) Idx.getDocCount(field);
		double df = (double) q.getDf();
		double tf = (double) q.docIteratorGetMatchPosting().tf;
		double doclen = (double) Idx.getFieldLength(field, docid);
		double avg_doclen = (double) Idx.getSumOfFieldLengths(field) / N;
		double qtf = model.getQueryFrequency(q);
		double k_1 = model.k_1;
		double b = model.b;
		double k_3 = model.k_3;
		
		// RSJ weight
		double rsj = Math.log((N - df + 0.5) / (df + 0.5));
		// tf weight
		double tf_weight = tf / (tf + k_1*(1 - b + (b * doclen / avg_doclen)));
		// user weight
		double user_weight = (k_3 + 1) * qtf / (k_3 + qtf);
		
		return rsj * tf_weight * user_weight;
	}

	/**
	 *  Initialize the query operator (and its arguments), including any
	 *  internal iterators.  If the query operator is of type QryIop, it
	 *  is fully evaluated, and the results are stored in an internal
	 *  inverted list that may be accessed via the internal iterator.
	 *  @param r A retrieval model that guides initialization
	 *  @throws IOException Error accessing the Lucene index.
	 */
	public void initialize (RetrievalModel r) throws IOException {

		Qry q = this.args.get (0);
		q.initialize (r);
	}

}
