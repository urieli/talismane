///////////////////////////////////////////////////////////////////////////////
//Copyright (C) 2014 Joliciel Informatique
//
//This file is part of Talismane.
//
//Talismane is free software: you can redistribute it and/or modify
//it under the terms of the GNU Affero General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//Talismane is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU Affero General Public License for more details.
//
//You should have received a copy of the GNU Affero General Public License
//along with Talismane.  If not, see <http://www.gnu.org/licenses/>.
//////////////////////////////////////////////////////////////////////////////
package com.joliciel.talismane.rawText;

import com.joliciel.talismane.TalismaneTest;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;

import java.util.List;
import java.util.Map.Entry;

import static org.junit.Assert.*;

public class SentenceHolderTest extends TalismaneTest {

  @Test
  public void testGetDetectedSentences() throws Exception {
    System.setProperty("config.file", "src/test/resources/test.conf");
    ConfigFactory.invalidateCaches();
    final Config config = ConfigFactory.load();

    final String sessionId = "test";
    SentenceHolder holder = new SentenceHolder(0, false, sessionId);

    String originalText = "Hello  <b>World</b>. <o>Output this</o>How are you?  Fine<o>Output</o>,  ";
    holder.setProcessedText("Hello  World. How are you?  Fine,  ");

    for (int i = 0; i < holder.getProcessedText().length(); i++) {
      if (i < "Hello  ".length())
        holder.addOriginalIndex(i);
      else if (i < "Hello  World".length())
        holder.addOriginalIndex(i + "<b>".length());
      else if (i < "Hello  World. ".length())
        holder.addOriginalIndex(i + "<b></b>".length());
      else if (i < "Hello  World. How are you?  Fine".length())
        holder.addOriginalIndex(i + "<b></b><o>Output this</o>".length());
      else
        holder.addOriginalIndex(i + "<b></b><o>Output this</o><o>Output</o>".length());
    }
    holder.getOriginalTextSegments().put("Hello  World. ".length() - 1, "<o>Output this</o>");
    holder.getOriginalTextSegments().put("Hello  World. How are you?  Fine,".length() - 1, "<o>Output</o>");

    holder.addSentenceBoundary("Hello  World.".length());
    holder.addSentenceBoundary("Hello  World. How are you?".length());

    List<Sentence> sentences = holder.getDetectedSentences(null);
    for (Sentence sentence : sentences) {
      System.out.println(sentence.getText().toString());
    }
    assertEquals(3, sentences.size());

    Sentence sentence1 = sentences.get(0);
    assertEquals("Hello World.", sentence1.getText());
    assertEquals("Hello  <b>W".length(), sentence1.getOriginalIndex("Hello W".length()));
    assertEquals("Hello  <b>World</b>.".length() - 1, sentence1.getOriginalIndex("Hello World.".length() - 1));

    Sentence sentence2 = sentences.get(1);
    assertEquals("How are you?", sentence2.getText());
    assertEquals("Hello  <b>World</b>. <o>Output this</o>H".length(), sentence2.getOriginalIndex("H".length()));
    for (Entry<Integer, String> originalSegment : sentence2.getOriginalTextSegments().entrySet()) {
      System.out.println(originalSegment.getKey() + ": \"" + originalSegment.getValue() + "\"");
    }
    assertEquals("<o>Output this</o>", sentence2.getOriginalTextSegments().get(0));

    Sentence leftover = sentences.get(2);
    assertFalse(leftover.isComplete());
    assertEquals("Fine, ", leftover.getText());
    assertEquals("Hello  <b>World</b>. <o>Output this</o>How are you?  F".length(), leftover.getOriginalIndex("F".length()));
    for (Entry<Integer, String> originalSegment : leftover.getOriginalTextSegments().entrySet()) {
      System.out.println(originalSegment.getKey() + ": \"" + originalSegment.getValue() + "\"");
    }
    assertEquals("<o>Output</o>", leftover.getOriginalTextSegments().get(4));

    SentenceHolder holder2 = new SentenceHolder(0, false, sessionId);

    String originalText2 = "thanks, and you";
    holder2.setProcessedText("thanks, and you");
    for (int i = 0; i < holder2.getProcessedText().length(); i++) {
      holder2.addOriginalIndex(originalText.length() + i);
    }
    sentences = holder2.getDetectedSentences(leftover);
    assertEquals(1, sentences.size());

    leftover = sentences.get(0);
    assertEquals("Fine, thanks, and you", leftover.getText());
    assertEquals("Hello  <b>World</b>. <o>Output this</o>How are you?  F".length(), leftover.getOriginalIndex("F".length()));
    for (Entry<Integer, String> originalSegment : leftover.getOriginalTextSegments().entrySet()) {
      System.out.println(originalSegment.getKey() + ": \"" + originalSegment.getValue() + "\"");
    }
    assertEquals("<o>Output</o>", leftover.getOriginalTextSegments().get(4));
    assertFalse(leftover.isComplete());

    SentenceHolder holder3 = new SentenceHolder(0, false, sessionId);

    String originalText3 = "? Grand.";
    holder3.setProcessedText(originalText3);
    for (int i = 0; i < holder2.getProcessedText().length(); i++) {
      holder3.addOriginalIndex(originalText.length() + originalText2.length() + i);
    }
    holder3.addSentenceBoundary("?".length());
    holder3.addSentenceBoundary("? Grand.".length());
    sentences = holder3.getDetectedSentences(leftover);
    System.out.println(sentences.toString());
    assertEquals(2, sentences.size());

    sentence1 = sentences.get(0);
    assertEquals("Fine, thanks, and you?", sentence1.getText());
    assertEquals("Hello  <b>World</b>. <o>Output this</o>How are you?  F".length(), sentence1.getOriginalIndex("F".length()));
    for (Entry<Integer, String> originalSegment : sentence1.getOriginalTextSegments().entrySet()) {
      System.out.println(originalSegment.getKey() + ": \"" + originalSegment.getValue() + "\"");
    }
    assertEquals("<o>Output</o>", sentence1.getOriginalTextSegments().get(4));
    assertTrue(sentence1.isComplete());

    sentence2 = sentences.get(1);
    assertEquals("Grand.", sentence2.getText());
    assertEquals("Hello  <b>World</b>. <o>Output this</o>How are you?  Fine<o>Output</o>,  thanks, and you? G".length(),
        sentence2.getOriginalIndex("G".length()));
    assertTrue(sentence2.isComplete());
  }

