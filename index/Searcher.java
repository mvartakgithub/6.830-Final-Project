import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryParser.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.function.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.*;

class Searcher {
  public static void main(String[] args) {
    if (args.length < 4) {
      System.err.println("Usage: java Searcher INDEX TWEIGHT AWEIGHT FWEIGHT");
      System.exit(1);
    }
    try {
      IndexReader r = IndexReader.open(FSDirectory.open(new File(args[0])));
      IndexSearcher s = new IndexSearcher(r);
      Analyzer a = new StandardAnalyzer(Version.LUCENE_36);
      String[] fields = new String[] {"title", "abstract", "fulltext"};
      Map<String, Float> boosts = new HashMap<String, Float>();
      boosts.put("title", Float.parseFloat(args[1]));
      boosts.put("abstract", Float.parseFloat(args[2]));
      boosts.put("fulltext", Float.parseFloat(args[3]));
      QueryParser p = new MultiFieldQueryParser(Version.LUCENE_36, fields, a,
                                                boosts);
      ValueSourceQuery pagerank = new FieldScoreQuery("pagerank", FieldScoreQuery.Type.FLOAT);
      Scanner in = new Scanner(System.in);
      while (in.hasNextLine()) {
        Query q = p.parse(in.nextLine().trim());
        TopDocs hits = s.search(q, 10);
        for (ScoreDoc d : hits.scoreDocs) {
          Document doc = s.doc(d.doc);
          float score = d.score;
          String title = doc.getFieldable("title").stringValue();
          String pid = doc.getFieldable("proc_id").stringValue();
          String aid = doc.getFieldable("article_id").stringValue();
          System.out.format("%f %s (%s, %s)\n", score, title, pid, aid);
        }
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
