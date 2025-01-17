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
package com.joliciel.talismane.lexicon;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.joliciel.talismane.NeedsSessionId;
import com.joliciel.talismane.TalismaneException;
import com.joliciel.talismane.TalismaneSession;
import com.joliciel.talismane.sentenceAnnotators.SentenceAnnotatorLoadException;
import com.joliciel.talismane.utils.StringUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

/**
 * <p>
 * Given a set of lexicons described in a properties file, reads them into a
 * list of {@link PosTaggerLexicon} objects, using regex descriptors to read
 * lexical attributes from the files.
 * </p>
 * <p>
 * The structure of the properties file is as follows:
 * </p>
 * <ul>
 * <li><b>lexicons</b>: comma-delimited list of lexicon names, indicating how
 * each lexicon will be referred to within Talismane</li>
 * <li><b><i>lexiconName</i>.file</b>: the file containing the actual lexicon,
 * assumed to be contained in the same directory as the properties file. The
 * lexicon file structure is described in {@link LexiconFile}.</li>
 * <li><b><i>lexiconName</i>.regex</b>: the file containing the various regex
 * patterns used to extract lexical attributes from the lexicon, as described in
 * {@link RegexLexicalEntryReader}.</li>
 * <li><b><i>lexiconName</i>.categories</b>: optional - if included, limits the
 * categories that will be loaded to this list.</li>
 * <li><b><i>lexiconName</i>.exclusions</b>: optional - if included, reads a set
 * of exclusions from an exclusion file, where the first row is the list of
 * tab-delimited attribute names to be examined, and the remaining rows are
 * attribute values. If for a given entry all values match, the entry is
 * excluded.</li>
 * <li><b><i>lexiconName</i>.encoding</b>: optional - if included, the lexicon
 * file is read using the provided encoding. If not, it is read in UTF-8. All
 * other files are assumed to be in UTF-8.</li>
 * <li><b><i>lexiconName</i>.uniqueKey</b>: optional - a comma-delimited list of
 * {@link LexicalAttribute}. If included, defines lexical entry uniqueness: only
 * one entry with a given combination of these attributes will be added, and
 * others will be skipped</li>
 * </ul>
 * <p>
 * The order of lexicons is important, as entries will be searched for in the
 * order provided.
 * </p>
 * 
 * @see LexiconFile
 * @see RegexLexicalEntryReader
 * @see SimplePosTagMapper
 * @author Assaf Urieli
 *
 */
public class LexiconReader {
  private static final Logger LOG = LoggerFactory.getLogger(LexiconReader.class);

  private final String sessionId;

  public static void main(String[] args) throws IOException, TalismaneException {
    OptionParser parser = new OptionParser();
    parser.accepts("serializeLexicon", "serialize lexicon");
    parser.acceptsAll(Arrays.asList("?", "help"), "show help").availableUnless("serializeLexicon").forHelp();

    OptionSpec<String> sessionIdOption = parser.accepts("sessionId", "the current session id - configuration read as talismane.core.[sessionId]")
        .requiredUnless("?", "help").withRequiredArg().ofType(String.class);

    OptionSpec<File> lexiconPropsFileOption = parser.accepts("lexiconProps", "the lexicon properties file").withRequiredArg().required().ofType(File.class);
    OptionSpec<File> outFileOption = parser.accepts("outFile", "where to write the lexicon").withRequiredArg().required().ofType(File.class);

    if (args.length <= 1) {
      parser.printHelpOn(System.out);
      return;
    }

    OptionSet options = parser.parse(args);

    File lexiconPropsFile = options.valueOf(lexiconPropsFileOption);
    File outFile = options.valueOf(outFileOption);

    Config config = ConfigFactory.load();
    String sessionId = options.valueOf(sessionIdOption);

    LexiconReader lexiconSerializer = new LexiconReader(sessionId);
    List<PosTaggerLexicon> lexicons = lexiconSerializer.readLexicons(lexiconPropsFile);
    lexiconSerializer.serializeLexicons(lexicons, outFile);
  }

  public LexiconReader(String sessionId) {
    this.sessionId = sessionId;
  }