  @Test
  public void testGetDetectedSentencesWithBoundaryAtEnd() throws Exception {
    System.setProperty("config.file", "src/test/resources/test.conf");
    ConfigFactory.invalidateCaches();
    final Config config = ConfigFactory.load();

    final String sessionId = "test";
    SentenceHolder holder = new SentenceHolder(0, false, sessionId);

    holder.setProcessedText("Hello World.");

    for (int i = 0; i < holder.getProcessedText().length(); i++) {
      holder.addOriginalIndex(i);
    }

    holder.addSentenceBoundary("Hello World.".length());

    List<Sentence> sentences = holder.getDetectedSentences(null);
    for (Sentence sentence : sentences) {
      System.out.println(sentence.getText().toString());
    }
    assertEquals(1, sentences.size());

    assertEquals("Hello World.", sentences.iterator().next().getText());

  }

  @Test
  public void testGetDetectedSentencesWithNewlines() throws Exception {
    System.setProperty("config.file", "src/test/resources/test.conf");
    ConfigFactory.invalidateCaches();
    final Config config = ConfigFactory.load();

    final String sessionId = "test";
    SentenceHolder holder = new SentenceHolder(0, false, sessionId);

    holder.setProcessedText("Hello World. How are you? Fine thanks.");

    for (int i = 0; i < holder.getProcessedText().length(); i++) {
      holder.addOriginalIndex(i);
    }

    holder.addSentenceBoundary("Hello World.".length());
    holder.addSentenceBoundary("Hello World.\nHow\nare you?".length());
    holder.addSentenceBoundary("Hello World.\nHow\nare you? Fine\nthanks.".length());
    holder.addNewline(0, 0);
    holder.addNewline("Hello World.\n".length(), 1);
    holder.addNewline("Hello World.\nHow\n".length(), 2);
    holder.addNewline("Hello World.\nHow\nare you? Fine\n".length(), 3);

    List<Sentence> sentences = holder.getDetectedSentences(null);
    for (Sentence sentence : sentences) {
      System.out.println(sentence.getText().toString());
    }
    assertEquals(3, sentences.size());

    for (int i = 0; i < sentences.size(); i++) {
      Sentence sentence = sentences.get(i);
      if (i == 0) {
        assertEquals("Hello World.", sentence.getText());
      } else if (i == 1) {
        assertEquals("How are you?", sentence.getText());
        assertEquals(1, sentence.getLineNumber(sentence.getOriginalIndex("How".length() - 1)));
        assertEquals(2, sentence.getColumnNumber(sentence.getOriginalIndex("How".length() - 1)));
        assertEquals(2, sentence.getLineNumber(sentence.getOriginalIndex("How a".length() - 1)));
        assertEquals(2, sentence.getColumnNumber(sentence.getOriginalIndex("How are".length() - 1)));
      }
    }

  }
}
