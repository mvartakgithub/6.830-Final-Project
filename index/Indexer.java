import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.standard.*;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.index.IndexWriterConfig.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.*;

/**
   Generate a Lucene index for the collection of ACM XML files.
 */
class Indexer {
  static final Pattern REFS = Pattern.compile("References:? \\[");
  static final String QUERY = "select pr from pagerank where citer_proc_id = ? and citer_paper_id = ?";

  /**
     Return the pagerank for the specified paper
   */
  static Field pagerank(Field pid, Field aid, PreparedStatement ps) throws SQLException {
    ps.setString(1, pid.stringValue());
    ps.setString(2, aid.stringValue());
    ResultSet r = ps.executeQuery();
    if (r.next()) {
      return new Field("pagerank", r.getString(1), Field.Store.YES, Field.Index.NO);
    }
    else {
      return new Field("pagerank", "0", Field.Store.YES, Field.Index.NO);
    }
  }

  /**
     Return a formatted list of names separated by "and"
   */
  static String formatAuthors(List<String> first, List<String> middle,
                              List<String> last) {
    StringBuffer result = new StringBuffer();
    Iterator<String> iter1 = first.iterator();
    Iterator<String> iter2 = middle.iterator();
    Iterator<String> iter3 = last.iterator();
    while (iter1.hasNext() && iter2.hasNext() && iter3.hasNext()) {
      String s1 = iter1.next().trim();
      String s2 = iter2.next().trim();
      String s3 = iter3.next().trim();
      if (!s1.isEmpty()) {
        result.append(s1);
        if (!s2.isEmpty()) {
          result.append(" ");
        }
        if (s2.isEmpty() && !s3.isEmpty()) {
          result.append(" ");
        }
      }
      if (!s2.isEmpty()) {
        result.append(s2);
        if (!s3.isEmpty()) {
          result.append(" ");
        }
      }
      if (!s3.isEmpty()) {
        result.append(s3);
      }
      if (iter1.hasNext() && iter2.hasNext() && iter3.hasNext()) {
        result.append(" and ");
      }
    }
    return result.toString();
  }

  /**
     Return the authors of the current article, separated by "and".
   */
  static String parseAuthors(XMLStreamReader r) throws XMLStreamException {
    List<String> first = new LinkedList<String>();
    List<String> middle = new LinkedList<String>();
    List<String> last = new LinkedList<String>();
    while (r.hasNext()) {
      r.next();
      if (r.getEventType() == XMLEvent.START_ELEMENT) {
        if (r.getName().toString().equals("first_name")) {
          first.add(r.getElementText());
        }
        else if (r.getName().toString().equals("middle_name")) {
          middle.add(r.getElementText());
        }
        else if (r.getName().toString().equals("last_name")) {
          last.add(r.getElementText());
        }
      }
      else if (r.getEventType() == XMLEvent.END_ELEMENT &&
               r.getName().toString().equals("authors")) {
        return formatAuthors(first, middle, last);
      }
    }
    return formatAuthors(first, middle, last);
  }

  /**
     Return the abstract of the current article.
   */
  static String parseAbstract(XMLStreamReader r) throws XMLStreamException {
    StringBuffer result = new StringBuffer();
    while (r.hasNext()) {
      r.next();
      if (r.getEventType() == XMLEvent.CDATA ||
          r.getEventType() == XMLEvent.CHARACTERS) {
        result.append(r.getText().trim());
      }
      else if (r.getEventType() == XMLEvent.END_ELEMENT &&
               r.getName().toString().equals("abstract")) {
        return result.toString();
      }
    }
    return result.toString();
  }

  /**
     Return a string without the references section of the full text.
   */
  static String parseFullText(String fulltext) {
    Matcher m = REFS.matcher(fulltext);
    if (m.find()) {
      return fulltext.substring(0, m.start(0));
    }
    else {
      return fulltext;
    }
  }
  