  /**
   * Read the lexicons based on an properties file, as described in the class
   * description. The pos-tag set is the one read from the configuration.
   * 
   * @param lexiconPropsFile
   * @return
   * @throws IOException
   * @throws TalismaneException
   *           if the config files contained an unknown property
   */
  public List<PosTaggerLexicon> readLexicons(File lexiconPropsFile) throws IOException, TalismaneException {
    LOG.debug("Serializing from " + lexiconPropsFile.getPath());
    List<PosTaggerLexicon> lexicons = new ArrayList<>();

    File lexiconDir = lexiconPropsFile.getParentFile();

    Map<String, String> properties = StringUtils.getArgMap(lexiconPropsFile, "UTF-8");

    String[] lexiconList = properties.get("lexicons").split(",");

    List<String> knownPropertyList = Arrays.asList("file", "regex", "categories", "exclusions", "encoding", "uniqueKey");
    Set<String> knownProperties = new HashSet<String>(knownPropertyList);
    for (String property : properties.keySet()) {
      if (property.equals("lexicons")) {
        // nothing to do
      } else {
        boolean foundLexicon = false;
        for (String lexiconName : lexiconList) {
          if (property.startsWith(lexiconName + ".")) {
            foundLexicon = true;
            String remainder = property.substring(lexiconName.length() + 1);
            if (!knownProperties.contains(remainder)) {
              throw new TalismaneException("Unknown property: " + property);
            }
          }
          if (foundLexicon)
            break;
        }
        if (!foundLexicon)
          throw new TalismaneException("Unknown lexicon in property: " + property);
      }
    }

    for (String lexiconName : lexiconList) {
      LOG.debug("Lexicon: " + lexiconName);
      String lexiconFilePath = properties.get(lexiconName + ".file");
      String lexiconRegexPath = properties.get(lexiconName + ".regex");
      String lexiconExclusionPath = properties.get(lexiconName + ".exclusions");
      String categoryString = properties.get(lexiconName + ".categories");
      String lexiconEncoding = properties.get(lexiconName + ".encoding");
      String lexiconUniqueKey = properties.get(lexiconName + ".uniqueKey");

      File lexiconRegexFile = new File(lexiconDir, lexiconRegexPath);
      Scanner regexScanner = new Scanner(new BufferedReader(new InputStreamReader(new FileInputStream(lexiconRegexFile), "UTF-8")));

      File lexiconInputFile = new File(lexiconDir, lexiconFilePath);
      InputStream inputStream = null;
      if (lexiconInputFile.getName().endsWith(".zip")) {
        InputStream inputStream2 = new FileInputStream(lexiconInputFile);
        @SuppressWarnings("resource")
        ZipInputStream zis = new ZipInputStream(inputStream2);
        zis.getNextEntry();
        inputStream = zis;
      } else {
        inputStream = new FileInputStream(lexiconInputFile);
      }

      Charset lexiconCharset = Charset.defaultCharset();
      if (lexiconEncoding != null)
        lexiconCharset = Charset.forName(lexiconEncoding);
      Reader reader = new BufferedReader(new InputStreamReader(inputStream, lexiconCharset));
      Scanner lexiconScanner = new Scanner(reader);

      RegexLexicalEntryReader lexicalEntryReader = new RegexLexicalEntryReader(regexScanner);

      Set<String> categories = null;
      if (categoryString != null) {
        categories = new HashSet<String>();
        String[] cats = categoryString.split(",");
        for (String cat : cats)
          categories.add(cat);
      }

      List<String> exclusionAttributes = null;
      List<List<String>> exclusions = null;
      if (lexiconExclusionPath != null) {
        exclusions = new ArrayList<List<String>>();
        File lexiconExclusionFile = new File(lexiconDir, lexiconExclusionPath);
        Scanner exclusionScanner = new Scanner(new BufferedReader(new InputStreamReader(new FileInputStream(lexiconExclusionFile), "UTF-8")));
        while (exclusionScanner.hasNextLine()) {
          String line = exclusionScanner.nextLine();
          if (line.length() == 0 || line.startsWith("#"))
            continue;
          String[] parts = line.split("\t");
          if (exclusionAttributes == null) {
            exclusionAttributes = new ArrayList<String>();
            for (String part : parts) {
              exclusionAttributes.add(part);
            }
          } else {
            List<String> exclusion = new ArrayList<String>();
            for (String part : parts) {
              exclusion.add(part);
            }
            exclusions.add(exclusion);
          }
        }
        exclusionScanner.close();
      }

      List<LexicalAttribute> uniqueAttributes = null;
      if (lexiconUniqueKey != null) {
        uniqueAttributes = new ArrayList<LexicalAttribute>();
        String[] uniqueKeyElements = lexiconUniqueKey.split(",");
        for (String uniqueKeyElement : uniqueKeyElements) {
          try {
            LexicalAttribute attribute = LexicalAttribute.valueOf(uniqueKeyElement);
            uniqueAttributes.add(attribute);
          } catch (IllegalArgumentException e) {
            lexiconScanner.close();
            throw new TalismaneException("Unknown attribute in " + lexiconName + ".uniqueKey: " + uniqueKeyElement);
          }
        }
      }

      LOG.debug("Serializing: " + lexiconFilePath);

      LexiconFile lexiconFile = new LexiconFile(lexiconName, lexiconScanner, lexicalEntryReader, sessionId);
      if (categories != null)
        lexiconFile.setCategories(categories);
      if (exclusionAttributes != null)
        lexiconFile.setExclusionAttributes(exclusionAttributes);
      if (exclusions != null)
        lexiconFile.setExclusions(exclusions);
      if (uniqueAttributes != null)
        lexiconFile.setUniqueKeyAttributes(uniqueAttributes);

      lexiconFile.load();
      inputStream.close();

      lexicons.add(lexiconFile);
    }
    return lexicons;
  }

