package com.joliciel.talismane.lexicon;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Set;

import com.joliciel.talismane.TalismaneTest;
import org.junit.Test;

import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.posTagger.PosTag;
import com.joliciel.talismane.posTagger.PosTagSet;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class LexiconReaderTest extends TalismaneTest {

  @Test
  public void testReadLexicons() throws Exception {
    System.setProperty("config.file", "src/test/resources/testWithLex.conf");
    ConfigFactory.invalidateCaches();
    final Config config = ConfigFactory.load();

    final String sessionId = "test";

    PosTaggerLexicon lexicon = TalismaneSession.get(sessionId).getMergedLexicon();

    List<LexicalEntry> entries = lexicon.getEntries("dame");
    for (LexicalEntry entry : entries) {
      System.out.println(entry);
    }
    assertEquals(9, entries.size());

    PosTagSet posTagSet = TalismaneSession.get(sessionId).getPosTagSet();

    entries = lexicon.findLexicalEntries("dame", posTagSet.getPosTag("NC"));
    for (LexicalEntry entry : entries) {
      System.out.println(entry);
    }
    assertEquals(2, entries.size());

    Set<PosTag> posTags = lexicon.findPossiblePosTags("dame");
    System.out.println(posTags);

    assertEquals(4, posTags.size());

    System.clearProperty("config.file");
    ConfigFactory.invalidateCaches();
  }

}