  /**
     Return the first article parsed from the current position of the specified
     reader.
   */
  static Document parseArticle(XMLStreamReader r, Field pid,
                               PreparedStatement ps) throws XMLStreamException,
      SQLException {
    Document result = new Document();
    result.add(pid);
    while (r.hasNext()) {
      r.next();
      if (r.getEventType() == XMLEvent.START_ELEMENT) {
        if (r.getName().toString().equals("title")) {
          result.add(new Field("title", r.getElementText(), Field.Store.YES,
                               Field.Index.ANALYZED));
        }
        else if (r.getName().toString().equals("subtitle")) {
          result.add(new Field("subtitle", r.getElementText(), Field.Store.YES,
                               Field.Index.ANALYZED));
        }
        else if (r.getName().toString().equals("abstract")) {
          result.add(new Field("abstract", parseAbstract(r), Field.Store.YES,
                               Field.Index.ANALYZED));
        }
        else if (r.getName().toString().equals("ft_body")) {
          result.add(new Field("fulltext", parseFullText(r.getElementText()),
                               Field.Store.NO, Field.Index.ANALYZED));
        }
        else if (r.getName().toString().equals("article_id")) {
          result.add(new Field("article_id", r.getElementText(), Field.Store.YES,
                               Field.Index.NO));
          result.add(pagerank((Field)result.getFieldable("proc_id"),
                              (Field)result.getFieldable("article_id"), ps));
        }
        else if (r.getName().toString().equals("url")) {
          result.add(new Field("url", r.getElementText(), Field.Store.YES,
                               Field.Index.NO));
        }
        else if (r.getName().toString().equals("authors")) {
          result.add(new Field("authors", parseAuthors(r), Field.Store.YES,
                               Field.Index.NO));
        }
      }
      else if (r.getEventType() == XMLEvent.END_ELEMENT &&
               r.getName().toString().equals("article_rec")) {
        return result;
      }
    }
    return result;
  }
  
  /**
     Return a list of Documents parsed from the specified file using the
     specified factory.
   */
  static List<Document> parse(File f, XMLInputFactory factory,
                              PreparedStatement ps) throws
      FileNotFoundException, IOException, XMLStreamException, SQLException {
    List<Document> result = new LinkedList<Document>();
    if (!f.exists() || !f.canRead() || !f.isFile()) {
      return result;
    }
    XMLStreamReader r = factory.createXMLStreamReader(new FileInputStream(f));
    Field pid = new Field("proc_id", "", Field.Store.YES, Field.Index.NO);
    while (r.hasNext()) {
      r.next();
      if (r.getEventType() == XMLEvent.START_ELEMENT) {
        if (r.getName().toString().equals("proc_id") ||
            r.getName().toString().equals("issue_id")) {
          pid.setValue(r.getElementText());
        }
        else if (r.getName().toString().equals("article_rec")) {
          result.add(parseArticle(r, pid, ps));
        }
      }
      else if (r.getEventType() == XMLEvent.END_ELEMENT) {
        if (r.getName().toString().equals("content")) {
          return result;
        }
      }
    }
    return result;
  }

  /**
     Walk the specified directory, indexing files using the specified indexer,
     parsing them using the specified factory, and getting pageranks using the
     specified query.
   */
  static void visit(File d, IndexWriter w, XMLInputFactory factory,
                    PreparedStatement ps) {
    if (!d.exists() || !d.canRead() || !d.isDirectory()) {
      throw new IllegalArgumentException();
    }
    for (File f : d.listFiles()) {
      if (f.isDirectory()) {
        visit(f, w, factory, ps);
      }
      else {
        try {
          for (Document doc : parse(f, factory, ps)) {
            w.addDocument(doc);
          }
        }
        catch (Exception e) {
          System.err.format("Error parsing %s: %s\n", f, e.getMessage());
        }
      }
    }
  }
  
  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Usage: java Indexer IN OUT");
      System.exit(1);
    }
    File in = new File(args[0]);
    if (!in.exists() || !in.isDirectory()) {
      System.err.println("Cannot index non-directory");
      System.exit(1);
    }
    Scanner s = new Scanner(System.in);
    String host = s.next().trim();
    String port = s.next().trim();
    String db = s.next().trim();
    String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
    String user = s.next().trim();
    String pass = s.next().trim();
    try {
      Connection conn = DriverManager.getConnection(url, user, pass);
      PreparedStatement ps = conn.prepareStatement(QUERY);
      Directory out = FSDirectory.open(new File(args[1]));
      Analyzer a = new StandardAnalyzer(Version.LUCENE_36);
      IndexWriterConfig c = new IndexWriterConfig(Version.LUCENE_36, a);
      // Assume re-indexing for simplicity
      c.setOpenMode(OpenMode.CREATE_OR_APPEND);
      IndexWriter w = new IndexWriter(out, c);
      XMLInputFactory factory = XMLInputFactory.newInstance();
      visit(in, w, factory, ps);
      w.close();
      conn.close();
    }
    catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
    }
  }
}