  /**
   * Serialize a set of lexicons in zip format.
   */
  public void serializeLexicons(List<PosTaggerLexicon> lexicons, File outFile) throws IOException {
    File outDir = outFile.getParentFile();
    if (outDir != null)
      outDir.mkdirs();

    try (FileOutputStream fos = new FileOutputStream(outFile); ZipOutputStream zos = new ZipOutputStream(fos);) {
      zos.putNextEntry(new ZipEntry("lexicons.txt"));
      Writer writer = new BufferedWriter(new OutputStreamWriter(zos, "UTF-8"));
      for (PosTaggerLexicon lexicon : lexicons) {
        writer.write(lexicon.getName() + "\n");
      }
      writer.flush();
      zos.flush();

      for (PosTaggerLexicon lexicon : lexicons) {
        zos.putNextEntry(new ZipEntry(lexicon.getName() + ".obj"));
        ObjectOutputStream out = new ObjectOutputStream(zos);
        try {
          out.writeObject(lexicon);
        } finally {
          out.flush();
        }
        zos.flush();
      }
    }
  }

  public List<PosTaggerLexicon> deserializeLexicons(File lexiconFile) throws ClassNotFoundException, UnsupportedEncodingException, IOException {
    if (!lexiconFile.exists())
      throw new RuntimeException("LexiconFile does not exist: " + lexiconFile.getPath());
    FileInputStream fis = new FileInputStream(lexiconFile);
    ZipInputStream zis = new ZipInputStream(fis);
    return this.deserializeLexicons(zis);
  }

  public List<PosTaggerLexicon> deserializeLexicons(ZipInputStream zis) throws ClassNotFoundException, UnsupportedEncodingException, IOException {
    List<PosTaggerLexicon> lexicons = new ArrayList<PosTaggerLexicon>();
    Map<String, PosTaggerLexicon> lexiconMap = new HashMap<String, PosTaggerLexicon>();
    List<String> lexiconNames = new ArrayList<>();

    ZipEntry ze = null;
    while ((ze = zis.getNextEntry()) != null) {
      LOG.debug(ze.getName());
      if (ze.getName().endsWith(".obj")) {
        LOG.debug("deserializing " + ze.getName());
        ObjectInputStream in = new ObjectInputStream(zis);
        PosTaggerLexicon lexicon = (PosTaggerLexicon) in.readObject();
        lexiconMap.put(lexicon.getName(), lexicon);
      } else if (ze.getName().equals("lexicons.txt")) {
        // this ensures the lexicons will be added in the correct
        // order

        @SuppressWarnings("resource")
        Scanner scanner = new Scanner(new BufferedReader(new InputStreamReader(zis, "UTF-8")));
        while (scanner.hasNextLine()) {
          String line = scanner.nextLine();
          if (line.length() > 0)
            lexiconNames.add(line);
        }
      }
    }

    for (String lexiconName : lexiconNames) {
      PosTaggerLexicon lexicon = lexiconMap.get(lexiconName);
      if (lexicon instanceof NeedsSessionId)
        ((NeedsSessionId) lexicon).setSessionId(sessionId);
      lexicons.add(lexicon);
    }

    return lexicons;
  }
}
