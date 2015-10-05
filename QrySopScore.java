/**
 *  Copyright (c) 2015, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;
import java.util.ArrayList;

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
		else if (r instanceof RetrievalModelIndri) {
			return this.getScoreIndri (r);
		}
		else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the SCORE operator.");
		}
	}
	
	  /**
	   * A default score for the probabilistic Indri ranked retrieval model
	   */
	public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
		
		if(this.defaultScore != Double.MIN_VALUE)
			return this.defaultScore;

		if (r instanceof RetrievalModelIndri) {
			QryIop q = (QryIop) this.args.get(0);
			this.defaultScore = getQueryLikelihood(r, docid, q);
			return this.defaultScore;
		} else {
			throw new IllegalArgumentException
			(r.getClass().getName() + " doesn't support the default score for AND operator.");
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

	/**
	 *  getScore for the BM25 retrieval model.
	 *  @param r The retrieval model that determines how scores are calculated.
	 *  @return The document score.
	 *  @throws IOException Error accessing the Lucene index
	 */
	public double getUserWeightedScore (RetrievalModelBM25 model, double qtf) throws IOException {
		QryIop q = (QryIop) this.args.get(0);
		String field = q.getField();
		int docid = q.docIteratorGetMatch();
		if (docid == INVALID_DOCID){
			System.out.println("invalid encoutnered");
			return 0.0;
		}
		
		// collect the statistics
		double N = (double) Idx.getDocCount(field);
		//double N = (double) Idx.getNumDocs();
		double df = (double) q.getDf();
		double tf = (double) q.docIteratorGetMatchPosting().tf;
		double doclen = (double) Idx.getFieldLength(field, docid);
		double avg_doclen = (double) Idx.getSumOfFieldLengths(field) / (double) Idx.getDocCount(field);
		//TODO: Fix logic for qtf
		//double qtf = 1.0;
		double k_1 = model.k_1;
		double b = model.b;
		double k_3 = model.k_3;
		
		// RSJ weight
		double rsj = Math.max(0.0, Math.log((N - df + 0.5) / (df + 0.5)));
		// tf weight
		double tf_weight = tf / (tf + k_1*(1 - b + (b * doclen / avg_doclen)));
		// user weight
		double user_weight = (k_3 + 1) * qtf / (k_3 + qtf);
		
		return rsj * tf_weight * user_weight;
	}
	
	private double getQueryLikelihood(RetrievalModel r, int docid, QryIop q) throws IOException{
		double score = 1.0;
		double lambda = ((RetrievalModelIndri) r).lambda;
		double mu = (double) ((RetrievalModelIndri) r).mu;
		double tf = 0.0;
		double ctf = (double) q.invertedList.ctf;
		double doclen = (double) Idx.getFieldLength(q.field, docid);
		double corpuslen = Idx.getSumOfFieldLengths(q.field);
		
		score = (1.0 - lambda) * (tf + (mu * ctf / corpuslen)) / (doclen + mu);
		score += lambda * ctf / corpuslen;
		return score;
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
	
	/**
	 * For use in a HashMap
	 */
  public boolean equals(Object obj){
      if(!(obj instanceof QrySopScore))   return false; 
      QrySopScore that = (QrySopScore) obj;
      return this.args.equals(that.args);
   }
   
   public int hashCode(){
       return this.args.hashCode();
   }

}
